import { useMutation } from '@tanstack/react-query';
import { type SubmitEvent, useId, useState } from 'react';
import { api } from '../../api/endpoints';
import type { SearchRequest, SearchResult } from '../../api/schemas';
import { ErrorMessage } from '../../components/ErrorMessage';
import { Section } from '../../components/Section';
import ui from '../../components/ui.module.css';
import { useDiagnostics } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { cx } from '../../lib/cx';
import { embeddingEnabled } from '../../lib/embedding';
import { formatNumber } from '../../lib/format';
import { useUiStore } from '../../state/uiStore';
import styles from './RagSearchSection.module.css';

const DEFAULT_TOP_K = 5;
const DEFAULT_THRESHOLD = 0.5;
const MAX_QUESTION = 2000;

/** Section 7, "Simula una ricerca RAG": the retrieval step of the L18 RagService. */
export function RagSearchSection() {
  const enabled = embeddingEnabled(useDiagnostics().data);
  return (
    <Section title={it.rag.title} info={it.rag.info} lesson={it.rag.lesson}>
      {enabled === false ? <DisabledPanel /> : <SearchPanel />}
    </Section>
  );
}

function DisabledPanel() {
  return (
    <div className={ui.warningNote}>
      <p className={styles.disabledTitle}>{it.rag.disabledTitle}</p>
      <p>{it.rag.disabledText}</p>
      <ul className={styles.variables}>
        {it.rag.disabledVariables.map((variable) => (
          <li key={variable}>
            <code>{variable}</code>
          </li>
        ))}
      </ul>
      <p>{it.rag.disabledRestart}</p>
    </div>
  );
}

function SearchPanel() {
  const [question, setQuestion] = useState('');
  const [topK, setTopK] = useState(DEFAULT_TOP_K);
  const [threshold, setThreshold] = useState(DEFAULT_THRESHOLD);
  const normalize = useUiStore((state) => state.normalize);
  const setRagOverlay = useUiStore((state) => state.setRagOverlay);
  const questionId = useId();
  const topKId = useId();
  const thresholdId = useId();

  const search = useMutation({
    mutationFn: (request: SearchRequest) => api.search(request),
    onSuccess: (result) => {
      setRagOverlay({
        datasetVersion: result.datasetVersion,
        normalized: result.normalized,
        queryPoint: result.queryPoint,
        results: result.results.map((match) => ({ id: match.id, passes: match.passesThreshold })),
      });
    },
  });

  const submit = (event: SubmitEvent<HTMLFormElement>) => {
    event.preventDefault();
    search.mutate({ question: question.trim(), topK, similarityThreshold: threshold, normalize });
  };

  return (
    <>
      <form onSubmit={submit} className={styles.form}>
        <label htmlFor={questionId} className={ui.label}>
          {it.rag.question}
        </label>
        <textarea
          id={questionId}
          className={cx(ui.input, styles.question)}
          rows={3}
          maxLength={MAX_QUESTION}
          placeholder={it.rag.questionPlaceholder}
          value={question}
          onChange={(event) => {
            setQuestion(event.target.value);
          }}
        />
        <label htmlFor={topKId} className={ui.label}>
          <code>{it.rag.topK}</code> = {topK}
        </label>
        <input
          id={topKId}
          type="range"
          min={1}
          max={20}
          step={1}
          value={topK}
          onChange={(event) => {
            setTopK(Number(event.target.value));
          }}
        />
        <label htmlFor={thresholdId} className={ui.label}>
          <code>{it.rag.threshold}</code> = {formatNumber(threshold, 2)}
        </label>
        <input
          id={thresholdId}
          type="range"
          min={0}
          max={1}
          step={0.05}
          value={threshold}
          onChange={(event) => {
            setThreshold(Number(event.target.value));
          }}
        />
        <button
          type="submit"
          className={ui.primary}
          disabled={question.trim() === '' || search.isPending}
        >
          {search.isPending ? it.rag.searching : it.rag.search}
        </button>
      </form>
      <p className={ui.muted}>{it.rag.cost}</p>
      <p className={ui.muted}>{it.rag.sameModel}</p>

      {search.isPending && (
        <p className={ui.muted} aria-busy="true">
          {it.rag.searching}
        </p>
      )}
      {search.isError && <ErrorMessage error={search.error} />}
      {search.data !== undefined && (
        <SearchResults
          result={search.data}
          onClear={() => {
            setRagOverlay(null);
          }}
        />
      )}
    </>
  );
}

function SearchResults({
  result,
  onClear,
}: {
  readonly result: SearchResult;
  readonly onClear: () => void;
}) {
  const select = useUiStore((state) => state.select);
  const focusOn = useUiStore((state) => state.focusOn);
  const passing = result.results.filter((match) => match.passesThreshold).length;

  return (
    <div className={styles.results}>
      <h3 className={ui.subheading}>{it.rag.results}</h3>
      <p className={styles.summary}>{it.rag.summary(passing, result.results.length)}</p>
      <p className={ui.muted}>{it.rag.latency(result.model, result.embeddingLatencyMs)}</p>
      <ol className={styles.list}>
        {result.results.map((match) => (
          <li
            key={match.id}
            className={cx(styles.match, !match.passesThreshold && styles.discarded)}
          >
            <div className={styles.matchHeader}>
              <span className={styles.similarity}>
                {it.chunk.similarity}:{' '}
                {match.similarity === null ? '—' : formatNumber(match.similarity)}
              </span>
              <span className={match.passesThreshold ? styles.badgePasses : styles.badgeDiscarded}>
                {match.passesThreshold ? it.rag.passes : it.rag.discarded}
              </span>
            </div>
            <button
              type="button"
              className={styles.matchBody}
              onClick={() => {
                select(match.id);
                focusOn(match.id);
              }}
            >
              <span className={ui.muted}>
                {match.rank}. {match.file} · {it.colors.pageShort(match.page)}
              </span>
              <span className={styles.preview}>{match.preview}</span>
            </button>
          </li>
        ))}
      </ol>
      <h3 className={ui.subheading}>{it.rag.sql}</h3>
      <pre className={ui.code}>{result.springAiSql}</pre>
      <p className={ui.muted}>{it.rag.sqlParameters(result.similarityThreshold, result.topK)}</p>
      <button type="button" className={ui.button} onClick={onClear}>
        {it.rag.clear}
      </button>
    </div>
  );
}
