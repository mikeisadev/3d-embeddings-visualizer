import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { render } from '@testing-library/react';
import type { ReactElement } from 'react';
import { useUiStore } from '../state/uiStore';

const initialUiState = useUiStore.getState();

/** Renders with a fresh QueryClient (no retries, no cache between tests) and a clean UI store. */
export function renderWithProviders(ui: ReactElement) {
  useUiStore.setState(initialUiState, true);
  const client = new QueryClient({
    defaultOptions: { queries: { retry: false, gcTime: Infinity }, mutations: { retry: false } },
  });
  return { client, ...render(<QueryClientProvider client={client}>{ui}</QueryClientProvider>) };
}

/** Element {@code index} of a list (fails the test when it does not exist). */
export function nth<T>(items: readonly T[], index: number): T {
  const item = items[index];
  if (item === undefined) {
    throw new Error(`Expected at least ${index + 1} elements, found ${items.length}`);
  }
  return item;
}

export function firstOf<T>(items: readonly T[]): T {
  return nth(items, 0);
}
