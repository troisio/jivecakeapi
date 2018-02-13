package com.jivecake.api.service;

import javax.inject.Inject;

import com.jivecake.api.model.Application;

public class ApplicationService {
    public static final int LIMIT_DEFAULT = 100;
    private final Application application;

    @Inject
    public ApplicationService(Application application) {
        this.application = application;
    }

    public Application read() {
        return this.application;
    }
}