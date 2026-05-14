package com.ninimum.api.camelcase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.HashMap;

public class CamelCaseMap extends HashMap<String, Object> {
    private static final long serialVersionUID = -7826221370286387542L;

    private static final ObjectMapper objectMapper = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .registerModule(new JavaTimeModule()) // For Java 8 date/time classes
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS) // Use ISO-8601 format
            .setDateFormat(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")); // Global date format for compatibility

    public CamelCaseMap() {
    }

    public <T> T toObject(Class<T> clazz) {

        try {
            String json = objectMapper.writeValueAsString(this);
            return objectMapper.readValue(json, clazz);
        } catch (IOException e) {
            throw new RuntimeException("Failed to map CamelCaseMap to " + clazz.getName(), e);
        }
    }

    private static SimpleModule getCustomInstantModule() {
        SimpleModule module = new SimpleModule();
        module.addDeserializer(Instant.class, new NumericTimestampDeserializer());
        module.addSerializer(Instant.class, new InstantSerializer());
        return module;
    }
}
