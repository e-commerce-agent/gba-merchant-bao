package com.gba.merchantbao.product.entity;

import java.time.LocalDateTime;

/** Database times are UTC, without implicit JVM timezone conversion. */
public record ProductRow(long id, long merchantId, Long categoryId, String productCode, String name,
                         String brand, String description, String status,
                         LocalDateTime createdAt, LocalDateTime updatedAt) {
    @Override public String toString() { return "ProductRow[id=" + id + ", merchantId=" + merchantId + "]"; }
}
