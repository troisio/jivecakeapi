package com.jivecake.api.request;

import java.util.List;

import com.jivecake.api.model.FormFieldResponse;

public class PaypalAuthorizationPayload {
    public String intent;
    public String payerID;
    public String paymentID;
    public String orderID;
    public String paymentToken;
    public String returnUrl;

    public String email;
    public String firstName;
    public String lastName;
    public String organizationName;

    public List<FormFieldResponse> responses;
}