import type { MouseEvent } from 'react';
import ui from '../../components/ui.module.css';
import { it } from '../../i18n/it';
import type { Legend as LegendModel } from '../../lib/colorBy';
import { cx } from '../../lib/cx';
import { formatInteger, formatNumber } from '../../lib/format';
import { viridis } from '../../lib/palette';
import { useUiStore } from '../../state/uiStore';
import styles from './Legend.module.css';

const GRADIENT_STOPS = 9;

/** The legend: categories can be hidden (click) or isolated (Alt+click); numbers show a scale. */
export function Legend({ legend }: { readonly legend: LegendModel }) {
  const hidden = useUiStore((state) => state.hiddenCategories);
  const toggleCategory = useUiStore((state) => state.toggleCategory);
  const isolateCategory = useUiStore((state) => state.isolateCategory);
  const showAll = useUiStore((state) => state.showAllCategories);

  if (legend.kind === 'sequential') {
    const gradient = Array.from({ length: GRADIENT_STOPS }, (_, index) =>
      viridis(index / (GRADIENT_STOPS - 1)),
    );
    return (
      <div className={styles.sequential}>
        <div
          className={styles.gradient}
          style={{ background: `linear-gradient(to right, ${gradient.join(', ')})` }}
        />
        <div className={styles.range}>
          <span>{formatNumber(legend.min, Number.isInteger(legend.min) ? 0 : 2)}</span>
          <span>{formatNumber(legend.max, Number.isInteger(legend.max) ? 0 : 2)}</span>
        </div>
        {legend.missing > 0 && (
          <p className={ui.muted}>
            {it.colors.missingValue}: {formatInteger(legend.missing)}
          </p>
        )}
      </div>
    );
  }

  const allIds = legend.categories.map((category) => category.id);
  const onClick = (id: string) => (event: MouseEvent<HTMLButtonElement>) => {
    if (event.altKey) {
      isolateCategory(id, allIds);
    } else {
      toggleCategory(id);
    }
  };

  return (
    <>
      <p className={ui.muted}>{it.colors.legendHint}</p>
      <ul className={styles.categories}>
        {legend.categories.map((category) => {
          const isHidden = hidden.includes(category.id);
          return (
            <li key={category.id}>
              <button
                type="button"
                className={cx(styles.category, isHidden && styles.hidden)}
                aria-pressed={!isHidden}
                aria-label={it.colors.categoryLabel(category.label, category.count, isHidden)}
                onClick={onClick(category.id)}
              >
                <span
                  className={styles.swatch}
                  style={{ background: category.color }}
                  aria-hidden="true"
                />
                <span className={styles.name}>{category.label}</span>
                <span className={styles.count}>{formatInteger(category.count)}</span>
              </button>
            </li>
          );
        })}
      </ul>
      {hidden.length > 0 && (
        <button type="button" className={ui.button} onClick={showAll}>
          {it.colors.showAll}
        </button>
      )}
    </>
  );
}
