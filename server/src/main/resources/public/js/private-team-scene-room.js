import * as THREE from './vendor/three.module.min.js';
import { POCKET_LCD_H, POCKET_LCD_W, drawPocketMaryPreviewLcd } from './private-team-pocket-lcd.js';
import { privateTeamSeededRand } from './private-team-scene-window.js';

/** 立繪在場景中的高度（公尺比例）；寬度依紋理長寬比計算 */
const SECRETARY_SPRITE_HEIGHT = 1.42;

export const FLOOR_SIZE = 3.6;
export const FLOOR_HALF = FLOOR_SIZE / 2;
export const WALL_THICK = 0.14;
const TEX_BASE = '/assets/private-team/textures/';

export const COL = {
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

export const PRIVATE_TEAM_LEFT_POSTER_IDX_KEY = 'punchclock.office.leftPosterIdx';

export const LEFT_WALL_POSTER_URLS = [
  '/assets/private-team/posters/poster-1-beach-sunset.webp',
  '/assets/private-team/posters/poster-2-infinity-pool.webp',
  '/assets/private-team/posters/poster-3-resort-terrace.webp',
  '/assets/private-team/posters/poster-4-retro-neon.webp'
];

/** 將 localStorage 字串正規為 0–3；缺鍵或非法值回 0（第 1 張）。 */
export function parseLeftPosterIndex(raw) {
  if (raw == null || raw === '') return 0;
  const n = Number.parseInt(String(raw), 10);
  if (!Number.isFinite(n) || n < 0 || n > 3) return 0;
  return n;
}

export function nextLeftPosterIndex(current) {
  const cur = parseLeftPosterIndex(current);
  return (cur + 1) % LEFT_WALL_POSTER_URLS.length;
}

export function readLeftPosterIndexFromStorage(getItem) {
  let raw = null;
  try {
    if (typeof getItem === 'function') {
      raw = getItem(PRIVATE_TEAM_LEFT_POSTER_IDX_KEY);
    }
  } catch (_e) {
    return 0;
  }
  return parseLeftPosterIndex(raw);
}

export function formatLeftPosterIndexForStorage(index) {
  return String(parseLeftPosterIndex(index));
}

export function box(w, h, d, color, basic) {
  const g = new THREE.BoxGeometry(w, h, d);
  const m = basic
    ? new THREE.MeshBasicMaterial({ color })
    : new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

export function cyl(rt, rb, h, seg, color) {
  const g = new THREE.CylinderGeometry(rt, rb, h, seg);
  const m = new THREE.MeshLambertMaterial({ color });
  return new THREE.Mesh(g, m);
}

export function roundedRectShape(hw, hh, r) {
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

export function extrudedRoundedPlate(hw, hh, depth, radius, mat) {
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

export function extrudedRoundedRing(hw, hh, inHw, inHh, depth, radius, mat) {
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

/** 大理石地板開洞（洞心 world XZ = holeCx, holeCz）。 */
export function extrudedRoundedPlateWithRectHole(
  hw,
  hh,
  holeCx,
  holeCz,
  holeHw,
  holeHh,
  depth,
  radius,
  mat
) {
  const shape = roundedRectShape(hw, hh, radius);
  const hole = roundedRectShape(holeHw, holeHh, Math.max(0.02, radius * 0.5));
  hole.translate(holeCx, holeCz);
  shape.holes.push(hole);
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

export function loadRepeatTex(loader, file, rx, ry) {
  const tex = loader.load(TEX_BASE + file);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(rx, ry);
  return tex;
}

export function makeStoneTileTexture(repeatX, repeatY) {
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

export function makeWaterRippleTexture() {
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


export function makeBathtubCausticsTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = 128;
  canvas.height = 128;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, 128, 128);
  const rand = privateTeamSeededRand(617);
  for (let i = 0; i < 18; i++) {
    const cx = 16 + rand() * 96;
    const cy = 16 + rand() * 96;
    const rw = 6 + rand() * 14;
    const rh = 5 + rand() * 12;
    const g = ctx.createRadialGradient(cx, cy, 1, cx, cy, Math.max(rw, rh));
    g.addColorStop(0, 'rgba(210,238,255,0.68)');
    g.addColorStop(0.5, 'rgba(148,198,248,0.3)');
    g.addColorStop(1, 'rgba(0,0,0,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.ellipse(cx, cy, rw, rh, rand() * Math.PI, 0, Math.PI * 2);
    ctx.fill();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.repeat.set(1.4, 1.1);
  return tex;
}

export function addRomanceCandle(parent, x, y, z, name) {
  const group = new THREE.Group();
  group.name = name;
  group.position.set(x, y, z);
  const wax = cyl(0.011, 0.013, 0.055, 8, 0xe6ddd4);
  wax.position.y = 0.028;
  group.add(wax);
  const flameMat = new THREE.MeshBasicMaterial({
    color: 0xffcc66,
    transparent: true,
    opacity: 0.9,
    depthWrite: false
  });
  const flame = new THREE.Mesh(new THREE.SphereGeometry(0.013, 6, 5), flameMat);
  flame.position.y = 0.06;
  flame.scale.set(0.65, 1.15, 0.65);
  flame.name = 'candleFlame';
  group.add(flame);
  const glow = new THREE.PointLight(0xffb848, 0.42, 2.55, 2);
  glow.position.y = 0.058;
  group.add(glow);
  parent.add(group);
  return { flameMat, glow, baseIntensity: 0.42 };
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

export function stripUnderwaterTint(mat) {
  if (!mat) return;
  mat.onBeforeCompile = null;
  mat.customProgramCacheKey = () => 'meshbasic-vanilla';
  mat.needsUpdate = true;
}

export function applyUnderwaterTint(mat, waterYUniform) {
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

export function addBathPillow(parent) {
  if (parent.getObjectByName('bathPillow')) return;
  const group = new THREE.Group();
  group.name = 'bathPillow';
  group.position.set(0.02, 0.44, -0.52);
  function towelMat(color) {
    return new THREE.MeshLambertMaterial({ color });
  }
  const cushion = new THREE.Mesh(new THREE.SphereGeometry(0.22, 14, 12), towelMat(COL.towel));
  cushion.name = 'bathPillowCushion';
  cushion.scale.set(1.12, 0.38, 0.86);
  cushion.position.set(0, 0.06, 0.02);
  cushion.rotation.x = 0.32;
  const cushionDrape = new THREE.Mesh(new THREE.BoxGeometry(0.36, 0.08, 0.2), towelMat(COL.towel));
  cushionDrape.name = 'bathPillowCushionDrape';
  cushionDrape.position.set(0.02, -0.02, 0.12);
  cushionDrape.rotation.x = -0.55;
  const roll = new THREE.Mesh(
    new THREE.CylinderGeometry(0.048, 0.062, 0.24, 14),
    towelMat(COL.towelDk)
  );
  roll.name = 'bathPillowRoll';
  roll.rotation.z = Math.PI / 2;
  roll.position.set(0.08, 0.16, 0.06);
  const foldA = new THREE.Mesh(new THREE.BoxGeometry(0.13, 0.06, 0.11), towelMat(COL.towelDk));
  foldA.name = 'bathPillowFold';
  foldA.position.set(-0.16, 0.05, 0.1);
  foldA.rotation.set(0.42, -0.22, -0.28);
  const foldB = new THREE.Mesh(new THREE.BoxGeometry(0.1, 0.05, 0.14), towelMat(COL.towel));
  foldB.name = 'bathPillowFoldB';
  foldB.position.set(0.18, 0.03, 0.08);
  foldB.rotation.set(0.18, 0.35, 0.22);
  group.add(cushion, cushionDrape, roll, foldA, foldB);
  parent.add(group);
}

export const MONITOR_SCREEN_PULSE_STEPS = 28;

export function quantizeMonitorScreenPulse(pulse) {
  const p = Number(pulse);
  if (!Number.isFinite(p)) return 0;
  return Math.round(p * MONITOR_SCREEN_PULSE_STEPS) / MONITOR_SCREEN_PULSE_STEPS;
}

export function makeScreenPainter() {
  const canvas = document.createElement('canvas');
  canvas.width = 160;
  canvas.height = 110;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  const mat = new THREE.MeshBasicMaterial({ map: tex });
  const mesh = new THREE.Mesh(new THREE.PlaneGeometry(0.56, 0.38), mat);
  mesh.position.z = 0.04;
  let lastDrawnPulse = null;
  function draw(pulse) {
    const q = quantizeMonitorScreenPulse(pulse);
    if (q === lastDrawnPulse) return;
    lastDrawnPulse = q;
    ctx.fillStyle = '#121a2c';
    ctx.fillRect(0, 0, 160, 110);
    const glow = '#b9e2ff';
    ctx.fillStyle = 'rgba(94, 186, 255, ' + (0.45 + q * 0.45) + ')';
    ctx.fillRect(12, 14, 136, 82);
    ctx.fillStyle = glow;
    const slide = (q * MONITOR_SCREEN_PULSE_STEPS) | 0;
    ctx.fillRect(22, 26, 48 + slide, 10);
    ctx.fillRect(22, 46, 90, 8);
    ctx.fillRect(22, 64, 62 + (18 - slide / 2), 8);
    tex.needsUpdate = true;
  }
  return { mesh, draw };
}

export function makeBubbleDrawer() {
  const canvas = document.createElement('canvas');
  canvas.width = 320;
  canvas.height = 112;
  const ctx = canvas.getContext('2d');
  const tex = new THREE.CanvasTexture(canvas);
  tex.colorSpace = THREE.SRGBColorSpace;
  function draw(text) {
    ctx.clearRect(0, 0, canvas.width, canvas.height);
    const x = 20;
    const y = 16;
    const w = 272;
    const h = 56;
    const r = h / 2;
    ctx.beginPath();
    ctx.moveTo(x + r, y);
    ctx.lineTo(x + w - r, y);
    ctx.arc(x + w - r, y + r, r, -Math.PI / 2, Math.PI / 2);
    ctx.lineTo(x + 168, y + h);
    ctx.lineTo(x + 154, y + h + 12);
    ctx.lineTo(x + 148, y + h);
    ctx.lineTo(x + r, y + h);
    ctx.arc(x + r, y + r, r, Math.PI / 2, -Math.PI / 2);
    ctx.closePath();
    ctx.fillStyle = 'rgba(18,32,58,0.74)';
    ctx.fill();
    ctx.strokeStyle = '#c9a850';
    ctx.lineWidth = 2.2;
    ctx.stroke();
    ctx.fillStyle = '#52d86e';
    ctx.beginPath();
    ctx.arc(x + 22, y + r, 5, 0, Math.PI * 2);
    ctx.fill();
    const label = text == null || String(text).trim() === '' ? '…' : String(text);
    const size = label.length > 6 ? 26 : label.length > 4 ? 30 : 34;
    ctx.fillStyle = '#e6f0ff';
    ctx.font = size + 'px sans-serif';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(label, x + w / 2 + 6, y + r);
    tex.needsUpdate = true;
  }
  draw('…');
  return { tex, draw };
}

export function lampGlowTexture() {
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

export function softShadowTexture() {
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

export function spriteAspectFromTexture(tex) {
  const img = tex && tex.image;
  if (img && img.width > 0 && img.height > 0) {
    return img.width / img.height;
  }
  return 526 / 768;
}

export function addMonitor(root, lookTarget, x, z, scale, traySurfaceY) {
  const group = new THREE.Group();
  group.userData.pickRole = 'monitor';
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

  return { painter, screenGlow, group, bezel };
}

export function makeHandheldPreviewLcdTexture() {
  const canvas = document.createElement('canvas');
  canvas.width = POCKET_LCD_W;
  canvas.height = POCKET_LCD_H;
  const ctx = canvas.getContext('2d');
  drawPocketMaryPreviewLcd(ctx);
  const tex = new THREE.CanvasTexture(canvas);
  tex.magFilter = THREE.NearestFilter;
  tex.minFilter = THREE.NearestFilter;
  tex.colorSpace = THREE.SRGBColorSpace;
  tex.needsUpdate = true;
  return tex;
}

/** 窗台掌機（馬克杯右側立放）。 */
export function addWindowSillHandheld(root, sillY, sillZ, mugX) {
  const group = new THREE.Group();
  group.userData.pickRole = 'handheld';
  const gx = mugX + 0.11;
  group.position.set(gx, sillY + 0.078, sillZ + 0.098);
  group.rotation.y = -0.3;
  group.scale.set(1.9, 1.9, 1.9);

  const bodyMat = new THREE.MeshLambertMaterial({
    color: 0xc8dc88,
    emissive: 0x1e542e,
    emissiveIntensity: 0.28
  });
  const body = box(0.056, 0.098, 0.03, bodyMat);
  body.name = 'windowSillHandheldBody';
  body.userData.pickRole = 'handheld';
  group.add(body);

  const lcdTex = makeHandheldPreviewLcdTexture();
  const screenMat = new THREE.MeshBasicMaterial({
    map: lcdTex,
    toneMapped: false
  });
  const screen = new THREE.Mesh(new THREE.PlaneGeometry(0.05, 0.046), screenMat);
  screen.position.set(0, 0.02, 0.016);
  screen.name = 'windowSillHandheldScreen';
  screen.userData.pickRole = 'handheld';
  group.add(screen);

  const lens = box(0.008, 0.008, 0.004, 0x302018, true);
  lens.position.set(-0.015, -0.03, 0.016);
  lens.name = 'windowSillHandheldLens';
  group.add(lens);

  const pickMat = new THREE.MeshBasicMaterial({
    transparent: true,
    opacity: 0,
    depthWrite: false,
    depthTest: false
  });
  const pick = new THREE.Mesh(new THREE.BoxGeometry(0.2, 0.26, 0.16), pickMat);
  pick.position.set(0, 0.01, 0.04);
  pick.name = 'windowSillHandheldPick';
  pick.userData.pickRole = 'handheld';
  pick.renderOrder = 10;
  group.add(pick);

  const glow = new THREE.PointLight(0x5ec87a, 0.62, 1.05, 2);
  glow.position.set(0, 0.024, 0.038);
  glow.name = 'windowSillHandheldGlow';
  group.add(glow);

  group.name = 'windowSillHandheld';
  root.add(group);
  return { group, body, screen, glow, pick };
}

export function addLowPolyPlant(root, x, z) {
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
