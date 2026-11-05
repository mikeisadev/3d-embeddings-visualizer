package com.example.embeddingexplorer.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.Duration;
import org.hibernate.validator.constraints.time.DurationMax;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Every setting of the application, bound from the {@code VIZ_*} environment variables
 * mapped in {@code application.yml} (which also holds the defaults) and validated at startup.
 *
 * <p>Invalid values stop the application with a clear message, because they are deployment
 * mistakes. An unreachable database, on the contrary, is an environment problem: the
 * application starts anyway and reports it through the diagnostics.
 *
 * @param db                       connection to the vector database
 * @param distanceMetric           {@code auto} (detected from the index) or a fixed metric
 * @param maxPoints                maximum number of chunks loaded; above it a deterministic sample is taken
 * @param snapshotMemoryBudgetMb   largest vector snapshot (rows × dimensions × 4 bytes) the backend accepts to hold
 * @param embedding                optional embedding provider for "Simula una ricerca RAG"
 * @param searchRateLimitPerMinute calls per minute allowed to the (possibly paid) embedding provider
 */
@Validated
@ConfigurationProperties(prefix = "viz")
public record ExplorerProperties(
        @Valid @NotNull Database db,
        @NotNull DistanceMetricSetting distanceMetric,
        @Min(3) @Max(100_000) int maxPoints,
        @Min(16) @Max(65_536) int snapshotMemoryBudgetMb,
        @Valid @NotNull Embedding embedding,
        @Min(1) @Max(1_000) int searchRateLimitPerMinute) {

    /**
     * Database connection settings.
     *
     * @param url               PostgreSQL JDBC URL
     * @param user              database user (ideally a read-only role)
     * @param password          password, only from the environment; may be empty for trust authentication
     * @param schema            schema of the vector table
     * @param table             name of the vector table
     * @param statementTimeout  maximum duration of any statement
     * @param connectionTimeout maximum wait for a connection, so a stopped database fails fast
     */
    public record Database(
            @NotBlank
                    @Pattern(
                            regexp = "^jdbc:postgresql://.+",
                            message = "must be a PostgreSQL JDBC URL like jdbc:postgresql://host:5432/database")
                    String url,
            @NotBlank String user,
            String password,
            @NotNull @Pattern(regexp = SqlIdentifier.PATTERN, message = SqlIdentifier.MESSAGE) String schema,
            @NotNull @Pattern(regexp = SqlIdentifier.PATTERN, message = SqlIdentifier.MESSAGE) String table,
            @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 10) Duration statementTimeout,
            @NotNull @DurationMin(seconds = 1) @DurationMax(minutes = 1) Duration connectionTimeout) {

        /** The table reference built from the validated schema and table names. */
        public TableRef tableRef() {
            return TableRef.of(schema, table);
        }

        /** Never prints the password. */
        @Override
        public String toString() {
            return "Database[url=%s, user=%s, password=%s, schema=%s, table=%s]"
                    .formatted(url, user, Secrets.mask(password), schema, table);
        }
    }

    /**
     * Embedding provider settings, used only by the RAG simulation.
     *
     * @param enabled whether the RAG simulation is available
     * @param baseUrl base URL of an OpenAI-compatible API (the {@code /embeddings} path is appended)
     * @param model   embedding model; it must be the one used to index the table
     * @param apiKey  optional bearer token; never logged and never sent to the browser
     */
    public record Embedding(
            boolean enabled,
            @NotBlank @Pattern(regexp = "^https?://\\S+$", message = "must be an http(s) URL") String baseUrl,
            @NotBlank String model,
            String apiKey) {

        /** Whether a non-blank API key is configured. */
        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }

        /** Never prints the API key. */
        @Override
        public String toString() {
            return "Embedding[enabled=%s, baseUrl=%s, model=%s, apiKey=%s]"
                    .formatted(enabled, baseUrl, model, Secrets.mask(apiKey));
        }
    }

    /** Masks secrets in {@code toString()} so they never end up in logs or failure reports. */
    private static final class Secrets {

        private Secrets() {}

        static String mask(String secret) {
            return secret == null || secret.isEmpty() ? "<empty>" : "******";
        }
    }
}
