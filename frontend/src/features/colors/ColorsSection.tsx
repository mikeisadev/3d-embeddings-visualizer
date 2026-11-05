import { useId } from 'react';
import type { Dataset } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { MIN_TEXT_QUERY, useDataset, usePointCloudView, usePoints } from '../../hooks/queries';
import { type PointStates, useColorMapping, usePointStates } from '../../hooks/usePointStates';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { type ColorKey, colorOptions } from '../../lib/colorBy';
import { isFilterActive } from '../../lib/filters';
import { formatInteger } from '../../lib/format';
import { useUiStore } from '../../state/uiStore';
import { ChunkList } from './ChunkList';
import styles from './ColorsSection.module.css';
import { Legend } from './Legend';

/** Section 4, "Colori e filtri". */
export function ColorsSection() {
  const points = usePoints();
  const dataset = useDataset();
  const view = usePointCloudView();
  const mapping = useColorMapping();
  const states = usePointStates();

  return (
    <Section title={it.colors.title} info={it.colors.info} lesson={it.colors.lesson}>
      <AsyncView
        state={toAsyncState(points, (cloud) => cloud.points.length === 0)}
        empty={it.scene.noData}
        onRetry={() => {
          void points.refetch();
        }}
      >
        {() =>
          view !== null && mapping !== null && states !== null && dataset.data !== undefined ? (
            <>
              <ColorBySelect dataset={dataset.data} />
              <h3 className={ui.subheading}>{it.colors.legend}</h3>
              <Legend legend={mapping.legend} />
              <h3 className={ui.subheading}>{it.colors.filters}</h3>
              <Filters dataset={dataset.data} states={states} />
              <h3 className={ui.subheading}>{it.colors.list}</h3>
              <ChunkList points={view.data.points} states={states} />
            </>
          ) : (
            <p className={ui.muted}>{it.common.loading}</p>
          )
        }
      </AsyncView>
    </Section>
  );
}

function ColorBySelect({ dataset }: { readonly dataset: Dataset }) {
  const colorBy = useUiStore((state) => state.colorBy);
  const setColorBy = useUiStore((state) => state.setColorBy);
  const selectId = useId();
  const options = colorOptions(dataset.metadataKeys);
  const fields = options.filter((option) => option.group === 'field');
  const metadata = options.filter((option) => option.group === 'metadata');

  return (
    <div>
      <label htmlFor={selectId} className={ui.label}>
        {it.colors.colorBy}
      </label>
      <select
        id={selectId}
        className={ui.input}
        value={colorBy}
        onChange={(event) => {
          setColorBy(event.target.value as ColorKey);
        }}
      >
        {fields.map((option) => (
          <option key={option.key} value={option.key}>
            {option.label}
          </option>
        ))}
        {metadata.length > 0 && (
          <optgroup label={it.colors.metadataGroup}>
            {metadata.map((option) => (
              <option key={option.key} value={option.key}>
                {option.label}
              </option>
            ))}
          </optgroup>
        )}
      </select>
    </div>
  );
}

function Filters({ dataset, states }: { readonly dataset: Dataset; readonly states: PointStates }) {
  const filters = useUiStore((state) => state.filters);
  const textQuery = useUiStore((state) => state.textQuery);
  const setFileFilter = useUiStore((state) => state.setFileFilter);
  const setPageRange = useUiStore((state) => state.setPageRange);
  const setTextQuery = useUiStore((state) => state.setTextQuery);
  const resetFilters = useUiStore((state) => state.resetFilters);
  const fromId = useId();
  const toId = useId();
  const searchId = useId();
  const hintId = useId();
  const files = dataset.files.map((entry) => entry.file);
  const selectedFiles = filters.files ?? files;

  const toggleFile = (file: string, checked: boolean) => {
    const next = checked
      ? [...selectedFiles, file]
      : selectedFiles.filter((other) => other !== file);
    setFileFilter(next.length === files.length ? null : next);
  };
  const parsePage = (value: string) =>
    value.trim() === '' ? null : Math.max(0, Math.trunc(Number(value)));

  return (
    <div className={styles.filters}>
      <fieldset className={styles.fieldset}>
        <legend className={ui.label}>{it.colors.fileFilter}</legend>
        <ul className={styles.files}>
          {dataset.files.map((entry) => (
            <li key={entry.file}>
              <label className={styles.fileOption}>
                <input
                  type="checkbox"
                  checked={selectedFiles.includes(entry.file)}
                  onChange={(event) => {
                    toggleFile(entry.file, event.target.checked);
                  }}
                />
                <span className={styles.fileName}>{entry.file}</span>
                <span className={ui.muted}>{formatInteger(entry.chunks)}</span>
              </label>
            </li>
          ))}
        </ul>
      </fieldset>

      <fieldset className={styles.fieldset}>
        <legend className={ui.label}>{it.colors.pageRange}</legend>
        <div className={ui.row}>
          <label htmlFor={fromId}>{it.colors.pageFrom}</label>
          <input
            id={fromId}
            className={styles.pageInput}
            type="number"
            min={0}
            value={filters.pages.from ?? ''}
            onChange={(event) => {
              setPageRange({ ...filters.pages, from: parsePage(event.target.value) });
            }}
          />
          <label htmlFor={toId}>{it.colors.pageTo}</label>
          <input
            id={toId}
            className={styles.pageInput}
            type="number"
            min={0}
            value={filters.pages.to ?? ''}
            onChange={(event) => {
              setPageRange({ ...filters.pages, to: parsePage(event.target.value) });
            }}
          />
        </div>
      </fieldset>

      <div>
        <label htmlFor={searchId} className={ui.label}>
          {it.colors.textSearch}
        </label>
        <input
          id={searchId}
          className={ui.input}
          type="search"
          maxLength={200}
          placeholder={it.colors.textSearchPlaceholder}
          aria-describedby={hintId}
          value={textQuery}
          onChange={(event) => {
            setTextQuery(event.target.value);
          }}
        />
        <p id={hintId} className={ui.muted} aria-live="polite">
          {textSearchStatus(textQuery, states)}
        </p>
        {states.textSearch.error !== null && <ErrorMessage error={states.textSearch.error} />}
      </div>

      {(isFilterActive(filters) || textQuery !== '') && (
        <button type="button" className={ui.button} onClick={resetFilters}>
          {it.colors.resetFilters}
        </button>
      )}
    </div>
  );
}

function textSearchStatus(textQuery: string, states: PointStates): string {
  if (textQuery.trim().length < MIN_TEXT_QUERY) {
    return it.colors.textSearchHint;
  }
  if (states.textSearch.loading || states.textMatches === null) {
    return it.common.loading;
  }
  return it.colors.textMatches(states.textSearch.count, states.textSearch.truncated);
}
