import * as THREE from './vendor/three.module.min.js';

export const PRIVATE_TEAM_WINDOW_TEXTURE_URLS = {
  day: '/assets/private-team/window/taipei-window-day.webp',
  sunset: '/assets/private-team/window/taipei-window-sunset.webp',
  night: '/assets/private-team/window/taipei-window-night.webp'
};

/** 窗景貼圖懶載：僅在需要時期向 TextureLoader 發請求（供場景與單測）。 */
export function ensureWindowVistaTexture(texLoader, cache, period) {
  if (!texLoader || !cache || !period) return null;
  if (cache[period]) return cache[period];
  const vurl = PRIVATE_TEAM_WINDOW_TEXTURE_URLS[period];
  if (!vurl) return null;
  const vtx = texLoader.load(vurl);
  vtx.colorSpace = THREE.SRGBColorSpace;
  cache[period] = vtx;
  return vtx;
}

const WINDOW_PREVIEW_MODES = ['auto', 'day', 'sunset', 'night'];

/** 依 Asia/Taipei 牆鐘判定窗景時段（供單測；date 可注入）。 */
export function privateTeamWindowPeriodFromTaipeiDate(date) {
  const d = date instanceof Date ? date : new Date(date);
  const parts = new Intl.DateTimeFormat('en-GB', {
    timeZone: 'Asia/Taipei',
    hour: 'numeric',
    minute: 'numeric',
    hour12: false
  }).formatToParts(d);
  let hour = 0;
  let minute = 0;
  for (let i = 0; i < parts.length; i++) {
    if (parts[i].type === 'hour') hour = parseInt(parts[i].value, 10);
    if (parts[i].type === 'minute') minute = parseInt(parts[i].value, 10);
  }
  const mins = hour * 60 + minute;
  if (mins >= 6 * 60 && mins < 16 * 60 + 30) return 'day';
  if (mins >= 16 * 60 + 30 && mins < 19 * 60) return 'sunset';
  return 'night';
}

export function privateTeamWindowCyclePreviewMode(current) {
  const idx = WINDOW_PREVIEW_MODES.indexOf(current);
  const i = idx >= 0 ? idx : 0;
  return WINDOW_PREVIEW_MODES[(i + 1) % WINDOW_PREVIEW_MODES.length];
}

export function privateTeamWindowPreviewToastLabel(mode) {
  if (mode === 'day') return '窗景：白天';
  if (mode === 'sunset') return '窗景：黃昏';
  if (mode === 'night') return '窗景：夜晚';
  return '窗景：自動';
}

const SCENE_LIGHTING_BY_PERIOD = {
  day: {
    amb: 0.68,
    hemi: 0.42,
    dir: 0.58,
    point: 0.06,
    spot: 0.28,
    spotDistance: 7.8,
    spotPenumbra: 0.72,
    spotDecay: 1.25,
    pointDistance: 4.6,
    bulbOpacity: 0.1,
    haloOpacity: 0.06,
    shadeEmissive: 0.06,
    ambColor: 0xe8f0ff,
    dirColor: 0xc8d8f8,
    background: 0xc8d4e8,
    floorWash: 0.44,
    wallWash: 0.34,
    tubWarm: 0.05,
    candleMul: 0.12
  },
  sunset: {
    amb: 0.54,
    hemi: 0.36,
    dir: 0.52,
    point: 0.42,
    spot: 1.85,
    spotDistance: 7.8,
    spotPenumbra: 0.72,
    spotDecay: 1.25,
    pointDistance: 4.6,
    bulbOpacity: 0.38,
    haloOpacity: 0.22,
    shadeEmissive: 0.28,
    ambColor: 0xffe8d0,
    dirColor: 0xffc888,
    background: 0x1a1428,
    floorWash: 0.7,
    wallWash: 0.52,
    tubWarm: 0.14,
    candleMul: 0.55
  },
  night: {
    amb: 0.42,
    hemi: 0.28,
    dir: 0.48,
    point: 1.05,
    spot: 5.4,
    spotDistance: 7.8,
    spotPenumbra: 0.72,
    spotDecay: 1.25,
    pointDistance: 4.6,
    bulbOpacity: 0.84,
    haloOpacity: 0.5,
    shadeEmissive: 0.58,
    ambColor: 0xc8d4ff,
    dirColor: 0x8898f8,
    background: 0x050a14,
    floorWash: 0.9,
    wallWash: 0.52,
    tubWarm: 0.22,
    candleMul: 1
  }
};

