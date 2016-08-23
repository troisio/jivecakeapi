package com.jivecake.api.service;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

import javax.ws.rs.core.MultivaluedHashMap;
import javax.ws.rs.core.MultivaluedMap;

public class HttpService {
    public  MultivaluedMap<String, String> bodyToMap(String body) {
        MultivaluedMap<String, String> result = new MultivaluedHashMap<>();

        for (String keyValue : body.split("&")) {
            String[] parts = keyValue.split("=");

            String key;

            try {
                key = URLDecoder.decode(parts[0], "UTF-8");
            } catch (UnsupportedEncodingException e) {
                key = null;
            }

            String value;

            try {
                value = parts.length > 1 ? URLDecoder.decode(parts[1], "UTF-8") : null;
            } catch (UnsupportedEncodingException e) {
                value = null;
            }

            result.add(key, value);
        }

        return result;
    }
}