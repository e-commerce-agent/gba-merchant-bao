package com.gba.merchantbao.product.service;

import com.gba.merchantbao.auth.entity.Tenant;
import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.auth.service.TenantResolver;
import com.gba.merchantbao.auth.util.AuthInput;
import com.gba.merchantbao.inventory.service.InventoryAvailabilityQueryService;
import com.gba.merchantbao.product.cache.ProductCatalog;
import com.gba.merchantbao.product.cache.ProductCacheStore;
import com.gba.merchantbao.product.entity.ProductAttributeRow;
import com.gba.merchantbao.product.entity.ProductCategoryRow;
import com.gba.merchantbao.product.entity.ProductRow;
import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.gba.merchantbao.product.exception.ProductError;
import com.gba.merchantbao.product.mapper.ProductAttributeMapper;
import com.gba.merchantbao.product.mapper.ProductCategoryMapper;
import com.gba.merchantbao.product.mapper.ProductMapper;
import com.gba.merchantbao.product.mapper.ProductSkuMapper;
import com.gba.merchantbao.product.vo.ProductViews.CategoryView;
import com.gba.merchantbao.product.vo.ProductViews.PageView;
import com.gba.merchantbao.product.vo.ProductViews.ProductView;
import com.gba.merchantbao.product.vo.ProductViews.PublicProductView;
import com.gba.merchantbao.product.vo.ProductViews.PublicSkuView;
import com.gba.merchantbao.product.vo.ProductViews.SkuView;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Product reads for the public mall, authenticated roles and the future Agent tool.
 * Public reads resolve the store by URL store slug; authenticated reads scope every
 * query with the trusted Actor merchant. Unpublished content never crosses public edges.
 */
@Service
public class ProductQueryService {
    public static final String PUBLISHED = "PUBLISHED";
    public static final String SKU_ACTIVE = "ACTIVE";

    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final ProductAttributeMapper attributes;
    private final ProductCategoryMapper categories;
    private final TenantResolver tenants;
    private final ObjectProvider<ProductCacheStore> cache;
    private final InventoryAvailabilityQueryService availability;

    public ProductQueryService(ProductMapper products, ProductSkuMapper skus, ProductAttributeMapper attributes,
                               ProductCategoryMapper categories, TenantResolver tenants,
                               ObjectProvider<ProductCacheStore> cache, InventoryAvailabilityQueryService availability) {
        this.products = products;
        this.skus = skus;
        this.attributes = attributes;
        this.categories = categories;
        this.tenants = tenants;
        this.cache = cache;
        this.availability = availability;
    }

    /** Resolved merchant facts for the future read-only product Tool; identity comes from the caller's trusted context. */
    public record ProductFact(long productId, String productCode, String name, String brand, String status,
                              Map<String, String> attributes, List<SkuFact> skus) { }
    public record SkuFact(long skuId, String skuCode, String title, String price, String status,
                          Map<String, String> specAttributes, Map<String, String> compatibilityAttributes) { }

    // ---------- public mall (anonymous, store slug resolves the tenant) ----------

