import * as THREE from "three";
import type { Acupoint } from "../acupoint-data";
import { snapPointsToSurface } from "./snap";

export type Marker = {
  point: Acupoint;
  color: string;
  dot: THREE.Sprite;
  pulse: THREE.Sprite;
  anchor: THREE.Vector3;
  opacity: number;
  emphasis: number;
};

const TAU = Math.PI * 2;
const VIEW_LIFT = 0.08;
const PULSE_SECONDS = 4.5;

function rgba(color: THREE.Color, alpha: number) {
  const r = Math.round(color.r * 255);
  const g = Math.round(color.g * 255);
  const b = Math.round(color.b * 255);
  return `rgba(${r}, ${g}, ${b}, ${alpha})`;
}

function dotTexture(hex: string) {
  const size = 128;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const c = size / 2;
  const color = new THREE.Color(hex);

  const halo = ctx.createRadialGradient(c, c, size * 0.28, c, c, size * 0.5);
  halo.addColorStop(0, rgba(color, 0.45));
  halo.addColorStop(0.55, rgba(color, 0.12));
  halo.addColorStop(1, rgba(color, 0));
  ctx.fillStyle = halo;
  ctx.beginPath();
  ctx.arc(c, c, c, 0, TAU);
  ctx.fill();

  ctx.beginPath();
  ctx.arc(c, c, size * 0.28, 0, TAU);
  ctx.fillStyle = "rgba(20, 28, 32, 0.28)";
  ctx.fill();

  ctx.beginPath();
  ctx.arc(c, c, size * 0.255, 0, TAU);
  ctx.fillStyle = "rgba(255, 252, 245, 0.96)";
  ctx.fill();

  ctx.beginPath();
  ctx.arc(c, c, size * 0.16, 0, TAU);
  ctx.fillStyle = rgba(color, 1);
  ctx.fill();

  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

function ringTexture() {
  const size = 128;
  const canvas = document.createElement("canvas");
  canvas.width = canvas.height = size;
  const ctx = canvas.getContext("2d")!;
  const c = size / 2;
  ctx.strokeStyle = "rgba(255, 255, 255, 1)";
  ctx.lineWidth = size * 0.04;
  ctx.beginPath();
  ctx.arc(c, c, size * 0.4, 0, TAU);
  ctx.stroke();
  const texture = new THREE.CanvasTexture(canvas);
  texture.colorSpace = THREE.SRGBColorSpace;
  return texture;
}

export class HotspotLayer {
  private markers: Marker[] = [];
  private ring = ringTexture();
  private group = new THREE.Group();
  private pixelScale = 0.018;
  private time = 0;
  private selectedAt = -PULSE_SECONDS;
  private lastSelectedId: string | null = null;

  private readonly world = new THREE.Vector3();
  private readonly toCamera = new THREE.Vector3();
  private readonly projected = new THREE.Vector3();
  private readonly lift = new THREE.Vector3();
  private readonly center = new THREE.Vector3();
  private readonly outward = new THREE.Vector3();

  constructor() {
    this.group.name = "acupoint-hotspots";
    this.group.renderOrder = 10;
  }

  get list(): readonly Marker[] {
    return this.markers;
  }

  attach(
    parent: THREE.Object3D,
    points: Acupoint[],
    colorById: Record<string, string>,
    meshes: THREE.Mesh[] = [],
  ) {
    this.clear();
    parent.add(this.group);

    const authored = points.map((p) => new THREE.Vector3(...p.position));
    const anchors = snapPointsToSurface(authored, parent, meshes);

    points.forEach((point, index) => {
      const color = colorById[point.meridianId] ?? "#c45c48";
      const anchor = anchors[index];

      const dot = new THREE.Sprite(
        new THREE.SpriteMaterial({
          map: dotTexture(color),
          transparent: true,
          depthWrite: false,
          depthTest: true,
          sizeAttenuation: false,
          toneMapped: false,
          polygonOffset: true,
          polygonOffsetFactor: -4,
          polygonOffsetUnits: -12,
        }),
      );
      dot.position.copy(anchor);
      dot.renderOrder = 11;
      dot.userData.pointId = point.id;

      const pulse = new THREE.Sprite(
        new THREE.SpriteMaterial({
          map: this.ring,
          color: new THREE.Color(color),
          transparent: true,
          depthWrite: false,
          depthTest: true,
          sizeAttenuation: false,
          toneMapped: false,
          opacity: 0,
        }),
      );
      pulse.position.copy(anchor);
      pulse.renderOrder = 10;
      pulse.scale.setScalar(0.001);

      this.group.add(dot, pulse);
      this.markers.push({
        point,
        color,
        dot,
        pulse,
        anchor: anchor.clone(),
        opacity: 1,
        emphasis: 0,
      });
    });
  }

  clear() {
    for (const marker of this.markers) {
      marker.dot.material.map?.dispose();
      marker.dot.material.dispose();
      marker.pulse.material.dispose();
      this.group.remove(marker.dot, marker.pulse);
    }
    this.markers = [];
    this.group.removeFromParent();
  }

  setSelected(id: string | null) {
    if (id !== this.lastSelectedId) {
      this.lastSelectedId = id;
      this.selectedAt = this.time;
    }
  }

  pick(normalizedX: number, normalizedY: number, camera: THREE.Camera): Acupoint | null {
    let best: Marker | null = null;
    let bestDist = 0.045;

    for (const marker of this.markers) {
      if (marker.opacity < 0.25) continue;
      this.projected.copy(marker.dot.position).project(camera);
      const dx = this.projected.x - normalizedX;
      const dy = this.projected.y - normalizedY;
      const dist = Math.hypot(dx, dy);
      if (dist < bestDist) {
        bestDist = dist;
        best = marker;
      }
    }
    return best?.point ?? null;
  }

  projectCallout(
    id: string,
    camera: THREE.Camera,
    width: number,
    height: number,
  ): { x: number; y: number; visible: boolean } | null {
    const marker = this.markers.find((m) => m.point.id === id);
    if (!marker) return null;
    this.projected.copy(marker.dot.position).project(camera);
    const visible =
      marker.opacity > 0.2 &&
      this.projected.z < 1 &&
      this.projected.x > -1.2 &&
      this.projected.x < 1.2 &&
      this.projected.y > -1.2 &&
      this.projected.y < 1.2;
    return {
      x: (this.projected.x * 0.5 + 0.5) * width,
      y: (-this.projected.y * 0.5 + 0.5) * height,
      visible,
    };
  }

  update(dt: number, camera: THREE.Camera, selectedId: string | null, hoveredId: string | null) {
    this.time += dt;
    this.setSelected(selectedId);
    const pulseAge = this.time - this.selectedAt;
    this.group.getWorldPosition(this.center);

    for (const marker of this.markers) {
      marker.dot.getWorldPosition(this.world);
      this.outward.copy(this.world).sub(this.center);
      const radius = this.outward.length();
      this.toCamera.copy(camera.position).sub(this.world).normalize();
      const facing = radius > 1e-4 ? this.outward.normalize().dot(this.toCamera) : 1;
      const targetOpacity = THREE.MathUtils.smoothstep(facing, -0.08, 0.28);
      marker.opacity = THREE.MathUtils.damp(marker.opacity, Math.max(0.12, targetOpacity), 8, dt);

      const selected = marker.point.id === selectedId;
      const hovered = marker.point.id === hoveredId;
      const targetEmphasis = selected ? 1 : hovered ? 0.55 : 0;
      marker.emphasis = THREE.MathUtils.damp(marker.emphasis, targetEmphasis, 10, dt);

      this.lift.copy(camera.position).sub(this.world);
      if (this.lift.lengthSq() > 1e-6) {
        this.lift.normalize().multiplyScalar(VIEW_LIFT * (0.35 + marker.emphasis * 0.4));
      } else {
        this.lift.set(0, 0, 0);
      }
      marker.dot.position.copy(marker.anchor).add(this.lift);
      marker.pulse.position.copy(marker.dot.position);

      const base = this.pixelScale * (0.9 + marker.emphasis * 0.55);
      marker.dot.scale.setScalar(base);
      (marker.dot.material as THREE.SpriteMaterial).opacity =
        marker.opacity * (0.75 + marker.emphasis * 0.25);

      if (selected && pulseAge < PULSE_SECONDS) {
        const t = pulseAge / PULSE_SECONDS;
        const wave = Math.sin(this.time * 4.2) * 0.5 + 0.5;
        const pulseMat = marker.pulse.material as THREE.SpriteMaterial;
        pulseMat.opacity = (1 - t) * (0.35 + wave * 0.35) * marker.opacity;
        marker.pulse.scale.setScalar(base * (1.4 + wave * 0.9));
      } else {
        (marker.pulse.material as THREE.SpriteMaterial).opacity = 0;
      }
    }
  }

  dispose() {
    this.clear();
    this.ring.dispose();
  }
}
