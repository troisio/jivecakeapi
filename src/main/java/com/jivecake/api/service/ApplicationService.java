package com.jivecake.api.service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.google.inject.Singleton;
import com.jivecake.api.model.Application;

@Singleton
public class ApplicationService {
    private final Application application;
    private final Set<String> permissions = new HashSet<>(Arrays.asList(
        this.getWritePermission(),
        this.getReadPermission()
    ));

    public ApplicationService(Application application) {
        this.application = application;
    }

    public Application read() {
        return this.application;
    }

    public Set<String> getPermissions() {
        return this.permissions;
    }

    public String getPermissionObjectClass() {
        return Application.class.getSimpleName();
    }

    public String getWritePermission() {
        return "WRITE";
    }

    public String getReadPermission() {
        return "READ";
    }
}
