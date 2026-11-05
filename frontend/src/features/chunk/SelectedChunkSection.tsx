import type { ChunkDetails } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { useChunk } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { formatInteger, formatNumber, formatValue } from '../../lib/format';
import { useUiStore } from '../../state/uiStore';
import { NeighborsList } from './NeighborsList';
import styles from './SelectedChunkSection.module.css';
import { VectorBarcode } from './VectorBarcode';

const FIRST_NUMBERS = 10;

/** Section 5, "Chunk selezionato": text, metadata, the vector and its real neighbors. */
export function SelectedChunkSection() {
  const selectedId = useUiStore((state) => state.selectedId);
  const chunk = useChunk(selectedId);

  return (
    <Section title={it.chunk.title} info={it.chunk.info} lesson={it.chunk.lesson}>
      {selectedId === null ? (
        <p className={ui.muted}>{it.chunk.empty}</p>
      ) : (
        <AsyncView
          state={toAsyncState(chunk)}
          onRetry={() => {
            void chunk.refetch();
          }}
        >
          {(details) => <ChunkDetailsView chunk={details} />}
        </AsyncView>
      )}
    </Section>
  );
}

function ChunkDetailsView({ chunk }: { readonly chunk: ChunkDetails }) {
  const select = useUiStore((state) => state.select);
  const focusOn = useUiStore((state) => state.focusOn);
  const setCompare = useUiStore((state) => state.setCompare);
  const metadata = Object.entries(chunk.metadata);

  return (
    <>
      <dl className={ui.facts}>
        <dt>{it.chunk.file}</dt>
        <dd>{chunk.file}</dd>
        <dt>{it.chunk.page}</dt>
        <dd>{chunk.page === null ? '—' : formatInteger(chunk.page)}</dd>
        <dt>{it.chunk.chunkIndex}</dt>
        <dd>{chunk.chunkIndex === null ? '—' : formatInteger(chunk.chunkIndex)}</dd>
      </dl>
      <div className={`${ui.row} ${styles.actions}`}>
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            focusOn(chunk.id);
          }}
        >
          {it.chunk.showInView}
        </button>
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            setCompare('A', chunk.id);
          }}
        >
          {it.chunk.useAsA}
        </button>
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            setCompare('B', chunk.id);
          }}
        >
          {it.chunk.useAsB}
        </button>
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            select(null);
          }}
        >
          {it.chunk.deselect}
        </button>
      </div>

      <h3 className={ui.subheading}>{it.chunk.text}</h3>
      <div className={styles.text}>{chunk.content}</div>

      <h3 className={ui.subheading}>{it.chunk.metadata}</h3>
      {metadata.length === 0 ? (
        <p className={ui.muted}>{it.chunk.metadataEmpty}</p>
      ) : (
        <table className={ui.table}>
          <thead>
            <tr>
              <th scope="col">{it.chunk.key}</th>
              <th scope="col">{it.chunk.value}</th>
            </tr>
          </thead>
          <tbody>
            {metadata.map(([key, value]) => (
              <tr key={key}>
                <th scope="row">{key}</th>
                <td>{formatValue(value)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      <h3 className={ui.subheading}>{it.chunk.vector}</h3>
      {chunk.embedding === null || chunk.stats === null ? (
        <p className={ui.muted}>{it.chunk.noVector}</p>
      ) : (
        <>
          <dl className={ui.facts}>
            <dt>{it.chunk.stats.dimensions}</dt>
            <dd>{formatInteger(chunk.stats.dimensions)}</dd>
            <dt>{it.chunk.stats.norm}</dt>
            <dd>{formatNumber(chunk.stats.norm, 4)}</dd>
            <dt>{it.chunk.stats.min}</dt>
            <dd>{formatNumber(chunk.stats.min, 4)}</dd>
            <dt>{it.chunk.stats.max}</dt>
            <dd>{formatNumber(chunk.stats.max, 4)}</dd>
            <dt>{it.chunk.stats.mean}</dt>
            <dd>{formatNumber(chunk.stats.mean, 5)}</dd>
          </dl>
          <p className={ui.label}>{it.chunk.firstNumbers}</p>
          <p className={styles.numbers}>
            [
            {chunk.embedding
              .slice(0, FIRST_NUMBERS)
              .map((value) => formatNumber(value, 4))
              .join('; ')}
            ; …]
          </p>
          <p className={ui.label}>{it.chunk.barcode}</p>
          <VectorBarcode values={chunk.embedding} />
        </>
      )}

      <h3 className={ui.subheading}>{it.chunk.neighbors}</h3>
      <NeighborsList id={chunk.id} />
    </>
  );
}
