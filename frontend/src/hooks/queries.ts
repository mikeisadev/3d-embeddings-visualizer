import { keepPreviousData, useQuery } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { api } from '../api/endpoints';
import { retryDelay, shouldRetry } from '../api/retry';
import type { Diagnostics, PointCloud } from '../api/schemas';
import {
  type DisplayTransform,
  fitToUnitBox,
  flatCoordinates,
  toDisplayPositions,
} from '../lib/displayTransform';
import { type ProjectionMethod, type UmapState, useUiStore } from '../state/uiStore';

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
  neighborSample: (version: string) => ['neighborSample', version] as const,
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

/** Derived, shared views of a point cloud (computed once per response and layout). */
export interface PointCloudView {
  readonly data: PointCloud;
  /** The projection on screen: the PCA of the backend, or the UMAP layout of the worker. */
  readonly method: ProjectionMethod;
  readonly indexById: ReadonlyMap<string, number>;
  /** The coordinates of the method, three per point (PCA: in the units of the vectors). */
  readonly coordinates: Float32Array;
  readonly transform: DisplayTransform;
  /** The coordinates fitted into the unit box of the 3D view. */
  readonly positions: Float32Array;
}

const indexes = new WeakMap<PointCloud, ReadonlyMap<string, number>>();
const pcaViews = new WeakMap<PointCloud, PointCloudView>();
const umapViews = new WeakMap<Float32Array, PointCloudView>();

function indexOf(data: PointCloud): ReadonlyMap<string, number> {
  let index = indexes.get(data);
  if (index === undefined) {
    index = new Map(data.points.map((point, position) => [point.id, position]));
    indexes.set(data, index);
  }
  return index;
}

function buildView(
  data: PointCloud,
  method: ProjectionMethod,
  coordinates: Float32Array,
): PointCloudView {
  const transform = fitToUnitBox(coordinates);
  return {
    data,
    method,
    indexById: indexOf(data),
    coordinates,
    transform,
    positions: toDisplayPositions(transform, coordinates),
  };
}

/** The view of {@code data}: with its UMAP layout when one is given, otherwise its PCA. */
export function viewOf(data: PointCloud, umapLayout: Float32Array | null = null): PointCloudView {
  if (umapLayout !== null) {
    const cached = umapViews.get(umapLayout);
    if (cached?.data === data) {
      return cached;
    }
    const view = buildView(data, 'umap', umapLayout);
    umapViews.set(umapLayout, view);
    return view;
  }
  let view = pcaViews.get(data);
  if (view === undefined) {
    view = buildView(data, 'pca', flatCoordinates(data.points));
    pcaViews.set(data, view);
  }
  return view;
}

/** The UMAP layout of exactly these points, when UMAP is chosen and ready. */
export function umapLayoutFor(
  data: PointCloud,
  method: ProjectionMethod,
  umap: UmapState,
): Float32Array | null {
  const matches =
    method === 'umap' &&
    umap.status === 'done' &&
    umap.key.datasetVersion === data.datasetVersion &&
    umap.key.normalized === data.projection.normalized &&
    umap.layout.length === data.points.length * 3;
  return matches ? umap.layout : null;
}

/**
 * The points as the 3D view shows them. While UMAP is being computed (or when it does not
 * match the points, for example right after a new PDF), the PCA stays on screen.
 */
export function usePointCloudView(): PointCloudView | null {
  const data = usePoints().data;
  const method = useUiStore((state) => state.projectionMethod);
  const umap = useUiStore((state) => state.umap);
  return data === undefined ? null : viewOf(data, umapLayoutFor(data, method, umap));
}

/** The real neighbors of a sample of chunks, computed once per dataset version. */
export function useNeighborSample() {
  const version = useDatasetVersion();
  return useQuery({
    queryKey: queryKeys.neighborSample(version ?? ''),
    queryFn: ({ signal }) => api.neighborSample(signal),
    enabled: version !== null,
    staleTime: Infinity,
    retry: shouldRetry,
    retryDelay,
  });
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
