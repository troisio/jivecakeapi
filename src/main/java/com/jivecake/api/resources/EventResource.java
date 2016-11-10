package com.jivecake.api.resources;

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
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Feature;
import com.jivecake.api.model.Item;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.FeatureService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;

@Path("/event")
@CORS
public class EventResource {
    private final FeatureService featureService;
    private final EventService eventService;
    private final ItemService itemService;
    private final OrganizationService organizationService;
    private final PermissionService permissionService;

    @Inject
    public EventResource(
        FeatureService featureService,
        EventService eventService,
        ItemService itemService,
        OrganizationService organizationService,
        PermissionService permissionService
    ) {
        this.featureService = featureService;
        this.itemService = itemService;
        this.eventService = eventService;
        this.organizationService = organizationService;
        this.permissionService = permissionService;
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
        Query<Event> query = this.eventService.query()
            .field("status").equal(this.eventService.getActiveEventStatus())
            .limit(1000);

        if (text != null && !text.isEmpty()) {
            List<ObjectId> organizationIds = this.organizationService.query()
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

        Paging<Event> entity = new Paging<>(query.asList(), query.countAll());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        return builder.build();
    }

    @POST
    @Path("/{id}")
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response update(@PathObject("id") Event original, @Context JsonNode claims, Event event) {
        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
            event.organizationId,
            claims.get("sub").asText(),
            this.organizationService.getWritePermission()
        );

        if (hasPermission) {
            long activeEventsCount = this.eventService.query()
                .field("id").notEqual(original.id)
                .field("status").equal(this.eventService.getActiveEventStatus())
                .field("organizationId").equal(original.organizationId)
                .countAll();

            if (event.status == this.eventService.getActiveEventStatus()) {
                activeEventsCount++;
            }

            List<Feature> currentOrganizationFeatures = this.featureService.getCurrentFeaturesQuery(new Date())
                .disableValidation()
                .field("organizationId").equal(original.organizationId)
                .asList();

            boolean hasFeatureViolation = activeEventsCount > currentOrganizationFeatures.size() &&
                                          event.status == this.eventService.getActiveEventStatus();

            boolean hasPaymentProfileViolation = (event.paymentProfileId == null && event.currency != null) ||
                                                 (event.paymentProfileId != null && event.currency == null);

            if (hasPaymentProfileViolation) {
                builder = Response.status(Status.BAD_REQUEST);
            } else if (hasFeatureViolation) {
                builder = Response.status(Status.BAD_REQUEST)
                        .entity(currentOrganizationFeatures)
                        .type(MediaType.APPLICATION_JSON);
            } else {
                event.organizationId = original.organizationId;
                event.id = original.id;
                event.timeCreated = original.timeCreated;
                event.timeUpdated = new Date();
                Key<Event> key = this.eventService.save(event);
                Event searchedEvent = this.eventService.read((ObjectId)key.getId());
                builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Path("/{id}/item")
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createItem(@PathObject("id") Event event, Item item, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                event.organizationId,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                item.eventId = event.id;
                item.timeCreated = new Date();

                Key<Item> key = this.itemService.save(item);
                Item searchedItem = this.itemService.read((ObjectId)key.getId());
                builder = Response.ok(searchedItem).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @DELETE
    @Authorized
    @Path("/{id}")
    public Response delete(@PathObject("id") Event event, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                event.organizationId,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                Query<Item> query = this.itemService.query().disableValidation();

                query.or(
                    query.criteria("eventId").equal(event.id),
                    query.criteria("organizationId").equal(event.organizationId)
                );

                long itemCount = query.countAll();

                if (itemCount == 0) {
                    Event entity = this.eventService.delete(event.id);
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                } else {
                    builder = Response.status(Status.BAD_REQUEST).entity(itemCount).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
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

        Query<Event> query = this.eventService.query();

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

        if (limit != null && limit > -1) {
            query.limit(limit);
        }

        if (offset != null && offset > -1) {
            query.offset(offset);
        }

        if (order != null) {
            query.order(order);
        }

        List<Event> entities = query.asList();
        boolean hasPermission = this.permissionService.has(claims.get("sub").asText(), entities, this.organizationService.getReadPermission());

        if (hasPermission) {
            Paging<Event> entity = new Paging<>(entities, query.countAll());
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