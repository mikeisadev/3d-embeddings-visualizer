import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { embeddingEnabled } from '../../lib/embedding';
import { callsTo, mockApi } from '../../test/apiMock';
import { dataset, diagnosticsOk, diagnosticsRagEnabled, problem } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { StatusSection } from '../status/StatusSection';

describe('embedding live test', () => {
  it('reads whether the RAG simulation is enabled from the diagnostics', () => {
    expect(embeddingEnabled(diagnosticsOk)).toBe(false);
    expect(embeddingEnabled(diagnosticsRagEnabled)).toBe(true);
    expect(embeddingEnabled(undefined)).toBeNull();
  });

  it('calls the provider only on request and reports the result', async () => {
    const fetchMock = mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/dataset', { json: { ...dataset, dimensions: 1536 } }],
      [
        '/api/v1/diagnostics/embedding-test',
        { json: { model: 'text-embedding-3-small', dimensions: 1536, latencyMs: 180 } },
      ],
    ]);
    renderWithProviders(<StatusSection />);

    const button = await screen.findByRole('button', { name: t.status.liveTest });
    expect(callsTo(fetchMock, '/api/v1/diagnostics/embedding-test')).toHaveLength(0);
    await userEvent.click(button);
    expect(
      await screen.findByText(t.status.liveTestOk('text-embedding-3-small', 1536, 180)),
    ).toBeInTheDocument();
  });

  it('warns when the provider answers with another dimension', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/dataset', { json: dataset }],
      [
        '/api/v1/diagnostics/embedding-test',
        { json: { model: 'bge-m3', dimensions: 1024, latencyMs: 90 } },
      ],
    ]);
    renderWithProviders(<StatusSection />);

    await userEvent.click(await screen.findByRole('button', { name: t.status.liveTest }));
    expect(
      await screen.findByText(new RegExp(t.errors.DIMENSION_MISMATCH.message)),
    ).toBeInTheDocument();
  });

  it('shows provider errors', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsRagEnabled }],
      ['/api/v1/dataset', { json: dataset }],
      [
        '/api/v1/diagnostics/embedding-test',
        { json: problem('EMBEDDING_AUTH_FAILED', 502), status: 502 },
      ],
    ]);
    renderWithProviders(<StatusSection />);

    await userEvent.click(await screen.findByRole('button', { name: t.status.liveTest }));
    expect(await screen.findByText(t.errors.EMBEDDING_AUTH_FAILED.message)).toBeInTheDocument();
  });
});
