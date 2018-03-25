package com.jivecake.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import javax.inject.Singleton;

import org.bson.types.ObjectId;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.FindAndModifyOptions;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.query.UpdateOpsImpl;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.cron.Hourly;
import com.jivecake.api.filter.AuthorizedFilter;
import com.jivecake.api.filter.CORSFilter;
import com.jivecake.api.filter.ClaimsFactory;
import com.jivecake.api.filter.ExceptionMapper;
import com.jivecake.api.filter.GZIPWriterInterceptor;
import com.jivecake.api.filter.HasPermissionFilter;
import com.jivecake.api.filter.HashDateCount;
import com.jivecake.api.filter.LimitUserRequestFilter;
import com.jivecake.api.filter.LogFilter;
import com.jivecake.api.filter.OptionsProcessor;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.PathObjectInjectionResolver;
import com.jivecake.api.filter.PathObjectValueFactoryProvider;
import com.jivecake.api.filter.QueryRestrictFilter;
import com.jivecake.api.filter.SingletonFactory;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.resources.AssetResource;
import com.jivecake.api.resources.Auth0Resource;
import com.jivecake.api.resources.ConnectionResource;
import com.jivecake.api.resources.EventResource;
import com.jivecake.api.resources.FormResource;
import com.jivecake.api.resources.ItemResource;
import com.jivecake.api.resources.LocalisationResource;
import com.jivecake.api.resources.LogResource;
import com.jivecake.api.resources.NotificationsResource;
import com.jivecake.api.resources.OrganizationInvitationResource;
import com.jivecake.api.resources.OrganizationResource;
import com.jivecake.api.resources.PaymentProfileResource;
import com.jivecake.api.resources.PaypalResource;
import com.jivecake.api.resources.PermissionResource;
import com.jivecake.api.resources.StripeResource;
import com.jivecake.api.resources.ToolsResource;
import com.jivecake.api.resources.TransactionResource;
import com.jivecake.api.resources.UserResource;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.ClientConnectionService;
import com.jivecake.api.service.CronService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.ExcelService;
import com.jivecake.api.service.FormService;
import com.jivecake.api.service.GoogleCloudPlatformService;
import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.MandrillService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;

import io.dropwizard.Application;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;
import io.sentry.Sentry;
import io.sentry.SentryClient;

public class APIApplication extends Application<APIConfiguration> {
    private final List<Class<?>> filters = Arrays.asList(
        AuthorizedFilter.class,
        APIConfiguration.class,
        ExceptionMapper.class,
        GZIPWriterInterceptor.class,
        HasPermissionFilter.class,
        LimitUserRequestFilter.class,
        LogFilter.class,
        OptionsProcessor.class,
        QueryRestrictFilter.class
    );

    private final List<Class<?>> resources = Arrays.asList(
        Auth0Resource.class,
        AssetResource.class,
        ConnectionResource.class,
        EventResource.class,
        FormResource.class,
        ItemResource.class,
        LocalisationResource.class,
        LogResource.class,
        NotificationsResource.class,
        OrganizationInvitationResource.class,
        OrganizationResource.class,
        PaymentProfileResource.class,
        PaypalResource.class,
        PermissionResource.class,
        StripeResource.class,
        ToolsResource.class,
        TransactionResource.class,
        UserResource.class
    );

    private final List<Class<?>> services = Arrays.asList(
        ApplicationService.class,
        Auth0Service.class,
        ClientConnectionService.class,
        CronService.class,
        EntityService.class,
        EventService.class,
        ExcelService.class,
        FormService.class,
        GoogleCloudPlatformService.class,
        HttpService.class,
        ItemService.class,
        MandrillService.class,
        NotificationService.class,
        OrganizationService.class,
        PermissionService.class,
        StripeService.class,
        TransactionService.class
    );

    public static void main(String[] args) throws Exception {
        new APIApplication().run(args);
    }

