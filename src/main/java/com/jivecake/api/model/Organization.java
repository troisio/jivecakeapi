package com.jivecake.api.model;

import java.util.Date;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.utils.IndexType;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field("email")),
    @Index(fields = @Field(value = "name", type = IndexType.TEXT))
})
public class Organization {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId parentId;
    public String email;
    public boolean emailConfirmed;
    public String createdBy;
    public String name;
    public Date timeCreated;
    public Date timeUpdated;
    public Date lastActivity;
}