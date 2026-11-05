import * as z from 'zod';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockApi } from '../../test/apiMock';
import { problem } from '../../test/fixtures';
import { ApiError } from '../ApiError';
import { newRequestId, requestJson } from '../http';

const schema = z.object({ value: z.number() });

async function failure(promise: Promise<unknown>): Promise<ApiError> {
  try {
    await promise;
  } catch (error) {
    if (error instanceof ApiError) {
      return error;
    }
    throw error;
  }
  throw new Error('Expected the request to fail');
}

describe('requestJson', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('returns the validated body and sends a request id', async () => {
    const fetchMock = mockApi([['/api/v1/thing', { json: { value: 3 } }]]);

    await expect(requestJson('/api/v1/thing', schema)).resolves.toEqual({ value: 3 });
    const init = fetchMock.mock.calls[0]?.[1];
    expect((init?.headers as Record<string, string>)['X-Request-Id']).toMatch(/^web-[0-9a-f]{32}$/);
  });

  it('turns a problem document into an ApiError with its code and request id', async () => {
    mockApi([['/api/v1/thing', { json: problem('DB_UNAVAILABLE', 503, 'down'), status: 503 }]]);

    const error = await failure(requestJson('/api/v1/thing', schema));
    expect(error.code).toBe('DB_UNAVAILABLE');
    expect(error.status).toBe(503);
    expect(error.requestId).toBe('req-problem-1');
    expect(error.detail).toBe('down');
  });

  it('reports the HTML page of a proxy while the backend restarts as BACKEND_UNAVAILABLE', async () => {
    mockApi([['/api/v1/thing', { text: '<html>502 Bad Gateway</html>', status: 502 }]]);

    expect((await failure(requestJson('/api/v1/thing', schema))).code).toBe('BACKEND_UNAVAILABLE');
  });

  it('reports other unexpected statuses', async () => {
    mockApi([['/api/v1/thing', { text: 'nope', status: 418 }]]);

    expect((await failure(requestJson('/api/v1/thing', schema))).code).toBe('UNEXPECTED_STATUS');
  });

  it('reports a body that is not JSON or has the wrong shape as INVALID_RESPONSE', async () => {
    mockApi([
      ['/api/v1/text', { text: 'hello', status: 200 }],
      ['/api/v1/shape', { json: { value: 'three' } }],
    ]);

    expect((await failure(requestJson('/api/v1/text', schema))).code).toBe('INVALID_RESPONSE');
    const invalid = await failure(requestJson('/api/v1/shape', schema));
    expect(invalid.code).toBe('INVALID_RESPONSE');
    expect(invalid.detail).toContain('value');
  });

  it('reports network failures', async () => {
    mockApi([['/api/v1/thing', { networkError: true }]]);

    expect((await failure(requestJson('/api/v1/thing', schema))).code).toBe('NETWORK_ERROR');
  });

  it('reports a timeout', async () => {
    vi.useFakeTimers();
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_input: unknown, init?: RequestInit) =>
          new Promise((_, reject) => {
            init?.signal?.addEventListener('abort', () => {
              reject(new DOMException('aborted', 'AbortError'));
            });
          }),
      ),
    );

    const pending = failure(requestJson('/api/v1/slow', schema, { timeoutMs: 1000 }));
    await vi.advanceTimersByTimeAsync(1000);
    expect((await pending).code).toBe('TIMEOUT');
  });

  it('lets a cancellation by the caller through as an abort, not as an error code', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(
        (_input: unknown, init?: RequestInit) =>
          new Promise((_, reject) => {
            init?.signal?.addEventListener('abort', () => {
              reject(new DOMException('aborted', 'AbortError'));
            });
          }),
      ),
    );
    const controller = new AbortController();

    const pending = requestJson('/api/v1/slow', schema, { signal: controller.signal });
    controller.abort();
    await expect(pending).rejects.toHaveProperty('name', 'AbortError');
  });

  it('creates ids accepted by the backend', () => {
    expect(newRequestId()).toMatch(/^[A-Za-z0-9._:-]{8,64}$/);
    expect(newRequestId()).not.toBe(newRequestId());
  });
});
