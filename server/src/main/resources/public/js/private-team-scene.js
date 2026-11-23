import * as THREE from './vendor/three.module.js';

export const SECRETARY_SPRITE_URL_TYPING = '/assets/private-team/secretary-typing-a.webp';
export const SECRETARY_SPRITE_URL_TYPING_B = '/assets/private-team/secretary-typing-b.webp';
export const SECRETARY_SPRITE_URL_WAVE = '/assets/private-team/secretary-wave.webp';
export const SECRETARY_SPRITE_URL_YEAH = '/assets/private-team/secretary-yeah.webp';
export const SECRETARY_SPRITE_URL_IDLE = '/assets/private-team/secretary-idle-tea.webp';
export const SECRETARY_SPRITE_URL_SLEEP = '/assets/private-team/secretary-sleep.webp';

const SECRETARY_TYPING_FRAME_MS = 500;
const SECRETARY_WAVE_MS = 3000;
const SECRETARY_YEAH_MS = 2500;

/** 慶祝舞 8 格路徑（5 張圖重複索引）；登記供後續單直接切幀。 */
export const SECRETARY_DANCE_FRAME_INDICES = [1, 2, 3, 4, 5, 3, 4, 2];
export const SECRETARY_DANCE_FRAME_MS = 120;
export const SECRETARY_DANCE_LOOPS = 3;
export const SECRETARY_DANCE_DURATION_MS =
  SECRETARY_DANCE_FRAME_INDICES.length * SECRETARY_DANCE_FRAME_MS * SECRETARY_DANCE_LOOPS;
export const SECRETARY_IDLE_DANCE_AFTER_MS = 4 * 60 * 1000;
export const SECRETARY_IDLE_DANCE_COOLDOWN_MS = 5 * 60 * 1000;

export function secretaryDanceFrameUrl(frameIndex) {
  const n = Number(frameIndex);
  if (!Number.isFinite(n) || n < 1) return '';
  return '/assets/private-team/secretary-dance-' + n + '.webp';
}

export function isStandbyTigerStatus(status) {
  const s = status == null ? '' : String(status).trim();
  return s === '待命中' || s === '待機';
}

/** 待命夠久且冷卻後是否該起舞（純函式，供單測）。 */
export function shouldTriggerIdleStandbyDance({
  status,
  standbySinceMs,
  now,
  lastIdleDanceMs,
  dancing,
  reducedMotion
}) {
  if (reducedMotion || dancing) return false;
  if (!isStandbyTigerStatus(status)) return false;
  const since = Number(standbySinceMs);
  const t = Number(now);
  if (!Number.isFinite(since) || !Number.isFinite(t) || t - since < SECRETARY_IDLE_DANCE_AFTER_MS) {
    return false;
  }
  const last = lastIdleDanceMs == null ? null : Number(lastIdleDanceMs);
  if (last != null && Number.isFinite(last) && t - last < SECRETARY_IDLE_DANCE_COOLDOWN_MS) {
    return false;
  }
  return true;
}

export function secretaryTypingDisplayUrl(statusBaseUrl, useTypingB, reducedMotion) {
  const base = statusBaseUrl == null ? '' : String(statusBaseUrl);
  if (base === SECRETARY_SPRITE_URL_TYPING && useTypingB && !reducedMotion) {
    return SECRETARY_SPRITE_URL_TYPING_B;
  }
  return base;
}

export const PRIVATE_TEAM_ZOOM_MIN = 0.72;
export const PRIVATE_TEAM_ZOOM_MAX = 2.05;

/** 立繪在場景中的高度（公尺比例）；寬度依紋理長寬比計算 */
const SECRETARY_SPRITE_HEIGHT = 1.42;

const FLOOR_SIZE = 3.6;
const FLOOR_HALF = FLOOR_SIZE / 2;
const WALL_THICK = 0.14;
const TEX_BASE = '/assets/private-team/textures/';

const COL = {
  wood: 0xc9a66b,
  woodDk: 0xa07840,
  wall: 0x243248,
  wallDk: 0x1a2438,
  rug: 0x6a8fd4,
  rugEdge: 0xa8d0f2,
  desk: 0x8b6914,
  screen: 0x1a2438,
  progress: 0x2e9a4a,
  plant: 0x3d8b4a,
  pot: 0xc4a882,
  mug: 0xf8fbff,
  skyNight: 0x1a2848,
  building: 0x4a5c7a,
  buildingLit: 0xf8dc6e,
  frame: 0x141c28,
  lampBase: 0x4a3c30,
  lampMetal: 0x6a5a48,
  lampShade: 0xe2cfb0
};

export function clampPrivateTeamZoom(z) {
  const n = Number(z);
  if (!Number.isFinite(n)) return 1;
  return Math.min(PRIVATE_TEAM_ZOOM_MAX, Math.max(PRIVATE_TEAM_ZOOM_MIN, n));
}

/** 雙指捏合縮放倍率（距離變大＝拉近，變小＝拉遠） */
export function privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist) {
  if (!(pinchStartDist > 0) || !(dist > 0)) return pinchStartZoom;
  return pinchStartZoom * (pinchStartDist / dist);
}

/** 夜間全域與檯燈強度（供單測） */
export function privateTeamSceneLighting() {
  return {
    amb: 0.26,
    dir: 0.32,
    point: 1.35,
    spot: 8,
    bulbOpacity: 0.9,
    haloOpacity: 0.62,
    shadeEmissive: 0.72
  };
}

