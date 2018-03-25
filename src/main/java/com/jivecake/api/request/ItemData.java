package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.FormField;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;

public class ItemData {
    public Double amount;
    public List<FormField> fields;
    public Item item;
    public List<Transaction> transactions;
}