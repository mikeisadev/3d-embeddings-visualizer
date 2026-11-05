package com.example.embeddingexplorer.dataset;

import static org.assertj.core.api.Assertions.assertThat;

import com.example.embeddingexplorer.config.DistanceMetricSetting;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Small pure rules of the dataset package: metric resolution, table rules, previews, metadata stats. */
class DatasetModelTest {

    private static final VectorIndex COSINE_INDEX =
            new VectorIndex("spring_ai_vector_index", "hnsw", "vector_cosine_ops");
    private static final VectorIndex L2_INDEX = new VectorIndex("l2_index", "hnsw", "vector_l2_ops");

    @Test
    void detectsTheMetricFromTheIndexOperatorClass() {
        assertThat(ResolvedMetric.resolve(DistanceMetricSetting.AUTO, List.of(L2_INDEX)))
                .isEqualTo(new ResolvedMetric(DistanceMetric.L2, ResolvedMetric.Source.DETECTED));
        assertThat(ResolvedMetric.resolve(DistanceMetricSetting.AUTO, List.of(
                        new VectorIndex("other", "btree", "int4_ops"), COSINE_INDEX)))
                .isEqualTo(new ResolvedMetric(DistanceMetric.COSINE, ResolvedMetric.Source.DETECTED));
    }

    @Test
    void fallsBackToCosineWithoutAnIndex() {
        assertThat(ResolvedMetric.resolve(DistanceMetricSetting.AUTO, List.of()))
                .isEqualTo(new ResolvedMetric(DistanceMetric.COSINE, ResolvedMetric.Source.DEFAULT));
    }

    @Test
    void anExplicitSettingWins() {
        assertThat(ResolvedMetric.resolve(DistanceMetricSetting.INNER_PRODUCT, List.of(COSINE_INDEX)))
                .isEqualTo(new ResolvedMetric(DistanceMetric.INNER_PRODUCT, ResolvedMetric.Source.CONFIGURED));
    }

    @Test
    void writesTheSameDistanceSqlAsSpringAi() {
        assertThat(DistanceMetric.COSINE.distanceSql("embedding", "?")).isEqualTo("embedding <=> ?");
        assertThat(DistanceMetric.L2.distanceSql("embedding", "?")).isEqualTo("embedding <-> ?");
        assertThat(DistanceMetric.INNER_PRODUCT.distanceSql("embedding", "?")).isEqualTo("(1 + (embedding <#> ?))");
        assertThat(DistanceMetric.INNER_PRODUCT.orderBySql("embedding", "?")).isEqualTo("embedding <#> ?");
        assertThat(DistanceMetric.COSINE.similarity(0.25)).isEqualTo(0.75);
    }

    @Test
    void validatesTheSpringAiTableStructure() {
        TableColumns valid = new TableColumns(List.of(
                new TableColumns.Column("id", "uuid", "uuid"),
                new TableColumns.Column("content", "text", "text"),
                new TableColumns.Column("metadata", "json", "json"),
                new TableColumns.Column("embedding", "vector(1536)", "vector")));
        TableColumns invalid = new TableColumns(List.of(
                new TableColumns.Column("id", "bigint", "int8"),
                new TableColumns.Column("metadata", "text", "text"),
                new TableColumns.Column("embedding", "real[]", "_float4")));

        assertThat(valid.problems()).isEmpty();
        assertThat(invalid.problems())
                .containsExactly(
                        "missing column 'content'",
                        "column 'metadata' has type text, expected json or jsonb",
                        "column 'embedding' has type real[], expected vector");
    }

    @Test
    void previewsAreSingleLineAndCutOnCodePoints() {
        assertThat(TextPreview.preview("  Ferie\n\n  e   permessi \t")).isEqualTo("Ferie e permessi");
        String emoji = "😀".repeat(200);
        String preview = TextPreview.preview(emoji);
        assertThat(preview.codePointCount(0, preview.length())).isEqualTo(TextPreview.PREVIEW_LENGTH + 1);
        assertThat(preview).endsWith("…");
        assertThat(TextPreview.length("😀a")).isEqualTo(2);
    }

    @Test
    void collectsMetadataStatisticsInReadingOrder() {
        JsonMapper json = JsonMapper.builder().build();
        MetadataStatsCollector collector = new MetadataStatsCollector();
        for (int page = 1; page <= 7; page++) {
            collector.add(json.readTree("{\"page_number\": %d, \"department\": \"HR\", \"mixed\": %s}"
                    .formatted(page, page % 2 == 0 ? "1" : "\"one\"")));
        }

        List<MetadataKeyStats> stats = collector.stats();

        assertThat(stats).extracting(MetadataKeyStats::key).containsExactly("department", "mixed", "page_number");
        MetadataKeyStats pages = stats.get(2);
        assertThat(pages.presentIn()).isEqualTo(7);
        assertThat(pages.distinctValues()).isEqualTo(7);
        assertThat(pages.sampleValues()).containsExactly("1", "2", "3", "4", "5");
        assertThat(pages.valueType()).isEqualTo(MetadataKeyStats.ValueType.NUMBER);
        assertThat(stats.get(0).distinctValues()).isEqualTo(1);
        assertThat(stats.get(1).valueType()).isEqualTo(MetadataKeyStats.ValueType.MIXED);
    }

    @Test
    void vectorMatrixKeepsRowsInOrder() {
        VectorMatrix matrix = VectorMatrix.of(new float[] {1, 2}, new float[] {3, 4});
        double[] row = new double[2];
        matrix.copyRowInto(1, row);

        assertThat(matrix.rows()).isEqualTo(2);
        assertThat(matrix.get(0, 1)).isEqualTo(2f);
        assertThat(row).containsExactly(3.0, 4.0);
        assertThat(matrix.byteSize()).isEqualTo(16);
    }
}
