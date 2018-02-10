import java.io.File;
import java.io.IOException;

import org.junit.Test;

import com.jivecake.api.APIConfiguration;

import io.dropwizard.configuration.ConfigurationException;
import io.dropwizard.configuration.ConfigurationFactory;
import io.dropwizard.configuration.ConfigurationFactoryFactory;
import io.dropwizard.configuration.DefaultConfigurationFactoryFactory;
import io.dropwizard.jackson.Jackson;
import io.dropwizard.validation.BaseValidator;

public class ConfigurationTest {
    @Test
    public void defaultConfigurationLoads() throws IOException, ConfigurationException {
        ConfigurationFactoryFactory<APIConfiguration> factory = new DefaultConfigurationFactoryFactory<>();
        File file = new File("docker/settings-test.yml");
        ConfigurationFactory<APIConfiguration> configFactory = factory.create(APIConfiguration.class, BaseValidator.newValidator(), Jackson.newObjectMapper(), "");
        configFactory.build(file);
    }
}
