/**
 * Pure rules that decide how each point is drawn: hidden (legend), dimmed (filtered out or
 * unrelated to the current focus) or emphasized.
 */

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
