package com.jivecake.api.resources;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.json.mgmt.tickets.EmailVerificationTicket;
import com.auth0.json.mgmt.users.User;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.request.Auth0UserUpdateEntity;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.UserEmailVerificationBody;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.CronService;

import io.dropwizard.jersey.PATCH;

@CORS
@Path("auth0")
@Singleton
public class Auth0Resource {
    private final APIConfiguration configuration;
    private final Map<String, Date> lastEmailTime = new HashMap<>();
    private final long emailLimitTime = 1000 * 60 * 5;
    private final Auth0Service auth0Service;

    @Inject
    public Auth0Resource(
        APIConfiguration configuration,
        Auth0Service auth0Service,
        CronService cronService
    ) {
        this.configuration = configuration;
        this.auth0Service = auth0Service;

        /*
         * This does not belong here
         * Temporary until we find a way to construct objects with
         * dependency injection on application start
         * */
        cronService.start();
    }

    @GET
    @Path("api/v2/users/{id}")
    @Authorized
    public Response getUser(
        @PathParam("id") String id,
        @Context DecodedJWT jwt
    ) throws Auth0Exception {
        boolean authorized = jwt.getSubject().equals(id);

        ResponseBuilder builder;

        if (authorized) {
            ManagementAPI api = new ManagementAPI(
                this.configuration.oauth.domain,
                this.auth0Service.getToken().get("access_token").asText()
            );

            User user = api.users().get(id, new UserFilter()).execute();
            builder = Response.ok(user, MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("api/v2/jobs/verification-email")
    @Authorized
    public Response sendVerifyEmailAddress(
        @Context DecodedJWT jwt,
        @ValidEntity UserEmailVerificationBody body
    ) throws Auth0Exception {
        String user_id = jwt.getSubject();

        ResponseBuilder builder;

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
                ErrorData entity = new ErrorData();
                entity.error = "emailLimitTime";
                entity.data = this.emailLimitTime;
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                ManagementAPI managementApi = new ManagementAPI(
                    configuration.oauth.domain,
                    this.auth0Service.getToken().get("access_token").asText()
                );

                EmailVerificationTicket ticket = new EmailVerificationTicket(body.user_id);
                managementApi.tickets().requestEmailVerification(ticket).execute();

                this.lastEmailTime.put(body.user_id, new Date());

                builder = Response.ok();
            }
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @PATCH
    @Path("api/v2/users/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response updateUser(
        @PathParam("id") String id,
        @Context DecodedJWT jwt,
        Auth0UserUpdateEntity entity
    ) throws Auth0Exception {
        ResponseBuilder builder;

        if (jwt.getSubject().equals(id)) {
            ManagementAPI managementApi = new ManagementAPI(
                this.configuration.oauth.domain,
                this.auth0Service.getToken().get("access_token").asText()
            );

            User user = managementApi.users().get(id, new UserFilter()).execute();

            boolean emailChange = !Objects.equals(user.getEmail(), entity.email);

            if (emailChange) {
                boolean emailAvailible = managementApi.users()
                    .list( new UserFilter().withQuery("email: \""+ user.getEmail() +"\""))
                    .execute()
                    .getItems()
                    .isEmpty();

                if (!emailAvailible) {
                    return Response.status(Status.CONFLICT).build();
                }

                user.setEmailVerified(false);
            }

            Map<String, Object> metaData = new HashMap<>();
            metaData.put("given_name", entity.user_metadata.given_name);
            metaData.put("family_name", entity.user_metadata.family_name);

            User updateUser = new User();
            updateUser.setEmail(entity.email);
            updateUser.setUserMetadata(metaData);

            User userAferUpdate = managementApi.users().update(id, updateUser).execute();

            if (emailChange) {
                EmailVerificationTicket ticket = new EmailVerificationTicket(id);
                managementApi.tickets().requestEmailVerification(ticket).execute();
            }

            builder = Response.ok(userAferUpdate, MediaType.APPLICATION_JSON);;
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }
}