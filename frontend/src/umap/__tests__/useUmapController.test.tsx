import { act, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, type Mock, vi } from 'vitest';
import { ApiError } from '../../api/ApiError';
import { useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { dataset, pointCloudOf, points } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { startUmap, type UmapRunCallbacks, type UmapRunRequest } from '../umapRunner';
import { useUmapController } from '../useUmapController';

vi.mock('../umapRunner', () => ({ startUmap: vi.fn() }));

interface RecordedRun {
  readonly request: UmapRunRequest;
  readonly callbacks: UmapRunCallbacks;
  readonly cancel: Mock;
}

const cloud = pointCloudOf(12);
const runs: RecordedRun[] = [];

function Probe() {
  useUmapController();
  return null;
}

/** Renders the controller and waits until the points are loaded. */
async function renderController(pointCloud = cloud) {
  mockApi([
    ['/api/v1/dataset', { json: dataset }],
    ['/api/v1/points', { json: pointCloud }],
  ]);
  const { client } = renderWithProviders(<Probe />);
  await waitFor(() => {
    const queries = client.getQueryCache().findAll({ queryKey: ['points'] });
    expect(queries.some((query) => query.state.status === 'success')).toBe(true);
  });
}

function choose(method: 'pca' | 'umap') {
  act(() => {
    useUiStore.getState().setProjectionMethod(method);
  });
}

function lastRun(): RecordedRun {
  const run = runs.at(-1);
  if (run === undefined) {
    throw new Error('No UMAP run was started');
  }
  return run;
}

describe('useUmapController', () => {
  beforeEach(() => {
    runs.length = 0;
    vi.mocked(startUmap).mockImplementation((request, callbacks) => {
      const cancel = vi.fn();
      runs.push({ request, callbacks, cancel });
      return { cancel };
    });
  });

  it('starts nothing while the PCA is chosen', async () => {
    await renderController();

    expect(startUmap).not.toHaveBeenCalled();
    expect(useUiStore.getState().umap).toEqual({ status: 'idle' });
  });

  it('computes the layout of the points on screen when UMAP is chosen', async () => {
    await renderController();
    choose('umap');

    await waitFor(() => {
      expect(runs).toHaveLength(1);
    });
    expect(lastRun().request).toEqual({
      datasetVersion: cloud.datasetVersion,
      normalize: true,
      expectedRows: 12,
    });
    const key = { datasetVersion: cloud.datasetVersion, normalized: true };
    expect(useUiStore.getState().umap).toEqual({ status: 'loading', key });

    act(() => {
      lastRun().callbacks.onProgress({ phase: 'neighbors' });
    });
    expect(useUiStore.getState().umap).toEqual({
      status: 'running',
      key,
      progress: { phase: 'neighbors' },
    });

    const layout = new Float32Array(36);
    act(() => {
      lastRun().callbacks.onDone(layout);
    });
    expect(useUiStore.getState().umap).toEqual({ status: 'done', key, layout });
  });

  it('cancels a running computation when the PCA is chosen again, but keeps a finished one', async () => {
    await renderController();
    choose('umap');
    await waitFor(() => {
      expect(runs).toHaveLength(1);
    });

    choose('pca');
    expect(lastRun().cancel).toHaveBeenCalled();
    expect(useUiStore.getState().umap.status).toBe('idle');

    choose('umap');
    await waitFor(() => {
      expect(runs).toHaveLength(2);
    });
    act(() => {
      lastRun().callbacks.onDone(new Float32Array(36));
    });
    choose('pca');
    choose('umap');

    expect(runs).toHaveLength(2);
    expect(useUiStore.getState().umap.status).toBe('done');
  });

  it('does not repeat a failed computation until "Riprova"', async () => {
    await renderController();
    choose('umap');
    await waitFor(() => {
      expect(runs).toHaveLength(1);
    });

    act(() => {
      lastRun().callbacks.onError(
        new ApiError({ code: 'UMAP_FAILED', status: null, requestId: null }),
      );
    });
    expect(useUiStore.getState().umap.status).toBe('error');
    expect(runs).toHaveLength(1);

    act(() => {
      useUiStore.getState().retryUmap();
    });
    await waitFor(() => {
      expect(runs).toHaveLength(2);
    });
  });

  it('needs enough points for UMAP', async () => {
    await renderController(points);
    choose('umap');

    expect(startUmap).not.toHaveBeenCalled();
  });
});
