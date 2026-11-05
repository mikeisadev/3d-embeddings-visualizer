import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import { mockApi } from '../../test/apiMock';
import { chunk, dataset, neighbors, points, problem } from '../../test/fixtures';
import { nth, renderWithProviders } from '../../test/render';
import { SelectedChunkSection } from '../chunk/SelectedChunkSection';

function renderSelected() {
  const result = renderWithProviders(<SelectedChunkSection />);
  useUiStore.getState().select('a');
  return result;
}

describe('SelectedChunkSection', () => {
  it('invites to select a chunk when nothing is selected', () => {
    mockApi([['/api/v1/dataset', { json: dataset }]]);
    renderWithProviders(<SelectedChunkSection />);

    expect(screen.getByText(t.chunk.empty)).toBeInTheDocument();
  });

  it('shows a loading state', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/chunks/a', { pending: true }],
    ]);
    renderSelected();

    expect(await screen.findByText(t.common.loading)).toBeInTheDocument();
  });

  it('shows an error when the chunk no longer exists', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/chunks/a', { json: problem('CHUNK_NOT_FOUND', 404), status: 404 }],
    ]);
    renderSelected();

    expect(await screen.findByText(t.errors.CHUNK_NOT_FOUND.message)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: t.common.retry })).toBeInTheDocument();
  });

  it('shows text as plain text, metadata, vector stats and the real neighbors', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      ['/api/v1/chunks/a', { json: chunk }],
      [/^\/api\/v1\/chunks\/a\/neighbors/, { json: neighbors }],
    ]);
    const { container } = renderSelected();

    expect(await screen.findByText(/<script>alert\(1\)<\/script>/)).toBeInTheDocument();
    expect(container.querySelector('script')).toBeNull();
    expect(screen.getByRole('rowheader', { name: 'department' })).toBeInTheDocument();
    expect(
      screen.getByText('[0,5000; -0,5000; 0,5000; -0,5000; 0,0000; 0,0000; …]'),
    ).toBeInTheDocument();
    expect(
      screen.getByRole('img', { name: t.chunk.barcodeDescription(6, 3, 2) }),
    ).toBeInTheDocument();
    expect(await screen.findByText(`${t.chunk.similarity}: 0,800`)).toBeInTheDocument();
    await waitFor(() => {
      expect(screen.getAllByRole('button', { name: t.chunk.notInSample })).toHaveLength(1);
    });
    expect(screen.getByRole('button', { name: t.chunk.notInSample })).toBeDisabled();
  });

  it('sends the chunk to the comparison and frames a neighbor without changing the selection', async () => {
    mockApi([
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/points', { json: points }],
      ['/api/v1/chunks/a', { json: chunk }],
      [/^\/api\/v1\/chunks\/a\/neighbors/, { json: neighbors }],
    ]);
    renderSelected();

    await userEvent.click(await screen.findByRole('button', { name: t.chunk.useAsA }));
    expect(useUiStore.getState().compareA).toBe('a');

    const showButtons = await screen.findAllByRole('button', { name: t.chunk.showInView });
    await userEvent.click(nth(showButtons, 1));
    expect(useUiStore.getState().focusRequest?.id).toBe('b');
    expect(useUiStore.getState().selectedId).toBe('a');
  });
});
