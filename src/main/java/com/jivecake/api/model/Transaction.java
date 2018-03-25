package com.jivecake.api.model;

import java.util.Date;
import java.util.HashSet;
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

@Indexes({
    @Index(fields = @Field("linkedId")),
    @Index(fields = @Field("itemId")),
    @Index(fields = @Field("eventId")),
    @Index(fields = @Field("organizationId")),
    @Index(fields = @Field("user_id")),
    @Index(fields = @Field("timeCreated"))
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

    @JsonSerialize(using=ObjectIdCollectionSerializer.class)
    public Set<ObjectId> formFieldResponseIds;

    public String user_id;
    public String linkedId;
    public String linkedObjectClass;
    public int status;
    public int paymentStatus;
    public long quantity;
    public double amount;
    public String given_name;
    public String middleName;
    public String family_name;
    public String organizationName;
    public String currency;
    public String email;
    public boolean leaf;
    public Date timeCreated;

    public Transaction() {
    }

    public Transaction(Transaction transaction) {
        this.id = transaction.id;
        this.parentTransactionId = transaction.parentTransactionId;
        this.organizationId = transaction.organizationId;
        this.eventId = transaction.eventId;
        this.itemId = transaction.itemId;
        this.user_id = transaction.user_id;
        this.linkedId = transaction.linkedId;
        this.linkedObjectClass = transaction.linkedObjectClass;
        this.status = transaction.status;
        this.paymentStatus = transaction.paymentStatus;
        this.quantity = transaction.quantity;
        this.amount = transaction.amount;
        this.given_name = transaction.given_name;
        this.middleName = transaction.middleName;
        this.family_name = transaction.family_name;
        this.organizationName = transaction.organizationName;
        this.currency = transaction.currency;
        this.email = transaction.email;
        this.leaf = transaction.leaf;
        this.timeCreated = transaction.timeCreated;
        this.formFieldResponseIds = transaction.formFieldResponseIds == null ?
                null : new HashSet<>(transaction.formFieldResponseIds);
    }
}