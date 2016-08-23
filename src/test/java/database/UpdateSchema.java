package database;

import java.util.Arrays;

import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;

import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class UpdateSchema {
    @Test
    public void updateSchema() {
        MongoClient mongoClient = new MongoClient(Arrays.asList(new ServerAddress("127.0.0.1", 27017)));
        Morphia morphia = new Morphia();
        Datastore datastore = morphia.createDatastore(mongoClient, "jiveCakeMorphia");

       /*
          Perform schema update here
       */

        mongoClient.close();
    }
}