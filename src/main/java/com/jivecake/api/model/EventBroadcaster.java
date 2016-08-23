package com.jivecake.api.model;

import java.util.Date;

import org.bson.types.ObjectId;
import org.glassfish.jersey.media.sse.SseBroadcaster;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

public class EventBroadcaster {
    @JsonSerialize(using=ObjectIdSerializer.class)
    public final ObjectId id;

    @JsonIgnore
    public final SseBroadcaster broadcaster;
    public final String user_id;
    public final Date timeCreated;

    public EventBroadcaster(ObjectId id, SseBroadcaster broadcaster, String user_id, Date timeCreated) {
        this.id = id;
        this.broadcaster = broadcaster;
        this.user_id = user_id;
        this.timeCreated = timeCreated;
    }
}