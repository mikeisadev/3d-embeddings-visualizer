import { describe, expect, it } from 'vitest';
import { IDENTITY } from '../../lib/displayTransform';
import type { RagOverlay } from '../../state/uiStore';
import {
  computeEmphasis,
  computeVisibility,
  fileGuides,
  type MarkerView,
  focusSet,
  placeQuestion,
  weightedMean,
} from '../sceneModel';

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

/** Three points on screen: a at x = 0, b at x = 1, c at x = 3. */
const VIEW: MarkerView = {
  method: 'pca',
  datasetVersion: 'v1',
  normalized: true,
  indexById: new Map([
    ['a', 0],
    ['b', 1],
    ['c', 2],
  ]),
  transform: { center: [1, 0, 0], scale: 0.5 },
  positions: new Float32Array([0, 0, 0, 1, 0, 0, 3, 0, 0]),
};

const OVERLAY: RagOverlay = {
  datasetVersion: 'v1',
  normalized: true,
  queryPoint: { x: 3, y: 2, z: 0 },
  results: [
    { id: 'a', passes: true, similarity: 0.75 },
    { id: 'zz', passes: true, similarity: 0.7 },
    { id: 'c', passes: false, similarity: 0.25 },
  ],
};

describe('placeQuestion', () => {
  it('projects the question exactly with the PCA, through the display transform', () => {
    expect(placeQuestion(OVERLAY, VIEW)).toEqual({
      position: [1, 1, 0],
      approximate: false,
      links: [
        { index: 0, passes: true },
        { index: 2, passes: false },
      ],
    });
  });

  it('draws nothing for another dataset version or another normalization', () => {
    expect(placeQuestion({ ...OVERLAY, datasetVersion: 'v0' }, VIEW)).toBeNull();
    expect(placeQuestion({ ...OVERLAY, normalized: false }, VIEW)).toBeNull();
    expect(placeQuestion(null, VIEW)).toBeNull();
    expect(placeQuestion(OVERLAY, null)).toBeNull();
  });

  it('places the question among its results with UMAP, and says it is approximate', () => {
    const umap: MarkerView = { ...VIEW, method: 'umap', transform: IDENTITY };

    const placement = placeQuestion({ ...OVERLAY, normalized: false }, umap);

    expect(placement?.approximate).toBe(true);
    // Weights 0.75 (a, x = 0) and 0.25 (c, x = 3): 0.75 · 0 + 0.25 · 3.
    expect(placement?.position).toEqual([0.75, 0, 0]);
    expect(placeQuestion({ ...OVERLAY, results: [] }, umap)).toBeNull();
  });

  it('averages evenly when no similarity is positive', () => {
    expect(
      weightedMean(VIEW.positions, [
        { index: 0, weight: 0 },
        { index: 2, weight: 0 },
      ]),
    ).toEqual([1.5, 0, 0]);
  });
});

describe('fileGuides', () => {
  it('joins the drawn chunks of each file in reading order and finds their center', () => {
    const positions = new Float32Array([0, 0, 0, 2, 0, 0, 9, 9, 9, 0, 4, 0, 0, 0, 6]);
    const files = ['ferie.pdf', 'ferie.pdf', 'ferie.pdf', 'sicurezza.pdf', 'sicurezza.pdf'];
    const visible = new Uint8Array([1, 1, 0, 1, 1]);

    const guides = fileGuides(files, visible, positions, (file) =>
      file === 'ferie.pdf' ? '#e69f00' : '#56b4e9',
    );

    expect(guides).toHaveLength(2);
    expect(guides[0]).toMatchObject({ label: 'ferie.pdf', color: '#e69f00', centroid: [1, 0, 0] });
    expect(Array.from(guides[0]?.path ?? [])).toEqual([0, 0, 0, 2, 0, 0]);
    expect(guides[1]?.centroid).toEqual([0, 2, 3]);
  });

  it('shortens long file names without cutting a character in half', () => {
    const name = `${'à'.repeat(40)}.pdf`;

    const [guide] = fileGuides([name], new Uint8Array([1]), new Float32Array(3), () => '#000000');

    expect(guide?.label).toBe(`${'à'.repeat(27)}…`);
    expect(fileGuides([name], new Uint8Array([0]), new Float32Array(3), () => '#000000')).toEqual(
      [],
    );
  });
});
