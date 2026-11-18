// 研發樓小室內。低多邊形機房＋自發光進度螢幕；進房時讀 /api/dev-status，製茶機牆輪詢 /api/tea-health。

import { interiorByBuilding } from './island-interior.js';
import { fitCanvasLine } from './island-roadmap.js';
import {
  appendLabMachineLines,
  formatLabDevMachineLines,
  shouldFetchDevMachines
} from './dev-machines-format.js';
import {
  drawTeaHealthCanvas,
  formatLabDevScreenWithTimeline,
  insertLabTeaStatusLine,
  labTeaWallFrameOffset,
  labTeaWallLayout,
  layoutLabScreenLines
} from './island-lab-health.js';
import {
  labInteriorCameraEye,
  labInteriorCameraForAspect,
  labInteriorLookTarget
} from './island-interior.js';

const SCREEN_W = 1024;
const SCREEN_H = 768;
const HEALTH_W = 512;
const HEALTH_H = 384;
const TEA_POLL_MS = 5000;

function configureLabCanvasTexture(texture, THREE, renderer) {
  texture.generateMipmaps = true;
  texture.minFilter = THREE.LinearMipmapLinearFilter;
  texture.magFilter = THREE.LinearFilter;
  if (renderer && renderer.capabilities && typeof renderer.capabilities.getMaxAnisotropy === 'function') {
    const max = renderer.capabilities.getMaxAnisotropy();
    if (Number.isFinite(max) && max > 0) {
      texture.anisotropy = Math.min(4, max);
    }
  }
}

