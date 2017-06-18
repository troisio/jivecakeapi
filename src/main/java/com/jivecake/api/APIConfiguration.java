package com.jivecake.api;

import java.util.List;

import io.dropwizard.Configuration;

public class APIConfiguration extends Configuration {
    public List<String> rootOAuthIds;
    public OAuthConfiguration oauth;
    public GCPConfiguration gcp;
    public List<String> databases;
    public StripeConfiguration stripe;
    public List<String> corsOrigins;
}