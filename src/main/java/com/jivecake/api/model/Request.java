package com.jivecake.api.model;

import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.ws.rs.core.Cookie;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexed;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field("user_id")),
    @Index(fields = @Field("timeCreated")),
    @Index(fields={
        @Field("user_id"),
        @Field("timeCreated")
    })
})
public class Request {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String path;
    public String uri;
    public String ip;
    public String body;
    public Date date;
    public Map<String, String[]> query;
    public Map<String, Cookie> cookies;
    public Map<String, List<String>> headers;
    public String user_id;

    @Indexed
    public Date timeCreated;
}
