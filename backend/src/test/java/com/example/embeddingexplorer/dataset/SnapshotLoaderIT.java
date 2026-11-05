package com.example.embeddingexplorer.dataset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.example.embeddingexplorer.config.DistanceMetricSetting;
import com.example.embeddingexplorer.config.ExplorerProperties;
import com.example.embeddingexplorer.error.ErrorCode;
import com.example.embeddingexplorer.error.ExplorerException;
import com.example.embeddingexplorer.error.WarningCode;
import com.example.embeddingexplorer.support.PgvectorDatabase;
import com.example.embeddingexplorer.support.SampleData;
import com.example.embeddingexplorer.support.TestProperties;
import com.example.embeddingexplorer.support.TestVectors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.json.JsonMapper;

/** Loading the snapshot from real tables: order, sampling, dirty data, versions, caching. */
class SnapshotLoaderIT {

    private static final int DIMENSIONS = 24;

    @BeforeAll
    static void createTables() {
        PgvectorDatabase.createVectorTable("snap_main", DIMENSIONS);
        List<PgvectorDatabase.TestRow> rows = new ArrayList<>(SampleData.rows(DIMENSIONS, 10));
        Collections.shuffle(rows, new Random(4));
        PgvectorDatabase.insert("snap_main", rows);

        PgvectorDatabase.createVectorTable("snap_dirty", DIMENSIONS);
        PgvectorDatabase.insert("snap_dirty", List.of(
                new PgvectorDatabase.TestRow(SampleData.uuid(1), null, "{\"original_filename\": \"a.pdf\"}",
                        TestVectors.random(DIMENSIONS, 1)),
                new PgvectorDatabase.TestRow(SampleData.uuid(2), "array metadata", "[1, 2, 3]",
                        TestVectors.random(DIMENSIONS, 2)),
                new PgvectorDatabase.TestRow(SampleData.uuid(3), "null metadata", null,
                        TestVectors.random(DIMENSIONS, 3)),
                new PgvectorDatabase.TestRow(SampleData.uuid(4), "no vector", "{}", null)));

        PgvectorDatabase.admin().execute("DROP TABLE IF EXISTS public.snap_mixed");
        PgvectorDatabase.admin().execute(
                "CREATE TABLE public.snap_mixed (id uuid PRIMARY KEY, content text, metadata json, embedding vector)");
        PgvectorDatabase.insert("snap_mixed", List.of(
                new PgvectorDatabase.TestRow(SampleData.uuid(1), "a", "{}", TestVectors.random(3, 1)),
                new PgvectorDatabase.TestRow(SampleData.uuid(2), "b", "{}", TestVectors.random(5, 2))));
    }

    @Test
    void loadsEveryChunkInReadingOrder() {
        DatasetSnapshot snapshot = loader(TestProperties.forTable("snap_main")).load();

        assertThat(snapshot.chunks()).hasSize(30);
        assertThat(snapshot.sampled()).isFalse();
        assertThat(snapshot.dimensions()).isEqualTo(DIMENSIONS);
        assertThat(snapshot.vectors().rows()).isEqualTo(30);
        List<ChunkPoint> sorted = new ArrayList<>(snapshot.chunks());
        sorted.sort(ChunkFields.READING_ORDER);
        assertThat(snapshot.chunks()).containsExactlyElementsOf(sorted);
        assertThat(snapshot.chunks().getFirst().file()).isEqualTo("ferie.pdf");
        assertThat(snapshot.chunks().getFirst().page()).isEqualTo(1);
        assertThat(snapshot.chunks().getFirst().chunkIndex()).isEqualTo(0);
        assertThat(snapshot.files()).extracting(DatasetSnapshot.FileCount::chunks).containsOnly(10);
    }

    @Test
    void vectorsStayAlignedWithTheirChunks() {
        DatasetSnapshot snapshot = loader(TestProperties.forTable("snap_main")).load();
        ChunkRepository repository = new ChunkRepository(jdbc(), TestProperties.forTable("snap_main").db().tableRef(),
                JsonMapper.builder().build());

        for (int row : new int[] {0, 13, 29}) {
            float[] stored = repository.findById(snapshot.chunks().get(row).id()).orElseThrow().embedding();
            assertThat(snapshot.vectors().row(row)).containsExactly(stored);
        }
    }

    @Test
    void takesADeterministicSampleAboveTheLimit() {
        ExplorerProperties limited = TestProperties.forTable("snap_main", DistanceMetricSetting.AUTO, 12);

        DatasetSnapshot first = loader(limited).load();
        DatasetSnapshot second = loader(limited).load();

        assertThat(first.sampled()).isTrue();
        assertThat(first.chunks()).hasSize(12);
        assertThat(first.rowsWithEmbedding()).isEqualTo(30);
        assertThat(second.chunks()).containsExactlyElementsOf(first.chunks());
        assertThat(first.files()).hasSizeGreaterThan(1);
    }

