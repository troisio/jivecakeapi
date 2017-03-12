package com.jivecake.api.service;

import javax.inject.Inject;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.jivecake.api.model.Event;

public class EventService {
    private final Datastore datastore;

    @Inject
    public EventService(Datastore datastore) {
        this.datastore = datastore;
    }

    public Event delete(ObjectId id) {
        Query<Event> deleteQuery = this.datastore.createQuery(Event.class).filter("id", id);
        Event result = this.datastore.findAndDelete(deleteQuery);
        return result;
    }

    public Key<Event> save(Event event) {
        Key<Event> result = this.datastore.save(event);
        return result;
    }

    public Event read(ObjectId id) {
        Event result = this.datastore.find(Event.class)
            .field("id").equal(id)
            .get();
        return result;
    }

    public Query<Event> query() {
        return this.datastore.find(Event.class);
    }

    public int getInactiveEventStatus() {
        return 0;
    }

    public int getActiveEventStatus() {
        return 1;
    }
}