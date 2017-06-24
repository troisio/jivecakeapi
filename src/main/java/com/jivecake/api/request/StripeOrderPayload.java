package com.jivecake.api.request;

import java.util.List;

import org.bson.types.ObjectId;

public class StripeOrderPayload {
    public List<EntityQuantity<ObjectId>> itemData;
    public StripeToken token;
    public String currency;
}