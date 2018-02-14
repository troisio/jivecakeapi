package com.jivecake.api.resources;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.HeaderParam;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;

import org.apache.commons.collections4.ListUtils;
import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.FindOptions;
import org.mongodb.morphia.query.Query;

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
import com.auth0.exception.Auth0Exception;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.cloud.storage.Acl;
import com.google.cloud.storage.Acl.Role;
import com.google.cloud.storage.Acl.User;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageClass;
import com.google.cloud.storage.StorageOptions;
import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.GZip;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.OrganizationInvitation;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.PaypalPaymentProfile;
import com.jivecake.api.model.Permission;
import com.jivecake.api.model.StripePaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.StripeAccountCredentials;
import com.jivecake.api.request.StripeOAuthCode;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.StripeService;
import com.stripe.exception.StripeException;
import com.stripe.model.Account;
import com.stripe.model.Subscription;

import io.sentry.SentryClient;
import io.sentry.event.EventBuilder;
import io.sentry.event.interfaces.ExceptionInterface;

@CORS
@Path("organization")
@Singleton
public class OrganizationResource {
    private final SentryClient sentry;
    private final Auth0Service auth0Service;
    private final APIConfiguration configuration;
    private final OrganizationService organizationService;
    private final EventService eventService;
    private final StripeService stripeService;
    private final NotificationService notificationService;
    private final EntityService entityService;
    private final Datastore datastore;
    private final long maximumOrganizationsPerUser = 10;

    @Inject
    public OrganizationResource(
        SentryClient sentry,
        Auth0Service auth0Service,
        APIConfiguration configuration,
        OrganizationService organizationService,
        EventService eventService,
        StripeService stripeService,
        NotificationService notificationService,
        EntityService entityService,
        Datastore datastore
    ) {
        this.sentry = sentry;
        this.auth0Service = auth0Service;
        this.configuration = configuration;
        this.organizationService = organizationService;
        this.eventService = eventService;
        this.stripeService = stripeService;
        this.notificationService = notificationService;
        this.entityService = entityService;
        this.datastore = datastore;
    }

    @GET
    @Path("{id}/tree")
    @GZip
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", read=true)
    public Response getOrganizationTree(
        @PathObject("id") Organization organization,
        @Context DecodedJWT jwt
    ) throws Auth0Exception {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.YEAR, -1);
        Date oneYearPrevious = calendar.getTime();

        List<PaymentProfile> profiles = this.datastore.createQuery(PaymentProfile.class)
            .field("organizationId").equal(organization.id)
            .asList();

        List<EntityAsset> assets = this.datastore.createQuery(EntityAsset.class)
            .field("entityId").equal(organization.id.toString())
            .field("entityType").equal(EntityType.ORGANIZATION)
            .field("assetType").in(
                Arrays.asList(
                    AssetType.ORGANIZATION_CONSENT_TEXT,
                    AssetType.GOOGLE_CLOUD_STORAGE_CONSENT_PDF
                )
             )
            .asList();

        List<Event> events = this.datastore.createQuery(Event.class)
            .field("organizationId").equal(organization.id)
            .field("lastActivity").greaterThan(oneYearPrevious)
            .asList();

        List<Item> items = this.datastore.createQuery(Item.class)
            .field("organizationId").equal(organization.id)
            .field("lastActivity").greaterThan(oneYearPrevious)
            .asList();

        List<Transaction> transactions = this.datastore.createQuery(Transaction.class)
            .field("organizationId").equal(organization.id)
            .field("timeCreated").greaterThan(oneYearPrevious)
            .asList();

        List<String> userIds = transactions.stream()
            .filter(transaction -> transaction.user_id != null)
            .map(transaction -> transaction.user_id)
            .distinct()
            .collect(Collectors.toList());

        ManagementAPI managementApi = new ManagementAPI(
            this.configuration.oauth.domain,
            this.auth0Service.getToken().get("access_token").asText()
        );

