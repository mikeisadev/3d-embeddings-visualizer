import { diverging } from './palette';

export interface BarcodeGrid {
  readonly columns: number;
  readonly rows: number;
}

/** Largest acceptable width/height ratio for an exact factorization. */
const MAX_RATIO = 2;

/**
 * A near-square grid with one cell per vector value: the most square exact factorization
 * (1536 → 48 × 32, 1024 → 32 × 32, 384 → 24 × 16); when the dimension factors badly (a
 * prime, for example), a square-ish grid with a few empty cells.
 */
export function barcodeGrid(dimensions: number): BarcodeGrid {
  if (dimensions <= 0) {
    return { columns: 0, rows: 0 };
  }
  let best: BarcodeGrid | null = null;
  for (let rows = Math.floor(Math.sqrt(dimensions)); rows >= 1; rows--) {
    if (dimensions % rows === 0) {
      best = { columns: dimensions / rows, rows };
      break;
    }
  }
  if (best !== null && best.columns / best.rows <= MAX_RATIO) {
    return best;
  }
  const columns = Math.ceil(Math.sqrt(dimensions));
  return { columns, rows: Math.ceil(dimensions / columns) };
}

/** Largest absolute value: the scale is symmetric so that white always means zero. */
export function symmetricRange(values: readonly number[]): number {
  let largest = 0;
  for (const value of values) {
    largest = Math.max(largest, Math.abs(value));
  }
  return largest;
}

export function barcodeColor(value: number, range: number): string {
  return diverging(range > 0 ? value / range : 0);
}
