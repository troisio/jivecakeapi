package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.mongodb.morphia.Datastore;

import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;

@Path("payment/profile")
@CORS
@Singleton
public class PaymentProfileResource {
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;

    @Inject
    public PaymentProfileResource(
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @GET
    @Path("{id}")
    @Produces(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=PaymentProfile.class, id="id", read=true)
    public Response read(@PathObject("id") PaymentProfile profile) {
        return Response.ok(profile).build();
    }

    @DELETE
    @Authorized
    @Path("{id}")
    @HasPermission(clazz=PaymentProfile.class, id="id", write=true)
    public Response delete(@PathObject("id") PaymentProfile profile) {
        ResponseBuilder builder;

        long count = this.datastore.createQuery(Event.class)
            .field("paymentProfileId").equal(profile.id)
            .count();

        if (count == 0) {
            this.datastore.delete(PaymentProfile.class, profile.id);
            this.entityService.cascadeLastActivity(Arrays.asList(profile), new Date());
            this.notificationService.notify(Arrays.asList(profile), "paymentprofile.delete");
            builder = Response.ok(profile).type(MediaType.APPLICATION_JSON);
        } else {
            Map<String, Object> entity = new HashMap<>();
            entity.put("error", "event");
            entity.put("data", count);

            builder = Response.status(Status.BAD_REQUEST)
                .entity(entity)
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}