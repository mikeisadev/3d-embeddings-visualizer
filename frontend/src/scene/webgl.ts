/** Whether the browser can create a WebGL 2 context (three.js needs WebGL 2). */
export function isWebGl2Available(): boolean {
  try {
    const canvas = document.createElement('canvas');
    return canvas.getContext('webgl2') !== null;
  } catch {
    return false;
  }
}
