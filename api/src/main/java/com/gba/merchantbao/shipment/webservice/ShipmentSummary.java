package com.gba.merchantbao.shipment.webservice;

import com.gba.merchantbao.shipment.dto.ShipmentView;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlType;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/** SOAP summary of one order shipment; same source and desensitization rules as the REST contract. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "ShipmentSummary",
        propOrder = {"orderNo", "status", "carrierName", "carrierCode", "trackingNo", "lastEventTime", "tracks"})
public class ShipmentSummary {

    private String orderNo;
    private String status;
    private String carrierName;
    private String carrierCode;

    @XmlElement(required = true)
    private String trackingNo;

    private String lastEventTime;

    @XmlElement(name = "track")
    private List<TrackSummary> tracks = new ArrayList<>();

    public ShipmentSummary() {
    }

    public static ShipmentSummary from(ShipmentView view) {
        ShipmentSummary summary = new ShipmentSummary();
        summary.orderNo = view.orderNo();
        summary.status = view.status().name();
        summary.carrierName = view.carrierName();
        summary.carrierCode = view.carrierCode();
        summary.trackingNo = view.trackingNo();
        summary.tracks = new ArrayList<>(view.tracks().stream()
                .map(t -> new TrackSummary(t.eventTime() == null ? null
                        : DateTimeFormatter.ISO_INSTANT.format(t.eventTime()), t.status(), t.description(), t.location()))
                .toList());
        summary.lastEventTime = summary.tracks.isEmpty() ? null
                : summary.tracks.get(summary.tracks.size() - 1).getEventTime();
        return summary;
    }

    public String getOrderNo() { return orderNo; }
    public void setOrderNo(String orderNo) { this.orderNo = orderNo; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getCarrierName() { return carrierName; }
    public void setCarrierName(String carrierName) { this.carrierName = carrierName; }
    public String getCarrierCode() { return carrierCode; }
    public void setCarrierCode(String carrierCode) { this.carrierCode = carrierCode; }
    public String getTrackingNo() { return trackingNo; }
    public void setTrackingNo(String trackingNo) { this.trackingNo = trackingNo; }
    public String getLastEventTime() { return lastEventTime; }
    public void setLastEventTime(String lastEventTime) { this.lastEventTime = lastEventTime; }
    public List<TrackSummary> getTracks() { return tracks; }
    public void setTracks(List<TrackSummary> tracks) { this.tracks = tracks == null ? new ArrayList<>() : tracks; }
}
