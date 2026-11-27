import * as THREE from './vendor/three.module.js';

export const PRIVATE_TEAM_ZOOM_MIN = 0.72;
export const PRIVATE_TEAM_ZOOM_MAX = 2.05;

/** 鍵盤托盤上緣（與原桌面高度相近） */
export const PRIVATE_TEAM_TRAY_SURFACE_Y = 0.498;

/** 浴缸水面高度（約齊腰，略高於托盤，鍵盤仍露出） */
export const PRIVATE_TEAM_WATER_Y = 0.468;

export function clampPrivateTeamZoom(z) {
  const n = Number(z);
  if (!Number.isFinite(n)) return 1;
  return Math.min(PRIVATE_TEAM_ZOOM_MAX, Math.max(PRIVATE_TEAM_ZOOM_MIN, n));
}

/** 窄螢幕直向（含手機直立） */
export function privateTeamNarrowPortrait(width, height) {
  const w = Number(width);
  const h = Number(height);
  if (!(w > 0) || !(h > 0)) return false;
  return w < 640 && h > w;
}

/** 預設垂直視野角；窄螢幕直向略加大，讓窗台掌機與秘書同時入鏡 */
export const PRIVATE_TEAM_CAMERA_FOV = 42;
export const PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT = 50;
/** 窄螢幕直向預設略向左轉（弧度），把右緣窗台與掌機收進畫面 */
export const PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT = -0.25;

export function privateTeamDefaultFov(width, height) {
  const w = Number(width);
  const h = Number(height);
  if (!(w > 0) || !(h > 0)) return PRIVATE_TEAM_CAMERA_FOV;
  if (privateTeamNarrowPortrait(w, h)) return PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT;
  return PRIVATE_TEAM_CAMERA_FOV;
}

export function privateTeamDefaultYaw(width, height) {
  const w = Number(width);
  const h = Number(height);
  if (!(w > 0) || !(h > 0)) return 0;
  if (privateTeamNarrowPortrait(w, h)) return PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT;
  return 0;
}

/** 首次載入預設鏡頭距離：窄螢幕直向拉遠，桌面／橫向維持約 1 */
export function privateTeamDefaultZoom(width, height) {
  const w = Number(width);
  const h = Number(height);
  if (!(w > 0) || !(h > 0)) return 1;
  if (privateTeamNarrowPortrait(w, h)) return clampPrivateTeamZoom(1.52);
  return 1;
}

/** 雙指捏合縮放倍率（距離變大＝拉近，變小＝拉遠） */
export function privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist) {
  if (!(pinchStartDist > 0) || !(dist > 0)) return pinchStartZoom;
  return pinchStartZoom * (pinchStartDist / dist);
}

/** 水平拖曳視角左右上限（度） */
export const PRIVATE_TEAM_YAW_MAX_DEG = 35;
/** 窄螢幕直向加大偏航，讓左右牆面與銅人可滑進畫面 */
export const PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT = 52;
export const PRIVATE_TEAM_DRAG_THRESHOLD_PX = 6;
export const PRIVATE_TEAM_PAN_HINT_DISMISS_KEY = 'punchclock-pt-pan-seen';

/** localStorage：左牆海報輪播索引（0–3 對應第 1–4 張）。 */
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

const PRIVATE_TEAM_YAW_MAX_RAD = (PRIVATE_TEAM_YAW_MAX_DEG * Math.PI) / 180;

export function privateTeamYawMaxRadians(width, height) {
  const deg = privateTeamNarrowPortrait(width, height)
    ? PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT
    : PRIVATE_TEAM_YAW_MAX_DEG;
  return (deg * Math.PI) / 180;
}

export function clampPrivateTeamYaw(radians) {
  const n = Number(radians);
  if (!Number.isFinite(n)) return 0;
  return Math.min(PRIVATE_TEAM_YAW_MAX_RAD, Math.max(-PRIVATE_TEAM_YAW_MAX_RAD, n));
}

export function clampPrivateTeamYawViewport(radians, width, height) {
  const n = Number(radians);
  if (!Number.isFinite(n)) return 0;
  const max = privateTeamYawMaxRadians(width, height);
  return Math.min(max, Math.max(-max, n));
}

/** 位移未超過門檻時仍視為點擊（不觸發旋轉） */
export function privateTeamDragExceedsThreshold(dx, dy) {
  return Math.hypot(Number(dx), Number(dy)) > PRIVATE_TEAM_DRAG_THRESHOLD_PX;
}

/** 依水平拖曳距離與畫布寬度換算繞 camLook 的偏航角 */
export function privateTeamYawFromDragDelta(deltaX, canvasWidth, startYaw, canvasHeight) {
  const w = Math.max(Number(canvasWidth), 1);
  const h = Number(canvasHeight);
  const base = Number(startYaw);
  const maxRad =
    Number.isFinite(h) && h > 0 ? privateTeamYawMaxRadians(w, h) : PRIVATE_TEAM_YAW_MAX_RAD;
  const scale = (2 * maxRad) / w;
  const next = (Number.isFinite(base) ? base : 0) - Number(deltaX) * scale;
  if (Number.isFinite(h) && h > 0) return clampPrivateTeamYawViewport(next, w, h);
  return clampPrivateTeamYaw(next);
}