function drawScreenCanvas(canvas, lines) {
  const ctx = canvas.getContext('2d');
  if (!ctx) return;
  ctx.fillStyle = '#07111f';
  ctx.fillRect(0, 0, SCREEN_W, SCREEN_H);
  ctx.fillStyle = '#9ae8ff';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  const pad = 48;
  const maxW = SCREEN_W - pad * 2;
  const rows = layoutLabScreenLines(lines);
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    ctx.font = row.font;
    const fitted = fitCanvasLine(ctx, row.text, maxW);
    ctx.fillText(fitted, pad, row.y);
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

function composeLabScreenLines(devBody, machines, teaData) {
  let lines = formatLabDevScreenWithTimeline(devBody, {
    screen: true,
    maxQueue: 2,
    maxTimeline: 5
  });
  if (machines && machines.success !== false) {
    lines = appendLabMachineLines(lines, formatLabDevMachineLines(machines));
  }
  return insertLabTeaStatusLine(lines, teaData);
}

function loadDevScreen(texture, canvas, screenState) {
  const disposed = () => screenState && screenState.disposed;
  screenState.refresh = () => {
    if (disposed() || !canvas) return;
    let lines;
    if (screenState.devBody == null) {
      lines = insertLabTeaStatusLine(['【開發進度】', '載入中…'], screenState.teaData);
    } else if (screenState.devBody.success === false) {
      lines = composeLabScreenLines(
        screenState.devBody,
        null,
        screenState.teaData
      );
    } else {
      lines = composeLabScreenLines(
        screenState.devBody,
        screenState.machines,
        screenState.teaData
      );
    }
    drawScreenCanvas(canvas, lines);
    if (texture) texture.needsUpdate = true;
  };
  const fetchFn = globalThis.fetch ? globalThis.fetch.bind(globalThis) : null;
  screenState.teaData = { success: true };
  screenState.refresh();
  if (!fetchFn) {
    screenState.devBody = { success: false, message: '開發進度暫時讀不到' };
    screenState.refresh();
    return;
  }
  const authed = typeof globalThis !== 'undefined' && globalThis.PUNCHCLOCK_AUTHENTICATED === true;
  const devStatusPromise = fetchFn('/api/dev-status').then((res) => res.json());
  const machinesPromise = shouldFetchDevMachines(authed)
    ? fetchFn('/api/dev-machines')
        .then((res) => (res.ok ? res.json() : null))
        .catch(() => null)
    : Promise.resolve(null);
  Promise.all([devStatusPromise, machinesPromise])
    .then(([body, machines]) => {
      if (disposed()) return;
      screenState.devBody = body;
      screenState.machines = machines;
      screenState.refresh();
    })
    .catch(() => {
      if (disposed()) return;
      screenState.devBody = { success: false, message: '開發進度暫時讀不到' };
      screenState.refresh();
    });
}

function loadTeaHealthWall(texture, canvas, loadState, onSample) {
  const disposed = () => loadState && loadState.disposed;
  const fetchFn = globalThis.fetch ? globalThis.fetch.bind(globalThis) : null;
  let timerId = null;
  let abortCtrl = null;

  const paint = (data) => {
    if (disposed() || !canvas) return;
    drawTeaHealthCanvas(canvas.getContext('2d'), data, HEALTH_W, HEALTH_H);
    if (texture) texture.needsUpdate = true;
    if (onSample && !disposed()) onSample(data);
  };

  const refresh = () => {
    if (disposed()) return;
    if (abortCtrl) abortCtrl.abort();
    if (!fetchFn) {
      paint({ success: false });
      return;
    }
    abortCtrl = typeof AbortController !== 'undefined' ? new AbortController() : null;
    const signal = abortCtrl ? abortCtrl.signal : undefined;
    fetchFn('/api/tea-health', signal ? { signal: signal } : undefined)
      .then((res) => {
        if (!res.ok) throw new Error('http');
        return res.json();
      })
      .then((body) => {
        if (disposed()) return;
        paint(body);
      })
      .catch((err) => {
        if (disposed()) return;
        if (err && err.name === 'AbortError') return;
        paint({ success: false });
      });
  };

  paint({ success: true });
  refresh();
  timerId = setInterval(refresh, TEA_POLL_MS);
  loadState.cleanup = () => {
    if (timerId != null) {
      clearInterval(timerId);
      timerId = null;
    }
    if (abortCtrl) {
      abortCtrl.abort();
      abortCtrl = null;
    }
  };
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
  let healthTexture = null;
  let healthCanvas = null;
  const exitTextures = [];
  const screenLoad = { disposed: false, devBody: null, machines: null, teaData: null };
  const teaLoad = { disposed: false };

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

  const wallEyeAspect = 1280 / 720;
  const wallPose = labInteriorCameraForAspect(wallEyeAspect);
  const wallLook = labInteriorLookTarget(room, { x: room.spawn.x, z: room.spawn.z }, wallEyeAspect);
  const wallEye = labInteriorCameraEye(wallPose, wallLook, wallEyeAspect);
  const teaWall = labTeaWallLayout(ground, wallEye.x, wallEye.z);
  const healthPlaneW = teaWall.w;
  const healthPlaneH = teaWall.h;
  healthCanvas = typeof document !== 'undefined' && document.createElement
    ? document.createElement('canvas')
    : null;
  if (healthCanvas) {
    healthCanvas.width = HEALTH_W;
    healthCanvas.height = HEALTH_H;
    healthTexture = new THREE.CanvasTexture(healthCanvas);
    configureLabCanvasTexture(healthTexture, THREE, opts && opts.renderer);
    const healthMat = new THREE.MeshLambertMaterial({
      map: healthTexture,
      emissive: new THREE.Color(0x3cf0ff),
      emissiveMap: healthTexture,
      emissiveIntensity: 0.58,
      flatShading: true
    });
    healthMat.userData.day = new THREE.Color(0xffffff);
    healthMat.userData.night = new THREE.Color(0x90c8e0);
    healthMat.userData.dayE = 0.48;
    healthMat.userData.nightE = 0.9;
    materials.push(healthMat);
    const healthGeo = new THREE.PlaneGeometry(healthPlaneW, healthPlaneH);
    geometries.push(healthGeo);
    const healthMesh = new THREE.Mesh(healthGeo, healthMat);
    healthMesh.rotation.y = teaWall.ry;
    healthMesh.position.set(teaWall.cx, teaWall.cy, teaWall.cz);
    group.add(healthMesh);
    const framePos = labTeaWallFrameOffset(teaWall);
    const frameW = healthPlaneW + 0.08;
    const frameH = healthPlaneH + 0.08;
    const frameGeo = new THREE.BoxGeometry(frameW, frameH, 0.04);
    geometries.push(frameGeo);
    const frameMat = mat(0x1a2838, 0x3a5068, 0x3cf0ff, 0.25);
    const frameMesh = new THREE.Mesh(frameGeo, frameMat);
    frameMesh.rotation.y = teaWall.ry;
    frameMesh.position.set(framePos.x, framePos.y, framePos.z);
    frameMesh.castShadow = true;
    frameMesh.receiveShadow = true;
    group.add(frameMesh);
    loadTeaHealthWall(healthTexture, healthCanvas, teaLoad, (data) => {
      if (screenLoad.disposed) return;
      screenLoad.teaData = data;
      if (screenLoad.refresh) screenLoad.refresh();
    });
  }

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
    configureLabCanvasTexture(screenTexture, THREE, opts && opts.renderer);
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
      teaLoad.disposed = true;
      if (screenLoad.cleanup) screenLoad.cleanup();
      if (teaLoad.cleanup) teaLoad.cleanup();
      geometries.forEach((geometry) => geometry.dispose());
      materials.forEach((material) => {
        if (material.map) material.map.dispose();
        material.dispose();
      });
      screenTexture = null;
      screenCanvas = null;
      healthTexture = null;
      healthCanvas = null;
      for (let i = 0; i < exitTextures.length; i++) {
        if (exitTextures[i].texture) exitTextures[i].texture.dispose();
      }
      exitTextures.length = 0;
    }
  };
}