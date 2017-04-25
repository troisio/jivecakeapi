import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.junit.After;
import org.junit.Before;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class Test {
    private Datastore datastore;
    private MongoClient client;

    @Before
    public void connect() {
        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        Morphia morphia = new Morphia();

        this.datastore = morphia.createDatastore(this.client, "jiveCakeMorphia");
    }

    @After
    public void disconnect() {
        //this.client.dropDatabase("test");
        this.client.close();
    }

    @org.junit.Test
    public void test() {
        Map<String, String> map = new HashMap<>();

        System.out.println(map.get(null));
    }
}