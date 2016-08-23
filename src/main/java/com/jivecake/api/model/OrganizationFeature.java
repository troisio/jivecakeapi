package com.jivecake.api.model;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity("Feature")
@Indexes({
    @Index(fields = {
        @Field("organizationId"),
        @Field("type")
    })
})
public class OrganizationFeature extends Feature {
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;
}