        List<com.auth0.json.mgmt.users.User> users =
            ListUtils.partition(userIds, 50)
            .stream()
            .map(ids -> {
                String ors = ids.stream()
                    .map(id -> String.format("\"%s\"", id))
                    .collect(Collectors.joining(" OR "));
                String query = String.format("user_id: (%s)", ors);

                List<com.auth0.json.mgmt.users.User> result;

                try {
                    result = managementApi
                        .users()
                        .list(new UserFilter().withQuery(query))
                        .execute()
                        .getItems();
                } catch (Auth0Exception e) {
                    this.sentry.sendEvent(
                        new EventBuilder()
                            .withMessage(e.getMessage())
                            .withEnvironment(this.sentry.getEnvironment())
                            .withLevel(io.sentry.event.Event.Level.ERROR)
                            .withSentryInterface(new ExceptionInterface(e))
                            .withExtra("sub", jwt.getSubject())
                            .build()
                    );
                    result = Arrays.asList();
                }

                return result;
            })
            .flatMap(List::stream)
            .collect(Collectors.toList());

        List<EntityAsset> transactionUserAssets = this.datastore.createQuery(EntityAsset.class)
            .field("entityId").in(userIds)
            .field("entityType").equal(EntityType.USER)
            .asList();

        Map<String, Object> entity = new HashMap<>();
        entity.put("organization", organization);
        entity.put("event", events);
        entity.put("item", items);
        entity.put("transaction", transactions);
        entity.put("paymentProfile", profiles);
        entity.put("organizationAsset", assets);
        entity.put("transactionUser", users);
        entity.put("transactionUserAsset", transactionUserAssets);

