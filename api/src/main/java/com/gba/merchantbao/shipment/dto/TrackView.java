package com.gba.merchantbao.shipment.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;

/** One track event; exposed only inside an already-authorized shipment response. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackView(Instant eventTime, String status, String description, String location) {
}
