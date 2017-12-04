package com.jivecake.api.resources;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
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

import com.auth0.client.mgmt.ManagementAPI;
import com.auth0.client.mgmt.filter.UserFilter;
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
import com.jivecake.api.model.AssetType;
import com.jivecake.api.model.EntityAsset;
import com.jivecake.api.model.EntityType;
import com.jivecake.api.model.Event;
import com.jivecake.api.model.Item;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.PaymentProfile;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.model.UserData;
import com.jivecake.api.request.AggregatedEvent;
import com.jivecake.api.request.ErrorData;
import com.jivecake.api.request.ItemData;
import com.jivecake.api.request.Paging;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.stripe.exception.StripeException;
import com.stripe.model.Subscription;

@Path("event")
@CORS
@Singleton
public class EventResource {
    private final Auth0Service auth0Service;
    private final EventService eventService;
    private final ItemService itemService;
    private final TransactionService transactionService;
    private final StripeService stripeService;
    private final EntityService entityService;
    private final NotificationService notificationService;
    private final Datastore datastore;
    private final APIConfiguration configuration;

    @Inject
    public EventResource(
        Auth0Service auth0Service,
        EventService eventService,
        ItemService itemService,
        TransactionService transactionService,
        StripeService stripeService,
        EntityService entityService,
        NotificationService notificationService,
        Datastore datastore,
        APIConfiguration configuration
    ) {
        this.auth0Service = auth0Service;
        this.itemService = itemService;
        this.eventService = eventService;
        this.transactionService = transactionService;
        this.stripeService = stripeService;
        this.entityService = entityService;
        this.notificationService = notificationService;
        this.datastore = datastore;
        this.configuration = configuration;
    }

    @GZip
    @GET
    @Path("/{eventId}/aggregated")
    public Response getAggregatedItemData(
        @PathObject("eventId") Event event,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else if (event.status == EventService.STATUS_ACTIVE) {
            AggregatedEvent group = this.eventService.getAggregatedaEventData(
                event,
                this.transactionService,
                new Date()
            );

            group.event.userData = null;
            group.organization.children = null;
            group.itemData = group.itemData.stream()
                .filter(itemData -> itemData.item.status == ItemService.STATUS_ACTIVE)
                .collect(Collectors.toList());

            for (ItemData datum: group.itemData) {
                for (Transaction transaction: datum.transactions) {
                    if (jwt == null || !jwt.getSubject().equals(transaction.user_id)) {
                        transaction.user_id = null;
                    }

                    transaction.given_name = null;
                    transaction.middleName = null;
                    transaction.family_name = null;
                }
            }

            builder = Response.ok(group).type(MediaType.APPLICATION_JSON);
        } else {
            ErrorData error = new ErrorData();
            error.error = "status";
            builder = Response.status(Status.BAD_REQUEST)
                .entity(error)
                .type(MediaType.APPLICATION_JSON);
        }

        return builder.build();
    }

    @GET
    @Path("{id}/userData/{userId}")
    @Authorized
    public Response getUserData(
        @PathObject("id") Event event,
        @PathParam("userId") String userId,
        @Context DecodedJWT jwt
    ) {
        ResponseBuilder builder;

        if (event == null) {
            builder = Response.status(Status.NOT_FOUND);
        } else {
            if (jwt.getSubject().equals(userId)) {
                Optional<UserData> optional = event.userData
                    .stream()
                    .filter(userData -> jwt.getSubject().equals(userData.userId))
                    .findFirst();

                if (optional.isPresent()) {
                    builder = Response.ok(optional.get(), MediaType.APPLICATION_JSON);
                } else {
                    builder = Response.status(Status.NOT_FOUND);
                }
            } else {
                builder = Response.status(Status.UNAUTHORIZED);
            }
        }

        return builder.build();
    }

