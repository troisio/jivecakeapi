import java.io.IOException;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.dropwizard.configuration.ConfigurationException;

public class Auth0ImpersonationTest {
    public void canImpersonate() throws IOException, ConfigurationException {
        String impersonatingUser = "google-oauth2|105220434348009698992";
        String userToImpersonate = "";
        String globalClientId = "";
        String globalClientSecret = "";

        String redirect = "https://jivecake.com/oauth/redirect";
        String clientId = "j3ZrslQlvV52JqsDSUjDQy0XPdYEjuMV";
        String audience = "https://api.jivecake.com/";

/*
        String redirect = "http://127.0.0.1/oauth/redirect";
        String clientId = "XUoSMI9WREWpgWAetI7bE4agoELNi66m";
        String audience = "http://127.0.0.1:8080/";
*/
        ObjectMapper mapper = new ObjectMapper();

        ObjectNode authBody = mapper
            .createObjectNode()
            .put("client_id", globalClientId)
            .put("client_secret", globalClientSecret)
            .put("grant_type", "client_credentials");

        String tokenJson = ClientBuilder.newClient()
            .target("https://jivecake.auth0.com/oauth/token")
            .request()
            .header("Content-Type", "application/json")
            .buildPost(Entity.entity(authBody, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        String token = mapper.readTree(tokenJson).get("access_token").asText();

        ObjectNode node = mapper.createObjectNode()
            .put("protocol", "oauth2")
            .put("impersonator_id", impersonatingUser)
            .put("client_id", clientId);
        node.set("additionalParameters", mapper.createObjectNode()
            .put("response_type", "token id_token")
            .put("aud", audience)
            .put("callback_url", redirect)
            .put("scope", "openid email profile")
        );

        String impersonateLink = ClientBuilder.newClient()
            .target("https://jivecake.auth0.com/users/" + userToImpersonate + "/impersonate")
            .request()
            .header("Authorization", "Bearer " + token)
            .buildPost(Entity.entity(node, MediaType.APPLICATION_JSON))
            .invoke()
            .readEntity(String.class);

        System.out.println(impersonateLink);
    }
}
