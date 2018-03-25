package com.jivecake.api.request;

import java.util.List;

import org.bson.types.ObjectId;

import com.jivecake.api.model.FormFieldResponse;

public class OrderData {
    public List<EntityQuantity<ObjectId>> order;
    public List<FormFieldResponse> responses;
    public String firstName;
    public String lastName;
    public String organizationName;
    public String email;
}