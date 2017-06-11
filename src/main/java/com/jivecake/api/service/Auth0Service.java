package com.jivecake.api.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Invocation;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.OAuthConfiguration;

public class Auth0Service {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JWTVerifier> verifiers;
    private final OAuthConfiguration oAuthConfiguration;

    @Inject
    public Auth0Service(OAuthConfiguration oAuthConfiguration, List<JWTVerifier> verifiers) {
        this.oAuthConfiguration = oAuthConfiguration;
        this.verifiers = verifiers;
    }

    public DecodedJWT getClaimsFromToken(String token) {
        DecodedJWT result = null;

        for (JWTVerifier verifier: verifiers) {
            try {
                result = verifier.verify(token);
                break;
            } catch (JWTVerificationException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public Invocation getUser(String id) {
        return ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users/" + id)
             .request()
            .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
            .buildGet();
    }

    public Future<Response> queryUsers(String query, InvocationCallback<Response> callback) {
        WebTarget target = ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users");

        MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle("q", query);
        parameters.putSingle("search_engine","v2");

        for (String key: parameters.keySet()) {
            target = target.queryParam(key, parameters.get(key).toArray());
        }

        return target.request()
            .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
            .buildGet()
            .submit(callback);
    }

    public CompletableFuture<JsonNode> searchEmailOrNames(String text) {
        WebTarget target = ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users");

        String luceneQuery = String.format(
            "user_metadata.given_name: %s* OR user_metadata.family_name: %s* OR given_name: %s* OR family_name: %s* OR email: %s* OR name: %s*",
            text,
            text,
            text,
            text,
            text,
            text
        );

        MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle("q", luceneQuery);
        parameters.putSingle("search_engine","v2");

        for (String key: parameters.keySet()) {
            target = target.queryParam(key, parameters.get(key).toArray());
        }

        CompletableFuture<JsonNode> future = new CompletableFuture<>();

        target.request()
            .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
            .buildGet()
            .submit(new InvocationCallback<Response>() {
                @Override
                public void completed(Response response) {
                    String body = response.readEntity(String.class);

                    try {
                        JsonNode array = Auth0Service.this.mapper.readTree(body);
                        future.complete(array);
                    } catch (IOException e) {
                        future.completeExceptionally(e);
                    }
                }

                @Override
                public void failed(Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });

        return future;
    }
}