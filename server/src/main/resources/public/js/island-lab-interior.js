// 研發樓小室內。低多邊形機房＋自發光進度螢幕；進房時讀一次 /api/dev-status。

import { interiorByBuilding } from './island-interior.js';
import { formatLabDevScreenLines, fitCanvasLine } from './island-roadmap.js';

const SCREEN_W = 1024;
const SCREEN_H = 768;

function drawScreenLine(ctx, raw, pad, y, maxW, lineH) {
  const text = raw == null ? '' : String(raw);
  if (!text) return y + lineH * 0.55;
  const fitted = fitCanvasLine(ctx, text, maxW);
  if (ctx.measureText(fitted).width <= maxW) {
    ctx.fillText(fitted, pad, y);
    return y + lineH;
  }
  const colon = text.indexOf('：');
  if (colon > 0 && colon < text.length - 1) {
    const head = text.slice(0, colon + 1);
    const tail = fitCanvasLine(ctx, text.slice(colon + 1).trim(), maxW);
    if (ctx.measureText(head).width <= maxW) {
      ctx.fillText(head, pad, y);
      ctx.fillText(tail, pad, y + lineH * 0.88);
      return y + lineH * 1.72;
    }
  }
  ctx.fillText(fitted, pad, y);
  return y + lineH;
}

function drawScreenCanvas(canvas, lines) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.fillStyle = '#07111f';
  ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);
  ctx.fillStyle = '#9ae8ff';
  ctx.font = '600 38px "PingFang TC", "Noto Sans TC", sans-serif';
  const list = Array.isArray(lines) ? lines : [];
  let y = 52;
  const lineH = 46;
  const pad = 48;
  const maxW = SCREEN_W - pad * 2;
  for (let i = 0; i < list.length; i++) {
    y = drawScreenLine(ctx, list[i], pad, y, maxW, lineH);
    if (y > SCREEN_H - 20) return;
  }
}

