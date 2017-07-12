package com.jivecake.api.service;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.Response;

import com.jivecake.api.APIConfiguration;

public class MandrillService {
    private final APIConfiguration apiConfiguration;

    @Inject
    public MandrillService(APIConfiguration apiConfiguration) {
        this.apiConfiguration = apiConfiguration;
    }

    public Future<Response> send(Map<String, Object> message) {
        Map<String, Object> body = new HashMap<>();
        body.put("key", this.apiConfiguration.mandrill.key);
        body.put("message", message);

        Future<Response> future;

        if (this.apiConfiguration.mandrill.mock) {
            CompletableFuture<Response> completeable = new CompletableFuture<>();
            completeable.complete(Response.ok().build());
            future = completeable;
        } else {
            future = ClientBuilder.newClient()
                .target("https://mandrillapp.com/api/1.0/messages/send.json")
                .request()
                .buildPost(Entity.json(body))
                .submit();
        }

        return future;
    }
}
