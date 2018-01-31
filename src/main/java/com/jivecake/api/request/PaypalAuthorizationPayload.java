package com.jivecake.api.request;

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
}