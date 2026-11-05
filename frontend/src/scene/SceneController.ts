import {
  AmbientLight,
  BackSide,
  BufferGeometry,
  Color,
  DirectionalLight,
  DynamicDrawUsage,
  Float32BufferAttribute,
  GridHelper,
  Group,
  IcosahedronGeometry,
  InstancedMesh,
  LineBasicMaterial,
  LineDashedMaterial,
  LineSegments,
  type Material,
  Matrix4,
  Mesh,
  MeshBasicMaterial,
  MeshLambertMaterial,
  OctahedronGeometry,
  PerspectiveCamera,
  Raycaster,
  Scene,
  SRGBColorSpace,
  type Sprite,
  Vector2,
  Vector3,
  WebGLRenderer,
} from 'three';
import { OrbitControls } from 'three/addons/controls/OrbitControls.js';
import { createTextSprite, disposeTextSprite } from './labels';

export interface SceneCallbacks {
  /** The point under the pointer changed or moved; `null` when the pointer leaves the points. */
  onHover(index: number | null, clientX: number, clientY: number): void;
  /** A click (not a drag) on a point, or on empty space (`null`). */
  onSelect(index: number | null): void;
  /** The GPU dropped the WebGL context: the view must be recreated. */
  onContextLost(): void;
}

/** How every point is drawn, one entry per point. */
export interface PointAppearance {
  /** sRGB colors in [0, 1], three values per point. */
  readonly colors: Float32Array;
  /** 0 = not drawn (hidden in the legend). */
  readonly visible: Uint8Array;
  /** 1 = full strength, 0 = dimmed (smaller and transparent). */
  readonly emphasized: Uint8Array;
  readonly pointSize: number;
}

export interface QueryLink {
  readonly index: number;
  readonly passes: boolean;
}

/** The question of the RAG simulation and its lines to the top-K chunks. */
export interface QueryMarker {
  readonly position: readonly [number, number, number];
  readonly label: string;
  readonly links: readonly QueryLink[];
}

type Vec3 = readonly [number, number, number];

interface CameraAnimation {
  readonly start: number;
  readonly fromTarget: Vector3;
  readonly toTarget: Vector3;
  readonly fromPosition: Vector3;
  readonly toPosition: Vector3;
}

const BASE_RADIUS = 0.008;
const DIMMED_SCALE = 0.6;
const SELECTED_SCALE = 1.7;
const OUTLINE_SCALE = 2.1;
const MARKER_RADIUS = 0.024;
const CLICK_TOLERANCE_PX = 5;
const FOCUS_DISTANCE = 0.7;
const CAMERA_ANIMATION_MS = 450;
const AXIS_LENGTH = 0.62;
const LABEL_HEIGHT = 0.032;
const MAX_PIXEL_RATIO = 2;
const DEFAULT_CAMERA = new Vector3(1.05, 0.75, 1.25);
const ORIGIN = new Vector3(0, 0, 0);

const PALETTE = {
  background: '#0f1720',
  axis: '#9fb0c0',
  neighbor: '#f0b400',
  outline: '#ffffff',
  compare: '#e8edf2',
  passes: '#009e73',
  discarded: '#d55e00',
  question: '#ffffff',
  gridCenter: '#3b4a5a',
  grid: '#26323e',
};

/**
 * Owns the three.js objects of the 3D view and translates data into them. Students can
 * recognize the classic building blocks: Scene, PerspectiveCamera, WebGLRenderer, Raycaster
 * and OrbitControls.
 *
 * Points are two InstancedMeshes of low-poly spheres (one for emphasized points, one for
 * dimmed, transparent points), so 5,000 points cost two draw calls; picking uses the
 * instanceId returned by the Raycaster. Buffers are allocated once per dataset and rewritten
 * in place. A frame is rendered only when something changed.
 */
