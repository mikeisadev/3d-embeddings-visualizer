import { describe, expect, it } from 'vitest';
import { barcodeColor, barcodeGrid, symmetricRange } from '../barcode';
import { comparableDistance, shownShare } from '../compare';
import {
  applyTransform,
  distance3d,
  fitToUnitBox,
  IDENTITY,
  toDisplayPositions,
} from '../displayTransform';
import { passesFilters } from '../filters';
import { comparison, points } from '../../test/fixtures';

describe('displayTransform', () => {
  it('centers the points and scales the largest extent to 1', () => {
    const transform = fitToUnitBox([
      { x: 10, y: 0, z: 0 },
      { x: 14, y: 2, z: 1 },
    ]);
    expect(transform.center).toEqual([12, 1, 0.5]);
    expect(transform.scale).toBeCloseTo(0.25);
    expect(applyTransform(transform, { x: 14, y: 2, z: 1 })).toEqual([0.5, 0.25, 0.125]);
  });

  it('uses one scale for all axes and survives degenerate input', () => {
    expect(fitToUnitBox([])).toBe(IDENTITY);
    expect(fitToUnitBox([{ x: 3, y: 3, z: 3 }]).scale).toBe(1);
    const positions = toDisplayPositions(IDENTITY, [{ x: 1, y: 2, z: 3 }]);
    expect(Array.from(positions)).toEqual([1, 2, 3]);
  });

  it('measures distances in the projected coordinates', () => {
    expect(distance3d({ x: 0, y: 0, z: 0 }, { x: 1, y: 2, z: 2 })).toBe(3);
  });
});

describe('filters', () => {
  it('keeps every point without filters', () => {
    expect(passesFilters(points.points, { files: null, pages: { from: null, to: null } })).toEqual([
      true,
      true,
      true,
      true,
    ]);
  });

  it('filters by file and by page range', () => {
    expect(
      passesFilters(points.points, { files: ['ferie.pdf'], pages: { from: null, to: null } }),
    ).toEqual([true, true, false, false]);
    expect(passesFilters(points.points, { files: null, pages: { from: 2, to: null } })).toEqual([
      false,
      true,
      false,
      true,
    ]);
  });
});

describe('barcode', () => {
  it('chooses a near-square exact grid', () => {
    expect(barcodeGrid(1536)).toEqual({ columns: 48, rows: 32 });
    expect(barcodeGrid(1024)).toEqual({ columns: 32, rows: 32 });
    expect(barcodeGrid(384)).toEqual({ columns: 24, rows: 16 });
    expect(barcodeGrid(3072)).toEqual({ columns: 64, rows: 48 });
  });

  it('falls back to a square grid for awkward dimensions', () => {
    const grid = barcodeGrid(1531);
    expect(grid.columns * grid.rows).toBeGreaterThanOrEqual(1531);
    expect(grid.columns / grid.rows).toBeLessThan(1.1);
    expect(barcodeGrid(0)).toEqual({ columns: 0, rows: 0 });
  });

  it('uses a symmetric scale so white means zero', () => {
    expect(symmetricRange([0.1, -0.3, 0.2])).toBe(0.3);
    expect(barcodeColor(0, 0.3)).toBe('#f7f7f7');
    expect(barcodeColor(-0.3, 0.3)).toBe('#2166ac');
  });
});

describe('compare', () => {
  it('compares the 3D distance with the distance between unit vectors when normalized', () => {
    expect(comparableDistance(comparison, true)).toBeCloseTo(1);
    expect(comparableDistance({ ...comparison, euclideanDistance: 2 }, false)).toBe(2);
    expect(comparableDistance({ ...comparison, cosineSimilarity: null }, true)).toBeNull();
  });

  it('never shows more than the whole real distance', () => {
    expect(shownShare(0.25, 1)).toBe(0.25);
    expect(shownShare(1.0000001, 1)).toBe(1);
    expect(shownShare(0.5, 0)).toBeNull();
    expect(shownShare(0.5, null)).toBeNull();
  });
});
