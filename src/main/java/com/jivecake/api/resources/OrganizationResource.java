package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

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
import com.google.inject.Inject;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Feature;
import com.jivecake.api.model.IndexedOrganizationNode;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.model.OrganizationNode;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.SubscriptionPaymentDetail;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.FeatureService;
import com.jivecake.api.service.IndexedOrganizationNodeService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.MappingService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PaymentProfileService;
import com.jivecake.api.service.PaymentService;
import com.jivecake.api.service.PermissionService;

@CORS
@Path("/organization")
public class OrganizationResource {
    private final ApplicationService applicationService;
    private final OrganizationService organizationService;
    private final IndexedOrganizationNodeService indexedOrganizationNodeService;
    private final MappingService mappingService;
    private final EventService eventService;
    private final ItemService itemService;
    private final PaymentProfileService paymentProfileService;
    private final FeatureService featureService;
    private final PermissionService permissionService;
    private final PaymentService paymentService;
    private final long maximumOrganizationsPerUser = 50;

    @Inject
    public OrganizationResource(
        ApplicationService applicationService,
        OrganizationService organizationService,
        IndexedOrganizationNodeService indexedOrganizationNodeService,
        MappingService mappingService,
        EventService eventService,
        ItemService itemService,
        PaymentProfileService paymentProfileService,
        FeatureService featureService,
        PermissionService permissionService,
        PaymentService paymentService
    ) {
        this.applicationService = applicationService;
        this.organizationService = organizationService;
        this.indexedOrganizationNodeService = indexedOrganizationNodeService;
        this.mappingService = mappingService;
        this.eventService = eventService;
        this.itemService = itemService;
        this.paymentProfileService = paymentProfileService;
        this.featureService = featureService;
        this.permissionService = permissionService;
        this.paymentService = paymentService;
    }

    @POST
    @Path("/{id}/feature")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response create(@PathObject("id") Organization organization, @Context JsonNode claims, OrganizationFeature feature) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Application application = this.applicationService.read();

            boolean hasPermission = this.permissionService.has(
                claims.get("sub").asText(),
                Application.class,
                this.applicationService.getWritePermission(),
                application.id
            );

