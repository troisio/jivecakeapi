package com.jivecake.api.model;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Id;

@Entity
public class OrganizationInvitation {
    @Id
    public ObjectId id;
    public ObjectId organizationId;
    public Set<Integer> permissions;
    public int include;
    public String email;
    public List<String> userIds;
    public Date timeCreated;
}