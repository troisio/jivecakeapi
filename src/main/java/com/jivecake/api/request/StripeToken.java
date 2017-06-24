package com.jivecake.api.request;

import java.util.Map;

public class StripeToken {
    public String id;
    public boolean livemode;
    public String client_ip;
    public long created;
    public String email;
    public String object;
    public String type;
    public boolean used;
    public Map<String, Object> card;
}