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
    @Index(fields = @Field("eventId"))
})
public class FormFieldResponse {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId eventId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId formFieldId;

    public String string;
    public Double doubleValue;
    public Long longValue;
    public Date timeUpdated;
    public Date timeCreated;
}
