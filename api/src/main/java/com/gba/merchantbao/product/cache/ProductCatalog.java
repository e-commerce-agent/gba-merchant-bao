package com.gba.merchantbao.product.cache;

import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.product.entity.ProductRow;
import com.gba.merchantbao.product.entity.ProductSkuRow;
import com.gba.merchantbao.product.vo.ProductViews.PublicProductView;
import com.gba.merchantbao.product.vo.ProductViews.PublicSkuView;

import java.util.List;
import java.util.Map;

/** Cacheable catalog facts. Stock is added only when constructing a fresh HTTP response. */
public final class ProductCatalog {
    private ProductCatalog() { }

    public record Sku(ApiId skuId, String skuCode, String title, String price, Map<String, String> attributes) {
        public static Sku from(ProductSkuRow row, Map<String, String> attributes) {
            return new Sku(new ApiId(row.id()), row.skuCode(), row.title(), row.price().toPlainString(), attributes);
        }

        public PublicSkuView withAvailability(boolean inStock) {
            return new PublicSkuView(skuId, skuCode, title, price, attributes, inStock);
        }
    }

    public record Product(ApiId productId, String productCode, String name, String brand, String description,
                          Map<String, String> attributes, List<Sku> skus) {
        public static Product from(ProductRow row, Map<String, String> attributes, List<Sku> skus) {
            return new Product(new ApiId(row.id()), row.productCode(), row.name(), row.brand(),
                    row.description(), attributes, skus);
        }

        public PublicProductView withAvailability(Map<Long, Boolean> stock) {
            return new PublicProductView(productId, productCode, name, brand, description, attributes,
                    skus.stream().map(sku -> sku.withAvailability(stock.getOrDefault(sku.skuId().value(), false))).toList());
        }
    }
}
