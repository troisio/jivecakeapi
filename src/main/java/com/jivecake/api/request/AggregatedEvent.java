package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;

public class AggregatedEvent {
    public Organization organization;
    public Event event;
    public List<FormField> fields;
    public PaymentProfile profile;
    public List<ItemData> itemData;
    public List<EntityAsset> assets;
}
