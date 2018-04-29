package com.jivecake.api.resources;

import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.bson.types.ObjectId;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Key;
import org.mongodb.morphia.query.Query;
import org.mongodb.morphia.query.UpdateOperations;

import com.jivecake.api.filter.Authorized;
import com.jivecake.api.filter.CORS;
import com.jivecake.api.filter.HasPermission;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.ValidEntity;
import com.jivecake.api.model.FormField;
import com.jivecake.api.model.FormFieldResponse;
import com.jivecake.api.model.Transaction;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.NotificationService;

@Path("form")
@CORS
@Singleton
public class FormResource {
    private final Datastore datastore;
    private final NotificationService notificationservice;
    private final EntityService entityService;

    @Inject
    public FormResource(
        Datastore datastore,
        NotificationService notificationservice,
        EntityService entityService
    ) {
        this.datastore = datastore;
        this.notificationservice = notificationservice;
        this.entityService = entityService;
    }

    @GET
    @Authorized
    @Path("field/{id}")
    @HasPermission(clazz=FormField.class, id="id", read=true)
    public Response getFormField(@PathObject(value = "id") FormField field) {
        return Response.ok(field, MediaType.APPLICATION_JSON).build();
    }

    @GET
    @Authorized
    @Path("response/{id}")
    @HasPermission(clazz=FormFieldResponse.class, id="id", read=true)
    public Response getFormField(@PathObject(value = "id") FormFieldResponse response) {
        return Response.ok(response, MediaType.APPLICATION_JSON).build();
    }

    @DELETE
    @Authorized
    @Path("field/{id}")
    @HasPermission(clazz=FormField.class, id="id", write=true)
    public Response deleteFormField(@PathObject(value = "id") FormField field) {
        List<ObjectId> ids = this.datastore.createQuery(FormFieldResponse.class)
            .field("eventId").equal(field.eventId)
            .field("formFieldId").equal(field.id)
            .asKeyList()
            .stream()
            .map(key -> (ObjectId)key.getId())
            .collect(Collectors.toList());

        this.datastore.delete(FormFieldResponse.class, ids);

        if (!ids.isEmpty()) {
            UpdateOperations<Transaction> operations = this.datastore
                .createUpdateOperations(Transaction.class)
                .removeAll("formFieldResponseIds", ids);

            Query<Transaction> query = this.datastore.createQuery(Transaction.class)
                .field("eventId")
                .equal(field.eventId);

            this.datastore.update(query, operations);
        }

        this.datastore.delete(field);

        this.notificationservice.notify(Arrays.asList(field), "formField.delete");
        this.entityService.cascadeLastActivity(Arrays.asList(field), new Date());

        return Response.ok().build();
    }

    @DELETE
    @Authorized
    @Path("respose/{id}")
    @HasPermission(clazz=FormFieldResponse.class, id="id", write=true)
    public Response deleteFormField(@PathObject(value = "id") FormFieldResponse response) {
        this.datastore.delete(response);
        this.notificationservice.notify(Arrays.asList(response), "formFieldResponse.delete");
        this.entityService.cascadeLastActivity(Arrays.asList(response), new Date());
        return Response.ok().build();
    }

    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Authorized
    @Path("field/{id}")
    @HasPermission(clazz=FormField.class, id="id", write=true)
    public Response updateFormField(
        @PathObject(value = "id") FormField original,
        @ValidEntity FormField field
    ) {
        field.id = original.id;
        field.eventId = original.eventId;
        field.item = original.item;
        field.event = original.event;
        field.timeUpdated = new Date();
        field.timeCreated = original.timeCreated;

        Key<FormField> key = this.datastore.save(field);
        FormField after = this.datastore.getByKey(FormField.class, key);

        this.notificationservice.notify(Arrays.asList(field), "formField.update");
        this.entityService.cascadeLastActivity(Arrays.asList(field), new Date());

        return Response.ok(after, MediaType.APPLICATION_JSON).build();
    }
}
