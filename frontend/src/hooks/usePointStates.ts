import { useMemo } from 'react';
import type { PointCloud } from '../api/schemas';
import { buildColorMapping, type ColorKey, type ColorMapping } from '../lib/colorBy';
import { passesFilters } from '../lib/filters';
import { computeVisibility } from '../scene/sceneModel';
import { useUiStore } from '../state/uiStore';
import { usePointCloudView, useTextMatches } from './queries';

const mappings = new WeakMap<PointCloud, Map<ColorKey, ColorMapping>>();

/** Color mapping of a response, computed once and shared by the legend and the 3D view. */
export function colorMappingOf(data: PointCloud, key: ColorKey): ColorMapping {
  let byKey = mappings.get(data);
  if (byKey === undefined) {
    byKey = new Map();
    mappings.set(data, byKey);
  }
  let mapping = byKey.get(key);
  if (mapping === undefined) {
    mapping = buildColorMapping(data.points, key);
    byKey.set(key, mapping);
  }
  return mapping;
}

export function useColorMapping(): ColorMapping | null {
  const view = usePointCloudView();
  const key = useUiStore((state) => state.colorBy);
  return view === null ? null : colorMappingOf(view.data, key);
}

export interface PointStates {
  /** 1 when the point is drawn (its legend category is not hidden). */
  readonly visible: Uint8Array;
  /** Whether the point passes the file and page filters. */
  readonly inFilter: readonly boolean[];
  /** Indices of the chunks containing the searched text; `null` without an active search. */
  readonly textMatches: ReadonlySet<number> | null;
  readonly textSearch: {
    readonly active: boolean;
    readonly loading: boolean;
    readonly error: unknown;
    readonly count: number;
    readonly truncated: boolean;
  };
}

/** Visibility, filters and text matches of every point, recomputed only when they change. */
export function usePointStates(): PointStates | null {
  const view = usePointCloudView();
  const mapping = useColorMapping();
  const hiddenCategories = useUiStore((state) => state.hiddenCategories);
  const filters = useUiStore((state) => state.filters);
  const { query, active } = useTextMatches();
  const matches = query.data;

  return useMemo(() => {
    if (view === null || mapping === null) {
      return null;
    }
    const textMatches =
      active && matches !== undefined
        ? new Set(
            matches.ids
              .map((id) => view.indexById.get(id))
              .filter((index): index is number => index !== undefined),
          )
        : null;
    return {
      visible: computeVisibility(mapping.categoryOf, hiddenCategories),
      inFilter: passesFilters(view.data.points, filters),
      textMatches,
      textSearch: {
        active,
        loading: active && query.isFetching,
        error: active ? query.error : null,
        count: matches?.ids.length ?? 0,
        truncated: matches?.truncated ?? false,
      },
    };
  }, [view, mapping, hiddenCategories, filters, active, matches, query.isFetching, query.error]);
}