export class SceneController {
  private readonly container: HTMLElement;
  private readonly callbacks: SceneCallbacks;
  private readonly renderer: WebGLRenderer;
  private readonly scene = new Scene();
  private readonly camera = new PerspectiveCamera(50, 1, 0.01, 50);
  private readonly controls: OrbitControls;
  private readonly raycaster = new Raycaster();
  private readonly pointer = new Vector2();
  private readonly sphere = new IcosahedronGeometry(1, 1);
  private readonly focusMaterial = new MeshLambertMaterial();
  private readonly dimMaterial = new MeshLambertMaterial({
    transparent: true,
    opacity: 0.16,
    depthWrite: false,
  });
  private readonly matrix = new Matrix4();
  private readonly color = new Color();
  private readonly resizeObserver: ResizeObserver;
  private readonly grid: GridHelper;
  private readonly axes = new Group();
  private readonly overlays = new Group();
  private readonly overlayResources: { dispose(): void }[] = [];
  private readonly axisSprites: Sprite[] = [];
  private axisLines: LineSegments<BufferGeometry, LineBasicMaterial> | null = null;

  private focusMesh: InstancedMesh | null = null;
  private dimMesh: InstancedMesh | null = null;
  private capacity = -1;
  private focusSlots = new Int32Array(0);
  private dimSlots = new Int32Array(0);
  private positions: Float32Array = new Float32Array(0);
  private appearance: PointAppearance | null = null;
  private selected: number | null = null;
  private neighbors: readonly number[] = [];
  private compare: readonly [number | null, number | null] = [null, null];
  private compareLabels: readonly [string, string] = ['', ''];
  private question: QueryMarker | null = null;
  private needsRender = true;
  private pointerInside = false;
  private pointerMoved = false;
  private lastClient = { x: 0, y: 0 };
  private pointerDown: { x: number; y: number } | null = null;
  private hovered: number | null = null;
  private animation: CameraAnimation | null = null;
  private disposed = false;

  constructor(container: HTMLElement, callbacks: SceneCallbacks) {
    this.container = container;
    this.callbacks = callbacks;

    this.renderer = new WebGLRenderer({ antialias: true, powerPreference: 'high-performance' });
    this.renderer.setPixelRatio(Math.min(window.devicePixelRatio, MAX_PIXEL_RATIO));
    this.renderer.setClearColor(PALETTE.background);
    container.appendChild(this.renderer.domElement);

    this.camera.position.copy(DEFAULT_CAMERA);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.12;
    this.controls.minDistance = 0.05;
    this.controls.maxDistance = 8;
    this.controls.addEventListener('change', this.requestRender);

    const light = new DirectionalLight(0xffffff, 1.6);
    light.position.set(1, 1, 2);
    this.camera.add(light);
    this.scene.add(this.camera, new AmbientLight(0xffffff, 1.1));

    this.grid = new GridHelper(1.2, 12, PALETTE.gridCenter, PALETTE.grid);
    this.grid.position.y = -0.55;
    this.grid.visible = false;
    this.scene.add(this.grid, this.axes, this.overlays);
    this.setAxisLabels(['PC1', 'PC2', 'PC3']);

    const canvas = this.renderer.domElement;
    canvas.addEventListener('pointermove', this.onPointerMove);
    canvas.addEventListener('pointerleave', this.onPointerLeave);
    canvas.addEventListener('pointerdown', this.onPointerDown);
    canvas.addEventListener('pointerup', this.onPointerUp);
    canvas.addEventListener('dblclick', this.onDoubleClick);
    canvas.addEventListener('webglcontextlost', this.onContextLost);

    this.resizeObserver = new ResizeObserver(() => {
      this.resize();
    });
    this.resizeObserver.observe(container);
    this.resize();
    this.renderer.setAnimationLoop(this.frame);
  }

  /** New point positions (display coordinates, three values per point). */
  setPoints(positions: Float32Array): void {
    const count = positions.length / 3;
    if (count !== this.capacity) {
      this.disposePointMeshes();
      this.focusMesh = this.createPointMesh(this.focusMaterial, count);
      this.dimMesh = this.createPointMesh(this.dimMaterial, count);
      this.focusSlots = new Int32Array(count);
      this.dimSlots = new Int32Array(count);
      this.capacity = count;
    }
    this.positions = positions;
    this.refreshPoints();
    this.refreshOverlays();
  }

