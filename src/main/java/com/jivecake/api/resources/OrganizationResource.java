package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
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
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.StripePaymentProfile;
import com.jivecake.api.request.Paging;
import com.jivecake.api.request.StripeAccountCredentials;
import com.jivecake.api.request.StripeOAuthCode;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Subscription;

@CORS
@Path("organization")
@Singleton
public class OrganizationResource {
    private final OrganizationService organizationService;
    private final EventService eventService;
    private final PermissionService permissionService;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final EntityService entityService;
    private final Datastore datastore;
    private final long maximumOrganizationsPerUser = 50;
    private final ExecutorService reindexExecutor = Executors.newSingleThreadExecutor();

    @Inject
    public OrganizationResource(
        OrganizationService organizationService,
        EventService eventService,
        PermissionService permissionService,
        StripeService stripeService,
        NotificationService notificationService,
        EntityService entityService,
        Datastore datastore
    ) {
        this.organizationService = organizationService;
        this.eventService = eventService;
        this.permissionService = permissionService;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.entityService = entityService;
        this.datastore = datastore;
    }

    @POST
    @Path("{id}/payment/profile/stripe")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public void createStripeProfile(
        @PathObject("id") Organization organization,
        @Suspended AsyncResponse asyncResponse,
        StripeOAuthCode oauthCode
    ) {
        this.stripeService.getAccountCredentials(oauthCode.code, new InvocationCallback<StripeAccountCredentials>() {
            @Override
            public void completed(StripeAccountCredentials token) {
                List<StripePaymentProfile> profiles = OrganizationResource.this.datastore.createQuery(StripePaymentProfile.class)
                    .field("organizationId").equal(organization.id)
                    .field("stripe_user_id").equal(token.stripe_user_id)
                    .asList();

                StripePaymentProfile profile;

                ResponseBuilder builder;

                if (profiles.isEmpty()) {
                    profile = new StripePaymentProfile();
                    profile.organizationId = organization.id;
                    profile.stripe_publishable_key = token.stripe_publishable_key;
                    profile.stripe_user_id = token.stripe_user_id;
                    profile.timeCreated = new Date();

                    StripeException exception = null;

                    try {
                        Account account = Account.retrieve(
                            token.stripe_user_id,
                            OrganizationResource.this.stripeService.getRequestOptions()
                        );

                        profile.email = account.getEmail();
                    } catch (StripeException e) {
                        exception = e;
                    }

                    if (exception == null) {
                        OrganizationResource.this.datastore.save(profile);
                        OrganizationResource.this.notificationService.notify(
                            Arrays.asList(profile),
                            "paymentprofile.create"
                        );

                        builder = Response.status(Status.CREATED)
                            .entity(profile)
                            .type(MediaType.APPLICATION_JSON);
                    } else {
                        builder = Response.status(Status.SERVICE_UNAVAILABLE).entity(exception);
                    }

                    asyncResponse.resume(builder.build());
                } else {
                    profile = profiles.get(0);

                    builder = Response.status(Status.OK)
                        .entity(profile)
                        .type(MediaType.APPLICATION_JSON);
                }

                asyncResponse.resume(builder.build());
            }

            @Override
            public void failed(Throwable throwable) {
                asyncResponse.resume(throwable);
            }
        });
    }

