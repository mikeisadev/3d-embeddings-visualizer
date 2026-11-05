import { describe, expect, it } from 'vitest';
import {
  chunk,
  comparison,
  dataset,
  diagnosticsDbDown,
  diagnosticsOk,
  neighbors,
  points,
  problem,
} from '../../test/fixtures';
import {
  chunkSchema,
  comparisonSchema,
  datasetSchema,
  diagnosticsSchema,
  neighborsSchema,
  pointsSchema,
  problemSchema,
} from '../schemas';

describe('schemas', () => {
  it('accept the responses of the backend', () => {
    expect(diagnosticsSchema.safeParse(diagnosticsOk).success).toBe(true);
    expect(diagnosticsSchema.safeParse(diagnosticsDbDown).success).toBe(true);
    expect(datasetSchema.safeParse(dataset).success).toBe(true);
    expect(pointsSchema.safeParse(points).success).toBe(true);
    expect(chunkSchema.safeParse(chunk).success).toBe(true);
    expect(neighborsSchema.safeParse(neighbors).success).toBe(true);
    expect(comparisonSchema.safeParse(comparison).success).toBe(true);
  });

  it('reject unknown statuses and metrics', () => {
    expect(diagnosticsSchema.safeParse({ ...diagnosticsOk, status: 'MAYBE' }).success).toBe(false);
    expect(
      datasetSchema.safeParse({
        ...dataset,
        distanceMetric: { metric: 'MANHATTAN', source: 'DETECTED' },
      }).success,
    ).toBe(false);
  });

  it('reject missing fields and wrong types', () => {
    const { datasetVersion: _removed, ...withoutVersion } = points;
    expect(pointsSchema.safeParse(withoutVersion).success).toBe(false);
    expect(pointsSchema.safeParse({ ...points, points: [{ id: 1 }] }).success).toBe(false);
    expect(
      pointsSchema.safeParse({
        ...points,
        projection: { ...points.projection, explainedVarianceRatio: [0.5, 0.2] },
      }).success,
    ).toBe(false);
  });

  it('accept nullable values the backend sends for undefined numbers', () => {
    expect(comparisonSchema.safeParse({ ...comparison, cosineSimilarity: null }).success).toBe(
      true,
    );
    expect(chunkSchema.safeParse({ ...chunk, embedding: null, stats: null }).success).toBe(true);
  });

  it('keep the extra members of a problem document', () => {
    const parsed = problemSchema.parse({
      ...problem('DIMENSION_MISMATCH', 409),
      dimensionsFound: [384, 1536],
    });
    expect(parsed.code).toBe('DIMENSION_MISMATCH');
    expect(parsed).toHaveProperty('dimensionsFound', [384, 1536]);
  });
});
