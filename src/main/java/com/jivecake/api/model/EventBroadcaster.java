package com.jivecake.api.model;

import java.util.Date;

import org.glassfish.jersey.media.sse.EventOutput;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EventBroadcaster {
    @JsonIgnore
    public final SseBroadcaster broadcaster;
    public final EventOutput output;
    public final String user_id;
    public final Date timeCreated;

    public EventBroadcaster(SseBroadcaster broadcaster, EventOutput output, String user_id, Date timeCreated) {
        this.broadcaster = broadcaster;
        this.output = output;
        this.user_id = user_id;
        this.timeCreated = timeCreated;
    }
}