package com.jivecake.api.service;

import com.jivecake.api.model.Application;

public class ApplicationService {
    private final Application application;

    public ApplicationService(Application application) {
        this.application = application;
    }

    public Application read() {
        return this.application;
    }
}