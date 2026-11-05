import { requestJson } from './http';
import {
  chunkMatchesSchema,
  chunkSchema,
  comparisonSchema,
  datasetSchema,
  diagnosticsSchema,
  neighborsSchema,
  pointsSchema,
} from './schemas';

const BASE = '/api/v1';

/** The first load of a large table (snapshot + PCA) can take a few seconds. */
const SLOW_TIMEOUT_MS = 60_000;

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
};
