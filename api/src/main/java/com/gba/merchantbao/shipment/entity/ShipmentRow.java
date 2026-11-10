package com.gba.merchantbao.shipment.entity;

import java.time.Instant;

/** Physical shipment row; read-only module, no version/optimistic-lock column. */
public record ShipmentRow(long id, long merchantId, long orderId, String orderNo, String status,
        String carrierName, String carrierCode, String trackingNo, Instant estimatedDeliveryAt,
        Instant deliveredAt, Instant createdAt, Instant updatedAt) {
}