function loadDevScreen(texture, canvas, loadState) {
  const disposed = () => loadState && loadState.disposed;
  const apply = (lines) => {
    if (disposed()) return;
    drawScreenCanvas(canvas, lines);
    texture.needsUpdate = true;
  };
  const fetchFn = globalThis.fetch ? globalThis.fetch.bind(globalThis) : null;
  const loading = formatLabDevScreenLines(null, { maxQueue: 3 });
  loading[2] = '載入中…';
  apply(loading);
  if (!fetchFn) {
    apply(formatLabDevScreenLines({ success: false, message: '開發進度暫時讀不到' }));
    return;
  }
  fetchFn('/api/dev-status')
    .then((res) => res.json())
    .then((body) => {
      apply(formatLabDevScreenLines(body, { maxQueue: 3 }));
    })
    .catch(() => {
      apply(formatLabDevScreenLines({ success: false, message: '開發進度暫時讀不到' }));
    });
}

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('computer-site');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const wallH = 2.2;
  const wallT = 0.1;
  const materials = [];
  const geometries = [];
  let screenTexture = null;
  let screenCanvas = null;
  const screenLoad = { disposed: false };

  function mat(color, night, glow, emissiveI) {
    const material = new THREE.MeshLambertMaterial({
      color: color,
      flatShading: true,
      emissive: new THREE.Color(glow || 0x000000),
      emissiveIntensity: emissiveI || 0
    });
    material.userData.day = new THREE.Color(color);
    material.userData.night = new THREE.Color(night);
    material.userData.dayE = emissiveI || 0;
    material.userData.nightE = glow ? Math.max(emissiveI || 0, 0.95) : 0;
    materials.push(material);
    return material;
  }

  function box(w, h, d, color, night, x, y, z, glow, emissiveI) {
    const geometry = new THREE.BoxGeometry(w, h, d);
    geometries.push(geometry);
    const mesh = new THREE.Mesh(geometry, mat(color, night, glow, emissiveI));
    mesh.position.set(x, y, z);
    mesh.castShadow = true;
    mesh.receiveShadow = true;
    group.add(mesh);
    return mesh;
  }

  const group = new THREE.Group();
  const wallY = ground + wallH / 2;
  box(width + wallT * 2, 0.1, depth + wallT * 2, 0x2a3548, 0x5a6878, cx, ground + 0.05, cz);
  box(width, 0.04, depth, 0x3d4a60, 0x7a8aa0, cx, ground + 0.1, cz);

  box(wallT, wallH, depth + wallT, 0xd8e8f8, 0x8ea0bf, floor.minX - wallT / 2, wallY, cz);
  box(wallT, wallH, depth + wallT, 0xd8e8f8, 0x8ea0bf, floor.maxX + wallT / 2, wallY, cz);
  box(width + wallT * 2, wallH, wallT, 0xd8e8f8, 0x8ea0bf, cx, wallY, floor.maxZ + wallT / 2);

  const doorW = 0.88;
  const doorX = -1.5;
  const southZ = floor.minZ - wallT / 2;
  const westSegW = doorX - doorW / 2 - floor.minX;
  if (westSegW > 0.02) {
    box(westSegW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, floor.minX + westSegW / 2, wallY, southZ);
  }
  const eastSegW = floor.maxX - (doorX + doorW / 2);
  box(eastSegW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, doorX + doorW / 2 + eastSegW / 2, wallY, southZ);
  box(doorW + 0.14, 0.14, wallT + 0.02, 0x8eb8d8, 0x7a98b8, doorX, ground + wallH - 0.08, southZ);
  box(doorW * 0.9, 1.32, 0.06, 0xc8dce8, 0x6a8898, doorX, ground + 0.71, southZ + 0.02);
  box(0.06, 1.38, 0.08, 0x3cf0ff, 0x3cf0ff, doorX - doorW / 2 - 0.03, ground + 0.74, southZ + 0.03, 0x3cf0ff, 0.45);
  box(0.06, 1.38, 0.08, 0x3cf0ff, 0x3cf0ff, doorX + doorW / 2 + 0.03, ground + 0.74, southZ + 0.03, 0x3cf0ff, 0.45);
  box(doorW * 0.82, 0.02, 0.52, 0x2a8898, 0x1a5868, doorX, ground + 0.12, floor.minZ + 0.38);
  box(0.22, 0.03, 0.14, 0x9ae8ff, 0x5ac8e8, doorX, ground + 0.14, floor.minZ + 0.24, 0x7ad8ff, 0.35);
  box(0.36, 0.1, 0.04, 0x07111f, 0x2a8898, doorX, ground + 1.92, southZ + 0.05, 0x3cf0ff, 0.55);

  const rack = room.blocks[0];
  const rw = rack.maxX - rack.minX;
  const rd = rack.maxZ - rack.minZ;
  const rcx = (rack.minX + rack.maxX) / 2;
  const rcz = (rack.minZ + rack.maxZ) / 2;
  box(rw, 1.05, rd, 0x243040, 0x506878, rcx, ground + 0.62, rcz);
  box(rw - 0.08, 0.04, rd - 0.08, 0x3cf0ff, 0x5aa8b8, rcx, ground + 0.35, rcz, 0x3cf0ff, 0.35);

  const bench = room.blocks[1];
  const bw = bench.maxX - bench.minX;
  const bd = bench.maxZ - bench.minZ;
  const bcx = (bench.minX + bench.maxX) / 2;
  const bcz = (bench.minZ + bench.maxZ) / 2;
  box(bw, 0.42, bd, 0x506070, 0x788898, bcx, ground + 0.31, bcz);

  const screenW = 1.35;
  const screenH = 0.88;
  const screenY = ground + 0.84;
  const screenZ = floor.minZ + 0.06;
  const screenX = 0;
  screenCanvas = typeof document !== 'undefined' && document.createElement
    ? document.createElement('canvas')
    : null;
  if (screenCanvas) {
    screenCanvas.width = SCREEN_W;
    screenCanvas.height = SCREEN_H;
    screenTexture = new THREE.CanvasTexture(screenCanvas);
    screenTexture.minFilter = THREE.LinearFilter;
    screenTexture.magFilter = THREE.LinearFilter;
    const screenMat = new THREE.MeshLambertMaterial({
      map: screenTexture,
      emissive: new THREE.Color(0x3cf0ff),
      emissiveMap: screenTexture,
      emissiveIntensity: 0.55,
      flatShading: true
    });
    screenMat.userData.day = new THREE.Color(0xffffff);
    screenMat.userData.night = new THREE.Color(0x90c8e0);
    screenMat.userData.dayE = 0.45;
    screenMat.userData.nightE = 0.85;
    materials.push(screenMat);
    const screenGeo = new THREE.PlaneGeometry(screenW, screenH);
    geometries.push(screenGeo);
    const screenMesh = new THREE.Mesh(screenGeo, screenMat);
    screenMesh.position.set(screenX, screenY, screenZ);
    group.add(screenMesh);
    box(screenW + 0.12, screenH + 0.12, 0.05, 0x1a2838, 0x3a5068, screenX, screenY, screenZ - 0.03);
    loadDevScreen(screenTexture, screenCanvas, screenLoad);
  } else {
    box(screenW, screenH, 0.06, 0x07111f, 0x07111f, screenX, screenY, screenZ, 0x3cf0ff, 0.7);
  }

  return {
    group: group,
    materials: materials,
    dispose() {
      screenLoad.disposed = true;
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => {
        if (material.map) material.map.dispose();
        material.dispose();
      });
      screenTexture = null;
      screenCanvas = null;
    }
  };
}