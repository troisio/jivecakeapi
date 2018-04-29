package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Transaction;

public class TransactionResponse {
    public Transaction transaction;
    public List<FormFieldResponse> responses;
}
