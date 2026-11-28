import * as THREE from './vendor/three.module.min.js';

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
