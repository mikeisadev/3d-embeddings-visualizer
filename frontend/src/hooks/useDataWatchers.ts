import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { useUiStore } from '../state/uiStore';
import { queryKeys, useDataset, useDiagnostics, usePoints } from './queries';

const RELOADED_NOTICE_MS = 5000;

/**
 * Keeps the data fresh without user action:
 * - when the diagnostics recover (database back), every data query is reloaded;
 * - when a new datasetVersion arrives (a PDF was indexed), the points reload and the user is
 *   told why;
 * - when the points belong to another version than the summary, the summary is refreshed.
 */
export function useDataWatchers(): void {
  const queryClient = useQueryClient();
  const setNotice = useUiStore((state) => state.setNotice);
  const diagnosticsStatus = useDiagnostics().data?.status;
  const version = useDataset().data?.datasetVersion;
  const points = usePoints();
  const pointsVersion = points.data?.datasetVersion;

  const previousStatus = useRef(diagnosticsStatus);
  useEffect(() => {
    const recovered =
      previousStatus.current === 'FAIL' &&
      diagnosticsStatus !== undefined &&
      diagnosticsStatus !== 'FAIL';
    previousStatus.current = diagnosticsStatus;
    if (recovered) {
      void queryClient.invalidateQueries({
        predicate: (query) => query.queryKey[0] !== queryKeys.diagnostics[0],
      });
    }
  }, [diagnosticsStatus, queryClient]);

  const previousVersion = useRef(version);
  useEffect(() => {
    if (
      previousVersion.current !== undefined &&
      version !== undefined &&
      version !== previousVersion.current
    ) {
      setNotice('datasetChanged');
    }
    previousVersion.current = version;
  }, [version, setNotice]);

  useEffect(() => {
    if (pointsVersion === undefined || version === undefined || points.isPlaceholderData) {
      return;
    }
    if (pointsVersion !== version) {
      void queryClient.invalidateQueries({ queryKey: queryKeys.dataset });
      return;
    }
    if (useUiStore.getState().notice === 'datasetChanged') {
      setNotice('datasetReloaded');
      const timer = setTimeout(() => {
        setNotice(null);
      }, RELOADED_NOTICE_MS);
      return () => {
        clearTimeout(timer);
      };
    }
    return undefined;
  }, [pointsVersion, version, points.isPlaceholderData, queryClient, setNotice]);
}
