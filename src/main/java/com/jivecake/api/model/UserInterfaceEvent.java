package com.jivecake.api.model;

import java.util.Date;
import java.util.Map;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Indexes({
    @Index(fields = @Field(value = "userId")),
    @Index(fields = @Field(value = "timeCreated"))
})
@Entity
public class UserInterfaceEvent {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String userId;
    public String agent;
    public String event;
    public Map<String, Object> parameters;
    public String ip;
    public Date timeCreated;
}
