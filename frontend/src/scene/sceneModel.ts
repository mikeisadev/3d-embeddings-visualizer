/**
 * Pure rules of the 3D view: how each point is drawn (hidden by the legend, dimmed when
 * filtered out or unrelated to the current focus, or emphasized), where the RAG question
 * goes, and the reading paths and centers of the files.
 */
import { applyTransform, type DisplayTransform, type Vec3 } from '../lib/displayTransform';
import type { RagOverlay } from '../state/uiStore';

export interface FocusSources {
  readonly selected: number | null;
  readonly neighbors: readonly number[];
  readonly ragResults: readonly number[] | null;
  readonly textMatches: ReadonlySet<number> | null;
}

/**
 * The points in focus, by priority: the selected chunk and its real neighbors, else the
 * results of the RAG simulation, else the text matches; `null` when nothing is in focus.
 */
export function focusSet(sources: FocusSources): ReadonlySet<number> | null {
  if (sources.selected !== null) {
    return new Set([sources.selected, ...sources.neighbors]);
  }
  if (sources.ragResults !== null) {
    return new Set(sources.ragResults);
  }
  return sources.textMatches;
}

/** 1 when the point is drawn at full strength, 0 when it is dimmed (smaller, transparent). */
export function computeEmphasis(
  inFilter: readonly boolean[],
  focus: ReadonlySet<number> | null,
): Uint8Array {
  const emphasized = new Uint8Array(inFilter.length);
  inFilter.forEach((passes, index) => {
    emphasized[index] = passes && (focus === null || focus.has(index)) ? 1 : 0;
  });
  return emphasized;
}

/** 1 when the point is drawn at all: its category is not hidden in the legend. */
export function computeVisibility(
  categoryOf: readonly (string | null)[],
  hiddenCategories: readonly string[],
): Uint8Array {
  const hidden = new Set(hiddenCategories);
  const visible = new Uint8Array(categoryOf.length);
  categoryOf.forEach((category, index) => {
    visible[index] = category !== null && hidden.has(category) ? 0 : 1;
  });
  return visible;
}

/** What the question marker needs to know about the points on screen. */
export interface MarkerView {
  readonly method: 'pca' | 'umap';
  readonly datasetVersion: string;
  readonly normalized: boolean;
  readonly indexById: ReadonlyMap<string, number>;
  readonly transform: DisplayTransform;
  readonly positions: Float32Array;
}

export interface QuestionPlacement {
  readonly position: Vec3;
  /** True with UMAP: a new point cannot be projected, so it is placed among its results. */
  readonly approximate: boolean;
  readonly links: readonly { readonly index: number; readonly passes: boolean }[];
}

/**
 * Where the question of the last RAG search goes, only when the search belongs to the points
 * on screen. With PCA the backend projected the question with the same model and normalize
 * flag as the points: the position is exact. UMAP cannot place a new point, so the question
 * goes to the similarity-weighted average of its top-K chunks and is labeled approximate.
 */
export function placeQuestion(
  overlay: RagOverlay | null,
  view: MarkerView | null,
): QuestionPlacement | null {
  if (overlay === null || view?.datasetVersion !== overlay.datasetVersion) {
    return null;
  }
  const results = overlay.results.flatMap((result) => {
    const index = view.indexById.get(result.id);
    return index === undefined ? [] : [{ ...result, index }];
  });
  const links = results.map(({ index, passes }) => ({ index, passes }));
  if (view.method === 'pca') {
    return overlay.normalized === view.normalized
      ? { position: applyTransform(view.transform, overlay.queryPoint), approximate: false, links }
      : null;
  }
  if (results.length === 0) {
    return null;
  }
  const weighted = results.map(({ index, similarity }) => ({
    index,
    weight: Math.max(similarity ?? 0, 0),
  }));
  return { position: weightedMean(view.positions, weighted), approximate: true, links };
}

/** Weighted mean of some points; plain mean when no weight is positive. */
export function weightedMean(
  positions: Float32Array,
  items: readonly { readonly index: number; readonly weight: number }[],
): Vec3 {
  const total = items.reduce((sum, item) => sum + item.weight, 0);
  const sum = [0, 0, 0];
  for (const { index, weight } of items) {
    const share = total > 0 ? weight / total : 1 / items.length;
    for (let axis = 0; axis < 3; axis++) {
      sum[axis] = (sum[axis] ?? 0) + share * (positions[index * 3 + axis] ?? 0);
    }
  }
  return [sum[0] ?? 0, sum[1] ?? 0, sum[2] ?? 0];
}

const GUIDE_LABEL_LENGTH = 28;

/** The reading path and the center of one file, in display coordinates. */
export interface FileGuide {
  readonly label: string;
  /** sRGB hex color of the file (the colors of "Colora per: File"). */
  readonly color: string;
  /** The file's drawn chunks in reading order, three values per chunk. */
  readonly path: Float32Array;
  readonly centroid: Vec3;
}

/**
 * One guide per file with drawn points. The points arrive in reading order (file, page,
 * chunk index), so a file's path joins them as they come. The centroid is the mean of the
 * projected points; since the PCA is linear, it is exactly the projection of the file's mean
 * vector. With UMAP it is the center of the file's points on screen.
 */
export function fileGuides(
  files: readonly string[],
  visible: Uint8Array,
  positions: Float32Array,
  colorOf: (file: string) => string,
): FileGuide[] {
  const indicesByFile = new Map<string, number[]>();
  files.forEach((file, index) => {
    if (visible[index] === 0) {
      return;
    }
    const indices = indicesByFile.get(file) ?? [];
    indices.push(index);
    indicesByFile.set(file, indices);
  });
  return [...indicesByFile].map(([file, indices]) => {
    const path = new Float32Array(indices.length * 3);
    indices.forEach((index, position) => {
      path.set(positions.subarray(index * 3, index * 3 + 3), position * 3);
    });
    return {
      label: shorten(file, GUIDE_LABEL_LENGTH),
      color: colorOf(file),
      path,
      centroid: weightedMean(
        positions,
        indices.map((index) => ({ index, weight: 1 })),
      ),
    };
  });
}

/** At most {@code length} characters (code points, so an accented letter is never cut in half). */
function shorten(text: string, length: number): string {
  const characters = Array.from(text);
  return characters.length <= length ? text : `${characters.slice(0, length - 1).join('')}…`;
}
