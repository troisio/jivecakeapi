package com.jivecake.api.service;

import javax.inject.Inject;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.Request;

public class LogService {
    public final Datastore datastore;

    @Inject
    public LogService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Query<Request> query() {
        return this.datastore.createQuery(Request.class);
    }

    public Key<Request> save(Request request) {
        return this.datastore.save(request);
    }
}