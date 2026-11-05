const FONT_PX = 14;
const PADDING_PX = 10;
const LINE_HEIGHT = 1.45;
const BAND_COLOR = '#0f1720';
const TEXT_COLOR = '#e8edf2';

/**
 * The image of a canvas with a caption band below it, as a PNG data URL. The caption carries
 * the projection caveat, so a screenshot shared outside the tool still says what it shows.
 * Must run right after the frame is rendered: the WebGL buffer is not kept afterwards.
 */
export function captionedPng(source: HTMLCanvasElement, caption: readonly string[]): string {
  const scale = source.clientWidth > 0 ? source.width / source.clientWidth : 1;
  const fontPx = Math.round(FONT_PX * scale);
  const padding = Math.round(PADDING_PX * scale);
  const lineHeight = Math.round(fontPx * LINE_HEIGHT);
  const band = caption.length === 0 ? 0 : caption.length * lineHeight + padding * 2;

  const canvas = document.createElement('canvas');
  canvas.width = source.width;
  canvas.height = source.height + band;
  const context = canvas.getContext('2d');
  if (context === null) {
    return source.toDataURL('image/png');
  }
  context.drawImage(source, 0, 0);
  context.fillStyle = BAND_COLOR;
  context.fillRect(0, source.height, canvas.width, band);
  context.fillStyle = TEXT_COLOR;
  context.font = `${fontPx}px system-ui, sans-serif`;
  context.textBaseline = 'top';
  caption.forEach((line, index) => {
    context.fillText(line, padding, source.height + padding + index * lineHeight);
  });
  return canvas.toDataURL('image/png');
}