    @Test
    void toleratesDirtyData() {
        DatasetSnapshot snapshot = loader(TestProperties.forTable("snap_dirty")).load();

        assertThat(snapshot.totalRows()).isEqualTo(4);
        assertThat(snapshot.chunks()).hasSize(3);
        assertThat(snapshot.warnings()).containsExactlyInAnyOrder(
                new DatasetSnapshot.Warning(WarningCode.NULL_EMBEDDINGS_SKIPPED, 1),
                new DatasetSnapshot.Warning(WarningCode.METADATA_NOT_OBJECT, 2),
                new DatasetSnapshot.Warning(WarningCode.CONTENT_NULL, 1));
        ChunkPoint nullContent = snapshot.chunks().stream()
                .filter(chunk -> chunk.id().equals(SampleData.uuid(1)))
                .findFirst()
                .orElseThrow();
        assertThat(nullContent.preview()).isEmpty();
        assertThat(nullContent.contentLength()).isZero();
        assertThat(snapshot.chunks()).filteredOn(chunk -> chunk.file().equals(ChunkFields.UNKNOWN_FILE)).hasSize(2);
    }

    @Test
    void refusesMixedDimensions() {
        assertThatThrownBy(() -> loader(TestProperties.forTable("snap_mixed")).load())
                .isInstanceOfSatisfying(ExplorerException.class, error -> {
                    assertThat(error.code()).isEqualTo(ErrorCode.DIMENSION_MISMATCH);
                    assertThat(error.properties()).containsEntry("dimensionsFound", List.of(3, 5));
                });
    }

    @Test
    void refusesSnapshotsAboveTheMemoryBudget() {
        ExplorerProperties base = TestProperties.forTable("snap_main");
        ExplorerProperties tiny = new ExplorerProperties(
                base.db(), base.distanceMetric(), base.maxPoints(), 0, base.embedding(), 10);

        assertThatThrownBy(() -> loader(tiny).load())
                .isInstanceOfSatisfying(ExplorerException.class, error -> assertThat(error.code())
                        .isEqualTo(ErrorCode.SNAPSHOT_TOO_LARGE));
    }

    @Test
    void versionChangesWhenRowsAreAddedOrUpdatedInPlace() {
        PgvectorDatabase.createVectorTable("snap_versions", DIMENSIONS);
        PgvectorDatabase.insert("snap_versions", SampleData.rows(DIMENSIONS, 2));
        ExplorerProperties properties = TestProperties.forTable("snap_versions");
        TableInspector inspector = new TableInspector(jdbc());

        String initial = inspector.fingerprint(properties.db().tableRef());
        assertThat(inspector.fingerprint(properties.db().tableRef())).isEqualTo(initial);

        PgvectorDatabase.admin().update(
                "UPDATE public.snap_versions SET content = 'riscritto' WHERE id = ?::uuid", SampleData.uuid(0));
        String updated = inspector.fingerprint(properties.db().tableRef());
        assertThat(updated).isNotEqualTo(initial);

        PgvectorDatabase.insert("snap_versions", List.of(new PgvectorDatabase.TestRow(
                SampleData.uuid(777), "nuovo", "{}", TestVectors.random(DIMENSIONS, 777))));
        assertThat(inspector.fingerprint(properties.db().tableRef())).isNotEqualTo(updated).startsWith("7-");
    }

    @Test
    void concurrentRequestsShareOneLoadAndTheCacheFollowsTheTable() throws Exception {
        PgvectorDatabase.createVectorTable("snap_cache", DIMENSIONS);
        PgvectorDatabase.insert("snap_cache", SampleData.rows(DIMENSIONS, 3));
        ExplorerProperties properties = TestProperties.forTable("snap_cache");
        SnapshotLoader loader = spy(loader(properties));
        TableInspector inspector = new TableInspector(jdbc());
        DatasetService service = new DatasetService(
                loader,
                new VectorTableGuard(inspector, properties.db().tableRef()),
                inspector,
                properties.db().tableRef(),
                properties);

        List<Callable<DatasetSnapshot>> requests = Collections.nCopies(8, service::currentSnapshot);
        List<DatasetSnapshot> snapshots = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(8)) {
            for (Future<DatasetSnapshot> future : pool.invokeAll(requests)) {
                snapshots.add(future.get());
            }
        }
        assertThat(snapshots).allSatisfy(snapshot -> assertThat(snapshot).isSameAs(snapshots.getFirst()));
        verify(loader, times(1)).load();

        assertThat(service.currentSnapshot()).isSameAs(snapshots.getFirst());
        verify(loader, times(1)).load();

        PgvectorDatabase.insert("snap_cache", List.of(new PgvectorDatabase.TestRow(
                SampleData.uuid(555), "nuovo", "{}", TestVectors.random(DIMENSIONS, 555))));
        DatasetSnapshot reloaded = service.currentSnapshot();
        assertThat(reloaded.chunks()).hasSize(10);
        assertThat(reloaded.version()).isNotEqualTo(snapshots.getFirst().version());
        verify(loader, times(2)).load();
    }

    private static SnapshotLoader loader(ExplorerProperties properties) {
        DataSource dataSource = dataSource();
        return new SnapshotLoader(
                JdbcClient.create(dataSource),
                new TableInspector(JdbcClient.create(dataSource)),
                properties.db().tableRef(),
                properties,
                JsonMapper.builder().build(),
                new DataSourceTransactionManager(dataSource));
    }

    private static JdbcClient jdbc() {
        return JdbcClient.create(dataSource());
    }

    private static DataSource dataSource() {
        var container = PgvectorDatabase.container();
        return new DriverManagerDataSource(container.getJdbcUrl(), container.getUsername(), container.getPassword());
    }
}