/** 房間燈光與窗外洒光參數；period 為 day／sunset／night。 */
export function privateTeamSceneLighting(period) {
  const key = period === 'day' || period === 'sunset' || period === 'night' ? period : 'night';
  return Object.assign({}, SCENE_LIGHTING_BY_PERIOD[key]);
}

/** 蠟燭點光閃爍倍率（供單測）；lowMotion 為窄螢或粗指標時較緩。 */
export function privateTeamCandleFlickerMultiplier(elapsedSec, reducedMotion, lowMotion) {
  if (reducedMotion) return 0.7;
  const t = Number(elapsedSec);
  if (!Number.isFinite(t)) return 0.85;
  if (lowMotion) {
    return 0.8 + ((Math.sin(t * 1.6) + 1) / 2) * 0.1;
  }
  return (
    0.76 +
    ((Math.sin(t * 4.8) + 1) / 2) * 0.16 +
    ((Math.sin(t * 10.5 + 0.7) + 1) / 2) * 0.08
  );
}

export function privateTeamSeededRand(seed) {
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

/** 窗外天際線各層視差係數（愈近愈大）。 */
export const PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX = {
  sky: 0.06,
  far: 0.1,
  mid: 0.22,
  near: 0.45,
  clouds: 0.14,
  flicker: 0.3
};

/** 鏡頭微動時依層別計算平面位移；reducedMotion 時歸零。 */
export function privateTeamWindowSkylineParallaxOffset(yawRad, pitchRad, parallaxScale, reducedMotion) {
  if (reducedMotion) return { x: 0, y: 0 };
  const y = Number(yawRad);
  const p = Number(pitchRad);
  const s = Number(parallaxScale);
  if (!Number.isFinite(y) || !Number.isFinite(p) || !Number.isFinite(s)) return { x: 0, y: 0 };
  return { x: y * s * 0.38, y: p * s * 0.2 };
}

/** 夜間窗景貼圖上 101 塔尖航空燈（UV 0–1，y 愈小愈靠天際）。 */
export const PRIVATE_TEAM_WINDOW_TOWER_BEACON_ANCHORS = [
  { x: 0.542, y: 0.448, r: 1.15, primary: true },
  { x: 0.518, y: 0.512, r: 0.95 },
  { x: 0.612, y: 0.538, r: 0.85 },
  { x: 0.388, y: 0.558, r: 0.8 }
];

/** 白點飛機燈水平路徑所在天空帶（貼圖 UV，須在天際線之上）。 */
export const PRIVATE_TEAM_WINDOW_AIRPLANE_SKY_Y = 0.155;

/** 天際線大致上緣（UV）；航空燈 y 應不低於此，飛機 y 應明顯低於此。 */
export const PRIVATE_TEAM_WINDOW_SKYLINE_TOP_Y = 0.38;

/** 樓窗閃爍燈撒點下緣（UV，須在天際線之下的大樓帶內）。 */
export const PRIVATE_TEAM_WINDOW_FLICKER_Y_MIN = PRIVATE_TEAM_WINDOW_SKYLINE_TOP_Y + 0.04;

/** 樓窗閃爍燈撒點上緣（UV，須低於高架／河面前景帶）。 */
export const PRIVATE_TEAM_WINDOW_FLICKER_Y_MAX = 0.66;

/** 樓窗閃爍燈撒點高度帶（UV 跨度，錨點取樣時夾於 MIN–MAX）。 */
export const PRIVATE_TEAM_WINDOW_FLICKER_Y_SPAN =
  PRIVATE_TEAM_WINDOW_FLICKER_Y_MAX - PRIVATE_TEAM_WINDOW_FLICKER_Y_MIN;

/**
 * 夜間窗景貼圖上已對準大樓窗格的樓燈 UV（依 taipei-window-night／day 量測，非全幅隨機）。
 */
export const PRIVATE_TEAM_WINDOW_FLICKER_ANCHORS = [
  { x: 0.647, y: 0.496 },
  { x: 0.769, y: 0.496 },
  { x: 0.459, y: 0.504 },
  { x: 0.825, y: 0.512 },
  { x: 0.895, y: 0.521 },
  { x: 0.637, y: 0.529 },
  { x: 0.127, y: 0.537 },
  { x: 0.094, y: 0.546 },
  { x: 0.234, y: 0.546 },
  { x: 0.272, y: 0.546 },
  { x: 0.436, y: 0.546 },
  { x: 0.717, y: 0.546 },
  { x: 0.389, y: 0.554 },
  { x: 0.755, y: 0.554 },
  { x: 0.319, y: 0.562 },
  { x: 0.492, y: 0.562 },
  { x: 0.797, y: 0.579 },
  { x: 0.544, y: 0.588 },
  { x: 0.684, y: 0.588 },
  { x: 0.727, y: 0.588 },
  { x: 0.173, y: 0.596 },
  { x: 0.272, y: 0.596 },
  { x: 0.408, y: 0.596 },
  { x: 0.511, y: 0.596 },
  { x: 0.591, y: 0.596 },
  { x: 0.844, y: 0.596 },
  { x: 0.459, y: 0.604 },
  { x: 0.113, y: 0.613 },
  { x: 0.244, y: 0.613 },
  { x: 0.300, y: 0.621 },
  { x: 0.558, y: 0.621 },
  { x: 0.591, y: 0.629 },
  { x: 0.647, y: 0.629 },
  { x: 0.347, y: 0.637 },
  { x: 0.684, y: 0.637 },
  { x: 0.863, y: 0.637 },
  { x: 0.417, y: 0.646 },
  { x: 0.450, y: 0.646 },
  { x: 0.487, y: 0.646 },
  { x: 0.792, y: 0.646 },
  { x: 0.103, y: 0.654 },
  { x: 0.173, y: 0.654 }
];

/** 白天窗景雲層漂移速度（貼圖 repeat U／秒）。 */
export const PRIVATE_TEAM_WINDOW_CLOUD_DRIFT_U_PER_SEC = 0.024;

/** 橋面車流光點數量與車道 UV、水平捲動速度。 */
export const PRIVATE_TEAM_WINDOW_TRAFFIC_LIGHT_COUNT = 12;
export const PRIVATE_TEAM_WINDOW_TRAFFIC_LANE_Y_NORM = 0.78;
export const PRIVATE_TEAM_WINDOW_TRAFFIC_LANE_HALF_PX = 5;
export const PRIVATE_TEAM_WINDOW_TRAFFIC_DRIFT_U_PER_SEC = 0.018;

/** 河面波光各時段基礎透明度（疊乘閃爍倍率，無水平位移）。 */
export const PRIVATE_TEAM_WINDOW_RIVER_OPACITY = {
  day: 0.14,
  sunset: 0.18,
  night: 0.22
};

/** 河面波光明滅倍率（reducedMotion 時固定 1）。 */
export function privateTeamWindowRiverShimmerMul(elapsedSec, reducedMotion) {
  if (reducedMotion) return 1;
  const t = Number(elapsedSec);
  if (!Number.isFinite(t)) return 1;
  return (
    0.8 +
    ((Math.sin(t * 1.35) + 1) / 2) * 0.16 +
    ((Math.sin(t * 3.2 + 0.5) + 1) / 2) * 0.07
  );
}

/** 101 航空障礙燈緩慢明滅係數（0.35–1）。 */
export function privateTeamWindowTowerBeaconOpacity(elapsedSec, reducedMotion) {
  if (reducedMotion) return 0.85;
  const t = Number(elapsedSec);
  if (!Number.isFinite(t)) return 0.85;
  const wave = (Math.sin(t * 1.15) + 1) / 2;
  return 0.38 + wave * 0.58;
}

export function privateTeamWindowAirplaneBeaconOpacity(elapsedSec, reducedMotion) {
  return privateTeamWindowTowerBeaconOpacity(elapsedSec, reducedMotion);
}

/** 窗景上 101 與近樓航空燈透明疊層。 */
export function privateTeamWindowAirplaneXNorm(elapsedSec) {
  const t = Number(elapsedSec);
  if (!Number.isFinite(t)) return 0.08;
  const cycleSec = 52;
  return ((t / cycleSec) % 1 + 1) % 1;
}

export function makeWindowTowerBeaconOverlayTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 144;
  const ctx = canvas.getContext('2d');
  const beacons = PRIVATE_TEAM_WINDOW_TOWER_BEACON_ANCHORS.map((a) => ({
    x: canvas.width * a.x,
    y: canvas.height * a.y,
    r: a.r,
    primary: !!a.primary
  }));
  const paint = (elapsedSec) => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const t = Number(elapsedSec);
    for (let i = 0; i < beacons.length; i++) {
      const b = beacons[i];
      const wave = Number.isFinite(t)
        ? (Math.sin(t * 0.95 + i * 1.7) + 1) / 2
        : 0.85;
      const on = b.primary ? wave > 0.42 : wave > 0.55;
      if (!on) continue;
      const glowR = b.r * 2.2;
      const g = ctx.createRadialGradient(b.x, b.y, 0, b.x, b.y, glowR);
      g.addColorStop(0, 'rgba(255,72,54,0.95)');
      g.addColorStop(0.55, 'rgba(255,88,68,0.35)');
      g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g;
      ctx.fillRect(b.x - glowR, b.y - glowR, glowR * 2, glowR * 2);
      ctx.fillStyle = 'rgba(255,92,72,0.98)';
      ctx.fillRect(b.x - b.r * 0.55, b.y - b.r * 0.55, b.r * 1.1, b.r * 1.1);
    }
  };
  paint(0);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { tex, paint, beacons };
}