    @POST
    @Path("{id}/permission")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response update(
        @PathObject("id") Organization organization,
        List<Permission> permissions
    ) {
        Date currentTime = new Date();

        for (Permission permission : permissions) {
            permission.id = null;
            permission.objectId = organization.id;
            permission.timeCreated = currentTime;
            permission.objectClass = this.organizationService.getPermissionObjectClass();

            if (permission.permissions == null) {
                permission.permissions = new HashSet<>();
            }
        }

        this.permissionService.write(permissions);
        this.notificationService.notify(new ArrayList<>(permissions), "permission.write");
        this.entityService.cascadeLastActivity(Arrays.asList(organization), currentTime);

        List<Permission> entity = this.datastore.createQuery(Permission.class)
            .field("objectId").equal(organization.id)
            .field("objectClass").equal(this.organizationService.getPermissionObjectClass())
            .asList();

        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Path("{id}/payment/profile/paypal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response createPaypalPaymentProfile(
        @PathObject("id") Organization organization,
        PaypalPaymentProfile profile
    ) {
        ResponseBuilder builder;

        boolean validProfile = profile.email != null && profile.email.contains("@");

        if (validProfile) {
            long profileCount = this.datastore.createQuery(PaymentProfile.class)
                .field("organizationId").equal(organization.id)
                .count();

            if (profileCount > 50) {
                builder = Response.status(Status.BAD_REQUEST)
                    .entity("{\"error\": \"limit\"}")
                    .type(MediaType.APPLICATION_JSON);
            } else {
                Date currentTime = new Date();

                profile.organizationId = organization.id;
                profile.timeCreated = currentTime;

                Key<PaymentProfile> key = this.datastore.save(profile);
                this.entityService.cascadeLastActivity(Arrays.asList(profile), currentTime);
                this.notificationService.notify(Arrays.asList(profile), "paymentprofile.create");

                PaymentProfile entity = this.datastore.get(PaymentProfile.class, key.getId());
                builder = Response.ok(entity).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/event")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response createEvent(
        @PathObject("id") Organization organization,
        Event event
     ) {
        ResponseBuilder builder;

        boolean isValid = this.eventService.isValidEvent(event);

        if (isValid) {
            long activeEventsCount = this.datastore.createQuery(Event.class)
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

            long eventCount = this.datastore.createQuery(Event.class)
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
                Date currentTime = new Date();

                event.id = null;
                event.organizationId = organization.id;
                event.timeCreated = currentTime;
                event.lastActivity = currentTime;

                Key<Event> key = this.datastore.save(event);

                Event searchedEvent = this.datastore.get(Event.class, key.getId());

                this.notificationService.notify(Arrays.asList(searchedEvent), "event.create");
                this.entityService.cascadeLastActivity(Arrays.asList(searchedEvent), currentTime);

                builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.BAD_REQUEST);
        }

        return builder.build();
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(
        @Context DecodedJWT jwt,
        Organization organization
    ) {
        ResponseBuilder builder;

        long sameEmailCount = this.datastore.createQuery(Organization.class)
            .field("email").equalIgnoreCase(organization.email)
            .count();

        if (sameEmailCount == 0) {
            if (organization.parentId == null) {
                builder = Response.status(Status.BAD_REQUEST);
            } else {

                Organization rootOrganization = this.organizationService.getRootOrganization();

                boolean parentIdOrganizationViolation = !rootOrganization.id.equals(organization.parentId);

                long userOrganizationPermissions = this.datastore.createQuery(Permission.class)
                    .field("user_id").equal(jwt.getSubject())
                    .field("objectClass").equal(Organization.class.getSimpleName())
                    .field("include").equal(PermissionService.ALL)
                    .count();

                if (userOrganizationPermissions > this.maximumOrganizationsPerUser) {
                    builder = Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"limit\"}")
                        .type(MediaType.APPLICATION_JSON);
                } else if (parentIdOrganizationViolation) {
                    builder = Response.status(Status.BAD_REQUEST)
                        .entity("{\"error\": \"parentId\"}")
                        .type(MediaType.APPLICATION_JSON);
                } else {
                    Date currentTime = new Date();

                    organization.id = null;
                    organization.children = new ArrayList<>();
                    organization.timeCreated = currentTime;
                    organization.timeUpdated = null;
                    organization.lastActivity = currentTime;

                    Key<Organization> key = this.datastore.save(organization);

                    Permission permission = new Permission();
                    permission.user_id = jwt.getSubject();
                    permission.include = PermissionService.ALL;
                    permission.permissions = new HashSet<>();
                    permission.objectClass = this.organizationService.getPermissionObjectClass();
                    permission.objectId = (ObjectId)key.getId();
                    permission.timeCreated = currentTime;

                    this.permissionService.write(Arrays.asList(permission));

                    List<Object> entities = new ArrayList<>();
                    entities.add(organization);
                    entities.add(permission);

                    this.notificationService.notify(entities, "organization.create");

                    Organization entity = this.datastore.get(Organization.class, key.getId());
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
        Query<Organization> query = this.datastore.createQuery(Organization.class);

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

        if (limit != null && limit > -1 && limit <= 100) {
            options.limit(limit);
        } else {
            options.limit(100);
        }

        if (offset != null && offset > -1) {
            options.skip(offset);
        }

        Paging<Organization> entity = new Paging<>(query.asList(options), query.count());
        return Response.ok(entity).type(MediaType.APPLICATION_JSON).build();
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
        @Context DecodedJWT jwt
    ) {
        Query<Organization> query = this.datastore.createQuery(Organization.class);

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

        List<Organization> organizations = query.asList(options);

        boolean hasPermission = this.permissionService.has(
            jwt.getSubject(),
            organizations,
            PermissionService.READ
        );

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
        @PathObject("id") Organization searchedOrganization,
        Organization organization
    ) {
        ResponseBuilder builder;

        Query<Organization> query = this.datastore.createQuery(Organization.class)
            .field("id").notEqual(searchedOrganization.id)
            .field("email").equalIgnoreCase(organization.email);

        long sameEmailCount = query.count();

        if (sameEmailCount == 0) {
            boolean parentIdChangeViolation = !Objects.equals(searchedOrganization.parentId, organization.parentId);

            if (parentIdChangeViolation) {
                builder = Response.status(Status.BAD_REQUEST);
            } else {
                organization.children = searchedOrganization.children;
                organization.id = searchedOrganization.id;
                organization.timeCreated = searchedOrganization.timeCreated;
                organization.timeUpdated = new Date();

                Key<Organization> key = this.datastore.save(organization);
                Organization entity = this.datastore.get(Organization.class, key.getId());

                this.notificationService.notify(Arrays.asList(entity), "organization.update");

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
    public Response read(@PathObject("id") Organization organization) {
        return Response.ok(organization).type(MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("/{id}")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", permission=PermissionService.WRITE)
    public Response delete(@PathObject("id") Organization searchedOrganization) {
        ResponseBuilder builder;

        long count = this.datastore.createQuery(Event.class)
            .field("organizationId").equal(searchedOrganization.id)
            .count();

        if (count > 0) {
            builder = Response.status(Status.BAD_REQUEST)
                .entity("{\"error\": \"count\"}")
                .type(MediaType.APPLICATION_JSON);
        } else if (searchedOrganization.parentId == null) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            this.datastore.delete(Organization.class, searchedOrganization.id);

            Query<Permission> query = this.datastore.createQuery(Permission.class)
                .field("objectId").equal(searchedOrganization.id)
                .field("objectClass").equal(this.organizationService.getPermissionObjectClass());

            List<Permission> permisisons = query.asList();

            Collection<Object> entities = new ArrayList<>();
            entities.add(searchedOrganization);
            entities.addAll(permisisons);

            this.notificationService.notify(entities, "organization.delete");
            this.datastore.delete(query);

            builder = Response.ok(searchedOrganization).type(MediaType.APPLICATION_JSON);

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