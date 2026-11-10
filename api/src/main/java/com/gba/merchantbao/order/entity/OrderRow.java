package com.gba.merchantbao.order.entity;

import java.math.BigDecimal;
import java.time.Instant;

/** Persistence only. Item snapshots live in trade_order_item; consumers see projected views. */
public record OrderRow(long id, long merchantId, String orderNo, long customerId, String status, String payStatus,
                       BigDecimal totalAmount, BigDecimal paidAmount, String currency, Instant placedAt,
                       Instant createdAt, Instant updatedAt, int version) {
    @Override
    public String toString() {
        return "OrderRow[id=" + id + ", merchantId=" + merchantId + "]";
    }
}
