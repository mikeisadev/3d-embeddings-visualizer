# Embedding Explorer 3D – Implementation plan

Milestones from §13 of the brief, implemented in order. After each milestone: build, run all
tests, fix failures, tick the checklist and write a short checkpoint.

Legend: `[x]` done, `[ ]` to do.

## M1 – Backend foundation

- [x] Maven project (`backend/`): Java 21, Spring Boot 4.1.2, starters webmvc / jdbc / validation / actuator, PostgreSQL driver, Maven wrapper
- [x] `ExplorerProperties`: one validated `@ConfigurationProperties` record bound from `VIZ_*` environment variables
- [x] Read-only Hikari `DataSource` (pool 5, read-only, statement timeout, starts with the database down)
- [x] `SqlIdentifier` + `TableRef` value objects (validated, quoted identifiers)
- [x] Request id filter (`X-Request-Id`, MDC, response header)
- [x] Error handling: `ErrorCode` enum, `ExplorerException`, SQLSTATE translator, `@RestControllerAdvice` with RFC 9457 `ProblemDetail`
- [x] Table inspector (pgvector version, columns, index, distance metric detection)
- [x] Diagnostics checklist (10 checks with dependencies and `SKIPPED`) – `GET /api/v1/diagnostics`
- [x] Dataset summary – `GET /api/v1/dataset` (files, metadata keys, warnings, metric, index)
- [x] Tests: unit (identifiers, SQLSTATE translator, error responses, properties validation) and Testcontainers integration (diagnostics: happy path, empty table, missing table, wrong columns, different metadata, `PGVECTOR_MISSING`, database down)

## M2 – Data and math

- [x] Snapshot loading: one consistent read-only transaction, deterministic order, sampling above `VIZ_MAX_POINTS`, dirty-data warnings, memory budget, `DIMENSION_MISMATCH`
- [x] `datasetVersion` fingerprint, single-flight cache that keeps only the latest snapshot
- [x] PCA (deterministic block power iteration + Rayleigh–Ritz, sign fix, explained variance), cached per `datasetVersion` and `normalize`
- [x] `GET /api/v1/points?normalize=`
- [x] `GET /api/v1/chunks/{id}` (full content, metadata, vector, stats) and `GET /api/v1/chunks?contains=` (full-text match)
- [x] `GET /api/v1/chunks/{id}/neighbors?k=` (real neighbors in SQL, SQL template returned)
- [x] `GET /api/v1/compare?a=&b=` (cosine, euclidean, dot product in SQL)
- [x] Tests: PCA math (known axes, orthonormality, ratios, determinism, performance), snapshot/sampling/neighbors/compare integration tests

## M3 – Frontend MVP

- [x] Vite + React + TypeScript (strict) project, ESLint + Prettier, Vitest + Testing Library
- [x] Typed API client (timeout, `ProblemDetail` parsing, `ApiError`), zod schemas, retry policy
- [x] Italian i18n (`src/i18n/it.ts`) and error code → message/hint mapping
- [x] UI store (selection, compare, colors, filters, preferences)
- [x] Layout: header, collapsible sidebar sections with "i" buttons, first-run overlay
- [x] Sections 1–6 and 8: Stato, Come leggere il grafico, Proiezione, Colori e filtri, Chunk selezionato, Confronta due chunk, Esercizi
- [x] `SceneController` (three.js): instanced points, hover/click/double click, neighbors lines, axes, reset view, resource hygiene
- [x] 3D view wrapper: error boundary, WebGL detection, context loss, resize
- [x] Dataset version watcher (reload points and tell the user why)
- [x] Tests: pure modules, zod schemas, component states (loading / empty / error / success) for every section

## M4 – RAG simulation

- [ ] `EmbeddingClient` interface: OpenAI-compatible implementation (RestClient, timeouts, error mapping) and disabled implementation
- [ ] Global token bucket rate limiter
- [ ] `POST /api/v1/search` (top-K without threshold, `passesThreshold`, projected query point, Spring AI SQL) and `POST /api/v1/diagnostics/embedding-test`
- [ ] Sidebar section 7 "Simula una ricerca RAG" (enabled and disabled states)
- [ ] Question marker and lines in 3D
- [ ] Tests: embedding client error mapping (mock HTTP server), rate limiter, search API, RAG panel states

