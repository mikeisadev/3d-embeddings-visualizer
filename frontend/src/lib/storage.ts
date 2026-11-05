/**
 * Small preferences in localStorage. Storage can be missing or throw (private windows,
 * blocked site data): then the preference is simply not remembered.
 */

export function readFlag(key: string): boolean {
  try {
    return window.localStorage.getItem(key) === 'true';
  } catch {
    return false;
  }
}

export function writeFlag(key: string, value: boolean): void {
  try {
    window.localStorage.setItem(key, String(value));
  } catch {
    // Not remembering a preference is harmless.
  }
}

export const GUIDE_SEEN_KEY = 'embedding-explorer.guide-seen';
