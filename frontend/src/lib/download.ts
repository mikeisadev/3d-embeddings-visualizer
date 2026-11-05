/** Starts the download of {@code url} as {@code fileName} through a temporary link. */
export function downloadUrl(url: string, fileName: string): void {
  const link = document.createElement('a');
  link.href = url;
  link.download = fileName;
  link.rel = 'noopener';
  document.body.append(link);
  link.click();
  link.remove();
}

/**
 * A file name that says what the image shows, for example
 * `embedding-explorer_public.vector_store_pca_2026-11-05_0930.png`. Only safe characters.
 */
export function screenshotFileName(table: string, method: string, date: Date): string {
  const pad = (value: number) => String(value).padStart(2, '0');
  const stamp =
    `${String(date.getFullYear())}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `_${pad(date.getHours())}${pad(date.getMinutes())}`;
  const safe = (text: string) => text.replace(/[^A-Za-z0-9._-]+/g, '-');
  return `embedding-explorer_${safe(table)}_${safe(method)}_${stamp}.png`;
}
