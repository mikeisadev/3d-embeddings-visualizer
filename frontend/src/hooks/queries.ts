import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/endpoints';
import { retryDelay, shouldRetry } from '../api/retry';
import type { Diagnostics, PointCloud } from '../api/schemas';
import { type DisplayTransform, fitToUnitBox, toDisplayPositions } from '../lib/displayTransform';
import { useUiStore } from '../state/uiStore';

export const DIAGNOSTICS_POLL_MS = 10_000;
export const DATASET_POLL_MS = 30_000;
export const MIN_TEXT_QUERY = 2;
const TEXT_DEBOUNCE_MS = 300;

/** Query keys: everything about points and chunks includes the datasetVersion. */
export const queryKeys = {
  diagnostics: ['diagnostics'] as const,
  dataset: ['dataset'] as const,
  points: (version: string, normalize: boolean) => ['points', version, normalize] as const,
  chunk: (version: string, id: string) => ['chunk', version, id] as const,
  neighbors: (version: string, id: string, k: number) => ['neighbors', version, id, k] as const,
  compare: (version: string, a: string, b: string) => ['compare', version, a, b] as const,
  matches: (version: string, text: string) => ['matches', version, text] as const,
};

/** Polls every 10 s while something fails (database down, table missing…), otherwise on demand. */
export function useDiagnostics() {
  return useQuery({
    queryKey: queryKeys.diagnostics,
    queryFn: ({ signal }) => api.diagnostics(signal),
    retry: false,
    refetchInterval: (query) =>
      needsPolling(query.state.data, query.state.error) ? DIAGNOSTICS_POLL_MS : false,
  });
}

function needsPolling(data: Diagnostics | undefined, error: unknown): boolean {
  return error !== null || data?.status === 'FAIL';
}

/** The dataset summary; re-checked every 30 s so a newly indexed PDF shows up by itself. */
export function useDataset() {
  return useQuery({
    queryKey: queryKeys.dataset,
    queryFn: ({ signal }) => api.dataset(signal),
    retry: shouldRetry,
    retryDelay,
    refetchOnWindowFocus: true,
    refetchInterval: (query) => (query.state.status === 'success' ? DATASET_POLL_MS : false),
  });
}

export function useDatasetVersion(): string | null {
  return useDataset().data?.datasetVersion ?? null;
}

/** The projected points of the current dataset version; the previous ones stay on screen while reloading. */
export function usePoints() {
  const version = useDatasetVersion();
  const normalize = useUiStore((state) => state.normalize);
  return useQuery({
    queryKey: queryKeys.points(version ?? '', normalize),
    queryFn: ({ signal }) => api.points(normalize, signal),
    enabled: version !== null,
    placeholderData: keepPreviousData,
    staleTime: Infinity,
    retry: shouldRetry,
    retryDelay,
  });
}

/** Derived, shared views of a point cloud (computed once per response). */
export interface PointCloudView {
  readonly data: PointCloud;
  readonly indexById: ReadonlyMap<string, number>;
  readonly transform: DisplayTransform;
  readonly positions: Float32Array;
}

const views = new WeakMap<PointCloud, PointCloudView>();

export function viewOf(data: PointCloud): PointCloudView {
  let view = views.get(data);
  if (view === undefined) {
    const transform = fitToUnitBox(data.points);
    view = {
      data,
      indexById: new Map(data.points.map((point, index) => [point.id, index])),
      transform,
      positions: toDisplayPositions(transform, data.points),
    };
    views.set(data, view);
  }
  return view;
}

export function usePointCloudView(): PointCloudView | null {
  const data = usePoints().data;
  return data === undefined ? null : viewOf(data);
}

export function useChunk(id: string | null) {
  const version = useDatasetVersion();
  return useQuery({
    queryKey: queryKeys.chunk(version ?? '', id ?? ''),
    queryFn: ({ signal }) => api.chunk(id ?? '', signal),
    enabled: id !== null && version !== null,
    staleTime: Infinity,
    retry: shouldRetry,
    retryDelay,
  });
}

export function useNeighbors(id: string | null) {
  const version = useDatasetVersion();
  const k = useUiStore((state) => state.neighborsK);
  return useQuery({
    queryKey: queryKeys.neighbors(version ?? '', id ?? '', k),
    queryFn: ({ signal }) => api.neighbors(id ?? '', k, signal),
    enabled: id !== null && version !== null,
    staleTime: Infinity,
    placeholderData: keepPreviousData,
    retry: shouldRetry,
    retryDelay,
  });
}

export function useComparison(a: string | null, b: string | null) {
  const version = useDatasetVersion();
  return useQuery({
    queryKey: queryKeys.compare(version ?? '', a ?? '', b ?? ''),
    queryFn: ({ signal }) => api.compare(a ?? '', b ?? '', signal),
    enabled: a !== null && b !== null && version !== null,
    staleTime: Infinity,
    retry: shouldRetry,
    retryDelay,
  });
}

/** Full-text matches of the (debounced) search box; disabled below 2 characters. */
export function useTextMatches() {
  const version = useDatasetVersion();
  const text = useDebouncedValue(useUiStore((state) => state.textQuery).trim(), TEXT_DEBOUNCE_MS);
  const active = text.length >= MIN_TEXT_QUERY;
  const query = useQuery({
    queryKey: queryKeys.matches(version ?? '', text),
    queryFn: ({ signal }) => api.matches(text, signal),
    enabled: active && version !== null,
    staleTime: Infinity,
    retry: shouldRetry,
    retryDelay,
  });
  return { query, active };
}

function useDebouncedValue<T>(value: T, delayMs: number): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebounced(value);
    }, delayMs);
    return () => {
      clearTimeout(timer);
    };
  }, [value, delayMs]);
  return debounced;
}
