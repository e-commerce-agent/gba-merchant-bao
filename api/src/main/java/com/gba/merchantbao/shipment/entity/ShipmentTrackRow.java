package com.gba.merchantbao.shipment.entity;

import java.time.Instant;

/** Append-only track event row; merchant_id is redundant for isolation queries without a join. */
public record ShipmentTrackRow(long id, long shipmentId, long merchantId, Instant eventTime,
        String status, String description, String location, Instant createdAt) {
}
