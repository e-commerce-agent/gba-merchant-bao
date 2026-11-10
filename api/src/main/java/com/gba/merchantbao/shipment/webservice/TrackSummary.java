package com.gba.merchantbao.shipment.webservice;

import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlType;

/** One desensitized track event inside a SOAP shipment summary. */
@XmlAccessorType(XmlAccessType.FIELD)
@XmlType(name = "TrackSummary", propOrder = {"eventTime", "status", "description", "location"})
public class TrackSummary {

    private String eventTime;
    private String status;
    private String description;
    private String location;

    public TrackSummary() {
    }

    public TrackSummary(String eventTime, String status, String description, String location) {
        this.eventTime = eventTime;
        this.status = status;
        this.description = description;
        this.location = location;
    }

    public String getEventTime() { return eventTime; }
    public void setEventTime(String eventTime) { this.eventTime = eventTime; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
}
