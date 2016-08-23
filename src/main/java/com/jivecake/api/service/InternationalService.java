package com.jivecake.api.service;

import java.util.HashMap;
import java.util.Map;

import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class InternationalService {
    public final Map<String, String> internationCodes;

    @Inject
    public InternationalService(Map<String, String> internationCodes) {
        this.internationCodes = new HashMap<>(internationCodes);
    }
}