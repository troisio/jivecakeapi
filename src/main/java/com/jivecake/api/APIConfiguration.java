package com.jivecake.api;

import java.util.List;

import io.dropwizard.Configuration;

public class APIConfiguration extends Configuration {
    public FaceBookConfiguration facebook;
    public List<String> rootOAuthIds;
    public List<String> errorRecipients;
    public OAuthConfiguration oauth;
    public GCPConfiguration gcp;
    public List<String> databases;
    public StripeConfiguration stripe;
    public List<String> corsOrigins;
    public PaypalConfiguration paypal;
    public MandrillConfiguration mandrill;
    public SentryConfiguration sentry;
}