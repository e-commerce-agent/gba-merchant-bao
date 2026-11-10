package com.gba.merchantbao.product.service;

import com.gba.merchantbao.auth.security.Actor;
import com.gba.merchantbao.common.id.IdGenerator;
import com.gba.merchantbao.product.dto.ProductRequests;
import com.gba.merchantbao.product.entity.ProductAttributeRow;
import com.gba.merchantbao.product.entity.ProductCategoryRow;
import com.gba.merchantbao.product.entity.ProductRow;
import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.gba.merchantbao.product.exception.ProductError;
import com.gba.merchantbao.product.mapper.ProductAttributeMapper;
import com.gba.merchantbao.product.mapper.ProductCategoryMapper;
import com.gba.merchantbao.product.mapper.ProductMapper;
import com.gba.merchantbao.product.mapper.ProductSkuMapper;
import com.gba.merchantbao.product.service.ProductQueryService.Scope;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Merchant staff catalog maintenance. Every write is scoped to the authenticated Actor's
 * merchant; category depth is capped at two levels; publish flow follows the frozen
 * DRAFT -> PUBLISHED -> UNPUBLISHED transitions with DISCONTINUED as a terminal state.
 */
@Service
public class ProductCommandService {
    public static final String DRAFT = "DRAFT";
    public static final String UNPUBLISHED = "UNPUBLISHED";
    public static final String DISCONTINUED = "DISCONTINUED";
    public static final String SKU_DISCONTINUED = "DISCONTINUED";

    private final ProductMapper products;
    private final ProductSkuMapper skus;
    private final ProductAttributeMapper attributes;
    private final ProductCategoryMapper categories;
    private final IdGenerator ids;
    private final Clock clock;

    public ProductCommandService(ProductMapper products, ProductSkuMapper skus, ProductAttributeMapper attributes,
                                 ProductCategoryMapper categories, IdGenerator ids, Clock clock) {
        this.products = products;
        this.skus = skus;
        this.attributes = attributes;
        this.categories = categories;
        this.ids = ids;
        this.clock = clock;
    }

    // ---------- categories ----------

    public ProductCategoryRow createCategory(Actor actor, ProductRequests.CategoryCreate body) {
        long merchantId = staff(actor).merchantId();
        var now = now();
        Long parentId = body.parentId();
        if (parentId != null) {
            var parent = categories.find(merchantId, parentId);
            if (parent == null) throw ProductError.missing();
            if (parent.parentId() != null) throw ProductError.conflict(); // depth cap: parents must be roots
        }
        if (categories.nameTaken(merchantId, parentId, body.name(), null)) throw ProductError.conflict();
        var row = new ProductCategoryRow(ids.nextId(), merchantId, parentId, body.name(), body.sortNo(),
                "ACTIVE", now, now);
        if (categories.insert(row) != 1) throw ProductError.conflict();
        return row;
    }

    public ProductCategoryRow updateCategory(Actor actor, long categoryId, ProductRequests.CategoryUpdate body) {
        long merchantId = staff(actor).merchantId();
        var existing = categories.find(merchantId, categoryId);
        if (existing == null) throw ProductError.missing();
        String name = body.name() != null ? body.name() : existing.name();
        int sortNo = body.sortNo() != null ? body.sortNo() : existing.sortNo();
        String status = body.status() != null ? body.status() : existing.status();
        if (!"ACTIVE".equals(existing.status()) && "ACTIVE".equals(status)) {
            var parent = existing.parentId() == null ? null : categories.find(merchantId, existing.parentId());
            if (parent != null && !"ACTIVE".equals(parent.status())) throw ProductError.conflict();
        }
        if ("INACTIVE".equals(status) && categories.existsActiveProduct(merchantId, categoryId))
            throw ProductError.conflict();
        if (categories.nameTaken(merchantId, existing.parentId(), name, categoryId)) throw ProductError.conflict();
        if (categories.update(categoryId, merchantId, existing.parentId(), name, sortNo, status, now()) != 1)
            throw ProductError.missing();
        return categories.find(merchantId, categoryId);
    }

    // ---------- products ----------

    public ProductRow createProduct(Actor actor, ProductRequests.ProductCreate body) {
        long merchantId = staff(actor).merchantId();
        if (body.categoryId() != null && categories.find(merchantId, body.categoryId()) == null)
            throw ProductError.missing();
        if (products.codeTaken(merchantId, body.productCode(), null)) throw ProductError.conflict();
        var now = now();
        var row = new ProductRow(ids.nextId(), merchantId, body.categoryId(), body.productCode(), body.name(),
                body.brand(), body.description(), DRAFT, now, now);
        if (products.insert(row) != 1) throw ProductError.conflict();
        return row;
    }

