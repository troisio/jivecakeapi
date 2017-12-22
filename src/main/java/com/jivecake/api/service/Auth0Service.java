package com.jivecake.api.service;

import java.io.IOException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivecake.api.APIConfiguration;

public class Auth0Service {
    private final ObjectMapper mapper = new ObjectMapper();
    private final APIConfiguration configuration;
    private final ApplicationService applicationService;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();
    private JsonNode token = null;

    @Inject
    public Auth0Service(
            APIConfiguration configuration,
        ApplicationService applicationService
    ) {
        this.configuration = configuration;
        this.applicationService = applicationService;

        this.executor.scheduleAtFixedRate(() -> {
            try {
                this.token = this.getNewToken();
            } catch (IOException e) {
                this.applicationService.saveException(e, null);
            }
        }, 0, 1, TimeUnit.HOURS);
    }

    public DecodedJWT getClaimsFromToken(String token) throws JwkException {
        DecodedJWT decoded = JWT.decode(token);
        JwkProvider jwkProvider = new UrlJwkProvider("https://" + this.configuration.oauth.domain + "/.well-known/jwks.json");
        Jwk jwk = jwkProvider.get(decoded.getHeaderClaim("kid").asString());
        RSAPublicKey key = (RSAPublicKey)jwk.getPublicKey();

        RSAKeyProvider provider = new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String kid) {
                return key;
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return null;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        };

        return JWT.require(Algorithm.RSA256(provider))
            .withIssuer(String.format("https://%s/", "jivecake.auth0.com"))
            .build()
            .verify(token);
    }

    public JsonNode getToken() {
        return this.token;
    }

    public JsonNode getNewToken() throws IOException {
        ObjectNode node = this.mapper.createObjectNode();
        node.put("grant_type", "client_credentials");
        node.put("client_id", this.configuration.oauth.nonInteractiveClientId);
        node.put("client_secret", this.configuration.oauth.nonInteractiveSecret);
        node.put("audience", "https://" +  this.configuration.oauth.domain + "/api/v2/");

        String entity = ClientBuilder.newClient()
            .target("https://" + this.configuration.oauth.domain + "/oauth/token")
            .request()
            .buildPost(Entity.entity(node, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }
}