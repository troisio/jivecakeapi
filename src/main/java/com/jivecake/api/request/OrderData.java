package com.jivecake.api.request;

import java.util.List;

import org.bson.types.ObjectId;

public class OrderData {
    public List<EntityQuantity<ObjectId>> order;
    public String firstName;
    public String lastName;
    public String organizationName;
    public String email;
}