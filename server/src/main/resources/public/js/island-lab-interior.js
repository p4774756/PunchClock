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

function drawExitLabel(ctx, w, h, subtitle) {
  ctx.fillStyle = '#07111f';
  ctx.fillRect(0, 0, w, h);
  ctx.fillStyle = '#9ae8ff';
  ctx.font = '700 40px "PingFang TC", "Noto Sans TC", sans-serif';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('出口', w / 2, h * (subtitle ? 0.42 : 0.5));
  if (subtitle) {
    ctx.font = '600 26px "PingFang TC", "Noto Sans TC", sans-serif';
    ctx.fillText('↑', w / 2, h * 0.72);
  }
}

function exitCanvasTexture(THREE, opts) {
  const w = (opts && opts.w) || 128;
  const h = (opts && opts.h) || 64;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  drawExitLabel(canvas.getContext('2d'), w, h, !!(opts && opts.arrow));
  const texture = new THREE.CanvasTexture(canvas);
  texture.minFilter = THREE.LinearFilter;
  texture.magFilter = THREE.LinearFilter;
  return { texture: texture, canvas: canvas };
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
  const exitTextures = [];
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
  // 北牆剖面：鏡頭可於房外北側拉遠；南牆門與螢幕維持實牆。
  const northKickH = 0.14;
  box(width + wallT * 2, northKickH, wallT, 0xd8e8f8, 0x8ea0bf, cx, ground + northKickH / 2, floor.maxZ + wallT / 2);

  const doorW = 0.88;
  const doorX = -1.5;
  const southZ = floor.minZ - wallT / 2;
  const westSegW = doorX - doorW / 2 - floor.minX;
  if (westSegW > 0.02) {
    box(westSegW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, floor.minX + westSegW / 2, wallY, southZ);
  }
  const eastSegW = floor.maxX - (doorX + doorW / 2);
  box(eastSegW, wallH, wallT, 0xd8e8f8, 0x8ea0bf, doorX + doorW / 2 + eastSegW / 2, wallY, southZ);
  const lintelY = ground + wallH - 0.22;
  box(doorW + 0.14, 0.14, wallT + 0.02, 0x8eb8d8, 0x7a98b8, doorX, lintelY, southZ, 0x5ac8e8, 0.48);
  box(doorW * 0.9, 1.32, 0.06, 0xc8dce8, 0x6a8898, doorX, ground + 0.71, southZ + 0.02, 0x3cf0ff, 0.28);
  box(0.06, 1.38, 0.08, 0x3cf0ff, 0x3cf0ff, doorX - doorW / 2 - 0.03, ground + 0.74, southZ + 0.03, 0x3cf0ff, 0.55);
  box(0.06, 1.38, 0.08, 0x3cf0ff, 0x3cf0ff, doorX + doorW / 2 + 0.03, ground + 0.74, southZ + 0.03, 0x3cf0ff, 0.55);
  box(doorW * 0.82, 0.02, 0.52, 0x2a8898, 0x2a8898, doorX, ground + 0.12, floor.minZ + 0.38, 0x3cf0ff, 0.62);
  box(0.22, 0.03, 0.14, 0x9ae8ff, 0x7ad8ff, doorX, ground + 0.14, floor.minZ + 0.24, 0x7ad8ff, 0.45);

  if (typeof document !== 'undefined' && document.createElement) {
    const header = exitCanvasTexture(THREE, { w: 128, h: 64 });
    const headerMat = new THREE.MeshLambertMaterial({
      map: header.texture,
      emissive: new THREE.Color(0x3cf0ff),
      emissiveMap: header.texture,
      emissiveIntensity: 0.55,
      flatShading: true,
      transparent: true
    });
    headerMat.userData.day = new THREE.Color(0xffffff);
    headerMat.userData.night = new THREE.Color(0x90c8e0);
    headerMat.userData.dayE = 0.5;
    headerMat.userData.nightE = 0.92;
    materials.push(headerMat);
    exitTextures.push(header);
    const headerGeo = new THREE.PlaneGeometry(0.34, 0.14);
    geometries.push(headerGeo);
    const headerMesh = new THREE.Mesh(headerGeo, headerMat);
    headerMesh.position.set(-1.0, ground + 1.56, southZ + 0.06);
    group.add(headerMesh);

    const matLabel = exitCanvasTexture(THREE, { w: 128, h: 96, arrow: true });
    const matMat = new THREE.MeshLambertMaterial({
      map: matLabel.texture,
      emissive: new THREE.Color(0x3cf0ff),
      emissiveMap: matLabel.texture,
      emissiveIntensity: 0.42,
      flatShading: true,
      transparent: true
    });
    matMat.userData.day = new THREE.Color(0xffffff);
    matMat.userData.night = new THREE.Color(0x90c8e0);
    matMat.userData.dayE = 0.38;
    matMat.userData.nightE = 0.9;
    materials.push(matMat);
    exitTextures.push(matLabel);
    const matGeo = new THREE.PlaneGeometry(0.36, 0.22);
    geometries.push(matGeo);
    const matMesh = new THREE.Mesh(matGeo, matMat);
    matMesh.rotation.x = -Math.PI / 2;
    matMesh.position.set(-1.0, ground + 0.125, floor.minZ + 0.39);
    group.add(matMesh);
  }

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
      for (let i = 0; i < exitTextures.length; i++) {
        if (exitTextures[i].texture) exitTextures[i].texture.dispose();
      }
      exitTextures.length = 0;
    }
  };
}