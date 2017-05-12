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
        EventOutput output = new EventOutput();
        SseBroadcaster broadcaster = new SseBroadcaster();
        broadcaster.add(output);
        EventBroadcaster eventBroadcaster = new EventBroadcaster(broadcaster, output, key, new Date());

        this.broadcasters.putIfAbsent(key, eventBroadcaster);
        return this.broadcasters.get(key).output;
    }
}