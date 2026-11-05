import { Component, type ErrorInfo, type ReactNode } from 'react';
import ui from '../components/ui.module.css';
import { it } from '../i18n/it';
import styles from './SceneView.module.css';

interface Props {
  readonly children: ReactNode;
}

interface State {
  readonly failed: boolean;
}

/**
 * Keeps a crash of the 3D view (a WebGL or three.js error) inside the view: the sidebar stays
 * usable, and "Ricarica la vista" mounts a fresh view.
 */
export class SceneErrorBoundary extends Component<Props, State> {
  override state: State = { failed: false };

  static getDerivedStateFromError(): State {
    return { failed: true };
  }

  override componentDidCatch(error: unknown, info: ErrorInfo): void {
    console.error('The 3D view crashed', error, info.componentStack);
  }

  override render(): ReactNode {
    if (!this.state.failed) {
      return this.props.children;
    }
    return (
      <div className={styles.message} role="alert">
        <p>{it.scene.crashed}</p>
        <button
          type="button"
          className={ui.primary}
          onClick={() => {
            this.setState({ failed: false });
          }}
        >
          {it.scene.reload}
        </button>
      </div>
    );
  }
}
