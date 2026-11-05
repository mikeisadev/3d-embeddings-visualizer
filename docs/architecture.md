# Architecture

Embedding Explorer 3D is a read-only teaching tool: one Spring Boot backend that reads the pgvector
table of lesson L17, one React frontend that draws it in 3D. This document explains how the pieces
fit together; [`decisions.md`](decisions.md) explains *why* (entries D1–D33).

## 1. Overview

```text
Browser ──► viz-frontend (nginx: React build + reverse proxy /api) ──► viz-backend (Spring Boot, read-only) ──► PostgreSQL + pgvector (L17)
                                                                              └──► optional: an OpenAI-compatible /embeddings endpoint
```

- **Same origin.** nginx serves the static build and proxies `/api/` to the backend, so the browser
  never needs CORS and never sees the backend port.
- **Only the backend talks to the database and to the embedding provider.** API keys and database
  credentials stay in the backend container.
- **Snapshot + projection cache.** The backend loads a consistent *dataset snapshot* (ids, vectors,
  derived fields, metadata statistics) once, computes the PCA on it and caches both under a
  `datasetVersion`. Every response about points carries that version, so the frontend notices when the
  chatbot indexes a new PDF and reloads.
- **Honesty rule.** 3D coordinates are only for drawing. Every number shown as "real" (distances,
  similarities, neighbors, comparisons) is computed by PostgreSQL on the full vectors, or by the backend
  on the full vector (vector statistics).

## 2. Backend

Java 21, Spring Boot 4.1 (Spring MVC, `JdbcClient`, Bean Validation, Actuator), PostgreSQL JDBC
driver. No Spring AI and no JPA: the tool only reads rows and calls one HTTP endpoint. Base package
`com.example.embeddingexplorer`, organized by feature.

### 2.1 Packages

| Package | Responsibility | Main classes |
| --- | --- | --- |
| `config` | Validated configuration and the read-only connection pool. | `ExplorerProperties` (one `@Validated` record bound from `VIZ_*` variables; secrets masked in `toString`), `DatabaseConfig` (Hikari pool: 5 connections, `readOnlyMode=always`, `SET statement_timeout`, `initializationFailTimeout=-1`, `ApplicationName`), `SqlIdentifier` / `TableRef` (validated, quoted identifiers), `DistanceMetricSetting`. |
| `dataset` | Reading the vector table. | `TableInspector` (catalog queries and cheap statistics), `VectorTableGuard` (pgvector installed, table exists and is readable, Spring AI columns), `SnapshotLoader` + `SnapshotRowCollector` (consistent snapshot), `DatasetService` (single-flight cache), `ChunkFields` (derived file / page / chunk index, reading order), `TextPreview`, `MetadataStatsCollector`, `VectorMatrix` (row-major `float[]`, dot products, float32 little-endian writer), `DatasetSnapshot`, `DatasetSummary`, `ChunkRepository` / `ChunkService` (single chunks, full-text matches), `MetricResolver`, `DistanceMetric` (pgvector operators and the Spring AI SQL for each metric), `PgVectors` (vector literal). |
| `projection` | PCA and the point cloud. | `Pca` (block power iteration + Rayleigh–Ritz), `CenteredData`, `SymmetricEigen` (Jacobi), `PcaModel` (mean, components, explained variance, `project`), `PcaProjection`, `ProjectionService` (cache per version and normalize flag), `PointCloud` / `PointCloudService` (`/points`). |
| `search` | Similarity queries. | `SimilarChunks` (the top-K SQL, written like Spring AI), `NeighborService` (exact neighbors), `CompareService`, `RagSearchService`, `SearchRequest` / `SearchResult`, `Neighbors`, `Comparison`, `NeighborSampleService` + `ExactNeighbors` (in-memory exact neighbors of a sample, for the fidelity score). |
| `embedding` | The optional embedding provider. | `EmbeddingClient` (interface, `requireEnabled()`), `OpenAiCompatibleEmbeddingClient` (Spring `RestClient` on the JDK `HttpClient`, 5 s connect / 20 s read), `DisabledEmbeddingClient`, `TokenBucket`, `EmbeddingRateLimiter`, `EmbeddingLiveTest`, `EmbeddingConfig`. |
| `diagnostics` | The checklist of the "Stato" section. | `DiagnosticsService`, `DiagnosticCheck`, `DiagnosticsReport`, `CheckStatus`. |
| `error` | Error model and traceability. | `ErrorCode`, `WarningCode`, `ExplorerException`, `DatabaseErrorTranslator`, `ApiExceptionHandler`, `RequestIdFilter`. |
| `api` | REST controllers (thin: validation and delegation). | `DiagnosticsController`, `DatasetController`, `ChunkController`, `SearchController`, `ApiPaths`. |

