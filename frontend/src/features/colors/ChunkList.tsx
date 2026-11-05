import { useState } from 'react';
import type { Point } from '../../api/schemas';
import ui from '../../components/ui.module.css';
import type { PointStates } from '../../hooks/usePointStates';
import { it } from '../../i18n/it';
import { cx } from '../../lib/cx';
import { useUiStore } from '../../state/uiStore';
import styles from './ChunkList.module.css';

const PAGE_SIZE = 100;

interface ChunkListProps {
  readonly points: readonly Point[];
  readonly states: PointStates;
}

/**
 * The chunks currently shown, in reading order: the accessible alternative to the canvas
 * (keyboard and screen readers can select any chunk from here).
 */
export function ChunkList({ points, states }: ChunkListProps) {
  const [limit, setLimit] = useState(PAGE_SIZE);
  const selectedId = useUiStore((state) => state.selectedId);
  const select = useUiStore((state) => state.select);
  const focusOn = useUiStore((state) => state.focusOn);

  const shown = points.filter(
    (_, index) =>
      states.visible[index] === 1 &&
      states.inFilter[index] === true &&
      (states.textMatches === null || states.textMatches.has(index)),
  );

  if (shown.length === 0) {
    return <p className={ui.muted}>{it.colors.listEmpty}</p>;
  }

  return (
    <>
      <p className={ui.muted}>{it.colors.listCount(Math.min(limit, shown.length), shown.length)}</p>
      <ul className={styles.list}>
        {shown.slice(0, limit).map((point) => (
          <li key={point.id}>
            <button
              type="button"
              className={cx(styles.item, point.id === selectedId && styles.selected)}
              aria-current={point.id === selectedId ? 'true' : undefined}
              onClick={() => {
                select(point.id);
                focusOn(point.id);
              }}
            >
              <span className={styles.meta}>
                {point.file} · {it.colors.pageShort(point.page)}
              </span>
              <span className={styles.preview}>{point.preview}</span>
            </button>
          </li>
        ))}
      </ul>
      {shown.length > limit && (
        <button
          type="button"
          className={ui.button}
          onClick={() => {
            setLimit(limit + PAGE_SIZE);
          }}
        >
          {it.common.showMore(Math.min(PAGE_SIZE, shown.length - limit))}
        </button>
      )}
    </>
  );
}