/** 天際線上方緩慢橫移的飛機導航白燈（疊在窗景上半部）。 */
export function makeWindowVistaAirplaneOverlayTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 144;
  const ctx = canvas.getContext('2d');
  const paint = (elapsedSec) => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const xNorm = privateTeamWindowAirplaneXNorm(elapsedSec);
    const x = xNorm * canvas.width;
    const y = canvas.height * PRIVATE_TEAM_WINDOW_AIRPLANE_SKY_Y;
    const flash = Number.isFinite(Number(elapsedSec))
      ? 0.55 + ((Math.sin(Number(elapsedSec) * 2.4) + 1) / 2) * 0.45
      : 0.88;
    const g = ctx.createRadialGradient(x, y, 0, x, y, 5);
    g.addColorStop(0, 'rgba(255,255,255,' + (0.92 * flash) + ')');
    g.addColorStop(0.45, 'rgba(230,240,255,' + (0.35 * flash) + ')');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(x - 5, y - 5, 10, 10);
    ctx.fillStyle = 'rgba(255,255,255,' + (0.98 * flash) + ')';
    ctx.fillRect(x - 1.1, y - 1.1, 2.2, 2.2);
  };
  paint(0);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { tex, paint };
}

/** 白天／黃昏窗景雲層半透明疊層（repeat 後可水平漂移）。 */
export function makeDayVistaCloudLayerTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 180;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(61);
  const edgeMargin = canvas.width * 0.1;
  for (let i = 0; i < 6; i++) {
    const cx = edgeMargin + rand() * (canvas.width - edgeMargin * 2);
    const cy = 22 + rand() * 72;
    const rwMax = Math.min(
      95 + rand() * 95,
      (cx - edgeMargin) * 0.92,
      (canvas.width - edgeMargin - cx) * 0.92
    );
    const rw = Math.max(48, rwMax);
    const rh = 22 + rand() * 30;
    const g = ctx.createRadialGradient(cx, cy, 4, cx, cy, rw);
    g.addColorStop(0, 'rgba(255,255,255,0.36)');
    g.addColorStop(0.5, 'rgba(248,252,255,0.14)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.ellipse(cx, cy, rw, rh, 0, 0, Math.PI * 2);
    ctx.fill();
  }
  const edgeFade = ctx.createLinearGradient(0, 0, canvas.width, 0);
  edgeFade.addColorStop(0, 'rgba(0,0,0,0)');
  edgeFade.addColorStop(0.07, 'rgba(255,255,255,1)');
  edgeFade.addColorStop(0.93, 'rgba(255,255,255,1)');
  edgeFade.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.globalCompositeOperation = 'destination-in';
  ctx.fillStyle = edgeFade;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.globalCompositeOperation = 'source-over';
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

/** 河岸／橋面車流光點（貼在窗景下半部）。 */
export function makeWindowVistaTrafficOverlayTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 120;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(157);
  const laneY = canvas.height * PRIVATE_TEAM_WINDOW_TRAFFIC_LANE_Y_NORM;
  const half = PRIVATE_TEAM_WINDOW_TRAFFIC_LANE_HALF_PX;
  for (let t = 0; t < PRIVATE_TEAM_WINDOW_TRAFFIC_LIGHT_COUNT; t++) {
    const tx = rand() * canvas.width;
    const ty = laneY + (rand() - 0.5) * half * 2;
    ctx.fillStyle = 'rgba(255,78,62,' + (0.28 + rand() * 0.32) + ')';
    ctx.fillRect(tx, ty, 1.8 + rand() * 2.4, 1 + rand() * 0.9);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

/** 基隆河水面極淡波光。 */
export function makeWindowRiverShimmerTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, canvas.width, 0);
  g.addColorStop(0, 'rgba(180,210,255,0)');
  g.addColorStop(0.25, 'rgba(200,225,255,0.12)');
  g.addColorStop(0.5, 'rgba(165,195,250,0.05)');
  g.addColorStop(0.75, 'rgba(210,230,255,0.1)');
  g.addColorStop(1, 'rgba(180,210,255,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, canvas.height * 0.42, canvas.width, canvas.height * 0.22);
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeNightWindowSkyTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 640;
  canvas.height = 400;
  const ctx = canvas.getContext('2d');
  const grad = ctx.createLinearGradient(0, 0, 0, canvas.height);
  grad.addColorStop(0, '#080e1c');
  grad.addColorStop(0.45, '#101a30');
  grad.addColorStop(0.72, '#142438');
  grad.addColorStop(1, '#182c44');
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
  const moonG = ctx.createRadialGradient(moonX, moonY, 0, moonX, moonY, 38);
  moonG.addColorStop(0, 'rgba(210,218,255,0.55)');
  moonG.addColorStop(0.35, 'rgba(165,175,245,0.22)');
  moonG.addColorStop(0.55, 'rgba(120,135,210,0.09)');
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

export function makeNightSkylineLayerTexture(layer) {
  const canvas = document.createElement('canvas');
  canvas.width = 640;
  canvas.height = 320;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(layer === 'far' ? 11 : layer === 'mid' ? 23 : 37);
  const baseY = canvas.height * (layer === 'far' ? 0.78 : layer === 'mid' ? 0.62 : 0.5);
  const maxH = canvas.height * (layer === 'far' ? 0.2 : layer === 'mid' ? 0.36 : 0.46);
  const fog = layer === 'far' ? 0.38 : layer === 'mid' ? 0.68 : 1;
  let x = -12;
  while (x < canvas.width + 8) {
    const bw = (layer === 'near' ? 34 : 26) + rand() * (layer === 'near' ? 48 : 32);
    const bh = maxH * (0.32 + rand() * 0.68);
    const tone = layer === 'far' ? 14 + rand() * 14 : layer === 'mid' ? 20 + rand() * 22 : 24 + rand() * 30;
    const rCh = layer === 'far' ? tone + 6 : tone + 14;
    const gCh = layer === 'far' ? tone + 14 : tone + 22;
    const bCh = layer === 'far' ? tone + 44 : layer === 'mid' ? tone + 36 : tone + 32;
    ctx.fillStyle = 'rgba(' + rCh + ',' + gCh + ',' + bCh + ',' + fog + ')';
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
    const mist = ctx.createLinearGradient(0, canvas.height * 0.42, 0, canvas.height);
    mist.addColorStop(0, 'rgba(0,0,0,0)');
    mist.addColorStop(0.55, 'rgba(14,22,40,0.14)');
    mist.addColorStop(0.85, 'rgba(18,28,48,0.22)');
    mist.addColorStop(1, 'rgba(16,26,44,0.28)');
    ctx.fillStyle = mist;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }
  if (layer === 'mid') {
    const midMist = ctx.createLinearGradient(0, canvas.height * 0.62, 0, canvas.height);
    midMist.addColorStop(0, 'rgba(0,0,0,0)');
    midMist.addColorStop(1, 'rgba(18,28,48,0.12)');
    ctx.fillStyle = midMist;
    ctx.fillRect(0, 0, canvas.width, canvas.height);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeNightCloudLayerTexture() {
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

export function makeWindowRainTexture(lineCount) {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 256;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(97);
  const n = Math.max(16, Math.min(120, lineCount | 0));
  const rainTop = canvas.height * 0.46;
  for (let i = 0; i < n; i++) {
    const x = rand() * canvas.width;
    const y0 = rand() * rainTop * 0.85;
    const len = Math.min(10 + rand() * 18, rainTop - y0 - 2);
    if (len < 4) continue;
    const w = 0.5 + rand() * 0.65;
    ctx.strokeStyle = 'rgba(175,195,230,' + (0.14 + rand() * 0.2) + ')';
    ctx.lineWidth = w;
    ctx.beginPath();
    ctx.moveTo(x, y0);
    ctx.lineTo(x + 1.2, y0 + len);
    ctx.stroke();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeWindowGlassStreakTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 400;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(113);
  for (let d = 0; d < 14; d++) {
    const dx = rand() * canvas.width;
    const dy = rand() * canvas.height;
    const r = 2 + rand() * 5;
    const g = ctx.createRadialGradient(dx, dy, 0, dx, dy, r);
    g.addColorStop(0, 'rgba(210,230,250,0.28)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.fillRect(dx - r, dy - r, r * 2, r * 2);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeSkylineFlickerLightsTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 144;
  const ctx = canvas.getContext('2d');
  const rand = privateTeamSeededRand(89);
  const yMin = PRIVATE_TEAM_WINDOW_FLICKER_Y_MIN;
  const yMax = PRIVATE_TEAM_WINDOW_FLICKER_Y_MAX;
  const jitterU = 0.006;
  const jitterV = 0.005;
  const lights = [];
  const anchors = PRIVATE_TEAM_WINDOW_FLICKER_ANCHORS;
  for (let i = 0; i < anchors.length; i++) {
    const a = anchors[i];
    let xNorm = a.x + (rand() - 0.5) * 2 * jitterU;
    let yNorm = a.y + (rand() - 0.5) * 2 * jitterV;
    xNorm = Math.min(0.98, Math.max(0.02, xNorm));
    yNorm = Math.min(yMax, Math.max(yMin, yNorm));
    lights.push({
      x: canvas.width * xNorm,
      y: canvas.height * yNorm,
      w: 1.5 + rand() * 2.4,
      h: 2 + rand() * 3,
      on: rand() > 0.32,
      warm: rand() > 0.42
    });
  }
  const primaryAnchor = PRIVATE_TEAM_WINDOW_TOWER_BEACON_ANCHORS.find((a) => a.primary);
  const antenna = {
    x: canvas.width * (primaryAnchor ? primaryAnchor.x : 0.542),
    y: canvas.height * (primaryAnchor ? primaryAnchor.y : 0.448),
    on: false
  };
  const paint = () => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    for (let li = 0; li < lights.length; li++) {
      const L = lights[li];
      if (!L.on) continue;
      ctx.fillStyle = L.warm ? 'rgba(255,210,120,0.78)' : 'rgba(180,215,240,0.68)';
      ctx.fillRect(L.x, L.y, L.w, L.h);
    }
    if (antenna.on) {
      ctx.fillStyle = 'rgba(255,232,160,0.88)';
      ctx.fillRect(antenna.x, antenna.y, 3, 3);
      const ag = ctx.createRadialGradient(antenna.x, antenna.y, 0, antenna.x, antenna.y, 10);
      ag.addColorStop(0, 'rgba(255,220,140,0.55)');
      ag.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = ag;
      ctx.fillRect(antenna.x - 10, antenna.y - 10, 20, 20);
    }
  };
  paint();
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { tex, lights, antenna, paint, rand: privateTeamSeededRand(91) };
}

export function makeWindowStreetTrafficTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 160;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(131);
  const groundY = canvas.height * 0.72;
  ctx.fillStyle = 'rgba(28,36,52,0.55)';
  ctx.fillRect(0, groundY, canvas.width, canvas.height - groundY);
  for (let lx = 36; lx < canvas.width; lx += 88 + rand() * 40) {
    const poleH = 48 + rand() * 22;
    ctx.strokeStyle = 'rgba(60,70,88,0.75)';
    ctx.lineWidth = 2;
    ctx.beginPath();
    ctx.moveTo(lx, groundY);
    ctx.lineTo(lx, groundY - poleH);
    ctx.stroke();
    const lampG = ctx.createRadialGradient(lx, groundY - poleH, 0, lx, groundY - poleH, 18);
    lampG.addColorStop(0, 'rgba(255,228,150,0.85)');
    lampG.addColorStop(0.45, 'rgba(255,210,120,0.35)');
    lampG.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = lampG;
    ctx.fillRect(lx - 22, groundY - poleH - 22, 44, 44);
  }
  for (let t = 0; t < 28; t++) {
    const tx = rand() * canvas.width;
    const ty = groundY + 4 + rand() * 10;
    const warm = rand() > 0.22;
    ctx.fillStyle = warm
      ? 'rgba(255,72,58,' + (0.55 + rand() * 0.4) + ')'
      : 'rgba(230,240,255,' + (0.35 + rand() * 0.45) + ')';
    ctx.fillRect(tx, ty, 2.5 + rand() * 4, 1.2 + rand() * 1.5);
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeWindowMoonlightFloorTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 384;
  canvas.height = 240;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const cols = 3;
  const rows = 2;
  const padX = 14;
  const padY = 10;
  const muntin = 11;
  const innerW = canvas.width - padX * 2;
  const innerH = canvas.height - padY * 2;
  const paneW = (innerW - muntin * (cols - 1)) / cols;
  const paneH = (innerH - muntin * (rows - 1)) / rows;
  const cornerR = 7;

  function strokeRoundRect(x, y, w, h, r) {
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
  }

  function fillRoundRect(x, y, w, h, r) {
    strokeRoundRect(x, y, w, h, r);
    ctx.fill();
  }

  ctx.save();
  ctx.transform(1, 0, 0.2, 1, 18, 4);
  for (let row = 0; row < rows; row++) {
    for (let col = 0; col < cols; col++) {
      const x = padX + col * (paneW + muntin);
      const y = padY + row * (paneH + muntin);
      const fade = 0.35 + ((cols - 1 - col) / (cols - 1)) * 0.65;
      const lg = ctx.createLinearGradient(x, y, x + paneW, y + paneH);
      lg.addColorStop(0, 'rgba(185,202,255,' + (0.42 * fade) + ')');
      lg.addColorStop(0.4, 'rgba(155,180,252,' + (0.78 * fade) + ')');
      lg.addColorStop(0.75, 'rgba(130,160,245,' + (0.62 * fade) + ')');
      lg.addColorStop(1, 'rgba(95,125,225,' + (0.28 * fade) + ')');
      ctx.fillStyle = lg;
      fillRoundRect(x, y, paneW, paneH, cornerR);
      const cx = x + paneW * 0.38;
      const cy = y + paneH * 0.36;
      const edge = Math.max(paneW, paneH) * 0.58;
      const pg = ctx.createRadialGradient(cx, cy, 1, cx, cy, edge);
      pg.addColorStop(0, 'rgba(225,235,255,' + (0.55 * fade) + ')');
      pg.addColorStop(0.55, 'rgba(165,188,252,' + (0.32 * fade) + ')');
      pg.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = pg;
      fillRoundRect(x + 2, y + 2, paneW - 4, paneH - 4, Math.max(2, cornerR - 2));
    }
  }
  ctx.strokeStyle = 'rgba(16,22,52,0.72)';
  ctx.lineWidth = muntin;
  ctx.lineCap = 'square';
  for (let row = 1; row < rows; row++) {
    const ly = padY + row * paneH + (row - 0.5) * muntin;
    ctx.beginPath();
    ctx.moveTo(padX - 3, ly);
    ctx.lineTo(padX + cols * paneW + (cols - 1) * muntin + 3, ly);
    ctx.stroke();
  }
  ctx.restore();
  ctx.strokeStyle = 'rgba(28,36,68,0.48)';
  ctx.lineWidth = 3;
  ctx.strokeRect(padX + 1, padY + 1, innerW - 2, innerH - 2);
  const castFade = ctx.createLinearGradient(0, canvas.height * 0.48, 0, canvas.height);
  castFade.addColorStop(0, 'rgba(0,0,0,0)');
  castFade.addColorStop(0.72, 'rgba(0,0,0,0.35)');
  castFade.addColorStop(1, 'rgba(0,0,0,0.92)');
  ctx.globalCompositeOperation = 'destination-out';
  ctx.fillStyle = castFade;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  ctx.globalCompositeOperation = 'source-over';
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function makeWindowMoonlightWallTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 192;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const cx = canvas.width * 0.5;
  const g = ctx.createRadialGradient(cx, 18, 6, cx, 48, 200);
  g.addColorStop(0, 'rgba(210,224,255,0.92)');
  g.addColorStop(0.28, 'rgba(160,185,252,0.62)');
  g.addColorStop(0.62, 'rgba(120,150,240,0.28)');
  g.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, canvas.width, canvas.height);
  const baseWash = ctx.createLinearGradient(0, 0, canvas.width, 0);
  baseWash.addColorStop(0, 'rgba(88,108,168,0.2)');
  baseWash.addColorStop(0.35, 'rgba(72,92,152,0.14)');
  baseWash.addColorStop(0.68, 'rgba(58,78,138,0.1)');
  baseWash.addColorStop(1, 'rgba(48,68,128,0.08)');
  ctx.fillStyle = baseWash;
  ctx.fillRect(0, canvas.height * 0.22, canvas.width, canvas.height * 0.62);
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.ClampToEdgeWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

export function stepSkylineFlickerLights(flicker) {
  const r = flicker.rand;
  if (typeof r !== 'function') return;
  const n = 3 + Math.floor(r() * 7);
  for (let i = 0; i < n; i++) {
    const L = flicker.lights[Math.floor(r() * flicker.lights.length)];
    if (r() > 0.38) L.on = !L.on;
  }
  flicker.paint();
}
