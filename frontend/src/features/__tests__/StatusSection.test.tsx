import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { it as t } from '../../i18n/it';
import { mockApi } from '../../test/apiMock';
import { dataset, diagnosticsDbDown, diagnosticsOk } from '../../test/fixtures';
import { firstOf, renderWithProviders } from '../../test/render';
import { StatusSection } from '../status/StatusSection';

describe('StatusSection', () => {
  it('shows a loading state', () => {
    mockApi([
      ['/api/v1/diagnostics', { pending: true }],
      ['/api/v1/dataset', { pending: true }],
    ]);
    renderWithProviders(<StatusSection />);

    expect(screen.getAllByText(t.common.loading)).toHaveLength(2);
  });

  it('shows the checklist and the dataset summary', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsOk }],
      ['/api/v1/dataset', { json: { ...dataset, sampled: true, loadedRows: 2 } }],
    ]);
    renderWithProviders(<StatusSection />);

    expect(await screen.findByText(t.status.overall.OK)).toBeInTheDocument();
    expect(screen.getByText('Connessione al database')).toBeInTheDocument();
    expect(screen.getByText('Connected to PostgreSQL 17.11.')).toBeInTheDocument();
    expect(await screen.findByText(/spring_ai_vector_index/)).toBeInTheDocument();
    expect(screen.getByText(t.status.sampled(2, 4))).toBeInTheDocument();
  });

  it('explains a failed check in Italian with its hint', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsDbDown }],
      ['/api/v1/dataset', { json: dataset }],
    ]);
    renderWithProviders(<StatusSection />);

    expect(await screen.findByText(t.errors.DB_UNAVAILABLE.message)).toBeInTheDocument();
    expect(screen.getByText(t.errors.DB_UNAVAILABLE.hint)).toBeInTheDocument();
    expect(screen.getByText(t.status.overall.FAIL)).toBeInTheDocument();
  });

  it('shows the empty table hint', async () => {
    mockApi([
      ['/api/v1/diagnostics', { json: diagnosticsOk }],
      [
        '/api/v1/dataset',
        { json: { ...dataset, rowCount: 0, rowsWithEmbedding: 0, loadedRows: 0, files: [] } },
      ],
    ]);
    renderWithProviders(<StatusSection />);

    expect(await screen.findByText(t.status.emptyTable)).toBeInTheDocument();
  });

  it('shows the error with a working "Riprova"', async () => {
    const fetchMock = mockApi([
      ['/api/v1/diagnostics', { text: '<html>Bad Gateway</html>', status: 502 }],
      ['/api/v1/dataset', { json: dataset }],
    ]);
    renderWithProviders(<StatusSection />);

    expect(await screen.findByText(t.errors.BACKEND_UNAVAILABLE.message)).toBeInTheDocument();
    const calls = fetchMock.mock.calls.length;
    await userEvent.click(firstOf(screen.getAllByRole('button', { name: t.common.retry })));
    await waitFor(() => {
      expect(fetchMock.mock.calls.length).toBeGreaterThan(calls);
    });
  });
});
