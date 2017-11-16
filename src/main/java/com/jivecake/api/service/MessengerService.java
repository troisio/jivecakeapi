package com.jivecake.api.service;

import java.io.File;
import java.io.IOException;

import javax.inject.Inject;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jivecake.api.APIConfiguration;

public class MessengerService {
    private final APIConfiguration configuration;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public MessengerService(APIConfiguration configuration) {
        this.configuration = configuration;
    }

    public JsonNode reply(String recipient, String message) throws IOException {
        ObjectNode node = mapper.createObjectNode();
        node.put("messaging_type", "RESPONSE");
        node.putObject("recipient").put("id", recipient);
        node.putObject("message").put("text", message);

        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messages")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .request()
            .buildPost(Entity.entity(node, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }

    public JsonNode setWhiteListedDomains() throws IOException {
        ObjectNode body = this.mapper.createObjectNode();
        ArrayNode array = body.putArray("whitelisted_domains");
        array.add("https://jivecake.com");

        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messenger_profile")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .request()
            .buildPost(Entity.entity(body, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }

    public JsonNode setStandardGreeting() throws IOException {
        File file = new File("facebook/greeting.json");

        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messenger_profile")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .request()
            .buildPost(Entity.entity(file, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }

    public JsonNode setGetStarted() throws IOException {
        File file = new File("facebook/getStarted.json");

        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messenger_profile")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .request()
            .buildPost(Entity.entity(file, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }

    public JsonNode setBotData() throws IOException {
        File file = new File("facebook/data.json");

        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messenger_profile")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .request()
            .buildPost(Entity.entity(file, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }

    public JsonNode getBotData() throws IOException {
        String entity = ClientBuilder.newClient()
            .target("https://graph.facebook.com/v2.6/me/messenger_profile")
            .queryParam("access_token", this.configuration.facebook.accessToken)
            .queryParam("fields", "greeting,whitelisted_domains,get_started")
            .request()
            .buildGet()
            .invoke()
            .readEntity(String.class);

        return this.mapper.readTree(entity);
    }
}
