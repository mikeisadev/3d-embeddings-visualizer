import type { Dataset, DiagnosticCheck, Diagnostics, Metric } from '../../api/schemas';
import { AsyncView } from '../../components/AsyncView';
import { Section } from '../../components/Section';
import { StatusIcon } from '../../components/StatusIcon';
import ui from '../../components/ui.module.css';
import { useDataset, useDiagnostics } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { toAsyncState } from '../../lib/asyncState';
import { cx } from '../../lib/cx';
import { describeCode } from '../../lib/errorMessages';
import { formatInteger } from '../../lib/format';
import { EmbeddingLiveTest } from './EmbeddingLiveTest';
import styles from './StatusSection.module.css';

/** pgvector operators: SQL symbols, shown as code. */
const METRIC_OPERATORS: Record<Metric, string> = { COSINE: '<=>', L2: '<->', INNER_PRODUCT: '<#>' };

/** Section 1, "Stato": the diagnostics checklist and the dataset summary. */
export function StatusSection() {
  const diagnostics = useDiagnostics();
  const dataset = useDataset();

  return (
    <Section title={it.status.title} info={it.status.info} lesson={it.status.lesson}>
      <h3 className={ui.subheading}>{it.status.checklist}</h3>
      <AsyncView
        state={toAsyncState(diagnostics)}
        onRetry={() => {
          void diagnostics.refetch();
        }}
      >
        {(report) => (
          <DiagnosticsList
            report={report}
            refreshing={diagnostics.isFetching}
            onRetry={() => {
              void diagnostics.refetch();
            }}
          />
        )}
      </AsyncView>

      <h3 className={ui.subheading}>{it.status.summaryTitle}</h3>
      <AsyncView
        state={toAsyncState(dataset)}
        onRetry={() => {
          void dataset.refetch();
        }}
      >
        {(summary) => <DatasetSummaryView summary={summary} />}
      </AsyncView>
    </Section>
  );
}

interface DiagnosticsListProps {
  readonly report: Diagnostics;
  readonly refreshing: boolean;
  readonly onRetry: () => void;
}

function DiagnosticsList({ report, refreshing, onRetry }: DiagnosticsListProps) {
  return (
    <>
      <p className={cx(styles.overall, styles[report.status])}>
        <StatusIcon status={report.status} /> {it.status.overall[report.status]}
      </p>
      <ul className={styles.checks}>
        {report.checks.map((check) => (
          <CheckItem key={check.id} check={check} />
        ))}
      </ul>
      <button type="button" className={ui.button} onClick={onRetry} disabled={refreshing}>
        {refreshing ? it.common.loading : it.common.retry}
      </button>
    </>
  );
}

function CheckItem({ check }: { readonly check: DiagnosticCheck }) {
  const title = it.status.checks[check.id] ?? check.id;
  const explained = check.code !== null && (check.status === 'FAIL' || check.status === 'WARN');
  const described = explained && check.code !== null ? describeCode(check.code) : null;
  return (
    <li className={cx(styles.check, check.status === 'SKIPPED' && styles.skipped)}>
      <StatusIcon status={check.status} />
      <div className={styles.checkBody}>
        <span className={styles.checkTitle}>{title}</span>
        {described !== null && (
          <>
            <span className={styles.message}>{described.message}</span>
            <span className={styles.hint}>{described.hint}</span>
          </>
        )}
        <span className={styles.detail}>{check.detail}</span>
        {check.id === 'embedding.client' && check.facts.enabled === true && <EmbeddingLiveTest />}
      </div>
    </li>
  );
}

function DatasetSummaryView({ summary }: { readonly summary: Dataset }) {
  return (
    <>
      <dl className={ui.facts}>
        <dt>{it.status.chunks}</dt>
        <dd>{formatInteger(summary.rowsWithEmbedding)}</dd>
        <dt>{it.status.files}</dt>
        <dd>{formatInteger(summary.files.length)}</dd>
        <dt>{it.status.dimensions}</dt>
        <dd>{summary.dimensions === null ? '—' : formatInteger(summary.dimensions)}</dd>
        <dt>{it.status.metric}</dt>
        <dd>
          {it.status.metricName[summary.distanceMetric.metric]}{' '}
          <code>{METRIC_OPERATORS[summary.distanceMetric.metric]}</code>,{' '}
          {it.status.metricSource[summary.distanceMetric.source]}
        </dd>
        <dt>{it.status.index}</dt>
        <dd>
          {summary.index === null
            ? it.status.noIndex
            : `${summary.index.name} (${summary.index.type}, ${summary.index.operatorClass})`}
        </dd>
      </dl>
      {summary.rowCount === 0 && <p className={ui.warningNote}>{it.status.emptyTable}</p>}
      {summary.sampled && (
        <p className={ui.warningNote}>
          {it.status.sampled(summary.loadedRows, summary.rowsWithEmbedding)}
        </p>
      )}
      {summary.warnings.length > 0 && (
        <>
          <h4 className={ui.subheading}>{it.status.datasetWarnings}</h4>
          <ul className={styles.warnings}>
            {summary.warnings.map((warning) => {
              const described = describeCode(warning.code);
              return (
                <li key={warning.code}>
                  <strong>{described.message}</strong> ({it.status.warningCount(warning.count)}).{' '}
                  {described.hint}
                </li>
              );
            })}
          </ul>
        </>
      )}
    </>
  );
}
