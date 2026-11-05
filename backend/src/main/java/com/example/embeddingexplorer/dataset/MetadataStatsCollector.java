package com.example.embeddingexplorer.dataset;

import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import tools.jackson.databind.JsonNode;

/**
 * Accumulates {@link MetadataKeyStats} while chunks are visited in reading order, so that the
 * sample values are deterministic and meaningful (page 1, 2, 3… rather than random pages).
 */
final class MetadataStatsCollector {

    private static final int SAMPLE_VALUES = 5;
    private static final int VALUE_LENGTH_LIMIT = 200;

    private final Map<String, KeyAccumulator> keys = new TreeMap<>();

    void add(JsonNode metadata) {
        for (Map.Entry<String, JsonNode> entry : metadata.properties()) {
            keys.computeIfAbsent(entry.getKey(), KeyAccumulator::new).add(entry.getValue());
        }
    }

    /** Statistics per key, sorted by key. */
    List<MetadataKeyStats> stats() {
        return keys.values().stream().map(KeyAccumulator::stats).toList();
    }

    private static final class KeyAccumulator {

        private final String key;
        private final Set<String> distinct = new LinkedHashSet<>();
        private final Set<MetadataKeyStats.ValueType> types = EnumSet.noneOf(MetadataKeyStats.ValueType.class);
        private int presentIn;

        KeyAccumulator(String key) {
            this.key = key;
        }

        void add(JsonNode value) {
            presentIn++;
            types.add(MetadataKeyStats.ValueType.of(value));
            String text = value.isString() ? value.stringValue() : value.toString();
            distinct.add(TextPreview.truncate(text, VALUE_LENGTH_LIMIT));
        }

        MetadataKeyStats stats() {
            MetadataKeyStats.ValueType type =
                    types.size() == 1 ? types.iterator().next() : MetadataKeyStats.ValueType.MIXED;
            List<String> samples = distinct.stream().limit(SAMPLE_VALUES).toList();
            return new MetadataKeyStats(key, presentIn, distinct.size(), samples, type);
        }
    }
}
