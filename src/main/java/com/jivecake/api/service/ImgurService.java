package com.jivecake.api.service;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.MultivaluedHashMap;

import com.jivecake.api.ImgurConfiguration;

public class ImgurService {
    private final ImgurConfiguration configuration;

    @Inject
    public ImgurService(ImgurConfiguration configuration) {
        this.configuration = configuration;
    }

    public Invocation postImageRequest(MultivaluedHashMap<String, String> form) {
        Builder builder = ClientBuilder.newClient()
            .target(this.configuration.url)
            .path("/3/image")
            .request();

        if (this.configuration.mashapeKey != null) {
            builder.header("X-Mashape-Key", this.configuration.mashapeKey);
        }

        return builder.header("Authorization", "Client-ID " + this.configuration.clientId)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .header("Accept", "application/json")
            .buildPost(Entity.form(form));
    }
}