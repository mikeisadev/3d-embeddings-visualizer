import { useEffect, useEffectEvent, useMemo, useRef, useState } from 'react';
import { ErrorMessage } from '../components/ErrorMessage';
import ui from '../components/ui.module.css';
import { useNeighbors, usePointCloudView, usePoints } from '../hooks/queries';
import { useColorMapping, usePointStates } from '../hooks/usePointStates';
import { it } from '../i18n/it';
import { useUiStore } from '../state/uiStore';
import { applyTransform } from '../lib/displayTransform';
import {
  type PointAppearance,
  type QueryLink,
  type QueryMarker,
  SceneController,
} from './SceneController';
import { computeEmphasis, focusSet } from './sceneModel';
import styles from './SceneView.module.css';
import { isWebGl2Available } from './webgl';

interface Hover {
  readonly index: number;
  readonly x: number;
  readonly y: number;
}

/** The 3D view: a React shell around {@link SceneController}, which owns the three.js objects. */
export default function SceneView() {
  const [webglAvailable] = useState(isWebGl2Available);
  const [generation, setGeneration] = useState(0);

  if (!webglAvailable) {
    return (
      <div className={styles.message} role="alert">
        <p>{it.scene.noWebgl}</p>
        <p>{it.scene.noWebglHint}</p>
      </div>
    );
  }
  return (
    <SceneCanvas
      key={generation}
      onReload={() => {
        setGeneration(generation + 1);
      }}
    />
  );
}

