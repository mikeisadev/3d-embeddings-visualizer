import { CanvasTexture, LinearFilter, SRGBColorSpace, Sprite, SpriteMaterial } from 'three';

const FONT_PX = 44;
const PADDING_PX = 14;

/**
 * A text label that always faces the camera: the text is drawn on a canvas used as the
 * texture of a sprite. `height` is the label height in scene units.
 */
export function createTextSprite(text: string, color: string, height: number): Sprite {
  const canvas = document.createElement('canvas');
  const context = canvas.getContext('2d');
  const font = `600 ${FONT_PX}px system-ui, sans-serif`;
  let width = 64;
  if (context) {
    context.font = font;
    width = Math.ceil(context.measureText(text).width) + PADDING_PX * 2;
  }
  canvas.width = width;
  canvas.height = FONT_PX + PADDING_PX * 2;
  if (context) {
    context.font = font;
    context.fillStyle = 'rgba(15, 23, 32, 0.72)';
    context.fillRect(0, 0, canvas.width, canvas.height);
    context.fillStyle = color;
    context.textBaseline = 'middle';
    context.fillText(text, PADDING_PX, canvas.height / 2);
  }
  const texture = new CanvasTexture(canvas);
  texture.colorSpace = SRGBColorSpace;
  texture.minFilter = LinearFilter;
  const material = new SpriteMaterial({ map: texture, transparent: true, depthTest: false });
  const sprite = new Sprite(material);
  sprite.scale.set((height * canvas.width) / canvas.height, height, 1);
  sprite.renderOrder = 10;
  return sprite;
}

/** Frees the GPU resources of a sprite created by {@link createTextSprite}. */
export function disposeTextSprite(sprite: Sprite): void {
  sprite.material.map?.dispose();
  sprite.material.dispose();
}