    public PageView<PublicProductView> publicPage(String storeSlug, Long categoryId, String cursor, int size) {
        ProductCacheStore store = cache.getIfAvailable();
        long merchantId = requireStore(storeSlug);
        var position = ProductCursor.decodePublic(cursor);
        boolean cacheable = store != null && cursor == null;
        if (cacheable) {
            var hit = store.readPage(merchantId, categoryId, size);
            if (hit != null) return new PageView<>(withAvailability(merchantId, hit.items()), hit.nextCursor(), hit.hasMore());
        }
        var before = position == null ? null : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(position[0])), ZoneOffset.UTC);
        var beforeId = position == null ? null : Long.parseLong(position[1]);
        var rows = products.page(merchantId, PUBLISHED, categoryId, before, beforeId, size + 1);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);
        var items = rows.stream().map(this::catalogProduct).toList();
        String nextCursor = hasMore ? ProductCursor.encodePublic(rows.getLast().updatedAt(), rows.getLast().id()) : null;
        if (cacheable) store.putPage(merchantId, categoryId, size, ProductCacheStore.PageEntry.of(items, nextCursor, hasMore));
        return new PageView<>(withAvailability(merchantId, items), nextCursor, hasMore);
    }

    public PublicProductView publicProduct(String storeSlug, long productId) {
        ProductCacheStore store = cache.getIfAvailable();
        long merchantId = requireStore(storeSlug);
        if (store != null) {
            var hit = store.readProduct(merchantId, productId);
            if (hit != null) return withAvailability(merchantId, List.of(hit)).getFirst();
        }
        var row = products.find(merchantId, productId);
        if (row == null || !PUBLISHED.equals(row.status())) throw ProductError.missing();
        var view = catalogProduct(row);
        if (store != null) store.putProduct(merchantId, productId, view);
        return withAvailability(merchantId, List.of(view)).getFirst();
    }

    public PublicSkuView publicSku(String storeSlug, long skuId) {
        ProductCacheStore store = cache.getIfAvailable();
        long merchantId = requireStore(storeSlug);
        if (store != null) {
            var hit = store.readSku(merchantId, skuId);
            if (hit != null) return hit.withAvailability(availability.inStock(merchantId, List.of(skuId)).get(skuId));
        }
        var row = skus.find(merchantId, skuId);
        if (row == null || !SKU_ACTIVE.equals(row.status())) throw ProductError.missing();
        var product = products.find(merchantId, row.productId());
        if (product == null || !PUBLISHED.equals(product.status())) throw ProductError.missing();
        var view = ProductCatalog.Sku.from(row, attributeMap(attributes.listForProduct(merchantId, row.productId())));
        if (store != null) store.putSku(merchantId, skuId, view);
        return view.withAvailability(availability.inStock(merchantId, List.of(skuId)).get(skuId));
    }

    // ---------- authenticated reads (consumer sees published, staff sees all) ----------

    public PageView<ProductView> staffOrConsumerPage(Actor actor, Long categoryId, String cursor, int size) {
        var scope = requireActor(actor);
        boolean staffOnly = scope.staff();
        var position = ProductCursor.decodeStaff(cursor);
        var before = position == null ? null : LocalDateTime.ofInstant(java.time.Instant.ofEpochMilli(Long.parseLong(position[0])), ZoneOffset.UTC);
        var beforeId = position == null ? null : Long.parseLong(position[1]);
        var rows = products.page(scope.merchantId(), staffOnly ? null : PUBLISHED, categoryId, before, beforeId, size + 1);
        boolean hasMore = rows.size() > size;
        if (hasMore) rows = rows.subList(0, size);
        var items = rows.stream().map(ProductView::from).toList();
        String nextCursor = hasMore ? ProductCursor.encodeStaff(rows.getLast().updatedAt(), rows.getLast().id()) : null;
        return new PageView<>(items, nextCursor, hasMore);
    }

    public ProductView staffOrConsumerProduct(Actor actor, long productId) {
        var scope = requireActor(actor);
        var row = products.find(scope.merchantId(), productId);
        if (row == null || (!scope.staff() && !PUBLISHED.equals(row.status()))) throw ProductError.missing();
        return ProductView.from(row);
    }

    public SkuView staffOrConsumerSku(Actor actor, long skuId) {
        var scope = requireActor(actor);
        var row = skus.find(scope.merchantId(), skuId);
        if (row == null) throw ProductError.missing();
        if (!scope.staff()) {
            if (!SKU_ACTIVE.equals(row.status())) throw ProductError.missing();
            var product = products.find(scope.merchantId(), row.productId());
            if (product == null || !PUBLISHED.equals(product.status())) throw ProductError.missing();
        }
        return SkuView.from(row, attributeMap(attributes.listForProduct(scope.merchantId(), row.productId())));
    }

    public List<CategoryView> staffCategories(Actor actor) {
        var scope = requireActor(actor);
        if (!scope.staff()) throw ProductError.forbidden(); // management view (incl. INACTIVE) is staff-only
        return categoryTree(categories.listAll(scope.merchantId()));
    }

    // ---------- Agent-facing read-only facts (merchant scope resolved by the caller's trusted context) ----------

    public ProductFact findVisibleProduct(long merchantId, long productId) {
        var row = products.find(merchantId, productId);
        if (row == null || !PUBLISHED.equals(row.status())) return null;
        var attrs = attributeMap(attributes.listForProduct(merchantId, productId));
        List<SkuFact> skuFacts = skus.listForProduct(merchantId, productId, SKU_ACTIVE).stream()
                .map(sku -> new SkuFact(sku.id(), sku.skuCode(), sku.title(), sku.price().toPlainString(), sku.status(),
                        parseSpecAttributes(sku.attributesJson()), attrs))
                .toList();
        return new ProductFact(row.id(), row.productCode(), row.name(), row.brand(), row.status(), attrs, skuFacts);
    }

    public SkuFact findVisibleSku(long merchantId, long skuId) {
        var row = skus.find(merchantId, skuId);
        if (row == null || !SKU_ACTIVE.equals(row.status())) return null;
        var product = products.find(merchantId, row.productId());
        if (product == null || !PUBLISHED.equals(product.status())) return null;
        var attrs = attributeMap(attributes.listForProduct(merchantId, row.productId()));
        return new SkuFact(row.id(), row.skuCode(), row.title(), row.price().toPlainString(), row.status(),
                parseSpecAttributes(row.attributesJson()), attrs);
    }

    public List<ProductFact> listVisibleProducts(long merchantId, int limit) {
        int bounded = Math.max(1, Math.min(limit, 50));
        return products.page(merchantId, PUBLISHED, null, null, null, bounded).stream()
                .map(row -> findVisibleProduct(merchantId, row.id()))
                .toList();
    }

    // ---------- helpers ----------

    public record Scope(long merchantId, boolean staff) { }

    public static Scope requireActor(Actor actor) {
        if (actor == null || actor.principal() == null) throw new ProductError(401, com.gba.merchantbao.common.error.CommonErrorCode.UNAUTHORIZED);
        var principal = actor.principal();
        boolean staff = principal.role() == com.gba.merchantbao.auth.enums.AccountRole.MERCHANT_STAFF;
        return new Scope(principal.merchantId(), staff);
    }

    long requireStore(String storeSlug) {
        try {
            storeSlug = AuthInput.storeSlug(storeSlug);
        } catch (IllegalArgumentException e) {
            throw ProductError.missing();
        }
        ProductCacheStore store = cache.getIfAvailable();
        if (store != null) {
            Long cached = store.readMerchantId(storeSlug);
            if (cached != null) return cached;
        }
        Tenant tenant = tenants.resolveActive(storeSlug).orElseThrow(ProductError::missing);
        if (store != null) store.putMerchantId(storeSlug, tenant.id());
        return tenant.id();
    }

    private ProductCatalog.Product catalogProduct(ProductRow row) {
        var skuViews = skus.listForProduct(row.merchantId(), row.id(), SKU_ACTIVE).stream()
                .map(sku -> ProductCatalog.Sku.from(sku, parseSpecAttributes(sku.attributesJson())))
                .toList();
        return ProductCatalog.Product.from(row, attributeMap(attributes.listForProduct(row.merchantId(), row.id())), skuViews);
    }

    /** One inventory read for the whole visible page, including when its catalog came from Redis. */
    private List<PublicProductView> withAvailability(long merchantId, List<ProductCatalog.Product> catalog) {
        var ids = catalog.stream().flatMap(product -> product.skus().stream())
                .map(sku -> sku.skuId().value()).toList();
        var stock = availability.inStock(merchantId, ids);
        return catalog.stream().map(product -> product.withAvailability(stock)).toList();
    }

    public static Map<String, String> attributeMap(List<ProductAttributeRow> rows) {
        var map = new LinkedHashMap<String, String>();
        for (var row : rows) map.put(row.attrName(), row.attrValue());
        return map;
    }

    /** SKU spec combos live in attributes_json; parsing failures degrade to an empty map, never a 500. */
    public static Map<String, String> parseSpecAttributes(String json) {
        if (json == null || json.isBlank()) return Map.of();
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            if (node == null || !node.isObject()) return Map.of();
            var map = new LinkedHashMap<String, String>();
            var fields = node.fields();
            while (fields.hasNext()) {
                var field = fields.next();
                if (field.getValue().isValueNode()) map.put(field.getKey(), field.getValue().asText());
            }
            return map;
        } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException e) {
            return Map.of();
        }
    }

    static List<CategoryView> categoryTree(List<ProductCategoryRow> rows) {
        var childrenByParent = new LinkedHashMap<Long, List<ProductCategoryRow>>();
        var roots = new ArrayList<ProductCategoryRow>();
        for (var row : rows) {
            if (row.parentId() == null) roots.add(row);
            else childrenByParent.computeIfAbsent(row.parentId(), k -> new ArrayList<>()).add(row);
        }
        var result = new ArrayList<CategoryView>();
        for (var root : roots) {
            var children = childrenByParent.getOrDefault(root.id(), List.of()).stream().map(CategoryView::from).toList();
            result.add(CategoryView.withChildren(root, children));
        }
        return result;
    }
}