function SceneCanvas({ onReload }: { readonly onReload: () => void }) {
  const containerRef = useRef<HTMLDivElement>(null);
  const controllerRef = useRef<SceneController | null>(null);
  const [hover, setHover] = useState<Hover | null>(null);
  const [contextLost, setContextLost] = useState(false);

  const points = usePoints();
  const view = usePointCloudView();
  const mapping = useColorMapping();
  const states = usePointStates();
  const selectedId = useUiStore((state) => state.selectedId);
  const compareA = useUiStore((state) => state.compareA);
  const compareB = useUiStore((state) => state.compareB);
  const pointSize = useUiStore((state) => state.pointSize);
  const showGrid = useUiStore((state) => state.showGrid);
  const setShowGrid = useUiStore((state) => state.setShowGrid);
  const focusRequest = useUiStore((state) => state.focusRequest);
  const select = useUiStore((state) => state.select);
  const ragOverlay = useUiStore((state) => state.ragOverlay);
  const neighbors = useNeighbors(selectedId);

  const indexOf = (id: string | null) =>
    id === null || view === null ? null : (view.indexById.get(id) ?? null);
  const selectedIndex = indexOf(selectedId);
  const neighborData = neighbors.data;
  const neighborIndices = useMemo(() => {
    if (view === null || neighborData?.id !== selectedId) {
      return [];
    }
    return neighborData.neighbors
      .map((neighbor) => view.indexById.get(neighbor.id))
      .filter((index): index is number => index !== undefined);
  }, [neighborData, selectedId, view]);

  /** The last RAG search, only when it belongs to the points on screen (same data, same projection). */
  const rag = useMemo(() => {
    if (ragOverlay === null || view === null) {
      return null;
    }
    const sameData = ragOverlay.datasetVersion === view.data.datasetVersion;
    if (!sameData || ragOverlay.normalized !== view.data.projection.normalized) {
      return null;
    }
    const links: QueryLink[] = ragOverlay.results.flatMap((result) => {
      const index = view.indexById.get(result.id);
      return index === undefined ? [] : [{ index, passes: result.passes }];
    });
    const marker: QueryMarker = {
      position: applyTransform(view.transform, ragOverlay.queryPoint),
      label: it.rag.questionMarker,
      links,
    };
    return { marker, indices: links.map((link) => link.index) };
  }, [ragOverlay, view]);

  const appearance = useMemo<PointAppearance | null>(() => {
    if (mapping === null || states === null) {
      return null;
    }
    const focus = focusSet({
      selected: selectedIndex,
      neighbors: neighborIndices,
      ragResults: rag?.indices ?? null,
      textMatches: states.textMatches,
    });
    return {
      colors: mapping.colors,
      visible: states.visible,
      emphasized: computeEmphasis(states.inFilter, focus),
      pointSize,
    };
  }, [mapping, states, selectedIndex, neighborIndices, rag, pointSize]);

  const onSelectPoint = useEffectEvent((index: number | null) => {
    select(index === null || view === null ? null : (view.data.points[index]?.id ?? null));
  });
  const onHoverPoint = useEffectEvent((index: number | null, clientX: number, clientY: number) => {
    const box = containerRef.current?.getBoundingClientRect();
    setHover(
      index === null || box === undefined
        ? null
        : { index, x: clientX - box.left, y: clientY - box.top },
    );
  });
  const onFocusRequest = useEffectEvent((id: string) => {
    const index = view?.indexById.get(id);
    if (index !== undefined) {
      controllerRef.current?.focusPoint(index);
    }
  });

  useEffect(() => {
    const container = containerRef.current;
    if (container === null) {
      return;
    }
    const controller = new SceneController(container, {
      onHover: (index, x, y) => {
        onHoverPoint(index, x, y);
      },
      onSelect: (index) => {
        onSelectPoint(index);
      },
      onContextLost: () => {
        setContextLost(true);
      },
    });
    controllerRef.current = controller;
    return () => {
      controller.dispose();
      controllerRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (view !== null) {
      controllerRef.current?.setPoints(view.positions);
    }
  }, [view]);

  useEffect(() => {
    if (appearance !== null) {
      controllerRef.current?.setAppearance(appearance);
    }
  }, [appearance]);

  useEffect(() => {
    controllerRef.current?.setSelection(selectedIndex, neighborIndices);
  }, [selectedIndex, neighborIndices]);

  const compareIndexA = indexOf(compareA);
  const compareIndexB = indexOf(compareB);
  useEffect(() => {
    controllerRef.current?.setComparison(compareIndexA, compareIndexB, [
      it.compare.slotA,
      it.compare.slotB,
    ]);
  }, [compareIndexA, compareIndexB]);

  useEffect(() => {
    controllerRef.current?.setQueryMarker(rag?.marker ?? null);
  }, [rag]);

  // Frame each new question once; reloading the points keeps the camera where it is.
  const focusQuestion = useEffectEvent(() => {
    if (rag !== null) {
      controllerRef.current?.focusPosition(rag.marker.position);
    }
  });
  useEffect(() => {
    if (ragOverlay !== null) {
      focusQuestion();
    }
  }, [ragOverlay]);

  const ratios = view?.data.projection.explainedVarianceRatio;
  const axisLabels = useMemo(
    () =>
      [0, 1, 2].map((axis) => it.scene.axis(axis, ratios?.[axis] ?? null)) as [
        string,
        string,
        string,
      ],
    [ratios],
  );
  useEffect(() => {
    controllerRef.current?.setAxisLabels(axisLabels);
  }, [axisLabels]);

  useEffect(() => {
    controllerRef.current?.setGridVisible(showGrid);
  }, [showGrid]);

  useEffect(() => {
    if (focusRequest !== null) {
      onFocusRequest(focusRequest.id);
    }
  }, [focusRequest]);

  const hovered = hover === null ? undefined : view?.data.points[hover.index];

  return (
    <div className={styles.scene}>
      <div ref={containerRef} className={styles.canvas} />

      <div className={styles.toolbar} role="toolbar" aria-label={it.scene.toolbar}>
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            controllerRef.current?.resetView();
          }}
        >
          {it.scene.resetView}
        </button>
        <label className={styles.toggle}>
          <input
            type="checkbox"
            checked={showGrid}
            onChange={(event) => {
              setShowGrid(event.target.checked);
            }}
          />
          {it.scene.grid}
        </label>
      </div>

      <p className={styles.badge}>{it.scene.projectionBadge}</p>
      <p className={styles.help}>{it.scene.help}</p>

      {points.isPending && points.fetchStatus !== 'idle' && (
        <div className={styles.overlay} aria-busy="true">
          {it.scene.loadingPoints}
        </div>
      )}
      {points.isPlaceholderData && <p className={styles.reloading}>{it.scene.reloadingPoints}</p>}
      {points.isError && (
        <div className={styles.overlay}>
          <ErrorMessage
            error={points.error}
            onRetry={() => {
              void points.refetch();
            }}
          />
        </div>
      )}
      {points.data?.points.length === 0 && <div className={styles.overlay}>{it.scene.noData}</div>}

      {hovered !== undefined && hover !== null && (
        <div
          className={styles.tooltip}
          style={{ left: hover.x + 14, top: hover.y + 14 }}
          role="tooltip"
        >
          <strong>{hovered.file}</strong>
          <span className={styles.tooltipPage}>{it.scene.tooltipPage(hovered.page)}</span>
          <span>{hovered.preview}</span>
        </div>
      )}

      {contextLost && (
        <div className={styles.overlay} role="alert">
          <p>{it.scene.contextLost}</p>
          <button type="button" className={ui.primary} onClick={onReload}>
            {it.scene.reload}
          </button>
        </div>
      )}
    </div>
  );
}
