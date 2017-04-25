package com.jivecake.api.model;

import java.util.Date;

import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.fasterxml.jackson.annotation.JsonIgnore;

public class EventBroadcaster {
    @JsonIgnore
    public final SseBroadcaster broadcaster;
    public final String user_id;
    public final Date timeCreated;

    public EventBroadcaster(SseBroadcaster broadcaster, String user_id, Date timeCreated) {
        this.broadcaster = broadcaster;
        this.user_id = user_id;
        this.timeCreated = timeCreated;
    }
}