    @GZip
    @GET
    @Path("search")
    public Response publicSearch(
        @QueryParam("id") ObjectId id,
        @QueryParam("hash") String hash,
        @QueryParam("order") String order,
        @QueryParam("text") String text
    ) {
        Query<Event> query = this.datastore.createQuery(Event.class)
            .project("userData", false)
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

        if (id != null) {
            query.field("id").equal(id);
        }

        if (hash != null) {
            query.field("hash").equal(hash);
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

            boolean hasValidEntityAssetConsentId = event.entityAssetConsentId == null ||
            this.datastore.createQuery(EntityAsset.class)
                .field("entityId").equal(original.organizationId.toString())
                .field("entityType").equal(EntityType.ORGANIZATION)
                .field("id").equal(event.entityAssetConsentId)
                .count() == 1;

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

            if (!hasValidEntityAssetConsentId) {
                ErrorData errorData =  new ErrorData();
                errorData.error = "entityAssetConsentId";
                builder = Response.status(Status.BAD_REQUEST)
                    .entity(errorData)
                    .type(MediaType.APPLICATION_JSON);
            } else if (!hasValidPaymentProfile) {
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
                event.hash = original.hash;
                event.userData = original.userData;
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

            if (item.countAmounts != null && item.countAmounts.isEmpty()) {
                item.countAmounts = null;
            }

            if (item.timeAmounts != null && item.timeAmounts.isEmpty()) {
                item.timeAmounts = null;
            }

            item.id = null;
            item.eventId = event.id;
            item.organizationId = event.organizationId;
            item.timeCreated = currentTime;
            item.timeUpdated = null;
            item.countAmounts = null;
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
    @Path("/{id}")
    @Authorized
    @HasPermission(id="id", clazz=Event.class, permission=PermissionService.READ)
    public Response read(@PathObject("id") Event event) {
        return Response.ok(event).type(MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Path("{id}/excel")
    @HasPermission(id="id", clazz=Event.class, permission=PermissionService.READ)
    public Response requestExcel(
        @PathObject("id") Event event,
        @Context DecodedJWT jwt
    ) throws IOException {
        File file = File.createTempFile("transactions", ".xlsx");

        Query<Transaction> query = this.datastore.createQuery(Transaction.class)
            .field("eventId").equal(event.id)
            .field("leaf").equal(true);

        List<Transaction> transactions = query.asList();

        String userQuery = transactions.stream()
            .filter(transaction -> transaction.user_id != null)
            .map(transaction -> String.format("user_id: \"%s\"", transaction.user_id))
            .collect(Collectors.joining(" OR "));

        File writeFile = file;

        ManagementAPI api = new ManagementAPI(
            this.configuration.oauth.domain,
            this.auth0Service.getToken().get("access_token").asText()
        );

        List<com.auth0.json.mgmt.users.User> users = api.users().list(new UserFilter().withQuery(userQuery))
            .execute()
            .getItems();

        EventResource.this.transactionService.writeToExcel(
            event,
            users,
            transactions,
            file
        );

        Storage storage = StorageOptions.getDefaultInstance().getService();
        String name = UUID.randomUUID().toString();
        BlobInfo info = BlobInfo.newBuilder(BlobId.of(EventResource.this.configuration.gcp.bucket, name))
            .setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
            .setAcl(Arrays.asList(Acl.of(User.ofAllUsers(), Role.READER)))
            .setStorageClass(StorageClass.REGIONAL)
            .build();

        byte[] bytes = Files.readAllBytes(writeFile.toPath());
        Blob blob = storage.create(info, bytes);

        EntityAsset asset = new EntityAsset();
        asset.assetId = blob.getBucket() + "/" + blob.getName();
        asset.assetType = AssetType.ORGANIZATION_EXCEL;
        asset.entityId = event.organizationId.toString();
        asset.entityType = EntityType.ORGANIZATION;
        asset.timeCreated = new Date();

        EventResource.this.datastore.save(asset);
        EventResource.this.notificationService.notify(Arrays.asList(asset), "asset.create");

        return Response.ok(asset).type(MediaType.APPLICATION_JSON).build();
    }
}