function box(w, h, d, color, basic) {
  const g = new THREE.BoxGeometry(w, h, d);
  const m = basic
    ? new THREE.MeshBasicMaterial({ color })
    : new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function cyl(rt, rb, h, seg, color) {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  const m = new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

function roundedRectShape(hw, hh, r) {
  const shape = new THREE.Shape();
  const x = -hw;
  const y = -hh;
  const w = hw * 2;
  const h = hh * 2;
  shape.moveTo(x + r, y);
  shape.lineTo(x + w - r, y);
  shape.quadraticCurveTo(x + w, y, x + w, y + r);
  shape.lineTo(x + w, y + h - r);
  shape.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
  shape.lineTo(x + r, y + h);
  shape.quadraticCurveTo(x, y + h, x, y + h - r);
  shape.lineTo(x, y + r);
  shape.quadraticCurveTo(x, y, x + r, y);
  return shape;
}

function extrudedRoundedPlate(hw, hh, depth, radius, mat) {
  const shape = roundedRectShape(hw, hh, radius);
  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: depth,
    bevelEnabled: true,
    bevelThickness: 0.012,
    bevelSize: 0.012,
    bevelSegments: 2,
    curveSegments: 6
  });
  geo.rotateX(-Math.PI / 2);
  return new THREE.Mesh(geo, mat);
}

function loadRepeatTex(loader, file, rx, ry) {
  const tex = loader.load(TEX_BASE + file);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(rx, ry);
  return tex;
}

function makeScreenPainter() {
  const canvas = document.createElement('canvas');
  canvas.width = 160;
  canvas.height = 110;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  const mat = new THREE.MeshBasicMaterial({ map: tex });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(0.56, 0.38), mat);
  mesh.position.z = 0.04;
  function draw(pulse) {
    ctx.fillStyle = '#121a2c';
    ctx.fillRect(0, 0, 160, 110);
    const glow = '#b9e2ff';
    ctx.fillStyle = 'rgba(94, 186, 255, ' + (0.45 + pulse * 0.45) + ')';
    ctx.fillRect(12, 14, 136, 82);
    ctx.fillStyle = glow;
    const slide = (pulse * 28) | 0;
    ctx.fillRect(22, 26, 48 + slide, 10);
    ctx.fillRect(22, 46, 90, 8);
    ctx.fillRect(22, 64, 62 + (18 - slide / 2), 8);
    tex.needsUpdate = true;
  }
  return { mesh, draw };
}

function progressTexture(pct) {
  const canvas = document.createElement('canvas');
  canvas.width = 200;
  canvas.height = 120;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#1a2438';
  ctx.fillRect(0, 0, 200, 120);
  ctx.fillStyle = '#8a96a8';
  ctx.font = 'bold 18px sans-serif';
  ctx.fillText('進度', 12, 28);
  ctx.fillStyle = '#2e4a3a';
  ctx.fillRect(12, 40, 176, 20);
  ctx.fillStyle = '#2e9a4a';
  ctx.fillRect(12, 40, Math.max(8, 176 * pct), 20);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeBubbleDrawer() {
  const canvas = document.createElement('canvas');
  canvas.width = 320;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  function draw(text) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#f8fbff';
    ctx.strokeStyle = '#163a70';
    ctx.lineWidth = 10;
    const x = 16;
    const y = 12;
    const w = 288;
    const h = 78;
    const r = 22;
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + 182, y + h);
    ctx.lineTo(x + 150, y + h + 26);
    ctx.lineTo(x + 140, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    const label = text == null || String(text).trim() === '' ? '…' : String(text);
    const size = label.length > 6 ? 46 : label.length > 4 ? 52 : 60;
    ctx.fillStyle = '#163a70';
    ctx.font = 'bold ' + size + 'px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, canvas.width / 2, 50);
    tex.needsUpdate = true;
  }
  draw('…');
  return { tex, draw };
}

function lampGlowTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const g = ctx.createRadialGradient(64, 64, 0, 64, 64, 62);
  g.addColorStop(0, 'rgba(255, 228, 180, 0.9)');
  g.addColorStop(0.4, 'rgba(255, 196, 120, 0.35)');
  g.addColorStop(1, 'rgba(255, 170, 90, 0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 128, 128);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function softShadowTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, 128, 64);
  const g = ctx.createRadialGradient(64, 32, 2, 64, 32, 60);
  g.addColorStop(0, 'rgba(10, 18, 36, 0.55)');
  g.addColorStop(0.55, 'rgba(10, 18, 36, 0.22)');
  g.addColorStop(1, 'rgba(10, 18, 36, 0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 128, 64);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function spriteAspectFromTexture(tex) {
  const img = tex && tex.image;
  if (img && img.width > 0 && img.height > 0) {
    return img.width / img.height;
  }
  return 526 / 768;
}

function addMonitor(root, lookTarget, x, z, scale) {
  const group = new THREE.Group();
  const s = scale == null ? 1 : scale;
  group.position.set(x, 0.86, z);
  group.scale.set(s, s, s);
  const bezel = box(0.64, 0.46, 0.05, COL.screen);
  const painter = makeScreenPainter();
  const stand = box(0.1, 0.28, 0.08, 0x2a3648);
  stand.position.y = -0.34;
  group.add(bezel, painter.mesh, stand);
  group.lookAt(lookTarget.x, lookTarget.y, lookTarget.z);
  root.add(group);

  const screenGlow = new THREE.PointLight(0x8ab8e8, 0.28, 2.4, 2);
  screenGlow.position.set(0, 0.05, 0.12);
  group.add(screenGlow);

  return { painter, screenGlow };
}

function addLowPolyPlant(root, x, z) {
  const pot = cyl(0.09, 0.075, 0.11, 10, COL.pot);
  pot.position.set(x, 0.055, z);
  root.add(pot);
  const leafMat = new THREE.MeshLambertMaterial({ color: COL.plant });
  for (let i = 0; i < 6; i++) {
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.055, 6, 5), leafMat);
    const a = (i / 6) * Math.PI * 2;
    leaf.position.set(x + Math.cos(a) * 0.06, 0.15 + (i % 3) * 0.04, z + Math.sin(a) * 0.06);
    leaf.scale.set(1.05, 0.5, 0.9);
    root.add(leaf);
  }
}

