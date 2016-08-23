package com.jivecake.api.service;

import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jivecake.api.model.PaymentProfile;

@Singleton
public class PaymentProfileService {
    private final Datastore datastore;

    @Inject
    public PaymentProfileService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Key<PaymentProfile> save(PaymentProfile profile) {
        Key<PaymentProfile> key = this.datastore.save(profile);
        return key;
    }

    public List<PaymentProfile> readByOrganization(ObjectId id) {
        List<PaymentProfile> result = this.datastore.find(PaymentProfile.class)
            .field("organizationId").equal(id)
            .asList();
        return result;
    }

    public PaymentProfile read(ObjectId id) {
        PaymentProfile result = this.datastore.find(PaymentProfile.class)
            .field("id").equal(id)
            .get();
        return result;
    }

    public PaymentProfile delete(ObjectId id) {
        Query<PaymentProfile> query = this.datastore.createQuery(PaymentProfile.class).filter("id", id);
        PaymentProfile result = this.datastore.findAndDelete(query);
        return result;
    }

    public Query<PaymentProfile> query() {
        return this.datastore.createQuery(PaymentProfile.class);
    }
}