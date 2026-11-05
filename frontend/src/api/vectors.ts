import { ApiError } from './ApiError';
import type { BinaryResponse } from './http';

/** The raw snapshot vectors of GET /api/v1/vectors, one row per point of /points, same order. */
export interface VectorBlock {
  readonly datasetVersion: string;
  readonly rows: number;
  readonly dimensions: number;
  /** rows × dimensions values, row-major. */
  readonly values: Float32Array<ArrayBuffer>;
}

const IS_LITTLE_ENDIAN = new Uint8Array(new Uint16Array([1]).buffer)[0] === 1;

/**
 * Checks the X-Rows, X-Dimensions and X-Dataset-Version headers against the body and decodes
 * the float32 little-endian values. Anything inconsistent is an INVALID_RESPONSE, never a
 * misaligned picture.
 */
export function decodeVectorBlock(response: BinaryResponse): VectorBlock {
  const rows = countHeader(response, 'X-Rows');
  const dimensions = countHeader(response, 'X-Dimensions');
  const datasetVersion = response.headers.get('X-Dataset-Version') ?? '';
  if (datasetVersion === '') {
    throw invalid(response, 'the X-Dataset-Version header is missing');
  }
  const expectedBytes = rows * dimensions * Float32Array.BYTES_PER_ELEMENT;
  if (response.body.byteLength !== expectedBytes) {
    throw invalid(
      response,
      `expected ${rows} × ${dimensions} float32 values (${expectedBytes} bytes), received ${response.body.byteLength} bytes`,
    );
  }
  return { datasetVersion, rows, dimensions, values: littleEndianFloats(response.body) };
}

/**
 * The values of a little-endian float32 buffer. A Float32Array reads the platform byte order:
 * little-endian on every common CPU, so the bytes are used as they are; a big-endian platform
 * gets a converted copy.
 */
export function littleEndianFloats(buffer: ArrayBuffer): Float32Array<ArrayBuffer> {
  if (IS_LITTLE_ENDIAN) {
    return new Float32Array(buffer);
  }
  const view = new DataView(buffer);
  const values = new Float32Array(buffer.byteLength / Float32Array.BYTES_PER_ELEMENT);
  for (let index = 0; index < values.length; index++) {
    values[index] = view.getFloat32(index * Float32Array.BYTES_PER_ELEMENT, true);
  }
  return values;
}

function countHeader(response: BinaryResponse, name: string): number {
  const value = response.headers.get(name) ?? '';
  if (!/^\d{1,9}$/.test(value)) {
    throw invalid(response, `the ${name} header is missing or not a count`);
  }
  return Number(value);
}

function invalid(response: BinaryResponse, detail: string): ApiError {
  return new ApiError({
    code: 'INVALID_RESPONSE',
    status: null,
    requestId: response.requestId,
    detail,
  });
}
