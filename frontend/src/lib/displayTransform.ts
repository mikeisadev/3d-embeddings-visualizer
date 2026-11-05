/**
 * Fits the backend coordinates into a unit-sized box centered on the origin, with one uniform
 * scale (a different scale per axis would distort the projection). The same transform is
 * applied to the question marker of the RAG simulation.
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

export function fitToUnitBox(points: readonly Coordinates[]): DisplayTransform {
  if (points.length === 0) {
    return IDENTITY;
  }
  let minX = Infinity;
  let minY = Infinity;
  let minZ = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let maxZ = -Infinity;
  for (const { x, y, z } of points) {
    minX = Math.min(minX, x);
    minY = Math.min(minY, y);
    minZ = Math.min(minZ, z);
    maxX = Math.max(maxX, x);
    maxY = Math.max(maxY, y);
    maxZ = Math.max(maxZ, z);
  }
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

/** All the points as a flat array (x, y, z per point), ready for the 3D buffers. */
export function toDisplayPositions(
  transform: DisplayTransform,
  points: readonly Coordinates[],
): Float32Array {
  const positions = new Float32Array(points.length * 3);
  points.forEach((point, index) => {
    positions.set(applyTransform(transform, point), index * 3);
  });
  return positions;
}

/** Euclidean distance between two points in the projected (backend) coordinates. */
export function distance3d(a: Coordinates, b: Coordinates): number {
  return Math.hypot(a.x - b.x, a.y - b.y, a.z - b.z);
}