export function privateTeamPanHintShouldShow(narrowPortrait, dismissed) {
  return !!narrowPortrait && !dismissed;
}

/** 拖曳釋放後的慣性（rad/ms）；reduced motion 時應略過 */
export function privateTeamYawDragVelocityRadPerMs(deltaPx, dtMs, canvasWidth, canvasHeight) {
  const dt = Number(dtMs);
  const dx = Number(deltaPx);
  if (!Number.isFinite(dt) || dt < 12 || !Number.isFinite(dx)) return 0;
  const w = Math.max(Number(canvasWidth), 1);
  const h = Number(canvasHeight);
  if (!(h > 0)) return 0;
  const maxRad = privateTeamYawMaxRadians(w, h);
  const scale = (2 * maxRad) / w;
  return (-dx / dt) * scale * 0.38;
}

export function privateTeamYawMomentumTick(yaw, velRadPerMs, dtMs, canvasWidth, canvasHeight) {
  const dt = Number(dtMs);
  let v = Number(velRadPerMs);
  let y = Number(yaw);
  if (!Number.isFinite(y) || !Number.isFinite(v) || !(dt > 0)) return { yaw: 0, vel: 0 };
  const w = Math.max(Number(canvasWidth), 1);
  const h = Number(canvasHeight);
  if (!(h > 0)) return { yaw: y, vel: 0 };
  y = clampPrivateTeamYawViewport(y + v * dt, w, h);
  v *= Math.exp(-5.2 * (dt / 1000));
  const maxRad = privateTeamYawMaxRadians(w, h);
  if (Math.abs(y) >= maxRad - 1e-5) v = 0;
  if (Math.abs(v) < 1e-5) v = 0;
  return { yaw: y, vel: v };
}

/** 上下俯仰上限（度，相對基準鏡頭 camBase→camLook）：上拉平視、下拉俯瞰 */
export const PRIVATE_TEAM_PITCH_MAX_UP_DEG = 20;
export const PRIVATE_TEAM_PITCH_MAX_DOWN_DEG = 14;
const PRIVATE_TEAM_PITCH_MAX_UP_RAD = (PRIVATE_TEAM_PITCH_MAX_UP_DEG * Math.PI) / 180;
const PRIVATE_TEAM_PITCH_MAX_DOWN_RAD = (PRIVATE_TEAM_PITCH_MAX_DOWN_DEG * Math.PI) / 180;

export function clampPrivateTeamPitch(radians) {
  const n = Number(radians);
  if (!Number.isFinite(n)) return 0;
  return Math.min(
    PRIVATE_TEAM_PITCH_MAX_UP_RAD,
    Math.max(-PRIVATE_TEAM_PITCH_MAX_DOWN_RAD, n)
  );
}

/** 依垂直拖曳與畫布高度換算俯仰；往上拖為正（平視窗外／秘書） */
export function privateTeamPitchFromDragDelta(deltaY, canvasHeight, startPitch) {
  const h = Math.max(Number(canvasHeight), 1);
  const base = Number(startPitch);
  const range = PRIVATE_TEAM_PITCH_MAX_UP_RAD + PRIVATE_TEAM_PITCH_MAX_DOWN_RAD;
  const scale = range / h;
  const next = (Number.isFinite(base) ? base : 0) - Number(deltaY) * scale;
  return clampPrivateTeamPitch(next);
}

export function privateTeamPitchDragVelocityRadPerMs(deltaPx, dtMs, canvasHeight) {
  const dt = Number(dtMs);
  const dy = Number(deltaPx);
  if (!Number.isFinite(dt) || dt < 12 || !Number.isFinite(dy)) return 0;
  const h = Math.max(Number(canvasHeight), 1);
  if (!(h > 0)) return 0;
  const range = PRIVATE_TEAM_PITCH_MAX_UP_RAD + PRIVATE_TEAM_PITCH_MAX_DOWN_RAD;
  const scale = range / h;
  return (-dy / dt) * scale * 0.38;
}

export function privateTeamPitchMomentumTick(pitch, velRadPerMs, dtMs, canvasHeight) {
  const dt = Number(dtMs);
  let v = Number(velRadPerMs);
  let p = Number(pitch);
  if (!Number.isFinite(p) || !Number.isFinite(v) || !(dt > 0)) return { pitch: 0, vel: 0 };
  const h = Math.max(Number(canvasHeight), 1);
  if (!(h > 0)) return { pitch: p, vel: 0 };
  p = clampPrivateTeamPitch(p + v * dt);
  v *= Math.exp(-5.2 * (dt / 1000));
  const up = PRIVATE_TEAM_PITCH_MAX_UP_RAD;
  const down = PRIVATE_TEAM_PITCH_MAX_DOWN_RAD;
  if (Math.abs(p - up) < 1e-5 || Math.abs(p + down) < 1e-5) v = 0;
  if (Math.abs(v) < 1e-5) v = 0;
  return { pitch: p, vel: v };
}
