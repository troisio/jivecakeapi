package com.jivecake.api.model;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Id;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

public class Application {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
}