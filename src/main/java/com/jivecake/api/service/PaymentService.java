package com.jivecake.api.service;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jivecake.api.model.PaymentDetail;

@Singleton
public class PaymentService {
    private final Datastore datastore;

    @Inject
    public PaymentService(Datastore datastore) {
        this.datastore = datastore;
    }

    public PaymentDetail getPaymentDetailsFromCustom(ObjectId id) {
        return this.datastore.find(PaymentDetail.class).field("custom").equal(id).get();
    }

    public Key<PaymentDetail> save(PaymentDetail detail) {
        return this.datastore.save(detail);
    }

    public PaymentDetail readPaymentDetail(ObjectId id) {
        return this.datastore.find(PaymentDetail.class).field("id").equal(id).get();
    }
}