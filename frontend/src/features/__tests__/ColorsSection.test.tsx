import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { dataset, points, problem } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { ColorsSection } from '../colors/ColorsSection';

describe('ColorsSection', () => {
  it('shows a loading state', () => {
    mockApi([['/api/v1/dataset', { pending: true }]]);
    renderWithProviders(<ColorsSection />);

    expect(screen.getByText(t.common.loading)).toBeInTheDocument();
  });

  it('shows an empty state when there are no points', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: { ...points, points: [] } }],
    ]);
    renderWithProviders(<ColorsSection />);

    expect(await screen.findByText(t.scene.noData)).toBeInTheDocument();
  });

  it('shows an error with "Riprova"', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: problem('DB_TIMEOUT', 504), status: 504 }],
    ]);
    renderWithProviders(<ColorsSection />);

    expect(await screen.findByText(t.errors.DB_TIMEOUT.message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.common.retry })).toBeInTheDocument();
  });

  it('shows the legend, hides a category and lists the visible chunks', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
    ]);
    renderWithProviders(<ColorsSection />);

    const legendButton = await screen.findByRole('button', {
      name: t.colors.categoryLabel('ferie.pdf', 2, false),
    });
    expect(screen.getByText(t.colors.listCount(4, 4))).toBeInTheDocument();

    await userEvent.click(legendButton);
    expect(useUiStore.getState().hiddenCategories).toEqual(['ferie.pdf']);
    expect(await screen.findByText(t.colors.listCount(2, 2))).toBeInTheDocument();

    await userEvent.click(screen.getByRole('button', { name: t.colors.showAll }));
    expect(useUiStore.getState().hiddenCategories).toEqual([]);
  });

  it('selects a chunk from the accessible list', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
    ]);
    renderWithProviders(<ColorsSection />);

    await userEvent.click(await screen.findByRole('button', { name: /Testo del chunk c/ }));
    expect(useUiStore.getState().selectedId).toBe('c');
    expect(useUiStore.getState().focusRequest?.id).toBe('c');
  });

  it('filters by file and searches the full text', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      [
        /^\/api\/v1\/chunks\?contains=ferie/,
        { json: { contains: 'ferie', ids: ['a'], truncated: false } },
      ],
    ]);
    renderWithProviders(<ColorsSection />);

    const files = await screen.findByRole('group', { name: t.colors.fileFilter });
    await userEvent.click(within(files).getByRole('checkbox', { name: /sicurezza\.pdf/ }));
    expect(useUiStore.getState().filters.files).toEqual(['ferie.pdf']);

    await userEvent.type(screen.getByRole('searchbox', { name: t.colors.textSearch }), 'ferie');
    expect(await screen.findByText(t.colors.textMatches(1, false))).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getByText(t.colors.listCount(1, 1))).toBeInTheDocument();
    });
  });
});
