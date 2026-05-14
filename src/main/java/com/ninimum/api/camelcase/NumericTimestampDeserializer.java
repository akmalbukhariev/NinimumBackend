package com.ninimum.api.camelcase;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Instant;

/*public class NumericTimestampDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedNumberIntToken()) {
            long timestamp = p.getLongValue();
            if (timestamp > 0 && timestamp < 253402300800000L) { // Clamp: 1970-01-01 to 9999-12-31
                return Instant.ofEpochMilli(timestamp);
            } else {
                throw new IllegalArgumentException("Invalid timestamp: " + timestamp);
            }
        }
        throw ctxt.mappingException("Expected numeric timestamp but got: " + p.getText());
    }
}*/

public class NumericTimestampDeserializer extends JsonDeserializer<Instant> {
    @Override
    public Instant deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
        if (p.isExpectedNumberIntToken()) {
            long timestamp = p.getLongValue();
            if (timestamp > 0 && timestamp < 253402300800000L) { // Clamp: 1970-01-01 to 9999-12-31
                return Instant.ofEpochMilli(timestamp);
            } else {
                throw new IllegalArgumentException("Invalid timestamp: " + timestamp);
            }
        } else {
            String value = p.getText();
            try {
                return Instant.parse(value);
            } catch (Exception e) {
                throw new IOException("Failed to parse timestamp: " + value);
            }
        }
    }
}