## M5 – Docker and docs

- [ ] Backend image (multi-stage, JRE 21, non-root, healthcheck)
- [ ] Frontend image (Node LTS build with type check + tests, unprivileged nginx, `/api/` proxy, security headers, gzip)
- [ ] `docker-compose.yml` (hardened services, `.env`), `.env.example`, `.gitignore`
- [ ] `compose.demo.yaml` + demo database init scripts (synthetic, seeded, normalized vectors)
- [ ] `scripts/smoke-test.sh`
- [ ] Docs: `README.md`, `docs/collegamento-database-vettoriale.md`, `docs/guida-didattica.md`, `docs/architecture.md`, `docs/decisions.md`
- [ ] End-to-end check: demo stack up, smoke test green, database down → `DB_UNAVAILABLE` → recovery

## M6 – Optional features (only after M1–M5 are green)

- [ ] `GET /api/v1/vectors` (binary float32) + UMAP in a Web Worker (progress, cancel, errors)
- [ ] "Percorso del documento" lines and file centroids with labels
- [ ] "Fedeltà della proiezione" score (`GET /api/v1/neighbors/sample`)
- [ ] Presentation mode and PNG screenshot

## Checkpoints

### M1 – Backend foundation ✅

- Spring Boot 4.1.2 / Java 21 project with Maven wrapper; one validated `ExplorerProperties` record bound from `VIZ_*` variables (invalid values stop the startup with the property name).
- Read-only Hikari pool (`readOnlyMode=always`, statement timeout, starts and recovers with the database down).
- `SqlIdentifier`/`TableRef`, request id filter, RFC 9457 problems with `code`/`requestId`/`timestamp`, SQLSTATE translator.
- `GET /api/v1/diagnostics` (10 checks, dependencies, `SKIPPED`), `GET /api/v1/dataset` (snapshot-based summary; the snapshot loader and its cache landed here already because the summary needs them).
- Tests: 74 unit + 21 integration (Testcontainers `pgvector/pgvector:pg17` and plain `postgres:17-alpine`), including database stopped and restarted while the app runs.

### M2 – Data and math ✅

- Snapshot cache with `datasetVersion` (row count + hash of ids and row versions), single-flight loading, deterministic md5 sample, memory budget.
- PCA (block power iteration + Rayleigh–Ritz): 5,000 × 1,536 in about 0.3 s, verified against an exact eigendecomposition; cached per version and normalize flag.
- `GET /points`, `GET /chunks/{id}`, `GET /chunks?contains=`, `GET /chunks/{id}/neighbors` (exact, any k up to 50), `GET /compare`.
- Demo database scripts written early (needed to develop the frontend).
- Tests: 89 unit + 42 integration, all green.

### M3 – Frontend MVP ✅

- Vite 8 + React 19 + TypeScript 6 (strict, `noUncheckedIndexedAccess`), ESLint (type-checked) + Prettier, Vitest 5 + Testing Library.
- Typed API client (timeout, problem documents, one `ApiError`), zod validation of every response, retries only for transient GET failures.
- All Italian text in `src/i18n/it.ts`, including a message and a hint for every error and warning code.
- Sidebar sections 1–6 and 8 with explicit loading / empty / error / success states and "i" buttons; first-run overlay.
- three.js `SceneController`: two instanced meshes, hover tooltip, click and double click, real-neighbor lines, A/B comparison line, labeled axes, grid, reset view, context-loss recovery, render on demand.
- Verified in Chromium against the demo database: a new row is picked up in about 30 s with a notice; database stopped → `DB_UNAVAILABLE` with its hint → back by itself in about 10 s.
- Tests: 83 frontend tests (pure modules, zod schemas, HTTP client, every section in its states, WebGL fallback, error boundary).