    public ProductRow updateProduct(Actor actor, long productId, ProductRequests.ProductUpdate body) {
        long merchantId = staff(actor).merchantId();
        var existing = requireProduct(merchantId, productId);
        if (DISCONTINUED.equals(existing.status())) throw ProductError.conflict();
        if (body.categoryId() != null && categories.find(merchantId, body.categoryId()) == null)
            throw ProductError.missing();
        Long categoryId = body.categoryId() != null ? body.categoryId() : existing.categoryId();
        String name = body.name() != null ? body.name() : existing.name();
        String brand = body.brand() != null ? body.brand() : existing.brand();
        String description = body.description() != null ? body.description() : existing.description();
        if (products.updateBasics(productId, merchantId, categoryId, name, brand, description, now()) != 1)
            throw ProductError.missing();
        return products.find(merchantId, productId);
    }

    public ProductRow publish(Actor actor, long productId) {
        return transition(actor, productId, DRAFT, ProductQueryService.PUBLISHED);
    }

    public ProductRow unpublish(Actor actor, long productId) {
        return transition(actor, productId, ProductQueryService.PUBLISHED, UNPUBLISHED);
    }

    public ProductRow discontinue(Actor actor, long productId) {
        long merchantId = staff(actor).merchantId();
        var existing = requireProduct(merchantId, productId);
        if (DISCONTINUED.equals(existing.status())) return existing; // idempotent
        if (products.updateStatus(productId, merchantId, existing.status(), DISCONTINUED, now()) != 1)
            throw ProductError.conflict();
        skus.listForProduct(merchantId, productId, ProductQueryService.SKU_ACTIVE).forEach(
                sku -> skus.update(sku.id(), merchantId, sku.title(), sku.price(), sku.attributesJson(),
                        SKU_DISCONTINUED, now()));
        return products.find(merchantId, productId);
    }

    private ProductRow transition(Actor actor, long productId, String expected, String target) {
        long merchantId = staff(actor).merchantId();
        requireProduct(merchantId, productId);
        if (products.updateStatus(productId, merchantId, expected, target, now()) != 1) throw ProductError.conflict();
        return products.find(merchantId, productId);
    }

    // ---------- attributes (replace whole set) ----------

    @Transactional
    public List<ProductAttributeRow> replaceAttributes(Actor actor, long productId, ProductRequests.AttributesUpdate body) {
        long merchantId = staff(actor).merchantId();
        var product = requireProduct(merchantId, productId);
        if (DISCONTINUED.equals(product.status())) throw ProductError.conflict();
        attributes.deleteForProduct(merchantId, productId);
        var now = now();
        for (var row : body.rows()) {
            var record = new ProductAttributeRow(ids.nextId(), merchantId, productId, row.name(), row.value(),
                    row.searchable(), now, now);
            if (attributes.insert(record) != 1) throw ProductError.conflict();
        }
        return attributes.listForProduct(merchantId, productId);
    }

    // ---------- SKUs ----------

    public ProductSkuRow createSku(Actor actor, long productId, ProductRequests.SkuCreate body) {
        long merchantId = staff(actor).merchantId();
        var product = requireProduct(merchantId, productId);
        if (DISCONTINUED.equals(product.status())) throw ProductError.conflict();
        if (skus.codeTaken(merchantId, body.skuCode(), null)) throw ProductError.conflict();
        var now = now();
        var row = new ProductSkuRow(ids.nextId(), merchantId, productId, body.skuCode(), body.title(),
                body.price(), writeSpecAttributes(body.attributes()), body.status(), now, now);
        if (skus.insert(row) != 1) throw ProductError.conflict();
        return row;
    }

    public ProductSkuRow updateSku(Actor actor, long skuId, ProductRequests.SkuUpdate body) {
        long merchantId = staff(actor).merchantId();
        var existing = skus.find(merchantId, skuId);
        if (existing == null) throw ProductError.missing();
        if (SKU_DISCONTINUED.equals(existing.status()) && !SKU_DISCONTINUED.equals(body.status()))
            throw ProductError.conflict(); // discontinued SKUs never return to sale
        String title = body.title() != null ? body.title() : existing.title();
        var price = body.price() != null ? body.price() : existing.price();
        String json = body.attributes() != null ? writeSpecAttributes(body.attributes()) : existing.attributesJson();
        String status = body.status() != null ? body.status() : existing.status();
        if (skus.update(skuId, merchantId, title, price, json, status, now()) != 1) throw ProductError.missing();
        return skus.find(merchantId, skuId);
    }

    // ---------- helpers ----------

    static Scope staff(Actor actor) {
        var scope = ProductQueryService.requireActor(actor);
        if (!scope.staff()) throw ProductError.forbidden();
        return scope;
    }

    private ProductRow requireProduct(long merchantId, long productId) {
        var row = products.find(merchantId, productId);
        if (row == null) throw ProductError.missing();
        return row;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }

    private String writeSpecAttributes(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) return null;
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
            attributes.forEach(node::put);
            return node.toString();
        } catch (RuntimeException e) {
            throw ProductError.invalid();
        }
    }
}
