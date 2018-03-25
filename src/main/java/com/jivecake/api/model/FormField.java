package com.jivecake.api.model;

import java.util.Date;
import java.util.List;

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
    @Index(fields = @Field(value = "eventId"))
})
public class FormField {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId eventId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId item;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId event;
    public String label;
    public boolean required;
    public boolean active;
    public List<String> options;
    public int type;
    public Date timeUpdated;
    public Date timeCreated;
}