### 2.2 Configuration

`application.yml` maps every variable explicitly (`${VIZ_X:default}`) because Spring's relaxed binding
would map `VIZ_DISTANCE_METRIC` to `viz.distance.metric`. The values are bound to `ExplorerProperties`
and validated at startup: an illegal table name, a non-PostgreSQL JDBC URL or an out-of-range limit
stops the application with the property name in the message. An unreachable database is *not* a
configuration error: the pool starts empty and the diagnostics report the problem.

Main variables: `VIZ_DB_URL`, `VIZ_DB_USER`, `VIZ_DB_PASSWORD`, `VIZ_DB_SCHEMA`, `VIZ_DB_TABLE`,
`VIZ_DB_STATEMENT_TIMEOUT`, `VIZ_DB_CONNECTION_TIMEOUT`, `VIZ_DISTANCE_METRIC`, `VIZ_MAX_POINTS`,
`VIZ_SNAPSHOT_MEMORY_BUDGET_MB`, `VIZ_EMBEDDING_ENABLED`, `VIZ_EMBEDDING_BASE_URL`,
`VIZ_EMBEDDING_MODEL`, `VIZ_EMBEDDING_API_KEY`, `VIZ_SEARCH_RATE_LIMIT_PER_MINUTE`.

Actuator exposes only `health`, and the database is deliberately *not* part of it
(`management.health.db.enabled=false`): a stopped database must not make the backend unhealthy.

### 2.3 Request flows

**Snapshot (`DatasetService.currentSnapshot`)**

1. `VectorTableGuard` checks pgvector, the table, the `SELECT` permission and the columns, so errors
   are as precise as in the diagnostics (`PGVECTOR_MISSING` before `TABLE_NOT_FOUND`).
