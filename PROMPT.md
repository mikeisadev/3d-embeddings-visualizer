# Build brief: "Embedding Explorer 3D", a teaching tool to explore the pgvector embeddings of lesson L17

You are a senior full-stack engineer. Build the application described below, end to end, in the current (empty) folder. Read the whole brief before writing any code.

## 0. Working rules

1. **Plan first.** Create `PLAN.md` with the milestones of §13 as checklists, then implement them in order. After each milestone: build, run all tests, fix failures, tick the checklist and print a short checkpoint list of what was done.
2. **Decisions.** Everything decided in this brief is final. When something is not specified, choose the simplest robust option and record it in `docs/decisions.md` (one short entry: context, decision, consequence). Don't stop to ask questions unless you are truly blocked.
3. **No invented APIs.** Check the real API of every library version you install (official docs or the sources in the local dependency cache). Pin exact versions (`package-lock.json`, explicit versions in `pom.xml` where Spring Boot doesn't manage them).
4. **The L17 project and its database are read-only for you.** Don't modify its files, schema or data. Never run destructive Docker commands on other projects (`docker compose down -v`, `docker volume rm`, `docker system prune`).
5. **Keep it simple.** One backend, one frontend, no authentication system, no extra infrastructure (no Redis, no message brokers, no Kubernetes).
6. **Language.** Code, identifiers, comments, logs, API error codes and technical docs in **English**. Everything students see (UI text, README, the database connection guide, the classroom guide) in **Italian**.

## 1. Context

The tool is for a classroom course, "Full Stack Web Developer con Java e React". In lessons L16–L18 the students built a Spring Boot chatbot with Spring AI and RAG:

- **L17** indexed PDFs. Each PDF page is read, split into chunks (Spring AI `TokenTextSplitter`, about 500 tokens per chunk), each chunk becomes an embedding (OpenAI `text-embedding-3-small`, 1536 dimensions) and is stored in PostgreSQL with pgvector.
- **L18** explained tokens vs embeddings and the context window, and introduced a `department` metadata filter, stable document ids (`document_id`) and other embedding models (Ollama `bge-m3`, 1024 dims; ONNX `all-MiniLM-L6-v2`, 384 dims), each one in its own table.

The students have never "seen" an embedding. The tool must make them tangible: what a vector is, why similar texts end up close to each other, how the RAG search picks chunks, and what gets lost when 1536 dimensions are squeezed into 3.

## 2. Goal and scope

A small web app called **Embedding Explorer 3D**: a sidebar on the left with information and teaching tools, a three.js 3D view on the right where every point is a chunk stored in pgvector.

**Honesty rule (non-negotiable).** A 1536-dimension vector cannot be drawn directly: the 3D positions are a **projection**. The UI must say so clearly and show how much information the projection keeps (explained variance for PCA). Every "real" number shown to the user (distances, similarities, neighbors) must be computed on the full vectors, in the database or in the backend, never on the 3D coordinates.

**MVP (must have):**
- read-only connection to the L17 database, with a diagnostics checklist;
- 3D point cloud of all the chunks, projected with PCA (computed in the backend) and colored by file;
- hover tooltip and click selection; chunk details: full text, metadata, the vector as a "barcode" plus stats;
- real nearest neighbors of the selected chunk (from pgvector), highlighted in 3D;
- color by any metadata key, legend with show/hide, filters (file, page range, text search), searchable chunk list;
- "Confronta due chunk": real similarity next to the distance in the 3D view;
- "Simula una ricerca RAG": type a question, see the query point and the top-K chunks with the similarity threshold, exactly as Spring AI does it. It needs an embedding model, so it is optional at runtime and cleanly disabled when not configured;
- built-in teaching guide, complete error handling, Docker, tests, docs.

**Optional (only after the MVP is complete and green):**
- UMAP projection computed in a Web Worker (umap-js);
- "Percorso del documento" (lines that join the chunks of a file in reading order) and file centroids with labels;
- "Fedeltà della proiezione" score: how many real neighbors are also neighbors in 3D;
- presentation mode for the projector; PNG screenshot of the view.

## 3. The vector database (reference)

This is the database created in L17. Treat these values as defaults; all of them must be overridable through environment variables.

| Item | Value in L17 |
| --- | --- |
| Docker | image `pgvector/pgvector:pg17`, service `postgres`, container `chatbot-postgres`; the compose file is in the L17 project at `docker/docker-compose.yml` |
| Host and port | `localhost:5432` (published on the host) |
| Database / user / password | `chatbot` / `chatbot` / `chatbot123` (classroom credentials) |
| Extensions (`init.sql`) | `vector`, `hstore`, `uuid-ossp` |
| Table | `public.vector_store`, created by Spring AI `PgVectorStore` |
| Columns | `id uuid PRIMARY KEY`, `content text`, `metadata json` (json, not jsonb), `embedding vector(1536)` |
| Index | HNSW on `embedding` with `vector_cosine_ops` (Spring AI `distance-type=COSINE_DISTANCE`), default name `spring_ai_vector_index` |
| Embedding model | OpenAI `text-embedding-3-small`, 1536 dimensions, vectors normalized to length 1 |

**Metadata is free-form JSON.** Never assume a key exists: discover the keys at runtime. Known keys, used for friendly labels and smart defaults:
- `original_filename` (name of the uploaded file), `source` (stored file name: a UUID + `.pdf`), `file_name` (same as `source`, written by the PDF reader);
- `page_number` (1-based). The L17 text mentions `start_page_number` / `end_page_number`: support them as fallbacks, but `page_number` is the key the PDF reader really writes;
- `parent_document_id`, `chunk_index`, `total_chunks` (added by the text splitter in recent Spring AI versions);
- `department` (L18 §7), `document_id` (L18 §11.1), `excerpt_keywords` (L18 §11.5).

Derived fields:
- **file**: the first available of `original_filename`, `file_name`, `source`; otherwise "Sconosciuto";
- **page**: `page_number`, then `start_page_number`;
- **order inside a file**: page, then `chunk_index`, then `id`.

Variants to support through configuration: another table (e.g. `vector_store_bge_m3`), another schema, any dimension (detected from the data), another distance metric. Detect the metric from the operator class of the index on `embedding` (`vector_cosine_ops` → cosine `<=>`, `vector_l2_ops` → L2 `<->`, `vector_ip_ops` → inner product `<#>`); otherwise use the configured one (default cosine).

**How the containers reach the database** (implement A as the default, document all four):
- **A (default)**: through the host's published port: `jdbc:postgresql://host.docker.internal:5432/chatbot`, with `extra_hosts: ["host.docker.internal:host-gateway"]` on the backend service (required on Linux, harmless on Docker Desktop).
- **B**: join the L17 compose network as an external network (find its name with `docker network ls`; with the L17 layout it is usually `docker_default`) and use host `postgres` (or `chatbot-postgres`).
- **C**: backend started from the IDE: `jdbc:postgresql://localhost:5432/chatbot`.
- **D**: demo database (§10), with synthetic data, for development and tests without L17.

Useful SQL (also for the docs):

```sql
SELECT extname, extversion FROM pg_extension WHERE extname = 'vector';
SELECT count(*) FROM public.vector_store;
SELECT vector_dims(embedding) AS dims, count(*) FROM public.vector_store GROUP BY 1;
SELECT DISTINCT json_object_keys(metadata) FROM public.vector_store WHERE json_typeof(metadata) = 'object';
SELECT metadata->>'original_filename' AS file, count(*) FROM public.vector_store GROUP BY 1 ORDER BY 2 DESC;
SELECT embedding::real[] FROM public.vector_store LIMIT 1;   -- the raw numbers of one vector
```

Read-only role (recommended and documented, not mandatory in class):

```sql
CREATE ROLE viz_reader LOGIN PASSWORD 'change-me';
GRANT CONNECT ON DATABASE chatbot TO viz_reader;
GRANT USAGE ON SCHEMA public TO viz_reader;
GRANT SELECT ON ALL TABLES IN SCHEMA public TO viz_reader;
-- run as the table owner (chatbot), for tables created later, e.g. the L18 §12 ones
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO viz_reader;
```

## 4. Architecture

```text
Browser ──► viz-frontend (nginx: React build + reverse proxy /api) ──► viz-backend (Spring Boot, read-only) ──► PostgreSQL + pgvector (L17)
                                                                              └──► optional: an OpenAI-compatible /embeddings endpoint
```

- Same origin: nginx proxies `/api/` to the backend, so CORS is not needed.
- Only the backend talks to the database and to the embedding provider. API keys never reach the browser.
- The backend loads a **dataset snapshot** (ids, vectors, metadata summary) once, computes the PCA model on it and caches both under a `datasetVersion`. Every response about points carries the `datasetVersion`, so the frontend can tell that the data changed (for example, a new PDF was uploaded in the chatbot) and reload.

## 5. Backend (Spring Boot)

### 5.1 Stack
- Java 21, Spring Boot 4.1.x, Maven.
- Starters: `spring-boot-starter-webmvc` (blocking MVC, not WebFlux), `spring-boot-starter-jdbc` (use `JdbcClient`), `spring-boot-starter-validation`, `spring-boot-starter-actuator`, plus the PostgreSQL JDBC driver.
- Don't add Spring AI or JPA: the tool only reads rows and calls one HTTP endpoint. Staying independent from the chatbot's Spring AI version avoids model auto-configurations that require API keys at startup.
- Spring Boot 4 uses Jackson 3 (`tools.jackson.*` packages) and Testcontainers 2.x, whose module and package names changed: check them.
- Base package `com.example.embeddingexplorer`, organized by feature: `config`, `dataset`, `projection`, `search`, `embedding`, `diagnostics`, `error`, `api`.

### 5.2 Configuration
All settings in one validated `@ConfigurationProperties` record (`@Validated`), bound from environment variables, with the defaults of §3:

| Variable | Default | Notes |
| --- | --- | --- |
| `VIZ_DB_URL` | `jdbc:postgresql://host.docker.internal:5432/chatbot` | |
| `VIZ_DB_USER` / `VIZ_DB_PASSWORD` | `chatbot` / no default in code | the password comes only from the environment |
| `VIZ_DB_SCHEMA` / `VIZ_DB_TABLE` | `public` / `vector_store` | validated identifiers |
| `VIZ_DISTANCE_METRIC` | `auto` | `auto`, `cosine`, `l2`, `inner_product` |
| `VIZ_MAX_POINTS` | `5000` | above it: deterministic sample, shown in the UI |
| `VIZ_EMBEDDING_ENABLED` | `false` | enables "Simula una ricerca RAG" |
| `VIZ_EMBEDDING_BASE_URL` | `https://api.openai.com/v1` | any OpenAI-compatible endpoint |
| `VIZ_EMBEDDING_MODEL` | `text-embedding-3-small` | must be the model used to index the table |
| `VIZ_EMBEDDING_API_KEY` | none | optional for local providers |
| `VIZ_SEARCH_RATE_LIMIT_PER_MINUTE` | `10` | protects the paid endpoint |

- Invalid configuration (e.g. a table name with illegal characters) fails at startup with a clear message.
- An unreachable database must **not** prevent startup: the app starts, reports the problem through the diagnostics and recovers by itself when the database comes back (Hikari `initialization-fail-timeout: -1`, `connection-timeout` 5 s).
- Small pool (max 5), `read-only: true`, a statement timeout on every connection (e.g. `connection-init-sql: SET statement_timeout = '15s'`).
- Actuator: expose only `health`.

### 5.3 Data access rules
- Only `SELECT` statements, every value a bound parameter.
- Schema and table names can't be bound: they come only from validated configuration (pattern `^[a-z_][a-z0-9_]{0,62}$`), wrapped in a small `SqlIdentifier` value object that quotes them. Never build SQL from request input.
- Pass vectors to SQL as a bound string cast to `vector` (`CAST(? AS vector)`); read them with `embedding::real[]`.
- Ids are UUIDs in L17, but Spring AI also allows text or numeric ids: treat them as opaque strings in the API and compare with `id::text = ?` (or cast to the column type detected by the diagnostics).
- **Snapshot loading**: one query with a deterministic order (file, page, `chunk_index`, id) and a fetch size, at most `VIZ_MAX_POINTS` rows. If the table has more, take a deterministic sample (e.g. `ORDER BY md5(id::text)`), set `sampled = true` with both counts, and the UI must show it.
- Robust to dirty data: skip rows with a NULL embedding and count them as a warning; NULL content becomes an empty string; NULL or non-object metadata becomes `{}` and is counted as a warning.
- **`datasetVersion`**: a cheap fingerprint, e.g. `count(*)` plus `md5(string_agg(id::text, ',' ORDER BY id))`, checked before reusing the cache. Loading is single-flight (concurrent requests wait for the same load). Keep only the latest snapshot and refuse snapshots above a configurable memory budget.
- Mixed dimensions in one table (possible only with an untyped `vector` column): don't mix them; return `DIMENSION_MISMATCH` with the dimensions found.

### 5.4 REST API (`/api/v1`, JSON unless stated)

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/diagnostics` | The checklist of §5.7. Always 200: each check reports its own failure. |
| POST | `/diagnostics/embedding-test` | Live test of the embedding provider (one short call). Rate limited, run only on explicit request because it costs money. |
| GET | `/dataset` | Summary: schema, table, row count, sampling info, dimensions, distance metric (and whether detected or configured), index (type, name, operator class), files with chunk counts, metadata keys (distinct values count, up to 5 sample values, value type), warnings, `datasetVersion`. |
| GET | `/points?normalize=true` | Everything the 3D view needs: `datasetVersion`, projection info (method `pca`, explained variance ratio of each component and the total) and one entry per chunk: `id`, `x`, `y`, `z`, `file`, `page`, `chunkIndex`, `preview` (first 160 chars), `contentLength`, `metadata` (scalar values only, strings cut at 200 chars). |
| GET | `/chunks/{id}` | Full content, full metadata, `embedding` (array of floats) and stats: dimensions, L2 norm, min, max, mean. |
| GET | `/chunks/{id}/neighbors?k=10` | Real nearest neighbors computed by the database with the detected metric (`ORDER BY embedding <=> CAST(? AS vector) LIMIT ?`, excluding the chunk itself): id, distance, similarity (cosine: `1 - distance`), file, page, preview. Also returns the SQL template used, for display. `k` between 1 and 50. |
| GET | `/compare?a={id}&b={id}` | Computed in SQL on the full vectors: cosine similarity and distance, euclidean distance, dot product. |
| POST | `/search` | RAG simulation, body `{question, topK, similarityThreshold, normalize}`. See below. |
| GET | `/vectors?datasetVersion=…` | Optional (UMAP): the snapshot vectors as `application/octet-stream`, float32 little-endian, row-major, in the same order as `/points`; headers `X-Rows`, `X-Dimensions`, `X-Dataset-Version`; 409 `DATASET_CHANGED` when the version differs. |
| GET | `/neighbors/sample?size=200&k=10` | Optional: the true neighbor ids of a deterministic sample, for the projection quality score. |

`POST /search` in detail:
- validation: question 1–2000 characters, `topK` 1–20 (default 5), `similarityThreshold` 0–1 (default 0.5: the values of the L18 `RagService`);
- embed the question and check that its dimension matches the dataset;
- return the top-K chunks by distance **without** applying the threshold in SQL, each with `similarity` and `passesThreshold`, so the students see what the threshold cuts;
- also return the query point projected with the PCA model of the same `normalize` flag as the view, the model name, the latency, and the SQL that Spring AI's `PgVectorStore` runs for the same search: `SELECT *, embedding <=> ? AS distance FROM vector_store WHERE embedding <=> ? < ? ORDER BY distance LIMIT ?`, where the third parameter is `1 - similarityThreshold`;
- 503 `EMBEDDING_DISABLED` when the feature is off; rate limited.

Every DTO is a Java record; every parameter is validated with Bean Validation.

### 5.5 Projection (PCA)
- Input: the snapshot vectors. When the metric is cosine and `normalize=true` (default), L2-normalize them first, so the view reflects cosine geometry.
- Center on the mean. Compute the top 3 principal components with a deterministic method: power iteration with deflation (fixed initial vector, convergence tolerance, iteration cap) or the SVD of a well-known Apache-licensed library such as EJML. Fix the sign of each component (largest-magnitude coordinate positive) so the view doesn't flip between runs.
- Explained variance ratio = eigenvalue / total variance. For text embeddings the first three components usually explain a minority of the variance (often less than 30%): that is expected and is itself a lesson, and the UI shows it.
- Keep the model (mean, components, normalize flag) with the snapshot, cached per `datasetVersion` and `normalize` flag, so query vectors and file centroids are projected with exactly the same transform as the points on screen.
- Fewer than 3 vectors, or zero variance: `PROJECTION_NOT_POSSIBLE` with a clear detail.
- Performance target: under 2 s for 5,000 × 1536 on a laptop.
- Unit-test the math on synthetic data with known axes: orthonormal components, ratios in [0, 1] and decreasing, determinism.

### 5.6 Embedding client (optional feature)
- An `EmbeddingClient` interface with two implementations: one for any **OpenAI-compatible** `/embeddings` endpoint (OpenAI; Ollama at `http://host.docker.internal:11434/v1`; Docker Model Runner at `http://model-runner.docker.internal/engines/v1`), built on Spring `RestClient` with explicit timeouts (connect 5 s, read 20 s); and a "disabled" one used when `VIZ_EMBEDDING_ENABLED=false`.
- Request body `{"model": …, "input": …, "encoding_format": "float"}`; send `Authorization: Bearer …` only when a key is set.
- Map upstream failures to error codes: 401/403 → `EMBEDDING_AUTH_FAILED`, 404 → `EMBEDDING_MODEL_NOT_FOUND`, 429 → `EMBEDDING_RATE_LIMITED`, timeout → `EMBEDDING_TIMEOUT`, anything else → `EMBEDDING_PROVIDER_ERROR`.
- Never log the key, never put it in an error. Log the question length, not its text.
- The question must be embedded with the same model used to index the table (L18 §12.1). The app can only check the dimension: on mismatch it returns `DIMENSION_MISMATCH`, and the UI reminds the rule.
- Rate limit `/search` and the live test with a simple global in-memory token bucket (`VIZ_SEARCH_RATE_LIMIT_PER_MINUTE`) → `RATE_LIMITED`.

### 5.7 Diagnostics
Each check returns `{id, status: OK | WARN | FAIL | SKIPPED, detail (English, technical), code (when failed), facts}`. Checks, in order; when one fails, the checks that depend on it are `SKIPPED`:

1. `db.connection`: connection and server version;
2. `db.pgvector`: extension installed, and its version;
3. `table.exists`;
4. `table.columns`: `id`, `content`, `metadata`, and `embedding` of type `vector`;
5. `table.rows`: WARN when the table is empty;
6. `embedding.dimensions`: WARN when mixed;
7. `embedding.norms`: min, average and max L2 norm on a sample (informative: OpenAI vectors are ≈ 1);
8. `table.index`: index type, name and operator class, and the detected metric; WARN when missing (neighbors still work, just slower);
9. `metadata.keys`: keys found; WARN when no file key is present;
10. `embedding.client`: configuration only (enabled, base URL, model, key present yes/no). The live test is the separate endpoint.

## 6. Error handling (fundamental)

**Principles:** fail fast on programming and configuration errors; degrade gracefully on environment errors (database down, provider down); never swallow an exception; every error the user sees is understandable and actionable; every error in the logs is traceable.

**Backend:**
- One `@RestControllerAdvice` returns an RFC 9457 `ProblemDetail` for every error, with the extra properties `code` (a stable enum value), `requestId` and `timestamp`. No stack traces, SQL or secrets in responses.
- A filter assigns a `requestId` (reuses a valid `X-Request-Id`, otherwise generates one), puts it in the MDC and in the response header.
- Error codes (enum with their HTTP status):

| Status | Codes |
| --- | --- |
| 400 | `VALIDATION_FAILED` |
| 404 | `CHUNK_NOT_FOUND` |
| 409 | `DATASET_CHANGED`, `DIMENSION_MISMATCH` |
| 422 | `PROJECTION_NOT_POSSIBLE` |
| 429 | `RATE_LIMITED`, `EMBEDDING_RATE_LIMITED` |
| 500 | `INTERNAL_ERROR` |
| 502 | `EMBEDDING_PROVIDER_ERROR`, `EMBEDDING_AUTH_FAILED`, `EMBEDDING_MODEL_NOT_FOUND` |
| 503 | `DB_UNAVAILABLE`, `DB_AUTH_FAILED`, `DB_NOT_FOUND`, `PGVECTOR_MISSING`, `TABLE_NOT_FOUND`, `TABLE_SCHEMA_INVALID`, `EMBEDDING_DISABLED` |
| 504 | `DB_TIMEOUT`, `EMBEDDING_TIMEOUT` |

- Translate database failures by the PostgreSQL SQLSTATE found in the cause chain: `08xxx` → `DB_UNAVAILABLE`; `28P01`, `28000` → `DB_AUTH_FAILED`; `3D000` → `DB_NOT_FOUND`; `42P01` → `TABLE_NOT_FOUND`; `42703` → `TABLE_SCHEMA_INVALID`; `42704`, `42883` (type `vector` or operator `<=>` unknown) → `PGVECTOR_MISSING`; `57014` → `DB_TIMEOUT`. Unit-test the translator.
- Log once, at the boundary: WARN without stack trace for expected environment errors, ERROR with stack trace for unexpected ones, always with the `requestId`. Never log chunk text, questions or keys at INFO.

**Frontend:**
- A typed API client: `fetch` with a timeout (`AbortController`), parsing of the `ProblemDetail`; every failure (network error, timeout, non-JSON body, invalid shape) becomes one `ApiError` type with `code`, `status`, `requestId`.
- Validate every response at runtime with zod; a shape mismatch is an `INVALID_RESPONSE` error, not a crash.
- Map error codes to Italian messages and hints in one place (i18n). Example: `DB_UNAVAILABLE` → "Il database vettoriale non risponde." / "Avvia il database della L17 (`docker compose up -d` nella cartella `docker`) e controlla `VIZ_DB_URL`." Unknown codes fall back to a generic message that shows the code and the `requestId`.
- Every panel has explicit loading, empty, error (with a "Riprova" button) and success states. The app shell never goes blank: the sidebar and the diagnostics stay usable even when the dataset can't be loaded.
- An error boundary around the 3D view; detect missing WebGL and `webglcontextlost`, show a message and allow reloading the view.
- Web Worker errors (optional UMAP) are reported to the user; long tasks show progress and can be cancelled.
- Automatic retries only for idempotent GETs, with backoff, never for `/search`. On `DATASET_CHANGED`, or when a new `datasetVersion` arrives, reload the points and tell the user why.

## 7. Frontend

### 7.1 Stack
- TypeScript (strict, no `any`, `noUncheckedIndexedAccess`), React, Vite.
- three.js used directly, wrapped in a small `SceneController` class, so the students can recognize `Scene`, `PerspectiveCamera`, `WebGLRenderer`, `Raycaster` and `OrbitControls`.
- TanStack Query for server state, a tiny store (Zustand, or `useReducer` + context) for UI state, zod for validation.
- CSS modules with a few CSS variables (no heavy UI kit). Vitest + Testing Library, ESLint + Prettier.
- Optional: umap-js in a Web Worker.

### 7.2 Layout
- Left sidebar (about 380 px, scrollable, collapsible sections), 3D view filling the rest. A header bar with the app name, the dataset (`schema.table`, N chunks, dimensions) and, optionally, the "Modalità presentazione" toggle.
- Sidebar sections, in this order (titles in Italian):
  1. **Stato**: diagnostics checklist with icons, hints and "Riprova"; dataset summary (chunks, files, dimensions, metric, index) and the sampling warning when relevant.
  2. **Come leggere il grafico**: a short guide (§7.5).
  3. **Proiezione**: method (PCA; UMAP if implemented); explained variance of each component as small bars, plus the total; "Normalizza i vettori" toggle; point size. A permanent note: "Le posizioni sono una proiezione: le distanze vere sono quelle calcolate dal database."
  4. **Colori e filtri**: "Colora per" (file by default; page; chunk index; content length; or any metadata key, with a categorical palette for strings and a sequential one for numbers); legend with click to hide or isolate; filters by file and page range; text search that highlights the matching chunks; a searchable list of the visible chunks (click to select; it is also the accessible alternative to the canvas).
  5. **Chunk selezionato**: file, page, chunk index; full text as plain text; metadata table; vector stats (dimensions, norm, min, max, mean); the first 10 numbers; a "barcode" of all the values (a near-square grid sized to the dimension, e.g. 48 × 32 for 1536, with a diverging color scale and its legend); the list of real neighbors (similarity, file, page, preview) with "Mostra nel grafico".
  6. **Confronta due chunk**: pick A and B (from the view or the list); show the real cosine similarity and distance next to the distance in the 3D view, with one sentence on why they can disagree.
  7. **Simula una ricerca RAG**: question, `topK` and threshold sliders, "Cerca"; results ranked by similarity with an "Entra nel contesto" / "Scartato dalla soglia" badge; the SQL that Spring AI runs; a short note on cost. When the feature is disabled, keep the panel and explain what to configure.
  8. **Esercizi**: 4–6 short classroom exercises (§12).

### 7.3 3D view
- Points: an `InstancedMesh` of low-poly spheres (picking through `instanceId`), up to 5,000 points at 60 fps; colors through instance colors; points filtered out or unrelated to the selection are dimmed (smaller, more transparent).
- Interaction: orbit, zoom and pan; hover tooltip (file, page, preview); click to select; double click to focus the camera; "Reimposta vista" button; axes labeled PC1, PC2, PC3 with their explained variance; optional grid.
- Selection: the selected point is bigger and outlined, with lines to its real neighbors; the other points are dimmed.
- RAG simulation: a distinct marker for the question (e.g. a star or a cone) and lines to the top-K chunks, colored by "passes the threshold". With PCA the marker position is exact; with UMAP (optional) place it at the similarity-weighted average of its top-K chunks and label it as approximate.
- Display transform: scale and center the backend coordinates into a unit-sized box in the frontend, and apply the same transform to the question marker and the centroids.
- Resource hygiene: dispose geometries, materials and the renderer on unmount; handle resizing with `ResizeObserver`; don't recreate buffers on every render; cap the device pixel ratio at 2.
- Colors: a colorblind-safe categorical palette (e.g. Okabe–Ito, then a deterministic hash for extra categories) and a perceptual sequential scale (e.g. viridis) for numbers.

### 7.4 State and data
- TanStack Query keys include the `datasetVersion`. Refetch the diagnostics every 10 s while the database is down, otherwise on demand.
- Pure logic (color mapping, display transform, 3D distances, error mapping, zod schemas) lives in plain TypeScript modules with unit tests; components only render.
- All UI strings in `src/i18n/it.ts` (typed); no hard-coded user-facing strings in components.
- `localStorage` only for small preferences (e.g. "guide already seen"), always inside `try/catch`.

### 7.5 Teaching content (Italian, short, plain language)
- "Come leggere il grafico", in substance: each point is a chunk of a PDF; its embedding is a list of 1536 numbers; we can't draw 1536 dimensions, so we show the "shadow" of the points on the three directions where they differ the most (PCA), like the shadow of an object on a wall: useful, but some distances get squeezed. Close points usually talk about similar things; the real distances are computed by the database on all 1536 numbers.
- Reuse the course vocabulary: chunk; embedding as "la coordinata GPS del significato" (L18 §2.3); similarity = 1 − cosine distance; `topK` and `similarityThreshold` as in the L18 `RagService` (5 and 0.5).
- Every section has an "i" button with 2–4 sentences and the related lesson: L17 (indexing, similarity search), L18 §2.2–2.3 (embeddings), §2.4 (context), §5 (RAG advisor), §11 (ingestion), §12 (embedding models).
- A first-run overlay (dismissible) that points at the three main actions: select a chunk, look at its real neighbors, simulate a RAG search.

## 8. Security
- Read-only by construction: read-only connection, only `SELECT`, recommended dedicated role (§3). Identifiers only from validated configuration; values always bound.
- Secrets only in environment variables: commit `.env.example` (the L17 classroom database defaults are allowed there, marked as classroom-only; API keys always empty), put `.env` in `.gitignore`, never bake secrets into images. The embedding key stays in the backend.
- Validate and bound every input (sizes, ranges, lengths); cap response sizes (`VIZ_MAX_POINTS`, preview lengths).
- XSS: chunk text and metadata come from arbitrary PDFs and must be rendered as plain text (React escaping). Never `dangerouslySetInnerHTML`; never render them as Markdown or HTML.
- Network: publish only the frontend port, bound to `${VIZ_BIND_ADDRESS:-127.0.0.1}`. The docs explain that `0.0.0.0` shares the tool, and therefore the documents, with the whole network. The backend is reachable only on the compose network; no CORS (same origin).
- nginx security headers: a strict Content-Security-Policy (self only, plus `worker-src 'self' blob:` if the worker needs it), `X-Content-Type-Options: nosniff`, `Referrer-Policy: no-referrer`, `frame-ancestors 'none'`; hide the nginx version.
- Containers: multi-stage builds, minimal pinned base images, non-root users, `read_only: true` with `tmpfs` where needed, `security_opt: ["no-new-privileges:true"]`, `cap_drop: [ALL]`, memory limits, healthchecks.
- Actuator: health only. Rate-limit the endpoints that cost money.

## 9. Code quality
- Senior-level code: clean, modular, readable; small classes and functions with a single responsibility; package by feature; no dead or commented-out code.
- Java: constructor injection, `final` fields, records for DTOs and value objects, no static mutable state, no `null` collections, meaningful exception types; Javadoc on public types explaining the "why".
- TypeScript: discriminated unions for async state, pure functions for the math, hooks for logic, components without business logic.
- Comments explain intent and non-obvious decisions, in English. Consistent formatting (Prettier; standard Java style).
- No premature abstractions: one implementation per interface, except where this brief asks for more (embedding client).

## 10. Docker
- `docker-compose.yml` with `viz-backend` and `viz-frontend`. The frontend is published on `${VIZ_BIND_ADDRESS:-127.0.0.1}:${VIZ_HTTP_PORT:-8088}`; the backend has `extra_hosts: ["host.docker.internal:host-gateway"]`; `depends_on` with `condition: service_healthy`; configuration from `.env`.
- Backend image: Maven + JDK 21 build stage with dependency caching; JRE 21 runtime, non-root, `-XX:MaxRAMPercentage=75`, healthcheck on `/actuator/health`.
- Frontend image: Node LTS build stage (`npm ci`, type check, tests, build); unprivileged nginx runtime on port 8080 with SPA fallback, `/api/` proxy (sensible timeouts, larger buffers for `/api/v1/points`), gzip for JSON and binary responses.
- Demo database: `compose.demo.yaml`, used with `docker compose -f docker-compose.yml -f compose.demo.yaml up --build`. It adds `demo-db` (`pgvector/pgvector:pg17`) with init scripts that create the same table as Spring AI and about 300 **synthetic** vectors (seeded, normalized, 1536 dimensions, 3–4 clusters, a few "files" with pages), and points the backend to it. Content and metadata must say clearly that the data is synthetic (`"demo": true`).
- `scripts/smoke-test.sh`: after `up`, checks health, diagnostics and `/api/v1/points` through nginx, and prints a readable result.

## 11. Testing
- Backend unit tests: PCA, identifier validation, SQLSTATE translation, error responses, rate limiter, embedding client error mapping (mocked HTTP server).
- Backend integration tests with Testcontainers on `pgvector/pgvector:pg17`: snapshot loading, sampling, neighbors, compare, and diagnostics with an empty table, a missing table, wrong columns and different metadata. Test `PGVECTOR_MISSING` with a plain `postgres` image.
- Frontend: unit tests for the pure modules and the zod schemas; component tests for each sidebar section in loading, empty, error and success states; the RAG panel in its disabled state.
- Every milestone ends with all tests green; the Docker build runs the frontend tests.

## 12. Documentation to write
- `README.md` (Italian): what the tool does and what it does not (the projection caveat); quick start with the L17 database and with the demo database; configuration table; screenshot placeholder; troubleshooting.
- `docs/collegamento-database-vettoriale.md` (Italian), step by step:
  - connecting to the L17 database with options A–D of §3;
  - checking the data with pgAdmin or `psql` (the SQL of §3);
  - creating the read-only role;
  - pointing the tool to the L18 tables (`vector_store_bge_m3`, `vector_store_minilm`) or to another metric;
  - a troubleshooting table "Errore → Causa → Soluzione" covering every database error code of §6 (connection refused, password authentication failed, relation does not exist, type "vector" does not exist, Linux without `host-gateway`, timeouts…).
- `docs/guida-didattica.md` (Italian): how to use the tool in class, with exercises:
  - find two chunks that are close in 3D but not similar in reality, and explain why;
  - upload a new PDF in the chatbot and find where its chunks land;
  - ask the same question with `topK` 3 and 8, and with threshold 0.3 and 0.7;
  - read the explained variance and discuss what the projection loses;
  - compare two embedding tables from L18 §12.
- `docs/architecture.md` and `docs/decisions.md` (English).

## 13. Milestones and definition of done
1. **M1 – Backend foundation**: project skeleton, configuration, `SqlIdentifier`, request id filter, error handling, diagnostics, dataset summary, Testcontainers tests.
2. **M2 – Data and math**: snapshot loading and caching, PCA, `/points`, `/chunks/{id}`, neighbors, `/compare`, tests.
3. **M3 – Frontend MVP**: API client and schemas, layout and sidebar sections 1–6 and 8, 3D view with selection and neighbors, error states, i18n, tests.
4. **M4 – RAG simulation**: embedding client, `/search`, rate limit, sidebar section 7, question marker in 3D, tests.
5. **M5 – Docker and docs**: images, compose files, demo database, smoke test, README and docs.
6. **M6 – Optional features**, only after M1–M5 are done and green: UMAP worker and `/vectors`, document paths and centroids, projection quality score, presentation mode, screenshot.

**Definition of done:**
- `docker compose up --build` starts everything, and with the L17 database running `http://localhost:8088` shows the chunks colored by file;
- with the database stopped the app still loads, shows `DB_UNAVAILABLE` with its hint, and recovers by itself when the database comes back; the same works with the demo database;
- all tests and type checks pass; no secrets in the repository; docs complete.

Finish with a short checkpoint list: what was built, how to run it, known limitations.

## 14. Out of scope
Authentication, writing to the database, re-indexing or uploading PDFs (that is the chatbot's job), editing embeddings, multi-user features, cloud deployment.