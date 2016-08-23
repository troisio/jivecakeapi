package com.jivecake.api.filter;

import java.io.IOException;
import java.util.Map;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.glassfish.hk2.api.Factory;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jivecake.api.service.Auth0Service;

public class ClaimsFactory implements Factory<JsonNode> {
    private final HttpServletRequest request;
    private final Auth0Service auth0Service;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public ClaimsFactory(Auth0Service auth0Service, HttpServletRequest request) {
        this.request = request;
        this.auth0Service = auth0Service;
    }

    @Override
    public JsonNode provide() {
       String authorization = this.request.getHeader("Authorization");
       JsonNode node = null;

       if (authorization != null && authorization.startsWith("Bearer ")) {
           String jwt = authorization.substring("Bearer ".length());
           Map<String, Object> claims = this.auth0Service.getClaimsFromToken(jwt);

           if (claims != null) {
               try {
                   String json = this.mapper.writeValueAsString(claims);
                   node = this.mapper.readTree(json);
               } catch (IOException  e) {
               }
           }
       }

       return node;
    }

    @Override
    public void dispose(JsonNode node) {
    }
}