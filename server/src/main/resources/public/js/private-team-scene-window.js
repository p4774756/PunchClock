import * as THREE from './vendor/three.module.min.js';

export const PRIVATE_TEAM_WINDOW_TEXTURE_URLS = {
  day: '/assets/private-team/window/taipei-window-day.webp',
  sunset: '/assets/private-team/window/taipei-window-sunset.webp',
  night: '/assets/private-team/window/taipei-window-night.webp'
};

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
    amb: 0.56,
    dir: 0.48,
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
    floorWash: 0.34,
    wallWash: 0.26,
    tubWarm: 0.05,
    candleMul: 0.12
  },
  sunset: {
    amb: 0.46,
    dir: 0.44,
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
    floorWash: 0.62,
    wallWash: 0.44,
    tubWarm: 0.14,
    candleMul: 0.55
  },
  night: {
    amb: 0.35,
    dir: 0.42,
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
export function makeWindowTowerBeaconOverlayTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 256;
  canvas.height = 144;
  const ctx = canvas.getContext('2d');
  const beacons = [
    { x: canvas.width * 0.53, y: canvas.height * 0.21, r: 3.2 },
    { x: canvas.width * 0.53, y: canvas.height * 0.33, r: 2.4 },
    { x: canvas.width * 0.38, y: canvas.height * 0.54, r: 1.9 },
    { x: canvas.width * 0.63, y: canvas.height * 0.51, r: 1.7 }
  ];
  const paint = (elapsedSec) => {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const t = Number(elapsedSec);
    for (let i = 0; i < beacons.length; i++) {
      const b = beacons[i];
      const wave = Number.isFinite(t)
        ? (Math.sin(t * 0.95 + i * 1.7) + 1) / 2
        : 0.85;
      const on = i === 0 ? wave > 0.42 : wave > 0.52;
      if (!on) continue;
      const g = ctx.createRadialGradient(b.x, b.y, 0, b.x, b.y, b.r * 5);
      g.addColorStop(0, 'rgba(255,68,52,0.92)');
      g.addColorStop(0.45, 'rgba(255,92,72,0.42)');
      g.addColorStop(1, 'rgba(0,0,0,0)');
      ctx.fillStyle = g;
      ctx.fillRect(b.x - b.r * 5, b.y - b.r * 5, b.r * 10, b.r * 10);
    }
  };
  paint(0);
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  return { tex, paint, beacons };
}

/** 河岸／橋面車流光點（貼在窗景下半部）。 */
export function makeWindowVistaTrafficOverlayTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 512;
  canvas.height = 120;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, canvas.width, canvas.height);
  const rand = privateTeamSeededRand(157);
  const laneY = canvas.height * 0.62;
  for (let t = 0; t < 22; t++) {
    const tx = rand() * canvas.width;
    const ty = laneY + (rand() - 0.5) * 14;
    const warm = rand() > 0.28;
    ctx.fillStyle = warm
      ? 'rgba(255,78,62,' + (0.48 + rand() * 0.42) + ')'
      : 'rgba(220,235,255,' + (0.32 + rand() * 0.4) + ')';
    ctx.fillRect(tx, ty, 2 + rand() * 3.5, 1.1 + rand() * 1.2);
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
  g.addColorStop(0.25, 'rgba(200,225,255,0.22)');
  g.addColorStop(0.5, 'rgba(165,195,250,0.08)');
  g.addColorStop(0.75, 'rgba(210,230,255,0.18)');
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
  const antenna = { x: canvas.width * 0.68, y: canvas.height * 0.4, on: false };
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
  const r = flicker.rand();
  const n = 2 + Math.floor(r() * 3);
  for (let i = 0; i < n; i++) {
    const L = flicker.lights[Math.floor(r() * flicker.lights.length)];
    if (r() > 0.5) L.on = !L.on;
  }
  flicker.paint();
}
