package me.asu.ai.util;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;

import java.io.IOException;
import java.time.Duration;

/**
 * Flexible Duration deserializer for YAML config.
 * Supports values like 1s, 10s, 1m, 5m, 1h, 1d, ISO-8601 (PT1M), and numeric seconds.
 */
public class FlexibleDurationDeserializer extends JsonDeserializer<Duration> {

    @Override
    public Duration deserialize(JsonParser parser, DeserializationContext context) throws IOException {
        JsonToken token = parser.currentToken();

        if (token == JsonToken.VALUE_NULL) {
            return null;
        }

        if (token == JsonToken.VALUE_NUMBER_INT) {
            return Duration.ofSeconds(parser.getLongValue());
        }

        String text = parser.getValueAsString();
        if (text == null || text.isBlank()) {
            return null;
        }

        try {
            return DurationParser.parse(text);
        } catch (IllegalArgumentException ex) {
            throw context.weirdStringException(text, Duration.class,
                    "Invalid duration format. Expected values like 10s, 5m, 1h, 1d, or PT1M");
        }
    }
}
