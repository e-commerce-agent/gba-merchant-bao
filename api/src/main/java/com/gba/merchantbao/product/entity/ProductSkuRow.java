package com.gba.merchantbao.product.entity;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Database times are UTC, without implicit JVM timezone conversion. */
public record ProductSkuRow(long id, long merchantId, long productId, String skuCode, String title,
                            BigDecimal price, String attributesJson, String status,
                            LocalDateTime createdAt, LocalDateTime updatedAt) {
    @Override public String toString() { return "ProductSkuRow[id=" + id + ", merchantId=" + merchantId + "]"; }
}
