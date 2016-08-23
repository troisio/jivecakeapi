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
import com.jivecake.api.serializer.ObjectIdCollectionSerializer;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field("organizationId")),
    @Index(fields = @Field("parentIds")),
    @Index(fields = @Field("childIds"))
})
public class IndexedOrganizationNode {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;

    @JsonSerialize(using=ObjectIdCollectionSerializer.class)
    public List<ObjectId> parentIds;

    @JsonSerialize(using=ObjectIdCollectionSerializer.class)
    public Set<ObjectId> childIds;
    public Date timeCreated;
}