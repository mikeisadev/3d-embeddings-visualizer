import { requestJson } from './http';
import {
  chunkMatchesSchema,
  chunkSchema,
  comparisonSchema,
  datasetSchema,
  diagnosticsSchema,
  embeddingTestSchema,
  neighborsSchema,
  pointsSchema,
  type SearchRequest,
  searchResultSchema,
} from './schemas';

const BASE = '/api/v1';

/** The first load of a large table (snapshot + PCA) can take a few seconds. */
const SLOW_TIMEOUT_MS = 60_000;

/** An embedding call plus the database query; a cold local model can be slow. */
const SEARCH_TIMEOUT_MS = 45_000;

/** Typed functions for every backend endpoint. */
export const api = {
  diagnostics: (signal?: AbortSignal) =>
    requestJson(`${BASE}/diagnostics`, diagnosticsSchema, { signal, timeoutMs: 30_000 }),

  dataset: (signal?: AbortSignal) =>
    requestJson(`${BASE}/dataset`, datasetSchema, { signal, timeoutMs: SLOW_TIMEOUT_MS }),

  points: (normalize: boolean, signal?: AbortSignal) =>
    requestJson(
      `${BASE}/points?${new URLSearchParams({ normalize: String(normalize) }).toString()}`,
      pointsSchema,
      {
        signal,
        timeoutMs: SLOW_TIMEOUT_MS,
      },
    ),

  chunk: (id: string, signal?: AbortSignal) =>
    requestJson(`${BASE}/chunks/${encodeURIComponent(id)}`, chunkSchema, { signal }),

  neighbors: (id: string, k: number, signal?: AbortSignal) =>
    requestJson(
      `${BASE}/chunks/${encodeURIComponent(id)}/neighbors?${new URLSearchParams({ k: String(k) }).toString()}`,
      neighborsSchema,
      { signal },
    ),

  compare: (a: string, b: string, signal?: AbortSignal) =>
    requestJson(`${BASE}/compare?${new URLSearchParams({ a, b }).toString()}`, comparisonSchema, {
      signal,
    }),

  matches: (contains: string, signal?: AbortSignal) =>
    requestJson(
      `${BASE}/chunks?${new URLSearchParams({ contains }).toString()}`,
      chunkMatchesSchema,
      {
        signal,
      },
    ),

  /** POST, never retried automatically: it calls a (possibly paid) embedding provider. */
  search: (request: SearchRequest) =>
    requestJson(`${BASE}/search`, searchResultSchema, {
      method: 'POST',
      body: request,
      timeoutMs: SEARCH_TIMEOUT_MS,
    }),

  embeddingTest: () =>
    requestJson(`${BASE}/diagnostics/embedding-test`, embeddingTestSchema, {
      method: 'POST',
      timeoutMs: SEARCH_TIMEOUT_MS,
    }),
};
