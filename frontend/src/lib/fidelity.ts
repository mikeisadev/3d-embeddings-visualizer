import type { NeighborSample } from '../api/schemas';

export interface FidelityScore {
  /** Share of the real neighbors that are also among the nearest points on screen, in [0, 1]. */
  readonly share: number;
  /** The same as an average count: how many of the k real neighbors are kept. */
  readonly meanKept: number;
  readonly k: number;
  /** Sampled chunks that could be measured. */
  readonly samples: number;
}

/**
 * The "Fedeltà della proiezione": for each sampled chunk, how many of its k real neighbors
 * (computed by the backend on the full vectors) are also among its k nearest points in the
 * projected coordinates. Uniform scaling and shifting do not change the ranking, so the raw
 * or the display coordinates give the same score. `null` when nothing can be measured.
 */
export function projectionFidelity(
  coordinates: Float32Array,
  indexById: ReadonlyMap<string, number>,
  sample: NeighborSample,
): FidelityScore | null {
  if (sample.k === 0) {
    return null;
  }
  let kept = 0;
  let expected = 0;
  let measured = 0;
  for (const entry of sample.samples) {
    const index = indexById.get(entry.id);
    const real = new Set(
      entry.neighbors
        .map((id) => indexById.get(id))
        .filter((neighbor): neighbor is number => neighbor !== undefined),
    );
    if (index === undefined || real.size === 0) {
      continue;
    }
    kept += nearestIndices(coordinates, index, real.size).filter((near) => real.has(near)).length;
    expected += real.size;
    measured++;
  }
  if (measured === 0) {
    return null;
  }
  return { share: kept / expected, meanKept: kept / measured, k: sample.k, samples: measured };
}

/** The {@code k} points nearest to point {@code query} (itself excluded); ties go to the lower index. */
export function nearestIndices(coordinates: Float32Array, query: number, k: number): number[] {
  const count = Math.floor(coordinates.length / 3);
  const qx = coordinates[query * 3] ?? 0;
  const qy = coordinates[query * 3 + 1] ?? 0;
  const qz = coordinates[query * 3 + 2] ?? 0;
  // Kept sorted by distance; k is small (10), so insertion beats sorting every point.
  const best: { index: number; distance: number }[] = [];
  for (let index = 0; index < count; index++) {
    if (index === query) {
      continue;
    }
    const dx = (coordinates[index * 3] ?? 0) - qx;
    const dy = (coordinates[index * 3 + 1] ?? 0) - qy;
    const dz = (coordinates[index * 3 + 2] ?? 0) - qz;
    const distance = dx * dx + dy * dy + dz * dz;
    if (best.length === k && distance >= (best[k - 1]?.distance ?? Infinity)) {
      continue;
    }
    let position = best.length;
    while (position > 0 && (best[position - 1]?.distance ?? 0) > distance) {
      position--;
    }
    best.splice(position, 0, { index, distance });
    if (best.length > k) {
      best.pop();
    }
  }
  return best.map((candidate) => candidate.index);
}
