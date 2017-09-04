import static org.junit.Assert.assertEquals;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mongodb.morphia.Datastore;
import org.mongodb.morphia.Morphia;
import org.mongodb.morphia.mapping.MapperOptions;

import com.jivecake.api.model.Event;
import com.jivecake.api.model.UserData;
import com.jivecake.api.service.EventService;
import com.mongodb.MongoClient;
import com.mongodb.ServerAddress;

public class EventUserNumberTest {
    private Datastore datastore;
    private MongoClient client;
    private EventService eventService;

    @Before
    public void connect() {
        Morphia morphia = new Morphia();
        morphia.map(Event.class);
        MapperOptions options = morphia.getMapper().getOptions();
        options.setStoreEmpties(true);

        this.client = new MongoClient(Arrays.asList(new ServerAddress(System.getProperty("db"))));
        this.datastore = morphia.createDatastore(this.client, "test");
        this.eventService = new EventService(this.datastore);
    }

    @After
    public void disconnect() {
        this.client.dropDatabase("test");
        this.client.close();
    }

    @Test
    public void assign1ToFirst() throws InterruptedException, ExecutionException {
        Event newEvent = new Event();
        newEvent.userData = new ArrayList<>();

        this.datastore.save(newEvent);
        Event eventToQuery = this.datastore.get(newEvent);

        CompletableFuture<Event> future = this.eventService.assignNumberToUserSafely("user|123", eventToQuery);

        Event event = future.get();

        UserData userData = event.userData.get(0);

        assertEquals(1, userData.number);
    }

    @Test
    public void concurrenctRequestAssignUniqueIntegers() throws InterruptedException, ExecutionException {
        Event newEvent = new Event();
        newEvent.userData = new ArrayList<>();
        this.datastore.save(newEvent);

        ExecutorService service = Executors.newFixedThreadPool(10);

        List<CompletableFuture<Event>> futures = Collections.synchronizedList(new ArrayList<>());

        for (int index = 0; index < 1000; index++) {
            String userid = "user|" + index;

            CompletableFuture<Event> future = new CompletableFuture<>();
            futures.add(future);

            service.execute(() -> {
               CompletableFuture<Event> assignmentFuture = this.eventService.assignNumberToUserSafely(
                    userid,
                    newEvent
                );

                try {
                    future.complete(assignmentFuture.get());
                } catch (InterruptedException | ExecutionException e) {
                    throw new RuntimeException(e);
                }
            });
        }

        for (CompletableFuture<Event> future: futures) {
            future.get();
        }

        Event event = this.datastore.get(newEvent);

        for (UserData userData: event.userData) {
            long appears = event.userData
                .stream()
                .filter(currentUserData -> userData.number == currentUserData.number)
                .count();

            assertEquals(1, appears);
        }
    }
}
