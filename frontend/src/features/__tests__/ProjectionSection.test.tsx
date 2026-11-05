import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { dataset, points, problem } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { ProjectionSection } from '../projection/ProjectionSection';

describe('ProjectionSection', () => {
  it('shows a loading state', () => {
    mockApi([['/api/v1/dataset', { pending: true }]]);
    renderWithProviders(<ProjectionSection />);

    expect(screen.getByText(t.common.loading)).toBeInTheDocument();
    expect(screen.getByText(t.projection.note)).toBeInTheDocument();
  });

  it('shows the explained variance of each component and the total', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
    ]);
    renderWithProviders(<ProjectionSection />);

    expect(await screen.findByText('15,0%')).toBeInTheDocument();
    expect(screen.getByText('30,0%')).toBeInTheDocument();
    expect(screen.getByText(t.projection.totalExplained(0.3))).toBeInTheDocument();
  });

  it('explains when a projection is not possible (empty or tiny table)', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: problem('PROJECTION_NOT_POSSIBLE', 422), status: 422 }],
    ]);
    renderWithProviders(<ProjectionSection />);

    expect(await screen.findByText(t.errors.PROJECTION_NOT_POSSIBLE.message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.common.retry })).toBeInTheDocument();
  });

  it('toggles normalization and disables it for non-cosine metrics', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
    ]);
    renderWithProviders(<ProjectionSection />);
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
    ]);
    renderWithProviders(<ProjectionSection />);

    expect(await screen.findByText(t.projection.normalizeOnlyCosine)).toBeInTheDocument();
    expect(screen.getByRole('checkbox', { name: t.projection.normalize })).toBeDisabled();
  });
});
