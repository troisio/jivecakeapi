package com.jivecake.api.model;

import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;

@Entity("PaymentProfile")
@Indexes({
    @Index(fields = @Field("email"))
})
public class PaypalPaymentProfile extends PaymentProfile {
    public String email;
}