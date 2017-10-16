package com.jivecake.api.model;

import java.util.Date;
import java.util.List;
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
    @Index(fields = @Field("userIds")),
    @Index(fields = @Field("organizationId"))
})
public class OrganizationInvitation {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;
    public Set<Integer> permissions;
    public int include;
    public String email;
    public List<String> userIds;
    public Date timeCreated;
    public Date timeAccepted;
}