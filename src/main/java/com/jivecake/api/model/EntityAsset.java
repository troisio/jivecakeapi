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
    @Index(fields = {
        @Field(value = "entityId"),
        @Field(value = "entityType")
    })
})
public class EntityAsset {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String entityId;
    public int entityType;
    public String assetId;
    public int assetType;
    public byte[] data;
    public String name;
    public Date timeCreated;
}