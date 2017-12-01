package com.jivecake.api.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivecake.api.OAuthConfiguration;

public class Auth0Service {
    private final ObjectMapper mapper = new ObjectMapper();
    private final List<JWTVerifier> verifiers;
    private final OAuthConfiguration oAuthConfiguration;
    private final ApplicationService applicationService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private JsonNode token = null;

    @Inject
    public Auth0Service(
        OAuthConfiguration oAuthConfiguration,
        List<JWTVerifier> verifiers,
        ApplicationService applicationService
    ) {
        this.oAuthConfiguration = oAuthConfiguration;
        this.verifiers = verifiers;
        this.applicationService = applicationService;

        this.executor.scheduleAtFixedRate(() -> {
            try {
                this.token = this.getNewToken();
            } catch (IOException e) {
                this.applicationService.saveException(e, null);
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    public DecodedJWT getClaimsFromToken(String token) {
        DecodedJWT result = null;

        for (JWTVerifier verifier: verifiers) {
            try {
                result = verifier.verify(token);
                break;
            } catch (JWTVerificationException e) {
            }
        }

        return result;
    }

    public JsonNode getToken() {
        return this.token;
    }

    public JsonNode getNewToken() throws IOException {
        ObjectNode node = this.mapper.createObjectNode();
        node.put("grant_type", "client_credentials");
        node.put("client_id", this.oAuthConfiguration.nonInteractiveClientId);
        node.put("client_secret", this.oAuthConfiguration.nonInteractiveSecret);
        node.put("audience", "https://" + this.oAuthConfiguration.domain + "/api/v2/");

        String entity = ClientBuilder.newClient()
            .target("https://jivecake.auth0.com/oauth/token")
            .request()
            .buildPost(Entity.entity(node, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }
}