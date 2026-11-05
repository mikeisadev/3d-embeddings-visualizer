/** Italian number formatting, shared by every component (0,584 · 15,0% · 1.536). */

const integerFormat = new Intl.NumberFormat('it-IT', { maximumFractionDigits: 0 });

export function formatInteger(value: number): string {
  return integerFormat.format(value);
}

export function formatNumber(value: number, fractionDigits = 3): string {
  return new Intl.NumberFormat('it-IT', {
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(value);
}

export function formatPercent(ratio: number, fractionDigits = 1): string {
  return new Intl.NumberFormat('it-IT', {
    style: 'percent',
    minimumFractionDigits: fractionDigits,
    maximumFractionDigits: fractionDigits,
  }).format(ratio);
}

/** A value of unknown type (metadata) as readable text. */
export function formatValue(value: unknown): string {
  if (value === null || value === undefined) {
    return '—';
  }
  if (typeof value === 'string') {
    return value;
  }
  if (typeof value === 'number' || typeof value === 'boolean') {
    return String(value);
  }
  return JSON.stringify(value);
}
