package com.jivecake.api.model;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;

@Entity
@Indexes({
    @Index(
        fields = @Field(value = "stripeId"),
        options = @IndexOptions(unique = true)
    )
})
public class StripeCharge {
    @Id
    public ObjectId id;
    public String stripeId;
}