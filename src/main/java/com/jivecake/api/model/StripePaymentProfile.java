package com.jivecake.api.model;

import org.mongodb.morphia.annotations.Entity;

@Entity("PaymentProfile")
public class StripePaymentProfile extends PaymentProfile {
    public String stripe_publishable_key;
    public String stripe_user_id;
}