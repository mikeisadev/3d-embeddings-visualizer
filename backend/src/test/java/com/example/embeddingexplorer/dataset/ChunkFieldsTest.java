package com.example.embeddingexplorer.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ChunkFieldsTest {

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void prefersTheOriginalFileName() {
        JsonNode metadata = json.readTree("""
                {"original_filename": "ferie.pdf", "file_name": "1b2c.pdf", "source": "1b2c.pdf"}""");
        assertThat(ChunkFields.file(metadata)).isEqualTo("ferie.pdf");
    }

    @Test
    void fallsBackToFileNameThenSourceThenUnknown() {
        assertThat(ChunkFields.file(json.readTree("{\"file_name\": \"a.pdf\", \"source\": \"b.pdf\"}")))
                .isEqualTo("a.pdf");
        assertThat(ChunkFields.file(json.readTree("{\"original_filename\": \"  \", \"source\": \"b.pdf\"}")))
                .isEqualTo("b.pdf");
        assertThat(ChunkFields.file(json.readTree("{\"page_number\": 1}"))).isEqualTo(ChunkFields.UNKNOWN_FILE);
        assertThat(ChunkFields.file(json.readTree("{\"source\": null}"))).isEqualTo(ChunkFields.UNKNOWN_FILE);
    }

    @Test
    void readsPagesFromNumbersAndNumericStrings() {
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": 3}"))).isEqualTo(3);
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": \"7\"}"))).isEqualTo(7);
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": 2.0}"))).isEqualTo(2);
        assertThat(ChunkFields.page(json.readTree("{\"start_page_number\": 4, \"end_page_number\": 5}")))
                .isEqualTo(4);
    }

    @Test
    void ignoresValuesThatAreNotIntegers() {
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": \"tre\"}"))).isNull();
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": 2.5}"))).isNull();
        assertThat(ChunkFields.page(json.readTree("{\"page_number\": [1]}"))).isNull();
        assertThat(ChunkFields.chunkIndex(json.readTree("{}"))).isNull();
        assertThat(ChunkFields.chunkIndex(json.readTree("{\"chunk_index\": 12}"))).isEqualTo(12);
    }

    @Test
    void keepsOnlyScalarMetadataAndCutsLongStrings() {
        JsonNode metadata = json.readTree("""
                {"department": "HR", "page_number": 3, "ok": true, "nested": {"a": 1}, "list": [1, 2],
                 "nothing": null, "long": "%s"}""".formatted("x".repeat(250)));

        Map<String, Object> scalars = ChunkFields.scalarMetadata(metadata);

        assertThat(scalars).containsOnlyKeys("department", "page_number", "ok", "long");
        assertThat(scalars.get("page_number")).isEqualTo(3);
        assertThat((String) scalars.get("long")).hasSize(201).endsWith("…");
    }

    @Test
    void sortsInReadingOrderWithMissingValuesLast() {
        List<ChunkPoint> points = new ArrayList<>(List.of(
                point("4", "b.pdf", 1, 0),
                point("3", "a.pdf", null, null),
                point("2", "a.pdf", 2, 1),
                point("1", "a.pdf", 2, 0),
                point("0", "a.pdf", 1, null)));

        points.sort(ChunkFields.READING_ORDER);

        assertThat(points).extracting(ChunkPoint::id).containsExactly("0", "1", "2", "3", "4");
    }

    private static ChunkPoint point(String id, String file, Integer page, Integer chunkIndex) {
        return new ChunkPoint(id, file, page, chunkIndex, "", 0, Map.of());
    }
}
