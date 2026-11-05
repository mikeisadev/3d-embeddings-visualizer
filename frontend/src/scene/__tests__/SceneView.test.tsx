import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { it as t } from '../../i18n/it';
import { SceneErrorBoundary } from '../SceneErrorBoundary';
import SceneView from '../SceneView';

describe('SceneView', () => {
  it('explains that WebGL is missing and points to the accessible list', () => {
    render(<SceneView />);

    expect(screen.getByText(t.scene.noWebgl)).toBeInTheDocument();
    expect(screen.getByText(t.scene.noWebglHint)).toBeInTheDocument();
  });
});

describe('SceneErrorBoundary', () => {
  it('keeps a crash inside the view and offers a reload', async () => {
    vi.spyOn(console, 'error').mockImplementation(() => undefined);
    let crash = true;
    function Fragile() {
      if (crash) {
        throw new Error('WebGL exploded');
      }
      return <p>vista ok</p>;
    }
    render(
      <SceneErrorBoundary>
        <Fragile />
      </SceneErrorBoundary>,
    );

    expect(screen.getByText(t.scene.crashed)).toBeInTheDocument();
    crash = false;
    await userEvent.click(screen.getByRole('button', { name: t.scene.reload }));
    expect(screen.getByText('vista ok')).toBeInTheDocument();
  });
});
