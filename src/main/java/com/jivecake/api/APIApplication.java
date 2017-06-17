package com.jivecake.api;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.bson.types.ObjectId;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.api.TypeLiteral;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MapperOptions;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.filter.AuthorizedFilter;
import com.jivecake.api.filter.CORSFilter;
import com.jivecake.api.filter.ClaimsFactory;
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
import com.jivecake.api.resources.ItemResource;
import com.jivecake.api.resources.LogResource;
import com.jivecake.api.resources.NotificationsResource;
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
import com.jivecake.api.service.CloudStorageService;
import com.jivecake.api.service.EntityService;
import com.jivecake.api.service.EventService;
import com.jivecake.api.service.HttpService;
import com.jivecake.api.service.ItemService;
import com.jivecake.api.service.NotificationService;
import com.jivecake.api.service.OrganizationService;
import com.jivecake.api.service.PaypalService;
import com.jivecake.api.service.PermissionService;
import com.jivecake.api.service.StripeService;
import com.jivecake.api.service.TransactionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

import io.dropwizard.Application;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Environment;

public class APIApplication extends Application<APIConfiguration> {
    private final List<Class<?>> filters = Arrays.asList(
        AuthorizedFilter.class,
        HasPermissionFilter.class,
        LimitUserRequestFilter.class,
        LogFilter.class,
        OAuthConfiguration.class,
        OptionsProcessor.class,
        QueryRestrictFilter.class
    );

    private final List<Class<?>> resources = Arrays.asList(
        Auth0Resource.class,
        AssetResource.class,
        CloudStorageService.class,
        ConnectionResource.class,
        EventResource.class,
        ItemResource.class,
        LogResource.class,
        NotificationsResource.class,
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
        EntityService.class,
        EventService.class,
        HttpService.class,
        ItemService.class,
        NotificationService.class,
        OrganizationService.class,
        PaypalService.class,
        PermissionService.class,
        StripeService.class,
        TransactionService.class
    );

    public static void main(String[] args) throws Exception {
        new APIApplication().run(args);
    }

    @Override
    public void run(APIConfiguration configuration, Environment environment) {
        MongoClient mongoClient = new MongoClient(configuration.databases
            .stream()
            .map(url -> new ServerAddress(url))
            .collect(Collectors.toList())
        );
        Morphia morphia = new Morphia();
        morphia.mapPackage("com.jivecake.api.model");
        MapperOptions options = morphia.getMapper().getOptions();
        options.setStoreEmpties(true);

        Datastore datastore = morphia.createDatastore(mongoClient, "jiveCakeMorphia");
        datastore.ensureIndexes();

        com.jivecake.api.model.Application application = new com.jivecake.api.model.Application();
        application.id = new ObjectId("55865027c1fcce003aa0aa43");

        ObjectId rootId = new ObjectId("55865027c1fcce003aa0aa40");
        Organization rootOrganization = datastore.get(Organization.class, rootId);

        if (rootOrganization == null) {
            rootOrganization = new Organization();
            rootOrganization.id = rootId;
            rootOrganization.children = new ArrayList<>();
            rootOrganization.name = "JiveCake";
            rootOrganization.email = "luis@trois.io";
            rootOrganization.timeCreated = new Date();
            datastore.save(rootOrganization);
        }

        List<JWTVerifier> verifiers = new ArrayList<>();

        try {
            verifiers.add(
                JWT.require(Algorithm.HMAC256(configuration.oauth.webClientSecret))
                .withIssuer(String.format("https://%s/", configuration.oauth.domain))
                .build()
            );
        } catch (IllegalArgumentException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }

        ApplicationService applicationService = new ApplicationService(application);
        OrganizationService organizationService = new OrganizationService(datastore);

        PermissionService permissionService = new PermissionService(
            datastore,
            applicationService,
            organizationService
        );

        this.establishRootUsers(
            permissionService,
            application,
            rootOrganization,
            configuration.rootOAuthIds
        );

        JerseyEnvironment jersey = environment.jersey();
        DropwizardResourceConfig resourceConfiguration = jersey.getResourceConfig();
        resourceConfiguration.register(datastore);
        resourceConfiguration.register(new AbstractBinder() {
            @Override
            protected void configure() {
                this.bind(verifiers).to(new TypeLiteral<List<JWTVerifier>>() {});
                this.bind(new HashDateCount()).to(HashDateCount.class);

                this.bind(new ApplicationService(application)).to(ApplicationService.class);
                this.bind(datastore).to(Datastore.class);
                this.bind(configuration.oauth).to(OAuthConfiguration.class);
                this.bind(configuration.stripe).to(StripeConfiguration.class);
                this.bind(configuration).to(APIConfiguration.class);

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
        PermissionService permissionService,
        com.jivecake.api.model.Application application,
        Organization organization,
        List<String> user_ids
    ) {
        Date time = new Date();

        Collection<Permission> permissions = new ArrayList<>();

        for (String user_id: user_ids) {
            Permission organizationPermission = new Permission();
            organizationPermission.include = PermissionService.ALL;
            organizationPermission.permissions = new HashSet<>();
            organizationPermission.objectClass = Organization.class.getSimpleName();
            organizationPermission.objectId = organization.id;
            organizationPermission.timeCreated = time;
            organizationPermission.user_id = user_id;

            Permission applicationPermission = new Permission();
            applicationPermission.include = PermissionService.ALL;
            applicationPermission.permissions = new HashSet<>();
            applicationPermission.objectClass = Application.class.getSimpleName();
            applicationPermission.objectId = application.id;
            applicationPermission.timeCreated = time;
            applicationPermission.user_id = user_id;

            permissions.add(organizationPermission);
            permissions.add(applicationPermission);
        }

        permissionService.write(permissions);
    }
}
