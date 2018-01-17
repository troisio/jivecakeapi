package com.jivecake.api.filter;

import javax.inject.Inject;
import javax.servlet.http.HttpServletRequest;

import org.glassfish.hk2.api.Factory;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.jivecake.api.service.Auth0Service;

public class ClaimsFactory implements Factory<DecodedJWT> {
    private final HttpServletRequest request;
    private final Auth0Service auth0Service;

    @Inject
    public ClaimsFactory(Auth0Service auth0Service, HttpServletRequest request) {
        this.request = request;
        this.auth0Service = auth0Service;
    }

    @Override
    public DecodedJWT provide() {
       String authorization = this.request.getHeader("Authorization");
       DecodedJWT jwt = null;

       if (authorization != null && authorization.startsWith("Bearer ")) {
           try {
               jwt = this.auth0Service.getClaimsFromToken(authorization.substring("Bearer ".length()));
           } catch (Exception e) {
               e.printStackTrace();
           }
       }

       return jwt;
    }

    @Override
    public void dispose(DecodedJWT jwt) {
    }
}