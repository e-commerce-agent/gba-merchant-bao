package com.gba.merchantbao.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** Trade snapshot frozen at placement; history never re-joins current product data. */
public record OrderItemRow(long id, long merchantId, long orderId, long productId, long skuId,
                           String productNameSnapshot, String skuAttrsSnapshot, BigDecimal unitPrice,
                           int quantity, BigDecimal amount, Instant createdAt, Instant updatedAt) {
    @Override
    public String toString() {
        return "OrderItemRow[id=" + id + ", merchantId=" + merchantId + ", orderId=" + orderId + "]";
    }
}
