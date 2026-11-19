// 研發樓室內：挑高科幻大廳、數據牆、機房與職涯文物廳；輪詢 /api/dev-status 與 /api/tea-health。

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
import {
  CAREER_TIMELINE_URL,
  buildCardTextModel,
  careerCardHeading,
  pickWhitelistFields
} from './island-career-corridor-logic.js';

const SCREEN_W = 1024;
const SCREEN_H = 768;
const HEALTH_W = 512;
const HEALTH_H = 384;
const TEA_POLL_MS = 5000;
const PLAQUE_W = 384;
const PLAQUE_H = 288;
const CEILING_Y = 4.35;

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

function drawScreenCanvas(canvas, lines, scanY) {
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
  if (Number.isFinite(scanY)) {
    const y = scanY % SCREEN_H;
    const grad = ctx.createLinearGradient(0, y - 18, 0, y + 18);
    grad.addColorStop(0, 'rgba(60, 240, 255, 0)');
    grad.addColorStop(0.45, 'rgba(60, 240, 255, 0.12)');
    grad.addColorStop(0.5, 'rgba(180, 255, 255, 0.28)');
    grad.addColorStop(0.55, 'rgba(60, 240, 255, 0.12)');
    grad.addColorStop(1, 'rgba(60, 240, 255, 0)');
    ctx.fillStyle = grad;
    ctx.fillRect(0, y - 18, SCREEN_W, 36);
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

function drawCareerPlaque(ctx, w, h, model) {
  ctx.fillStyle = '#0a1628';
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = '#3cf0ff';
  ctx.lineWidth = 3;
  ctx.strokeRect(6, 6, w - 12, h - 12);
  ctx.fillStyle = '#9ae8ff';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  const heading = careerCardHeading(model);
  ctx.font = '700 26px "PingFang TC", "Noto Sans TC", sans-serif';
  ctx.fillText(heading, 18, 18);
  ctx.font = '600 20px "PingFang TC", "Noto Sans TC", sans-serif';
  ctx.fillStyle = '#7ad8e8';
  const meta = model.role ? model.years + ' · ' + model.role : model.years;
  ctx.fillText(meta, 18, 52);
  ctx.fillStyle = '#b8e8ff';
  ctx.font = '400 18px "PingFang TC", "Noto Sans TC", sans-serif';
  const summary = model.summary || '';
  const maxW = w - 36;
  let y = 84;
  const words = summary.split('');
  let line = '';
  for (let i = 0; i < words.length; i++) {
    const test = line + words[i];
    if (ctx.measureText(test).width > maxW && line) {
      ctx.fillText(line, 18, y);
      y += 24;
      line = words[i];
    } else {
      line = test;
    }
    if (y > h - 58) break;
  }
  if (line && y <= h - 58) ctx.fillText(line, 18, y);
  if (model.prop) {
    ctx.fillStyle = '#5ad0e0';
    ctx.font = '500 16px "PingFang TC", "Noto Sans TC", sans-serif';
    ctx.fillText('展件：' + model.prop, 18, h - 38);
  }
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

function loadDevScreen(texture, canvas, screenState, reducedMotion) {
  const disposed = () => screenState && screenState.disposed;
  screenState.scanY = 0;
  screenState.reducedMotion = !!reducedMotion;
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
    const scan = screenState.reducedMotion ? null : screenState.scanY;
    drawScreenCanvas(canvas, lines, scan);
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
  const devStatusPromise = fetchFn('/api/dev-status').then((res) => res.json());
  const machinesPromise = shouldFetchDevMachines()
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

const MUSEUM_PLAQUES = Object.freeze([
  { entryId: 'thu-2007', x: -6.35, z: 7.75, ry: Math.PI / 2 },
  { entryId: 'firmware-2013-2022', x: -6.35, z: 9.65, ry: Math.PI / 2 },
  { entryId: 'software-2022-now', x: -6.35, z: 11.55, ry: Math.PI / 2 },
  { entryId: 'punchclock', x: -4.15, z: 9.55, ry: -Math.PI / 2 }
]);

function loadCareerHall(THREE, opts, materials, geometries, group, plaqueTextures, loadState) {
  const disposed = () => loadState && loadState.disposed;
  const fetchFn = globalThis.fetch ? globalThis.fetch.bind(globalThis) : null;
  if (!fetchFn || typeof document === 'undefined' || !document.createElement) return;

  const paintPlaque = (slot, raw) => {
    if (disposed() || !slot.canvas) return;
    const model = buildCardTextModel(pickWhitelistFields(raw));
    drawCareerPlaque(slot.canvas.getContext('2d'), PLAQUE_W, PLAQUE_H, model);
    if (slot.texture) slot.texture.needsUpdate = true;
  };

  fetchFn(CAREER_TIMELINE_URL)
  .then((res) => (res.ok ? res.json() : null))
  .then((data) => {
    if (disposed() || !data || !Array.isArray(data.entries)) return;
    const byId = {};
    for (let i = 0; i < data.entries.length; i++) byId[data.entries[i].id] = data.entries[i];
    for (let p = 0; p < MUSEUM_PLAQUES.length; p++) {
      const spec = MUSEUM_PLAQUES[p];
      const entry = byId[spec.entryId];
      if (!entry) continue;
      const canvas = document.createElement('canvas');
      canvas.width = PLAQUE_W;
      canvas.height = PLAQUE_H;
      const texture = new THREE.CanvasTexture(canvas);
      configureLabCanvasTexture(texture, THREE, opts && opts.renderer);
      const mat = new THREE.MeshLambertMaterial({
        map: texture,
        emissive: new THREE.Color(0x3cf0ff),
        emissiveMap: texture,
        emissiveIntensity: 0.42,
        flatShading: true
      });
      mat.userData.day = new THREE.Color(0xffffff);
      mat.userData.night = new THREE.Color(0x90c8e0);
      mat.userData.dayE = 0.35;
      mat.userData.nightE = 0.82;
      materials.push(mat);
      const geo = new THREE.PlaneGeometry(0.92, 0.68);
      geometries.push(geo);
      const mesh = new THREE.Mesh(geo, mat);
      mesh.rotation.y = spec.ry;
      mesh.position.set(spec.x, opts.groundY + 1.45, spec.z);
      group.add(mesh);
      const slot = { canvas: canvas, texture: texture };
      plaqueTextures.push(slot);
      paintPlaque(slot, entry);
    }
  })
  .catch(() => {});
}

export function createInterior(THREE, opts) {
  const room = interiorByBuilding('computer-site');
  const ground = (opts && opts.groundY != null) ? opts.groundY : 0.2;
  const reducedMotion = !!(opts && opts.reducedMotion);
  const floor = room.floor;
  const width = floor.maxX - floor.minX;
  const depth = floor.maxZ - floor.minZ;
  const cx = (floor.minX + floor.maxX) / 2;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const wallH = 4.6;
  const wallT = 0.12;
  const materials = [];
  const geometries = [];
  let screenTexture = null;
  let screenCanvas = null;
  let healthTexture = null;
  let healthCanvas = null;
  const exitTextures = [];
  const plaqueTextures = [];
  const screenLoad = { disposed: false, devBody: null, machines: null, teaData: null };
  const teaLoad = { disposed: false };
  const museumLoad = { disposed: false };
  let glowPhase = 0;

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

  box(width + wallT * 2, 0.12, depth + wallT * 2, 0x1a2438, 0x3a4a68, cx, ground + 0.06, cz);
  box(width, 0.06, depth, 0x2a3848, 0x4a5a78, cx, ground + 0.12, cz);

  box(wallT, wallH, depth + wallT, 0xc8dce8, 0x7a98b8, floor.minX - wallT / 2, wallY, cz, 0x000000, 0);
  box(wallT, wallH, depth + wallT, 0xc8dce8, 0x7a98b8, floor.maxX + wallT / 2, wallY, cz);
  const northKickH = 0.16;
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
  const lintelY = ground + 2.05;
  box(doorW + 0.14, 0.16, wallT + 0.02, 0x8eb8d8, 0x7a98b8, doorX, lintelY, southZ, 0x3cf0ff, 0.48);
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
    headerMesh.position.set(-1.0, ground + 1.92, southZ + 0.06);
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

  const stripMat = mat(0x2a8898, 0x2a8898, 0x3cf0ff, reducedMotion ? 0.28 : 0.42);
  const stripCount = 9;
  for (let i = 0; i < stripCount; i++) {
    const t = i / (stripCount - 1);
    const x = floor.minX + 1.2 + t * (width - 2.4);
    box(1.6, 0.04, 0.06, 0x2a8898, 0x2a8898, x, CEILING_Y - 0.08, cz, 0x3cf0ff, reducedMotion ? 0.22 : 0.38);
    box(0.05, CEILING_Y - ground - 0.2, 0.05, 0x3a5068, 0x5a7888, x, ground + (CEILING_Y - ground) / 2, cz - 2.5, 0x3cf0ff, 0.12);
  }

  box(0.14, CEILING_Y - ground, depth * 0.82, 0x88b8d0, 0x6090b0, floor.minX + 0.65, (ground + CEILING_Y) / 2, cz + 1.5,
    0x000000, 0);
  box(0.14, CEILING_Y - ground, depth * 0.82, 0x88b8d0, 0x6090b0, floor.maxX - 0.65, (ground + CEILING_Y) / 2, cz + 1.5);

  box(1.1, 4.8, 1.1, 0x2a4058, 0x4a6888, 0, ground + 2.5, 4.85, 0x3cf0ff, 0.08);

  box(2.2, 0.08, 5.5, 0x3a5068, 0x5a7898, 4.8, ground + 0.16, 4.8);
  box(2.2, 0.08, 5.5, 0x3a5068, 0x5a7898, -4.5, ground + 0.16, 8.5);

  box(0.38, 1.05, 0.55, 0x243040, 0x506878, 5.65, ground + 0.62, 2.1);
  box(0.38, 1.05, 0.55, 0x243040, 0x506878, 5.65, ground + 0.62, 3.85);
  box(0.38, 1.05, 0.55, 0x243040, 0x506878, 5.65, ground + 0.62, 5.6);
  box(0.38, 1.05, 0.55, 0x243040, 0x506878, 5.65, ground + 0.62, 7.35);

  const rackGeo = new THREE.BoxGeometry(0.34, 0.92, 0.48);
  geometries.push(rackGeo);
  const rackMat = mat(0x243040, 0x406070, 0x3cf0ff, 0.18);
  const rackCount = 14;
  const racks = new THREE.InstancedMesh(rackGeo, rackMat, rackCount);
  const dummy = new THREE.Object3D();
  let ri = 0;
  const rackXs = [6.0, 6.55, 7.05];
  const rackZs = [2.0, 3.7, 5.4, 7.1, 8.8];
  for (let rx = 0; rx < rackXs.length; rx++) {
    for (let rz = 0; rz < rackZs.length; rz++) {
      if (ri >= rackCount) break;
      dummy.position.set(rackXs[rx], ground + 0.58, rackZs[rz]);
      dummy.updateMatrix();
      racks.setMatrixAt(ri, dummy.matrix);
      ri += 1;
    }
  }
  racks.instanceMatrix.needsUpdate = true;
  racks.castShadow = true;
  racks.receiveShadow = true;
  group.add(racks);

  box(0.55, 0.06, 6.2, 0x3cf0ff, 0x5aa8b8, 6.55, ground + 0.08, 5.2, 0x3cf0ff, 0.35);

  box(1.6, 0.18, 0.12, 0x1a2838, 0x3a5068, -5.5, ground + 1.55, 9.1, 0x3cf0ff, 0.2);
  const signGeo = new THREE.PlaneGeometry(1.45, 0.32);
  geometries.push(signGeo);
  const signCanvas = typeof document !== 'undefined' && document.createElement
    ? document.createElement('canvas') : null;
  if (signCanvas) {
    signCanvas.width = 320;
    signCanvas.height = 72;
    const sctx = signCanvas.getContext('2d');
    sctx.fillStyle = '#07111f';
    sctx.fillRect(0, 0, 320, 72);
    sctx.fillStyle = '#9ae8ff';
    sctx.font = '700 28px "PingFang TC", "Noto Sans TC", sans-serif';
    sctx.textAlign = 'center';
    sctx.fillText('職涯文物廳', 160, 46);
    const signTex = new THREE.CanvasTexture(signCanvas);
    configureLabCanvasTexture(signTex, THREE, opts && opts.renderer);
    const signMat = new THREE.MeshLambertMaterial({
      map: signTex,
      emissive: new THREE.Color(0x3cf0ff),
      emissiveMap: signTex,
      emissiveIntensity: 0.5,
      flatShading: true
    });
    // 進房時 dayNight 會對 kit 裡每份材質做 color.copy(userData.day)；缺了會整段進房失敗。
    signMat.userData.day = new THREE.Color(0xffffff);
    signMat.userData.night = new THREE.Color(0x90c8e0);
    signMat.userData.dayE = 0.45;
    signMat.userData.nightE = 0.88;
    materials.push(signMat);
    const signMesh = new THREE.Mesh(signGeo, signMat);
    signMesh.position.set(-5.5, ground + 2.12, 9.16);
    group.add(signMesh);
    plaqueTextures.push({ texture: signTex, canvas: signCanvas });
  }

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

  const screenW = 2.15;
  const screenH = 1.42;
  const screenY = ground + 0.86;
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
    box(screenW + 0.14, screenH + 0.14, 0.05, 0x1a2838, 0x3a5068, screenX, screenY, screenZ - 0.03, 0x3cf0ff, 0.2);
    loadDevScreen(screenTexture, screenCanvas, screenLoad, reducedMotion);
  } else {
    box(screenW, screenH, 0.06, 0x07111f, 0x07111f, screenX, screenY, screenZ, 0x3cf0ff, 0.7);
  }

  loadCareerHall(THREE, { groundY: ground, renderer: opts && opts.renderer }, materials, geometries, group, plaqueTextures, museumLoad);

  const glowStrips = [];
  for (let i = 0; i < 6; i++) {
    const m = stripMat.clone();
    materials.push(m);
    const geo = new THREE.BoxGeometry(1.4, 0.03, 0.04);
    geometries.push(geo);
    const mesh = new THREE.Mesh(geo, m);
    mesh.position.set(-3 + i * 1.2, CEILING_Y - 0.12, -0.5 + i * 0.35);
    group.add(mesh);
    glowStrips.push(m);
  }

  function tick(dt) {
    if (!reducedMotion && screenLoad.refresh) {
      screenLoad.scanY = (screenLoad.scanY || 0) + dt * 95;
      screenLoad.refresh();
    }
    if (!reducedMotion && glowStrips.length) {
      glowPhase += dt;
      const pulse = 0.32 + 0.14 * Math.sin(glowPhase * 2.2);
      for (let i = 0; i < glowStrips.length; i++) {
        glowStrips[i].emissiveIntensity = pulse;
      }
    }
    return null;
  }

  return {
    group: group,
    materials: materials,
    tick: tick,
    dispose() {
      screenLoad.disposed = true;
      teaLoad.disposed = true;
      museumLoad.disposed = true;
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
      for (let j = 0; j < plaqueTextures.length; j++) {
        if (plaqueTextures[j].texture) plaqueTextures[j].texture.dispose();
      }
      exitTextures.length = 0;
      plaqueTextures.length = 0;
    }
  };
}