  setAppearance(appearance: PointAppearance): void {
    this.appearance = appearance;
    this.refreshPoints();
    this.refreshOverlays();
  }

  /** The selected point and its real neighbors (point indices). */
  setSelection(selected: number | null, neighbors: readonly number[]): void {
    this.selected = selected;
    this.neighbors = neighbors;
    this.refreshOverlays();
  }

  /** The two chunks being compared, joined by a dashed line and labeled. */
  setComparison(a: number | null, b: number | null, labels: readonly [string, string]): void {
    this.compare = [a, b];
    this.compareLabels = labels;
    this.refreshOverlays();
  }

  setQueryMarker(marker: QueryMarker | null): void {
    this.question = marker;
    this.refreshOverlays();
  }

  setAxisLabels(labels: readonly [string, string, string]): void {
    this.disposeAxes();
    const directions: Vec3[] = [
      [AXIS_LENGTH, 0, 0],
      [0, AXIS_LENGTH, 0],
      [0, 0, AXIS_LENGTH],
    ];
    const vertices = directions.flatMap((direction) => [0, 0, 0, ...direction]);
    const geometry = new BufferGeometry();
    geometry.setAttribute('position', new Float32BufferAttribute(vertices, 3));
    this.axisLines = new LineSegments(geometry, new LineBasicMaterial({ color: PALETTE.axis }));
    this.axes.add(this.axisLines);
    directions.forEach((direction, axis) => {
      const sprite = createTextSprite(labels[axis] ?? '', PALETTE.axis, LABEL_HEIGHT);
      sprite.position.set(direction[0] * 1.12, direction[1] * 1.12, direction[2] * 1.12);
      this.axes.add(sprite);
      this.axisSprites.push(sprite);
    });
    this.requestRender();
  }

  setGridVisible(visible: boolean): void {
    this.grid.visible = visible;
    this.requestRender();
  }

  /** Moves the camera smoothly to frame point {@code index}. */
  focusPoint(index: number): void {
    const target = this.positionOf(index);
    if (target !== null) {
      this.focusTarget(target);
    }
  }

  /** Moves the camera smoothly to frame a position, for example the question of a RAG search. */
  focusPosition(position: Vec3): void {
    this.focusTarget(new Vector3(...position));
  }

  resetView(): void {
    this.animateCamera(ORIGIN.clone(), DEFAULT_CAMERA.clone());
  }

  /** The current frame as a PNG data URL. */
  screenshot(): string {
    this.renderer.render(this.scene, this.camera);
    return this.renderer.domElement.toDataURL('image/png');
  }

  dispose(): void {
    if (this.disposed) {
      return;
    }
    this.disposed = true;
    this.renderer.setAnimationLoop(null);
    this.resizeObserver.disconnect();
    const canvas = this.renderer.domElement;
    canvas.removeEventListener('pointermove', this.onPointerMove);
    canvas.removeEventListener('pointerleave', this.onPointerLeave);
    canvas.removeEventListener('pointerdown', this.onPointerDown);
    canvas.removeEventListener('pointerup', this.onPointerUp);
    canvas.removeEventListener('dblclick', this.onDoubleClick);
    canvas.removeEventListener('webglcontextlost', this.onContextLost);
    this.controls.removeEventListener('change', this.requestRender);
    this.controls.dispose();
    this.clearOverlays();
    this.disposeAxes();
    this.disposePointMeshes();
    this.sphere.dispose();
    this.focusMaterial.dispose();
    this.dimMaterial.dispose();
    this.grid.geometry.dispose();
    disposeMaterial(this.grid.material);
    this.renderer.dispose();
    canvas.remove();
  }

  private readonly frame = (time: number) => {
    if (this.animation !== null) {
      this.stepAnimation(time);
    }
    const moved = this.controls.update();
    if (this.pointerMoved) {
      this.pointerMoved = false;
      this.updateHover();
    }
    if (moved || this.needsRender) {
      this.needsRender = false;
      this.renderer.render(this.scene, this.camera);
    }
  };

