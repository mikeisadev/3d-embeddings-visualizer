import { describe, expect, it } from 'vitest';
import type { NeighborSample } from '../../api/schemas';
import { neighborSample } from '../../test/fixtures';
import { nearestIndices, projectionFidelity } from '../fidelity';
import { seededRandom } from '../random';

/** Four points on a line: 0 and 1 are close, 2 and 3 are close, the pairs are far apart. */
const LINE = new Float32Array([0, 0, 0, 0.1, 0, 0, 5, 0, 0, 5.3, 0, 0]);
const INDEX = new Map([
  ['a', 0],
  ['b', 1],
  ['c', 2],
  ['d', 3],
]);

describe('projection fidelity', () => {
  it('finds the nearest points, closest first, ties to the lower index', () => {
    expect(nearestIndices(LINE, 0, 2)).toEqual([1, 2]);
    expect(nearestIndices(LINE, 3, 1)).toEqual([2]);
    const tie = new Float32Array([0, 0, 0, 1, 0, 0, -1, 0, 0]);
    expect(nearestIndices(tie, 0, 1)).toEqual([1]);
    expect(nearestIndices(LINE, 0, 10)).toEqual([1, 2, 3]);
  });

  it('is 100% when every real neighbor is also the nearest point on screen', () => {
    expect(projectionFidelity(LINE, INDEX, neighborSample)).toEqual({
      share: 1,
      meanKept: 1,
      k: 1,
      samples: 4,
    });
  });

  it('counts how many real neighbors the projection keeps close', () => {
    const sample: NeighborSample = {
      ...neighborSample,
      samples: [
        { id: 'a', neighbors: ['c'] },
        { id: 'b', neighbors: ['a'] },
        { id: 'c', neighbors: ['d'] },
        { id: 'd', neighbors: ['a'] },
      ],
    };

    expect(projectionFidelity(LINE, INDEX, sample)).toMatchObject({ share: 0.5, meanKept: 0.5 });
  });

  it('skips chunks that are not on screen and reports nothing measurable as null', () => {
    const sample: NeighborSample = {
      ...neighborSample,
      samples: [
        { id: 'a', neighbors: ['b'] },
        { id: 'zz', neighbors: ['a'] },
        { id: 'c', neighbors: ['zz'] },
      ],
    };
    expect(projectionFidelity(LINE, INDEX, sample)).toMatchObject({ share: 1, samples: 1 });
    expect(projectionFidelity(LINE, INDEX, { ...neighborSample, k: 0, samples: [] })).toBeNull();
    expect(projectionFidelity(LINE, new Map(), neighborSample)).toBeNull();
  });

  it('does not depend on a uniform scale or shift of the coordinates', () => {
    const random = seededRandom(3);
    const coordinates = Float32Array.from({ length: 60 }, () => random());
    const moved = coordinates.map((value, index) => value * 7 + (index % 3));
    const ids = new Map(Array.from({ length: 20 }, (_, index) => [`p${String(index)}`, index]));
    const sample: NeighborSample = {
      ...neighborSample,
      k: 3,
      samples: [0, 5, 9].map((index) => ({
        id: `p${String(index)}`,
        neighbors: nearestIndices(coordinates, index, 3).map((near) => `p${String(near)}`),
      })),
    };

    expect(projectionFidelity(moved, ids, sample)?.share).toBe(1);
  });
});

describe('seeded random', () => {
  it('repeats the same sequence for the same seed, in [0, 1)', () => {
    const first = seededRandom(42);
    const second = seededRandom(42);
    const values = Array.from({ length: 1000 }, () => first());

    expect(values.slice(0, 5)).toEqual(Array.from({ length: 5 }, () => second()));
    expect(values.every((value) => value >= 0 && value < 1)).toBe(true);
    expect(new Set(values).size).toBeGreaterThan(990);
    expect(seededRandom(7)()).not.toBe(seededRandom(42)());
  });
});
