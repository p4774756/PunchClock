import * as THREE from "three";
import { OrbitControls } from "three/examples/jsm/controls/OrbitControls.js";
import gsap from "gsap";
import type { Acupoint, Meridian } from "../acupoint-data";
import { createMeridianLine } from "./body";
import { HotspotLayer } from "./hotspots";
import { loadBodyModel, type LoadedBody } from "./loaders";
import { snapPathToSurface } from "./snap";

type ViewerCallbacks = {
  onSelect: (point: Acupoint | null) => void;
  onHover: (point: Acupoint | null) => void;
  onLoading?: (loading: boolean, progress: number) => void;
};

const HOME_CAMERA = { x: 0.55, y: 0.35, z: 2.85 };
const HOME_TARGET = { x: 0, y: 0.05, z: 0 };

function contactShadowTexture() {
  const size = 256;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const g = ctx.createRadialGradient(size / 2, size / 2, size * 0.08, size / 2, size / 2, size * 0.48);
  g.addColorStop(0, "rgba(20, 30, 36, 0.45)");
  g.addColorStop(0.55, "rgba(20, 30, 36, 0.12)");
  g.addColorStop(1, "rgba(20, 30, 36, 0)");
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, size, size);
  return new THREE.CanvasTexture(canvas);
}

export class AcupointViewer {
  private renderer: THREE.WebGLRenderer;
  private scene = new THREE.Scene();
  private camera = new THREE.PerspectiveCamera(36, 1, 0.05, 50);
  private controls: OrbitControls;
  private hotspots = new HotspotLayer();
  private callbacks: ViewerCallbacks;
  private container: HTMLElement;
  private bodyRoot: THREE.Group | null = null;
  private bodyMeshes: THREE.Mesh[] = [];
  private meridianGroup = new THREE.Group();
  private pivot = new THREE.Group();
  private bodyReady = false;
  private pendingMeridian: { meridian: Meridian; showAll: boolean } | null = null;

  private clock = new THREE.Clock();
  private resizeObserver: ResizeObserver;
  private dirty = true;
  private width = 1;
  private height = 1;
  private disposed = false;

  private selectedId: string | null = null;
  private hoveredId: string | null = null;
  private pointerId: number | null = null;
  private pointerStart = { x: 0, y: 0 };
  private dragged = false;
  private calloutEl: HTMLElement | null = null;

  constructor(container: HTMLElement, callbacks: ViewerCallbacks) {
    this.container = container;
    this.callbacks = callbacks;

    const lowPower =
      window.matchMedia("(max-width: 780px)").matches || (navigator.hardwareConcurrency ?? 8) < 6;
    const pixelRatio = Math.min(window.devicePixelRatio, lowPower ? 1.5 : 2);

    this.renderer = new THREE.WebGLRenderer({
      antialias: !lowPower,
      alpha: true,
      powerPreference: "high-performance",
    });
    this.renderer.setPixelRatio(pixelRatio);
    this.renderer.outputColorSpace = THREE.SRGBColorSpace;
    this.renderer.toneMapping = THREE.ACESFilmicToneMapping;
    this.renderer.toneMappingExposure = 1.05;
    this.renderer.domElement.setAttribute(
      "aria-label",
      "交互式三維經絡穴位圖。拖拽旋轉，滾輪縮放，點擊穴位查看詳情。",
    );
    this.renderer.domElement.tabIndex = 0;
    container.appendChild(this.renderer.domElement);

    this.camera.position.set(HOME_CAMERA.x, HOME_CAMERA.y, HOME_CAMERA.z);
    this.controls = new OrbitControls(this.camera, this.renderer.domElement);
    this.controls.enableDamping = true;
    this.controls.dampingFactor = 0.06;
    this.controls.enablePan = false;
    this.controls.minDistance = 1.6;
    this.controls.maxDistance = 5.2;
    this.controls.autoRotate = true;
    this.controls.autoRotateSpeed = 0.55;
    this.controls.target.set(HOME_TARGET.x, HOME_TARGET.y, HOME_TARGET.z);
    this.controls.maxPolarAngle = Math.PI * 0.88;
    this.controls.minPolarAngle = Math.PI * 0.12;

    this.pivot.add(this.meridianGroup);
    this.scene.add(this.pivot);
    this.buildEnvironment();

    this.resizeObserver = new ResizeObserver(() => this.resize());
    this.resizeObserver.observe(container);

    this.controls.addEventListener("start", () => {
      this.dirty = true;
    });
    this.controls.addEventListener("change", () => {
      this.dirty = true;
    });

    const canvas = this.renderer.domElement;
    canvas.addEventListener("pointerdown", this.onPointerDown);
    canvas.addEventListener("pointermove", this.onPointerMove);
    canvas.addEventListener("pointerup", this.onPointerUp);
    canvas.addEventListener("pointerleave", this.onPointerLeave);

    this.resize();
    this.animate();
    void this.bootstrapBody();
  }

