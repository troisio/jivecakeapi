package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@Path("/event")
@CORS
@Singleton
@GZip
public class EventResource {
    private final EventService eventService;
    private final ItemService itemService;
    private final PermissionService permissionService;
    private final TransactionService transactionService;
    private final StripeService stripeService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;

    @Inject
    public EventResource(
        EventService eventService,
        ItemService itemService,
        PermissionService permissionService,
        TransactionService transactionService,
        StripeService stripeService,
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore
    ) {
        this.itemService = itemService;
        this.eventService = eventService;
        this.permissionService = permissionService;
        this.transactionService = transactionService;
        this.stripeService = stripeService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
    }

    @GET
    @Path("/{eventId}/aggregated")
    public Response getAggregatedItemData(@PathObject("eventId") Event event, @Context DecodedJWT jwt) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (event.status == EventService.STATUS_ACTIVE) {
            AggregatedEvent group = this.itemService.getAggregatedaEventData(
                event,
                this.transactionService,
                new Date()
            );

            group.itemData = group.itemData.stream()
                .filter(itemData -> itemData.item.status == ItemService.STATUS_ACTIVE)
                .collect(Collectors.toList());

            for (ItemData datum: group.itemData) {
                for (Transaction transaction: datum.transactions) {
                    if (jwt != null) {
                        if (!jwt.getSubject().equals(transaction.user_id)) {
                            transaction.user_id = null;
                        }
                    }

                    transaction.given_name = null;
                    transaction.middleName = null;
                    transaction.family_name = null;
                }
            }

            builder = Response.ok(group).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST)
                .encoding("{\"error\": \"status\"}")
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @GET
    @Path("/search")
    public Response publicSearch(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("timeStartGreaterThan") Long timeStartGreaterThan,
        @QueryParam("timeStartLessThan") Long timeStartLessThan,
        @QueryParam("timeEndLessThan") Long timeEndLessThan,
        @QueryParam("timeEndGreaterThan") Long timeEndGreaterThan,
        @QueryParam("timeCreatedLessThan") Long timeCreatedLessThan,
        @QueryParam("timeCreatedGreaterThan") Long timeCreatedGreaterThan,
        @QueryParam("order") String order,
        @QueryParam("text") String text
    ) {
        Query<Event> query = this.datastore.createQuery(Event.class)
            .field("status").equal(EventService.STATUS_ACTIVE);

        if (text != null && !text.isEmpty()) {
            List<ObjectId> organizationIds = this.datastore.createQuery(Organization.class)
                .project("id", true)
                .field("name").containsIgnoreCase(text)
                .asList()
                .stream()
                .map(organization -> organization.id)
                .collect(Collectors.toList());

            query.and(
                query.or(
                    query.criteria("organizationId").in(organizationIds),
                    query.criteria("name").containsIgnoreCase(text)
                )
            );
        }

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (timeStartLessThan != null) {
            query.field("timeStart").lessThan(new Date(timeStartLessThan));
        }

        if (timeStartGreaterThan != null) {
            query.field("timeStart").greaterThan(new Date(timeStartGreaterThan));
        }

        if (timeEndLessThan != null) {
            query.field("timeEnd").lessThan(new Date(timeEndLessThan));
        }

        if (timeEndGreaterThan != null) {
            query.field("timeEnd").greaterThan(new Date(timeEndGreaterThan));
        }

        if (timeCreatedGreaterThan != null) {
            query.field("timeCreated").greaterThan(new Date(timeCreatedGreaterThan));
        }

        if (timeCreatedLessThan != null) {
            query.field("timeCreated").lessThan(new Date(timeCreatedLessThan));
        }

        if (order != null) {
            query.order(order);
        }

        FindOptions options = new FindOptions();
        options.limit(ApplicationService.LIMIT_DEFAULT);

        Paging<Event> entity = new Paging<>(query.asList(options), query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Event.class, id="id", permission=PermissionService.WRITE)
    public Response update(
        @PathObject("id") Event original,
        Event event
    ) {
        ResponseBuilder builder;

        boolean isValid = this.eventService.isValidEvent(event);

        if (isValid) {
            long activeEventsCount = this.datastore.createQuery(Event.class)
                .field("id").notEqual(original.id)
                .field("status").equal(EventService.STATUS_ACTIVE)
                .field("organizationId").equal(original.organizationId)
                .count();

            boolean hasSubscriptionViolation;
            StripeException stripeException = null;
            List<Subscription> currentSubscriptions = null;

            boolean hasValidPaymentProfile = event.paymentProfileId == null ||
                this.datastore.createQuery(PaymentProfile.class)
                    .field("id").equal(event.paymentProfileId)
                    .count() > 0;

            if (event.status == EventService.STATUS_ACTIVE) {
                activeEventsCount++;

                try {
                    currentSubscriptions = this.stripeService.getCurrentSubscriptions(original.organizationId);
                } catch (StripeException e) {
                    stripeException = e;
                }

                hasSubscriptionViolation = currentSubscriptions != null && activeEventsCount > currentSubscriptions.size();
            } else {
                hasSubscriptionViolation = false;
            }

            if (!hasValidPaymentProfile) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"paymentProfileId\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else if (stripeException != null) {
                stripeException.printStackTrace();
                builder = Response.status(Status.SERVICE_UNAVAILABLE);
            } else if (hasSubscriptionViolation) {
                Map<String, Object> entity = new HashMap<>();
                entity.put("error", "subscription");
                entity.put("data", currentSubscriptions);

                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                Date currentTime = new Date();

                event.id = original.id;
                event.organizationId = original.organizationId;
                event.timeCreated = original.timeCreated;
                event.timeUpdated = currentTime;
                event.lastActivity = currentTime;

                Key<Event> key = this.datastore.save(event);

                Event searchedEvent = this.datastore.get(Event.class, key.getId());
                this.notificationService.notify(Arrays.asList(searchedEvent), "event.update");
                this.entityService.cascadeLastActivity(Arrays.asList(searchedEvent), currentTime);

                builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Path("/{id}/item")
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    @HasPermission(clazz=Event.class, id="id", permission=PermissionService.WRITE)
    public Response createItem(@PathObject("id") Event event, Item item) {
        ResponseBuilder builder;

        boolean isValid = this.itemService.isValid(item);

        if (isValid) {
            Date currentTime = new Date();

            item.eventId = event.id;
            item.organizationId = event.organizationId;
            item.timeCreated = currentTime;
            item.timeUpdated = null;
            item.lastActivity = currentTime;

            Key<Item> key = this.datastore.save(item);
            Item searchedItem = this.datastore.get(Item.class, key.getId());

            this.notificationService.notify(Arrays.asList(searchedItem), "item.create");
            this.entityService.cascadeLastActivity(Arrays.asList(item), currentTime);

            builder = Response.ok(searchedItem).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Event.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Event event) {
        long itemCount = this.datastore.createQuery(Item.class)
            .field("eventId").equal(event.id)
            .count();

        ResponseBuilder builder;

        if (itemCount == 0) {
            this.datastore.delete(Event.class, event.id);

            this.notificationService.notify(Arrays.asList(event), "event.delete");
            builder = Response.ok(event).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"itemcount\"}")
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("organizationId") List<ObjectId> organizationIds,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("status") List<Integer> statuses,
        @QueryParam("name") String name,
        @QueryParam("timeStartBefore") Long timeStartBefore,
        @QueryParam("timeStartAfter") Long timeStartAfter,
        @QueryParam("timeEndBefore") Long timeEndBefore,
        @QueryParam("timeEndAfter") Long timeEndAfter,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @QueryParam("order") String order,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        Query<Event> query = this.datastore.createQuery(Event.class);

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!organizationIds.isEmpty()) {
            query.field("organizationId").in(organizationIds);
        }

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        if (!statuses.isEmpty()) {
            query.field("status").in(statuses);
        }

        if (timeStartBefore != null) {
            query.field("timeStart").lessThan(new Date(timeStartBefore));
        }

        if (timeStartAfter != null) {
            query.field("timeStart").greaterThan(new Date(timeStartAfter));
        }

        if (timeEndBefore != null) {
            query.field("timeEnd").lessThan(new Date(timeEndBefore));
        }

        if (timeEndAfter != null) {
            query.field("timeEnd").greaterThan(new Date(timeEndAfter));
        }

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        if (order != null) {
            query.order(order);
        }

        List<Event> entities = query.asList(options);

        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            entities,
            PermissionService.READ
        );

        if (hasPermission) {
            Paging<Event> entity = new Paging<>(entities, query.count());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Path("/{id}")
    @Authorized
    public Response read(@PathObject("id") Event event) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            builder = Response.ok(event).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }
}