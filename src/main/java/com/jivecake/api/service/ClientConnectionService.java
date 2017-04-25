package com.jivecake.api.service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.jivecake.api.model.EventBroadcaster;

public class ClientConnectionService {
    private final List<EventBroadcaster> broadcasters = new ArrayList<>();

    public List<EventBroadcaster> getBroadcasters() {
        return this.broadcasters;
    }

    public EventOutput createEventOutput(String user_id) {
        List<EventBroadcaster> broadcasters = this.broadcasters.stream()
            .filter(broadcaster -> broadcaster.user_id.equals(user_id))
            .collect(Collectors.toList());

        SseBroadcaster broadcaster;

        if (broadcasters.isEmpty()) {
            broadcaster = new SseBroadcaster();
            EventBroadcaster eventBroadcaster = new EventBroadcaster(broadcaster, user_id, new Date());
            this.broadcasters.add(eventBroadcaster);
        } else {
            broadcaster = broadcasters.get(0).broadcaster;
        }

        EventOutput output = new EventOutput();
        broadcaster.add(output);

        return output;
    }
}