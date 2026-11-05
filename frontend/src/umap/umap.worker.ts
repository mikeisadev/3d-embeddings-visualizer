/**
 * Runs UMAP off the main thread, so the page stays responsive while it works (from a second
 * to half a minute). The page cancels a run by terminating the worker.
 */
import type { UmapRequest, UmapResponse } from './protocol';
import { computeUmapLayout } from './umapLayout';

function post(message: UmapResponse, transfer: Transferable[] = []): void {
  self.postMessage(message, { transfer });
}

self.addEventListener('message', (event: MessageEvent<UmapRequest>) => {
  try {
    const layout = computeUmapLayout(event.data, (progress) => {
      post({ type: 'progress', progress });
    });
    post({ type: 'done', layout }, [layout.buffer]);
  } catch (error) {
    post({ type: 'error', message: error instanceof Error ? error.message : 'Unknown error' });
  }
});
