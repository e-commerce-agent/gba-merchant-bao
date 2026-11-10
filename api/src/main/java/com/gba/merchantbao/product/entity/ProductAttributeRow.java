package com.gba.merchantbao.product.entity;

import java.time.LocalDateTime;

/** Database times are UTC, without implicit JVM timezone conversion. */
public record ProductAttributeRow(long id, long merchantId, long productId, String attrName,
                                  String attrValue, boolean searchable,
                                  LocalDateTime createdAt, LocalDateTime updatedAt) {
    @Override public String toString() { return "ProductAttributeRow[id=" + id + ", productId=" + productId + "]"; }
}
