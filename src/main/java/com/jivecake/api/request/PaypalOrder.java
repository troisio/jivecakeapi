package com.jivecake.api.request;

import java.util.List;

import org.bson.types.ObjectId;

public class PaypalOrder{
    public List<EntityQuantity<ObjectId>> itemData;
}