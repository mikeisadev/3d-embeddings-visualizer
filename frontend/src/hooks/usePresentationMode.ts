import { useEffect } from 'react';
import { useUiStore } from '../state/uiStore';

/**
 * Mirrors the presentation mode on `<html data-presentation>`, where the CSS reads it
 * (bigger text and a wider sidebar for the projector).
 */
export function usePresentationMode(): void {
  const presentation = useUiStore((state) => state.presentation);
  useEffect(() => {
    const root = document.documentElement;
    root.dataset.presentation = String(presentation);
    return () => {
      delete root.dataset.presentation;
    };
  }, [presentation]);
}
