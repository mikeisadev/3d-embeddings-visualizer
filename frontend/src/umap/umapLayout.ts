import { UMAP } from 'umap-js';
import { seededRandom } from '../lib/random';
import { UMAP_MIN_POINTS, type UmapProgress } from './protocol';

/** The umap-learn defaults: 15 neighbors balance local and global structure. */
const NEIGHBORS = 15;
const MIN_DIST = 0.1;
const PROGRESS_EVERY_EPOCHS = 10;

export interface UmapInput {
  readonly rows: number;
  readonly dimensions: number;
  readonly values: Float32Array;
  readonly normalize: boolean;
  readonly seed: number;
}

/**
 * The 3D UMAP layout of the vectors: rows × 3 coordinates, in the order of the rows.
 *
 * With {@code normalize}, every vector is first scaled to length 1: the euclidean distance UMAP
 * uses then ranks neighbors exactly like the cosine distance, the same rule the backend applies
 * before its PCA. A seeded generator makes the result reproducible.
 */
export function computeUmapLayout(
  input: UmapInput,
  onProgress: (progress: UmapProgress) => void,
): Float32Array<ArrayBuffer> {
  if (input.rows * input.dimensions !== input.values.length) {
    throw new Error(
      `Expected ${input.rows} × ${input.dimensions} values, received ${input.values.length}.`,
    );
  }
  if (input.rows < UMAP_MIN_POINTS) {
    throw new Error(`UMAP needs at least ${UMAP_MIN_POINTS} vectors, received ${input.rows}.`);
  }
  const umap = new UMAP({
    nComponents: 3,
    nNeighbors: Math.min(NEIGHBORS, input.rows - 1),
    minDist: MIN_DIST,
    random: seededRandom(input.seed),
  });

  onProgress({ phase: 'neighbors' });
  const epochs = umap.initializeFit(toRows(input));
  let epoch = 0;
  while (epoch < epochs) {
    const next = umap.step();
    if (next <= epoch) {
      break;
    }
    epoch = next;
    if (epoch % PROGRESS_EVERY_EPOCHS === 0 || epoch === epochs) {
      onProgress({ phase: 'optimizing', epoch, epochs });
    }
  }
  return flatten(umap.getEmbedding(), input.rows);
}

/** One plain array per vector, as umap-js expects, normalized when requested. */
function toRows({ rows, dimensions, values, normalize }: UmapInput): number[][] {
  const result: number[][] = [];
  for (let row = 0; row < rows; row++) {
    const vector = Array.from(values.subarray(row * dimensions, (row + 1) * dimensions));
    const length = normalize ? Math.sqrt(vector.reduce((sum, value) => sum + value * value, 0)) : 0;
    result.push(length > 0 ? vector.map((value) => value / length) : vector);
  }
  return result;
}

function flatten(
  embedding: readonly (readonly number[])[],
  rows: number,
): Float32Array<ArrayBuffer> {
  const layout = new Float32Array(rows * 3);
  embedding.forEach((point, row) => {
    layout[row * 3] = point[0] ?? 0;
    layout[row * 3 + 1] = point[1] ?? 0;
    layout[row * 3 + 2] = point[2] ?? 0;
  });
  return layout;
}
