package com.jivecake.api.resources;

import java.io.IOException;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.glassfish.jersey.client.HttpUrlConnectorProvider;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.OAuthConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.request.Auth0UserUpdateEntity;
import com.jivecake.api.request.UserEmailVerificationBody;

import io.dropwizard.jersey.PATCH;

@CORS
@Path("/auth0")
@Singleton
public class Auth0Resource {
    private final OAuthConfiguration oAuthConfiguration;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, Date> lastEmailTime = new HashMap<>();
    private final long emailLimitTime = 1000 * 60 * 5;

    @Inject
    public Auth0Resource(OAuthConfiguration oAuthConfiguration) {
        this.oAuthConfiguration = oAuthConfiguration;
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/api/v2/jobs/verification-email")
    @Authorized
    public void sendVerifyEmailAddress(
        @Context DecodedJWT jwt,
        @Suspended AsyncResponse promise,
        UserEmailVerificationBody body
    ) {
        String user_id = jwt.getSubject();

        if (user_id.equals(body.user_id)) {
            boolean emailTimeViolation;

            Date currentTime = new Date();
            Date lastEmailTime = this.lastEmailTime.get(user_id);

            if (lastEmailTime == null) {
                emailTimeViolation = false;
            } else {
                emailTimeViolation = currentTime.getTime() - lastEmailTime.getTime() < this.emailLimitTime;
            }

            if (emailTimeViolation) {
                String responseBody = String.format("{\"error\": \"emailLimitTime\", \"data\": %d}", lastEmailTime.getTime() + this.emailLimitTime);
                Response response = Response.status(Status.BAD_REQUEST)
                    .entity(responseBody)
                    .type(MediaType.APPLICATION_JSON)
                    .build();

                promise.resume(response);
            } else {
                ClientBuilder.newClient()
                    .target("https://" + this.oAuthConfiguration.domain)
                    .path("/api/v2/jobs/verification-email")
                    .request()
                    .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
                    .buildPost(Entity.json(body))
                    .submit(new InvocationCallback<Response>() {
                        @Override
                        public void completed(Response response) {
                            if (response.getStatus() == 201) {
                                Auth0Resource.this.lastEmailTime.put(user_id, new Date());
                            }

                            promise.resume(response);
                        }

                        @Override
                        public void failed(Throwable throwable) {
                            promise.resume(throwable);
                        }
                    });
                }
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }

    @GET
    @Path("/api/v2/users")
    @Authorized
    public void searchUsers(@Context UriInfo info, @Suspended AsyncResponse promise) {
        MultivaluedMap<String, String> parameters = new MultivaluedHashMap<>();
        parameters.putAll(info.getQueryParameters());

        WebTarget target = ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users");

        parameters.putSingle("search_engine","v2");
        parameters.putSingle("include_fields", "false");
        parameters.putSingle("fields", "email,identities,last_ip");

        for (String key: parameters.keySet()) {
            target = target.queryParam(key, parameters.get(key).toArray());
        }

        target.request()
            .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
            .buildGet()
            .submit(new InvocationCallback<Response>() {
                @Override
                public void completed(Response response) {
                    promise.resume(response);
                }

                @Override
                public void failed(Throwable throwable) {
                    promise.resume(throwable);
                }
            });
    }

    @GET
    @Path("/api/v2/users/{id}")
    @Authorized
    public void getUser(
        @PathParam("id") String id,
        @Context UriInfo uriInfo,
        @Context DecodedJWT jwt,
        @Suspended AsyncResponse promise
    ) {
        String sub = jwt.getSubject();

        if (sub.equals(id)) {
            ClientBuilder.newClient()
                .target("https://" + this.oAuthConfiguration.domain)
                .path("/api/v2/users/" + sub)
                .request()
                .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
                .buildGet()
                .submit(new InvocationCallback<Response>() {
                    @Override
                    public void completed(Response response) {
                        promise.resume(response);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        promise.resume(throwable);
                    }
                });
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }

    @POST
    @Path("/api/v2/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public void searchUsers(@PathParam("id") String id, @Context DecodedJWT jwt, @Suspended AsyncResponse promise, String body) {
        String sub = jwt.getSubject();

        if (sub.equals(id)) {
            ClientBuilder.newClient()
                .target("https://" + this.oAuthConfiguration.domain)
                .path("/api/v2/users/" + sub)
                .request()
                .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
                .buildPost(Entity.json(body))
                .submit(new InvocationCallback<Response>() {
                    @Override
                    public void completed(Response response) {
                        promise.resume(response);
                    }

                    @Override
                    public void failed(Throwable throwable) {
                        promise.resume(throwable);
                    }
                });
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }

    @PATCH
    @Path("/api/v2/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public void updateUser(@PathParam("id") String id, @Context DecodedJWT jwt, @Suspended AsyncResponse promise, Auth0UserUpdateEntity entity) {
        String userId = jwt.getSubject();

        if (userId.equals(id)) {
            ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/users/" + id)
            .request()
            .header("Authorization", "Bearer " + this.oAuthConfiguration.apiToken)
            .buildGet()
            .submit(new InvocationCallback<Response>() {
                @Override
                public void completed(Response response) {
                    JsonNode node;

                    try {
                        node = mapper.readTree(response.readEntity(String.class));
                    } catch (IOException e) {
                        node = null;
                    }

                    String userId = node.get("user_id").asText();
                    boolean isNonAuth0IdentityProvider = userId.startsWith("google") || userId.startsWith("facebook");

                    if (isNonAuth0IdentityProvider) {
                        promise.resume(Response.status(Status.BAD_REQUEST).build());
                    } else {
                        ClientBuilder.newClient()
                        .property(HttpUrlConnectorProvider.SET_METHOD_WORKAROUND, true) /*PATCH not supported as of 9 May 2016*/
                        .target("https://" + Auth0Resource.this.oAuthConfiguration.domain)
                        .path("/api/v2/users/" + id)
                        .request()
                        .header("Authorization", "Bearer " + Auth0Resource.this.oAuthConfiguration.apiToken)
                        .build("PATCH", Entity.entity(entity, MediaType.APPLICATION_JSON))
                        .submit(new InvocationCallback<Response>() {
                            @Override
                            public void completed(Response response) {
                                promise.resume(response);
                            }

                            @Override
                            public void failed(Throwable throwable) {
                                promise.resume(throwable);
                            }
                        });
                    }
                }

                @Override
                public void failed(Throwable throwable) {
                    promise.resume(throwable);
                }
            });
        } else {
            promise.resume(Response.status(Status.UNAUTHORIZED).build());
        }
    }
}