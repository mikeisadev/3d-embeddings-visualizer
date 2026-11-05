import { QueryCache, QueryClient } from '@tanstack/react-query';
import { isApiError, isEnvironmentError } from '../api/ApiError';
import { retryDelay, shouldRetry } from '../api/retry';
import { queryKeys } from '../hooks/queries';

/**
 * One QueryClient for the app. Global reactions to errors:
 * - DATASET_CHANGED: the summary is refreshed, which reloads the points;
 * - an environment error (database or backend down): the diagnostics are refreshed, so the
 *   "Stato" section explains the problem and starts polling until it is solved.
 */
export function createQueryClient(): QueryClient {
  const client: QueryClient = new QueryClient({
    queryCache: new QueryCache({
      onError: (error, query) => {
        if (query.queryKey[0] === queryKeys.diagnostics[0]) {
          return;
        }
        if (isApiError(error) && error.code === 'DATASET_CHANGED') {
          void client.invalidateQueries({ queryKey: queryKeys.dataset });
        }
        if (isEnvironmentError(error)) {
          void client.invalidateQueries({ queryKey: queryKeys.diagnostics });
        }
      },
    }),
    defaultOptions: {
      queries: { retry: shouldRetry, retryDelay, refetchOnWindowFocus: false },
      mutations: { retry: false },
    },
  });
  return client;
}
