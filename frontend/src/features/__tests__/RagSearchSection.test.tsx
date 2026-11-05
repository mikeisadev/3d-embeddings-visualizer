import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import { callsTo, jsonBody, mockApi } from '../../test/apiMock';
import {
  dataset,
  diagnosticsOk,
  diagnosticsRagEnabled,
  problem,
  searchResult,
} from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { RagSearchSection } from '../search/RagSearchSection';

async function ask(question: string) {
  await userEvent.type(await screen.findByRole('textbox', { name: t.rag.question }), question);
  await userEvent.click(screen.getByRole('button', { name: t.rag.search }));
}

describe('RagSearchSection', () => {
  it('keeps the panel and explains what to configure when the feature is disabled', async () => {
    mockApi([['/api/v1/diagnostics', { json: diagnosticsOk }]]);
    renderWithProviders(<RagSearchSection />);

    expect(await screen.findByText(t.rag.disabledTitle)).toBeInTheDocument();
    expect(screen.getByText('VIZ_EMBEDDING_ENABLED=true')).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: t.rag.search })).not.toBeInTheDocument();
  });

  it('only allows searching a non-empty question', async () => {
    mockApi([['/api/v1/diagnostics', { json: diagnosticsRagEnabled }]]);
    renderWithProviders(<RagSearchSection />);

    expect(await screen.findByRole('button', { name: t.rag.search })).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox', { name: t.rag.question }), '   ');
    expect(screen.getByRole('button', { name: t.rag.search })).toBeDisabled();
  });

  it('shows a loading state while searching', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/search', { pending: true }],
    ]);
    renderWithProviders(<RagSearchSection />);

    await ask('Quante ferie?');
    expect(screen.getAllByText(t.rag.searching).length).toBeGreaterThan(0);
  });

  it('shows an error in Italian without retrying the paid call', async () => {
    const fetchMock = mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/search', { json: problem('RATE_LIMITED', 429), status: 429 }],
    ]);
    renderWithProviders(<RagSearchSection />);

    await ask('Quante ferie?');
    expect(await screen.findByText(t.errors.RATE_LIMITED.message)).toBeInTheDocument();
    expect(callsTo(fetchMock, '/api/v1/search')).toHaveLength(1);
  });

  it('shows the ranked results with the threshold outcome and the Spring AI SQL', async () => {
    const fetchMock = mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/dataset', { json: dataset }],
      ['/api/v1/search', { json: searchResult }],
    ]);
    renderWithProviders(<RagSearchSection />);

    await ask('Come chiedo le ferie?');

    expect(await screen.findByText(t.rag.summary(2, 3))).toBeInTheDocument();
    expect(screen.getAllByText(t.rag.passes)).toHaveLength(2);
    expect(screen.getAllByText(t.rag.discarded)).toHaveLength(1);
    expect(screen.getByText(searchResult.springAiSql)).toBeInTheDocument();
    expect(screen.getByText(t.rag.sqlParameters(0.5, 3))).toBeInTheDocument();
    const [call] = callsTo(fetchMock, '/api/v1/search');
    expect(jsonBody(call?.[1])).toEqual({
      question: 'Come chiedo le ferie?',
      topK: 5,
      similarityThreshold: 0.5,
      normalize: true,
    });
    await waitFor(() => {
      expect(useUiStore.getState().ragOverlay?.results).toHaveLength(3);
    });

    await userEvent.click(screen.getByRole('button', { name: t.rag.clear }));
    expect(useUiStore.getState().ragOverlay).toBeNull();
  });
});
