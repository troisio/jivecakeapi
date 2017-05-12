package com.jivecake.api.serializer;

import java.io.IOException;
import java.util.Collection;

import org.bson.types.ObjectId;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

public class ObjectIdCollectionSerializer extends JsonSerializer<Collection<ObjectId>> {
    @Override
    public void serialize(Collection<ObjectId> value, JsonGenerator generator, SerializerProvider serializers) throws IOException {
        if (value == null) {
            generator.writeNull();
        } else {
            generator.writeStartArray();

            for (ObjectId subject : value) {
                generator.writeString(subject == null ? null : subject.toString());
            }

            generator.writeEndArray();
        }
    }
}
