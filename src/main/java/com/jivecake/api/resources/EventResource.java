package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.AggregatedItemGroup;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@Path("/event")
@CORS
public class EventResource {
    private final EventService eventService;
    private final ItemService itemService;
    private final PermissionService permissionService;
    private final TransactionService transactionService;
    private final StripeService stripeService;
    private final EntityService entityService;
    private final Datastore datastore;

    @Inject
    public EventResource(
        EventService eventService,
        ItemService itemService,
        PermissionService permissionService,
        TransactionService transactionService,
        StripeService stripeService,
        EntityService entityService,
        Datastore datastore
    ) {
        this.itemService = itemService;
        this.eventService = eventService;
        this.permissionService = permissionService;
        this.transactionService = transactionService;
        this.stripeService = stripeService;
        this.entityService = entityService;
        this.datastore = datastore;
    }

    @GET
    @Path("/{eventId}/aggregated")
    public Response getAggregatedItemData(@PathObject("eventId") Event event, @Context JsonNode node) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (event.status == this.eventService.getActiveEventStatus()) {
            AggregatedItemGroup group = this.itemService.getAggregatedaGroupData(
                event,
                this.transactionService,
                new Date()
            );

            group.itemData = group.itemData.stream()
                .filter(itemData -> itemData.item.status == this.itemService.getActiveItemStatus())
                .collect(Collectors.toList());

            for (ItemData datum: group.itemData) {
                for (Transaction transaction: datum.transactions) {
                    if (node != null) {
                        String sub = node.get("sub").asText();

                        if (!sub.equals(transaction.user_id)) {
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
    public Response search(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("timeStartBefore") Long timeStartBefore,
        @QueryParam("timeStartAfter") Long timeStartAfter,
        @QueryParam("timeEndBefore") Long timeEndBefore,
        @QueryParam("timeEndAfter") Long timeEndAfter,
        @QueryParam("timeCreatedAfter") Long timeCreatedAfter,
        @QueryParam("timeCreatedBefore") Long timeCreatedBefore,
        @QueryParam("order") String order,
        @QueryParam("text") String text
    ) {
        FindOptions options = new FindOptions();
        options.limit(1000);

        Query<Event> query = this.datastore.createQuery(Event.class)
            .field("status").equal(this.eventService.getActiveEventStatus());

        if (text != null && !text.isEmpty()) {
            List<ObjectId> organizationIds = this.datastore.createQuery(Organization.class)
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

        if (timeCreatedAfter != null) {
            query.field("timeCreated").greaterThan(new Date(timeCreatedAfter));
        }

        if (timeCreatedBefore != null) {
            query.field("timeCreated").lessThan(new Date(timeCreatedBefore));
        }

        if (order != null) {
            query.order(order);
        }

        Paging<Event> entity = new Paging<>(query.asList(options), query.count());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @POST
    @Path("/{id}")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Event.class, id="id", permission=PermissionService.WRITE)
    public Response update(
        @PathObject("id") Event original,
        @Context JsonNode claims,
        Event event
    ) {
        ResponseBuilder builder;

        long activeEventsCount = this.datastore.createQuery(Event.class)
            .field("id").notEqual(original.id)
            .field("status").equal(this.eventService.getActiveEventStatus())
            .field("organizationId").equal(original.organizationId)
            .count();

        boolean hasSubscriptionViolation;
        StripeException stripeException = null;
        List<Subscription> currentSubscriptions = null;

        if (event.status == this.eventService.getActiveEventStatus()) {
            activeEventsCount++;

            try {
                currentSubscriptions = this.stripeService.getCurrentSubscriptions(original.organizationId);
            } catch (StripeException e) {
                stripeException = e;
            }

            hasSubscriptionViolation = currentSubscriptions != null &&
                activeEventsCount > currentSubscriptions.size() &&
                event.status == this.eventService.getActiveEventStatus();
        } else {
            hasSubscriptionViolation = false;
        }

        boolean hasPaymentProfileViolation = (event.paymentProfileId == null && event.currency != null) ||
                                             (event.paymentProfileId != null && event.currency == null);

        if (stripeException != null) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE)
                .entity(stripeException);
        }  else if (hasPaymentProfileViolation) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"paymentProfile\"}")
                .type(MediaType.APPLICATION_JSON);
        } else if (hasSubscriptionViolation) {
            builder = Response.status(Status.BAD_REQUEST)
                    .entity(currentSubscriptions)
                    .type(MediaType.APPLICATION_JSON);
        } else {
            Date currentTime = new Date();

            event.organizationId = original.organizationId;
            event.id = original.id;
            event.timeCreated = original.timeCreated;
            event.timeUpdated = currentTime;
            event.lastActivity = currentTime;

            Key<Event> key = this.datastore.save(event);
            this.entityService.cascadeLastActivity(Arrays.asList(event), currentTime);

            Event searchedEvent = this.datastore.get(Event.class, key.getId());
            builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @POST
    @Path("/{id}/item")
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    @HasPermission(clazz=Event.class, id="id", permission=PermissionService.WRITE)
    public Response createItem(@PathObject("id") Event event, Item item, @Context JsonNode claims) {
        ResponseBuilder builder;

        boolean isValid = this.itemService.isValid(item);

        if (isValid) {
            Date currentTime = new Date();

            item.eventId = event.id;
            item.organizationId = event.organizationId;
            item.timeCreated = currentTime;
            item.timeUpdated = null;

            Key<Item> key = this.datastore.save(item);
            this.entityService.cascadeLastActivity(Arrays.asList(item), currentTime);
            Item searchedItem = this.datastore.get(Item.class, key.getId());

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
    public Response delete(@PathObject("id") Event event, @Context JsonNode claims) {
        long itemCount = this.datastore.createQuery(Item.class)
            .field("eventId").equal(event.id)
            .count();

        ResponseBuilder builder;

        if (itemCount == 0) {
            this.datastore.delete(Event.class, event.id);
            builder = Response.ok();
        } else {
            builder = Response.status(Status.BAD_REQUEST).entity(itemCount).type(MediaType.APPLICATION_JSON);
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
        @Context JsonNode claims
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
            claims.get("sub").asText(),
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