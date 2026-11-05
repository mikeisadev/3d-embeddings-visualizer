/**
 * Color scales: a colorblind-safe categorical palette (Okabe–Ito, without black because the
 * 3D view is dark), a deterministic hash color for extra categories, the perceptual viridis
 * scale for numbers and a red–blue diverging scale for signed values.
 */

export type Rgb = readonly [number, number, number];

export const OKABE_ITO = [
  '#e69f00',
  '#56b4e9',
  '#009e73',
  '#f0e442',
  '#0072b2',
  '#d55e00',
  '#cc79a7',
] as const;

export const MISSING_COLOR = '#8a949e';

/** Viridis sampled at 9 evenly spaced points. */
const VIRIDIS = [
  '#440154',
  '#482878',
  '#3e4989',
  '#31688e',
  '#26828e',
  '#1f9e89',
  '#35b779',
  '#6ece58',
  '#fde725',
] as const;

/** ColorBrewer RdBu, from negative (blue) through white to positive (red). */
const DIVERGING = [
  '#2166ac',
  '#4393c3',
  '#92c5de',
  '#f7f7f7',
  '#f4a582',
  '#d6604d',
  '#b2182b',
] as const;

/** Color of the n-th category: Okabe–Ito first, then a stable hash of the label. */
export function categoricalColor(index: number, label: string): string {
  return OKABE_ITO[index] ?? hashColor(label);
}

/** A stable, well-saturated color for any label (FNV-1a hash → hue). */
export function hashColor(label: string): string {
  let hash = 0x811c9dc5;
  for (let i = 0; i < label.length; i++) {
    hash ^= label.charCodeAt(i);
    hash = Math.imul(hash, 0x01000193);
  }
  const hue = (hash >>> 0) % 360;
  return hslToHex(hue, 0.6, 0.58);
}

/** Viridis color for t in [0, 1]. */
export function viridis(t: number): string {
  return interpolate(VIRIDIS, clamp(t, 0, 1));
}

/** Diverging color for t in [-1, 1]: blue below zero, white at zero, red above. */
export function diverging(t: number): string {
  return interpolate(DIVERGING, (clamp(t, -1, 1) + 1) / 2);
}

export function hexToRgb(hex: string): Rgb {
  const value = Number.parseInt(hex.slice(1), 16);
  return [((value >> 16) & 0xff) / 255, ((value >> 8) & 0xff) / 255, (value & 0xff) / 255];
}

function rgbToHex([r, g, b]: Rgb): string {
  const channel = (value: number) =>
    Math.round(clamp(value, 0, 1) * 255)
      .toString(16)
      .padStart(2, '0');
  return `#${channel(r)}${channel(g)}${channel(b)}`;
}

function interpolate(stops: readonly string[], t: number): string {
  const position = t * (stops.length - 1);
  const lower = Math.floor(position);
  const upper = Math.min(lower + 1, stops.length - 1);
  const weight = position - lower;
  const from = hexToRgb(stops[lower] ?? stops[0] ?? '#000000');
  const to = hexToRgb(stops[upper] ?? stops[0] ?? '#000000');
  return rgbToHex([
    from[0] + (to[0] - from[0]) * weight,
    from[1] + (to[1] - from[1]) * weight,
    from[2] + (to[2] - from[2]) * weight,
  ]);
}

function hslToHex(hue: number, saturation: number, lightness: number): string {
  const chroma = (1 - Math.abs(2 * lightness - 1)) * saturation;
  const segment = hue / 60;
  const x = chroma * (1 - Math.abs((segment % 2) - 1));
  const [r, g, b]: Rgb =
    segment < 1
      ? [chroma, x, 0]
      : segment < 2
        ? [x, chroma, 0]
        : segment < 3
          ? [0, chroma, x]
          : segment < 4
            ? [0, x, chroma]
            : segment < 5
              ? [x, 0, chroma]
              : [chroma, 0, x];
  const m = lightness - chroma / 2;
  return rgbToHex([r + m, g + m, b + m]);
}

function clamp(value: number, min: number, max: number): number {
  return Math.min(max, Math.max(min, value));
}