2. `TableInspector.fingerprint` computes `datasetVersion` = row count + hash of every `id` and `xmin`
   (an in-place update, as Spring AI's upsert does, also changes it).
3. A matching cached snapshot is returned. Otherwise one thread loads (a `ReentrantLock` makes the
   load single-flight; concurrent requests wait and reuse the result). Only the latest snapshot is kept.
4. `SnapshotLoader` runs one read-only `REPEATABLE READ` transaction: fingerprint, row counts, vector
   dimensions (mixed dimensions → `DIMENSION_MISMATCH`), index and metric, memory budget check
   (`SNAPSHOT_TOO_LARGE`), then the rows: `ORDER BY md5(id::text) LIMIT VIZ_MAX_POINTS` picks a
   deterministic sample when the table is larger, `embedding::real[]` is read with a fetch size of 500.
5. `SnapshotRowCollector` tolerates dirty data (NULL content → `""`, NULL or non-object metadata →
   `{}`, rows without embedding skipped; all counted as warnings), keeps only previews (160 characters)
   and scalar metadata, then sorts the rows in reading order (file, page, chunk index, id) with
   `ChunkFields`, the single place where derived fields are defined.

**Points (`GET /api/v1/points?normalize=`)** — `PointCloudService` takes the snapshot and asks
`ProjectionService` for its PCA. Projections are cached per `datasetVersion` and *effective* normalize
flag (normalization is applied only with the cosine metric). The response carries the coordinates, the
explained variance of each component and the total, and one entry per chunk.

**Neighbors (`GET /api/v1/chunks/{id}/neighbors?k=`)** — `NeighborService` reads the chunk vector as a
pgvector literal and runs `ORDER BY embedding <=> CAST(? AS vector) LIMIT ?` (operator of the dataset
metric, the chunk itself excluded) inside a read-only transaction that first executes
`SELECT set_config('enable_indexscan', 'off', true)`. PostgreSQL therefore scans every vector: the
result is *exact* for any `k` up to 50, while an HNSW index is approximate and returns at most
`hnsw.ef_search` (40) rows. The SQL template is returned for display.

**Compare (`GET /api/v1/compare?a=&b=`)** — one SQL statement computes `<=>`, `<->` and `<#>` between
the two stored vectors; cosine similarity = 1 − cosine distance. Undefined values (zero vectors) are
`null`.

**RAG simulation (`POST /api/v1/search`)** — `RagSearchService`:
1. `requireEnabled()` (503 `EMBEDDING_DISABLED` before touching the database);
2. current snapshot and PCA model for the requested `normalize` flag;
3. the shared rate limit (`RATE_LIMITED` with `Retry-After`);
4. the question is embedded (its length is logged, never its text) and its dimension is checked against
   the dataset (`DIMENSION_MISMATCH`);
5. `SimilarChunks.topK` runs the same top-K query as Spring AI `PgVectorStore` (same distance
   expression, index used), *without* the threshold clause; every result gets
   `passesThreshold = distance < 1 − similarityThreshold`, Spring AI's own comparison;
6. the question vector is projected with the same `PcaModel` as the points (exact, PCA is linear);
7. the response includes the Spring AI SQL for the metric, for example
   `SELECT *, embedding <=> ? AS distance FROM public.vector_store WHERE embedding <=> ? < ? ORDER BY distance LIMIT ?`.

**Vectors (`GET /api/v1/vectors?datasetVersion=`)** — `DatasetService.snapshotOfVersion` answers 409
`DATASET_CHANGED` when the version is not the current one; otherwise a `StreamingResponseBody` writes the
snapshot matrix as float32 little-endian, row-major, in the order of `/points`, with `X-Rows`,
`X-Dimensions` and `X-Dataset-Version`. Nothing is copied: the values go through a 64 KB buffer.

**Neighbor sample (`GET /api/v1/neighbors/sample?size=&k=`)** — `NeighborSampleService` picks `size`
chunks evenly spaced in reading order and `ExactNeighbors` scans the snapshot for the `k` nearest
chunks of each one, in parallel, with the ordering of the pgvector operator of the metric. The
candidates are the loaded chunks, the same points the view draws.

**Diagnostics (`GET /api/v1/diagnostics`)** — ten checks in a fixed order: `db.connection`,
`db.pgvector`, `table.exists`, `table.columns`, `table.rows`, `embedding.dimensions`, `embedding.norms`,
`table.index`, `metadata.keys`, `embedding.client`. Each one catches its own failure and returns
`{id, status, detail, code, facts}`; when a check fails, the checks depending on it are `SKIPPED`. The
endpoint always answers 200. A stopped database costs one connection timeout (5 s). Because the
frontend polls while something fails, status *changes* are logged, not every poll.
`POST /api/v1/diagnostics/embedding-test` makes one real call to the provider, only on request, and
shares the rate limit.

### 2.4 REST API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/v1/diagnostics` | Checklist; always 200. |
| POST | `/api/v1/diagnostics/embedding-test` | Live test of the embedding provider (rate limited). |
| GET | `/api/v1/dataset` | Summary: table, counts, sampling, dimensions, metric and its source, index, files, metadata keys, warnings, `datasetVersion`. |
| GET | `/api/v1/points?normalize=true` | Projection info and one entry per chunk (`id`, `x`, `y`, `z`, `file`, `page`, `chunkIndex`, `preview`, `contentLength`, scalar `metadata`). |
| GET | `/api/v1/chunks/{id}` | Full content and metadata, the embedding, its statistics (dimensions, norm, min, max, mean). |
| GET | `/api/v1/chunks?contains=` | Ids of the chunks whose full text contains the words (2–200 characters, case-insensitive). |
| GET | `/api/v1/chunks/{id}/neighbors?k=10` | Exact nearest neighbors (k 1–50) with distance, similarity and the SQL template. |
| GET | `/api/v1/compare?a=&b=` | Cosine similarity and distance, euclidean distance, dot product. |
| POST | `/api/v1/search` | RAG simulation: `{question, topK, similarityThreshold, normalize}` (defaults 5, 0.5, true). |
| GET | `/api/v1/vectors?datasetVersion=` | Snapshot vectors for the UMAP worker: `application/octet-stream`, float32 little-endian, row-major, order of `/points`; headers `X-Rows`, `X-Dimensions`, `X-Dataset-Version`; 409 `DATASET_CHANGED` for another version. |
| GET | `/api/v1/neighbors/sample?size=200&k=10` | Real neighbors (ids) of a deterministic sample (size 10–1000, k 1–50), for the projection fidelity score. |

Every parameter is validated with Bean Validation; every DTO is a Java record.

### 2.5 Error model

Every error is an RFC 9457 `ProblemDetail` (`application/problem+json`) with three extra members:
`code` (a stable `ErrorCode`), `requestId` and `timestamp`, plus safe extra properties when useful
(`sqlState`, `dimensionsFound`, `retryAfterSeconds`…). Responses never contain stack traces, SQL or
secrets; details are short English sentences written by the application, never driver or provider
messages.

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_FAILED` (also unreadable bodies and wrong parameter types; field errors without rejected values) |
| 404 | `NOT_FOUND` (unknown endpoint), `CHUNK_NOT_FOUND` |
| 405 | `METHOD_NOT_ALLOWED` |
| 409 | `DATASET_CHANGED`, `DIMENSION_MISMATCH` |
| 422 | `PROJECTION_NOT_POSSIBLE` |
| 429 | `RATE_LIMITED`, `EMBEDDING_RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
| 502 | `EMBEDDING_PROVIDER_ERROR`, `EMBEDDING_AUTH_FAILED`, `EMBEDDING_MODEL_NOT_FOUND` |
| 503 | `DB_UNAVAILABLE`, `DB_AUTH_FAILED`, `DB_PERMISSION_DENIED`, `DB_NOT_FOUND`, `PGVECTOR_MISSING`, `TABLE_NOT_FOUND`, `TABLE_SCHEMA_INVALID`, `SNAPSHOT_TOO_LARGE`, `EMBEDDING_DISABLED` |
| 504 | `DB_TIMEOUT`, `EMBEDDING_TIMEOUT` |

Non-blocking problems use `WarningCode` (diagnostics `WARN` and dataset warnings): `TABLE_EMPTY`,
`NULL_EMBEDDINGS_SKIPPED`, `DIMENSIONS_MIXED`, `INDEX_MISSING`, `METRIC_DIFFERS_FROM_INDEX`,
`FILE_KEY_MISSING`, `METADATA_NOT_OBJECT`, `CONTENT_NULL`, `EMBEDDING_KEY_MISSING`.

`DatabaseErrorTranslator` searches the whole cause chain for the PostgreSQL SQLSTATE:

| SQLSTATE | Code |
| --- | --- |
| `08xxx`, `57P01`–`57P03`, `53300` | `DB_UNAVAILABLE` |
| `28P01`, `28000` | `DB_AUTH_FAILED` |
| `42501` | `DB_PERMISSION_DENIED` |
| `3D000` | `DB_NOT_FOUND` |
| `42P01`, `3F000` | `TABLE_NOT_FOUND` |
| `42703` | `TABLE_SCHEMA_INVALID` |
| `42704`, `42883` | `PGVECTOR_MISSING` |
| `57014` | `DB_TIMEOUT` |

Without a SQLSTATE, timeout exception types map to `DB_TIMEOUT` and connection failures (pool timeout,
connection refused, unknown host) to `DB_UNAVAILABLE`. Anything else is unexpected.

**Logging**: errors are logged once, in `ApiExceptionHandler`. Client errors at INFO without their
content; environment problems (5xx except `INTERNAL_ERROR`) at WARN without a stack trace; unexpected
errors at ERROR with the stack trace. `RequestIdFilter` reuses a valid `X-Request-Id` (the frontend sends
one) or generates one, and puts it in the MDC (every log line), in the response header and in the
problem document. Chunk text, questions and keys are never logged at INFO.

### 2.6 PCA

`Pca.fit` works on a `float` copy of the vectors, L2-normalized when the metric is cosine and the flag
is on, then centered on the mean; all sums are `double`. It runs a **block power iteration** with 8
vectors for the 3 components: each iteration multiplies the centered data by the block (the 1536 × 1536
covariance matrix is never built), then a **Rayleigh–Ritz** step diagonalizes the 8 × 8 projected matrix
(cyclic Jacobi) and yields the Ritz pairs. It stops when the largest residual ‖C·z − θ·z‖ of the first
three pairs is below 10⁻⁶·θ₁, or after 100 iterations (`converged: false`). Rows are processed in
parallel in fixed blocks of 256 summed in a fixed order, the initial block comes from a fixed seed and
each component is oriented so its largest coordinate is positive: the result is bit-for-bit
deterministic. The explained variance of each axis is its Rayleigh quotient divided by the total
variance, i.e. the exact variance of the data along the axis that is drawn. Fewer than 3 vectors, fewer
than 3 dimensions or zero variance → `PROJECTION_NOT_POSSIBLE`. Typical cost: about 0.3 s for
5,000 × 1,536 on 4 cores.

## 3. Frontend

TypeScript (strict, `noUncheckedIndexedAccess`), React 19, Vite, TanStack Query (server state), Zustand
(UI state), zod (runtime validation), three.js used directly, CSS modules with a few CSS variables,
Vitest + Testing Library, ESLint + Prettier.

### 3.1 Structure

| Folder | Content |
| --- | --- |
| `src/api` | `http.ts` (`requestJson` and `requestBinary` on one core: `fetch` with timeout via `AbortController`, problem parsing, request id), `ApiError.ts` (the only error type), `schemas.ts` (zod schemas and types of every response), `vectors.ts` (checks and decodes the binary vectors), `endpoints.ts` (typed functions), `retry.ts`. |
| `src/i18n` | `it.ts`: every Italian string, including a message and a hint for every error and warning code. |
| `src/lib` | Pure, unit-tested logic: palettes, color mapping, display transform, filters, barcode grid, comparison math, projection fidelity, seeded random numbers, downloads, error messages, async state, formatting, safe `localStorage`. |
| `src/umap` | The UMAP feature: `protocol.ts` (messages between page and worker), `umapLayout.ts` (pure UMAP run), `umap.worker.ts` (thin worker shell), `umapRunner.ts` (download, worker, cancel, errors), `useUmapController.ts` (keeps the layout in step with the method and the data). |
| `src/state` | `uiStore.ts`: selection, comparison slots, color key, hidden categories, filters, text query, preferences, notices, the RAG overlay, the projection method and the UMAP job (a discriminated union: idle, loading, running, done, error). |
| `src/hooks` | `queries.ts` (data hooks and query keys; `usePointCloudView` returns the projection on screen, UMAP when its layout matches the points, PCA otherwise), `usePointStates.ts` (color mapping, visibility, filters, text matches), `useDataWatchers.ts`, `usePresentationMode.ts`. |
| `src/components` | Generic UI: collapsible `Section` with the "i" button, `AsyncView` (loading / empty / error with "Riprova" / success), `ErrorMessage`, `StatusIcon`. |
| `src/features/*` | One folder per sidebar section (status, guide, projection, colors, chunk, compare, search, exercises) plus header and notices. |
| `src/scene` | `SceneController` (three.js), `SceneView` (React shell), `SceneErrorBoundary`, `sceneModel.ts` (pure rules: focus and dimming, question placement, file paths and centroids), labels, captioned screenshots, WebGL detection. |

Components contain no business logic: they read hooks and render; the rules live in `src/lib` and
`src/scene/sceneModel.ts`.

### 3.2 Data flow

- **API client.** Every failure becomes one `ApiError` with `code`, `status` and `requestId`: backend
  problems keep their code; the client adds `NETWORK_ERROR`, `TIMEOUT`, `BACKEND_UNAVAILABLE` (a non-JSON
  502/503/504 from nginx while the backend restarts), `INVALID_RESPONSE` (non-JSON body or wrong shape)
  and `UNEXPECTED_STATUS`. Timeouts: 20 s by default, 30 s for diagnostics, 60 s for dataset and points,
  45 s for the search.
- **Query keys** include the `datasetVersion`: `['points', version, normalize]`,
  `['chunk', version, id]`, `['neighbors', version, id, k]`, `['compare', version, a, b]`,
  `['matches', version, text]`; plus `['diagnostics']` and `['dataset']`.
- **Polling.** Diagnostics every 10 s only while a check fails or the backend is unreachable. The dataset
  summary (cheap: guard, fingerprint, cached snapshot) every 30 s and on window focus.
- **Watchers** (`useDataWatchers`): when the diagnostics recover, every data query is invalidated; a new
  `datasetVersion` changes the keys, reloads the points (previous points stay on screen) and shows a
  notice; points from another version than the summary refresh the summary. A global `QueryCache`
  handler refreshes the diagnostics on environment errors and the summary on `DATASET_CHANGED`.
- **Retries** only for GET queries and only for `NETWORK_ERROR`, `TIMEOUT` and `BACKEND_UNAVAILABLE`
  (2 retries, exponential backoff). Database errors are not retried: the diagnostics poll takes over.
  Mutations (`/search`, the live test) never retry.

### 3.3 3D view

`SceneController` owns `Scene`, `PerspectiveCamera`, `WebGLRenderer`, `Raycaster` and `OrbitControls`.

- **Points**: two `InstancedMesh`es sharing one low-poly sphere (`IcosahedronGeometry(1, 1)`): an opaque
  one for emphasized points and a transparent one (opacity 0.16, no depth writes) for dimmed points,
  which are also smaller. Colors are instance colors. Buffers are allocated once per dataset and
  rewritten in place; points hidden in the legend are not drawn.
- **Focus rules** (`sceneModel.ts`): a point is dimmed when it fails the filters or is outside the
  focus; the focus is the selection and its real neighbors, else the RAG results, else the text matches.
- **Picking**: `Raycaster.intersectObjects` on both meshes; `instanceId` maps back to the point through a
  slot table; emphasized points win. Hover is resolved once per frame; a click is a pointer down/up
  closer than 5 px; double click frames the point with a short camera animation.
- **Overlays**: selected point (bigger, white back-face outline), lines to the real neighbors, dashed
  A–B comparison line with labels, the question marker of the RAG simulation (octahedron) with solid
  green lines to kept chunks and dashed orange lines to discarded ones, axes PC1–PC3 with the explained
  variance as constant-size sprites, optional grid.
- **Display transform**: the coordinates of the method (PCA from the backend, UMAP from the worker) are
  centered on their bounding box and scaled uniformly so the largest extent is 1
  (`lib/displayTransform.ts`); the same transform places the PCA question marker.
- **Projection methods**: with UMAP the badge says "conserva i vicini, non le distanze", the axes are
  labeled UMAP 1–3 without variance, the question marker sits at the similarity-weighted average of its
  results (labeled approximate) and the comparison panel does not compare distances.
- **File guides**: "Percorsi" joins the chunks of each file in reading order (one `LineSegments` with
  vertex colors); "Centroidi" draws a small cross and the file name at the mean of the file's points.
- **Screenshot**: a frame is rendered and copied at once onto a 2D canvas with a caption band (dataset
  and projection caveat), then downloaded as PNG. Presentation mode enlarges the labels.
- **Rendering on demand**: the animation loop only calls `controls.update()` and renders when the camera
  moved or something changed. Device pixel ratio capped at 2; `ResizeObserver` for the size.
- **Resilience and hygiene**: WebGL 2 detection with an explanatory message, `webglcontextlost` handled
  with "Ricarica la vista", an error boundary around the lazily loaded view, and `dispose()` that stops
  the loop, removes listeners and frees geometries, materials, textures and the renderer.

### 3.4 Accessibility and language

The "Chunk visibili" list is the accessible alternative to the canvas: every chunk can be selected with
the keyboard. Buttons and icons have Italian labels, status icons combine shape and color, colors use
the colorblind-safe Okabe–Ito palette and viridis, and the comparison and RAG lines differ by pattern
as well as color. All texts come from `src/i18n/it.ts`; technical details from the backend stay in
English and are shown in small print.

## 4. Security

- **Read-only by construction**: read-only pool (`readOnlyMode=always`, so even auto-commit statements
  run in a read-only session), only `SELECT` statements (the neighbor query changes a planner setting
  with `SELECT set_config(...)`), statement timeout on every connection, a dedicated read-only role
  documented in `collegamento-database-vettoriale.md`.
- **No SQL injection**: schema and table names come only from validated configuration
  (`^[a-z_][a-z0-9_]{0,62}$`) and are quoted by `SqlIdentifier`; every value is a bound parameter; vectors
  are bound as text and cast with `CAST(? AS vector)`.
- **Secrets** only in environment variables (`.env` is git-ignored, `.env.example` has empty keys);
  masked in `toString`; never logged, never in error responses; the embedding key never reaches the
  browser.
- **Bounded inputs and outputs**: question ≤ 2000 characters, `topK` ≤ 20, `k` ≤ 50, ids ≤ 256
  characters, text search 2–200 characters, at most `VIZ_MAX_POINTS` points, previews and metadata
  strings truncated, memory budget for the snapshot, rate limit on the paid provider.
- **XSS**: chunk text and metadata come from arbitrary PDFs and are always rendered as plain text by
  React; `dangerouslySetInnerHTML` is forbidden by an ESLint rule.
- **nginx** (frontend container): strict Content-Security-Policy (`'self'` only, `frame-ancestors 'none'`),
  `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, hidden server version.
- **Containers**: multi-stage builds on pinned minimal images, non-root users, read-only root filesystem
  with `tmpfs` for `/tmp`, `no-new-privileges`, `cap_drop: [ALL]`, memory limits, healthchecks. Only the
  frontend is published, on `127.0.0.1` by default (`VIZ_BIND_ADDRESS`); the backend is reachable only on
  the compose network.

## 5. Deployment

```text
host 127.0.0.1:8088 ──► viz-frontend :8080 (nginx) ──/api/──► viz-backend :8080 ──► host.docker.internal:5432 (A, default)
                                                                                └──► postgres:5432 on the L17 network (B)
                                                                                └──► demo-db:5432 (D)
```

| File | Role |
| --- | --- |
| `docker-compose.yml` | `viz-backend` and `viz-frontend`. Only the frontend is published, on `${VIZ_BIND_ADDRESS:-127.0.0.1}:${VIZ_HTTP_PORT:-8088}`; the backend has `extra_hosts: host.docker.internal:host-gateway` (option A); the frontend waits for a healthy backend. Configuration from `.env`. |
| `compose.demo.yaml` | Option D: adds `demo-db` (synthetic data generated by `demo-db/init` at every start, in memory) and points the backend to it. |
| `compose.l17-network.yaml` | Option B: joins the external L17 network (`VIZ_L17_NETWORK`, default `docker_default`) and uses the host `postgres`. |
| `backend/Dockerfile` | Maven build stage (dependency layer cached, BuildKit cache for `~/.m2`, tests skipped: they need Docker) → JRE 21 runtime as user 10001, `-XX:MaxRAMPercentage=75`, `-XX:+ExitOnOutOfMemoryError`, healthcheck on `/actuator/health`. |
| `frontend/Dockerfile` | Node LTS build stage (`npm ci`, type check, lint, tests, build) → `nginx-unprivileged` on port 8080, healthcheck on `/healthz`. |
| `scripts/smoke-test.sh` | Checks a running stack through nginx (page, CSP header, diagnostics, `/api/v1/points`) and prints a readable report. |

**nginx** (`frontend/nginx`): SPA fallback (`index.html` is `no-cache`, hashed assets are `immutable`),
`/api/` proxy with a 90 s read timeout and 64 × 64 kB buffers for `/api/v1/points`, a 64 kB body limit,
gzip for JSON, problem documents and binary responses, the backend resolved through Docker's DNS at
request time, the security headers of §4 in every location.

**Containers**: `read_only` root filesystem with `tmpfs` for `/tmp`, `no-new-privileges`,
`cap_drop: [ALL]`, memory and process limits, non-root users. The backend health does not include the
database (D8), so a stopped database never blocks the stack.

## 6. Testing

| Layer | What | How to run |
| --- | --- | --- |
| Backend unit (Surefire, `*Test`) | PCA math (known axes, orthonormality, ratios, determinism, exact Jacobi comparison, performance), identifiers, properties validation, SQLSTATE translation, error responses (`@WebMvcTest`), request id filter, derived fields, metadata statistics, token bucket, embedding client against a real local HTTP server (statuses, timeout, unreachable provider, invalid bodies), float32 export, in-memory exact neighbors for every metric. | `cd backend && ./mvnw test` |
| Backend integration (Failsafe, `*IT`, Testcontainers) | `pgvector/pgvector:0.8.7-pg17` (the demo database image): snapshot loading, sampling, dirty data, versioning, single-flight cache, diagnostics on healthy / empty / missing / wrong / mixed tables, metric detection, permission denied, points, chunks, exact neighbors beyond `ef_search`, compare, text search, RAG search, rate limit, streamed vectors aligned with `/points`, the neighbor sample equal to the exact database query. Plain `postgres:17.11-alpine3.24`: `PGVECTOR_MISSING`. No database: the app starts and reports `DB_UNAVAILABLE`. Database stopped and restarted while the app runs: recovery without restart. | `cd backend && ./mvnw verify` (needs Docker) |
| Frontend (Vitest + Testing Library, jsdom) | Pure modules, zod schemas, HTTP client failures, binary vectors, every sidebar section in loading / empty / error / success states, the RAG panel enabled and disabled, the live test, WebGL fallback and error boundary, UMAP (real umap-js on small data: shape, clusters, determinism, normalization), the runner with a fake worker, the UMAP controller, fidelity, question placement, file guides, presentation mode. | `cd frontend && npm ci && npm run typecheck && npm run lint && npm test` |

The frontend Docker image runs the type check, the lint and the tests during the build (D24);
`scripts/smoke-test.sh` checks a running stack through nginx.
