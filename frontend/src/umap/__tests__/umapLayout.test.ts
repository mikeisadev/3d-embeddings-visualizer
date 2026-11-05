import { describe, expect, it } from 'vitest';
import { seededRandom } from '../../lib/random';
import type { UmapProgress } from '../protocol';
import { UMAP_MIN_POINTS } from '../protocol';
import { computeUmapLayout, type UmapInput } from '../umapLayout';

const DIMENSIONS = 16;
const PER_CLUSTER = 20;

/** Three well separated clusters of noisy vectors; row r belongs to cluster r % 3. */
function clusteredInput(seed = 1): UmapInput {
  const random = seededRandom(seed);
  const rows = PER_CLUSTER * 3;
  const values = new Float32Array(rows * DIMENSIONS);
  for (let row = 0; row < rows; row++) {
    for (let column = 0; column < DIMENSIONS; column++) {
      const center = column === (row % 3) * 4 ? 5 : 0;
      values[row * DIMENSIONS + column] = center + (random() - 0.5);
    }
  }
  return { rows, dimensions: DIMENSIONS, values, normalize: true, seed: 42 };
}

function distance(layout: Float32Array, a: number, b: number): number {
  return Math.hypot(
    (layout[a * 3] ?? 0) - (layout[b * 3] ?? 0),
    (layout[a * 3 + 1] ?? 0) - (layout[b * 3 + 1] ?? 0),
    (layout[a * 3 + 2] ?? 0) - (layout[b * 3 + 2] ?? 0),
  );
}

describe('computeUmapLayout', () => {
  it('returns three finite coordinates per vector and reports its progress', () => {
    const input = clusteredInput();
    const progress: UmapProgress[] = [];

    const layout = computeUmapLayout(input, (update) => progress.push(update));

    expect(layout).toHaveLength(input.rows * 3);
    expect(layout.every(Number.isFinite)).toBe(true);
    expect(progress[0]).toEqual({ phase: 'neighbors' });
    const last = progress.at(-1);
    expect(last?.phase).toBe('optimizing');
    if (last?.phase === 'optimizing') {
      expect(last.epoch).toBe(last.epochs);
    }
  });

  it('keeps the clusters apart', () => {
    const layout = computeUmapLayout(clusteredInput(), () => undefined);
    let inside = 0;
    let across = 0;
    let insidePairs = 0;
    let acrossPairs = 0;
    for (let a = 0; a < PER_CLUSTER * 3; a++) {
      for (let b = a + 1; b < PER_CLUSTER * 3; b++) {
        if (a % 3 === b % 3) {
          inside += distance(layout, a, b);
          insidePairs++;
        } else {
          across += distance(layout, a, b);
          acrossPairs++;
        }
      }
    }
    expect(inside / insidePairs).toBeLessThan((across / acrossPairs) * 0.5);
  });

  it('draws the same picture for the same data and seed', () => {
    const first = computeUmapLayout(clusteredInput(), () => undefined);
    const second = computeUmapLayout(clusteredInput(), () => undefined);

    expect(Array.from(second)).toEqual(Array.from(first));
  });

  it('ignores the length of the vectors when normalizing', () => {
    const input = clusteredInput();
    // Powers of two scale floating-point numbers exactly: the normalized vectors are identical.
    const longer = {
      ...input,
      values: input.values.map((value, index) => value * 2 ** (Math.floor(index / DIMENSIONS) % 4)),
    };

    const layout = computeUmapLayout(input, () => undefined);
    const layoutOfLonger = computeUmapLayout(longer, () => undefined);

    expect(Array.from(layoutOfLonger)).toEqual(Array.from(layout));
  });

  it('refuses inconsistent or too small inputs', () => {
    expect(() => computeUmapLayout({ ...clusteredInput(), rows: 59 }, () => undefined)).toThrow(
      /Expected 59 × 16 values/,
    );
    const few = UMAP_MIN_POINTS - 1;
    expect(() =>
      computeUmapLayout(
        { rows: few, dimensions: 2, values: new Float32Array(few * 2), normalize: false, seed: 1 },
        () => undefined,
      ),
    ).toThrow(/at least 10 vectors/);
  });
});
