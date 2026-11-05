package com.example.embeddingexplorer.support;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** A small, realistic L17-like dataset: three PDF files, several pages, three topic clusters. */
public final class SampleData {

    public static final List<String> FILES = List.of("ferie.pdf", "sicurezza.pdf", "welfare.pdf");

    private SampleData() {}

    /** {@code perFile} chunks for each file; chunks of the same file share a cluster. */
    public static List<PgvectorDatabase.TestRow> rows(int dimensions, int perFile) {
        List<PgvectorDatabase.TestRow> rows = new ArrayList<>();
        for (int file = 0; file < FILES.size(); file++) {
            for (int chunk = 0; chunk < perFile; chunk++) {
                long seed = file * 1_000L + chunk;
                String metadata = """
                        {"original_filename": "%s", "source": "%s.pdf", "page_number": %d, "chunk_index": %d, "department": "%s"}"""
                        .formatted(FILES.get(file), uuid(seed + 500_000), chunk / 2 + 1, chunk, file == 0 ? "HR" : "OPS");
                rows.add(new PgvectorDatabase.TestRow(
                        uuid(seed),
                        "Chunk " + chunk + " of " + FILES.get(file) + ": testo di esempio sul tema " + file + ".",
                        metadata,
                        TestVectors.clustered(dimensions, file * 3, seed, 0.15)));
            }
        }
        return rows;
    }

    /** A deterministic UUID derived from a number. */
    public static String uuid(long seed) {
        return UUID.nameUUIDFromBytes(("chunk-" + seed).getBytes(StandardCharsets.UTF_8)).toString();
    }
}