  private async bootstrapBody() {
    this.callbacks.onLoading?.(true, 0);
    const loaded: LoadedBody = await loadBodyModel((ratio) => {
      this.callbacks.onLoading?.(true, ratio);
    });
    if (this.disposed) return;

    if (this.bodyRoot) {
      this.pivot.remove(this.bodyRoot);
    }
    this.bodyRoot = loaded.root;
    this.bodyMeshes = loaded.meshes;
    this.pivot.add(this.bodyRoot);
    this.bodyReady = true;
    this.callbacks.onLoading?.(false, 1);
    this.dirty = true;

    if (this.pendingMeridian) {
      const { meridian, showAll } = this.pendingMeridian;
      this.pendingMeridian = null;
      this.setMeridian(meridian, showAll);
    }
  }

  private buildEnvironment() {
    this.scene.add(new THREE.AmbientLight(0xffffff, 0.38));
    this.scene.add(new THREE.HemisphereLight(0xe8f2f0, 0x2a3238, 0.7));

    const key = new THREE.DirectionalLight(0xfff5ea, 2.8);
    key.position.set(3.5, 5.5, 4.5);
    this.scene.add(key);
    const fill = new THREE.DirectionalLight(0xc5dde8, 1.1);
    fill.position.set(-3.8, 1.5, 3.2);
    this.scene.add(fill);
    const rim = new THREE.DirectionalLight(0x7eb8a8, 1.4);
    rim.position.set(-2.5, 3, -4);
    this.scene.add(rim);

    const plinth = new THREE.Mesh(
      new THREE.CylinderGeometry(0.55, 0.62, 0.06, 48),
      new THREE.MeshStandardMaterial({ color: 0x1e2a30, roughness: 0.7, metalness: 0.15 }),
    );
    plinth.position.y = -0.92;
    this.scene.add(plinth);

    const shadow = new THREE.Mesh(
      new THREE.PlaneGeometry(1.4, 1.4),
      new THREE.MeshBasicMaterial({
        map: contactShadowTexture(),
        transparent: true,
        depthWrite: false,
        opacity: 0.7,
        toneMapped: false,
      }),
    );
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = -0.888;
    this.scene.add(shadow);

    const positions = new Float32Array(36 * 3);
    for (let i = 0; i < positions.length; i += 3) {
      positions[i] = (Math.random() - 0.5) * 4;
      positions[i + 1] = (Math.random() - 0.5) * 3;
      positions[i + 2] = (Math.random() - 0.5) * 3 - 1;
    }
    const pg = new THREE.BufferGeometry();
    pg.setAttribute("position", new THREE.BufferAttribute(positions, 3));
    this.scene.add(
      new THREE.Points(
        pg,
        new THREE.PointsMaterial({
          color: 0x8ec4b4,
          size: 0.012,
          transparent: true,
          opacity: 0.22,
        }),
      ),
    );
  }

  setMeridian(meridian: Meridian, showAll = false) {
    if (!this.bodyReady) {
      this.pendingMeridian = { meridian, showAll };
      return;
    }

    while (this.meridianGroup.children.length) {
      const child = this.meridianGroup.children[0];
      this.meridianGroup.remove(child);
      child.traverse((obj) => {
        if (obj instanceof THREE.Mesh) {
          obj.geometry.dispose();
          if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose());
          else obj.material.dispose();
        }
      });
    }

    const bilateral = meridian.id !== "cv" && meridian.id !== "gv";
    const snappedPath = snapPathToSurface(meridian.path, this.pivot, this.bodyMeshes);
    this.meridianGroup.add(createMeridianLine(snappedPath, meridian.accent, bilateral));

    const colorById: Record<string, string> = { [meridian.id]: meridian.accent };
    const points = showAll
      ? meridian.points
      : meridian.points.filter((p) => p.side === "right" || p.side === "mid");

    this.hotspots.attach(this.pivot, points, colorById, this.bodyMeshes);
    this.selectedId = null;
    this.callbacks.onSelect(null);
    this.dirty = true;

