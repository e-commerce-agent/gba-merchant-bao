package com.gba.merchantbao.product.vo;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.product.entity.ProductAttributeRow;
import com.gba.merchantbao.product.entity.ProductCategoryRow;
import com.gba.merchantbao.product.entity.ProductRow;
import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/** Product module output models. IDs are decimal strings; times are UTC ISO-8601. */
public final class ProductViews {
    private ProductViews() { }

    public record CategoryView(ApiId categoryId, ApiId parentId, String name, int sortNo, String status,
                               Instant createdAt, Instant updatedAt, List<CategoryView> children) {
        public static CategoryView from(ProductCategoryRow row) {
            return new CategoryView(new ApiId(row.id()), row.parentId() == null ? null : new ApiId(row.parentId()),
                    row.name(), row.sortNo(), row.status(), utc(row.createdAt()), utc(row.updatedAt()), List.of());
        }
        public static CategoryView withChildren(ProductCategoryRow row, List<CategoryView> children) {
            return new CategoryView(new ApiId(row.id()), row.parentId() == null ? null : new ApiId(row.parentId()),
                    row.name(), row.sortNo(), row.status(), utc(row.createdAt()), utc(row.updatedAt()), children);
        }
    }

    /** Staff management view; carries the internal status and audit columns. */
    public record ProductView(ApiId productId, ApiId merchantId, ApiId categoryId, String productCode,
                              String name, String brand, String description, String status,
                              Instant createdAt, Instant updatedAt) {
        public static ProductView from(ProductRow row) {
            return new ProductView(new ApiId(row.id()), new ApiId(row.merchantId()),
                    row.categoryId() == null ? null : new ApiId(row.categoryId()), row.productCode(), row.name(),
                    row.brand(), row.description(), row.status(), utc(row.createdAt()), utc(row.updatedAt()));
        }
    }

    /** Public mall view: no merchantId, no description-less management noise. */
    public record PublicProductView(ApiId productId, String productCode, String name, String brand,
                                    String description, @JsonProperty("attributes") Map<String, String> attributes,
                                    List<PublicSkuView> skus) {
        public static PublicProductView from(ProductRow row, Map<String, String> attributes, List<PublicSkuView> skus) {
            return new PublicProductView(new ApiId(row.id()), row.productCode(), row.name(), row.brand(),
                    row.description(), attributes, skus);
        }
    }

    /** Staff SKU view. */
    public record SkuView(ApiId skuId, ApiId productId, String skuCode, String title, String price,
                          Map<String, String> attributes, String status, Instant createdAt, Instant updatedAt) {
        public static SkuView from(ProductSkuRow row, Map<String, String> attributes) {
            return new SkuView(new ApiId(row.id()), new ApiId(row.productId()), row.skuCode(), row.title(),
                    row.price().toPlainString(), attributes, row.status(), utc(row.createdAt()), utc(row.updatedAt()));
        }
    }

    /** Public SKU view: no internal status noise beyond sale availability. */
    public record PublicSkuView(ApiId skuId, String skuCode, String title, String price,
                                Map<String, String> attributes) {
        public static PublicSkuView from(ProductSkuRow row, Map<String, String> attributes) {
            return new PublicSkuView(new ApiId(row.id()), row.skuCode(), row.title(), row.price().toPlainString(), attributes);
        }
    }

    public record AttributeView(ApiId productId, String attrName, String attrValue, boolean searchable) {
        public static AttributeView from(ProductAttributeRow row) {
            return new AttributeView(new ApiId(row.productId()), row.attrName(), row.attrValue(), row.searchable());
        }
    }

    public record PageView<T>(List<T> items, String nextCursor, boolean hasMore) { }

    private static Instant utc(LocalDateTime time) { return time == null ? null : time.toInstant(ZoneOffset.UTC); }
}
