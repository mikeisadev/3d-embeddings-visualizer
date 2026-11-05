/**
 * The contract between the page and the UMAP worker. Kept free of umap-js imports, so the page
 * bundle stays small: only the worker loads the library.
 */

/** UMAP needs a few points to build a neighborhood graph; fewer make no sense to look at. */
export const UMAP_MIN_POINTS = 10;

/** The page asks the worker for a layout; the vectors buffer is transferred, not copied. */
export interface UmapRequest {
  readonly type: 'start';
  readonly rows: number;
  readonly dimensions: number;
  /** rows × dimensions values, row-major, in the order of the points. */
  readonly values: Float32Array<ArrayBuffer>;
  /** Scale every vector to length 1 first (the cosine geometry, as the PCA does). */
  readonly normalize: boolean;
  readonly seed: number;
}

/** UMAP first finds the neighbors of every vector (no progress available), then optimizes. */
export type UmapProgress =
  | { readonly phase: 'neighbors' }
  | { readonly phase: 'optimizing'; readonly epoch: number; readonly epochs: number };

export type UmapResponse =
  | { readonly type: 'progress'; readonly progress: UmapProgress }
  /** rows × 3 coordinates, in the order of the rows. */
  | { readonly type: 'done'; readonly layout: Float32Array<ArrayBuffer> }
  | { readonly type: 'error'; readonly message: string };
