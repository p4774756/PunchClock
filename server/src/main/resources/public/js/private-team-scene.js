import * as THREE from './vendor/three.module.js';

export const SECRETARY_SPRITE_URL_TYPING = '/assets/private-team/secretary-typing-a.webp';
export const SECRETARY_SPRITE_URL_TYPING_B = '/assets/private-team/secretary-typing-b.webp';
export const SECRETARY_SPRITE_URL_WAVE = '/assets/private-team/secretary-wave.webp';
export const SECRETARY_SPRITE_URL_YEAH = '/assets/private-team/secretary-yeah.webp';
export const SECRETARY_SPRITE_URL_IDLE = '/assets/private-team/secretary-idle-tea.webp';
export const SECRETARY_SPRITE_URL_READ = '/assets/private-team/secretary-read.webp';
export const SECRETARY_SPRITE_URL_PHONE = '/assets/private-team/secretary-phone.webp';
export const SECRETARY_SPRITE_URL_SLEEP = '/assets/private-team/secretary-sleep.webp';

export const SECRETARY_STANDBY_IDLE_URLS = [
  SECRETARY_SPRITE_URL_IDLE,
  SECRETARY_SPRITE_URL_READ,
  SECRETARY_SPRITE_URL_PHONE
];
export const SECRETARY_IDLE_ROTATE_MS = 2 * 60 * 1000;

const SECRETARY_TYPING_FRAME_MS = 500;
const SECRETARY_WAVE_MS = 3000;
const SECRETARY_YEAH_MS = 2500;

export const SECRETARY_CLAP_SPRITE_A = '/assets/private-team/secretary-clap-a.webp';
export const SECRETARY_CLAP_SPRITE_B = '/assets/private-team/secretary-clap-b.webp';

/** 慶祝：拍手兩下 → 比讚 → 小跳（只播一次）。 */
export const SECRETARY_CELEBRATE_STEPS = [
  { kind: 'frame', url: SECRETARY_CLAP_SPRITE_A, ms: 250 },
  { kind: 'frame', url: SECRETARY_CLAP_SPRITE_B, ms: 250 },
  { kind: 'frame', url: SECRETARY_CLAP_SPRITE_A, ms: 250 },
  { kind: 'frame', url: SECRETARY_CLAP_SPRITE_B, ms: 250 },
  { kind: 'frame', url: SECRETARY_SPRITE_URL_YEAH, ms: 700 },
  { kind: 'jump', ms: 450 }
];
export const SECRETARY_DANCE_DURATION_MS = SECRETARY_CELEBRATE_STEPS.reduce((sum, s) => sum + s.ms, 0);
export const SECRETARY_IDLE_DANCE_AFTER_MS = 4 * 60 * 1000;
export const SECRETARY_IDLE_DANCE_COOLDOWN_MS = 5 * 60 * 1000;

export function secretaryCelebrateStateAt(elapsedMs) {
  const t0 = Number(elapsedMs);
  if (!Number.isFinite(t0) || t0 < 0) {
    return { kind: 'frame', url: SECRETARY_CELEBRATE_STEPS[0].url, jumpT: null };
  }
  if (t0 >= SECRETARY_DANCE_DURATION_MS) {
    return { kind: 'done', url: '', jumpT: null };
  }
  let t = t0;
  let lastUrl = SECRETARY_CELEBRATE_STEPS[0].url;
  for (let i = 0; i < SECRETARY_CELEBRATE_STEPS.length; i++) {
    const step = SECRETARY_CELEBRATE_STEPS[i];
    if (t < step.ms) {
      if (step.kind === 'frame') {
        return { kind: 'frame', url: step.url, jumpT: null };
      }
      return { kind: 'jump', url: lastUrl, jumpT: t / step.ms };
    }
    t -= step.ms;
    if (step.kind === 'frame') lastUrl = step.url;
  }
  return { kind: 'done', url: '', jumpT: null };
}

export function secretaryCelebrateFrameUrlAt(elapsedMs) {
  const st = secretaryCelebrateStateAt(elapsedMs);
  if (st.kind === 'done') return '';
  return st.url;
}

export function secretaryCelebrateJumpOffsetAt(elapsedMs) {
  const st = secretaryCelebrateStateAt(elapsedMs);
  if (st.kind !== 'jump' || st.jumpT == null) return 0;
  return Math.sin(st.jumpT * Math.PI) * 0.11;
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

export function isSecretaryStandbyIdleUrl(url) {
  const u = url == null ? '' : String(url).trim();
  return SECRETARY_STANDBY_IDLE_URLS.includes(u);
}

/** 心跳輪詢仍傳待命預設喝茶圖時，勿蓋掉輪替中的看書／講電話。 */
export function shouldIgnoreStandbyIdleSpriteUpdate(currentUrl, incomingUrl) {
  const cur = currentUrl == null ? '' : String(currentUrl).trim();
  const inc = incomingUrl == null ? '' : String(incomingUrl).trim();
  if (inc !== SECRETARY_SPRITE_URL_IDLE || cur === SECRETARY_SPRITE_URL_IDLE) return false;
  return isSecretaryStandbyIdleUrl(cur);
}

/** 待命立繪輪替：從三張中選，不連續同一張（randomInt 供單測固定種子）。 */
export function pickNextStandbyIdleUrl(currentUrl, randomInt) {
  const cur = currentUrl == null ? '' : String(currentUrl).trim();
  const pool = SECRETARY_STANDBY_IDLE_URLS.filter((u) => u !== cur);
  if (pool.length === 0) return SECRETARY_STANDBY_IDLE_URLS[0];
  const ri = Number(randomInt);
  const idx = Number.isFinite(ri) ? Math.abs(Math.floor(ri)) % pool.length : 0;
  return pool[idx];
}

export const PRIVATE_TEAM_ZOOM_MIN = 0.72;
export const PRIVATE_TEAM_ZOOM_MAX = 2.05;

/** 鍵盤托盤上緣（與原桌面高度相近） */
export const PRIVATE_TEAM_TRAY_SURFACE_Y = 0.498;

/** 浴缸水面高度（約齊腰，略高於托盤，鍵盤仍露出） */
export const PRIVATE_TEAM_WATER_Y = 0.468;

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
  desk: 0x8b6914,
  tub: 0x7a7268,
  tubInner: 0x24343c,
  tubRim: 0x9a9084,
  water: 0x2c7a88,
  waterHi: 0xc4f0f2,
  towel: 0xefe4d4,
  towelDk: 0xddcbb3,
  screen: 0x1a2438,
  screenStand: 0x2a3648,
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

/** 首次載入預設鏡頭距離：窄螢幕直向拉遠，桌面／橫向維持約 1 */
export function privateTeamDefaultZoom(width, height) {
  const w = Number(width);
  const h = Number(height);
  if (!(w > 0) || !(h > 0)) return 1;
  const narrowPortrait = w < 640 && h > w;
  if (narrowPortrait) return clampPrivateTeamZoom(1.58);
  return 1;
}

/** 雙指捏合縮放倍率（距離變大＝拉近，變小＝拉遠） */
export function privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist) {
  if (!(pinchStartDist > 0) || !(dist > 0)) return pinchStartZoom;
  return pinchStartZoom * (pinchStartDist / dist);
}

/** 水平拖曳視角左右上限（度） */
export const PRIVATE_TEAM_YAW_MAX_DEG = 35;
export const PRIVATE_TEAM_DRAG_THRESHOLD_PX = 6;
export const PRIVATE_TEAM_YAW_RETURN_MS = 600;

const PRIVATE_TEAM_YAW_MAX_RAD = (PRIVATE_TEAM_YAW_MAX_DEG * Math.PI) / 180;

export function clampPrivateTeamYaw(radians) {
  const n = Number(radians);
  if (!Number.isFinite(n)) return 0;
  return Math.min(PRIVATE_TEAM_YAW_MAX_RAD, Math.max(-PRIVATE_TEAM_YAW_MAX_RAD, n));
}

/** 位移未超過門檻時仍視為點擊（不觸發旋轉） */
export function privateTeamDragExceedsThreshold(dx, dy) {
  return Math.hypot(Number(dx), Number(dy)) > PRIVATE_TEAM_DRAG_THRESHOLD_PX;
}

/** 依水平拖曳距離與畫布寬度換算繞 camLook 的偏航角 */
export function privateTeamYawFromDragDelta(deltaX, canvasWidth, startYaw) {
  const w = Math.max(Number(canvasWidth), 1);
  const base = Number(startYaw);
  const scale = (2 * PRIVATE_TEAM_YAW_MAX_RAD) / w;
  return clampPrivateTeamYaw((Number.isFinite(base) ? base : 0) + Number(deltaX) * scale);
}

export function privateTeamYawReturnProgress(elapsedMs, reducedMotion) {
  if (reducedMotion) return 1;
  const t = Number(elapsedMs);
  if (!Number.isFinite(t) || t <= 0) return 0;
  if (t >= PRIVATE_TEAM_YAW_RETURN_MS) return 1;
  const u = t / PRIVATE_TEAM_YAW_RETURN_MS;
  return 1 - (1 - u) * (1 - u);
}

