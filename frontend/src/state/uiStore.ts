import { create } from 'zustand';
import { type ColorKey, DEFAULT_COLOR_KEY } from '../lib/colorBy';
import { type Filters, NO_FILTERS, type PageRange } from '../lib/filters';

export type CompareSlot = 'A' | 'B';

export type Notice = 'datasetChanged' | 'datasetReloaded';

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
  /** Asks the 3D view to frame a chunk ("Mostra nel grafico"); the nonce repeats the request. */
  readonly focusRequest: { readonly id: string; readonly nonce: number } | null;
  readonly presentation: boolean;
  readonly notice: Notice | null;

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
  readonly focusOn: (id: string) => void;
  readonly setPresentation: (presentation: boolean) => void;
  readonly setNotice: (notice: Notice | null) => void;
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
  focusRequest: null,
  presentation: false,
  notice: null,

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
  focusOn: (id) => {
    set((state) => ({ focusRequest: { id, nonce: (state.focusRequest?.nonce ?? 0) + 1 } }));
  },
  setPresentation: (presentation) => {
    set({ presentation });
  },
  setNotice: (notice) => {
    set({ notice });
  },
}));
