package com.jivecake.api.cron;

import java.io.IOException;

import javax.inject.Inject;
import javax.inject.Singleton;

import com.jivecake.api.service.Auth0Service;

@Singleton
public class Hourly {
    private final Auth0Service auth0Service;

    @Inject
    public Hourly(Auth0Service auth0Service) {
        this.auth0Service = auth0Service;
    }

    public void reloadAccessToken() throws IOException {
        this.auth0Service.token = this.auth0Service.getNewToken();
    }
}
