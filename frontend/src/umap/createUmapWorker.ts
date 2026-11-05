/**
 * The real UMAP worker. Vite recognizes this exact `new Worker(new URL(...))` form and bundles
 * the worker and umap-js as a separate file, served from the same origin (CSP `worker-src 'self'`).
 * Tests replace this function: jsdom has no workers.
 */
export function createUmapWorker(): Worker {
  return new Worker(new URL('./umap.worker.ts', import.meta.url), { type: 'module' });
}
