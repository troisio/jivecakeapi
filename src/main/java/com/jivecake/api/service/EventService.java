package com.jivecake.api.service;

import java.util.HashMap;
import java.util.Map;

import org.bson.types.ObjectId;

import com.jivecake.api.request.AggregatedItemGroup;

public class EventService {
    private final Map<String, AggregatedItemGroup> cache = new HashMap<>();

    public AggregatedItemGroup read(ObjectId eventId) {
        return this.cache.get(this.getAggregatedCacheKey(eventId));
    }

    public String getAggregatedCacheKey(ObjectId eventId) {
        return "AggregatedItemGroup|" + eventId.toString();
    }

    public void invalidateAggregatedEventCache(ObjectId eventId) {
        this.cache.remove(this.getAggregatedCacheKey(eventId));
    }

    public int getInactiveEventStatus() {
        return 0;
    }

    public int getActiveEventStatus() {
        return 1;
    }
}