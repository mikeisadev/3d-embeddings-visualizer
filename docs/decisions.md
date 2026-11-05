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

## D16 – npm lockfile generated with npm 11

- **Context:** npm 10 (bundled with Node 22) crashes with `Cannot read properties of null (reading 'edgesOut')` while resolving the optional peer dependencies of Vitest 5.
- **Decision:** the lockfile is generated with npm 11; `npm ci` (used by Docker and recommended to students) installs it correctly with both npm 10 and npm 11.
- **Consequence:** use `npm ci`, not `npm install`, with Node 22; Node 24 LTS (npm 11) has no problem.

## D17 – Keeping the data fresh

- **Context:** the brief asks to reload the points when the data changes and to recover by itself when the database comes back, without hammering the backend.
- **Decision:** the diagnostics are polled every 10 s only while a check fails (or the backend is unreachable); when they recover, every data query is invalidated. The dataset summary (cheap: a fingerprint and the cached snapshot) is re-checked every 30 s and on window focus; a new `datasetVersion` changes the query keys, reloads the points and shows a notice. Any environment error from another query refreshes the diagnostics, so the "Stato" section explains it. Database errors are not retried automatically (each attempt would wait for the 5 s connection timeout); network errors, timeouts and gateway errors are retried twice with backoff, and only for GET queries.
- **Consequence:** a new PDF appears within about 30 s, a restarted database within about 10 s; previous points stay on screen while new ones load.

## D18 – How dimmed points are drawn

- **Context:** points outside the filters or unrelated to the selection must look smaller and more transparent, but `InstancedMesh` has no per-instance opacity.
- **Decision:** two instanced meshes share one low-poly sphere: an opaque one for emphasized points and a transparent one (opacity 0.16, no depth writes) for dimmed points. Buffers are allocated once per dataset and rewritten in place; picking maps `instanceId` back to the point, preferring emphasized points. Points hidden in the legend are not drawn. The focus has a priority: selection and its real neighbors, then the RAG results, then the text matches.
- **Consequence:** two draw calls for any number of points, and no custom shader to explain.

## D19 – Frontend loading and rendering

- **Context:** three.js is about 580 kB minified; the sidebar must stay usable even when the 3D view cannot load.
- **Decision:** the 3D view is lazy-loaded in its own chunk behind an error boundary; frames are rendered only when the camera moves or the data changes (the animation loop only checks for changes); `<code>`/`<pre>` disable font ligatures so that `<=>` never turns into an arrow.
- **Consequence:** fast first paint; idle laptops do not spin the GPU.

## D20 – RAG simulation

- **Context:** the simulation must behave exactly like Spring AI and still show what the threshold cuts.
- **Decision:** the question is embedded once; the database runs the same top-K query as `PgVectorStore` (same distance expression, same index, default `hnsw.ef_search`) without the `WHERE distance < 1 − threshold` clause, and each result gets `passesThreshold = distance < 1 − threshold`, the same comparison. The response shows the original Spring AI SQL. The question is projected with the PCA model of the `normalize` flag sent by the view; the 3D marker is drawn only while the points on screen have the same `datasetVersion` and flag, and the camera frames each new question once.
- **Consequence:** "Entra nel contesto" is exactly what the chatbot would put in the prompt; a stale marker is never drawn in the wrong place.

## D21 – Embedding client

- **Context:** OpenAI, Ollama and Docker Model Runner all expose the OpenAI `/embeddings` API; the call may cost money and its errors may contain secrets.
- **Decision:** Spring `RestClient` on the JDK `HttpClient` (connect 5 s, read 20 s). 401/403 → `EMBEDDING_AUTH_FAILED`, 404 → `EMBEDDING_MODEL_NOT_FOUND`, 429 → `EMBEDDING_RATE_LIMITED`, timeouts → `EMBEDDING_TIMEOUT`, anything else (400, 5xx, connection refused, unreadable or empty vector, non-finite numbers) → `EMBEDDING_PROVIDER_ERROR`. Error details never include the provider's body (OpenAI echoes part of the key there). A disabled feature is a separate `EmbeddingClient`; services call `requireEnabled()` first, so a disabled simulation answers 503 before touching the database or the rate limit.
- **Consequence:** one code path for every provider; the student sees an actionable message for each failure.

## D22 – One rate limit for the paid provider

- **Context:** `/search` and the live test both call the provider.
- **Decision:** one global, in-memory token bucket (capacity `VIZ_SEARCH_RATE_LIMIT_PER_MINUTE`, refilled continuously over a minute) shared by both; `RATE_LIMITED` (429) carries `retryAfterSeconds` and a `Retry-After` header. The frontend never retries a search automatically.
- **Consequence:** a class cannot burn the API budget by accident; bursts up to the limit are allowed.
