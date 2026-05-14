package com.ninimum.api.camelcase;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;

public class InstantSerializer extends JsonSerializer<Instant> {
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_INSTANT;

    @Override
    public void serialize(Instant value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
        gen.writeString(FORMATTER.format(value));
    }
}
