import type * as z from 'zod';
import { ApiError } from './ApiError';
import { problemSchema } from './schemas';

const DEFAULT_TIMEOUT_MS = 20_000;
const GATEWAY_STATUSES = new Set([502, 503, 504]);
/** Passed to abort() when the timeout fires, to tell a timeout from a cancellation. */
const TIMEOUT_REASON = new Error('The request timed out');

export interface RequestOptions {
  readonly method?: 'GET' | 'POST';
  readonly body?: unknown;
  readonly timeoutMs?: number;
  /** Cancellation from the caller (TanStack Query passes one): cancelling is not an error. */
  readonly signal?: AbortSignal | undefined;
}

/**
 * Calls the backend and validates the JSON response with {@code schema}. Every failure becomes
 * an {@link ApiError}: problem documents keep the backend code; network errors, timeouts,
 * non-JSON bodies (for example the nginx page while the backend restarts) and unexpected
 * shapes get a client code.
 */
export async function requestJson<T>(
  path: string,
  schema: z.ZodType<T>,
  options: RequestOptions = {},
): Promise<T> {
  const requestId = newRequestId();
  const controller = new AbortController();
  const timer = setTimeout(() => {
    controller.abort(TIMEOUT_REASON);
  }, options.timeoutMs ?? DEFAULT_TIMEOUT_MS);
  const cancel = () => {
    controller.abort();
  };
  options.signal?.addEventListener('abort', cancel, { once: true });

  try {
    const response = await fetch(path, {
      method: options.method ?? 'GET',
      headers: requestHeaders(requestId, options.body !== undefined),
      body: options.body === undefined ? null : JSON.stringify(options.body),
      signal: controller.signal,
    });
    const text = await response.text();
    return parseResponse(response, text, schema, requestId);
  } catch (error) {
    if (error instanceof ApiError) {
      throw error;
    }
    if (controller.signal.reason === TIMEOUT_REASON) {
      throw new ApiError({ code: 'TIMEOUT', status: null, requestId });
    }
    if (options.signal?.aborted) {
      throw error;
    }
    throw new ApiError({ code: 'NETWORK_ERROR', status: null, requestId });
  } finally {
    clearTimeout(timer);
    options.signal?.removeEventListener('abort', cancel);
  }
}

function requestHeaders(requestId: string, hasBody: boolean): Record<string, string> {
  const headers: Record<string, string> = {
    Accept: 'application/json, application/problem+json',
    'X-Request-Id': requestId,
  };
  if (hasBody) {
    headers['Content-Type'] = 'application/json';
  }
  return headers;
}

function parseResponse<T>(
  response: Response,
  text: string,
  schema: z.ZodType<T>,
  sentRequestId: string,
): T {
  const requestId = response.headers.get('X-Request-Id') ?? sentRequestId;
  const body = parseJson(text);

  if (!response.ok) {
    const problem = body.ok ? problemSchema.safeParse(body.value) : null;
    if (problem?.success) {
      throw new ApiError({
        code: problem.data.code,
        status: response.status,
        requestId: problem.data.requestId ?? requestId,
        detail: problem.data.detail ?? null,
        properties: problem.data,
      });
    }
    const code = GATEWAY_STATUSES.has(response.status)
      ? 'BACKEND_UNAVAILABLE'
      : 'UNEXPECTED_STATUS';
    throw new ApiError({ code, status: response.status, requestId });
  }

  if (!body.ok) {
    throw new ApiError({
      code: 'INVALID_RESPONSE',
      status: response.status,
      requestId,
      detail: 'The response is not JSON.',
    });
  }
  const parsed = schema.safeParse(body.value);
  if (!parsed.success) {
    const issue = parsed.error.issues[0];
    const where = issue
      ? `${issue.path.join('.') || '(root)'}: ${issue.message}`
      : 'shape mismatch';
    throw new ApiError({
      code: 'INVALID_RESPONSE',
      status: response.status,
      requestId,
      detail: where,
    });
  }
  return parsed.data;
}

type JsonResult = { readonly ok: true; readonly value: unknown } | { readonly ok: false };

function parseJson(text: string): JsonResult {
  if (text.trim() === '') {
    return { ok: false };
  }
  try {
    return { ok: true, value: JSON.parse(text) as unknown };
  } catch {
    return { ok: false };
  }
}

/**
 * A random id sent as X-Request-Id, so an error can be found in the backend logs. Uses
 * getRandomValues, which (unlike randomUUID) also works on plain http, for example when the
 * tool is shared on the classroom network.
 */
export function newRequestId(): string {
  const bytes = new Uint8Array(16);
  crypto.getRandomValues(bytes);
  return 'web-' + Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
}
