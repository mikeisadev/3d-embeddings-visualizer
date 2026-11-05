import type { UmapState } from '../state/uiStore';

/** Completed share of the UMAP optimization; `null` while the duration is unknown. */
export function progressShare(umap: UmapState): number | null {
  if (umap.status !== 'running' || umap.progress.phase !== 'optimizing') {
    return null;
  }
  return umap.progress.epochs > 0 ? umap.progress.epoch / umap.progress.epochs : null;
}
