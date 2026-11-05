import { describe, expect, it } from 'vitest';
import { computeEmphasis, computeVisibility, focusSet } from '../sceneModel';

describe('sceneModel', () => {
  it('focuses the selection and its neighbors first', () => {
    const focus = focusSet({
      selected: 1,
      neighbors: [2, 3],
      ragResults: [4],
      textMatches: new Set([0]),
    });
    expect(focus).toEqual(new Set([1, 2, 3]));
  });

  it('then the RAG results, then the text matches, else nothing', () => {
    expect(
      focusSet({ selected: null, neighbors: [], ragResults: [4], textMatches: new Set([0]) }),
    ).toEqual(new Set([4]));
    expect(
      focusSet({ selected: null, neighbors: [], ragResults: null, textMatches: new Set([0]) }),
    ).toEqual(new Set([0]));
    expect(
      focusSet({ selected: null, neighbors: [], ragResults: null, textMatches: null }),
    ).toBeNull();
  });

  it('dims points outside the filters or outside the focus', () => {
    expect(Array.from(computeEmphasis([true, true, false], null))).toEqual([1, 1, 0]);
    expect(Array.from(computeEmphasis([true, true, true], new Set([1])))).toEqual([0, 1, 0]);
  });

  it('hides the categories hidden in the legend', () => {
    expect(Array.from(computeVisibility(['a', 'b', null], ['b']))).toEqual([1, 0, 1]);
  });
});
