import { useDataset } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { useUiStore } from '../../state/uiStore';
import styles from './Header.module.css';

/** App name, the dataset in use (schema.table, chunks, dimensions) and the presentation mode. */
export function Header() {
  const dataset = useDataset();
  const presentation = useUiStore((state) => state.presentation);
  const setPresentation = useUiStore((state) => state.setPresentation);
  const summary = dataset.data;
  let datasetText: string = it.header.loadingDataset;
  if (summary !== undefined) {
    datasetText = it.header.dataset(
      `${summary.schema}.${summary.table}`,
      summary.rowsWithEmbedding,
      summary.dimensions,
    );
  } else if (dataset.isError) {
    datasetText = it.header.datasetUnavailable;
  }

  return (
    <header className={styles.header}>
      <div className={styles.brand}>
        <span className={styles.logo} aria-hidden="true" />
        <h1 className={styles.name}>{it.app.name}</h1>
        <span className={styles.tagline}>{it.app.tagline}</span>
      </div>
      <div className={styles.side}>
        <p className={styles.dataset} aria-live="polite">
          {datasetText}
        </p>
        <button
          type="button"
          className={styles.presentation}
          aria-pressed={presentation}
          onClick={() => {
            setPresentation(!presentation);
          }}
        >
          {it.header.presentation}
        </button>
      </div>
    </header>
  );
}
