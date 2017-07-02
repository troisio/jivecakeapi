package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.PaymentProfile;

public class AggregatedEvent {
    public Event event;
    public PaymentProfile profile;
    public List<ItemData> itemData;
}
