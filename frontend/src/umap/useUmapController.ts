import { useEffect } from 'react';
import { usePoints } from '../hooks/queries';
import { sameLayoutKey, useUiStore } from '../state/uiStore';
import { UMAP_MIN_POINTS } from './protocol';
import { startUmap } from './umapRunner';

/**
 * Keeps the UMAP layout in step with the chosen method and the points on screen. Mounted once
 * (in App), so the computation survives collapsing the sidebar sections.
 *
 * - UMAP chosen and no layout for these points: download the vectors and start the worker.
 * - Data or normalization changed: the old run is cancelled and a new one starts.
 * - PCA chosen: a running computation is cancelled; a finished layout is kept, so switching
 *   back is instant.
 * - A failed run is not repeated by itself: "Riprova" increments `umapAttempt`.
 */
export function useUmapController(): void {
  const method = useUiStore((state) => state.projectionMethod);
  const attempt = useUiStore((state) => state.umapAttempt);
  const setUmap = useUiStore((state) => state.setUmap);
  const points = usePoints();
  // Placeholder data belongs to the previous version: wait for the current points.
  const data = points.isPlaceholderData ? undefined : points.data;
  const version = data?.datasetVersion ?? null;
  const normalized = data?.projection.normalized ?? null;
  const rows = data?.points.length ?? 0;

  useEffect(() => {
    if (method !== 'umap' || version === null || normalized === null || rows < UMAP_MIN_POINTS) {
      return;
    }
    const key = { datasetVersion: version, normalized };
    const current = useUiStore.getState().umap;
    const settled = current.status === 'done' || current.status === 'error';
    if (settled && sameLayoutKey(current.key, key)) {
      return;
    }
    setUmap({ status: 'loading', key });
    const run = startUmap(
      { datasetVersion: version, normalize: normalized, expectedRows: rows },
      {
        onProgress: (progress) => {
          setUmap({ status: 'running', key, progress });
        },
        onDone: (layout) => {
          setUmap({ status: 'done', key, layout });
        },
        onError: (error) => {
          setUmap({ status: 'error', key, error });
        },
      },
    );
    return () => {
      run.cancel();
      const state = useUiStore.getState().umap;
      const inProgress = state.status === 'loading' || state.status === 'running';
      if (inProgress && sameLayoutKey(state.key, key)) {
        setUmap({ status: 'idle' });
      }
    };
  }, [method, version, normalized, rows, attempt, setUmap]);
}
