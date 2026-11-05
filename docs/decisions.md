# Decisions

Choices that the brief left open. Each entry: context, decision, consequence.

## D1 – Dependency versions

- **Context:** the brief asks for exact, pinned versions and no invented APIs.
- **Decision:** every dependency is the newest stable release that had been public for at least 14 days when the project started (supply-chain hygiene). Spring Boot 4.1.2 manages most backend versions (Spring Framework 7.0, Jackson 3.1, Testcontainers 2.0, PostgreSQL driver 42.7). TypeScript is 6.0, not 7.0: `typescript-eslint` supports TypeScript `< 6.1`. Every API was checked in the sources of the installed version.
- **Consequence:** upgrades are deliberate: change the version, read the release notes, run all tests.

## D2 – Connection pool built from the properties record

- **Context:** the brief wants every setting in one validated `@ConfigurationProperties` record, and specific Hikari settings.
- **Decision:** `DatabaseConfig` builds the `HikariDataSource` from `ExplorerProperties` instead of `spring.datasource.*`: pool of 5, `initializationFailTimeout = -1`, 5 s connection timeout, `SET statement_timeout` on every connection, PostgreSQL driver `readOnlyMode=always` and `ApplicationName=embedding-explorer`.
- **Consequence:** the safety settings live in one place. `readOnlyMode=always` makes even auto-commit statements read-only (the default `transaction` mode only protects explicit transactions). A teacher can spot the tool in `pg_stat_activity`.

## D3 – Explicit environment variable mapping

- **Context:** Spring relaxed binding maps `VIZ_DISTANCE_METRIC` to `viz.distance.metric`, not to `viz.distance-metric`.
- **Decision:** `application.yml` maps every `VIZ_*` variable explicitly with `${VIZ_X:default}`; the defaults of §3 live there. Three variables were added: `VIZ_DB_STATEMENT_TIMEOUT` (15 s), `VIZ_DB_CONNECTION_TIMEOUT` (5 s), `VIZ_SNAPSHOT_MEMORY_BUDGET_MB` (256).
- **Consequence:** one file lists every variable and default; the record validates them (bad values stop the startup with the property name in the message). The password has no default value other than empty, so trust-authentication databases still work and a missing password shows up as `DB_AUTH_FAILED` in the diagnostics.

## D4 – Additional error and warning codes

- **Context:** the error code table of §6 does not cover unknown endpoints, wrong methods, missing `SELECT` permission (a likely mistake with the read-only role) or the memory budget.
- **Decision:** added `NOT_FOUND` (404), `METHOD_NOT_ALLOWED` (405), `DB_PERMISSION_DENIED` (503, SQLSTATE `42501`) and `SNAPSHOT_TOO_LARGE` (503). SQLSTATE `3F000` (schema missing) maps to `TABLE_NOT_FOUND`; `57P01`–`57P03` (server shutting down or starting) and `53300` (too many connections) map to `DB_UNAVAILABLE`. Non-blocking problems use a separate `WarningCode` enum (`TABLE_EMPTY`, `INDEX_MISSING`, `FILE_KEY_MISSING`…), shared by the diagnostics and the dataset warnings. Other client errors from Spring MVC (unreadable body, wrong type) become `VALIDATION_FAILED` with status 400, so each code keeps exactly one status.
- **Consequence:** every error the UI can meet has an Italian message and hint.

## D5 – Dataset fingerprint includes the row version

- **Context:** the suggested fingerprint (`count(*)` + hash of the ids) misses documents re-indexed in place: Spring AI upserts on `id`, and L18 introduces stable document ids.
- **Decision:** `datasetVersion` = row count + first 16 hex characters of `md5(string_agg(id || ':' || xmin))`. `xmin` changes whenever a row is rewritten.
- **Consequence:** an updated chunk triggers a reload, at no extra cost.

## D6 – Consistent snapshot, order applied in Java

- **Context:** the snapshot needs a deterministic order and sample, and must not mix two states of the table while the chatbot indexes a PDF.
- **Decision:** the loader runs one read-only `REPEATABLE READ` transaction (fingerprint, counts, dimensions, index, rows). The sample is chosen in SQL with `ORDER BY md5(id::text) LIMIT VIZ_MAX_POINTS` (it does not favour any file; with fewer rows it simply returns all of them). The reading order (file, page, chunk index, id) is applied in Java with `ChunkFields`, the same derivation rules used everywhere, instead of repeating them in SQL.
- **Consequence:** one source of truth for the derived fields; files and metadata statistics describe the loaded chunks (the sample, when sampled), exactly like the 3D view.

## D7 – Table checks before every dataset request

