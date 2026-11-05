import { beforeEach, describe, expect, it } from 'vitest';
import { ApiError } from '../../api/ApiError';
import { sameLayoutKey, useUiStore } from '../uiStore';

const initial = useUiStore.getState();

describe('uiStore', () => {
  beforeEach(() => {
    useUiStore.setState(initial, true);
  });

  it('resets hidden categories when the color key changes', () => {
    useUiStore.getState().toggleCategory('ferie.pdf');
    expect(useUiStore.getState().hiddenCategories).toEqual(['ferie.pdf']);
    useUiStore.getState().setColorBy('meta:department');
    expect(useUiStore.getState().hiddenCategories).toEqual([]);
  });

  it('toggles and isolates categories', () => {
    const { toggleCategory, isolateCategory } = useUiStore.getState();
    toggleCategory('a');
    toggleCategory('a');
    expect(useUiStore.getState().hiddenCategories).toEqual([]);
    isolateCategory('b', ['a', 'b', 'c']);
    expect(useUiStore.getState().hiddenCategories).toEqual(['a', 'c']);
  });

  it('keeps focus requests separate from the selection', () => {
    useUiStore.getState().select('a');
    useUiStore.getState().focusOn('b');
    useUiStore.getState().focusOn('b');
    expect(useUiStore.getState().selectedId).toBe('a');
    expect(useUiStore.getState().focusRequest).toEqual({ id: 'b', nonce: 2 });
  });

  it('resets filters and text search together', () => {
    useUiStore.getState().setFileFilter(['ferie.pdf']);
    useUiStore.getState().setPageRange({ from: 2, to: 3 });
    useUiStore.getState().setTextQuery('ferie');
    useUiStore.getState().resetFilters();
    expect(useUiStore.getState().filters).toEqual({ files: null, pages: { from: null, to: null } });
    expect(useUiStore.getState().textQuery).toBe('');
  });

  it('starts UMAP again on "Riprova" and keeps the guides switches', () => {
    const key = { datasetVersion: 'v1', normalized: true };
    useUiStore.getState().setUmap({
      status: 'error',
      key,
      error: new ApiError({ code: 'UMAP_FAILED', status: null, requestId: null }),
    });

    useUiStore.getState().retryUmap();
    useUiStore.getState().setShowPaths(true);

    expect(useUiStore.getState().umap).toEqual({ status: 'idle' });
    expect(useUiStore.getState().umapAttempt).toBe(1);
    expect(useUiStore.getState().showPaths).toBe(true);
    expect(sameLayoutKey(key, { ...key })).toBe(true);
    expect(sameLayoutKey(key, { ...key, normalized: false })).toBe(false);
  });
});
