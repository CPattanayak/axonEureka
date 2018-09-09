package com.axon.example.democomplaint;

public class ComplaintEventBase {
    protected EventType eventType;

    public EventType getEventType() {
        return eventType;
    }

    public void setEventType(EventType eventType) {
        this.eventType = eventType;
    }
}
