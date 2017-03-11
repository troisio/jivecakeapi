package com.jivecake.api.model;

import java.util.Date;
import java.util.Set;

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
    @Index(fields = @Field("user_id"))
})
public class Permission {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String user_id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId objectId;

    public int include;
    public String objectClass;
    public Set<Integer> permissions;
    public Date timeCreated;
}