    gsap.fromTo(
      this.pivot.rotation,
      { y: this.pivot.rotation.y },
      { y: this.pivot.rotation.y + 0.12, duration: 0.55, ease: "power2.out", yoyo: true, repeat: 1 },
    );
  }

  setAutoRotate(enabled: boolean) {
    this.controls.autoRotate = enabled;
    this.dirty = true;
  }

  resetView() {
    gsap.to(this.camera.position, {
      x: HOME_CAMERA.x,
      y: HOME_CAMERA.y,
      z: HOME_CAMERA.z,
      duration: 0.8,
      ease: "power2.inOut",
      onUpdate: () => {
        this.dirty = true;
      },
    });
    gsap.to(this.controls.target, {
      x: HOME_TARGET.x,
      y: HOME_TARGET.y,
      z: HOME_TARGET.z,
      duration: 0.8,
      ease: "power2.inOut",
    });
  }

  focusPoint(point: Acupoint) {
    this.selectedId = point.id;
    this.callbacks.onSelect(point);
    this.dirty = true;
    const marker = this.hotspots.list.find((m) => m.point.id === point.id);
    const target = marker?.anchor ?? new THREE.Vector3(...point.position);
    gsap.to(this.controls.target, {
      x: target.x * 0.35,
      y: target.y,
      z: target.z * 0.35,
      duration: 0.7,
      ease: "power2.out",
      onUpdate: () => {
        this.dirty = true;
      },
    });
  }

  attachCallout(el: HTMLElement | null) {
    this.calloutEl = el;
  }

  private resize = () => {
    const rect = this.container.getBoundingClientRect();
    this.width = Math.max(1, rect.width);
    this.height = Math.max(1, rect.height);
    this.camera.aspect = this.width / this.height;
    this.camera.updateProjectionMatrix();
    this.renderer.setSize(this.width, this.height, false);
    this.dirty = true;
  };

  private onPointerDown = (event: PointerEvent) => {
    this.pointerId = event.pointerId;
    this.pointerStart = { x: event.clientX, y: event.clientY };
    this.dragged = false;
  };

  private onPointerMove = (event: PointerEvent) => {
    if (this.pointerId !== null) {
      const dx = event.clientX - this.pointerStart.x;
      const dy = event.clientY - this.pointerStart.y;
      if (Math.hypot(dx, dy) > 6) this.dragged = true;
    }

    const rect = this.renderer.domElement.getBoundingClientRect();
    const nx = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const ny = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
    const hit = this.hotspots.pick(nx, ny, this.camera);
    const nextId = hit?.id ?? null;
    if (nextId !== this.hoveredId) {
      this.hoveredId = nextId;
      this.callbacks.onHover(hit);
      this.renderer.domElement.style.cursor = hit ? "pointer" : "grab";
      this.dirty = true;
    }
  };

  private onPointerUp = (event: PointerEvent) => {
    if (this.pointerId !== event.pointerId) return;
    this.pointerId = null;
    if (this.dragged) return;

    const rect = this.renderer.domElement.getBoundingClientRect();
    const nx = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    const ny = -(((event.clientY - rect.top) / rect.height) * 2 - 1);
    const hit = this.hotspots.pick(nx, ny, this.camera);
    this.selectedId = hit?.id ?? null;
    this.callbacks.onSelect(hit);
    this.dirty = true;
  };

  private onPointerLeave = () => {
    this.pointerId = null;
    if (this.hoveredId) {
      this.hoveredId = null;
      this.callbacks.onHover(null);
      this.dirty = true;
    }
    this.renderer.domElement.style.cursor = "grab";
  };

  private updateCallout() {
    if (!this.calloutEl || !this.selectedId) {
      if (this.calloutEl) this.calloutEl.style.opacity = "0";
      return;
    }
    const pos = this.hotspots.projectCallout(this.selectedId, this.camera, this.width, this.height);
    if (!pos || !pos.visible) {
      this.calloutEl.style.opacity = "0";
      return;
    }
    this.calloutEl.style.opacity = "1";
    this.calloutEl.style.transform = `translate(${pos.x}px, ${pos.y - 28}px) translate(-50%, -100%)`;
  }

  private animate = () => {
    if (this.disposed) return;
    requestAnimationFrame(this.animate);
    const dt = Math.min(this.clock.getDelta(), 0.05);
    const needs = this.dirty || this.controls.autoRotate || this.controls.enabled;

    if (needs) {
      this.controls.update();
      this.hotspots.update(dt, this.camera, this.selectedId, this.hoveredId);
      this.updateCallout();
      this.renderer.render(this.scene, this.camera);
      this.dirty = this.controls.autoRotate;
    }
  };

  dispose() {
    this.disposed = true;
    this.resizeObserver.disconnect();
    this.hotspots.dispose();
    this.controls.dispose();
    this.renderer.dispose();
    this.renderer.domElement.remove();
  }
}
