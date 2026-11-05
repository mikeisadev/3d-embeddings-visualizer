/**
 * Error codes produced by the client itself, when there is no problem document from the
 * backend. UMAP_FAILED comes from the UMAP worker: it is shown like every other error.
 */
export type ClientErrorCode =
  | 'NETWORK_ERROR'
  | 'TIMEOUT'
  | 'BACKEND_UNAVAILABLE'
  | 'INVALID_RESPONSE'
  | 'UNEXPECTED_STATUS'
  | 'UMAP_FAILED';

interface ApiErrorInit {
  readonly code: string;
  readonly status: number | null;
  readonly requestId: string | null;
  readonly detail?: string | null;
  readonly properties?: Readonly<Record<string, unknown>>;
}

/**
 * The only error type the API client throws: network failures, timeouts, non-JSON bodies,
 * invalid shapes and backend problem documents all become an ApiError with a stable code.
 */
export class ApiError extends Error {
  readonly code: string;
  readonly status: number | null;
  readonly requestId: string | null;
  readonly detail: string | null;
  readonly properties: Readonly<Record<string, unknown>>;

  constructor(init: ApiErrorInit) {
    super(init.detail ? `${init.code}: ${init.detail}` : init.code);
    this.name = 'ApiError';
    this.code = init.code;
    this.status = init.status;
    this.requestId = init.requestId;
    this.detail = init.detail ?? null;
    this.properties = init.properties ?? {};
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}

/** Codes caused by the environment (database, backend, network): the diagnostics explain them. */
const ENVIRONMENT_CODES = new Set([
  'DB_UNAVAILABLE',
  'DB_AUTH_FAILED',
  'DB_PERMISSION_DENIED',
  'DB_NOT_FOUND',
  'DB_TIMEOUT',
  'PGVECTOR_MISSING',
  'TABLE_NOT_FOUND',
  'TABLE_SCHEMA_INVALID',
  'BACKEND_UNAVAILABLE',
  'NETWORK_ERROR',
]);

export function isEnvironmentError(error: unknown): boolean {
  return isApiError(error) && ENVIRONMENT_CODES.has(error.code);
}
