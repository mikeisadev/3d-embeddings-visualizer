import { isApiError } from './ApiError';

/**
 * Automatic retries, only for idempotent GET queries and only for transient failures (the
 * request may not have reached the backend). Mutations such as the RAG search never retry:
 * they may cost money. Database problems are not retried either: the diagnostics poll every
 * 10 seconds and reload the data when the database comes back.
 */
const RETRYABLE_CODES = new Set(['NETWORK_ERROR', 'TIMEOUT', 'BACKEND_UNAVAILABLE']);

export const MAX_RETRIES = 2;

export function shouldRetry(failureCount: number, error: unknown): boolean {
  return failureCount < MAX_RETRIES && isApiError(error) && RETRYABLE_CODES.has(error.code);
}

/** Exponential backoff: 1 s, 2 s, 4 s… capped at 8 s. */
export function retryDelay(attempt: number): number {
  return Math.min(1000 * 2 ** attempt, 8000);
}
