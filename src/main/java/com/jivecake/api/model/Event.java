package com.jivecake.api.model;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.IndexOptions;
import org.mongodb.morphia.annotations.Indexes;
import org.mongodb.morphia.utils.IndexType;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field(value = "organizationId")),
    @Index(fields = @Field(value = "hash"), options=@IndexOptions(unique=true)),
    @Index(fields = @Field(value = "name", type = IndexType.TEXT))
})
public class Event {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId organizationId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId entityAssetConsentId;

    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId paymentProfileId;

    public List<UserData> userData;
    public String currency;
    public String hash;
    public String description;
    public String name;
    public int status;
    public boolean requireName;
    public boolean requireOrganizationName;
    public boolean assignIntegerToRegistrant;
    public boolean requirePhoto;
    public String facebookEventId;
    public String twitterUrl;
    public String websiteUrl;
    public String previewImageUrl;
    public Date timeStart;
    public Date timeEnd;
    public Date timeUpdated;
    public Date timeCreated;
    public Date lastActivity;
}