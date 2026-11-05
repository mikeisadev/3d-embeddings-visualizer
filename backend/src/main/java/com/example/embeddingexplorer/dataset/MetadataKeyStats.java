package com.example.embeddingexplorer.dataset;

import java.util.List;
import tools.jackson.databind.JsonNode;

/**
 * Statistics about one metadata key, used by the UI to offer "Colora per" on any key.
 *
 * @param key            the metadata key
 * @param presentIn      how many loaded chunks have the key
 * @param distinctValues how many different values it takes
 * @param sampleValues   up to five values, in reading order
 * @param valueType      the JSON type of the values, or {@code MIXED}
 */
public record MetadataKeyStats(
        String key, int presentIn, int distinctValues, List<String> sampleValues, ValueType valueType) {

    public MetadataKeyStats {
        sampleValues = List.copyOf(sampleValues);
    }

    /** JSON type of the values of a key. */
    public enum ValueType {
        STRING,
        NUMBER,
        BOOLEAN,
        NULL,
        OBJECT,
        ARRAY,
        MIXED;

        static ValueType of(JsonNode value) {
            if (value.isString()) {
                return STRING;
            }
            if (value.isNumber()) {
                return NUMBER;
            }
            if (value.isBoolean()) {
                return BOOLEAN;
            }
            if (value.isObject()) {
                return OBJECT;
            }
            return value.isArray() ? ARRAY : NULL;
        }
    }
}