  private readonly requestRender = () => {
    this.needsRender = true;
  };

  private createPointMesh(material: Material, capacity: number): InstancedMesh {
    const mesh = new InstancedMesh(this.sphere, material, Math.max(capacity, 1));
    mesh.instanceMatrix.setUsage(DynamicDrawUsage);
    mesh.setColorAt(0, this.color.set(0xffffff));
    mesh.instanceColor?.setUsage(DynamicDrawUsage);
    mesh.count = 0;
    mesh.frustumCulled = false;
    this.scene.add(mesh);
    return mesh;
  }

  /** Rewrites the instance buffers: each visible point goes to the emphasized or the dimmed mesh. */
  private refreshPoints(): void {
    const focusMesh = this.focusMesh;
    const dimMesh = this.dimMesh;
    const appearance = this.appearance;
    const count = this.positions.length / 3;
    if (focusMesh === null || dimMesh === null || appearance?.visible.length !== count) {
      return;
    }
    const radius = BASE_RADIUS * appearance.pointSize;
    let focusUsed = 0;
    let dimUsed = 0;
    for (let index = 0; index < count; index++) {
      if (appearance.visible[index] === 0) {
        continue;
      }
      const emphasized = appearance.emphasized[index] === 1;
      const mesh = emphasized ? focusMesh : dimMesh;
      const slot = emphasized ? focusUsed++ : dimUsed++;
      const size = emphasized ? radius : radius * DIMMED_SCALE;
      this.matrix.makeScale(size, size, size);
      this.matrix.setPosition(
        this.positions[index * 3] ?? 0,
        this.positions[index * 3 + 1] ?? 0,
        this.positions[index * 3 + 2] ?? 0,
      );
      mesh.setMatrixAt(slot, this.matrix);
      mesh.setColorAt(slot, this.colorOf(index));
      (emphasized ? this.focusSlots : this.dimSlots)[slot] = index;
    }
    finishInstances(focusMesh, focusUsed);
    finishInstances(dimMesh, dimUsed);
    this.requestRender();
  }

  /** Rebuilds the few extra objects: selection, neighbor lines, comparison and question. */
  private refreshOverlays(): void {
    this.clearOverlays();
    const radius = BASE_RADIUS * (this.appearance?.pointSize ?? 1);
    const selected = this.selected === null ? null : this.positionOf(this.selected);
    if (this.selected !== null && selected !== null) {
      const highlight = new MeshLambertMaterial({ color: this.colorOf(this.selected).clone() });
      const outline = new MeshBasicMaterial({ color: PALETTE.outline, side: BackSide });
      this.addOverlay(this.sphereAt(selected, radius * SELECTED_SCALE, highlight), highlight);
      this.addOverlay(this.sphereAt(selected, radius * OUTLINE_SCALE, outline), outline);
      const ends = this.neighbors
        .map((index) => this.positionOf(index))
        .filter((end) => end !== null);
      this.addLines(
        ends.flatMap((end) => [selected, end]),
        new LineBasicMaterial({ color: PALETTE.neighbor, transparent: true, opacity: 0.85 }),
      );
    }

    const [a, b] = this.compare;
    const from = a === null ? null : this.positionOf(a);
    const to = b === null ? null : this.positionOf(b);
    if (from !== null && to !== null) {
      this.addLines(
        [from, to],
        new LineDashedMaterial({ color: PALETTE.compare, dashSize: 0.02, gapSize: 0.012 }),
        true,
      );
      this.addLabel(this.compareLabels[0], from);
      this.addLabel(this.compareLabels[1], to);
    }

    if (this.question !== null) {
      const position = new Vector3(...this.question.position);
      const material = new MeshLambertMaterial({ color: PALETTE.question, emissive: '#555555' });
      const marker = new Mesh(new OctahedronGeometry(MARKER_RADIUS), material);
      marker.position.copy(position);
      this.addOverlay(marker, material, marker.geometry);
      this.addLabel(
        this.question.label,
        position.clone().add(new Vector3(0, MARKER_RADIUS * 2.4, 0)),
      );
      const ends = (passes: boolean) =>
        (this.question?.links ?? [])
          .filter((link) => link.passes === passes)
          .map((link) => this.positionOf(link.index))
          .filter((end) => end !== null)
          .flatMap((end) => [position, end]);
      this.addLines(ends(true), new LineBasicMaterial({ color: PALETTE.passes }));
      this.addLines(
        ends(false),
        new LineDashedMaterial({ color: PALETTE.discarded, dashSize: 0.015, gapSize: 0.012 }),
        true,
      );
    }
    this.requestRender();
  }

