package com.jivecake.api.service;

import com.jivecake.api.model.Event;

public class EventService {
    public boolean isValidEvent(Event event) {
        return event.name != null &&
               event.name.length() > 0 &&
               event.name.length() < 500 &&
               (event.status == this.getInactiveEventStatus() || event.status == this.getActiveEventStatus());
    }

    public int getInactiveEventStatus() {
        return 0;
    }

    public int getActiveEventStatus() {
        return 1;
    }
}