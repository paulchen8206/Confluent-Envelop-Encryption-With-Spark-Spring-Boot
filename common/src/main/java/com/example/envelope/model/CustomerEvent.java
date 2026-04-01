package com.example.envelope.model;

import jakarta.validation.constraints.NotBlank;

public class CustomerEvent {

    @NotBlank
    private String eventId;

    @NotBlank
    private String customerId;

    @NotBlank
    private String fullName;

    @NotBlank
    private String ssn;

    @NotBlank
    private String action;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public void setCustomerId(String customerId) {
        this.customerId = customerId;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getSsn() {
        return ssn;
    }

    public void setSsn(String ssn) {
        this.ssn = ssn;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}
