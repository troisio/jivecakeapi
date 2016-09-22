package com.jivecake.api;

import java.util.List;

import io.dropwizard.Configuration;

public class APIConfiguration extends Configuration {
    public List<String> databases;
    public OAuthConfiguration oauth;
    public List<String> rootOAuthIds;
}