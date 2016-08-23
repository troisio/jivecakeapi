package com.jivecake.api.serializer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class JsonTools {
    private final ObjectMapper mapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public String pretty(Object object) {
        String result;

        try {
            return this.mapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            result = null;
        }

        return result;
    }
}