/** The four states every panel renders explicitly. */
export type AsyncState<T> =
  | { readonly status: 'loading' }
  | { readonly status: 'error'; readonly error: unknown }
  | { readonly status: 'empty' }
  | { readonly status: 'success'; readonly data: T };

interface QueryLike<T> {
  readonly status: 'pending' | 'error' | 'success';
  readonly data: T | undefined;
  readonly error: unknown;
}

/** Converts a TanStack query into an {@link AsyncState}; previous data stays visible while refetching. */
export function toAsyncState<T>(
  query: QueryLike<T>,
  isEmpty: (data: T) => boolean = () => false,
): AsyncState<T> {
  if (query.data !== undefined && query.status !== 'error') {
    return isEmpty(query.data) ? { status: 'empty' } : { status: 'success', data: query.data };
  }
  if (query.status === 'error') {
    return { status: 'error', error: query.error };
  }
  return { status: 'loading' };
}
