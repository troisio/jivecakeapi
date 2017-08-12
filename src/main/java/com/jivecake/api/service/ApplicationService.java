package com.jivecake.api.service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Date;

import javax.inject.Inject;

import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;

import com.jivecake.api.model.Application;

public class ApplicationService {
    public static final int LIMIT_DEFAULT = 100;
    private final Application application;
    private final Datastore datastore;

    @Inject
    public ApplicationService(Application application, Datastore datastore) {
        this.application = application;
        this.datastore = datastore;
    }

    public Application read() {
        return this.application;
    }

    public Key<com.jivecake.api.model.Exception> saveException(Exception e, String userId) {
        StringWriter writer = new StringWriter();
        e.printStackTrace(new PrintWriter(writer));

        com.jivecake.api.model.Exception exception = new com.jivecake.api.model.Exception();
        exception.stackTrace = writer.toString();
        exception.userId = userId;
        exception.timeCreated = new Date();

        return this.datastore.save(exception);
    }
}