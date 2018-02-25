import java.io.File;
import java.io.IOException;

import org.junit.After;
import org.junit.Before;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.jivecake.api.APIConfiguration;
import com.jivecake.api.service.ApplicationService;
import com.mongodb.MongoClient;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.validation.BaseValidator;

public class DatastoreTest {
    public Datastore datastore;
    public MongoClient client;
    public Morphia morphia;

    @Before
    public void connect() throws IOException, ConfigurationException {
        APIConfiguration configuration = new DefaultConfigurationFactoryFactory<APIConfiguration>().create(
            APIConfiguration.class,
            BaseValidator.newValidator(),
            Jackson.newObjectMapper(),
            ""
        ).build(new File("docker/settings-test.yml"));

        this.client = ApplicationService.getClient(configuration);
        this.morphia = ApplicationService.getMorphia(client);
        this.datastore = ApplicationService.getDatastore(this.morphia, this.client, "test");
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }
}
