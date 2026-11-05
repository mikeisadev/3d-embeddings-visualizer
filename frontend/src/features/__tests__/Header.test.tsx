import { screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { usePresentationMode } from '../../hooks/usePresentationMode';
import { it as t } from '../../i18n/it';
import { mockApi } from '../../test/apiMock';
import { dataset } from '../../test/fixtures';
import { renderWithProviders } from '../../test/render';
import { Header } from '../header/Header';

function HeaderWithPresentation() {
  usePresentationMode();
  return <Header />;
}

describe('Header', () => {
  it('names the dataset in use', async () => {
    mockApi([['/api/v1/dataset', { json: dataset }]]);
    renderWithProviders(<Header />);

    expect(
      await screen.findByText(t.header.dataset('public.vector_store', 4, 6)),
    ).toBeInTheDocument();
  });

  it('switches the presentation mode for the projector', async () => {
    mockApi([['/api/v1/dataset', { pending: true }]]);
    renderWithProviders(<HeaderWithPresentation />);
    const button = screen.getByRole('button', { name: t.header.presentation });

    expect(button).toHaveAttribute('aria-pressed', 'false');
    await userEvent.click(button);
    expect(button).toHaveAttribute('aria-pressed', 'true');
    expect(document.documentElement.dataset.presentation).toBe('true');
    await userEvent.click(button);
    expect(document.documentElement.dataset.presentation).toBe('false');
  });
});
