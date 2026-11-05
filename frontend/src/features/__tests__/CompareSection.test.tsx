import { act, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { comparison, dataset, points, problem } from '../../test/fixtures';
import { firstOf, renderWithProviders } from '../../test/render';
import { CompareSection } from '../compare/CompareSection';

function pick(a: string, b: string) {
  act(() => {
    useUiStore.getState().setCompare('A', a);
    useUiStore.getState().setCompare('B', b);
  });
}

describe('CompareSection', () => {
  it('explains how to fill the two slots when they are empty', () => {
    mockApi([['/api/v1/dataset', { json: dataset }]]);
    renderWithProviders(<CompareSection />);

    expect(screen.getAllByText(t.compare.emptySlot)).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: t.compare.pickSelected })[0]).toBeDisabled();
  });

  it('fills a slot with the selected chunk', async () => {
    mockApi([['/api/v1/dataset', { json: dataset }]]);
    renderWithProviders(<CompareSection />);
    act(() => {
      useUiStore.getState().select('c');
    });

    await userEvent.click(firstOf(screen.getAllByRole('button', { name: t.compare.pickSelected })));
    expect(useUiStore.getState().compareA).toBe('c');
  });

  it('shows a loading state', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      [/^\/api\/v1\/compare/, { pending: true }],
    ]);
    renderWithProviders(<CompareSection />);
    pick('a', 'c');

    expect(await screen.findByText(t.common.loading)).toBeInTheDocument();
  });

  it('shows an error', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      [/^\/api\/v1\/compare/, { json: problem('CHUNK_NOT_FOUND', 404), status: 404 }],
    ]);
    renderWithProviders(<CompareSection />);
    pick('a', 'c');

    expect(await screen.findByText(t.errors.CHUNK_NOT_FOUND.message)).toBeInTheDocument();
  });

  it('shows the real similarity next to the distance in the 3D view', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      [/^\/api\/v1\/compare/, { json: comparison }],
    ]);
    renderWithProviders(<CompareSection />);
    pick('a', 'c');

    expect(await screen.findByText(t.compare.cosineSimilarity)).toBeInTheDocument();
    expect(screen.getAllByText('0,500').length).toBeGreaterThan(0);
    // a = (0.1, 0.05, -0.1), c = (-0.3, -0.15, 0.3): distance 0.6, real distance √(2 − 2·0.5) = 1
    expect(screen.getByText('0,600')).toBeInTheDocument();
    expect(screen.getByText(t.compare.shownShare(0.6))).toBeInTheDocument();
    expect(screen.getByText(t.compare.why)).toBeInTheDocument();
  });

  it('does not compare distances on a UMAP picture', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      [/^\/api\/v1\/compare/, { json: comparison }],
    ]);
    renderWithProviders(<CompareSection />);
    pick('a', 'c');
    await screen.findByText(t.compare.distance3d);

    act(() => {
      useUiStore.setState({
        projectionMethod: 'umap',
        umap: {
          status: 'done',
          key: { datasetVersion: points.datasetVersion, normalized: true },
          layout: new Float32Array(points.points.length * 3),
        },
      });
    });

    expect(screen.getByText(t.compare.umapDistance)).toBeInTheDocument();
    expect(screen.queryByText(t.compare.distance3d)).not.toBeInTheDocument();
    expect(screen.queryByText(/della distanza vera/)).not.toBeInTheDocument();
  });
});