export function privateTeamYawAtReturn(startYaw, progress) {
  const y = Number(startYaw);
  const p = Math.min(1, Math.max(0, Number(progress)));
  if (!Number.isFinite(y)) return 0;
  return y * (1 - p);
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

function extrudedRoundedRing(hw, hh, inHw, inHh, depth, radius, mat) {
  const shape = roundedRectShape(hw, hh, radius);
  const hole = roundedRectShape(inHw, inHh, Math.max(0.02, radius * 0.55));
  shape.holes.push(hole);
  const geo = new THREE.ExtrudeGeometry(shape, {
    depth: depth,
    bevelEnabled: true,
    bevelThickness: 0.01,
    bevelSize: 0.01,
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

function makeStoneTileTexture(repeatX, repeatY) {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#94897b';
  ctx.fillRect(0, 0, 128, 128);
  for (let row = 0; row < 4; row++) {
    for (let col = 0; col < 4; col++) {
      const shade = 140 + ((row * 3 + col * 5) % 7) * 5;
      ctx.fillStyle = 'rgb(' + (shade + 8) + ',' + (shade - 2) + ',' + (shade - 14) + ')';
      ctx.fillRect(col * 32 + 2, row * 32 + 2, 28, 28);
    }
  }
  ctx.strokeStyle = '#5c564e';
  ctx.lineWidth = 3;
  for (let i = 0; i <= 4; i++) {
    ctx.beginPath();
    ctx.moveTo(i * 32, 0);
    ctx.lineTo(i * 32, 128);
    ctx.stroke();
    ctx.beginPath();
    ctx.moveTo(0, i * 32);
    ctx.lineTo(128, i * 32);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(repeatX, repeatY);
  tex.anisotropy = 4;
  return tex;
}

function makeWaterRippleTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, 128, 128);
  const g = ctx.createRadialGradient(64, 64, 8, 64, 64, 62);
  g.addColorStop(0, 'rgba(255,255,255,0.0)');
  g.addColorStop(0.35, 'rgba(220,250,255,0.28)');
  g.addColorStop(0.55, 'rgba(255,255,255,0.0)');
  g.addColorStop(0.78, 'rgba(200,240,245,0.22)');
  g.addColorStop(1, 'rgba(255,255,255,0.0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 128, 128);
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(2.2, 1.6);
  return tex;
}

function privateTeamSeededRand(seed) {
  let s = seed >>> 0;
  return () => {
    s = (Math.imul(s, 16807) + 1) >>> 0;
    return (s & 0x7fffffff) / 0x7fffffff;
  };
}

/** 落地窗外天空平面深度（愈大愈靠後）。 */
export const PRIVATE_TEAM_WINDOW_SKY_Z = 0.22;

/** 落地窗外天際線平面層深度（愈大愈靠後；各層須小於 {@link PRIVATE_TEAM_WINDOW_SKY_Z}），供單測鎖定多層景深。 */
export const PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z = [0.16, 0.11, 0.06];

/** 天際線各層須比天空更靠鏡頭（offset 較小）。 */
export function skylineLayersInFrontOfWindowSky(skyZ, layerZs) {
  const sky = Number(skyZ);
  if (!Number.isFinite(sky) || !layerZs || !layerZs.length) return false;
  return layerZs.every((z) => Number(z) < sky);
}

export function privateTeamWindowAmbientMotionEnabled(reducedMotion) {
  return !reducedMotion;
}

export function makeNightWindowSkyTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 640;
  canvas.height = 400;
  const ctx = canvas.getContext('2d');
  const grad = ctx.createLinearGradient(0, 0, 0, canvas.height);
  grad.addColorStop(0, '#080e1c');
  grad.addColorStop(0.5, '#101a30');
  grad.addColorStop(0.82, '#1a2c48');
  grad.addColorStop(1, '#253a5c');
  ctx.fillStyle = grad;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const horizonGlow = ctx.createLinearGradient(0, canvas.height * 0.62, 0, canvas.height);
  horizonGlow.addColorStop(0, 'rgba(0,0,0,0)');
  horizonGlow.addColorStop(0.45, 'rgba(70,90,130,0.12)');
  horizonGlow.addColorStop(0.85, 'rgba(110,85,55,0.18)');
  horizonGlow.addColorStop(1, 'rgba(80,60,40,0.14)');
  ctx.fillStyle = horizonGlow;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const moonX = canvas.width * 0.76;
  const moonY = canvas.height * 0.2;
  const moonG = ctx.createRadialGradient(moonX, moonY, 0, moonX, moonY, 34);
  moonG.addColorStop(0, 'rgba(236,240,248,0.5)');
  moonG.addColorStop(0.4, 'rgba(210,220,238,0.16)');
  moonG.addColorStop(0.55, 'rgba(190,200,220,0.07)');
  moonG.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = moonG;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(503);
  for (let i = 0; i < 48; i++) {
    const sx = rand() * canvas.width;
    const sy = rand() * canvas.height * 0.58;
    const br = rand() * 1.1 + 0.35;
    ctx.fillStyle = 'rgba(255,255,255,' + (0.12 + rand() * 0.45) + ')';
    ctx.fillRect(sx, sy, br, br);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeNightSkylineLayerTexture(layer) {
  const canvas = document.createElement('canvas');
  canvas.width = 640;
  canvas.height = 320;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(layer === 'far' ? 11 : layer === 'mid' ? 23 : 37);
  const baseY = canvas.height * (layer === 'far' ? 0.78 : layer === 'mid' ? 0.62 : 0.5);
  const maxH = canvas.height * (layer === 'far' ? 0.2 : layer === 'mid' ? 0.36 : 0.46);
  const fog = layer === 'far' ? 0.52 : layer === 'mid' ? 0.82 : 1;
  let x = -12;
  while (x < canvas.width + 8) {
    const bw = (layer === 'near' ? 34 : 26) + rand() * (layer === 'near' ? 48 : 32);
    const bh = maxH * (0.32 + rand() * 0.68);
    const tone = 22 + rand() * (layer === 'far' ? 18 : 30);
    ctx.fillStyle =
      'rgba(' +
      (tone + 14) +
      ',' +
      (tone + 22) +
      ',' +
      (tone + 38) +
      ',' +
      fog +
      ')';
    ctx.fillRect(x, baseY - bh, bw, bh);
    const cols = Math.max(1, Math.floor(bw / 13));
    const rows = Math.max(1, Math.floor(bh / 15));
    for (let c = 0; c < cols; c++) {
      for (let r = 0; r < rows; r++) {
        if (rand() < (layer === 'far' ? 0.72 : 0.38)) continue;
        const warm = rand() > 0.4;
        const wx = x + 5 + c * 12 + rand() * 2;
        const wy = baseY - bh + 5 + r * 14 + rand();
        const ww = 3.5 + rand() * 3;
        const wh = 4 + rand() * 4;
        ctx.fillStyle = warm
          ? 'rgba(245,205,100,' + (0.35 + rand() * 0.55) + ')'
          : 'rgba(175,205,228,' + (0.28 + rand() * 0.42) + ')';
        ctx.fillRect(wx, wy, ww, wh);
      }
    }
    if (layer === 'near' && rand() > 0.55) {
      ctx.fillStyle = 'rgba(40,48,62,0.9)';
      const rw = bw * (0.5 + rand() * 0.35);
      ctx.fillRect(x + (bw - rw) / 2, baseY - bh - 4 - rand() * 6, rw, 4 + rand() * 5);
    }
    x += bw - rand() * 10;
  }
  if (layer === 'far') {
    const mist = ctx.createLinearGradient(0, canvas.height * 0.45, 0, canvas.height);
    mist.addColorStop(0, 'rgba(0,0,0,0)');
    mist.addColorStop(0.6, 'rgba(18,28,48,0.18)');
    mist.addColorStop(1, 'rgba(12,20,36,0.42)');
    ctx.fillStyle = mist;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeNightCloudLayerTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 200;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(71);
  for (let i = 0; i < 5; i++) {
    const cx = rand() * canvas.width;
    const cy = 30 + rand() * 90;
    const rw = 70 + rand() * 110;
    const rh = 22 + rand() * 28;
    const g = ctx.createRadialGradient(cx, cy, 4, cx, cy, rw);
    g.addColorStop(0, 'rgba(120,140,175,0.2)');
    g.addColorStop(0.55, 'rgba(95,115,155,0.12)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.ellipse(cx, cy, rw, rh, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeWindowRainTexture(lineCount) {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(97);
  const n = Math.max(16, Math.min(120, lineCount | 0));
  for (let i = 0; i < n; i++) {
    const x = rand() * canvas.width;
    const len = 12 + rand() * 28;
    const w = 0.6 + rand() * 0.9;
    ctx.strokeStyle = 'rgba(175,195,230,' + (0.15 + rand() * 0.22) + ')';
    ctx.lineWidth = w;
    ctx.beginPath();
    ctx.moveTo(x, rand() * canvas.height * 0.3);
    ctx.lineTo(x + 1.5, len);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeWindowGlassStreakTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 400;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(113);
  for (let i = 0; i < 9; i++) {
    const x = rand() * canvas.width;
    const y0 = rand() * canvas.height * 0.35;
    const len = 40 + rand() * 120;
    ctx.strokeStyle = 'rgba(200,220,245,' + (0.08 + rand() * 0.14) + ')';
    ctx.lineWidth = 1 + rand() * 2.2;
    ctx.beginPath();
    ctx.moveTo(x, y0);
    ctx.lineTo(x + 4 + rand() * 8, y0 + len);
    ctx.stroke();
  }
  for (let d = 0; d < 6; d++) {
    const dx = rand() * canvas.width;
    const dy = rand() * canvas.height;
    const r = 2 + rand() * 4;
    const g = ctx.createRadialGradient(dx, dy, 0, dx, dy, r);
    g.addColorStop(0, 'rgba(210,230,250,0.18)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(dx - r, dy - r, r * 2, r * 2);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

function makeSkylineFlickerLightsTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  const rand = privateTeamSeededRand(89);
  const lights = [];
  for (let i = 0; i < 14; i++) {
    lights.push({
      x: rand() * canvas.width,
      y: canvas.height * (0.35 + rand() * 0.55),
      w: 2 + rand() * 3,
      h: 2 + rand() * 4,
      on: rand() > 0.32,
      warm: rand() > 0.45
    });
  }
  const paint = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let li = 0; li < lights.length; li++) {
      const L = lights[li];
      if (!L.on) continue;
      ctx.fillStyle = L.warm ? 'rgba(255,210,120,0.78)' : 'rgba(180,215,240,0.68)';
      ctx.fillRect(L.x, L.y, L.w, L.h);
    }
  };
  paint();
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { tex, lights, paint, rand: privateTeamSeededRand(91) };
}

function stepSkylineFlickerLights(flicker) {
  const r = flicker.rand();
  const n = 2 + Math.floor(r() * 3);
  for (let i = 0; i < n; i++) {
    const L = flicker.lights[Math.floor(r() * flicker.lights.length)];
    if (r() > 0.5) L.on = !L.on;
  }
  flicker.paint();
}

function applyUnderwaterTint(mat, waterYUniform) {
  mat.alphaTest = 0.02;
  mat.onBeforeCompile = (shader) => {
    shader.uniforms.uWaterY = waterYUniform;
    shader.vertexShader = 'varying vec3 vWorldPos;\n' + shader.vertexShader.replace(
      '#include <project_vertex>',
      'vec4 worldPos = modelMatrix * vec4(transformed, 1.0);\n  vWorldPos = worldPos.xyz;\n  #include <project_vertex>'
    );
    shader.fragmentShader = 'uniform float uWaterY;\nvarying vec3 vWorldPos;\n' + shader.fragmentShader.replace(
      '#include <dithering_fragment>',
      [
        '#include <dithering_fragment>',
        'float sub = uWaterY - vWorldPos.y;',
        'if (sub > 0.0) {',
        '  float fade = smoothstep(0.0, 0.08, sub);',
        '  gl_FragColor.rgb = mix(gl_FragColor.rgb, gl_FragColor.rgb * vec3(0.22, 0.48, 0.58), fade);',
        '  gl_FragColor.a *= 1.0 - 0.97 * smoothstep(0.015, 0.12, sub);',
        '  if (gl_FragColor.a < 0.03) discard;',
        '}'
      ].join('\n')
    );
  };
  mat.customProgramCacheKey = () => 'secretary-underwater-tint-v2';
}

function addBathPillow(parent) {
  if (parent.getObjectByName('bathPillow')) return;
  const group = new THREE.Group();
  group.name = 'bathPillow';
  group.position.set(0.04, 0.48, -0.3);
  function towelBox(w, h, d, color) {
    const mesh = new THREE.Mesh(
      new THREE.BoxGeometry(w, h, d),
      new THREE.MeshBasicMaterial({ color })
    );
    mesh.renderOrder = 1;
    return mesh;
  }
  const cushion = towelBox(0.42, 0.3, 0.1, COL.towel);
  cushion.name = 'bathPillowCushion';
  cushion.position.set(0, 0.02, 0.03);
  cushion.rotation.x = 0.24;
  const roll = new THREE.Mesh(
    new THREE.CylinderGeometry(0.055, 0.055, 0.26, 14),
    new THREE.MeshBasicMaterial({ color: COL.towelDk })
  );
  roll.name = 'bathPillowRoll';
  roll.rotation.z = Math.PI / 2;
  roll.position.set(0.06, 0.17, 0.05);
  roll.renderOrder = 1;
  const fold = towelBox(0.14, 0.18, 0.05, COL.towelDk);
  fold.name = 'bathPillowFold';
  fold.position.set(-0.14, 0.05, 0.04);
  fold.rotation.z = -0.18;
  group.add(cushion, roll, fold);
  parent.add(group);
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

export const PROGRESS_BOARD_PLANE_W = 1.05;
export const PROGRESS_BOARD_PLANE_H = 0.65625;

export const BRONZE_POLISHED_GOLD = 0xcda864;

export function isBronzeBodyMaterialName(name) {
  const n = (name || '').toLowerCase();
  return n === 'bronze_body' || n === 'bronze.001';
}

export function isBronzePedestalMesh(meshName, matName) {
  const mat = (matName || '').toLowerCase();
  const mesh = (meshName || '').toLowerCase();
  if (mat.indexOf('pedestal') >= 0 || mat.indexOf('plaque') >= 0) return true;
  if (mesh.indexOf('pedestal') >= 0 || mesh.indexOf('plaque') >= 0) return true;
  return false;
}

export function shouldSkipBronzeMaterialCoerce(matName, meshName) {
  const mat = (matName || '').toLowerCase();
  const mesh = (meshName || '').toLowerCase();
  if (mat.indexOf('sash') >= 0) return true;
  if (isBronzePedestalMesh(meshName, matName)) return true;
  if (mat.indexOf('meridian') >= 0 || mat.indexOf('bronze_dark') >= 0) return true;
  return false;
}

/** 不含台座／牌子的人像 native 高（腳踏台座面到髮頂；GLB 量測約 1.728） */
export const BRONZE_FIGURE_NATIVE_HEIGHT = 1.728;
/** 整件含台座 native 高（約 1.92）；勿與 BRONZE_FIGURE_NATIVE_HEIGHT 混用 */
export const BRONZE_ASSEMBLY_NATIVE_HEIGHT = 1.92;
export const BRONZE_NAMEPLATE_OFFSET_Y = 0.08;

export function bronzeScaleForFigureHeight(figureNativeHeight) {
  const h =
    figureNativeHeight != null && Number(figureNativeHeight) > 0.01
      ? Number(figureNativeHeight)
      : BRONZE_FIGURE_NATIVE_HEIGHT;
  return SECRETARY_SPRITE_HEIGHT / h;
}

export function bronzeAssemblyDisplayHeight(figureNativeHeight = BRONZE_FIGURE_NATIVE_HEIGHT) {
  return BRONZE_ASSEMBLY_NATIVE_HEIGHT * bronzeScaleForFigureHeight(figureNativeHeight);
}

export function bronzeFigureNativeHeightFromModel(model) {
  const box = new THREE.Box3();
  const piece = new THREE.Box3();
  let any = false;
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    let matName = '';
    if (obj.material) {
      const m = Array.isArray(obj.material) ? obj.material[0] : obj.material;
      matName = m && m.name ? m.name : '';
    }
    if (isBronzePedestalMesh(obj.name, matName)) return;
    piece.setFromObject(obj);
    if (any) box.union(piece);
    else {
      box.copy(piece);
      any = true;
    }
  });
  if (!any) return BRONZE_FIGURE_NATIVE_HEIGHT;
  return Math.max(0.01, box.max.y - box.min.y);
}

export function isBronzeHairMaterial(matName, meshName) {
  const mat = (matName || '').toLowerCase();
  const mesh = (meshName || '').toLowerCase();
  return mat.indexOf('hair') >= 0 || mesh.indexOf('hair') >= 0;
}

function ellipsizeLine(ctx, text, maxW) {
  const s = text == null ? '' : String(text);
  if (!s) return '';
  if (ctx.measureText(s).width <= maxW) return s;
  let out = s;
  while (out.length > 1 && ctx.measureText(out + '…').width > maxW) {
    out = out.slice(0, -1);
  }
  return out + '…';
}

function wrapBoardLine(ctx, text, maxW, maxLines) {
  const s = text == null ? '' : String(text).trim();
  if (!s) return [];
  const words = s.split(/\s+/);
  const lines = [];
  let cur = '';
  for (let i = 0; i < words.length; i++) {
    const tryLine = cur ? cur + ' ' + words[i] : words[i];
    if (ctx.measureText(tryLine).width <= maxW) {
      cur = tryLine;
      continue;
    }
    if (cur) lines.push(cur);
    cur = words[i];
    if (lines.length >= maxLines - 1) break;
  }
  if (cur && lines.length < maxLines) lines.push(cur);
  if (lines.length > maxLines) lines.length = maxLines;
  if (lines.length === maxLines && words.length > 1) {
    lines[maxLines - 1] = ellipsizeLine(ctx, lines[maxLines - 1], maxW);
  }
  return lines;
}

function makeProgressBoardDrawer() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 320;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  function draw(view) {
    const bag = view && typeof view === 'object' ? view : {};
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#a8823c';
    ctx.fillRect(0, 0, canvas.width, canvas.height);
    ctx.fillStyle = '#6a5028';
    ctx.fillRect(6, 6, canvas.width - 12, canvas.height - 12);
    ctx.fillStyle = '#141c28';
    ctx.fillRect(14, 14, canvas.width - 28, canvas.height - 28);
    const ix = 28;
    const iy = 28;
    const iw = canvas.width - 56;
    const ih = canvas.height - 56;
    ctx.fillStyle = '#121a2c';
    ctx.fillRect(ix, iy, iw, ih);
    ctx.fillStyle = '#ecc440';
    ctx.font = 'bold 34px sans-serif';
    ctx.textAlign = 'left';
    ctx.textBaseline = 'top';
    ctx.fillText('施工進度', ix + 16, iy + 14);
    const status = bag.statusLine == null ? '待命中' : String(bag.statusLine);
    ctx.font = 'bold 26px sans-serif';
    ctx.fillStyle = status === '進行中' ? '#9ed4ff' : '#d8c090';
    ctx.fillText(status, ix + iw - 16 - ctx.measureText(status).width, iy + 18);
    let y = iy + 58;
    const issueLine = bag.issueLine == null ? '' : String(bag.issueLine);
    if (issueLine) {
      ctx.font = 'bold 40px sans-serif';
      ctx.fillStyle = '#f2e2b8';
      ctx.fillText(issueLine, ix + 16, y);
      y += 48;
    }
    const phrase = bag.phraseLine == null ? '' : String(bag.phraseLine);
    if (phrase) {
      ctx.font = '23px sans-serif';
      ctx.fillStyle = '#f2f6fc';
      const lines = wrapBoardLine(ctx, phrase, iw - 32, 2);
      for (let li = 0; li < lines.length; li++) {
        ctx.fillText(lines[li], ix + 16, y);
        y += 28;
      }
    } else if (status === '待命中') {
      ctx.font = '24px sans-serif';
      ctx.fillStyle = '#c8d0e0';
      ctx.fillText('待命中', ix + 16, y);
      y += 34;
    }
    const meta = bag.metaLine == null ? '' : String(bag.metaLine);
    if (meta) {
      ctx.font = 'bold 20px sans-serif';
      ctx.fillStyle = '#e2eaf6';
      ctx.fillText(ellipsizeLine(ctx, meta, iw - 32), ix + 16, y);
      y += 28;
    }
    const todayLabel = bag.todayLabel == null ? '' : String(bag.todayLabel);
    const frac = Number(bag.todayFraction);
    if (todayLabel && Number.isFinite(frac)) {
      const barY = iy + ih - 52;
      ctx.font = 'bold 20px sans-serif';
      ctx.fillStyle = '#f5e6a8';
      ctx.fillText(todayLabel, ix + 16, barY);
      const countText =
        bag.todayCountText == null ? '' : String(bag.todayCountText).trim();
      const pct = Math.round(frac * 100);
      const rightText = countText || pct + '%';
      ctx.fillText(rightText, ix + iw - 16 - ctx.measureText(rightText).width, barY);
      const barH = 14;
      const barTop = barY + 24;
      ctx.fillStyle = '#243248';
      ctx.fillRect(ix + 16, barTop, iw - 32, barH);
      ctx.fillStyle = '#ecc440';
      ctx.fillRect(ix + 16, barTop, Math.max(6, (iw - 32) * frac), barH);
    }
    tex.needsUpdate = true;
  }
  draw({
    statusLine: '待命中',
    issueLine: '',
    phraseLine: '',
    metaLine: '',
    todayLabel: '',
    todayFraction: null
  });
  return { tex, draw };
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

const BRONZE_GLB_URL = '/models/tcm-bronze-figure.glb';

function makeNameplateDrawer() {
  const canvas = document.createElement('canvas');
  canvas.width = 280;
  canvas.height = 88;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  function draw(text) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const label = text == null || String(text).trim() === '' ? '…' : String(text);
    ctx.fillStyle = '#f8fbff';
    ctx.strokeStyle = '#163a70';
    ctx.lineWidth = 8;
    const x = 10;
    const y = 10;
    const w = 260;
    const h = 64;
    const r = 14;
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.quadraticCurveTo(x + w, y, x + w, y + r);
    ctx.lineTo(x + w, y + h - r);
    ctx.quadraticCurveTo(x + w, y + h, x + w - r, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.quadraticCurveTo(x, y + h, x, y + h - r);
    ctx.lineTo(x, y + r);
    ctx.quadraticCurveTo(x, y, x + r, y);
    ctx.closePath();
    ctx.fill();
    ctx.stroke();
    const size = label.length > 5 ? 34 : 40;
    ctx.fillStyle = '#163a70';
    ctx.font = 'bold ' + size + 'px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, canvas.width / 2, 42);
    tex.needsUpdate = true;
  }
  return { tex, draw };
}

function makeBronzeEnvMap(renderer) {
  if (!renderer) return null;
  const w = 512;
  const h = 256;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx2d = canvas.getContext('2d');
  const sky = ctx2d.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, '#f8f2e8');
  sky.addColorStop(0.28, '#e8dfd0');
  sky.addColorStop(0.55, '#b8a080');
  sky.addColorStop(0.82, '#585048');
  sky.addColorStop(1, '#282420');
  ctx2d.fillStyle = sky;
  ctx2d.fillRect(0, 0, w, h);
  const sunSpot = ctx2d.createRadialGradient(w * 0.68, h * 0.26, 0, w * 0.68, h * 0.26, w * 0.4);
  sunSpot.addColorStop(0, 'rgba(255,215,150,0.92)');
  sunSpot.addColorStop(1, 'rgba(255,215,150,0)');
  ctx2d.fillStyle = sunSpot;
  ctx2d.fillRect(0, 0, w, h);
  const srcTex = new THREE.CanvasTexture(canvas);
  srcTex.colorSpace = THREE.SRGBColorSpace;
  srcTex.mapping = THREE.EquirectangularReflectionMapping;
  const pmrem = new THREE.PMREMGenerator(renderer);
  pmrem.compileEquirectangularShader();
  const envMap = pmrem.fromEquirectangular(srcTex).texture;
  srcTex.dispose();
  pmrem.dispose();
  return envMap;
}

export function bronzeBodyMaterialFrom(root) {
  let bodyMat = null;
  if (!root) return null;
  root.traverse((obj) => {
    if (bodyMat || !obj.isMesh || !obj.material) return;
    const meshName = (obj.name || '').toLowerCase();
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    for (let i = 0; i < mats.length; i++) {
      const m = mats[i];
      if (!m || !m.isMeshStandardMaterial) continue;
      const matName = m.name || '';
      if (isBronzeBodyMaterialName(matName)) {
        bodyMat = m;
        return;
      }
      if (meshName === 'bronze_body' && (matName || '').toLowerCase().indexOf('bronze') >= 0) {
        bodyMat = m;
        return;
      }
    }
  });
  return bodyMat;
}

export function coerceBronzeHumanMaterials(root) {
  if (!root) return;
  const bodyMat = bronzeBodyMaterialFrom(root);
  const gold = new THREE.Color(BRONZE_POLISHED_GOLD);
  const hairMetal = 0.9;
  const hairRough = 0.3;
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return;
    const meshName = obj.name || '';
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    for (let i = 0; i < mats.length; i++) {
      const m = mats[i];
      if (!m || !m.isMeshStandardMaterial) continue;
      const matName = m.name || '';
      if (shouldSkipBronzeMaterialCoerce(matName, meshName)) continue;
      if (isBronzeHairMaterial(matName, meshName)) {
        m.color.copy(gold);
        m.metalness = hairMetal;
        m.roughness = hairRough;
        m.needsUpdate = true;
        continue;
      }
      if (!bodyMat) continue;
      const needsCoerce = m.metalness < 0.45 && (matName || '').toLowerCase().indexOf('bronze') >= 0;
      if (!needsCoerce) continue;
      m.color.copy(bodyMat.color);
      m.metalness = bodyMat.metalness;
      m.roughness = bodyMat.roughness;
      m.needsUpdate = true;
    }
  });
}

function applyBronzeEnvMap(root, envMap, intensityStore) {
  if (!envMap || !root) return;
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    for (let i = 0; i < mats.length; i++) {
      const m = mats[i];
      if (!m || !m.isMeshStandardMaterial) continue;
      const isMetal = m.metalness >= 0.45;
      const isFabric = (m.name || '').toLowerCase().indexOf('sash') >= 0;
      if (!isMetal || isFabric) continue;
      m.envMap = envMap;
      const base = isMetal ? 1.28 : 0.85;
      if (intensityStore) intensityStore.set(m, base);
      m.envMapIntensity = base;
      m.needsUpdate = true;
    }
  });
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

function addMonitor(root, lookTarget, x, z, scale, traySurfaceY) {
  const group = new THREE.Group();
  const s = scale == null ? 1 : scale;
  const trayY = traySurfaceY == null ? PRIVATE_TEAM_TRAY_SURFACE_Y : traySurfaceY;
  group.position.set(x, 0.86, z);
  group.scale.set(s, s, s);
  const bezel = box(0.58, 0.42, 0.05, COL.screen);
  const painter = makeScreenPainter();
  const mountLocalY = trayY - group.position.y;
  const bezelBottom = -0.21;
  const standBottom = mountLocalY + 0.008;
  const standH = Math.max(0.08, bezelBottom - standBottom);
  const stand = box(0.09, standH, 0.07, COL.screenStand);
  stand.position.y = (bezelBottom + standBottom) / 2;
  const basePlate = box(0.15, 0.018, 0.11, COL.screenStand);
  basePlate.position.y = standBottom - 0.009;
  const bracket = box(0.04, standH * 0.55, 0.05, COL.screenStand);
  bracket.position.set(0, standBottom + standH * 0.22, 0.04);
  group.add(bezel, painter.mesh, stand, basePlate, bracket);
  group.lookAt(lookTarget.x, lookTarget.y, lookTarget.z);
  root.add(group);

  const screenGlow = new THREE.PointLight(0x8ab8e8, 0.28, 2.4, 2);
  screenGlow.position.set(0, 0.05, 0.12);
  group.add(screenGlow);

  return { painter, screenGlow, group };
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
  const camYawAxis = new THREE.Vector3(0, 1, 0);
  let zoomFactor = 1;
  let userAdjustedZoom = false;
  let defaultZoomApplied = false;
  let yawRadians = 0;
  let yawReturnFrom = 0;
  let yawReturnStartMs = null;

  const camera = new THREE.PerspectiveCamera(42, 1, 0.1, 80);
  function applyCameraView() {
    zoomFactor = clampPrivateTeamZoom(zoomFactor);
    const dir = camBase.clone().sub(camLook);
    dir.applyAxisAngle(camYawAxis, yawRadians);
    camera.position.copy(camLook).add(dir.multiplyScalar(zoomFactor));
    camera.lookAt(camLook);
  }
  function applyZoom(factor, fromUser) {
    zoomFactor = clampPrivateTeamZoom(factor);
    if (fromUser) userAdjustedZoom = true;
    applyCameraView();
  }
  applyCameraView();

  const lighting = privateTeamSceneLighting();
  const amb = new THREE.AmbientLight(0xe8eeff, lighting.amb);
  const dir = new THREE.DirectionalLight(0xb0c0f0, lighting.dir);
  dir.position.set(3.5, 7, 4);
  scene.add(amb, dir);

  const texLoader = new THREE.TextureLoader();
  const wallTex = loadRepeatTex(texLoader, 'wall.webp', 2.2, 1.4);
  const floorTex = loadRepeatTex(texLoader, 'floor-wood.webp', 2.4, 2.4);
  const deskTex = loadRepeatTex(texLoader, 'desk-wood.webp', 2.2, 1.2);

  const root = new THREE.Group();
  scene.add(root);

  const floorMat = new THREE.MeshLambertMaterial({ map: floorTex, color: 0xffffff });
  const floor = extrudedRoundedPlate(FLOOR_HALF, FLOOR_HALF, 0.035, 0.08, floorMat);
  floor.position.y = 0;
  root.add(floor);

  const TRAY_THICK = 0.036;
  const TRAY_TOP_Y = PRIVATE_TEAM_TRAY_SURFACE_Y;
  const trayZ = 0.12;
  const WATER_Y = PRIVATE_TEAM_WATER_Y;
  const RIM_TOP_Y = 0.62;
  const TUB_CX = 0;
  const TUB_CZ = 0.64;
  const TUB_HW = 0.84;
  const TUB_HD = 0.52;
  const WALL_T = 0.08;
  const IN_HW = TUB_HW - WALL_T;
  const IN_HD = TUB_HD - WALL_T;
  const outerH = RIM_TOP_Y - 0.02;
  const outerMidY = 0.02 + outerH / 2;
  const tileTex = makeStoneTileTexture(8, 3);
  const tileSideTex = makeStoneTileTexture(8, 4);
  const outerMat = new THREE.MeshLambertMaterial({ map: tileSideTex, color: COL.tub });
  const innerMat = new THREE.MeshLambertMaterial({ color: COL.tubInner });
  const rimMat = new THREE.MeshLambertMaterial({ map: tileTex, color: COL.tubRim });
  const waterYUniform = { value: WATER_Y };

  const tubFloor = box(IN_HW * 2 - 0.02, 0.03, IN_HD * 2 - 0.02, COL.tubInner);
  tubFloor.position.set(TUB_CX, 0.03, TUB_CZ);
  tubFloor.name = 'bathtubFloor';
  root.add(tubFloor);

  const frontZ = TUB_CZ + TUB_HD - WALL_T / 2;
  const backZTub = TUB_CZ - TUB_HD + WALL_T / 2;
  const tubFront = box(TUB_HW * 2, outerH, WALL_T, COL.tub);
  tubFront.material = outerMat.clone();
  tubFront.position.set(TUB_CX, outerMidY, frontZ);
  tubFront.name = 'bathtubFront';
  root.add(tubFront);
  const tubBack = box(TUB_HW * 2, outerH, WALL_T, COL.tub);
  tubBack.material = outerMat.clone();
  tubBack.position.set(TUB_CX, outerMidY, backZTub);
  tubBack.name = 'bathtubBack';
  root.add(tubBack);
  const tubLeft = box(WALL_T, outerH, TUB_HD * 2 - WALL_T * 2, COL.tub);
  tubLeft.material = outerMat.clone();
  tubLeft.position.set(TUB_CX - TUB_HW + WALL_T / 2, outerMidY, TUB_CZ);
  tubLeft.name = 'bathtubLeft';
  root.add(tubLeft);
  const tubRight = box(WALL_T, outerH, TUB_HD * 2 - WALL_T * 2, COL.tub);
  tubRight.material = outerMat.clone();
  tubRight.position.set(TUB_CX + TUB_HW - WALL_T / 2, outerMidY, TUB_CZ);
  tubRight.name = 'bathtubRight';
  root.add(tubRight);

  const rim = extrudedRoundedRing(TUB_HW + 0.03, TUB_HD + 0.03, IN_HW, IN_HD, 0.055, 0.07, rimMat);
  rim.position.set(TUB_CX, RIM_TOP_Y - 0.045, TUB_CZ);
  rim.name = 'bathtubRim';
  root.add(rim);
  const liningH = RIM_TOP_Y - 0.08;
  const liningY = 0.04 + liningH / 2;
  const liningT = 0.02;
  function addLining(name, w, h, d, x, y, z) {
    const mesh = box(w, h, d, COL.tubInner);
    mesh.position.set(x, y, z);
    mesh.name = name;
    root.add(mesh);
    return mesh;
  }
  addLining('bathtubInnerFront', IN_HW * 2, liningH, liningT, TUB_CX, liningY, TUB_CZ + IN_HD - liningT / 2);
  addLining('bathtubInnerBack', IN_HW * 2, liningH, liningT, TUB_CX, liningY, TUB_CZ - IN_HD + liningT / 2);
  addLining('bathtubInnerLeft', liningT, liningH, IN_HD * 2 - liningT * 2, TUB_CX - IN_HW + liningT / 2, liningY, TUB_CZ);
  addLining('bathtubInnerRight', liningT, liningH, IN_HD * 2 - liningT * 2, TUB_CX + IN_HW - liningT / 2, liningY, TUB_CZ);

  const waterMat = new THREE.MeshLambertMaterial({
    color: COL.water,
    transparent: true,
    opacity: 0.9,
    depthWrite: false
  });
  const water = extrudedRoundedPlate(IN_HW - 0.02, IN_HD - 0.02, 0.012, 0.05, waterMat);
  water.position.set(TUB_CX, WATER_Y, TUB_CZ);
  water.name = 'bathtubWater';
  water.renderOrder = 1;
  root.add(water);
  const waterRippleTex = makeWaterRippleTexture();
  const rippleMat = new THREE.MeshBasicMaterial({
    map: waterRippleTex,
    color: COL.waterHi,
    transparent: true,
    opacity: 0.28,
    depthWrite: false
  });
  const waterRipple = extrudedRoundedPlate(IN_HW - 0.04, IN_HD - 0.04, 0.004, 0.05, rippleMat);
  waterRipple.position.set(TUB_CX, WATER_Y + 0.008, TUB_CZ);
  waterRipple.name = 'bathtubWaterHighlight';
  waterRipple.renderOrder = 1;
  root.add(waterRipple);

  const trayMat = new THREE.MeshLambertMaterial({ map: deskTex, color: COL.desk });
  const tray = extrudedRoundedPlate(0.54, 0.26, TRAY_THICK, 0.04, trayMat);
  tray.position.set(0, TRAY_TOP_Y - TRAY_THICK, trayZ);
  tray.name = 'keyboardTray';
  root.add(tray);
  const steamPuffs = [];
  const steamGroup = new THREE.Group();
  steamGroup.position.set(-0.08, WATER_Y + 0.04, TUB_CZ + 0.22);
  steamGroup.name = 'tubSteam';
  for (let si = 0; si < 4; si++) {
    const puffMat = new THREE.MeshBasicMaterial({
      color: 0xf8fbff,
      transparent: true,
      opacity: 0.07,
      depthWrite: false
    });
    const puff = new THREE.Mesh(new THREE.SphereGeometry(0.035 + si * 0.008, 6, 5), puffMat);
    puff.position.set((si - 1.5) * 0.055, si * 0.045, 0);
    steamGroup.add(puff);
    steamPuffs.push(puff);
  }
  root.add(steamGroup);

  const wallMat = new THREE.MeshLambertMaterial({ map: wallTex, color: 0xd8e4f4 });
  const backZ = -FLOOR_HALF;
  const leftX = -FLOOR_HALF;
  const wallY = 1.22;
  const wallH = 2.44;

  function wallSlab(w, h, x, y, z, depthZ) {
    const dz = depthZ == null ? WALL_THICK : depthZ;
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, dz), wallMat);
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
  wallSlab(WALL_THICK, wallH, leftX + WALL_THICK / 2, wallY, 0, FLOOR_SIZE);

  const skyTex = makeNightWindowSkyTexture();
  const windowSky = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.2, winH + 0.2),
    new THREE.MeshBasicMaterial({ map: skyTex })
  );
  windowSky.position.set(0, winBottom + winH / 2, backZ - WALL_THICK - PRIVATE_TEAM_WINDOW_SKY_Z);
  windowSky.name = 'windowSky';
  root.add(windowSky);

  const skyline = new THREE.Group();
  skyline.name = 'skyline';
  const skylineCenterY = winBottom + winH / 2;
  const skylineLayers = [
    { name: 'skylineFar', layer: 'far', z: PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z[0], hScale: 0.82, yBias: -0.06 },
    { name: 'skylineMid', layer: 'mid', z: PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z[1], hScale: 0.92, yBias: -0.02 },
    { name: 'skylineNear', layer: 'near', z: PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z[2], hScale: 1, yBias: 0.04 }
  ];
  skylineLayers.forEach((cfg) => {
    const layerTex = makeNightSkylineLayerTexture(cfg.layer);
    const layerMat = new THREE.MeshBasicMaterial({
      map: layerTex,
      transparent: true,
      depthWrite: false,
      opacity: cfg.layer === 'far' ? 0.88 : 1
    });
    const layerMesh = new THREE.Mesh(
      new THREE.PlaneGeometry(winW + 0.12, winH * cfg.hScale),
      layerMat
    );
    layerMesh.position.set(0, skylineCenterY + cfg.yBias, backZ - WALL_THICK - cfg.z);
    layerMesh.name = cfg.name;
    skyline.add(layerMesh);
  });
  root.add(skyline);

  const flickerLights = makeSkylineFlickerLightsTexture();
  const windowSkylineFlicker = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.1, winH * 0.78),
    new THREE.MeshBasicMaterial({
      map: flickerLights.tex,
      transparent: true,
      depthWrite: false,
      opacity: 0.82,
      blending: THREE.AdditiveBlending
    })
  );
  windowSkylineFlicker.position.set(0, skylineCenterY + 0.02, backZ - WALL_THICK - 0.085);
  windowSkylineFlicker.name = 'windowSkylineFlicker';
  root.add(windowSkylineFlicker);

  const cloudTex = makeNightCloudLayerTexture();
  const windowClouds = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.16, winH * 0.52),
    new THREE.MeshBasicMaterial({
      map: cloudTex,
      transparent: true,
      depthWrite: false,
      opacity: 0.62
    })
  );
  windowClouds.position.set(0, skylineCenterY + winH * 0.18, backZ - WALL_THICK - 0.052);
  windowClouds.name = 'windowClouds';
  root.add(windowClouds);

  const coarsePointer =
    typeof win.matchMedia === 'function' && win.matchMedia('(pointer: coarse)').matches;
  const narrowWin = (win.innerWidth || 1024) < 640;
  const rainLineCount = coarsePointer || narrowWin ? 44 : 86;
  const rainTex = makeWindowRainTexture(rainLineCount);
  const rainMat = new THREE.MeshBasicMaterial({
    map: rainTex,
    transparent: true,
    opacity: 0.34,
    depthWrite: false
  });
  const windowRainBack = new THREE.Mesh(new THREE.PlaneGeometry(winW + 0.04, winH - 0.06), rainMat);
  windowRainBack.position.set(0, skylineCenterY, backZ - WALL_THICK - 0.032);
  windowRainBack.visible = false;
  windowRainBack.name = 'windowRainBack';
  root.add(windowRainBack);

  const streakTex = makeWindowGlassStreakTexture();
  const windowGlassStreaks = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.02, winH - 0.1),
    new THREE.MeshBasicMaterial({
      map: streakTex,
      transparent: true,
      opacity: 0.28,
      depthWrite: false
    })
  );
  windowGlassStreaks.visible = false;
  windowGlassStreaks.name = 'windowGlassStreaks';
  const glassY = winBottom + winH / 2;

  const glassMat = new THREE.MeshLambertMaterial({
    color: 0x8aa0c0,
    emissive: 0x2a1e10,
    emissiveIntensity: 0.26,
    transparent: true,
    opacity: 0.11,
    depthWrite: false
  });
  const paneW = (winW - 0.12) / 3;
  const glassZ = backZ + 0.02;
  windowGlassStreaks.position.set(0, glassY, glassZ + 0.012);
  root.add(windowGlassStreaks);

  const airplaneGroup = new THREE.Group();
  airplaneGroup.name = 'windowAirplane';
  airplaneGroup.visible = false;
  const planeBody = new THREE.Mesh(
    new THREE.SphereGeometry(0.01, 4, 4),
    new THREE.MeshBasicMaterial({ color: 0xfff4e0, transparent: true, opacity: 0.95 })
  );
  const planeWing = new THREE.Mesh(
    new THREE.SphereGeometry(0.007, 4, 4),
    new THREE.MeshBasicMaterial({ color: 0xffeecc, transparent: true, opacity: 0.75 })
  );
  planeWing.position.set(-0.022, 0, 0);
  const planeTail = new THREE.Mesh(
    new THREE.SphereGeometry(0.006, 4, 4),
    new THREE.MeshBasicMaterial({ color: 0xffe8b8, transparent: true, opacity: 0.7 })
  );
  planeTail.position.set(0.018, 0.006, 0);
  airplaneGroup.add(planeBody, planeWing, planeTail);
  airplaneGroup.position.set(winLeft, winTop - 0.22, backZ - WALL_THICK - 0.19);
  root.add(airplaneGroup);
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
  frameBar(winW + 0.12, 0.035, 0, glassY);

  const windowWash = new THREE.Mesh(
    new THREE.PlaneGeometry(2.8, 1.35),
    new THREE.MeshBasicMaterial({
      color: 0x5a78b0,
      transparent: true,
      opacity: 0.12,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
  );
  windowWash.rotation.x = -Math.PI / 2;
  windowWash.position.set(0, 0.022, -1.05);
  root.add(windowWash);

  const progressDrawer = makeProgressBoardDrawer();
  const progMesh = new THREE.Mesh(
    new THREE.PlaneGeometry(PROGRESS_BOARD_PLANE_W, PROGRESS_BOARD_PLANE_H),
    new THREE.MeshBasicMaterial({ map: progressDrawer.tex })
  );
  progMesh.name = 'progressBoard';
  progMesh.position.set(leftX + WALL_THICK + 0.05, 1.52, 0.1);
  progMesh.rotation.y = Math.PI / 2;
  root.add(progMesh);
  const progressGlow = new THREE.PointLight(0xc8a850, 0.16, 2.4, 2);
  progressGlow.position.set(leftX + WALL_THICK + 0.12, 1.52, 0.24);
  root.add(progressGlow);

  const monitorLook = new THREE.Vector3(0.02, 1.02, 0.72);
  const monitors = [addMonitor(root, monitorLook, -0.96, 0.94, 0.98, 0.02)];

  const mug = new THREE.Mesh(
    new THREE.CylinderGeometry(0.09, 0.075, 0.18, 12),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mug.position.set(-0.38, TRAY_TOP_Y + 0.09, 0.32);
  const mugHandle = new THREE.Mesh(
    new THREE.TorusGeometry(0.05, 0.015, 6, 10),
    new THREE.MeshBasicMaterial({ color: COL.mug })
  );
  mugHandle.position.set(-0.46, TRAY_TOP_Y + 0.09, 0.32);
  mugHandle.rotation.y = Math.PI / 2;
  root.add(mug, mugHandle);

  const lampGroup = new THREE.Group();
  lampGroup.position.set(0.78, TRAY_TOP_Y + 0.016, 0.36);
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

  const atlasRoot = new THREE.Group();
  atlasRoot.name = 'atlasBronzeDisplay';
  atlasRoot.userData.pickRole = 'atlas';
  atlasRoot.position.set(1.28, 0, -0.58);
  atlasRoot.rotation.y = 0.4;
  root.add(atlasRoot);

  const nameplateDrawer = makeNameplateDrawer();
  nameplateDrawer.draw(
    win.PUNCHCLOCK_AUTHENTICATED === true ? '經絡研習室' : '經絡研習室需登入'
  );
  const nameplateMat = new THREE.MeshBasicMaterial({
    map: nameplateDrawer.tex,
    transparent: true,
    depthWrite: false
  });
  const atlasNameplate = new THREE.Mesh(new THREE.PlaneGeometry(0.54, 0.17), nameplateMat);
  atlasNameplate.userData.pickRole = 'atlas';
  atlasNameplate.position.set(
    0,
    bronzeAssemblyDisplayHeight() + BRONZE_NAMEPLATE_OFFSET_Y,
    0.1
  );
  atlasNameplate.renderOrder = 2;
  atlasRoot.add(atlasNameplate);

  let bronzeEnvMap = null;
  const bronzeEnvBase = new WeakMap();
  let bronzeHover = false;

  const secretaryRoot = new THREE.Group();
  secretaryRoot.position.set(0.02, 0, 0.56);
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
  addBathPillow(secretaryRoot);

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
  let lastIdleRotateMs = null;
  let lastIdleDanceMs = null;
  let danceDisplayFrame = -1;
  let currentSpriteUrl = '';
  let desiredSpriteUrl = '';
  let spriteLoadSeq = 0;
  const loader = new THREE.TextureLoader();

  (function preloadSecretaryCelebrateFrames() {
    const seen = new Set();
    for (let i = 0; i < SECRETARY_CELEBRATE_STEPS.length; i++) {
      const step = SECRETARY_CELEBRATE_STEPS[i];
      if (step.kind !== 'frame') continue;
      const url = step.url;
      if (!url || seen.has(url)) continue;
      seen.add(url);
      loader.load(url, () => {}, undefined, () => {});
    }
    for (let i = 0; i < SECRETARY_STANDBY_IDLE_URLS.length; i++) {
      const url = SECRETARY_STANDBY_IDLE_URLS[i];
      if (url) loader.load(url, () => {}, undefined, () => {});
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
    const url = secretaryCelebrateFrameUrlAt(0);
    if (url) applySecretarySpriteUrl(url);
  }

  function noteTigerStatus(status, now) {
    const next = status == null ? '' : String(status).trim() || '待命中';
    const t = Number.isFinite(now) ? now : performance.now();
    const wasStandby = isStandbyTigerStatus(tigerStatus);
    tigerStatus = next;
    if (isStandbyTigerStatus(tigerStatus)) {
      if (!wasStandby || standbySinceMs == null) {
        standbySinceMs = t;
        lastIdleRotateMs = t;
      }
    } else {
      standbySinceMs = null;
      lastIdleRotateMs = null;
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
      alphaTest: 0.02,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    applyUnderwaterTint(mat, waterYUniform);
    secretarySprite = new THREE.Mesh(new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT), mat);
    secretarySprite.position.set(0, spriteBaseY, 0);
    secretarySprite.renderOrder = 2;
    secretarySprite.name = 'secretarySprite';
    secretaryRoot.add(secretarySprite);
    secretarySprite.userData.pickRole = 'secretary';
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
    if (!next) return;
    if (shouldIgnoreStandbyIdleSpriteUpdate(statusBaseUrl, next)) return;
    if (next === statusBaseUrl) return;
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
  bubble.userData.pickRole = 'secretary';
  root.add(bubble);

  progMesh.userData.pickRole = 'progress';

  const clickables = [bubble, progMesh, atlasRoot];
  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const boardCorner = new THREE.Vector3();
  const onActivate = typeof (opts && opts.onActivate) === 'function' ? opts.onActivate : null;
  const onOpenProgress = typeof (opts && opts.onOpenProgress) === 'function' ? opts.onOpenProgress : null;
  const onProgressBoardRect = typeof (opts && opts.onProgressBoardRect) === 'function' ? opts.onProgressBoardRect : null;
  let sceneInputEnabled = true;

  function pickRoleFromObject(obj) {
    let cur = obj;
    while (cur) {
      const role = cur.userData && cur.userData.pickRole;
      if (role) return role;
      cur = cur.parent;
    }
    return null;
  }

  function setBronzeHover(next) {
    bronzeHover = !!next;
    if (!atlasRoot) return;
    atlasRoot.traverse((obj) => {
      if (!obj.isMesh || !obj.material) return;
      const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
      for (let i = 0; i < mats.length; i++) {
        const m = mats[i];
        if (!m || !m.isMeshStandardMaterial) continue;
        const base = bronzeEnvBase.get(m);
        if (base == null) continue;
        m.envMapIntensity = bronzeHover && !reducedMotion ? base + 0.22 : base;
      }
    });
  }

  function beginAtlasBronzeLoad() {
    void (async () => {
      try {
        const mod = await import('./vendor/GLTFLoader.js');
        const GLTFLoader = mod.GLTFLoader;
        const loader = new GLTFLoader();
        const gltf = await new Promise((resolve, reject) => {
          loader.load(BRONZE_GLB_URL, resolve, undefined, reject);
        });
        if (!running) return;
        const model = gltf.scene;
        model.traverse((obj) => {
          if (obj.isMesh) obj.userData.pickRole = 'atlas';
        });
        const figureHeight = bronzeFigureNativeHeightFromModel(model);
        const scale = bronzeScaleForFigureHeight(figureHeight);
        model.scale.setScalar(scale);
        const bounds = new THREE.Box3().setFromObject(model);
        model.position.y = -bounds.min.y;
        bounds.setFromObject(model);
        atlasNameplate.position.y = bounds.max.y + BRONZE_NAMEPLATE_OFFSET_Y;
        if (!bronzeEnvMap) {
          bronzeEnvMap = makeBronzeEnvMap(renderer);
          if (bronzeEnvMap) scene.environment = bronzeEnvMap;
        }
        coerceBronzeHumanMaterials(model);
        applyBronzeEnvMap(model, bronzeEnvMap, bronzeEnvBase);
        atlasRoot.add(model);
      } catch (err) {
        console.warn('[PrivateTeam] 銅人載入失敗', err);
      }
    })();
  }

  beginAtlasBronzeLoad();

  const activePointers = new Map();
  let pinchActive = false;
  let pinchStartDist = 0;
  let pinchStartZoom = 1;
  let orbitPointerId = null;
  let orbitStartX = 0;
  let orbitStartY = 0;
  let orbitStartYaw = 0;
  let orbitDragged = false;
  let orbitPendingRole = null;

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
    const hw = PROGRESS_BOARD_PLANE_W / 2;
    const hh = PROGRESS_BOARD_PLANE_H / 2;
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
    const hits = raycaster.intersectObjects(clickables, true);
    if (!hits.length) return null;
    return pickRoleFromObject(hits[0].object);
  }

  function resetOrbitGesture() {
    orbitPointerId = null;
    orbitPendingRole = null;
    orbitDragged = false;
  }

  function activatePickRole(target) {
    if (!target) return;
    if (target === 'progress') {
      if (onOpenProgress) onOpenProgress();
      return;
    }
    if (target === 'atlas') {
      if (onActivate) onActivate('atlas');
      return;
    }
    playSecretaryWave();
    if (onActivate) onActivate('secretary');
  }

  function handlePointerDown(ev) {
    if (!sceneInputEnabled) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (activePointers.size >= 2) {
      pinchActive = true;
      pinchStartDist = pointerDistance();
      pinchStartZoom = zoomFactor;
      resetOrbitGesture();
      ev.preventDefault();
      return;
    }
    if (pinchActive) return;
    orbitPointerId = ev.pointerId;
    orbitStartX = ev.clientX;
    orbitStartY = ev.clientY;
    orbitStartYaw = yawRadians;
    orbitDragged = false;
    orbitPendingRole = pickTarget(ev.clientX, ev.clientY);
    yawReturnStartMs = null;
    if (orbitPendingRole) {
      ev.preventDefault();
    }
  }

  function handlePointerMove(ev) {
    if (!activePointers.has(ev.pointerId)) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (pinchActive && activePointers.size >= 2 && pinchStartDist > 0) {
      const dist = pointerDistance();
      applyZoom(privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist), true);
      ev.preventDefault();
      return;
    }
    if (
      !pinchActive &&
      sceneInputEnabled &&
      orbitPointerId === ev.pointerId &&
      activePointers.size === 1
    ) {
      const dx = ev.clientX - orbitStartX;
      const dy = ev.clientY - orbitStartY;
      if (!orbitDragged && privateTeamDragExceedsThreshold(dx, dy)) {
        orbitDragged = true;
        try {
          canvas.setPointerCapture(ev.pointerId);
        } catch (_e) { /* noop */ }
      }
      if (orbitDragged) {
        const rect = canvas.getBoundingClientRect();
        yawRadians = privateTeamYawFromDragDelta(dx, rect.width, orbitStartYaw);
        applyCameraView();
        ev.preventDefault();
        canvas.style.cursor = 'grabbing';
        setBronzeHover(false);
        return;
      }
    }
    if (!pinchActive && sceneInputEnabled && !orbitDragged) {
      const hover = pickTarget(ev.clientX, ev.clientY);
      const pointerOn = hover === 'atlas' || hover === 'secretary' || hover === 'progress';
      canvas.style.cursor = pointerOn ? 'pointer' : '';
      setBronzeHover(hover === 'atlas');
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
    if (ev.pointerId === orbitPointerId) {
      if (!orbitDragged && orbitPendingRole) {
        activatePickRole(orbitPendingRole);
      } else if (orbitDragged && Math.abs(yawRadians) > 1e-6) {
        yawReturnFrom = yawRadians;
        yawReturnStartMs = performance.now();
      }
      resetOrbitGesture();
      canvas.style.cursor = '';
      try {
        if (canvas.hasPointerCapture(ev.pointerId)) {
          canvas.releasePointerCapture(ev.pointerId);
        }
      } catch (_e) { /* noop */ }
    }
    clearPointer(ev);
  }

  function onWheel(ev) {
    if (!sceneInputEnabled) return;
    ev.preventDefault();
    const delta = ev.deltaY;
    const step = delta > 0 ? 0.045 : -0.045;
    applyZoom(zoomFactor * (1 + step), true);
  }

  canvas.addEventListener('pointerdown', handlePointerDown);
  canvas.addEventListener('pointermove', handlePointerMove);
  canvas.addEventListener('pointerup', handlePointerUp);
  canvas.addEventListener('pointercancel', handlePointerUp);
  canvas.addEventListener('wheel', onWheel, { passive: false });

  let running = true;
  let animationPaused = false;
  if (typeof win.matchMedia === 'function') {
    const mq = win.matchMedia('(prefers-reduced-motion: reduce)');
    reducedMotion = !!mq.matches;
    if (typeof mq.addEventListener === 'function') {
      mq.addEventListener('change', () => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        if (
          reducedMotion &&
          isStandbyTigerStatus(tigerStatus) &&
          isSecretaryStandbyIdleUrl(statusBaseUrl) &&
          statusBaseUrl !== SECRETARY_SPRITE_URL_IDLE
        ) {
          statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
          lastIdleRotateMs = null;
        }
        refreshSecretaryDisplay();
      });
    } else if (typeof mq.addListener === 'function') {
      mq.addListener(() => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        if (
          reducedMotion &&
          isStandbyTigerStatus(tigerStatus) &&
          isSecretaryStandbyIdleUrl(statusBaseUrl) &&
          statusBaseUrl !== SECRETARY_SPRITE_URL_IDLE
        ) {
          statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
          lastIdleRotateMs = null;
        }
        refreshSecretaryDisplay();
      });
    }
  }
  let raf = 0;
  let t0 = performance.now();
  let windowRainActive = Math.random() > 0.45;
  let windowRainToggleAt = t0 + 52000 + Math.random() * 80000;
  let lastSkylineFlickerMs = 0;
  let airplaneActive = false;
  let airplaneStartMs = 0;
  let airplaneNextAt = t0 + 22000 + Math.random() * 36000;
  const WINDOW_AIRPLANE_MS = 4800;

  function resize() {
    const parent = canvas.parentElement;
    const w = parent ? parent.clientWidth : 1;
    const h = parent ? parent.clientHeight : 1;
    renderer.setPixelRatio(scenePixelRatio());
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(h, 1);
    camera.updateProjectionMatrix();
    if (!defaultZoomApplied && !userAdjustedZoom) {
      applyZoom(privateTeamDefaultZoom(w, h));
      defaultZoomApplied = true;
    }
  }

  function tick(now) {
    if (!running) return;
    raf = win.requestAnimationFrame(tick);
    if (animationPaused) return;
    if (dancePlayback) {
      const elapsed = now - dancePlayback.startMs;
      if (elapsed >= SECRETARY_DANCE_DURATION_MS) {
        stopSecretaryDance();
      } else {
        const st = secretaryCelebrateStateAt(elapsed);
        if (st.kind !== 'done') {
          const frameKey = st.kind === 'jump' ? 'jump' : st.url;
          if (frameKey !== danceDisplayFrame) {
            danceDisplayFrame = frameKey;
            if (st.url) applySecretarySpriteUrl(st.url);
          }
          dancePlayback.jumpOffset = secretaryCelebrateJumpOffsetAt(elapsed);
        }
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
    } else if (
      !overlayActive &&
      !dancePlayback &&
      !reducedMotion &&
      isStandbyTigerStatus(tigerStatus) &&
      isSecretaryStandbyIdleUrl(statusBaseUrl) &&
      lastIdleRotateMs != null &&
      now - lastIdleRotateMs >= SECRETARY_IDLE_ROTATE_MS
    ) {
      lastIdleRotateMs = now;
      statusBaseUrl = pickNextStandbyIdleUrl(statusBaseUrl, Math.floor(Math.random() * 1e6));
      refreshPoseAnimMode();
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
        if (dancePlayback && dancePlayback.jumpOffset) {
          extraY = dancePlayback.jumpOffset;
        } else {
          extraY = breath;
          if (spriteAnimMode === 'working') {
            extraY += typeNudge;
            leanZ = lean;
          } else if (spriteAnimMode === 'sleep') {
            extraY = breath * 0.45;
          }
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
    for (let mi = 0; mi < monitors.length; mi++) {
      monitors[mi].painter.draw(pulse);
      monitors[mi].screenGlow.intensity = 0.22 + pulse * 0.12;
    }

    if (reducedMotion) {
      for (let si = 0; si < steamPuffs.length; si++) {
        steamPuffs[si].material.opacity = 0;
      }
      rippleMat.opacity = 0.16;
    } else {
      for (let si = 0; si < steamPuffs.length; si++) {
        const steamPhase = (Math.sin(elapsed * 1.4 + si * 0.7) + 1) / 2;
        steamPuffs[si].material.opacity = 0.04 + steamPhase * 0.06;
        steamPuffs[si].position.y = si * 0.045 + steamPhase * 0.025;
      }
      waterRippleTex.offset.x = (elapsed * 0.03) % 1;
      waterRippleTex.offset.y = (elapsed * 0.045) % 1;
      rippleMat.opacity = 0.2 + ((Math.sin(elapsed * 1.1) + 1) / 2) * 0.12;
    }

    if (lampPointBase > 0) {
      const lampPulse = reducedMotion ? 1 : 0.93 + ((Math.sin(elapsed * 3.6) + 1) / 2) * 0.07;
      lampPoint.intensity = lampPointBase * lampPulse;
      lampSpot.intensity = lampSpotBase * lampPulse;
      lampHaloMat.opacity =
        lampHaloBase * (reducedMotion ? 1 : 0.88 + ((Math.sin(elapsed * 3.6 + 0.8) + 1) / 2) * 0.12);
    }

    if (now >= windowRainToggleAt) {
      windowRainActive = !windowRainActive;
      windowRainToggleAt = now + 72000 + Math.random() * 96000;
    }
    windowRainBack.visible = windowRainActive;
    windowGlassStreaks.visible = windowRainActive;
    if (windowRainActive) {
      if (privateTeamWindowAmbientMotionEnabled(reducedMotion)) {
        rainTex.offset.y = (elapsed * 0.9) % 1;
      } else {
        rainTex.offset.y = 0.28;
      }
    }

    if (privateTeamWindowAmbientMotionEnabled(reducedMotion)) {
      cloudTex.offset.x = (elapsed * 0.014) % 1;
      if (now - lastSkylineFlickerMs >= 300) {
        lastSkylineFlickerMs = now;
        stepSkylineFlickerLights(flickerLights);
        flickerLights.tex.needsUpdate = true;
      }
      windowSkylineFlicker.visible = true;
      if (!airplaneActive && now >= airplaneNextAt) {
        airplaneActive = true;
        airplaneStartMs = now;
        airplaneGroup.visible = true;
        airplaneGroup.position.set(winLeft + 0.08, winTop - 0.22, backZ - WALL_THICK - 0.19);
      }
      if (airplaneActive) {
        const ap = (now - airplaneStartMs) / WINDOW_AIRPLANE_MS;
        if (ap >= 1) {
          airplaneActive = false;
          airplaneGroup.visible = false;
          airplaneNextAt = now + 40000 + Math.random() * 55000;
        } else {
          airplaneGroup.position.x = winLeft + 0.08 + ap * (winW + 0.28);
        }
      }
    } else {
      cloudTex.offset.x = 0;
      windowSkylineFlicker.visible = false;
      airplaneGroup.visible = false;
      airplaneActive = false;
    }

    if (yawReturnStartMs != null) {
      const progress = privateTeamYawReturnProgress(now - yawReturnStartMs, reducedMotion);
      yawRadians = privateTeamYawAtReturn(yawReturnFrom, progress);
      if (progress >= 1) {
        yawRadians = 0;
        yawReturnStartMs = null;
        yawReturnFrom = 0;
      }
      applyCameraView();
    }

    if (onProgressBoardRect) {
      const boardRect = progressBoardScreenRect();
      if (boardRect) onProgressBoardRect(boardRect);
    }
    bubble.lookAt(camera.position);
    lampHalo.lookAt(camera.position);
    if (atlasNameplate) atlasNameplate.lookAt(camera.position);

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
    setProgressBoard(view) {
      progressDrawer.draw(view);
    },
    setAtlasNameplateText(text) {
      if (nameplateDrawer) nameplateDrawer.draw(text == null ? '' : String(text));
    },
    setSceneInputEnabled(next) {
      sceneInputEnabled = !!next;
    },
    setSceneAnimationPaused(next) {
      animationPaused = !!next;
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
      if (bronzeEnvMap) bronzeEnvMap.dispose();
      scene.environment = null;
      renderer.dispose();
    }
  };
}