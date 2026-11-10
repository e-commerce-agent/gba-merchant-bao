package com.gba.merchantbao.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.gba.merchantbao.common.api.ApiId;
import com.gba.merchantbao.shipment.entity.ShipmentRow;
import com.gba.merchantbao.shipment.entity.ShipmentTrackRow;
import com.gba.merchantbao.shipment.enums.ShipmentStatus;

import java.time.Instant;
import java.util.List;

/**
 * Contract response for GET /api/v1/orders/{orderId}/shipment.
 * IDs are strings (ApiId) and times are UTC; NOT_SHIPPED synthesizes an empty track list.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ShipmentView(ApiId orderId, String orderNo, ShipmentStatus status, String carrierName,
        String carrierCode, String trackingNo, Instant estimatedDeliveryAt, Instant deliveredAt,
        List<TrackView> tracks) {

    public ShipmentView {
        tracks = tracks == null ? List.of() : List.copyOf(tracks);
    }

    public static ShipmentView of(ShipmentRow row, List<ShipmentTrackRow> tracks) {
        return new ShipmentView(new ApiId(row.orderId()), row.orderNo(), ShipmentStatus.parse(row.status()),
                row.carrierName(), row.carrierCode(), row.trackingNo(), row.estimatedDeliveryAt(),
                row.deliveredAt(), tracks.stream().map(ShipmentView::track).toList());
    }

    public static ShipmentView notShipped(long orderId) {
        return new ShipmentView(new ApiId(orderId), null, ShipmentStatus.NOT_SHIPPED,
                null, null, null, null, null, List.of());
    }

    private static TrackView track(ShipmentTrackRow row) {
        return new TrackView(row.eventTime(), row.status(), row.description(), row.location());
    }
}
