package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.Item;
import com.jivecake.api.model.Transaction;

public class ItemData {
    public Item item;
    public List<Transaction> transactions;
    public Double amount;
}