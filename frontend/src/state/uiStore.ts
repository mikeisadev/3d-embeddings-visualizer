import { create } from 'zustand';
import type { ApiError } from '../api/ApiError';
import { type ColorKey, DEFAULT_COLOR_KEY } from '../lib/colorBy';
import { type Filters, NO_FILTERS, type PageRange } from '../lib/filters';
import type { UmapProgress } from '../umap/protocol';

export type CompareSlot = 'A' | 'B';

export type Notice = 'datasetChanged' | 'datasetReloaded';

/** The last RAG search, drawn in the 3D view as the question marker and its links. */
export interface RagOverlay {
  readonly datasetVersion: string;
  readonly normalized: boolean;
  readonly queryPoint: { readonly x: number; readonly y: number; readonly z: number };
  readonly results: readonly {
    readonly id: string;
    readonly passes: boolean;
    readonly similarity: number | null;
  }[];
}

/** PCA comes from the backend; UMAP is computed in the browser on request. */
export type ProjectionMethod = 'pca' | 'umap';

/** The data a UMAP layout belongs to: the same rows, prepared the same way. */
export interface LayoutKey {
  readonly datasetVersion: string;
  readonly normalized: boolean;
}

export function sameLayoutKey(a: LayoutKey, b: LayoutKey): boolean {
  return a.datasetVersion === b.datasetVersion && a.normalized === b.normalized;
}

/** The UMAP computation: download, then the worker, then a layout or an error. */
export type UmapState =
  | { readonly status: 'idle' }
  | { readonly status: 'loading'; readonly key: LayoutKey }
  | { readonly status: 'running'; readonly key: LayoutKey; readonly progress: UmapProgress }
  | { readonly status: 'done'; readonly key: LayoutKey; readonly layout: Float32Array }
  | { readonly status: 'error'; readonly key: LayoutKey; readonly error: ApiError };

const UMAP_IDLE: UmapState = { status: 'idle' };

export const DEFAULT_NEIGHBORS_K = 10;
export const DEFAULT_POINT_SIZE = 1;

/**
 * UI state only (what the user chose); server data lives in TanStack Query. Arrays instead of
 * Sets keep the state serializable and easy to compare.
 */
export interface UiState {
  readonly selectedId: string | null;
  readonly compareA: string | null;
  readonly compareB: string | null;
  readonly colorBy: ColorKey;
  readonly hiddenCategories: readonly string[];
  readonly filters: Filters;
  readonly textQuery: string;
  readonly normalize: boolean;
  readonly pointSize: number;
  readonly neighborsK: number;
  readonly showGrid: boolean;
  /** Lines joining the chunks of each file in reading order. */
  readonly showPaths: boolean;
  /** The center of each file, with its name. */
  readonly showCentroids: boolean;
  readonly projectionMethod: ProjectionMethod;
  readonly umap: UmapState;
  /** Incremented by "Riprova": starts the UMAP computation again for the same data. */
  readonly umapAttempt: number;
  /** Asks the 3D view to frame a chunk ("Mostra nel grafico"); the nonce repeats the request. */
  readonly focusRequest: { readonly id: string; readonly nonce: number } | null;
  readonly presentation: boolean;
  readonly notice: Notice | null;
  readonly ragOverlay: RagOverlay | null;

  readonly select: (id: string | null) => void;
  readonly setCompare: (slot: CompareSlot, id: string | null) => void;
  readonly setColorBy: (key: ColorKey) => void;
  readonly toggleCategory: (category: string) => void;
  readonly isolateCategory: (category: string, allCategories: readonly string[]) => void;
  readonly showAllCategories: () => void;
  readonly setFileFilter: (files: readonly string[] | null) => void;
  readonly setPageRange: (pages: PageRange) => void;
  readonly setTextQuery: (text: string) => void;
  readonly resetFilters: () => void;
  readonly setNormalize: (normalize: boolean) => void;
  readonly setPointSize: (size: number) => void;
  readonly setNeighborsK: (k: number) => void;
  readonly setShowGrid: (show: boolean) => void;
  readonly setShowPaths: (show: boolean) => void;
  readonly setShowCentroids: (show: boolean) => void;
  readonly setProjectionMethod: (method: ProjectionMethod) => void;
  readonly setUmap: (state: UmapState) => void;
  readonly retryUmap: () => void;
  readonly focusOn: (id: string) => void;
  readonly setPresentation: (presentation: boolean) => void;
  readonly setNotice: (notice: Notice | null) => void;
  readonly setRagOverlay: (overlay: RagOverlay | null) => void;
}

export const useUiStore = create<UiState>()((set) => ({
  selectedId: null,
  compareA: null,
  compareB: null,
  colorBy: DEFAULT_COLOR_KEY,
  hiddenCategories: [],
  filters: NO_FILTERS,
  textQuery: '',
  normalize: true,
  pointSize: DEFAULT_POINT_SIZE,
  neighborsK: DEFAULT_NEIGHBORS_K,
  showGrid: false,
  showPaths: false,
  showCentroids: false,
  projectionMethod: 'pca',
  umap: UMAP_IDLE,
  umapAttempt: 0,
  focusRequest: null,
  presentation: false,
  notice: null,
  ragOverlay: null,

  select: (id) => {
    set({ selectedId: id });
  },
  setCompare: (slot, id) => {
    set(slot === 'A' ? { compareA: id } : { compareB: id });
  },
  setColorBy: (key) => {
    set({ colorBy: key, hiddenCategories: [] });
  },
  toggleCategory: (category) => {
    set((state) => ({
      hiddenCategories: state.hiddenCategories.includes(category)
        ? state.hiddenCategories.filter((hidden) => hidden !== category)
        : [...state.hiddenCategories, category],
    }));
  },
  isolateCategory: (category, allCategories) => {
    set({ hiddenCategories: allCategories.filter((other) => other !== category) });
  },
  showAllCategories: () => {
    set({ hiddenCategories: [] });
  },
  setFileFilter: (files) => {
    set((state) => ({ filters: { ...state.filters, files } }));
  },
  setPageRange: (pages) => {
    set((state) => ({ filters: { ...state.filters, pages } }));
  },
  setTextQuery: (text) => {
    set({ textQuery: text });
  },
  resetFilters: () => {
    set({ filters: NO_FILTERS, textQuery: '' });
  },
  setNormalize: (normalize) => {
    set({ normalize });
  },
  setPointSize: (size) => {
    set({ pointSize: size });
  },
  setNeighborsK: (k) => {
    set({ neighborsK: k });
  },
  setShowGrid: (show) => {
    set({ showGrid: show });
  },
  setShowPaths: (show) => {
    set({ showPaths: show });
  },
  setShowCentroids: (show) => {
    set({ showCentroids: show });
  },
  setProjectionMethod: (method) => {
    set({ projectionMethod: method });
  },
  setUmap: (state) => {
    set({ umap: state });
  },
  retryUmap: () => {
    set((state) => ({ umap: UMAP_IDLE, umapAttempt: state.umapAttempt + 1 }));
  },
  focusOn: (id) => {
    set((state) => ({ focusRequest: { id, nonce: (state.focusRequest?.nonce ?? 0) + 1 } }));
  },
  setPresentation: (presentation) => {
    set({ presentation });
  },
  setNotice: (notice) => {
    set({ notice });
  },
  setRagOverlay: (overlay) => {
    set({ ragOverlay: overlay });
  },
}));
