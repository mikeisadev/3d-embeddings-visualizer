import type { Comparison } from '../api/schemas';

/**
 * The real distance that the 3D distance must be compared with. The PCA projects the
 * normalized vectors when `normalized` is true, so the comparable distance is the one between
 * unit vectors, √(2 − 2·cos); otherwise it is the euclidean distance of the stored vectors.
 */
export function comparableDistance(comparison: Comparison, normalized: boolean): number | null {
  if (normalized) {
    return comparison.cosineSimilarity === null
      ? null
      : Math.sqrt(Math.max(0, 2 - 2 * comparison.cosineSimilarity));
  }
  return comparison.euclideanDistance;
}

/**
 * Share of the real distance still visible in 3D. A projection can only shorten distances,
 * so the value is at most 1 (rounding is clamped); `null` when it is not defined.
 */
export function shownShare(distance3d: number, realDistance: number | null): number | null {
  if (realDistance === null || realDistance <= 0) {
    return null;
  }
  return Math.min(1, distance3d / realDistance);
}