    @Override
    public void run(APIConfiguration configuration, Environment environment) {
        MongoClient client = ApplicationService.getClient(configuration);
        Morphia morphia = ApplicationService.getMorphia(client);
        Datastore datastore = ApplicationService.getDatastore(morphia, client, "jiveCakeMorphia");

        com.jivecake.api.model.Application application = new com.jivecake.api.model.Application();
        application.id = new ObjectId("55865027c1fcce003aa0aa43");

        ObjectId rootId = new ObjectId("55865027c1fcce003aa0aa40");
        Organization rootOrganization = datastore.get(Organization.class, rootId);

        if (rootOrganization == null) {
            rootOrganization = new Organization();
            rootOrganization.id = rootId;
            rootOrganization.name = "JiveCake";
            rootOrganization.email = "luis@trois.io";
            rootOrganization.createdBy = "google-oauth2|105220434348009698992";
            rootOrganization.lastActivity = new Date();
            rootOrganization.timeCreated = new Date();
            datastore.save(rootOrganization);
        }

        PermissionService permissionService = new PermissionService(datastore);

        if (configuration.rootOAuthIds != null) {
            this.establishRootUsers(
                datastore,
                morphia,
                permissionService,
                application,
                rootOrganization,
                configuration.rootOAuthIds
            );
        }

        JerseyEnvironment jersey = environment.jersey();
        DropwizardResourceConfig resourceConfiguration = jersey.getResourceConfig();
        resourceConfiguration.register(datastore);
        resourceConfiguration.register(new AbstractBinder() {
            @Override
            protected void configure() {
                this.bind(new HashDateCount()).to(HashDateCount.class);

                SentryClient client = Sentry.init(configuration.sentry.dsn);
                client.setEnvironment(configuration.sentry.environment);

                this.bind(client).to(SentryClient.class);
                this.bind(new ApplicationService(application)).to(ApplicationService.class);
                this.bind(datastore).to(Datastore.class);
                this.bind(configuration).to(APIConfiguration.class);
                this.bind(Hourly.class).to(Hourly.class).in(Singleton.class);

                for (Class<?> clazz: APIApplication.this.services) {
                    this.bind(clazz).to(clazz).in(Singleton.class);
                }

                SingletonFactory<Datastore> datastoreFactory = new SingletonFactory<>(datastore);
                this.bindFactory(datastoreFactory).to(Datastore.class).in(Singleton.class);

                bind(PathObjectValueFactoryProvider.class).to(ValueFactoryProvider.class).in(Singleton.class);
                bind(PathObjectInjectionResolver.class).to(new org.glassfish.hk2.api.TypeLiteral<InjectionResolver<PathObject>>() {}).in(Singleton.class);

                this.bindFactory(ClaimsFactory.class).to(DecodedJWT.class).in(RequestScoped.class);
            }
        });

        List<Class<?>> registerClasses = new ArrayList<>();
        registerClasses.addAll(this.resources);
        registerClasses.addAll(this.filters);

        for (Class<? extends Object> clazz : registerClasses) {
            jersey.register(clazz);
        }

        jersey.register(new CORSFilter(configuration.corsOrigins));
    }

    private void establishRootUsers(
        Datastore datastore,
        Morphia morphia,
        PermissionService permissionService,
        com.jivecake.api.model.Application application,
        Organization organization,
        List<String> userIds
    ) {
        for (String user_id: userIds) {
            Object[][] permissionTuples = {
                { "Organization", organization.id },
                { "Application",  application.id}
            };

            for (Object[] permissionTuple: permissionTuples) {
                datastore.findAndModify(
                    datastore.createQuery(Permission.class)
                        .field("objectClass").equal(permissionTuple[0])
                        .field("objectId").equal(permissionTuple[1])
                        .field("user_id").equal(user_id),
                    new UpdateOpsImpl<>(Permission.class, morphia.getMapper())
                        .set("read", true)
                        .set("write", true)
                        .set("objectClass", permissionTuple[0])
                        .set("objectId", permissionTuple[1])
                        .set("timeCreated", new Date())
                        .set("user_id", user_id),
                    new FindAndModifyOptions().upsert(true)
                );
            }
        }
    }
}
