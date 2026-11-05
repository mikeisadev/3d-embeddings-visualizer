import { useDataset } from '../../hooks/queries';
import { it } from '../../i18n/it';
import styles from './Header.module.css';

/** App name and the dataset in use (schema.table, chunks, dimensions). */
export function Header() {
  const dataset = useDataset();
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
      <p className={styles.dataset} aria-live="polite">
        {datasetText}
      </p>
    </header>
  );
}
