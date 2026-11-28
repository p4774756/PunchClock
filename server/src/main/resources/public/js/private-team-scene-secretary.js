import * as THREE from './vendor/three.module.min.js';
import { isWorkingStatus } from './private-team-office.js';
import {
  secretarySpritePackForOutfit,
  secretaryStandbyIdleUrlsForOutfit,
  secretaryCelebrateStepsForOutfit,
  secretaryDanceDurationMsForSteps
} from './private-team-secretary-outfit.js';

const DEFAULT_PACK = secretarySpritePackForOutfit('default');

export const SECRETARY_SPRITE_URL_TYPING = DEFAULT_PACK.typing;
export const SECRETARY_SPRITE_URL_TYPING_B = DEFAULT_PACK.typingB;
export const SECRETARY_SPRITE_URL_WAVE = DEFAULT_PACK.wave;
export const SECRETARY_SPRITE_URL_YEAH = DEFAULT_PACK.yeah;
export const SECRETARY_SPRITE_URL_IDLE = DEFAULT_PACK.idle;
export const SECRETARY_SPRITE_URL_READ = DEFAULT_PACK.read;
export const SECRETARY_SPRITE_URL_PHONE = DEFAULT_PACK.phone;
export const SECRETARY_SPRITE_URL_SLEEP = DEFAULT_PACK.sleep;
export const SECRETARY_SPRITE_URL_SOFA = DEFAULT_PACK.sofa;

export function isWorkingTigerStatus(status) {
  return isWorkingStatus(status);
}

export function isLoungeTigerStatus(status) {
  const s = status == null ? '' : String(status).trim();
  return s === '待命中' || s === '待機' || s === '休息中';
}

/** 沙發立繪無法載入時，改用具站姿／坐姿立繪（仍留在休憩區，不回浴缸）。 */
export function secretaryTubFallbackUrlForStatus(status, pack = DEFAULT_PACK) {
  const s = status == null ? '' : String(status).trim();
  if (isWorkingStatus(s)) return pack.typing;
  if (s === '休息中') return pack.sleep;
  if (s === '待命中' || s === '待機') return pack.idle;
  return pack.idle;
}

export function isSecretarySofaSpriteUrl(url) {
  const u = url == null ? '' : String(url).trim();
  const defaultSofa = secretarySpritePackForOutfit('default').sofa;
  const stageSofa = secretarySpritePackForOutfit('stage').sofa;
  return u === defaultSofa || u === stageSofa;
}

export const SECRETARY_STANDBY_IDLE_URLS = secretaryStandbyIdleUrlsForOutfit('default');
export const SECRETARY_IDLE_ROTATE_MS = 2 * 60 * 1000;

export const SECRETARY_CLAP_SPRITE_A = DEFAULT_PACK.clapA;
export const SECRETARY_CLAP_SPRITE_B = DEFAULT_PACK.clapB;

/** 慶祝：拍手兩下 → 比讚 → 小跳（只播一次）。 */
export const SECRETARY_CELEBRATE_STEPS = secretaryCelebrateStepsForOutfit('default');
export const SECRETARY_DANCE_DURATION_MS = secretaryDanceDurationMsForSteps(SECRETARY_CELEBRATE_STEPS);
export const SECRETARY_IDLE_DANCE_AFTER_MS = 4 * 60 * 1000;
export const SECRETARY_IDLE_DANCE_COOLDOWN_MS = 5 * 60 * 1000;

export function secretaryCelebrateStateAt(elapsedMs, steps = SECRETARY_CELEBRATE_STEPS) {
  const danceMs = secretaryDanceDurationMsForSteps(steps);
  const t0 = Number(elapsedMs);
  if (!Number.isFinite(t0) || t0 < 0) {
    const first = steps[0];
    return { kind: 'frame', url: first && first.url ? first.url : '', jumpT: null };
  }
  if (t0 >= danceMs) {
    return { kind: 'done', url: '', jumpT: null };
  }
  let t = t0;
  let lastUrl = steps[0] && steps[0].url ? steps[0].url : '';
  for (let i = 0; i < steps.length; i++) {
    const step = steps[i];
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

export function secretaryCelebrateFrameUrlAt(elapsedMs, steps = SECRETARY_CELEBRATE_STEPS) {
  const st = secretaryCelebrateStateAt(elapsedMs, steps);
  if (st.kind === 'done') return '';
  return st.url;
}

export function secretaryCelebrateJumpOffsetAt(elapsedMs, steps = SECRETARY_CELEBRATE_STEPS) {
  const st = secretaryCelebrateStateAt(elapsedMs, steps);
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

export function secretaryTypingDisplayUrl(statusBaseUrl, useTypingB, reducedMotion, pack = DEFAULT_PACK) {
  const base = statusBaseUrl == null ? '' : String(statusBaseUrl);
  if (base === pack.typing && useTypingB && !reducedMotion) {
    return pack.typingB;
  }
  return base;
}

export function isSecretaryStandbyIdleUrl(url, pack = DEFAULT_PACK) {
  const u = url == null ? '' : String(url).trim();
  return u === pack.idle || u === pack.read || u === pack.phone;
}

/** 心跳輪詢仍傳待命預設喝茶圖時，勿蓋掉輪替中的看書／講電話。 */
export function shouldIgnoreStandbyIdleSpriteUpdate(currentUrl, incomingUrl, pack = DEFAULT_PACK) {
  const cur = currentUrl == null ? '' : String(currentUrl).trim();
  const inc = incomingUrl == null ? '' : String(incomingUrl).trim();
  if (inc !== pack.idle || cur === pack.idle) return false;
  return isSecretaryStandbyIdleUrl(cur, pack);
}

/** 待命立繪輪替：從三張中選，不連續同一張（randomInt 供單測固定種子）。 */
export function pickNextStandbyIdleUrl(currentUrl, randomInt, pack = DEFAULT_PACK) {
  const cur = currentUrl == null ? '' : String(currentUrl).trim();
  const standby = [pack.idle, pack.read, pack.phone];
  const pool = standby.filter((u) => u !== cur);
  if (pool.length === 0) return standby[0];
  const ri = Number(randomInt);
  const idx = Number.isFinite(ri) ? Math.abs(Math.floor(ri)) % pool.length : 0;
  return pool[idx];
}

export {
  secretarySpritePackForOutfit,
  secretaryStandbyIdleUrlsForOutfit,
  secretaryCelebrateStepsForOutfit,
  secretaryDanceDurationMsForSteps,
  secretaryEquivalentSpriteUrl,
  secretaryOutfitPreloadUrls,
  parseSecretaryOutfitId
} from './private-team-secretary-outfit.js';

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

function makeBubbleDrawer() {
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
function spriteAspectFromTexture(tex) {
  const img = tex && tex.image;
  if (img && img.width > 0 && img.height > 0) {
    return img.width / img.height;
  }
  return 526 / 768;
}

const SECRETARY_TYPING_FRAME_MS = 500;
const SECRETARY_WAVE_MS = 3000;
const SECRETARY_YEAH_MS = 2500;
const SECRETARY_SPRITE_HEIGHT = 1.42;

export {
  SECRETARY_TYPING_FRAME_MS,
  SECRETARY_WAVE_MS,
  SECRETARY_YEAH_MS,
  SECRETARY_SPRITE_HEIGHT
};
