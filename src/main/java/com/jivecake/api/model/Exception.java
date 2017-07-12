package com.jivecake.api.model;

import java.util.Date;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field(value = "timeCreated"))
})
public class Exception {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String userId;
    public String stackTrace;
    public Date timeCreated;
}
