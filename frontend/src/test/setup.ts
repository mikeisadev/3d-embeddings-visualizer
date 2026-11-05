import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach, vi } from 'vitest';

afterEach(() => {
  cleanup();
});

// jsdom has no canvas: components draw only when a 2D context exists.
HTMLCanvasElement.prototype.getContext = vi.fn(() => null);
