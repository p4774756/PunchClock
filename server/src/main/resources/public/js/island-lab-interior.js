// 研發樓小室內。低多邊形機房＋自發光進度螢幕；進房時讀一次 /api/dev-status。

import { interiorByBuilding } from './island-interior.js';
import { formatLabDevScreenLines } from './island-roadmap.js';

const SCREEN_W = 1024;
const SCREEN_H = 768;

function drawScreenCanvas(canvas, lines) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.fillStyle = '#07111f';
  ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);
  ctx.fillStyle = '#9ae8ff';
  ctx.font = '600 40px "PingFang TC", "Noto Sans TC", sans-serif';
  const list = Array.isArray(lines) ? lines : [];
  let y = 56;
  const lineH = 52;
  const pad = 48;
  const maxW = SCREEN_W - pad * 2;
  for (let i = 0; i < list.length; i++) {
    const raw = list[i] == null ? '' : String(list[i]);
    if (!raw) {
      y += lineH * 0.55;
      continue;
    }
    let text = raw;
    while (text.length) {
      let fit = text.length;
      while (fit > 0 && ctx.measureText(text.slice(0, fit)).width > maxW) fit -= 1;
      if (fit < 1) fit = 1;
      const chunk = text.slice(0, fit);
      ctx.fillText(chunk, pad, y);
      text = text.slice(fit);
      y += lineH;
      if (y > SCREEN_H - 24) return;
    }
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
  const loading = formatLabDevScreenLines(null, { maxQueue: 4 });
  loading[2] = '載入中…';
  apply(loading);
  if (!fetchFn) {
    apply(formatLabDevScreenLines({ success: false, message: '開發進度暫時讀不到' }));
    return;
  }
  fetchFn('/api/dev-status')
    .then((res) => res.json())
    .then((body) => {
      apply(formatLabDevScreenLines(body, { maxQueue: 4 }));
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
  const southZ = floor.minZ - wallT / 2;
  const leftW = (width - doorW) / 2;
  box(leftW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, floor.minX + leftW / 2, wallY, southZ);
  box(leftW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, floor.maxX - leftW / 2, wallY, southZ);
  box(doorW + 0.14, 0.14, wallT + 0.02, 0x8eb8d8, 0x7a98b8, 0, ground + wallH - 0.08, southZ);

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

  const screenW = 1.4;
  const screenH = 0.95;
  const screenY = ground + 1.15;
  const screenZ = floor.minZ + 0.06;
  const screenX = floor.maxX - leftW / 2;
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