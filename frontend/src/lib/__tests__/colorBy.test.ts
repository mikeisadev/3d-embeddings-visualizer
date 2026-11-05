import { describe, expect, it } from 'vitest';
import { dataset, points } from '../../test/fixtures';
import { buildColorMapping, colorOptions, MISSING_CATEGORY, valueOf } from '../colorBy';
import { hexToRgb, MISSING_COLOR, OKABE_ITO, viridis } from '../palette';

describe('colorBy', () => {
  it('offers the derived fields and the scalar metadata keys', () => {
    const keys = colorOptions(dataset.metadataKeys).map((option) => option.key);
    expect(keys).toEqual([
      'field:file',
      'field:page',
      'field:chunkIndex',
      'field:contentLength',
      'meta:department',
      'meta:page_number',
    ]);
  });

  it('reads fields and metadata values', () => {
    const point = points.points[0];
    expect(point).toBeDefined();
    if (point === undefined) return;
    expect(valueOf(point, 'field:file')).toBe('ferie.pdf');
    expect(valueOf(point, 'field:page')).toBe(1);
    expect(valueOf(point, 'meta:department')).toBe('HR');
    expect(valueOf(point, 'meta:missing')).toBeNull();
  });

  it('builds alphabetical categories with counts for strings', () => {
    const mapping = buildColorMapping(points.points, 'field:file');
    expect(mapping.legend).toEqual({
      kind: 'categorical',
      categories: [
        { id: 'ferie.pdf', label: 'ferie.pdf', color: OKABE_ITO[0], count: 2 },
        { id: 'sicurezza.pdf', label: 'sicurezza.pdf', color: OKABE_ITO[1], count: 2 },
      ],
    });
    expect(Array.from(mapping.colors.slice(0, 3))).toEqual(
      hexToRgb(OKABE_ITO[0]).map((value) => Math.fround(value)),
    );
    expect(mapping.categoryOf).toEqual([
      'ferie.pdf',
      'ferie.pdf',
      'sicurezza.pdf',
      'sicurezza.pdf',
    ]);
  });

  it('puts missing values in a gray category at the end', () => {
    const mapping = buildColorMapping(points.points, 'meta:missing');
    expect(mapping.legend.kind).toBe('categorical');
    if (mapping.legend.kind !== 'categorical') return;
    expect(mapping.legend.categories).toEqual([
      { id: MISSING_CATEGORY, label: '(nessun valore)', color: MISSING_COLOR, count: 4 },
    ]);
  });

  it('uses a sequential scale when every value is a number', () => {
    const mapping = buildColorMapping(points.points, 'field:page');
    expect(mapping.legend).toEqual({ kind: 'sequential', min: 1, max: 2, missing: 0 });
    expect(Array.from(mapping.colors.slice(0, 3))).toEqual(
      hexToRgb(viridis(0)).map((value) => Math.fround(value)),
    );
    expect(mapping.categoryOf).toEqual([null, null, null, null]);
  });
});
