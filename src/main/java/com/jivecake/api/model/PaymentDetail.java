package com.jivecake.api.model;

import java.util.Date;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity("PaymentDetails")
@Indexes({
    @Index(fields = @Field("custom"), options = @IndexOptions(unique=true))
})
public abstract class PaymentDetail {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public String user_id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId custom;
    public Date timeCreated;
}