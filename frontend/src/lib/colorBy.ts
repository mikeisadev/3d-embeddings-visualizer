import type { MetadataKeyStats, Point, Scalar } from '../api/schemas';
import { it } from '../i18n/it';
import { categoricalColor, hexToRgb, MISSING_COLOR, viridis } from './palette';

/** What the points are colored by: a derived field or any metadata key. */
export type ColorKey =
  'field:file' | 'field:page' | 'field:chunkIndex' | 'field:contentLength' | `meta:${string}`;

export const DEFAULT_COLOR_KEY: ColorKey = 'field:file';

export interface ColorOption {
  readonly key: ColorKey;
  readonly label: string;
  readonly group: 'field' | 'metadata';
}

export interface LegendCategory {
  readonly id: string;
  readonly label: string;
  readonly color: string;
  readonly count: number;
}

export type Legend =
  | { readonly kind: 'categorical'; readonly categories: readonly LegendCategory[] }
  | {
      readonly kind: 'sequential';
      readonly min: number;
      readonly max: number;
      readonly missing: number;
    };

export interface ColorMapping {
  readonly legend: Legend;
  /** Color of each point, sRGB components in [0, 1], 3 values per point. */
  readonly colors: Float32Array;
  /** Category id of each point; `null` for a sequential scale. */
  readonly categoryOf: readonly (string | null)[];
}

/** Id of the "no value" category; cannot clash with a real value. */
export const MISSING_CATEGORY = '\u0000missing';

/** The "Colora per" options: the derived fields, then every metadata key with scalar values. */
export function colorOptions(metadataKeys: readonly MetadataKeyStats[]): ColorOption[] {
  const fields: ColorOption[] = [
    { key: 'field:file', label: it.colors.options.file, group: 'field' },
    { key: 'field:page', label: it.colors.options.page, group: 'field' },
    { key: 'field:chunkIndex', label: it.colors.options.chunkIndex, group: 'field' },
    { key: 'field:contentLength', label: it.colors.options.contentLength, group: 'field' },
  ];
  const metadata = metadataKeys
    .filter((stats) => stats.valueType !== 'OBJECT' && stats.valueType !== 'ARRAY')
    .map((stats): ColorOption => ({
      key: `meta:${stats.key}`,
      label: stats.key,
      group: 'metadata',
    }));
  return [...fields, ...metadata];
}

export function valueOf(point: Point, key: ColorKey): Scalar | null {
  switch (key) {
    case 'field:file':
      return point.file;
    case 'field:page':
      return point.page;
    case 'field:chunkIndex':
      return point.chunkIndex;
    case 'field:contentLength':
      return point.contentLength;
    default:
      return point.metadata[key.slice('meta:'.length)] ?? null;
  }
}

/**
 * Colors the points by {@code key}: a sequential (viridis) scale when every value is a
 * number, otherwise categories in alphabetical order (stable when a new file arrives) with
 * the Okabe–Ito palette. Missing values are gray.
 */
export function buildColorMapping(points: readonly Point[], key: ColorKey): ColorMapping {
  const values = points.map((point) => valueOf(point, key));
  const present = values.filter((value): value is Scalar => value !== null);
  const numeric = present.length > 0 && present.every((value) => typeof value === 'number');
  return numeric
    ? sequentialMapping(values as readonly (number | null)[])
    : categoricalMapping(values);
}

function sequentialMapping(values: readonly (number | null)[]): ColorMapping {
  let min = Number.POSITIVE_INFINITY;
  let max = Number.NEGATIVE_INFINITY;
  let missing = 0;
  for (const value of values) {
    if (value === null) {
      missing++;
    } else {
      min = Math.min(min, value);
      max = Math.max(max, value);
    }
  }
  const range = max - min;
  const colors = new Float32Array(values.length * 3);
  values.forEach((value, index) => {
    const hex = value === null ? MISSING_COLOR : viridis(range > 0 ? (value - min) / range : 0.5);
    colors.set(hexToRgb(hex), index * 3);
  });
  return {
    legend: { kind: 'sequential', min, max, missing },
    colors,
    categoryOf: values.map(() => null),
  };
}

function categoricalMapping(values: readonly (Scalar | null)[]): ColorMapping {
  const categoryOf = values.map((value) => (value === null ? MISSING_CATEGORY : String(value)));
  const counts = new Map<string, number>();
  for (const category of categoryOf) {
    counts.set(category, (counts.get(category) ?? 0) + 1);
  }
  const ordered = [...counts.keys()]
    .filter((category) => category !== MISSING_CATEGORY)
    .sort((a, b) => a.localeCompare(b, 'it', { numeric: true }));
  const categories: LegendCategory[] = ordered.map((category, index) => ({
    id: category,
    label: category,
    color: categoricalColor(index, category),
    count: counts.get(category) ?? 0,
  }));
  const missingCount = counts.get(MISSING_CATEGORY);
  if (missingCount !== undefined) {
    categories.push({
      id: MISSING_CATEGORY,
      label: it.colors.missingValue,
      color: MISSING_COLOR,
      count: missingCount,
    });
  }
  const colorById = new Map(categories.map((category) => [category.id, hexToRgb(category.color)]));
  const colors = new Float32Array(values.length * 3);
  categoryOf.forEach((category, index) => {
    colors.set(colorById.get(category) ?? hexToRgb(MISSING_COLOR), index * 3);
  });
  return { legend: { kind: 'categorical', categories }, colors, categoryOf };
}