- **Context:** with a plain PostgreSQL, the fingerprint query failed with `TABLE_NOT_FOUND` before the more fundamental `PGVECTOR_MISSING`.
- **Decision:** `VectorTableGuard` (pgvector installed, table exists and is readable, Spring AI columns) runs before the fingerprint. It costs four catalog queries.
- **Consequence:** endpoints report the same root cause as the diagnostics checklist.

## D8 – Health does not depend on the database

- **Context:** Docker restarts and `depends_on: service_healthy` use `/actuator/health`; the UI must stay usable with the database down.
- **Decision:** `management.health.db.enabled=false`. The database state is reported by `/api/v1/diagnostics`.
- **Consequence:** a stopped database never makes the backend container unhealthy or the frontend wait forever.

## D9 – Diagnostics details

- **Context:** a few details of the checklist were not specified.
- **Decision:** `embedding.client` is `SKIPPED` with code `EMBEDDING_DISABLED` when the feature is off, and `WARN` (`EMBEDDING_KEY_MISSING`) when OpenAI is configured without a key. `table.rows` also warns about rows with a NULL embedding. `embedding.dimensions`, `embedding.norms` and `metadata.keys` are `SKIPPED` on an empty table. Status changes are logged once (the UI polls every 10 s while the database is down), never every poll.
- **Consequence:** the checklist explains itself; logs stay readable.

## D10 – English technical messages

- **Context:** Spring interpolates validation messages in the browser language (`Accept-Language`).
- **Decision:** fixed English locale (`spring.web.locale=en`, `spring.web.locale-resolver=fixed`). Floating-point numbers in details are formatted with `Locale.ROOT`.
- **Consequence:** API details are always English and technical; the Italian text comes from the frontend i18n.

## D11 – PCA algorithm

- **Context:** top 3 principal components of up to 5,000 × 1,536 vectors in under 2 s, deterministic, without heavy dependencies.
- **Decision:** block power iteration (8 vectors for 3 components) with a Rayleigh–Ritz step, written in plain Java (`projection.Pca`). The d × d covariance matrix is never built; each iteration multiplies the centered data by the block. Fixed seed, rows processed in fixed-size blocks summed in order (deterministic whatever the number of threads), stop when the residual ‖C·z − θ·z‖ ≤ 10⁻⁶·θ₁ or after 100 iterations; the largest coordinate of each component is made positive. The working copy is `float` (half the memory), every sum is `double`. Normalization is applied only with the cosine metric.
- **Consequence:** about 0.3 s for 5,000 × 1,536 on 4 cores (7 iterations on embedding-like data); verified against an exact Jacobi eigendecomposition. The explained variance shown for each axis is the exact variance of the data along it. Structureless random data hits the cap (about 2 s) and reports `converged: false`.

## D12 – Exact neighbors

- **Context:** an HNSW index is approximate and returns at most `hnsw.ef_search` (40) rows, but the neighbors are the "truth" the 3D view is compared with, and `k` can be 50.
- **Decision:** the neighbors query is the classic `ORDER BY embedding <=> CAST(? AS vector) LIMIT ?`, run in a read-only transaction after `SELECT set_config('enable_indexscan', 'off', true)` (still a `SELECT`): PostgreSQL scans every vector. The response says `exact: true`. The RAG simulation keeps the index, exactly like Spring AI.
- **Consequence:** correct results for any `k` in milliseconds on classroom tables; a teaching point ("the chatbot uses an approximate index").

## D13 – Full-text search endpoint

- **Context:** the "text search that highlights the matching chunks" would only see the 160-character previews.
- **Decision:** added `GET /api/v1/chunks?contains=…` (2–200 characters): `strpos(lower(content), lower(?)) > 0`, a bound parameter, no `LIKE` wildcards to escape; at most `VIZ_MAX_POINTS` ids.
- **Consequence:** students find every chunk that mentions a word, not only those whose first words contain it.

## D14 – Undefined numbers are null

- **Context:** the cosine distance of a zero vector is NaN, which JSON cannot represent.
- **Decision:** distances and similarities are nullable and NaN becomes `null`.
- **Consequence:** dirty data never breaks the response validation of the frontend.

## D15 – Demo data

- **Context:** the demo database needs about 300 seeded, normalized synthetic vectors in a few clusters, clearly marked as synthetic.
- **Decision:** `demo-db/init` generates them in SQL from `md5()` (identical on every machine and PostgreSQL version): five files (four topics plus a mixed FAQ), 60 chunks each, three per page; each vector is `0.7·shared + topic + 0.35·reading position·drift + noise`, normalized. Same-topic cosine similarity is about 0.58, cross-topic about 0.19. Content starts with "[DEMO – dati sintetici]" and metadata has `"demo": true`. A second table, `vector_store_minilm` (384 dimensions), supports the L18 §12 comparison exercise.
- **Consequence:** realistic geometry (the first three components keep about 42% of the variance) and a reproducible classroom demo.
