import { type MouseEvent, useEffect, useRef, useState } from 'react';
import { it } from '../../i18n/it';
import { barcodeColor, barcodeGrid, symmetricRange } from '../../lib/barcode';
import { formatInteger, formatNumber } from '../../lib/format';
import { diverging } from '../../lib/palette';
import styles from './VectorBarcode.module.css';

const CELL = 6;
const LEGEND_STOPS = 7;

/**
 * Every value of the embedding as a colored cell of a near-square grid (48 × 32 for 1536):
 * blue below zero, white at zero, red above, on a symmetric scale.
 */
export function VectorBarcode({ values }: { readonly values: readonly number[] }) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [hovered, setHovered] = useState<number | null>(null);
  const grid = barcodeGrid(values.length);
  const range = symmetricRange(values);

  useEffect(() => {
    const context = canvasRef.current?.getContext('2d');
    if (!context) {
      return;
    }
    context.clearRect(0, 0, grid.columns * CELL, grid.rows * CELL);
    values.forEach((value, index) => {
      context.fillStyle = barcodeColor(value, range);
      context.fillRect(
        (index % grid.columns) * CELL,
        Math.floor(index / grid.columns) * CELL,
        CELL,
        CELL,
      );
    });
  }, [values, grid.columns, grid.rows, range]);

  const onMove = (event: MouseEvent<HTMLCanvasElement>) => {
    const box = event.currentTarget.getBoundingClientRect();
    const column = Math.floor(((event.clientX - box.left) / box.width) * grid.columns);
    const row = Math.floor(((event.clientY - box.top) / box.height) * grid.rows);
    const index = row * grid.columns + column;
    setHovered(index >= 0 && index < values.length ? index : null);
  };

  const legend = Array.from({ length: LEGEND_STOPS }, (_, index) =>
    diverging((index / (LEGEND_STOPS - 1)) * 2 - 1),
  );
  const hoveredValue = hovered === null ? undefined : values[hovered];

  return (
    <figure className={styles.figure}>
      <canvas
        ref={canvasRef}
        className={styles.canvas}
        width={grid.columns * CELL}
        height={grid.rows * CELL}
        role="img"
        aria-label={it.chunk.barcodeDescription(values.length, grid.columns, grid.rows)}
        onMouseMove={onMove}
        onMouseLeave={() => {
          setHovered(null);
        }}
      />
      <figcaption>
        <div
          className={styles.legend}
          style={{ background: `linear-gradient(to right, ${legend.join(', ')})` }}
        />
        <div className={styles.range}>
          <span>{formatNumber(-range, 3)}</span>
          <span>0</span>
          <span>{formatNumber(range, 3)}</span>
        </div>
        <p className={styles.readout} aria-live="polite">
          {hovered !== null && hoveredValue !== undefined
            ? `#${formatInteger(hovered + 1)}: ${formatNumber(hoveredValue, 5)}`
            : it.chunk.barcodeDescription(values.length, grid.columns, grid.rows)}
        </p>
      </figcaption>
    </figure>
  );
}
