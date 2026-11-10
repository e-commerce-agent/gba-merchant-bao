package com.gba.merchantbao.product.entity;

import java.time.LocalDateTime;

/** Database times are UTC, without implicit JVM timezone conversion. */
public record ProductCategoryRow(long id, long merchantId, Long parentId, String name, int sortNo,
                                 String status, LocalDateTime createdAt, LocalDateTime updatedAt) {
    @Override public String toString() { return "ProductCategoryRow[id=" + id + ", merchantId=" + merchantId + "]"; }
}
