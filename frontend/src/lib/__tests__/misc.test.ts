import { afterEach, describe, expect, it, vi } from 'vitest';
import { ApiError, isEnvironmentError } from '../../api/ApiError';
import { retryDelay, shouldRetry } from '../../api/retry';
import { toAsyncState } from '../asyncState';
import { describeCode, describeError } from '../errorMessages';
import { formatNumber, formatPercent, formatValue } from '../format';
import { GUIDE_SEEN_KEY, readFlag, writeFlag } from '../storage';

const error = (code: string) => new ApiError({ code, status: 503, requestId: 'req-1' });

describe('errorMessages', () => {
  it('describes known codes in Italian with a hint', () => {
    const described = describeError(error('DB_UNAVAILABLE'));
    expect(described.message).toBe('Il database vettoriale non risponde.');
    expect(described.hint).toContain('docker compose up -d');
    expect(described.requestId).toBe('req-1');
  });

  it('falls back to a generic message that shows the code', () => {
    const described = describeError(error('SOMETHING_NEW'));
    expect(described.message).toContain('SOMETHING_NEW');
    expect(described.code).toBe('SOMETHING_NEW');
    expect(describeError(new Error('boom')).code).toBe('CLIENT_ERROR');
  });

  it('describes warning codes too', () => {
    expect(describeCode('TABLE_EMPTY').message).toBe('La tabella è vuota.');
    expect(describeCode('PGVECTOR_MISSING').hint).toContain('pgvector/pgvector:pg17');
  });
});

describe('retry policy', () => {
  it('retries transient failures of GET queries only twice', () => {
    expect(shouldRetry(0, error('NETWORK_ERROR'))).toBe(true);
    expect(shouldRetry(1, error('BACKEND_UNAVAILABLE'))).toBe(true);
    expect(shouldRetry(2, error('TIMEOUT'))).toBe(false);
    expect(shouldRetry(0, error('DB_UNAVAILABLE'))).toBe(false);
    expect(shouldRetry(0, error('VALIDATION_FAILED'))).toBe(false);
    expect(shouldRetry(0, new Error('bug'))).toBe(false);
  });

  it('backs off exponentially', () => {
    expect([0, 1, 2, 5].map(retryDelay)).toEqual([1000, 2000, 4000, 8000]);
  });

  it('knows which errors the diagnostics explain', () => {
    expect(isEnvironmentError(error('DB_UNAVAILABLE'))).toBe(true);
    expect(isEnvironmentError(error('CHUNK_NOT_FOUND'))).toBe(false);
  });
});

describe('asyncState', () => {
  it('maps a query to loading, error, empty and success', () => {
    expect(toAsyncState({ status: 'pending', data: undefined, error: null })).toEqual({
      status: 'loading',
    });
    expect(toAsyncState({ status: 'error', data: undefined, error: 'x' })).toEqual({
      status: 'error',
      error: 'x',
    });
    expect(
      toAsyncState({ status: 'success', data: [], error: null }, (list) => list.length === 0),
    ).toEqual({
      status: 'empty',
    });
    expect(toAsyncState({ status: 'success', data: [1], error: null })).toEqual({
      status: 'success',
      data: [1],
    });
  });
});

describe('format', () => {
  it('formats numbers the Italian way', () => {
    expect(formatNumber(0.5844)).toBe('0,584');
    expect(formatPercent(0.15)).toBe('15,0%');
    expect(formatValue({ a: 1 })).toBe('{"a":1}');
    expect(formatValue(null)).toBe('—');
  });
});

describe('storage', () => {
  afterEach(() => {
    window.localStorage.clear();
  });

  it('remembers flags', () => {
    writeFlag(GUIDE_SEEN_KEY, true);
    expect(readFlag(GUIDE_SEEN_KEY)).toBe(true);
  });

  it('survives a storage that throws', () => {
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw new Error('blocked');
    });
    expect(() => {
      writeFlag(GUIDE_SEEN_KEY, true);
    }).not.toThrow();
    expect(readFlag(GUIDE_SEEN_KEY)).toBe(false);
  });
});
