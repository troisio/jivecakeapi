package com.jivecake.api.model;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity("PaymentDetails")
public class SubscriptionPaymentDetail extends PaymentDetail {
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;
}