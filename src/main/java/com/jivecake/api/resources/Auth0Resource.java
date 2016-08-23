package com.jivecake.api.resources;

import java.io.IOException;

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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.jivecake.api.OAuthConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.request.Auth0UserUpdateEntity;

import io.dropwizard.jersey.PATCH;

@CORS
@Path("/auth0")
public class Auth0Resource {
    private final OAuthConfiguration oAuthConfiguration;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public Auth0Resource(OAuthConfiguration oAuthConfiguration) {
        this.oAuthConfiguration = oAuthConfiguration;
    }

    @POST
    @Path("/api/v2/jobs/verification-email")
    @Authorized
    public void sendVerifyEmailAddress(@Suspended AsyncResponse promise, String body) {
        WebTarget target = ClientBuilder.newClient()
            .target("https://" + this.oAuthConfiguration.domain)
            .path("/api/v2/jobs/verification-email");

        target.request()
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
    public void getUser(@PathParam("id") String id, @Context UriInfo uriInfo, @Context JsonNode claims, @Suspended AsyncResponse promise) {
        String sub = claims.get("sub").asText();

        if (sub.equals(id)) {
            ClientBuilder.newClient()
                .target("https://" + this.oAuthConfiguration.domain)
                .path("/api/v2/users/" + claims.get("sub").asText())
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
    public void searchUsers(@PathParam("id") String id, @Context JsonNode claims, @Suspended AsyncResponse promise, String body) {
        String sub = claims.get("sub").asText();

        if (sub.equals(id)) {
            ClientBuilder.newClient()
                .target("https://" + this.oAuthConfiguration.domain)
                .path("/api/v2/users/" + claims.get("sub").asText())
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
    public void updateUser(@PathParam("id") String id, @Context JsonNode claims, @Suspended AsyncResponse promise, Auth0UserUpdateEntity entity) {
        String userId = claims.get("sub").asText();

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

                    String user_id = node.get("user_id").asText();
                    boolean isNonAuth0IdentityProvider = user_id.startsWith("google") || user_id.startsWith("facebook");

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