        return Response.ok(entity, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("{id}/invitation")
    @Authorized
    @HasPermission(id="id", clazz=Organization.class, read=true)
    public Response getInvitations(@PathObject("id") Organization organization) {
        Calendar calendar = Calendar.getInstance();
        calendar.add(Calendar.DATE, -7);

        FindOptions options = new FindOptions();
        options.limit(ApplicationService.LIMIT_DEFAULT);

        List<OrganizationInvitation> invitations = this.datastore.createQuery(OrganizationInvitation.class)
            .field("organizationId").equal(organization.id)
            .field("timeCreated").greaterThan(calendar.getTime())
            .field("timeAccepted").doesNotExist()
            .order("-timeCreated")
            .asList(options);
        return Response.ok(invitations, MediaType.APPLICATION_JSON).build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}/invitation")
    @Authorized
    @HasPermission(id="id", clazz=Organization.class, write=true)
    public Response inviteUser(
        @PathObject("id") Organization organization,
        @ValidEntity OrganizationInvitation invitation
    ) throws Auth0Exception {
        String emailSearch = invitation.email.replaceAll("\"", "\\\"");
        String query = String.format("email:\"%s\"", emailSearch);

        ManagementAPI api = new ManagementAPI(
            this.configuration.oauth.domain,
            this.auth0Service.getToken().get("access_token").asText()
        );

        UserFilter filter = new UserFilter();
        filter.withQuery(query);

        List<com.auth0.json.mgmt.users.User> users = api.users()
            .list(filter)
            .execute()
            .getItems();

        invitation.id = null;
        invitation.organizationId = organization.id;
        invitation.timeCreated = new Date();
        invitation.timeAccepted = null;
        invitation.userIds = users.stream()
            .map(com.auth0.json.mgmt.users.User::getId)
            .collect(Collectors.toList());

        long permissionsWithEmail = this.datastore.createQuery(Permission.class)
            .field("objectId").equal(organization.id)
            .field("objectClass").equal("Organization")
            .field("user_id").in(invitation.userIds)
            .count();

        ResponseBuilder builder;

        if (permissionsWithEmail == 0) {
            if (!invitation.userIds.isEmpty()) {
                this.datastore.save(invitation);
                this.notificationService.notify(
                    Arrays.asList(invitation),
                    "organizationInvitation.create"
                );
            }

            builder = Response.ok();
        } else {
            builder = Response.status(Status.CONFLICT);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}/consent")
    @Authorized
    @HasPermission(id="id", clazz=Organization.class, write=true)
    public Response createConsent(
        @PathObject("id") Organization organization,
        @HeaderParam("Content-Type") String contentType,
        @Context DecodedJWT jwt,
        @ValidEntity EntityAsset asset
    ) {
        ResponseBuilder builder;

        long count = this.datastore.createQuery(EntityAsset.class)
            .field("entityType").equal(EntityType.ORGANIZATION)
            .field("entityId").equal(organization.id.toString())
            .field("assetType").in(
                Arrays.asList(
                    AssetType.GOOGLE_CLOUD_STORAGE_CONSENT_PDF,
                    AssetType.ORGANIZATION_CONSENT_TEXT
                )
            ).count();

        if (count > 50) {
            ErrorData data = new ErrorData();
            data.error = "limit";
            data.data = 50;
            builder = Response.status(Status.BAD_REQUEST)
                .entity(data)
                .type(MediaType.APPLICATION_JSON);
        } else {
            if (asset.assetType == AssetType.ORGANIZATION_CONSENT_TEXT) {
                asset.id = null;
                asset.entityType = EntityType.ORGANIZATION;
                asset.entityId = organization.id.toString();
                asset.assetId = UUID.randomUUID().toString();
                asset.assetType = AssetType.ORGANIZATION_CONSENT_TEXT;
                asset.timeCreated = new Date();

                long byteLimit = 1000000;

                if (asset.data.length > byteLimit) {
                    ErrorData data = new ErrorData();
                    data.error = "bytelimit";
                    data.data = byteLimit;
                    builder = Response.status(Status.BAD_REQUEST)
                        .entity(data)
                        .type(MediaType.APPLICATION_JSON);
                } else {
                    this.datastore.save(asset);
                    this.notificationService.notify(Arrays.asList(asset), "asset.create");
                    builder = Response.ok(asset).type(MediaType.APPLICATION_JSON);
                }
            } else if (asset.assetType == AssetType.GOOGLE_CLOUD_STORAGE_CONSENT_PDF) {
                Storage storage = StorageOptions.getDefaultInstance().getService();
                String name = UUID.randomUUID().toString();
                BlobInfo info = BlobInfo.newBuilder(BlobId.of(this.configuration.gcp.bucket, name))
                    .setContentType("application/pdf")
                    .setAcl(Arrays.asList(Acl.of(User.ofAllUsers(), Role.READER)))
                    .setStorageClass(StorageClass.REGIONAL)
                    .build();

                Blob blob = storage.create(info, asset.data);

                asset.id = null;
                asset.data = null;
                asset.entityType = EntityType.ORGANIZATION;
                asset.entityId = organization.id.toString();
                asset.assetId = blob.getBucket() + "/" + blob.getName();
                asset.assetType = AssetType.GOOGLE_CLOUD_STORAGE_CONSENT_PDF;
                asset.timeCreated = new Date();

                this.datastore.save(asset);
                this.notificationService.notify(Arrays.asList(asset), "asset.create");

                builder = Response.ok(asset).type(MediaType.APPLICATION_JSON);
            } else {
                builder = Response.status(Status.BAD_REQUEST);
            }
        }

        return builder.build();
    }

    @POST
    @Path("{id}/payment/profile/stripe")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", write=true)
    public void createStripeProfile(
        @PathObject("id") Organization organization,
        @Suspended AsyncResponse asyncResponse,
        @ValidEntity StripeOAuthCode oauthCode
    ) {
        this.stripeService.getAccountCredentials(oauthCode.code, new InvocationCallback<StripeAccountCredentials>() {
            @Override
            public void completed(StripeAccountCredentials token) {
                List<StripePaymentProfile> profiles = OrganizationResource.this.datastore.createQuery(StripePaymentProfile.class)
                    .field("organizationId").equal(organization.id)
                    .field("stripe_user_id").equal(token.stripe_user_id)
                    .asList();

                ResponseBuilder builder;

                if (profiles.isEmpty()) {
                    StripePaymentProfile profile = new StripePaymentProfile();
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
                        builder = Response.status(Status.SERVICE_UNAVAILABLE);
                    }

                    asyncResponse.resume(builder.build());
                } else {
                    builder = Response.status(Status.OK)
                        .entity(profiles.get(0))
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
    @Path("{id}/payment/profile/paypal")
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", write=true)
    public Response createPaypalPaymentProfile(
        @PathObject("id") Organization organization,
        @ValidEntity PaypalPaymentProfile profile
    ) {
        ResponseBuilder builder;

        long profileCount = this.datastore.createQuery(PaymentProfile.class)
            .field("organizationId").equal(organization.id)
            .count();

        if (profileCount > 50) {
            ErrorData entity = new ErrorData();
            entity.error = "limit";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(entity)
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

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("/{id}/event")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", write=true)
    public Response createEvent(
        @PathObject("id") Organization organization,
        @ValidEntity Event event
    ) {
        ResponseBuilder builder;

        long activeEventsCount = this.datastore.createQuery(Event.class)
            .field("status").equal(EventService.STATUS_ACTIVE)
            .field("organizationId").equal(organization.id)
            .count();

        boolean hasFeatureViolation;

        StripeException stripeException = null;

        if (event.status == EventService.STATUS_ACTIVE) {
            activeEventsCount++;
            List<Subscription> subscriptions = null;

            try {
                subscriptions = this.stripeService.getCurrentSubscriptions(organization.id);
            } catch (StripeException e) {
                stripeException = e;
            }

            hasFeatureViolation = subscriptions != null && activeEventsCount > subscriptions.size();
        } else {
            hasFeatureViolation = false;
        }

        boolean hasValidEntityAssetConsentId = event.entityAssetConsentId == null ||
            this.datastore.createQuery(EntityAsset.class)
                .field("entityId").equal(event.organizationId.toString())
                .field("entityType").equal(EntityType.ORGANIZATION)
                .field("id").equal(event.entityAssetConsentId)
                .count() == 1;

        boolean hasValidPaymentProfile = event.paymentProfileId == null ||
            this.datastore.createQuery(PaymentProfile.class)
                .field("id").equal(event.paymentProfileId)
                .count() > 0;

        long eventCount = this.datastore.createQuery(Event.class)
            .field("organizationId").equal(organization.id)
            .count();

        if (!hasValidEntityAssetConsentId) {
            ErrorData errorData =  new ErrorData();
            errorData.error = "entityAssetConsentId";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON);
        } else if (!hasValidPaymentProfile) {
            ErrorData errorData =  new ErrorData();
            errorData.error = "paymentProfileId";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON);
        } else if (stripeException != null) {
            builder = Response.status(Status.SERVICE_UNAVAILABLE);
        } else if (eventCount > 100) {
            ErrorData errorData =  new ErrorData();
            errorData.error = "limit";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON);
        } else if (hasFeatureViolation) {
            ErrorData errorData =  new ErrorData();
            errorData.error = "subscription";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON);
        } else {
            Date currentTime = new Date();

            event.id = null;
            event.userData = new ArrayList<>();
            event.hash = this.eventService.getHash();
            event.organizationId = organization.id;
            event.timeUpdated = currentTime;
            event.timeCreated = currentTime;
            event.lastActivity = currentTime;

            Key<Event> key = this.datastore.save(event);

            Event searchedEvent = this.datastore.get(Event.class, key.getId());

            this.notificationService.notify(Arrays.asList(searchedEvent), "event.create");
            this.entityService.cascadeLastActivity(Arrays.asList(searchedEvent), currentTime);

            builder = Response.ok(searchedEvent).type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @POST
    @Authorized
    @Consumes(MediaType.APPLICATION_JSON)
    public Response create(
        @Context DecodedJWT jwt,
        @ValidEntity Organization organization
    ) {
        ResponseBuilder builder;

        long sameEmailCount = this.datastore.createQuery(Organization.class)
            .field("email").equalIgnoreCase(organization.email)
            .count();

        if (sameEmailCount == 0) {
            long organizationsCreatedByUser = this.datastore.createQuery(Organization.class)
                .field("createdBy").equal(jwt.getSubject())
                .count();

            if (organizationsCreatedByUser > this.maximumOrganizationsPerUser) {
                ErrorData entity = new ErrorData();
                entity.error = "limit";
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                Organization rootOrganization = this.organizationService.getRootOrganization();
                Date currentTime = new Date();

                organization.id = null;
                organization.createdBy = jwt.getSubject();
                organization.parentId = rootOrganization.id;
                organization.emailConfirmed = false;
                organization.timeCreated = currentTime;
                organization.timeUpdated = currentTime;
                organization.lastActivity = currentTime;

                Key<Organization> key = this.datastore.save(organization);

                Permission permission = new Permission();
                permission.write = true;
                permission.read = true;
                permission.user_id = jwt.getSubject();
                permission.objectClass = "Organization";
                permission.objectId = (ObjectId)key.getId();
                permission.timeCreated = currentTime;

                this.datastore.save(permission);

                List<Object> entities = new ArrayList<>();
                entities.add(organization);
                entities.add(permission);

                this.notificationService.notify(entities, "organization.create");

                Organization entity = this.datastore.get(Organization.class, key.getId());
                builder = Response.ok(entity, MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.CONFLICT);
        }

        return builder.build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Path("{id}")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", write=true)
    public Response update(
        @PathObject("id") Organization searchedOrganization,
        @ValidEntity Organization organization
    ) {
        ResponseBuilder builder;

        Query<Organization> query = this.datastore.createQuery(Organization.class)
            .field("id").notEqual(searchedOrganization.id)
            .field("email").equalIgnoreCase(organization.email);

        long sameEmailCount = query.count();

        if (sameEmailCount == 0) {
            boolean parentIdChangeViolation = !Objects.equals(searchedOrganization.parentId, organization.parentId);

            if (parentIdChangeViolation) {
                ErrorData entity = new ErrorData();
                entity.error = "parentId";
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(entity)
                    .type(MediaType.APPLICATION_JSON);
            } else {
                organization.id = searchedOrganization.id;
                organization.parentId = searchedOrganization.parentId;
                organization.createdBy = searchedOrganization.createdBy;
                organization.emailConfirmed = searchedOrganization.emailConfirmed;
                organization.timeCreated = searchedOrganization.timeCreated;
                organization.timeUpdated = new Date();
                organization.lastActivity = new Date();

                Key<Organization> key = this.datastore.save(organization);
                Organization entity = this.datastore.get(Organization.class, key.getId());

                this.notificationService.notify(Arrays.asList(entity), "organization.update");

                builder = Response.ok(entity, MediaType.APPLICATION_JSON);
            }
        } else {
            builder = Response.status(Status.CONFLICT);
        }

        return builder.build();
    }

    @GZip
    @GET
    @Path("{id}")
    public Response read(@PathObject("id") Organization organization) {
        ResponseBuilder builder;

        if (organization == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            builder = Response.ok(organization, MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @GZip
    @GET
    @Path("{id}/user")
    @Authorized
    @HasPermission(id="id", clazz=Organization.class, read=true)
    public Response getUsers(@PathObject("id") Organization organization) throws Auth0Exception {
        String query = this.datastore.createQuery(Permission.class)
            .field("objectClass").equal("Organization")
            .field("objectId").equal(organization.id)
            .asList()
            .stream()
            .map(permission -> "user_id: \"" + permission.user_id + "\"")
            .collect(Collectors.joining(" OR "));

        ManagementAPI api = new ManagementAPI(
            this.configuration.oauth.domain,
            this.auth0Service.getToken().get("access_token").asText()
        );

        UserFilter filter = new UserFilter();
        filter.withQuery(query);

        List<com.auth0.json.mgmt.users.User> users = api.users().list(filter).execute().getItems();
        return Response.ok(users, MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Path("{id}")
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", write=true)
    public Response delete(@PathObject("id") Organization searchedOrganization) {
        ResponseBuilder builder;

        long count = this.datastore.createQuery(Event.class)
            .field("organizationId").equal(searchedOrganization.id)
            .count();

        if (count > 0) {
            ErrorData errorData = new ErrorData();
            errorData.error = "count";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(errorData)
                .type(MediaType.APPLICATION_JSON);
        } else if (searchedOrganization.parentId == null) {
            builder = Response.status(Status.BAD_REQUEST);
        } else {
            this.datastore.delete(Organization.class, searchedOrganization.id);

            Query<Permission> query = this.datastore.createQuery(Permission.class)
                .field("objectId").equal(searchedOrganization.id)
                .field("objectClass").equal("Organization");

            List<Permission> permisisons = query.asList();

            Collection<Object> entities = new ArrayList<>();
            entities.add(searchedOrganization);
            entities.addAll(permisisons);

            this.datastore.delete(query);
            this.notificationService.notify(entities, "organization.delete");

            builder = Response.ok();
        }

        return builder.build();
    }

    @GET
    @Path("{id}/payment/profile")
    @GZip
    @Authorized
    @HasPermission(clazz=Organization.class, id="id", read=true)
    public Response getPaymentProfiles(
        @PathObject("id") Organization organization
    ) {
        List<PaymentProfile> profiles = this.datastore.createQuery(PaymentProfile.class)
            .field("organizationId").equal(organization.id)
            .asList();
        return Response.ok(profiles, MediaType.APPLICATION_JSON).build();
    }
}