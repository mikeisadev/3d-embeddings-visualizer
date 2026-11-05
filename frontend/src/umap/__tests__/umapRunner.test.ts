import { describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../api/ApiError';
import type { VectorBlock } from '../../api/vectors';
import type { UmapRequest, UmapResponse } from '../protocol';
import { startUmap, type UmapDependencies, type UmapRunCallbacks } from '../umapRunner';

/** A stand-in for the browser Worker: records what the page sends and lets the test answer. */
class FakeWorker extends EventTarget {
  readonly posted: { message: UmapRequest; transfer: Transferable[] }[] = [];
  terminated = false;

  postMessage(message: UmapRequest, transfer: Transferable[]): void {
    this.posted.push({ message, transfer });
  }

  terminate(): void {
    this.terminated = true;
  }

  answer(message: UmapResponse): void {
    this.dispatchEvent(new MessageEvent('message', { data: message }));
  }
}

function block(rows: number): VectorBlock {
  return { datasetVersion: '4-abc', rows, dimensions: 2, values: new Float32Array(rows * 2) };
}

function setup(download: Promise<VectorBlock>) {
  const worker = new FakeWorker();
  const callbacks: UmapRunCallbacks = { onProgress: vi.fn(), onDone: vi.fn(), onError: vi.fn() };
  const dependencies: UmapDependencies = {
    fetchVectors: vi.fn(() => download),
    // The fake implements the part of Worker the runner uses.
    createWorker: () => worker as unknown as Worker,
  };
  const run = startUmap(
    { datasetVersion: '4-abc', normalize: true, expectedRows: 4 },
    callbacks,
    dependencies,
  );
  return { worker, callbacks, dependencies, run };
}

async function settle(): Promise<void> {
  await new Promise((resolve) => setTimeout(resolve, 0));
}

describe('startUmap', () => {
  it('downloads the vectors, transfers them to the worker and relays its answers', async () => {
    const { worker, callbacks, dependencies } = setup(Promise.resolve(block(4)));
    await settle();

    expect(dependencies.fetchVectors).toHaveBeenCalledWith('4-abc', expect.any(AbortSignal));
    const [start] = worker.posted;
    expect(start?.message).toMatchObject({
      type: 'start',
      rows: 4,
      dimensions: 2,
      normalize: true,
      seed: 42,
    });
    expect(start?.transfer).toEqual([start?.message.values.buffer]);

    worker.answer({ type: 'progress', progress: { phase: 'neighbors' } });
    const layout = new Float32Array(12);
    worker.answer({ type: 'done', layout });

    expect(callbacks.onProgress).toHaveBeenCalledWith({ phase: 'neighbors' });
    expect(callbacks.onDone).toHaveBeenCalledWith(layout);
    expect(worker.terminated).toBe(true);
    expect(callbacks.onError).not.toHaveBeenCalled();
  });

  it('reports worker failures as UMAP_FAILED', async () => {
    const { worker, callbacks } = setup(Promise.resolve(block(4)));
    await settle();

    worker.answer({ type: 'error', message: 'out of memory' });

    expect(callbacks.onError).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'UMAP_FAILED', detail: 'out of memory' }),
    );
    expect(worker.terminated).toBe(true);
  });

  it('reports a worker that cannot run', async () => {
    const { worker, callbacks } = setup(Promise.resolve(block(4)));
    await settle();

    worker.dispatchEvent(new ErrorEvent('error', { message: 'SyntaxError in worker' }));

    expect(callbacks.onError).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'UMAP_FAILED', detail: 'SyntaxError in worker' }),
    );
  });

  it('passes download errors through and checks the number of rows', async () => {
    const failed = setup(
      Promise.reject(new ApiError({ code: 'DATASET_CHANGED', status: 409, requestId: 'r1' })),
    );
    const misaligned = setup(Promise.resolve(block(3)));
    await settle();

    expect(failed.callbacks.onError).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'DATASET_CHANGED' }),
    );
    expect(misaligned.callbacks.onError).toHaveBeenCalledWith(
      expect.objectContaining({ code: 'INVALID_RESPONSE' }),
    );
    expect(misaligned.worker.posted).toHaveLength(0);
  });

  it('stays silent after a cancel, during the download or in the worker', async () => {
    let resolveDownload: (value: VectorBlock) => void = () => undefined;
    const downloading = setup(
      new Promise<VectorBlock>((resolve) => {
        resolveDownload = resolve;
      }),
    );
    downloading.run.cancel();
    resolveDownload(block(4));
    await settle();
    expect(downloading.worker.posted).toHaveLength(0);

    const computing = setup(Promise.resolve(block(4)));
    await settle();
    computing.run.cancel();
    computing.worker.answer({ type: 'done', layout: new Float32Array(12) });

    expect(computing.worker.terminated).toBe(true);
    for (const callbacks of [downloading.callbacks, computing.callbacks]) {
      expect(callbacks.onDone).not.toHaveBeenCalled();
      expect(callbacks.onError).not.toHaveBeenCalled();
    }
  });
});
