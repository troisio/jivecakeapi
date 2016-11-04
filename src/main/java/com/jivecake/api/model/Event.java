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
    @Index(fields = @Field(value = "organizationId")),
    @Index(fields = @Field(value = "name", type = IndexType.TEXT))
})
public class Event {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId paymentProfileId;
    public String currency;
    public String description;
    public String name;
    public long minimumTimeBetweenTransactionTransfer;
    public int status;
    public Date timeStart;
    public Date timeEnd;
    public Date timeUpdated;
    public Date timeCreated;
}