import { ApiError } from '../api/ApiError';
import { api } from '../api/endpoints';
import type { VectorBlock } from '../api/vectors';
import { createUmapWorker } from './createUmapWorker';
import type { UmapProgress, UmapRequest, UmapResponse } from './protocol';

/** Fixed seed: the same data always gives the same picture. */
export const UMAP_SEED = 42;

export interface UmapRunRequest {
  readonly datasetVersion: string;
  readonly normalize: boolean;
  /** Points on screen: the vectors must have exactly one row per point. */
  readonly expectedRows: number;
}

export interface UmapRunCallbacks {
  readonly onProgress: (progress: UmapProgress) => void;
  readonly onDone: (layout: Float32Array<ArrayBuffer>) => void;
  readonly onError: (error: ApiError) => void;
}

export interface UmapRun {
  /** Stops the download or the worker; no callback fires afterwards. */
  readonly cancel: () => void;
}

/** What a run needs from the outside world; tests pass fakes. */
export interface UmapDependencies {
  readonly fetchVectors: (datasetVersion: string, signal: AbortSignal) => Promise<VectorBlock>;
  readonly createWorker: () => Worker;
}

const defaultDependencies: UmapDependencies = {
  fetchVectors: (datasetVersion, signal) => api.vectors(datasetVersion, signal),
  createWorker: createUmapWorker,
};

/**
 * Downloads the vectors of {@code request.datasetVersion} and computes their UMAP layout in a
 * Web Worker. Exactly one of onDone or onError ends a run that is not cancelled.
 */
export function startUmap(
  request: UmapRunRequest,
  callbacks: UmapRunCallbacks,
  dependencies: UmapDependencies = defaultDependencies,
): UmapRun {
  const download = new AbortController();
  let worker: Worker | null = null;
  let finished = false;

  const finish = () => {
    finished = true;
    worker?.terminate();
    worker = null;
  };
  const fail = (error: ApiError) => {
    if (!finished) {
      finish();
      callbacks.onError(error);
    }
  };

  const run = (block: VectorBlock) => {
    if (finished) {
      return;
    }
    if (block.rows !== request.expectedRows) {
      fail(
        new ApiError({
          code: 'INVALID_RESPONSE',
          status: null,
          requestId: null,
          detail: `Received ${block.rows} vectors for ${request.expectedRows} points.`,
        }),
      );
      return;
    }
    let started: Worker;
    try {
      started = dependencies.createWorker();
    } catch {
      fail(umapFailed('This browser cannot start the UMAP worker.'));
      return;
    }
    worker = started;
    started.addEventListener('message', (event: MessageEvent<UmapResponse>) => {
      if (finished) {
        return;
      }
      const message = event.data;
      if (message.type === 'progress') {
        callbacks.onProgress(message.progress);
      } else if (message.type === 'done') {
        finish();
        callbacks.onDone(message.layout);
      } else {
        fail(umapFailed(message.message));
      }
    });
    started.addEventListener('error', (event: ErrorEvent) => {
      event.preventDefault();
      fail(umapFailed(event.message || 'The UMAP worker could not run.'));
    });
    started.addEventListener('messageerror', () => {
      fail(umapFailed('A message of the UMAP worker could not be read.'));
    });
    const message: UmapRequest = {
      type: 'start',
      rows: block.rows,
      dimensions: block.dimensions,
      values: block.values,
      normalize: request.normalize,
      seed: UMAP_SEED,
    };
    started.postMessage(message, [block.values.buffer]);
  };

  dependencies.fetchVectors(request.datasetVersion, download.signal).then(run, (error: unknown) => {
    if (!download.signal.aborted) {
      fail(error instanceof ApiError ? error : umapFailed('The vectors could not be downloaded.'));
    }
  });

  return {
    cancel: () => {
      if (!finished) {
        finish();
        download.abort();
      }
    },
  };
}

function umapFailed(detail: string): ApiError {
  return new ApiError({ code: 'UMAP_FAILED', status: null, requestId: null, detail });
}
