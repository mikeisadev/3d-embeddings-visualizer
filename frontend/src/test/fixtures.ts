import type {
  ChunkDetails,
  SearchResult,
  Comparison,
  Dataset,
  Diagnostics,
  Neighbors,
  Point,
  PointCloud,
} from '../api/schemas';

/** Realistic API responses, shaped exactly like the backend JSON. */

export const diagnosticsOk: Diagnostics = {
  status: 'OK',
  checkedAt: '2026-11-05T08:00:00Z',
  checks: [
    {
      id: 'db.connection',
      status: 'OK',
      detail: 'Connected to PostgreSQL 17.11.',
      code: null,
      facts: { url: 'jdbc:postgresql://host.docker.internal:5432/chatbot', user: 'chatbot' },
    },
    {
      id: 'db.pgvector',
      status: 'OK',
      detail: 'pgvector 0.8.7 is installed.',
      code: null,
      facts: {},
    },
    {
      id: 'embedding.client',
      status: 'SKIPPED',
      detail: 'The RAG simulation is disabled (VIZ_EMBEDDING_ENABLED=false).',
      code: 'EMBEDDING_DISABLED',
      facts: { enabled: false },
    },
  ],
};

export const diagnosticsDbDown: Diagnostics = {
  status: 'FAIL',
  checkedAt: '2026-11-05T08:00:00Z',
  checks: [
    {
      id: 'db.connection',
      status: 'FAIL',
      detail: 'The vector database is not reachable. (SQLSTATE 08001)',
      code: 'DB_UNAVAILABLE',
      facts: {},
    },
    {
      id: 'db.pgvector',
      status: 'SKIPPED',
      detail: 'Skipped because db.connection did not pass.',
      code: null,
      facts: {},
    },
  ],
};

export const dataset: Dataset = {
  datasetVersion: '4-abc',
  schema: 'public',
  table: 'vector_store',
  rowCount: 4,
  rowsWithEmbedding: 4,
  loadedRows: 4,
  sampled: false,
  maxPoints: 5000,
  dimensions: 6,
  distanceMetric: { metric: 'COSINE', source: 'DETECTED' },
  index: { name: 'spring_ai_vector_index', type: 'hnsw', operatorClass: 'vector_cosine_ops' },
  files: [
    { file: 'ferie.pdf', chunks: 2 },
    { file: 'sicurezza.pdf', chunks: 2 },
  ],
  metadataKeys: [
    {
      key: 'department',
      presentIn: 4,
      distinctValues: 2,
      sampleValues: ['HR', 'OPS'],
      valueType: 'STRING',
    },
    {
      key: 'page_number',
      presentIn: 4,
      distinctValues: 2,
      sampleValues: ['1', '2'],
      valueType: 'NUMBER',
    },
    { key: 'nested', presentIn: 1, distinctValues: 1, sampleValues: ['{}'], valueType: 'OBJECT' },
  ],
  warnings: [],
};

function point(id: string, file: string, page: number, x: number, department: string): Point {
  return {
    id,
    x,
    y: x / 2,
    z: -x,
    file,
    page,
    chunkIndex: page - 1,
    preview: `Testo del chunk ${id} su ${file}`,
    contentLength: 120,
    metadata: { department, page_number: page },
  };
}

export const points: PointCloud = {
  datasetVersion: '4-abc',
  projection: {
    method: 'pca',
    normalized: true,
    dimensions: 6,
    explainedVarianceRatio: [0.15, 0.1, 0.05],
    totalExplainedVariance: 0.3,
    iterations: 7,
    converged: true,
  },
  sampled: false,
  rowsWithEmbedding: 4,
  points: [
    point('a', 'ferie.pdf', 1, 0.1, 'HR'),
    point('b', 'ferie.pdf', 2, 0.2, 'HR'),
    point('c', 'sicurezza.pdf', 1, -0.3, 'OPS'),
    point('d', 'sicurezza.pdf', 2, -0.4, 'OPS'),
  ],
};

export const chunk: ChunkDetails = {
  id: 'a',
  file: 'ferie.pdf',
  page: 1,
  chunkIndex: 0,
  content: 'Le ferie si richiedono con due settimane di anticipo. <script>alert(1)</script>',
  metadata: { original_filename: 'ferie.pdf', page_number: 1, department: 'HR' },
  embedding: [0.5, -0.5, 0.5, -0.5, 0, 0],
  stats: { dimensions: 6, norm: 1, min: -0.5, max: 0.5, mean: 0 },
};

export const neighbors: Neighbors = {
  id: 'a',
  k: 10,
  metric: { metric: 'COSINE', source: 'DETECTED' },
  exact: true,
  sql: 'SELECT … ORDER BY embedding <=> CAST(? AS vector) LIMIT ?',
  neighbors: [
    {
      rank: 1,
      id: 'b',
      distance: 0.2,
      similarity: 0.8,
      file: 'ferie.pdf',
      page: 2,
      chunkIndex: 1,
      preview: 'Vicino b',
    },
    {
      rank: 2,
      id: 'zz',
      distance: 0.6,
      similarity: 0.4,
      file: 'altro.pdf',
      page: null,
      chunkIndex: null,
      preview: 'Vicino fuori campione',
    },
  ],
};

export const comparison: Comparison = {
  a: 'a',
  b: 'c',
  cosineSimilarity: 0.5,
  cosineDistance: 0.5,
  euclideanDistance: 1,
  dotProduct: 0.5,
  metric: { metric: 'COSINE', source: 'DETECTED' },
  sql: 'SELECT …',
};

export function problem(code: string, status: number, detail = 'detail') {
  return {
    type: 'about:blank',
    title: 'Error',
    status,
    detail,
    code,
    requestId: 'req-problem-1',
    timestamp: '2026-11-05T08:00:00Z',
  };
}

export const diagnosticsRagEnabled: Diagnostics = {
  ...diagnosticsOk,
  checks: diagnosticsOk.checks.map((check) =>
    check.id === 'embedding.client'
      ? {
          ...check,
          status: 'OK' as const,
          code: null,
          detail: 'Model text-embedding-3-small at https://api.openai.com/v1.',
          facts: { enabled: true, apiKeyPresent: true },
        }
      : check,
  ),
};

export const searchResult: SearchResult = {
  datasetVersion: '4-abc',
  model: 'text-embedding-3-small',
  embeddingLatencyMs: 230,
  dimensions: 6,
  metric: { metric: 'COSINE', source: 'DETECTED' },
  topK: 3,
  similarityThreshold: 0.5,
  distanceThreshold: 0.5,
  normalized: true,
  queryPoint: { x: 0.15, y: 0.07, z: -0.15 },
  results: [
    {
      rank: 1,
      id: 'a',
      distance: 0.2,
      similarity: 0.8,
      passesThreshold: true,
      file: 'ferie.pdf',
      page: 1,
      chunkIndex: 0,
      preview: 'Le ferie si richiedono',
    },
    {
      rank: 2,
      id: 'b',
      distance: 0.4,
      similarity: 0.6,
      passesThreshold: true,
      file: 'ferie.pdf',
      page: 2,
      chunkIndex: 1,
      preview: 'Permessi brevi',
    },
    {
      rank: 3,
      id: 'c',
      distance: 0.7,
      similarity: 0.3,
      passesThreshold: false,
      file: 'sicurezza.pdf',
      page: 1,
      chunkIndex: 0,
      preview: 'Uscite di emergenza',
    },
  ],
  springAiSql:
    'SELECT *, embedding <=> ? AS distance FROM public.vector_store WHERE embedding <=> ? < ? ORDER BY distance LIMIT ?',
};
