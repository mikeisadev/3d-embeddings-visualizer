/**
 * Fits the projected coordinates (PCA from the backend or UMAP from the worker) into a
 * unit-sized box centered on the origin, with one uniform scale: a different scale per axis
 * would distort the projection. The same transform places the question marker of the RAG
 * simulation. Coordinates are flat arrays, three values (x, y, z) per point.
 */

export type Vec3 = readonly [number, number, number];

export interface Coordinates {
  readonly x: number;
  readonly y: number;
  readonly z: number;
}

export interface DisplayTransform {
  readonly center: Vec3;
  readonly scale: number;
}

export const IDENTITY: DisplayTransform = { center: [0, 0, 0], scale: 1 };

/** The x, y, z of every point as a flat array. */
export function flatCoordinates(points: readonly Coordinates[]): Float32Array {
  const coordinates = new Float32Array(points.length * 3);
  points.forEach(({ x, y, z }, index) => {
    coordinates[index * 3] = x;
    coordinates[index * 3 + 1] = y;
    coordinates[index * 3 + 2] = z;
  });
  return coordinates;
}

export function fitToUnitBox(coordinates: Float32Array): DisplayTransform {
  if (coordinates.length < 3) {
    return IDENTITY;
  }
  const min = [Infinity, Infinity, Infinity];
  const max = [-Infinity, -Infinity, -Infinity];
  for (let index = 0; index < coordinates.length; index++) {
    const axis = index % 3;
    const value = coordinates[index] ?? 0;
    min[axis] = Math.min(min[axis] ?? Infinity, value);
    max[axis] = Math.max(max[axis] ?? -Infinity, value);
  }
  const [minX = 0, minY = 0, minZ = 0] = min;
  const [maxX = 0, maxY = 0, maxZ = 0] = max;
  const center: Vec3 = [(minX + maxX) / 2, (minY + maxY) / 2, (minZ + maxZ) / 2];
  const largestRange = Math.max(maxX - minX, maxY - minY, maxZ - minZ);
  return { center, scale: largestRange > 0 ? 1 / largestRange : 1 };
}

export function applyTransform(transform: DisplayTransform, point: Coordinates): Vec3 {
  return [
    (point.x - transform.center[0]) * transform.scale,
    (point.y - transform.center[1]) * transform.scale,
    (point.z - transform.center[2]) * transform.scale,
  ];
}

/** All the points in display coordinates, ready for the 3D buffers. */
export function toDisplayPositions(
  transform: DisplayTransform,
  coordinates: Float32Array,
): Float32Array {
  const positions = new Float32Array(coordinates.length);
  for (let index = 0; index < coordinates.length; index++) {
    const axis = index % 3;
    positions[index] =
      ((coordinates[index] ?? 0) - (transform.center[axis] ?? 0)) * transform.scale;
  }
  return positions;
}

/** Point {@code index} of a flat coordinate array. */
export function pointAt(coordinates: Float32Array, index: number): Coordinates {
  return {
    x: coordinates[index * 3] ?? 0,
    y: coordinates[index * 3 + 1] ?? 0,
    z: coordinates[index * 3 + 2] ?? 0,
  };
}

/** Euclidean distance between two points of a flat coordinate array. */
export function distanceBetween(coordinates: Float32Array, a: number, b: number): number {
  const p = pointAt(coordinates, a);
  const q = pointAt(coordinates, b);
  return Math.hypot(p.x - q.x, p.y - q.y, p.z - q.z);
}
