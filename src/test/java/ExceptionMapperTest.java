import static org.junit.Assert.assertEquals;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import javax.ws.rs.core.Response;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MapperOptions;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.filter.ExceptionMapper;
import com.jivecake.api.model.Application;
import com.jivecake.api.model.Event;
import com.jivecake.api.service.ApplicationService;
import com.jivecake.api.service.Auth0Service;
import com.jivecake.api.service.MandrillService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class ExceptionMapperTest {
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        Morphia morphia = new Morphia();
        morphia.map(Event.class);
        MapperOptions options = morphia.getMapper().getOptions();
        options.setStoreEmpties(true);

        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = morphia.createDatastore(this.client, "test");
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void exceptionMapperSends1EmailPerHour() {
        APIConfiguration configuration = new APIConfiguration();
        configuration.errorRecipients = Arrays.asList();

        ApplicationService applicationService = new ApplicationService(new Application(), this.datastore);
        Auth0Service auth0Service = new Auth0Service(configuration, applicationService);

        AtomicInteger emailsSent = new AtomicInteger();

        MandrillService mandrillService = new MandrillService(configuration) {
            @Override
            public Future<Response> send(Map<String, Object> message) {
                CompletableFuture<Response> future = new CompletableFuture<>();
                future.complete(null);
                emailsSent.incrementAndGet();
                return future;
            }
        };

        ExceptionMapper mapper = new ExceptionMapper(
            this.datastore,
            configuration,
            auth0Service,
            mandrillService,
            new MockHttpServeltRequest(),
            applicationService
        );

        mapper.toResponse(new IllegalArgumentException());
        mapper.toResponse(new IllegalArgumentException());
        mapper.toResponse(new IllegalArgumentException());

        assertEquals(1, emailsSent.get());
    }
}