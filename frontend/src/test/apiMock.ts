import { vi } from 'vitest';

/** What a mocked endpoint answers. */
export type MockReply =
  | { readonly json: unknown; readonly status?: number }
  | { readonly text: string; readonly status: number }
  | { readonly pending: true }
  | { readonly networkError: true };

type Matcher = string | RegExp;

/**
 * Replaces fetch with a tiny router: the first route whose matcher matches the path (and
 * query) answers. Unmatched requests fail loudly, so a test never hits the network.
 */
export function mockApi(routes: readonly (readonly [Matcher, MockReply])[]) {
  const fetchMock = vi.fn((input: RequestInfo | URL, _init?: RequestInit) => {
    const url = new URL(input instanceof Request ? input.url : String(input), 'http://localhost');
    const target = url.pathname + url.search;
    const route = routes.find(([matcher]) =>
      typeof matcher === 'string' ? url.pathname === matcher : matcher.test(target),
    );
    if (route === undefined) {
      return Promise.reject(new Error(`Unexpected request in test: ${target}`));
    }
    return reply(route[1]);
  });
  vi.stubGlobal('fetch', fetchMock);
  return fetchMock;
}

function reply(mock: MockReply): Promise<Response> {
  if ('pending' in mock) {
    return new Promise<Response>(() => undefined);
  }
  if ('networkError' in mock) {
    return Promise.reject(new TypeError('Failed to fetch'));
  }
  if ('text' in mock) {
    return Promise.resolve(
      new Response(mock.text, { status: mock.status, headers: { 'Content-Type': 'text/html' } }),
    );
  }
  const status = mock.status ?? 200;
  return Promise.resolve(
    new Response(JSON.stringify(mock.json), {
      status,
      headers: {
        'Content-Type': status >= 400 ? 'application/problem+json' : 'application/json',
        'X-Request-Id': 'req-header-1',
      },
    }),
  );
}
