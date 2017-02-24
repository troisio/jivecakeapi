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

@Indexes({
    @Index(fields = @Field("itemId")),
    @Index(fields = @Field("eventId")),
    @Index(fields = @Field("organizationId")),
    @Index(fields = @Field("user_id")),
    @Index(fields = @Field("timeCreated")),
    @Index(fields={
        @Field("given_name")
    }),
    @Index(fields={
        @Field("family_name")
    }),
    @Index(fields={
        @Field("given_name"),
        @Field("family_name")
    }),
    @Index(fields={
        @Field("email")
    })
})
@Entity
public class Transaction {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId parentTransactionId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId itemId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId eventId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;

    public String user_id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId linkedId;
    public String linkedObjectClass;
    public int status;
    public long quantity;

    public String given_name;
    public String middleName;
    public String family_name;
    public double amount;
    public String currency;
    public String email;

    public Date timeCreated;
}