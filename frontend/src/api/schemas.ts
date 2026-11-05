/**
 * Runtime validation of every backend response. A response that does not match becomes an
 * INVALID_RESPONSE error instead of crashing a component.
 */
import * as z from 'zod';

const count = z.number().int().nonnegative();

export const checkStatusSchema = z.enum(['OK', 'WARN', 'FAIL', 'SKIPPED']);

export const diagnosticCheckSchema = z.object({
  id: z.string(),
  status: checkStatusSchema,
  detail: z.string(),
  code: z.string().nullable(),
  facts: z.record(z.string(), z.unknown()),
});

export const diagnosticsSchema = z.object({
  status: z.enum(['OK', 'WARN', 'FAIL']),
  checks: z.array(diagnosticCheckSchema),
  checkedAt: z.string(),
});

export const metricSchema = z.enum(['COSINE', 'L2', 'INNER_PRODUCT']);

export const resolvedMetricSchema = z.object({
  metric: metricSchema,
  source: z.enum(['DETECTED', 'CONFIGURED', 'DEFAULT']),
});

export const vectorIndexSchema = z.object({
  name: z.string(),
  type: z.string(),
  operatorClass: z.string(),
});

export const metadataKeySchema = z.object({
  key: z.string(),
  presentIn: count,
  distinctValues: count,
  sampleValues: z.array(z.string()),
  valueType: z.enum(['STRING', 'NUMBER', 'BOOLEAN', 'NULL', 'OBJECT', 'ARRAY', 'MIXED']),
});

export const datasetSchema = z.object({
  datasetVersion: z.string(),
  schema: z.string(),
  table: z.string(),
  rowCount: count,
  rowsWithEmbedding: count,
  loadedRows: count,
  sampled: z.boolean(),
  maxPoints: count,
  dimensions: z.number().int().positive().nullable(),
  distanceMetric: resolvedMetricSchema,
  index: vectorIndexSchema.nullable(),
  files: z.array(z.object({ file: z.string(), chunks: count })),
  metadataKeys: z.array(metadataKeySchema),
  warnings: z.array(z.object({ code: z.string(), count: count })),
});

export const scalarSchema = z.union([z.string(), z.number(), z.boolean()]);

export const pointSchema = z.object({
  id: z.string(),
  x: z.number(),
  y: z.number(),
  z: z.number(),
  file: z.string(),
  page: z.number().int().nullable(),
  chunkIndex: z.number().int().nullable(),
  preview: z.string(),
  contentLength: count,
  metadata: z.record(z.string(), scalarSchema),
});

export const projectionSchema = z.object({
  method: z.string(),
  normalized: z.boolean(),
  dimensions: z.number().int().positive(),
  explainedVarianceRatio: z.array(z.number().min(0).max(1)).length(3),
  totalExplainedVariance: z.number().min(0).max(1),
  iterations: count,
  converged: z.boolean(),
});

export const pointsSchema = z.object({
  datasetVersion: z.string(),
  projection: projectionSchema,
  sampled: z.boolean(),
  rowsWithEmbedding: count,
  points: z.array(pointSchema),
});

export const vectorStatsSchema = z.object({
  dimensions: count,
  norm: z.number(),
  min: z.number(),
  max: z.number(),
  mean: z.number(),
});

export const chunkSchema = z.object({
  id: z.string(),
  file: z.string(),
  page: z.number().int().nullable(),
  chunkIndex: z.number().int().nullable(),
  content: z.string(),
  metadata: z.record(z.string(), z.unknown()),
  embedding: z.array(z.number()).nullable(),
  stats: vectorStatsSchema.nullable(),
});

export const neighborSchema = z.object({
  rank: z.number().int().positive(),
  id: z.string(),
  distance: z.number().nullable(),
  similarity: z.number().nullable(),
  file: z.string(),
  page: z.number().int().nullable(),
  chunkIndex: z.number().int().nullable(),
  preview: z.string(),
});

export const neighborsSchema = z.object({
  id: z.string(),
  k: z.number().int().positive(),
  metric: resolvedMetricSchema,
  exact: z.boolean(),
  sql: z.string(),
  neighbors: z.array(neighborSchema),
});

export const comparisonSchema = z.object({
  a: z.string(),
  b: z.string(),
  cosineSimilarity: z.number().nullable(),
  cosineDistance: z.number().nullable(),
  euclideanDistance: z.number().nullable(),
  dotProduct: z.number().nullable(),
  metric: resolvedMetricSchema,
  sql: z.string(),
});

export const chunkMatchesSchema = z.object({
  contains: z.string(),
  ids: z.array(z.string()),
  truncated: z.boolean(),
});

/** RFC 9457 problem document with the backend extensions; other members are kept. */
export const problemSchema = z.looseObject({
  status: z.number().optional(),
  title: z.string().optional(),
  detail: z.string().optional(),
  code: z.string(),
  requestId: z.string().optional(),
  timestamp: z.string().optional(),
});

export type CheckStatus = z.infer<typeof checkStatusSchema>;
export type DiagnosticCheck = z.infer<typeof diagnosticCheckSchema>;
export type Diagnostics = z.infer<typeof diagnosticsSchema>;
export type Metric = z.infer<typeof metricSchema>;
export type ResolvedMetric = z.infer<typeof resolvedMetricSchema>;
export type MetadataKeyStats = z.infer<typeof metadataKeySchema>;
export type Dataset = z.infer<typeof datasetSchema>;
export type Scalar = z.infer<typeof scalarSchema>;
export type Point = z.infer<typeof pointSchema>;
export type Projection = z.infer<typeof projectionSchema>;
export type PointCloud = z.infer<typeof pointsSchema>;
export type VectorStats = z.infer<typeof vectorStatsSchema>;
export type ChunkDetails = z.infer<typeof chunkSchema>;
export type Neighbor = z.infer<typeof neighborSchema>;
export type Neighbors = z.infer<typeof neighborsSchema>;
export type Comparison = z.infer<typeof comparisonSchema>;
export type ChunkMatches = z.infer<typeof chunkMatchesSchema>;
export type Problem = z.infer<typeof problemSchema>;
