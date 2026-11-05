import type { Point } from '../api/schemas';

export interface PageRange {
  readonly from: number | null;
  readonly to: number | null;
}

export interface Filters {
  /** Files to keep; `null` keeps every file. */
  readonly files: readonly string[] | null;
  readonly pages: PageRange;
}

export const NO_FILTERS: Filters = { files: null, pages: { from: null, to: null } };

export function isFilterActive(filters: Filters): boolean {
  return filters.files !== null || filters.pages.from !== null || filters.pages.to !== null;
}

/**
 * Whether each point passes the file and page filters. With a page range, chunks without a
 * page do not pass.
 */
export function passesFilters(points: readonly Point[], filters: Filters): boolean[] {
  const files = filters.files === null ? null : new Set(filters.files);
  const { from, to } = filters.pages;
  const pageActive = from !== null || to !== null;
  return points.map((point) => {
    if (files !== null && !files.has(point.file)) {
      return false;
    }
    if (!pageActive) {
      return true;
    }
    if (point.page === null) {
      return false;
    }
    return (from === null || point.page >= from) && (to === null || point.page <= to);
  });
}