            if (hasPermission) {
                feature.id = null;
                feature.organizationId = organization.id;
                feature.timeCreated = new Date();
                Key<Feature> key = this.featureService.save(feature);

                Feature entity = this.featureService.query().field("id").equal(key.getId()).get();
                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Path("/{id}/permission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response write(@PathObject("id") Organization organization, @Context JsonNode claims, List<Permission> permissions) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                Date timeCreated = new Date();

                for (Permission permission : permissions) {
                    permission.id = null;
                    permission.objectId = organization.id;
                    permission.timeCreated = timeCreated;
                    permission.objectClass = this.organizationService.getPermissionObjectClass();
                }

                this.permissionService.write(permissions);

                List<Permission> entity = this.permissionService.query()
                    .field("objectId").equal(organization.id)
                    .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
                    .asList();

                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Path("/{id}/payment/detail")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    public Response createSubscriptionPaymentDetail(@PathObject("id") Organization organization, @Context JsonNode claims, SubscriptionPaymentDetail detail) {
        ResponseBuilder builder;

        boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
            organization.id,
            claims.get("sub").asText(),
            this.organizationService.getWritePermission()
        );

        if (hasPermission) {
            detail.custom = new ObjectId();
            detail.organizationId = organization.id;
            detail.user_id = claims.get("sub").asText();
            detail.timeCreated = new Date();

            Key<PaymentDetail> key = this.paymentService.save(detail);
            PaymentDetail entity = this.paymentService.readPaymentDetail((ObjectId)key.getId());

            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Authorized
    @Path("/{id}/payment/profile/paypal")
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createPaypalPaymentProfile(@PathObject("id") Organization organization, @Context JsonNode claims, PaypalPaymentProfile profile) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getWritePermission()
            );

            if (hasPermission) {
                long profileCount = this.paymentProfileService.query()
                    .field("organizationId").equal(organization.id)
                    .countAll();

                if (profileCount > 50) {
                    builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}").type(MediaType.APPLICATION_JSON);
                } else {
                    profile.organizationId = organization.id;
                    profile.timeCreated = new Date();

                    Key<PaymentProfile> key = this.paymentProfileService.save(profile);
                    PaymentProfile entity = this.paymentProfileService.read((ObjectId)key.getId());
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @GET
    @Path("/{id}/tree")
    @Authorized
    public Response getTree(@PathObject("id") Organization organization, @Context JsonNode claims) {
        boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
            organization.id,
            claims.get("sub").asText(),
            this.applicationService.getReadPermission()
        );

        ResponseBuilder builder;

        if (hasPermission) {
            OrganizationNode tree = this.organizationService.getOrganizationTree(organization.id);
            builder = Response.ok(tree).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/event")
    @Authorized
    public Response createEvent(@PathObject("id") Organization organization, @Context JsonNode claims, Event event) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.applicationService.getWritePermission()
            );

            if (hasPermission) {
                long activeEventsCount = this.eventService.query()
                    .field("status").equal(this.eventService.getActiveEventStatus())
                    .field("organizationId").equal(organization.id)
                    .countAll();

                if (event.status == this.eventService.getActiveEventStatus()) {
                    activeEventsCount++;
                }

                List<Feature> currentOrganizationFeatures = this.featureService.getCurrentFeaturesQuery(new Date())
                    .disableValidation()
                    .field("organizationId").equal(organization.id)
                    .asList();

                boolean hasFeatureViolation = activeEventsCount > currentOrganizationFeatures.size();
                long eventCount = this.eventService.query()
                    .field("organizationId").equal(organization.id)
                    .countAll();

                if (eventCount > 50) {
                    builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}").type(MediaType.APPLICATION_JSON);
                } else if (hasFeatureViolation) {
                    builder = Response.status(Status.BAD_REQUEST)
                            .entity(currentOrganizationFeatures)
                            .type(MediaType.APPLICATION_JSON);
                } else {
                    event.id = null;
                    event.organizationId = organization.id;
                    event.timeCreated = new Date();

                    Key<Event> key = this.eventService.save(event);
                    Event searchedEvent = this.eventService.read((ObjectId)key.getId());
                    builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(@Context JsonNode claims, Organization organization) {
        ResponseBuilder builder;

        long sameEmailCount = this.organizationService.query()
            .field("email").equalIgnoreCase(organization.email)
            .countAll();

        if (sameEmailCount == 0) {
            if (organization.parentId == null) {
                builder = Response.status(Status.BAD_REQUEST);
            } else {
                Application application = this.applicationService.read();

                Organization rootOrganization = this.organizationService.getRootOrganization();

                boolean parentIdOrganizationViolation;

                if (rootOrganization.id.equals(organization.parentId)) {
                    parentIdOrganizationViolation = false;
                } else {
                    boolean hasApplicationWrite = permissionService.has(
                        claims.get("sub").asText(),
                        Application.class,
                        this.applicationService.getWritePermission(),
                        application.id
                    );

                    parentIdOrganizationViolation = !hasApplicationWrite;
                }

                long userOrganizationPermissions = this.permissionService.query()
                    .field("user_id").equal(claims.get("sub").asText())
                    .field("objectClass").equal(Organization.class.getSimpleName())
                    .field("include").equal(this.permissionService.getIncludeAllPermission())
                    .countAll();

                if (userOrganizationPermissions > this.maximumOrganizationsPerUser) {
                    builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}");
                } else if (parentIdOrganizationViolation) {
                    builder = Response.status(Status.UNAUTHORIZED);
                } else {
                    organization.timeCreated = new Date();
                    organization.timeUpdated = null;
                    organization.id = null;

                    Key<Organization> key = this.organizationService.create(organization);

                    Permission permission = new Permission();
                    permission.user_id = claims.get("sub").asText();
                    permission.include = this.permissionService.getIncludeAllPermission();
                    permission.objectClass = Organization.class.getSimpleName();
                    permission.objectId = (ObjectId)key.getId();
                    permission.timeCreated = new Date();

                    this.permissionService.write(Arrays.asList(permission));

                    this.indexedOrganizationNodeService.writeIndexedOrganizationNodes(rootOrganization.id);

                    Organization entity = this.organizationService.read((ObjectId)key.getId());
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                }
            }
        } else {
            builder = Response.status(Status.CONFLICT);
        }

        return builder.build();
    }

    @GET
    @Path("/index")
    @Authorized
    public Response read(
        @QueryParam("organizationIds") Set<ObjectId> organizationIds,
        @QueryParam("parentIds") List<ObjectId> parentIds,
        @QueryParam("childIds") List<ObjectId> childIds,
        @Context JsonNode claims
    ) {
        Query<IndexedOrganizationNode> query = this.indexedOrganizationNodeService.query();

        if (!organizationIds.isEmpty()) {
            query.field("organizationId").in(organizationIds);
        }

        if (!parentIds.isEmpty()) {
            query.field("parentIds").equal(parentIds);
        }

        if (!childIds.isEmpty()) {
            query.field("childIds").equal(childIds);
        }

        List<IndexedOrganizationNode> nodes = query.asList();
        Set<ObjectId> searchedOrganizationIds = nodes.stream().map(node -> node.organizationId).collect(Collectors.toSet());

        boolean hasPermission = this.permissionService.hasAllHierarchicalPermission(
            claims.get("sub").asText(),
            this.organizationService.getReadPermission(),
            searchedOrganizationIds
        );

        ResponseBuilder builder;

        if (hasPermission) {
            Paging<IndexedOrganizationNode> paging = new Paging<>(nodes, query.countAll());
            builder = Response.ok(paging);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @GET
    @Path("/search")
    public Response search(
        @QueryParam("id") List<ObjectId> organizationIds,
        @QueryParam("parentId") List<ObjectId> parentIds,
        @QueryParam("name") String name,
        @QueryParam("shortName") String shortName,
        @QueryParam("email") String email,
        @QueryParam("order") String order,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset
    ) {
        Query<Organization> query = this.organizationService.query().retrievedFields(false, "email");

        if (!organizationIds.isEmpty()) {
            query.field("id").in(organizationIds);
        }

        if (shortName != null) {
            query.field("shortName").equal(shortName);
        }

        if (!parentIds.isEmpty()) {
            query.field("parentId").in(parentIds);
        }

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        if (email != null) {
            query.field("email").startsWithIgnoreCase(email);
        }

        if (order != null) {
            query.order(order);
        }

        if (limit != null && limit > -1) {
            query.limit(limit);
        }

        if (offset != null && offset > -1) {
            query.offset(offset);
        }

        List<Organization> organizations = query.asList();

        Paging<Organization> entity = new Paging<>(organizations, query.countAll());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);

        return builder.build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("id") List<ObjectId> ids,
        @QueryParam("eventId") List<ObjectId> eventIds,
        @QueryParam("name") String name,
        @QueryParam("email") String email,
        @QueryParam("order") String order,
        @QueryParam("limit") Integer limit,
        @QueryParam("offset") Integer offset,
        @Context JsonNode claims
    ) {
        Query<Organization> query = this.organizationService.query();

        if (!ids.isEmpty()) {
            query.field("id").in(ids);
        }

        if (!eventIds.isEmpty()) {
            Set<ObjectId> organizationEventIds = this.mappingService.getOrganizationIds(
                new ArrayList<>(),
                new ArrayList<>(),
                eventIds
            );

            query.field("id").in(organizationEventIds);
        }

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        if (email != null) {
            query.field("email").startsWithIgnoreCase(email);
        }

        if (order != null) {
            query.order(order);
        }

        if (limit != null && limit > -1) {
            query.limit(limit);
        }

        if (offset != null && offset > -1) {
            query.offset(offset);
        }

        List<Organization> organizations = query.asList();

        boolean hasPermission = this.permissionService.has(
            claims.get("sub").asText(),
            organizations,
            this.organizationService.getReadPermission()
        );

        ResponseBuilder builder;

        if (hasPermission) {
            Paging<Organization> entity = new Paging<>(organizations, query.countAll());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        } else {
            builder = Response.status(Status.UNAUTHORIZED);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}")
    @Authorized
    public Response update(@PathObject("id") Organization queriedOrganization, @Context JsonNode claims, Organization organization) {
        ResponseBuilder builder;

        if (queriedOrganization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            Query<Organization> query = this.organizationService.query()
                .field("id").notEqual(queriedOrganization.id)
                .field("email").equalIgnoreCase(organization.email);

            long sameEmailCount = query.countAll();

            if (sameEmailCount == 0) {
                boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                    queriedOrganization.id,
                    claims.get("sub").asText(),
                    this.applicationService.getWritePermission()
                );

                if (hasPermission) {
                    boolean parentIdChanged = !Objects.equals(queriedOrganization.parentId, organization.parentId);
                    boolean parentIdChangeViolation;

                    if (parentIdChanged) {
                        Application application = this.applicationService.read();
                        boolean hasApplicationWrite = this.permissionService.has(
                            claims.get("sub").asText(),
                            Application.class,
                            this.applicationService.getWritePermission(),
                            application.id
                        );

                        parentIdChangeViolation = !hasApplicationWrite;
                    } else {
                        parentIdChangeViolation = false;
                    }

                    if (parentIdChangeViolation) {
                        builder = Response.status(Status.UNAUTHORIZED);
                    } else {
                        organization.id = queriedOrganization.id;
                        organization.timeCreated = queriedOrganization.timeCreated;
                        organization.timeUpdated = new Date();

                        Key<Organization> key = this.organizationService.save(organization);
                        Organization rootOrganization = this.organizationService.getRootOrganization();
                        this.indexedOrganizationNodeService.writeIndexedOrganizationNodes(rootOrganization.id);

                        Organization entity = this.organizationService.read((ObjectId)key.getId());
                        builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
                    }
                } else {
                    builder = Response.status(Status.UNAUTHORIZED);
                }
            } else {
                builder = Response.status(Status.CONFLICT);
            }
        }

        return builder.build();
    }

    @GET
    @Path("/{id}")
    @Authorized
    public Response read(@PathObject("id") Organization organization, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.hasAnyHierarchicalPermission(
                organization.id,
                claims.get("sub").asText(),
                this.organizationService.getReadPermission()
            );

            if (hasPermission) {
                builder = Response.ok(organization).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    public Response delete(@PathObject("id") Organization searchedOrganization, @Context JsonNode claims) {
        ResponseBuilder builder;

        if (searchedOrganization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            boolean hasPermission = this.permissionService.has(
                claims.get("sub").asText(),
                Organization.class,
                this.organizationService.getWritePermission(),
                searchedOrganization.id
            );

            if (hasPermission) {
                List<Event> events = this.eventService.query().field("organizationId").equal(searchedOrganization.id).asList();
                List<Item> items = this.itemService.query().disableValidation().field("organizationId").equal(searchedOrganization.id).asList();

                if (!events.isEmpty()) {
                    builder = Response.status(Status.CONFLICT).entity(events);
                } else if (!items.isEmpty()) {
                    builder = Response.status(Status.CONFLICT).entity(items);
                } else if (searchedOrganization.parentId == null) {
                    builder = Response.status(Status.CONFLICT);
                } else {
                    Organization deletedOrganization = this.organizationService.delete(searchedOrganization.id);

                    Query<Permission> deleteQuery = this.permissionService.query()
                                          .field("objectId").equal(searchedOrganization.id)
                                          .field("objectClass").equal(Organization.class.getSimpleName());
                    this.permissionService.delete(deleteQuery);

                    Organization rootOrganization = this.organizationService.getRootOrganization();
                    this.indexedOrganizationNodeService.writeIndexedOrganizationNodes(rootOrganization.id);

                    builder = Response.ok(deletedOrganization);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }
}