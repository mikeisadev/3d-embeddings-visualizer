import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { ApiError } from '../../api/ApiError';
import { it as t } from '../../i18n/it';
import { type UmapState, useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { dataset, neighborSample, pointCloudOf, points, problem } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { UMAP_MIN_POINTS } from '../../umap/protocol';
import { ProjectionSection } from '../projection/ProjectionSection';

const KEY = { datasetVersion: points.datasetVersion, normalized: true };

function renderLoaded(cloud = points) {
  mockApi([
    ['/api/v1/dataset', { json: dataset }],
    ['/api/v1/points', { json: cloud }],
    ['/api/v1/neighbors/sample', { json: neighborSample }],
  ]);
  return renderWithProviders(<ProjectionSection />);
}

function showUmap(state: UmapState) {
  act(() => {
    useUiStore.setState({ projectionMethod: 'umap', umap: state });
  });
}

describe('ProjectionSection', () => {
  it('shows a loading state', () => {
    mockApi([['/api/v1/dataset', { pending: true }]]);
    renderWithProviders(<ProjectionSection />);

    expect(screen.getAllByText(t.common.loading).length).toBeGreaterThan(0);
    expect(screen.getByText(t.projection.note)).toBeInTheDocument();
  });

  it('shows the explained variance of each component and the total', async () => {
    renderLoaded();

    expect(await screen.findByText('15,0%')).toBeInTheDocument();
    expect(screen.getByText('30,0%')).toBeInTheDocument();
    expect(screen.getByText(t.projection.totalExplained(0.3))).toBeInTheDocument();
  });

  it('explains when a projection is not possible (empty or tiny table)', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: problem('PROJECTION_NOT_POSSIBLE', 422), status: 422 }],
      ['/api/v1/neighbors/sample', { json: neighborSample }],
    ]);
    renderWithProviders(<ProjectionSection />);

    expect(await screen.findByText(t.errors.PROJECTION_NOT_POSSIBLE.message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.common.retry })).toBeInTheDocument();
  });

  it('toggles normalization and disables it for non-cosine metrics', async () => {
    renderLoaded();
    const checkbox = await screen.findByRole('checkbox', { name: t.projection.normalize });

    await userEvent.click(checkbox);
    expect(useUiStore.getState().normalize).toBe(false);
  });

  it('disables normalization with the L2 metric', async () => {
    mockApi([
      [
        '/api/v1/dataset',
        { json: { ...dataset, distanceMetric: { metric: 'L2', source: 'DETECTED' } } },
      ],
      ['/api/v1/points', { json: points }],
      ['/api/v1/neighbors/sample', { json: neighborSample }],
    ]);
    renderWithProviders(<ProjectionSection />);

    expect(await screen.findByText(t.projection.normalizeOnlyCosine)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: t.projection.normalize })).toBeDisabled();
  });

  it('offers UMAP only when there are enough chunks', async () => {
    renderLoaded();

    expect(await screen.findByText(t.projection.umapTooFew(UMAP_MIN_POINTS))).toBeInTheDocument();
    expect(screen.getByRole('radio', { name: /UMAP/ })).toBeDisabled();
    expect(screen.getByRole('radio', { name: /PCA/ })).toBeChecked();
  });

  it('switches to UMAP', async () => {
    renderLoaded(pointCloudOf(12));
    const umap = await screen.findByRole('radio', { name: /UMAP/ });
    await screen.findByText('15,0%');

    await userEvent.click(umap);

    expect(useUiStore.getState().projectionMethod).toBe('umap');
    expect(screen.queryByText(t.projection.umapTooFew(UMAP_MIN_POINTS))).not.toBeInTheDocument();
  });

  it('shows the progress of UMAP and lets the student cancel', async () => {
    renderLoaded();
    await screen.findByText('15,0%');
    showUmap({ status: 'loading', key: KEY });
    expect(screen.getByText(t.projection.umapDownloading)).toBeInTheDocument();

    showUmap({ status: 'running', key: KEY, progress: { phase: 'neighbors' } });
    expect(screen.getByText(t.projection.umapNeighbors)).toBeInTheDocument();
    expect(
      screen.getByRole('progressbar', { name: t.projection.umapProgress }),
    ).not.toHaveAttribute('value');

    showUmap({
      status: 'running',
      key: KEY,
      progress: { phase: 'optimizing', epoch: 50, epochs: 500 },
    });
    expect(screen.getByText(t.projection.umapOptimizing(50, 500))).toBeInTheDocument();
    expect(screen.getByRole('progressbar', { name: t.projection.umapProgress })).toHaveAttribute(
      'value',
      '0.1',
    );

    await userEvent.click(screen.getByRole('button', { name: t.projection.umapCancel }));
    expect(useUiStore.getState().projectionMethod).toBe('pca');
  });

  it('shows UMAP errors with "Riprova"', async () => {
    renderLoaded();
    await screen.findByText('15,0%');
    showUmap({
      status: 'error',
      key: KEY,
      error: new ApiError({ code: 'UMAP_FAILED', status: null, requestId: null, detail: 'oom' }),
    });

    expect(screen.getByText(t.errors.UMAP_FAILED.message)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: t.common.retry }));
    expect(useUiStore.getState().umapAttempt).toBe(1);
    expect(useUiStore.getState().umap.status).toBe('idle');
  });

  it('replaces the explained variance with a note while UMAP is on screen', async () => {
    renderLoaded();
    await screen.findByText('15,0%');

    showUmap({ status: 'done', key: KEY, layout: new Float32Array(points.points.length * 3) });

    expect(screen.getByText(t.projection.umapReady)).toBeInTheDocument();
    expect(screen.getByText(t.projection.umapNoVariance)).toBeInTheDocument();
    expect(screen.queryByText('15,0%')).not.toBeInTheDocument();
  });

  it('measures how many real neighbors stay neighbors in the picture', async () => {
    renderLoaded();

    expect(await screen.findByText(t.projection.fidelityExplained(1, 1, 4))).toBeInTheDocument();
    expect(screen.getByRole('meter', { name: t.projection.fidelity })).toHaveAttribute(
      'value',
      '1',
    );
  });
});