export function mountPrivateTeamScene(canvas, opts) {
  const win = (opts && opts.win) || globalThis;
  const doc = (opts && opts.doc) || globalThis.document;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  function scenePixelRatio() {
    const coarse =
      typeof win.matchMedia === 'function' && win.matchMedia('(pointer: coarse)').matches;
    const narrow = (win.innerWidth || 1024) < 640;
    const cap = coarse || narrow ? 1.5 : 2;
    return Math.min(win.devicePixelRatio || 1, cap);
  }
  renderer.setPixelRatio(scenePixelRatio());

  const scene = new THREE.Scene();
  scene.background = new THREE.Color(0x050a14);

  const camLook = new THREE.Vector3(0, 0.92, -0.08);
  const camBase = new THREE.Vector3(3.25, 3.5, 4.15);
  let zoomFactor = 1;

  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  function applyZoom(factor) {
    zoomFactor = clampPrivateTeamZoom(factor);
    const dir = camBase.clone().sub(camLook);
    camera.position.copy(camLook).add(dir.multiplyScalar(zoomFactor));
    camera.lookAt(camLook);
  }
  applyZoom(1);

  const lighting = privateTeamSceneLighting();
  const amb = new THREE.AmbientLight(0xe8eeff, lighting.amb);
  const dir = new THREE.DirectionalLight(0xb0c0f0, lighting.dir);
  dir.position.set(3.5, 7, 4);
  scene.add(amb, dir);

  const texLoader = new THREE.TextureLoader();
  const wallTex = loadRepeatTex(texLoader, 'wall.webp', 2.2, 1.4);
  const floorTex = loadRepeatTex(texLoader, 'floor-wood.webp', 2.4, 2.4);
  const deskTex = loadRepeatTex(texLoader, 'desk-wood.webp', 2.2, 1.2);
  const rugTex = loadRepeatTex(texLoader, 'rug-weave.webp', 2.2, 1.4);

  const root = new THREE.Group();
  scene.add(root);

  const floorMat = new THREE.MeshLambertMaterial({ map: floorTex, color: 0xffffff });
  const floor = extrudedRoundedPlate(FLOOR_HALF, FLOOR_HALF, 0.035, 0.08, floorMat);
  floor.position.y = 0;
  root.add(floor);

  const rugMat = new THREE.MeshLambertMaterial({ map: rugTex, color: COL.rug });
  const rug = extrudedRoundedPlate(1.2, 0.8, 0.028, 0.06, rugMat);
  rug.position.set(0, 0.038, 0.06);
  root.add(rug);
  const edgeMat = new THREE.MeshLambertMaterial({ color: COL.rugEdge });
  const rugEdge = extrudedRoundedPlate(1.22, 0.82, 0.01, 0.06, edgeMat);
  rugEdge.position.set(0, 0.048, 0.06);
  root.add(rugEdge);

  const wallMat = new THREE.MeshLambertMaterial({ map: wallTex, color: 0xd8e4f4 });
  const backZ = -FLOOR_HALF;
  const leftX = -FLOOR_HALF;
  const wallY = 1.22;
  const wallH = 2.44;

  function wallSlab(w, h, x, y, z) {
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, WALL_THICK), wallMat);
    m.position.set(x, y, z);
    root.add(m);
  }

  const winW = 2.55;
  const winH = 1.72;
  const winBottom = 0.38;
  const winTop = winBottom + winH;
  const winLeft = -winW / 2;
  const winRight = winW / 2;

  wallSlab(FLOOR_HALF - winRight, wallH, (winRight + FLOOR_HALF) / 2, wallY, backZ - WALL_THICK / 2);
  wallSlab(FLOOR_HALF + winLeft, wallH, (winLeft - FLOOR_HALF) / 2, wallY, backZ - WALL_THICK / 2);
  wallSlab(winW, wallH - winTop, 0, winTop + (wallH - winTop) / 2 - 0.02, backZ - WALL_THICK / 2);
  wallSlab(winW, winBottom, 0, winBottom / 2, backZ - WALL_THICK / 2);
  wallSlab(WALL_THICK, wallH, leftX + WALL_THICK / 2, wallY, 0);

  const skyMat = new THREE.MeshBasicMaterial({ color: COL.skyNight });
  const windowSky = new THREE.Mesh(new THREE.PlaneGeometry(winW + 0.2, winH + 0.2), skyMat);
  windowSky.position.set(0, winBottom + winH / 2, backZ - WALL_THICK - 0.02);
  windowSky.name = 'windowSky';
  root.add(windowSky);

  const skyline = new THREE.Group();
  skyline.name = 'skyline';
  const buildingSpecs = [
    { x: -1.0, w: 0.28, h: 0.38 },
    { x: -0.58, w: 0.34, h: 0.74 },
    { x: -0.2, w: 0.22, h: 0.48 },
    { x: 0.18, w: 0.38, h: 0.88 },
    { x: 0.58, w: 0.26, h: 0.54 },
    { x: 0.92, w: 0.2, h: 0.34 }
  ];
  const buildingShades = [0x516484, 0x3e516e, 0x5a6d90, 0x445878, 0x4c6286, 0x3a4c68];
  const skylineY = winBottom + winH / 2;
  buildingSpecs.forEach((spec, i) => {
    const b = box(spec.w, spec.h, 0.14, buildingShades[i]);
    b.position.set(spec.x, skylineY - winH / 2 + 0.12 + spec.h / 2, backZ - WALL_THICK - 0.06);
    b.name = 'building';
    skyline.add(b);
    const cols = spec.w > 0.28 ? 2 : 1;
    const rows = spec.h > 0.6 ? 3 : 2;
    for (let c = 0; c < cols; c++) {
      for (let r = 0; r < rows; r++) {
        const lit = box(0.05, 0.05, 0.02, COL.buildingLit, true);
        const ox = (c - (cols - 1) / 2) * 0.1;
        const oy = (r - (rows - 1) / 2) * 0.14;
        lit.position.set(
          spec.x + ox,
          skylineY - winH / 2 + 0.12 + spec.h / 2 + oy,
          backZ - WALL_THICK + 0.02
        );
        lit.name = 'bwin';
        skyline.add(lit);
      }
    }
  });
  root.add(skyline);

  const glassMat = new THREE.MeshLambertMaterial({
    color: COL.skyNight,
    transparent: true,
    opacity: 0.2,
    depthWrite: false
  });
  const paneW = (winW - 0.12) / 3;
  const glassY = winBottom + winH / 2;
  const glassZ = backZ + 0.02;
  [-paneW - 0.04, 0, paneW + 0.04].forEach((px) => {
    const glass = new THREE.Mesh(new THREE.BoxGeometry(paneW, winH - 0.08, 0.02), glassMat);
    glass.position.set(px, glassY, glassZ);
    glass.name = 'windowGlass';
    root.add(glass);
  });

  function frameBar(w, h, x, y) {
    const m = box(w, h, 0.06, COL.frame);
    m.position.set(x, y, backZ + 0.05);
    root.add(m);
  }
  frameBar(0.08, winH, winLeft - 0.04, glassY);
  frameBar(0.08, winH, winRight + 0.04, glassY);
  frameBar(winW + 0.12, 0.08, 0, winBottom - 0.02);
  frameBar(winW + 0.12, 0.08, 0, winTop + 0.02);
  frameBar(0.06, winH, -paneW / 2 - 0.02, glassY);
  frameBar(0.06, winH, paneW / 2 + 0.02, glassY);

  const windowWash = new THREE.Mesh(
    new THREE.PlaneGeometry(2.8, 1.35),
    new THREE.MeshBasicMaterial({
      color: 0x6a8ec8,
      transparent: true,
      opacity: 0.13,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
  );
  windowWash.rotation.x = -Math.PI / 2;
  windowWash.position.set(0, 0.022, -1.05);
  root.add(windowWash);

  const progMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(1.0, 0.6),
    new THREE.MeshBasicMaterial({ map: progressTexture(0.62) })
  );
  progMesh.name = 'progressBoard';
  progMesh.position.set(leftX + 0.04, 1.52, 0.1);
  progMesh.rotation.y = Math.PI / 2;
  root.add(progMesh);

  const deskMat = new THREE.MeshLambertMaterial({ map: deskTex, color: COL.desk });
  const desk = extrudedRoundedPlate(1.08, 0.43, 0.075, 0.05, deskMat);
  desk.position.set(0, 0.468, 0.04);
  root.add(desk);

  const secretaryLook = new THREE.Vector3(0.02, 1.02, 0.78);
  const monitor = addMonitor(root, secretaryLook, 0.02, -0.1, 1);

  const mug = new THREE.Mesh(
    new THREE.CylinderGeometry(0.09, 0.075, 0.18, 12),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mug.position.set(-0.38, 0.58, 0.38);
  const mugHandle = new THREE.Mesh(
    new THREE.TorusGeometry(0.05, 0.015, 6, 10),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mugHandle.position.set(-0.46, 0.58, 0.38);
  mugHandle.rotation.y = Math.PI / 2;
  root.add(mug, mugHandle);

  const lampGroup = new THREE.Group();
  lampGroup.position.set(0.92, 0.512, 0.42);
  lampGroup.rotation.y = 1.35;
  const lampBase = cyl(0.07, 0.095, 0.045, 12, COL.lampBase);
  lampBase.position.y = 0.022;
  lampGroup.add(lampBase);
  const lampStem = cyl(0.016, 0.016, 0.1, 8, COL.lampMetal);
  lampStem.position.y = 0.09;
  lampGroup.add(lampStem);
  const lampArm = box(0.22, 0.028, 0.028, COL.lampMetal);
  lampArm.position.set(0.07, 0.16, 0);
  lampArm.rotation.z = -0.22;
  lampGroup.add(lampArm);
  const lampShadeMat = new THREE.MeshLambertMaterial({
    color: COL.lampShade,
    emissive: 0xffb060,
    emissiveIntensity: lighting.shadeEmissive
  });
  const lampShade = new THREE.Mesh(
    new THREE.CylinderGeometry(0.05, 0.085, 0.075, 12),
    lampShadeMat
  );
  lampShade.position.set(0.16, 0.19, 0);
  lampGroup.add(lampShade);
  const lampBulbMat = new THREE.MeshBasicMaterial({
    color: 0xfff2d8,
    transparent: true,
    opacity: lighting.bulbOpacity,
    depthWrite: false
  });
  const lampBulb = new THREE.Mesh(new THREE.SphereGeometry(0.028, 10, 10), lampBulbMat);
  lampBulb.position.set(0.16, 0.142, 0);
  lampGroup.add(lampBulb);
  const lampHaloMat = new THREE.MeshBasicMaterial({
    map: lampGlowTexture(),
    transparent: true,
    opacity: lighting.haloOpacity,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const lampHalo = new THREE.Mesh(new THREE.PlaneGeometry(0.36, 0.36), lampHaloMat);
  lampHalo.position.set(0.16, 0.18, 0.02);
  lampGroup.add(lampHalo);
  root.add(lampGroup);

  const lampLightLocal = new THREE.Vector3(0.16, 0.17, 0);
  const lampPoint = new THREE.PointLight(0xffc888, lighting.point, 5.2, 1.6);
  lampPoint.position.copy(lampLightLocal);
  lampGroup.add(lampPoint);
  const lampSpot = new THREE.SpotLight(0xffc888, lighting.spot, 9, Math.PI / 2.8, 0.5, 1);
  lampSpot.position.copy(lampLightLocal);
  const lampTarget = new THREE.Object3D();
  lampGroup.add(lampTarget);
  lampGroup.updateMatrixWorld(true);
  lampTarget.position.copy(lampGroup.worldToLocal(new THREE.Vector3(0.2, 0.52, 0.35)));
  lampSpot.target = lampTarget;
  lampGroup.add(lampSpot);

  let lampPointBase = lighting.point;
  let lampSpotBase = lighting.spot;
  let lampHaloBase = lighting.haloOpacity;

  let reducedMotion = false;

  addLowPolyPlant(root, -1.15, 0.82);

  const secretaryRoot = new THREE.Group();
  secretaryRoot.position.set(0.02, 0, 0.78);
  root.add(secretaryRoot);

  const shadowMat = new THREE.MeshBasicMaterial({
    map: softShadowTexture(),
    transparent: true,
    depthWrite: false
  });
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.16, 0.6), shadowMat);
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.set(0.06, 0.072, -0.42);
  shadow.renderOrder = 1;
  secretaryRoot.add(shadow);

  let secretarySprite = null;
  const spriteBaseY = SECRETARY_SPRITE_HEIGHT / 2 + 0.02;
  let spriteAnimMode = 'idle';
  let statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
  let typingShowB = false;
  let lastTypingFlipMs = 0;
  let overlayActive = null;
  let overlayPending = null;
  let dancePlayback = null;
  let tigerStatus = '待命中';
  let standbySinceMs = performance.now();
  let lastIdleDanceMs = null;
  let danceDisplayFrame = -1;
  let currentSpriteUrl = '';
  let desiredSpriteUrl = '';
  let spriteLoadSeq = 0;
  const loader = new THREE.TextureLoader();

  (function preloadSecretaryDanceFrames() {
    const seen = new Set();
    for (let i = 0; i < SECRETARY_DANCE_FRAME_INDICES.length; i++) {
      const idx = SECRETARY_DANCE_FRAME_INDICES[i];
      const url = secretaryDanceFrameUrl(idx);
      if (!url || seen.has(url)) continue;
      seen.add(url);
      loader.load(url, () => {}, undefined, () => {});
    }
  })();

  function refreshPoseAnimMode() {
    spriteAnimMode = spriteAnimModeForUrl(statusBaseUrl);
  }

  function resolveRestSpriteUrl() {
    return secretaryTypingDisplayUrl(statusBaseUrl, typingShowB, reducedMotion);
  }

  function applySecretarySpriteUrl(url) {
    const next = url == null ? '' : String(url).trim();
    if (!next || next === desiredSpriteUrl) return;
    desiredSpriteUrl = next;
    const seq = ++spriteLoadSeq;
    loader.load(
      next,
      (tex) => {
        if (seq !== spriteLoadSeq || desiredSpriteUrl !== next) {
          disposeUnusedSpriteTexture(tex);
          return;
        }
        if (secretarySprite) retextureSecretarySprite(tex, next);
        else mountSecretarySprite(tex, next);
      },
      undefined,
      () => {
        if (seq === spriteLoadSeq) desiredSpriteUrl = currentSpriteUrl;
      }
    );
  }

  function refreshSecretaryDisplay() {
    if (dancePlayback) return;
    if (overlayActive) {
      applySecretarySpriteUrl(overlayActive.url);
      return;
    }
    applySecretarySpriteUrl(resolveRestSpriteUrl());
  }

  function secretaryDanceUrlAtElapsed(elapsedMs) {
    const perLoop = SECRETARY_DANCE_FRAME_INDICES.length * SECRETARY_DANCE_FRAME_MS;
    const total = perLoop * SECRETARY_DANCE_LOOPS;
    if (elapsedMs >= total) return '';
    const frameInSeq = Math.floor(elapsedMs / SECRETARY_DANCE_FRAME_MS) % SECRETARY_DANCE_FRAME_INDICES.length;
    return secretaryDanceFrameUrl(SECRETARY_DANCE_FRAME_INDICES[frameInSeq]);
  }

  function stopSecretaryDance() {
    dancePlayback = null;
    danceDisplayFrame = -1;
    refreshSecretaryDisplay();
  }

  function playSecretaryDance(now) {
    if (reducedMotion) return;
    const t = Number.isFinite(now) ? now : performance.now();
    overlayActive = null;
    overlayPending = null;
    dancePlayback = { startMs: t };
    danceDisplayFrame = -1;
    const url = secretaryDanceUrlAtElapsed(0);
    if (url) applySecretarySpriteUrl(url);
  }

  function noteTigerStatus(status, now) {
    const next = status == null ? '' : String(status).trim() || '待命中';
    const t = Number.isFinite(now) ? now : performance.now();
    const wasStandby = isStandbyTigerStatus(tigerStatus);
    tigerStatus = next;
    if (isStandbyTigerStatus(tigerStatus)) {
      if (!wasStandby || standbySinceMs == null) standbySinceMs = t;
    } else {
      standbySinceMs = null;
    }
  }

  function endSecretaryOverlay(now) {
    overlayActive = null;
    if (overlayPending) {
      const pending = overlayPending;
      overlayPending = null;
      overlayActive = { url: pending.url, until: now + pending.ms };
      applySecretarySpriteUrl(pending.url);
      return;
    }
    refreshSecretaryDisplay();
  }

  function enqueueSecretaryOverlay(url, ms, now) {
    if (overlayActive && overlayActive.url === url) {
      overlayActive.until = Math.max(overlayActive.until, now + ms);
      return;
    }
    if (overlayActive) {
      overlayPending = { url, ms };
      return;
    }
    overlayActive = { url, until: now + ms };
    applySecretarySpriteUrl(url);
  }

  function playSecretaryWave() {
    if (dancePlayback) return;
    enqueueSecretaryOverlay(SECRETARY_SPRITE_URL_WAVE, SECRETARY_WAVE_MS, performance.now());
  }

  function playSecretaryYeah() {
    if (dancePlayback) return;
    if (overlayActive && overlayActive.url === SECRETARY_SPRITE_URL_YEAH) {
      return;
    }
    enqueueSecretaryOverlay(SECRETARY_SPRITE_URL_YEAH, SECRETARY_YEAH_MS, performance.now());
  }

  function spriteAnimModeForUrl(url) {
    if (url === SECRETARY_SPRITE_URL_TYPING || url === SECRETARY_SPRITE_URL_TYPING_B) return 'working';
    if (url === SECRETARY_SPRITE_URL_SLEEP) return 'sleep';
    return 'idle';
  }

  function mountSecretarySprite(tex, url) {
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.premultiplyAlpha = true;
    const aspect = spriteAspectFromTexture(tex);
    const spriteW = SECRETARY_SPRITE_HEIGHT * aspect;
    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      transparent: true,
      alphaTest: 0.06,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    secretarySprite = new THREE.Mesh(new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT), mat);
    secretarySprite.position.set(0, spriteBaseY, 0);
    secretarySprite.renderOrder = 2;
    secretarySprite.name = 'secretarySprite';
    secretaryRoot.add(secretarySprite);
    if (clickables.indexOf(secretarySprite) === -1) {
      clickables.push(secretarySprite);
    }
    currentSpriteUrl = url;
  }

  function retextureSecretarySprite(tex, url) {
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.premultiplyAlpha = true;
    const aspect = spriteAspectFromTexture(tex);
    const spriteW = SECRETARY_SPRITE_HEIGHT * aspect;
    const prev = secretarySprite.geometry;
    secretarySprite.geometry = new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT);
    if (prev) prev.dispose();
    const oldMap = secretarySprite.material.map;
    secretarySprite.material.map = tex;
    secretarySprite.material.needsUpdate = true;
    if (oldMap && oldMap !== tex) oldMap.dispose();
    secretarySprite.position.set(0, spriteBaseY, 0);
    secretarySprite.rotation.set(0, 0, 0);
    currentSpriteUrl = url;
  }

  function disposeUnusedSpriteTexture(tex) {
    if (tex && typeof tex.dispose === 'function') tex.dispose();
  }

  function setSecretarySprite(url) {
    const next = url == null ? '' : String(url).trim();
    if (!next || next === statusBaseUrl) return;
    statusBaseUrl = next;
    typingShowB = false;
    lastTypingFlipMs = performance.now();
    refreshPoseAnimMode();
    refreshSecretaryDisplay();
  }

  refreshPoseAnimMode();
  applySecretarySpriteUrl(resolveRestSpriteUrl());

  const secretaryBubbleX = 0.56;
  const bubbleDrawer = makeBubbleDrawer();
  const bubbleMat = new THREE.MeshBasicMaterial({
    map: bubbleDrawer.tex,
    transparent: true,
    depthWrite: false
  });
  const bubble = new THREE.Mesh(new THREE.PlaneGeometry(0.82, 0.36), bubbleMat);
  const bubbleFloatMin = 1.68;
  const bubbleFloatSpan = 0.07;
  const bubbleRestY = bubbleFloatMin + bubbleFloatSpan / 2;
  bubble.position.set(secretaryBubbleX, bubbleRestY, 0.72);
  bubble.renderOrder = 3;
  bubble.name = 'secretaryBubble';
  root.add(bubble);

  const clickables = [bubble, progMesh];
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const boardCorner = new THREE.Vector3();
  const onActivate = typeof (opts && opts.onActivate) === 'function' ? opts.onActivate : null;
  const onOpenProgress = typeof (opts && opts.onOpenProgress) === 'function' ? opts.onOpenProgress : null;
  const onProgressBoardRect = typeof (opts && opts.onProgressBoardRect) === 'function' ? opts.onProgressBoardRect : null;
  let sceneInputEnabled = true;

  const activePointers = new Map();
  let pinchActive = false;
  let pinchStartDist = 0;
  let pinchStartZoom = 1;

  function pointerDistance() {
    const pts = [...activePointers.values()];
    if (pts.length < 2) return 0;
    const dx = pts[0].x - pts[1].x;
    const dy = pts[0].y - pts[1].y;
    return Math.hypot(dx, dy);
  }

  function progressBoardScreenRect() {
    const stage = canvas.parentElement || canvas;
    const stageRect = stage.getBoundingClientRect();
    const canvasRect = canvas.getBoundingClientRect();
    if (!canvasRect.width || !canvasRect.height) return null;
    camera.updateWorldMatrix(true, false);
    const hw = 0.5;
    const hh = 0.3;
    const corners = [[-hw, -hh], [hw, -hh], [hw, hh], [-hw, hh]];
    let minX = Infinity;
    let minY = Infinity;
    let maxX = -Infinity;
    let maxY = -Infinity;
    for (let i = 0; i < corners.length; i++) {
      boardCorner.set(corners[i][0], corners[i][1], 0);
      progMesh.localToWorld(boardCorner);
      boardCorner.project(camera);
      const x = (boardCorner.x * 0.5 + 0.5) * canvasRect.width + (canvasRect.left - stageRect.left);
      const y = (-boardCorner.y * 0.5 + 0.5) * canvasRect.height + (canvasRect.top - stageRect.top);
      if (x < minX) minX = x;
      if (y < minY) minY = y;
      if (x > maxX) maxX = x;
      if (y > maxY) maxY = y;
    }
    const width = maxX - minX;
    const height = maxY - minY;
    if (!(width > 0) || !(height > 0)) return null;
    return { left: minX, top: minY, width: width, height: height };
  }

  function pickTarget(clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(clickables, false);
    if (!hits.length) return null;
    const obj = hits[0].object;
    if (obj === progMesh) return 'progress';
    return 'secretary';
  }

  function handlePointerDown(ev) {
    if (!sceneInputEnabled) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (activePointers.size >= 2) {
      pinchActive = true;
      pinchStartDist = pointerDistance();
      pinchStartZoom = zoomFactor;
      ev.preventDefault();
      return;
    }
    if (pinchActive) return;
    const target = pickTarget(ev.clientX, ev.clientY);
    if (!target) return;
    ev.preventDefault();
    if (target === 'progress') {
      if (onOpenProgress) onOpenProgress();
      return;
    }
    playSecretaryWave();
    if (onActivate) onActivate();
  }

  function handlePointerMove(ev) {
    if (!activePointers.has(ev.pointerId)) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (pinchActive && activePointers.size >= 2 && pinchStartDist > 0) {
      const dist = pointerDistance();
      applyZoom(privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist));
      ev.preventDefault();
    }
  }

  function clearPointer(ev) {
    activePointers.delete(ev.pointerId);
    if (activePointers.size < 2) {
      pinchActive = false;
      pinchStartDist = 0;
    }
  }

  function handlePointerUp(ev) {
    clearPointer(ev);
  }

  function onWheel(ev) {
    if (!sceneInputEnabled) return;
    ev.preventDefault();
    const delta = ev.deltaY;
    const step = delta > 0 ? 0.045 : -0.045;
    applyZoom(zoomFactor * (1 + step));
  }

  canvas.addEventListener('pointerdown', handlePointerDown);
  canvas.addEventListener('pointermove', handlePointerMove);
  canvas.addEventListener('pointerup', handlePointerUp);
  canvas.addEventListener('pointercancel', handlePointerUp);
  canvas.addEventListener('wheel', onWheel, { passive: false });

  let running = true;
  if (typeof win.matchMedia === 'function') {
    const mq = win.matchMedia('(prefers-reduced-motion: reduce)');
    reducedMotion = !!mq.matches;
    if (typeof mq.addEventListener === 'function') {
      mq.addEventListener('change', () => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        else refreshSecretaryDisplay();
      });
    } else if (typeof mq.addListener === 'function') {
      mq.addListener(() => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        else refreshSecretaryDisplay();
      });
    }
  }
  let raf = 0;
  let t0 = performance.now();

  function resize() {
    const parent = canvas.parentElement;
    const w = parent ? parent.clientWidth : 1;
    const h = parent ? parent.clientHeight : 1;
    renderer.setPixelRatio(scenePixelRatio());
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(h, 1);
    camera.updateProjectionMatrix();
  }

  function tick(now) {
    if (!running) return;
    raf = win.requestAnimationFrame(tick);
    if (dancePlayback) {
      const elapsed = now - dancePlayback.startMs;
      const frameNo = Math.floor(elapsed / SECRETARY_DANCE_FRAME_MS);
      const totalFrames = SECRETARY_DANCE_FRAME_INDICES.length * SECRETARY_DANCE_LOOPS;
      if (frameNo >= totalFrames) {
        stopSecretaryDance();
      } else if (frameNo !== danceDisplayFrame) {
        danceDisplayFrame = frameNo;
        const url = secretaryDanceUrlAtElapsed(elapsed);
        if (url) applySecretarySpriteUrl(url);
      }
    } else if (
      shouldTriggerIdleStandbyDance({
        status: tigerStatus,
        standbySinceMs,
        now,
        lastIdleDanceMs,
        dancing: false,
        reducedMotion
      })
    ) {
      lastIdleDanceMs = now;
      playSecretaryDance(now);
    }
    if (overlayActive && now >= overlayActive.until) {
      endSecretaryOverlay(now);
    } else if (
      !overlayActive &&
      !dancePlayback &&
      !reducedMotion &&
      statusBaseUrl === SECRETARY_SPRITE_URL_TYPING &&
      now - lastTypingFlipMs >= SECRETARY_TYPING_FRAME_MS
    ) {
      lastTypingFlipMs = now;
      typingShowB = !typingShowB;
      refreshSecretaryDisplay();
    }
    const elapsed = (now - t0) / 1000;
    const breath = Math.sin(elapsed * 1.65) * 0.014;
    const typeNudge = Math.sin(elapsed * 7.2) * 0.008;
    const lean = Math.sin(elapsed * 7.2 + 0.4) * 0.018;
    if (secretarySprite) {
      let extraY = 0;
      let leanZ = 0;
      if (!reducedMotion) {
        extraY = breath;
        if (spriteAnimMode === 'working') {
          extraY += typeNudge;
          leanZ = lean;
        } else if (spriteAnimMode === 'sleep') {
          extraY = breath * 0.45;
        }
      }
      secretarySprite.position.y = spriteBaseY + extraY;
      secretarySprite.rotation.z = leanZ;
      secretarySprite.lookAt(camera.position);
    }

    if (reducedMotion) {
      bubble.position.y = bubbleRestY;
      bubbleMat.opacity = 0.92;
    } else {
      const bubblePhase = (Math.sin(elapsed * 2.2) + 1) / 2;
      bubble.position.y = bubbleFloatMin + bubblePhase * bubbleFloatSpan;
      bubbleMat.opacity = 0.82 + bubblePhase * 0.18;
    }

    const pulse = (Math.sin(elapsed * 3) + 1) / 2;
    monitor.painter.draw(pulse);
    monitor.screenGlow.intensity = 0.22 + pulse * 0.12;

    if (lampPointBase > 0) {
      const lampPulse = reducedMotion ? 1 : 0.93 + ((Math.sin(elapsed * 3.6) + 1) / 2) * 0.07;
      lampPoint.intensity = lampPointBase * lampPulse;
      lampSpot.intensity = lampSpotBase * lampPulse;
      lampHaloMat.opacity =
        lampHaloBase * (reducedMotion ? 1 : 0.88 + ((Math.sin(elapsed * 3.6 + 0.8) + 1) / 2) * 0.12);
    }

    if (onProgressBoardRect) {
      const boardRect = progressBoardScreenRect();
      if (boardRect) onProgressBoardRect(boardRect);
    }
    bubble.lookAt(camera.position);
    lampHalo.lookAt(camera.position);

    renderer.render(scene, camera);
  }

  resize();
  win.addEventListener('resize', resize);
  if (typeof ResizeObserver !== 'undefined' && canvas.parentElement) {
    const ro = new ResizeObserver(resize);
    ro.observe(canvas.parentElement);
  }
  raf = win.requestAnimationFrame(tick);

  return {
    resize,
    setZoom(factor) {
      applyZoom(factor);
      return zoomFactor;
    },
    getZoom() {
      return zoomFactor;
    },
    setBubbleText(text) {
      bubbleDrawer.draw(text);
    },
    setReducedMotion(next) {
      reducedMotion = !!next;
      typingShowB = false;
      if (reducedMotion && dancePlayback) stopSecretaryDance();
      else refreshSecretaryDisplay();
    },
    setSecretarySprite,
    playSecretaryWave,
    playSecretaryYeah,
    playSecretaryDance,
    noteTigerStatus,
    setSceneInputEnabled(next) {
      sceneInputEnabled = !!next;
    },
    destroy() {
      running = false;
      win.cancelAnimationFrame(raf);
      win.removeEventListener('resize', resize);
      canvas.removeEventListener('pointerdown', handlePointerDown);
      canvas.removeEventListener('pointermove', handlePointerMove);
      canvas.removeEventListener('pointerup', handlePointerUp);
      canvas.removeEventListener('pointercancel', handlePointerUp);
      canvas.removeEventListener('wheel', onWheel);
      renderer.dispose();
    }
  };
}