  private sphereAt(position: Vector3, radius: number, material: Material): Mesh {
    const mesh = new Mesh(this.sphere, material);
    mesh.position.copy(position);
    mesh.scale.setScalar(radius);
    return mesh;
  }

  private addLines(vertices: readonly Vector3[], material: Material, dashed = false): void {
    if (vertices.length === 0) {
      material.dispose();
      return;
    }
    const geometry = new BufferGeometry().setFromPoints([...vertices]);
    const lines = new LineSegments(geometry, material);
    if (dashed) {
      lines.computeLineDistances();
    }
    this.addOverlay(lines, material, geometry);
  }

  private addLabel(text: string, position: Vector3): void {
    const sprite = createTextSprite(text, PALETTE.outline, LABEL_HEIGHT * 0.8);
    sprite.position.copy(position);
    this.overlays.add(sprite);
    this.overlayResources.push({
      dispose: () => {
        disposeTextSprite(sprite);
      },
    });
  }

  private addOverlay(
    object: Mesh | LineSegments,
    material: Material,
    geometry?: BufferGeometry,
  ): void {
    this.overlays.add(object);
    this.overlayResources.push(material);
    if (geometry !== undefined) {
      this.overlayResources.push(geometry);
    }
  }

  private clearOverlays(): void {
    this.overlays.clear();
    for (const resource of this.overlayResources.splice(0)) {
      resource.dispose();
    }
  }

  private disposeAxes(): void {
    for (const sprite of this.axisSprites.splice(0)) {
      disposeTextSprite(sprite);
    }
    if (this.axisLines !== null) {
      this.axisLines.geometry.dispose();
      this.axisLines.material.dispose();
      this.axisLines = null;
    }
    this.axes.clear();
  }

  private disposePointMeshes(): void {
    for (const mesh of [this.focusMesh, this.dimMesh]) {
      if (mesh !== null) {
        this.scene.remove(mesh);
        mesh.dispose();
      }
    }
    this.focusMesh = null;
    this.dimMesh = null;
    this.capacity = -1;
  }

  private positionOf(index: number): Vector3 | null {
    if (
      index < 0 ||
      index * 3 + 2 >= this.positions.length ||
      this.appearance?.visible[index] === 0
    ) {
      return null;
    }
    return new Vector3(
      this.positions[index * 3] ?? 0,
      this.positions[index * 3 + 1] ?? 0,
      this.positions[index * 3 + 2] ?? 0,
    );
  }

  private colorOf(index: number): Color {
    const colors = this.appearance?.colors;
    if (colors === undefined) {
      return this.color.set(0xffffff);
    }
    return this.color.setRGB(
      colors[index * 3] ?? 1,
      colors[index * 3 + 1] ?? 1,
      colors[index * 3 + 2] ?? 1,
      SRGBColorSpace,
    );
  }

