import { describe, expect, it } from 'vitest';
import { problem } from '../../test/fixtures';
import { mockApi } from '../../test/apiMock';
import { ApiError } from '../ApiError';
import { api } from '../endpoints';
import type { BinaryResponse } from '../http';
import { decodeVectorBlock, littleEndianFloats } from '../vectors';

/** The bytes the backend sends: float32, little-endian, row-major. */
function littleEndianBytes(values: readonly number[]): ArrayBuffer {
  const view = new DataView(new ArrayBuffer(values.length * 4));
  values.forEach((value, index) => {
    view.setFloat32(index * 4, value, true);
  });
  return view.buffer;
}

function binary(values: readonly number[], headers: Record<string, string>): BinaryResponse {
  return { body: littleEndianBytes(values), headers: new Headers(headers), requestId: 'req-1' };
}

const HEADERS = { 'X-Rows': '2', 'X-Dimensions': '3', 'X-Dataset-Version': '2-abc' };

function thrown(action: () => unknown): unknown {
  try {
    action();
  } catch (error) {
    return error;
  }
  throw new Error('Expected an error');
}

describe('vector blocks', () => {
  it('decodes float32 little-endian rows and their headers', () => {
    const block = decodeVectorBlock(binary([1.5, -2, 0, 0.25, 3, -0.125], HEADERS));

    expect(block).toMatchObject({ datasetVersion: '2-abc', rows: 2, dimensions: 3 });
    expect(Array.from(block.values)).toEqual([1.5, -2, 0, 0.25, 3, -0.125]);
    expect(Array.from(littleEndianFloats(littleEndianBytes([7.5])))).toEqual([7.5]);
  });

  it('rejects a body that does not match the headers', () => {
    const decode = (values: number[], headers: Record<string, string>) => () =>
      decodeVectorBlock(binary(values, headers));

    expect(decode([1, 2, 3], HEADERS)).toThrow(/expected 2 × 3 float32 values/);
    expect(decode([1], { ...HEADERS, 'X-Rows': 'many' })).toThrow(/X-Rows/);
    expect(decode([1], { 'X-Rows': '1', 'X-Dimensions': '1' })).toThrow(/X-Dataset-Version/);
    const error = thrown(decode([1, 2, 3], HEADERS));
    expect(error).toBeInstanceOf(ApiError);
    expect(error).toMatchObject({ code: 'INVALID_RESPONSE', requestId: 'req-1' });
  });

  it('downloads the vectors of a dataset version', async () => {
    const fetchMock = mockApi([
      [
        '/api/v1/vectors',
        {
          binary: littleEndianBytes([0.5, -0.5]),
          headers: { ...HEADERS, 'X-Rows': '1', 'X-Dimensions': '2' },
        },
      ],
    ]);

    const block = await api.vectors('2-abc');

    expect(Array.from(block.values)).toEqual([0.5, -0.5]);
    const [input, init] = fetchMock.mock.calls[0] ?? [];
    expect(input).toBe('/api/v1/vectors?datasetVersion=2-abc');
    expect(new Headers(init?.headers).get('Accept')).toContain('application/octet-stream');
  });

  it('turns a problem document into the same ApiError as the JSON endpoints', async () => {
    mockApi([['/api/v1/vectors', { json: problem('DATASET_CHANGED', 409), status: 409 }]]);

    await expect(api.vectors('1-old')).rejects.toMatchObject({
      code: 'DATASET_CHANGED',
      status: 409,
    });
  });
});
