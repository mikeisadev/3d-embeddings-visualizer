import type { Diagnostics } from '../api/schemas';

/**
 * Whether the RAG simulation is configured, read from the `embedding.client` check:
 * `null` while the diagnostics are not available.
 */
export function embeddingEnabled(diagnostics: Diagnostics | undefined): boolean | null {
  const check = diagnostics?.checks.find((candidate) => candidate.id === 'embedding.client');
  const enabled = check?.facts.enabled;
  return typeof enabled === 'boolean' ? enabled : null;
}
