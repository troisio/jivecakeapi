package com.jivecake.api.service;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.jivecake.api.model.EventBroadcaster;

public class ClientConnectionService {
    public final Map<String, EventBroadcaster> broadcasters = new HashMap<>();

    public EventOutput getEventOutput(String key) {
        SseBroadcaster newBroadcaster = new SseBroadcaster();
        EventBroadcaster eventBroadcaster = new EventBroadcaster(newBroadcaster, key, new Date());
        this.broadcasters.putIfAbsent(key, eventBroadcaster);

        SseBroadcaster broadcaster = this.broadcasters.get(key).broadcaster;
        EventOutput output = new EventOutput();
        broadcaster.add(output);
        return output;
    }
}