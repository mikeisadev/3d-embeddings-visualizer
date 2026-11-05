import { useMutation } from '@tanstack/react-query';
import { api } from '../../api/endpoints';
import { ErrorMessage } from '../../components/ErrorMessage';
import ui from '../../components/ui.module.css';
import { useDataset } from '../../hooks/queries';
import { it } from '../../i18n/it';
import { describeCode } from '../../lib/errorMessages';

/** "Prova il modello": one real call to the embedding provider, only when the user asks. */
export function EmbeddingLiveTest() {
  const datasetDimensions = useDataset().data?.dimensions ?? null;
  const test = useMutation({ mutationFn: () => api.embeddingTest() });
  const mismatch =
    test.data !== undefined &&
    datasetDimensions !== null &&
    test.data.dimensions !== datasetDimensions;

  return (
    <div>
      <button
        type="button"
        className={ui.button}
        disabled={test.isPending}
        onClick={() => {
          test.mutate();
        }}
      >
        {test.isPending ? it.status.liveTestRunning : it.status.liveTest}
      </button>
      <p className={ui.muted}>{it.status.liveTestCost}</p>
      {test.isError && <ErrorMessage error={test.error} />}
      {test.data !== undefined && (
        <p>{it.status.liveTestOk(test.data.model, test.data.dimensions, test.data.latencyMs)}</p>
      )}
      {mismatch && (
        <p className={ui.warningNote}>
          {describeCode('DIMENSION_MISMATCH').message} {describeCode('DIMENSION_MISMATCH').hint}
        </p>
      )}
    </div>
  );
}