  /** The point under the given screen position; emphasized points win over dimmed ones. */
  private pick(clientX: number, clientY: number): number | null {
    if (this.focusMesh === null || this.dimMesh === null) {
      return null;
    }
    const box = this.renderer.domElement.getBoundingClientRect();
    if (box.width === 0 || box.height === 0) {
      return null;
    }
    this.pointer.set(
      ((clientX - box.left) / box.width) * 2 - 1,
      -((clientY - box.top) / box.height) * 2 + 1,
    );
    this.raycaster.setFromCamera(this.pointer, this.camera);
    const hits = this.raycaster.intersectObjects([this.focusMesh, this.dimMesh], false);
    const hit = hits.find((candidate) => candidate.object === this.focusMesh) ?? hits[0];
    if (hit?.instanceId === undefined) {
      return null;
    }
    const slots = hit.object === this.focusMesh ? this.focusSlots : this.dimSlots;
    return slots[hit.instanceId] ?? null;
  }

  private updateHover(): void {
    const index = this.pointerInside ? this.pick(this.lastClient.x, this.lastClient.y) : null;
    if (index !== this.hovered || index !== null) {
      this.hovered = index;
      this.callbacks.onHover(index, this.lastClient.x, this.lastClient.y);
    }
  }

  private focusTarget(target: Vector3): void {
    const offset = this.camera.position
      .clone()
      .sub(this.controls.target)
      .normalize()
      .multiplyScalar(FOCUS_DISTANCE);
    this.animateCamera(target, target.clone().add(offset));
  }

  private animateCamera(target: Vector3, position: Vector3): void {
    this.animation = {
      start: performance.now(),
      fromTarget: this.controls.target.clone(),
      toTarget: target,
      fromPosition: this.camera.position.clone(),
      toPosition: position,
    };
    this.requestRender();
  }

  private stepAnimation(time: number): void {
    const animation = this.animation;
    if (animation === null) {
      return;
    }
    const progress = Math.min(1, Math.max(0, (time - animation.start) / CAMERA_ANIMATION_MS));
    const eased = 1 - (1 - progress) ** 3;
    this.controls.target.lerpVectors(animation.fromTarget, animation.toTarget, eased);
    this.camera.position.lerpVectors(animation.fromPosition, animation.toPosition, eased);
    if (progress >= 1) {
      this.animation = null;
    }
    this.requestRender();
  }

  private resize(): void {
    const width = this.container.clientWidth;
    const height = this.container.clientHeight;
    if (width === 0 || height === 0) {
      return;
    }
    this.renderer.setSize(width, height, false);
    this.camera.aspect = width / height;
    this.camera.updateProjectionMatrix();
    this.requestRender();
  }

  private readonly onPointerMove = (event: PointerEvent) => {
    this.lastClient = { x: event.clientX, y: event.clientY };
    this.pointerInside = true;
    this.pointerMoved = true;
  };

  private readonly onPointerLeave = () => {
    this.pointerInside = false;
    this.pointerMoved = true;
  };

  private readonly onPointerDown = (event: PointerEvent) => {
    this.pointerDown = event.button === 0 ? { x: event.clientX, y: event.clientY } : null;
  };

  private readonly onPointerUp = (event: PointerEvent) => {
    const down = this.pointerDown;
    this.pointerDown = null;
    if (event.button !== 0 || down === null) {
      return;
    }
    if (Math.hypot(event.clientX - down.x, event.clientY - down.y) > CLICK_TOLERANCE_PX) {
      return;
    }
    this.callbacks.onSelect(this.pick(event.clientX, event.clientY));
  };

  private readonly onDoubleClick = (event: MouseEvent) => {
    const index = this.pick(event.clientX, event.clientY);
    if (index !== null) {
      this.focusPoint(index);
      this.callbacks.onSelect(index);
    }
  };

  private readonly onContextLost = (event: Event) => {
    event.preventDefault();
    this.renderer.setAnimationLoop(null);
    this.callbacks.onContextLost();
  };
}

function finishInstances(mesh: InstancedMesh, used: number): void {
  mesh.count = used;
  mesh.instanceMatrix.needsUpdate = true;
  if (mesh.instanceColor !== null) {
    mesh.instanceColor.needsUpdate = true;
  }
  mesh.computeBoundingSphere();
}

function disposeMaterial(material: Material | Material[]): void {
  for (const single of Array.isArray(material) ? material : [material]) {
    single.dispose();
  }
}
