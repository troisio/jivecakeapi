package com.jivecake.api.service;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

import com.auth0.json.mgmt.users.User;
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

    /*
     * This Auth0 ManagementAPI class already has this functionality
     * Unfortunately, due to a transitive dependency (jackson) between
     *
     * 'io.dropwizard:dropwizard-client:1.2.0',
        'io.dropwizard:dropwizard-core:1.2.0'
        and
        'com.auth0:auth0:1.3.0'

        the project breaks when using this API method with the Auth0 ManagementAPI
        So, until both projects run on compatible versions or someone takes the take to proplerly
        resolve the dependency on the classpath with gradle this method is used
     */
    public User[] queryUsers(String query) throws IOException {
        WebTarget target = ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users");

        MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putSingle("q", query);
        parameters.putSingle("search_engine","v2");

        for (String key: parameters.keySet()) {
            target = target.queryParam(key, parameters.get(key).toArray());
        }

        String body = target.request()
            .header("Authorization", "Bearer " + this.token.get("access_token").asText())
            .buildGet()
            .invoke(String.class);

        return this.mapper.readValue(body, User[].class);
    }
}