package com.jivecake.api.model;

import java.util.Date;
import java.util.List;

import org.bson.types.ObjectId;
import org.mongodb.morphia.annotations.Entity;
import org.mongodb.morphia.annotations.Field;
import org.mongodb.morphia.annotations.Id;
import org.mongodb.morphia.annotations.Index;
import org.mongodb.morphia.annotations.Indexes;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.jivecake.api.serializer.ObjectIdSerializer;

@Entity
@Indexes({
    @Index(fields = @Field("txn_id")),
    @Index(fields = @Field("timeCreated"))
})
public class PaypalIPN {
    @Id
    @JsonSerialize(using=ObjectIdSerializer.class)
    public ObjectId id;
    public List<PaypalItemPayment> payments;
    public String num_cart_items;
    public String txn_id;
    public String parent_txn_id;
    public String payer_email;
    public String receiver_email;
    public String business;
    public String item_number;
    public String mc_gross;
    public String mc_gross1;
    public String protection_eligibility;
    public String payer_id;
    public String query_track_id;
    public String payment_date;
    public String payment_status;
    public String pending_reason;
    public String reason_code;;
    public String charset;
    public String first_name;
    public String address_name;
    public String address_street;
    public String address_city;
    public String address_state;
    public String address_zip;
    public String address_country;
    public String address_country_code;
    public String address_status;
    public String mc_fee;
    public String mc_handling;
    public String mc_handling1;
    public String mc_shipping;
    public String mc_shipping1;
    public String invoice;
    public String tax;
    public String notify_version;
    public String custom;
    public String payer_status;
    public String payer_business_name;
    public String quantity;
    public String verify_sign;
    public String payment_type;
    public String btn_id;
    public String last_name;
    public String payment_fee;
    public String shipping_discount;
    public String insurance_amount;
    public String receiver_id;
    public String txn_type;
    public String item_name;
    public String item_name1;
    public String quantity1;
    public String item_number1;
    public String discount;
    public String mc_currency;
    public String residence_country;
    public String handling_amount;
    public String shipping_method;
    public String transaction_subject;
    public String payment_gross;
    public String shipping;
    public String ipn_track_id;
    public String test_ipn;
    public String auction_buyer_id;
    public String auction_closing_date;
    public String for_auction;
    public String receipt_id;

    /*Multi-currency fields*/
    public String settle_amount;
    public String settle_currency;
    public String exchange_rate;

    /*Subscription fields*/
    public String subscr_date;
    public String amount1;
    public String amount2;
    public String amount3;
    public String period1;
    public String period2;
    public String period3;
    public String recurring;
    public String mc_amount1;
    public String mc_amount2;
    public String mc_amount3;
    public String reattempt;
    public String subscr_id;
    public String retry_at;
    public String recur_times;
    public String username;
    public String password;

    /*Refund fields*/
    public String echeck_time_processed;

    public Date timeCreated;
}