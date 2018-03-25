package com.jivecake.api.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import org.apache.commons.collections4.ListUtils;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkException;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.Claim;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.auth0.jwt.interfaces.RSAKeyProvider;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.io.Files;
import com.jivecake.api.APIConfiguration;

import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

public class Auth0Service {
    private final ObjectMapper mapper = new ObjectMapper();
    private final APIConfiguration configuration;
    private final SentryClient sentry;
    public JsonNode token = null;

    @Inject
    public Auth0Service(
        APIConfiguration configuration,
        SentryClient sentry
    ) {
        this.configuration = configuration;
        this.sentry = sentry;

        try {
            this.token = this.getNewToken();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public ManagementAPI getManagementApi() {
        return new ManagementAPI(
            this.configuration.oauth.domain,
            this.getToken().get("access_token").asText()
        );
    }

    public DecodedJWT getDecodedJWT(String token) {
        boolean isJWT = token.split("\\.").length == 3;
        DecodedJWT result = null;

        if (isJWT) {
            try {
                result = this.getAuth0Token(token);
            } catch (Exception e) {
                e.printStackTrace();
            }

            if (result == null) {
                try {
                    result = this.getServerIssusedToken(token);
                } catch (Exception serverIssusedException) {
                    serverIssusedException.printStackTrace();
                }
            }
        }

        return result;
    }

    public DecodedJWT getServerIssusedToken(String token) throws JwkException, JWTVerificationException, InvalidKeySpecException, IOException, NoSuchAlgorithmException {
        return JWT.require(this.getServerTokenAlgorithm())
            .withIssuer(this.configuration.oauth.audience)
            .withAudience(this.configuration.oauth.audience)
            .build()
            .verify(token);
    }

    public DecodedJWT getAuth0Token(String token) throws JwkException {
        DecodedJWT decoded = JWT.decode(token);
        JwkProvider jwkProvider = new UrlJwkProvider("https://" + this.configuration.oauth.domain + "/.well-known/jwks.json");

        Claim kid = decoded.getHeaderClaim("kid");

        if (kid.isNull()) {
            return null;
        }

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
            .withIssuer(String.format("https://%s/", this.configuration.oauth.domain))
            .withAudience(this.configuration.oauth.audience)
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

    public String getSignedJWT(User user) throws NoSuchAlgorithmException, InvalidKeySpecException, IOException {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.HOUR, 4);

        return JWT.create()
            .withIssuer(this.configuration.oauth.audience)
            .withSubject(user.getId())
            .withAudience(this.configuration.oauth.audience)
            .withExpiresAt(calendar.getTime())
            .withIssuedAt(new Date())
            .sign(this.getServerTokenAlgorithm());
    }

    public Algorithm getServerTokenAlgorithm() throws InvalidKeySpecException, IOException, NoSuchAlgorithmException {
        List<String> publicLines = Files.readLines(new File("resource/jwt/jwtrs256.key.pub"), StandardCharsets.UTF_8);
        byte[] publicBytes = publicLines
            .subList(1, publicLines.size() - 1)
            .stream()
            .collect(Collectors.joining(""))
            .getBytes(StandardCharsets.UTF_8);

        List<String> privateLines = Files.readLines(new File("resource/jwt/jwtrs256.key"), StandardCharsets.UTF_8);
        byte[] privateBytes = privateLines
            .subList(1, privateLines.size() - 1)
            .stream()
            .collect(Collectors.joining(""))
            .getBytes(StandardCharsets.UTF_8);

        KeyFactory factory = KeyFactory.getInstance("RSA");

        RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
            new X509EncodedKeySpec(Base64.getDecoder().decode(publicBytes))
        );

        RSAPrivateKey privateKey = (RSAPrivateKey) factory.generatePrivate(
            new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateBytes))
        );

        return Algorithm.RSA256(new RSAKeyProvider() {
            @Override
            public RSAPublicKey getPublicKeyById(String kid) {
                return publicKey;
            }

            @Override
            public RSAPrivateKey getPrivateKey() {
                return privateKey;
            }

            @Override
            public String getPrivateKeyId() {
                return null;
            }
        });
    }

    public List<User> getUsers(List<String> userIds) {
        ManagementAPI api = this.getManagementApi();

        return ListUtils.partition(userIds, 50)
            .stream()
            .map(ids -> {
                String ors = ids.stream()
                    .map(id -> String.format("\"%s\"", id))
                    .collect(Collectors.joining(" OR "));
                String query = String.format("user_id: (%s)", ors);

                List<com.auth0.json.mgmt.users.User> result;

                try {
                    result = api
                        .users()
                        .list(new UserFilter().withQuery(query))
                        .execute()
                        .getItems();
                } catch (Auth0Exception e) {
                    this.sentry.sendEvent(
                        new EventBuilder()
                            .withMessage(e.getMessage())
                            .withEnvironment(this.sentry.getEnvironment())
                            .withLevel(io.sentry.event.Event.Level.ERROR)
                            .withSentryInterface(new ExceptionInterface(e))
                            .build()
                    );
                    result = Arrays.asList();
                }

                return result;
            })
            .flatMap(List::stream)
            .collect(Collectors.toList());
    }

    public static String getGivenName(User user) {
        if (user == null) {
            return null;
        }

        if (user.getGivenName() != null) {
            return user.getGivenName();
        }

        Map<String, Object> meta = user.getUserMetadata();

        if (meta.containsKey("given_name")) {
            return (String) meta.get("given_name");
        }

        return null;
    }

    public static String getFamilyName(User user) {
        if (user == null) {
            return null;
        }

        if (user.getGivenName() != null) {
            return user.getGivenName();
        }

        Map<String, Object> meta = user.getUserMetadata();

        if (meta.containsKey("family_name")) {
            return (String) meta.get("family_name");
        }

        return null;
    }
}