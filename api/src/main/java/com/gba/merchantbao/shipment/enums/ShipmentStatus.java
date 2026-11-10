package com.gba.merchantbao.shipment.enums;

/** Closed shipment status set; NOT_SHIPPED is synthesized when no shipment record exists. */
public enum ShipmentStatus {
    NOT_SHIPPED, IN_TRANSIT, DELIVERED;

    public static ShipmentStatus parse(String raw) {
        try {
            return ShipmentStatus.valueOf(raw);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalStateException("Unknown shipment status: " + raw);
        }
    }
}
