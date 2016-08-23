package com.jivecake.api;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

import javax.inject.Singleton;

import org.bson.types.ObjectId;
import org.glassfish.hk2.api.InjectionResolver;
import org.glassfish.hk2.utilities.binding.AbstractBinder;
import org.glassfish.jersey.process.internal.RequestScoped;
import org.glassfish.jersey.server.spi.internal.ValueFactoryProvider;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MapperOptions;

import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.internal.org.apache.commons.codec.binary.Base64;
import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import com.jivecake.api.filter.AuthorizedFilter;
import com.jivecake.api.filter.CORSFilter;
import com.jivecake.api.filter.ClaimsFactory;
import com.jivecake.api.filter.LogFilter;
import com.jivecake.api.filter.OptionsProcessor;
import com.jivecake.api.filter.PathObject;
import com.jivecake.api.filter.PathObjectInjectionResolver;
import com.jivecake.api.filter.PathObjectValueFactoryProvider;
import com.jivecake.api.filter.QueryRestrictFilter;
import com.jivecake.api.filter.SingletonFactory;
import com.jivecake.api.model.Organization;
import com.jivecake.api.model.Permission;
import com.jivecake.api.resources.Auth0Resource;
import com.jivecake.api.resources.ConnectionResource;
import com.jivecake.api.resources.EventResource;
import com.jivecake.api.resources.FeatureResource;
import com.jivecake.api.resources.ItemResource;
import com.jivecake.api.resources.LogResource;
import com.jivecake.api.resources.NotificationsResource;
import com.jivecake.api.resources.OrganizationResource;
import com.jivecake.api.resources.PaymentProfileResource;
import com.jivecake.api.resources.PaypalResource;
import com.jivecake.api.resources.PermissionResource;
import com.jivecake.api.resources.TransactionResource;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.IndexedOrganizationNodeService;
import com.jivecake.api.service.PermissionService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

import io.dropwizard.Application;
import io.dropwizard.assets.AssetsBundle;
import io.dropwizard.configuration.EnvironmentVariableSubstitutor;
import io.dropwizard.configuration.SubstitutingSourceProvider;
import io.dropwizard.jersey.DropwizardResourceConfig;
import io.dropwizard.jersey.setup.JerseyEnvironment;
import io.dropwizard.setup.Bootstrap;
import io.dropwizard.setup.Environment;

public class APIApplication extends Application<APIConfiguration> {
    private final List<Class<?>> registerClasses = Arrays.asList(
        AuthorizedFilter.class,
        CORSFilter.class,
        LogFilter.class,
        OAuthConfiguration.class,
        OptionsProcessor.class,
        QueryRestrictFilter.class
    );

    private final List<Class<?>> resourceClasses = Arrays.asList(
        Auth0Resource.class,
        ConnectionResource.class,
        EventResource.class,
        FeatureResource.class,
        ItemResource.class,
        LogResource.class,
        NotificationsResource.class,
        OrganizationResource.class,
        PaymentProfileResource.class,
        PaypalResource.class,
        PermissionResource.class,
        TransactionResource.class
    );

    public static void main(String[] args) throws Exception {
        new APIApplication().run(args);
    }

    @Override
    public String getName() {
        return "JiveCakeAPI";
    }

    @Override
    public void initialize(Bootstrap<APIConfiguration> bootstrap) {
        bootstrap.setConfigurationSourceProvider(
            new SubstitutingSourceProvider(
                bootstrap.getConfigurationSourceProvider(),
                new EnvironmentVariableSubstitutor(false)
            )
        );

        bootstrap.addBundle(new AssetsBundle());
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

        Organization organization = new Organization();
        organization.id = new ObjectId("55865027c1fcce003aa0aa40");
        organization.name = "JiveCake";
        organization.email = "JiveCake@gmail.com";
        organization.timeCreated = new Date();
        datastore.save(organization);

        List<JWTVerifier> verifiers = new ArrayList<>(Arrays.asList(
            new JWTVerifier(
                new Base64(true).decode(configuration.oauth.webClientSecret),
                configuration.oauth.webClientId
            ),
            new JWTVerifier(
                new Base64(true).decode(configuration.oauth.nativeClientSecret),
                configuration.oauth.nativeClientId
            )
        ));

        Injector injector = Guice.createInjector(
            (binder) -> {
                binder.bind(new TypeLiteral<List<JWTVerifier>>() {}).toInstance(verifiers);
                binder.bind(ApplicationService.class).toInstance(new ApplicationService(application));
                binder.bind(Datastore.class).toInstance(datastore);
                binder.bind(OAuthConfiguration.class).toInstance(configuration.oauth);
            }
        );

        injector.getInstance(IndexedOrganizationNodeService.class).writeIndexedOrganizationNodes(organization.id);

        this.establishRootUsers(
            injector.getInstance(PermissionService.class),
            application,
            organization,
            configuration.rootOAuthIds
        );

        JerseyEnvironment jersey = environment.jersey();

        DropwizardResourceConfig resourceConfiguration = jersey.getResourceConfig();
        resourceConfiguration.register(datastore);

        resourceConfiguration.register(new AbstractBinder() {
            @Override
            protected void configure() {
                SingletonFactory<Datastore> datastoreFactory = new SingletonFactory<>(datastore);
                this.bindFactory(datastoreFactory).to(Datastore.class).in(Singleton.class);

                SingletonFactory<Auth0Service> auth0Factory = new SingletonFactory<>(injector.getInstance(Auth0Service.class));
                this.bindFactory(auth0Factory).to(Auth0Service.class).in(Singleton.class);

                bind(PathObjectValueFactoryProvider.class).to(ValueFactoryProvider.class).in(Singleton.class);
                bind(PathObjectInjectionResolver.class).to(new org.glassfish.hk2.api.TypeLiteral<InjectionResolver<PathObject>>() {}).in(Singleton.class);

                this.bindFactory(ClaimsFactory.class).to(JsonNode.class).in(RequestScoped.class);
            }
        });

        List<Class<?>> registerClasses = new ArrayList<>();
        registerClasses.addAll(this.resourceClasses);
        registerClasses.addAll(this.registerClasses);

        for (Class<? extends Object> clazz : registerClasses) {
            jersey.register(injector.getInstance(clazz));
        }
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
            organizationPermission.include = permissionService.getIncludeAllPermission();
            organizationPermission.objectClass = Organization.class.getSimpleName();
            organizationPermission.objectId = organization.id;
            organizationPermission.timeCreated = time;
            organizationPermission.user_id = user_id;

            Permission applicationPermission = new Permission();
            applicationPermission.include = permissionService.getIncludeAllPermission();
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
