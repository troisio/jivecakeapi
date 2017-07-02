package com.jivecake.api.service;

import com.jivecake.api.model.Event;

public class EventService {
    public static final int STATUS_ACTIVE = 1;
    public static final int STATUS_INACTIVE = 0;

    public boolean isValidEvent(Event event) {
        return event.name != null &&
               event.name.length() > 0 &&
               event.name.length() < 500 &&
               (event.status == EventService.STATUS_INACTIVE || event.status == EventService.STATUS_ACTIVE) &&
               (
                   (event.paymentProfileId == null && event.currency == null) ||
                   (event.paymentProfileId != null && event.currency != null)
               );
    }
}