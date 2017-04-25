package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.fasterxml.jackson.databind.JsonNode;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Feature;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationFeature;
import com.jivecake.api.model.PaymentDetail;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.SubscriptionPaymentDetail;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.FeatureService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PaymentProfileService;
import com.jivecake.api.service.PaymentService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@CORS
@Path("/organization")
public class OrganizationResource {
    private final ApplicationService applicationService;
    private final OrganizationService organizationService;
    private final EventService eventService;
    private final PaymentProfileService paymentProfileService;
    private final FeatureService featureService;
    private final PermissionService permissionService;
    private final PaymentService paymentService;
    private final StripeService stripeService;
    private final long maximumOrganizationsPerUser = 50;
    private final ExecutorService reindexExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public OrganizationResource(
        ApplicationService applicationService,
        OrganizationService organizationService,
        EventService eventService,
        PaymentProfileService paymentProfileService,
        FeatureService featureService,
        PermissionService permissionService,
        PaymentService paymentService,
        StripeService stripeService
    ) {
        this.applicationService = applicationService;
        this.organizationService = organizationService;
        this.eventService = eventService;
        this.paymentProfileService = paymentProfileService;
        this.featureService = featureService;
        this.permissionService = permissionService;
        this.paymentService = paymentService;
        this.stripeService = stripeService;
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
                Arrays.asList(application),
                PermissionService.WRITE
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
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response update(@PathObject("id") Organization organization, @Context JsonNode claims, List<Permission> permissions) {
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

        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{id}/payment/detail")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response createSubscriptionPaymentDetail(@PathObject("id") Organization organization, @Context JsonNode claims, SubscriptionPaymentDetail detail) {
        detail.custom = new ObjectId();
        detail.organizationId = organization.id;
        detail.user_id = claims.get("sub").asText();
        detail.timeCreated = new Date();

        Key<PaymentDetail> key = this.paymentService.save(detail);
        PaymentDetail entity = this.paymentService.readPaymentDetail((ObjectId)key.getId());

        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("/{id}/payment/profile/paypal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response createPaypalPaymentProfile(@PathObject("id") Organization organization, @Context JsonNode claims, PaypalPaymentProfile profile) {
        ResponseBuilder builder;

        long profileCount = this.paymentProfileService.query()
            .field("organizationId").equal(organization.id)
            .count();

        if (profileCount > 50) {
            builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}").type(MediaType.APPLICATION_JSON);
        } else {
            profile.organizationId = organization.id;
            profile.timeCreated = new Date();

            Key<PaymentProfile> key = this.paymentProfileService.save(profile);
            PaymentProfile entity = this.paymentProfileService.read((ObjectId)key.getId());
            builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/event")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response createEvent(@PathObject("id") Organization organization, @Context JsonNode claims, Event event) {
        ResponseBuilder builder;

        long activeEventsCount = this.eventService.query()
            .field("status").equal(this.eventService.getActiveEventStatus())
            .field("organizationId").equal(organization.id)
            .count();

        boolean hasFeatureViolation;

        StripeException stripeException = null;

        if (event.status == this.eventService.getActiveEventStatus()) {
            activeEventsCount++;
            List<Subscription> subscriptions = null;

            try {
                subscriptions = stripeService.getCurrentSubscriptions(organization.id);
            } catch (StripeException e) {
                stripeException = e;
            }

            hasFeatureViolation = subscriptions != null && activeEventsCount > subscriptions.size();
        } else {
            hasFeatureViolation = false;
        }

        long eventCount = this.eventService.query()
            .field("organizationId").equal(organization.id)
            .count();

        if (stripeException != null) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE)
                .entity(stripeException);
        } else if (eventCount > 100) {
            builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}").type(MediaType.APPLICATION_JSON);
        } else if (hasFeatureViolation) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"subscription\"}")
                .type(MediaType.APPLICATION_JSON);
        } else {
            event.id = null;
            event.organizationId = organization.id;
            event.timeCreated = new Date();

            Key<Event> key = this.eventService.save(event);
            Event searchedEvent = this.eventService.read((ObjectId)key.getId());
            builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(
        @Context JsonNode claims,
        Organization organization
    ) {
        ResponseBuilder builder;

        long sameEmailCount = this.organizationService.query()
            .field("email").equalIgnoreCase(organization.email)
            .count();

        if (sameEmailCount == 0) {
            if (organization.parentId == null) {
                builder = Response.status(Status.BAD_REQUEST);
            } else {
                String userId = claims.get("sub").asText();
                Application application = this.applicationService.read();

                Organization rootOrganization = this.organizationService.getRootOrganization();

                boolean parentIdOrganizationViolation;

                if (rootOrganization.id.equals(organization.parentId)) {
                    parentIdOrganizationViolation = false;
                } else {
                    boolean hasApplicationWrite = permissionService.has(
                        userId,
                        Arrays.asList(application),
                        PermissionService.WRITE
                    );

                    parentIdOrganizationViolation = !hasApplicationWrite;
                }

                long userOrganizationPermissions = this.permissionService.query()
                    .field("user_id").equal(userId)
                    .field("objectClass").equal(Organization.class.getSimpleName())
                    .field("include").equal(PermissionService.ALL)
                    .count();

                if (userOrganizationPermissions > this.maximumOrganizationsPerUser) {
                    builder = Response.status(Status.BAD_REQUEST).entity("{\"error\": \"limit\"}");
                } else if (parentIdOrganizationViolation) {
                    builder = Response.status(Status.UNAUTHORIZED);
                } else {
                    organization.children = new ArrayList<>();
                    organization.timeCreated = new Date();
                    organization.timeUpdated = null;
                    organization.id = null;

                    Key<Organization> key = this.organizationService.create(organization);

                    Permission permission = new Permission();
                    permission.user_id = userId;
                    permission.include = PermissionService.ALL;
                    permission.objectClass = Organization.class.getSimpleName();
                    permission.objectId = (ObjectId)key.getId();
                    permission.timeCreated = new Date();

                    this.permissionService.write(Arrays.asList(permission));

                    Organization entity = this.organizationService.read((ObjectId)key.getId());
                    builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);

                    this.reindexExecutor.submit(new Runnable() {
                        @Override
                        public void run() {
                            OrganizationResource.this.organizationService.reindex();
                        }
                    });
                }
            }
        } else {
            builder = Response.status(Status.CONFLICT);
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
        Query<Organization> query = this.organizationService.query();

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

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        List<Organization> organizations = query.asList();

        Paging<Organization> entity = new Paging<>(organizations, query.count());
        ResponseBuilder builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);

        return builder.build();
    }

    @GET
    @Authorized
    public Response search(
        @QueryParam("id") List<ObjectId> ids,
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

        if (name != null) {
            query.field("name").startsWithIgnoreCase(name);
        }

        if (email != null) {
            query.field("email").startsWithIgnoreCase(email);
        }

        if (order != null) {
            query.order(order);
        }

        FindOptions options = new FindOptions();

        if (limit != null && limit > -1) {
            options.limit(limit);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        List<Organization> organizations = query.asList();

        boolean hasPermission = this.permissionService.has(claims.get("sub").asText(), organizations, PermissionService.READ);

        ResponseBuilder builder;

        if (hasPermission) {
            Paging<Organization> entity = new Paging<>(organizations, query.count());
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
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response update(
        @PathObject("id") Organization queriedOrganization,
        Organization organization,
        @Context JsonNode claims
    ) {
        ResponseBuilder builder;

        Query<Organization> query = this.organizationService.query()
            .field("id").notEqual(queriedOrganization.id)
            .field("email").equalIgnoreCase(organization.email);

        long sameEmailCount = query.count();

        if (sameEmailCount == 0) {
            boolean parentIdChangeViolation = !Objects.equals(queriedOrganization.parentId, organization.parentId);

            if (parentIdChangeViolation) {
                builder = Response.status(Status.BAD_REQUEST);
            } else {
                organization.children = queriedOrganization.children;
                organization.id = queriedOrganization.id;
                organization.timeCreated = queriedOrganization.timeCreated;
                organization.timeUpdated = new Date();

                Key<Organization> key = this.organizationService.save(organization);

                Organization entity = this.organizationService.read((ObjectId)key.getId());
                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.CONFLICT);
        }

        return builder.build();
    }

    @GET
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.READ)
    public Response read(@PathObject("id") Organization organization, @Context JsonNode claims) {
        return Response.ok(organization).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Organization searchedOrganization, @Context JsonNode claims) {
        ResponseBuilder builder;

        long count = this.eventService.query()
            .field("organizationId").equal(searchedOrganization.id)
            .count();

        if (count > 0) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"count\"}")
                .type(MediaType.APPLICATION_JSON);
        } else if (searchedOrganization.parentId == null) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            Organization deletedOrganization = this.organizationService.delete(searchedOrganization.id);

            Query<Permission> deleteQuery = this.permissionService.query()
                .field("objectId").equal(searchedOrganization.id)
                .field("objectClass").equal(Organization.class.getSimpleName());
            this.permissionService.delete(deleteQuery);

            builder = Response.ok(deletedOrganization).type(MediaType.APPLICATION_JSON);

            this.reindexExecutor.submit(new Runnable() {
                @Override
                public void run() {
                    OrganizationResource.this.organizationService.reindex();
                }
            });
        }

        return builder.build();
    }
}