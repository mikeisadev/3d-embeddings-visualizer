import { describe, expect, it } from 'vitest';
import {
  categoricalColor,
  diverging,
  hashColor,
  hexToRgb,
  MISSING_COLOR,
  OKABE_ITO,
  viridis,
} from '../palette';

describe('palette', () => {
  it('uses the Okabe–Ito colors first, then stable hash colors', () => {
    expect(categoricalColor(0, 'ferie.pdf')).toBe(OKABE_ITO[0]);
    expect(categoricalColor(6, 'x')).toBe(OKABE_ITO[6]);
    expect(categoricalColor(7, 'ottavo.pdf')).toBe(hashColor('ottavo.pdf'));
    expect(hashColor('ottavo.pdf')).toBe(hashColor('ottavo.pdf'));
    expect(hashColor('ottavo.pdf')).not.toBe(hashColor('nono.pdf'));
    expect(hashColor('qualunque')).toMatch(/^#[0-9a-f]{6}$/);
  });

  it('maps numbers to viridis from dark purple to yellow', () => {
    expect(viridis(0)).toBe('#440154');
    expect(viridis(1)).toBe('#fde725');
    expect(viridis(-5)).toBe('#440154');
    expect(viridis(0.5)).toBe('#26828e');
  });

  it('maps signed values to blue, white and red', () => {
    expect(diverging(-1)).toBe('#2166ac');
    expect(diverging(0)).toBe('#f7f7f7');
    expect(diverging(1)).toBe('#b2182b');
  });

  it('converts hex colors to sRGB components', () => {
    expect(hexToRgb('#ff8000')).toEqual([1, 128 / 255, 0]);
    expect(hexToRgb(MISSING_COLOR)).toHaveLength(3);
  });
});
