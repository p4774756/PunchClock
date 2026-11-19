// 自己頭上名牌：純邏輯（名稱、可見性、錨點）與 DOM 放置分離，供 node:test。
import {
  BOAT_DECK_OFFSET,
  BOAT_FLYBRIDGE_TOP,
  BOAT_PASSENGER_HEIGHT,
  boatPassengerScaleFactor,
  boatWorldPose,
  dockedBob,
  isSailingPhase,
  isSteeringPhase
} from './island-boat.js';
import { cleanNick, loadNick, NAME_MAX } from './island-presence.js';
import { PINK_CAR_ROOF_Y_FALLBACK } from './island-city-data.js';
import { isDrivingPhase } from './island-vehicle.js';
import { playerVisible } from './island-explore.js';

export const SELF_NAME_KEY = 'punchclock.island.selfName';
export const SELF_LABEL_HEAD_OFFSET = 1.18;
/** 名牌下緣對齊車頂時錨點（-120% 底緣）在車頂上方的距離（世界單位）。 */
export const VEHICLE_SELF_LABEL_ABOVE_ROOF = 0;
/** 航行／掌舵且主角隱藏時，掛在船頂（相對船體 Y）。 */
export const BOAT_VEHICLE_LABEL_TOP = BOAT_FLYBRIDGE_TOP + 0.08;
/** 測試／舊 API：名牌上方泡泡還用 px 佈局（見 island-overlay-layout.js）。 */
export const SELF_BUBBLE_ABOVE_NAME_REM = 1.65;
/** 與同儕名牌螢幕距離過近時，自己名牌往上錯開（px）。 */
export const SELF_PEER_SCREEN_NUDGE_PX = 22;
export const SELF_PEER_SCREEN_MIN_PX = 58;

const FALLBACK_NAME = '旅人';

export function loadSelfNameEnabled(storage) {
  if (!storage || typeof storage.getItem !== 'function') return true;
  try {
    const v = storage.getItem(SELF_NAME_KEY);
    if (v == null || v === '') return true;
    return v !== '0';
  } catch (err) {
    return true;
  }
}

export function saveSelfNameEnabled(storage, enabled) {
  if (!storage || typeof storage.setItem !== 'function') return;
  try {
    storage.setItem(SELF_NAME_KEY, enabled ? '1' : '0');
  } catch (err) {
    // 隱私模式：略過
  }
}

export function formatSelfDisplayName(raw) {
  const cleaned = cleanNick(raw);
  const text = cleaned || String(raw == null ? '' : raw).trim();
  if (!text) return '';
  const chars = Array.from(text);
  if (chars.length <= NAME_MAX) return text;
  return chars.slice(0, NAME_MAX).join('') + '…';
}

/** 已存暱稱 > welcome selfName > 後備「旅人」。 */
export function resolveSelfLabelName(opts) {
  const o = opts || {};
  const storage = o.storage;
  const stored = typeof o.loadNick === 'function' ? o.loadNick(storage) : loadNick(storage);
  if (stored) return formatSelfDisplayName(stored);
  const welcome = cleanNick(o.selfName);
  if (welcome) return formatSelfDisplayName(welcome);
  const fb = o.fallback == null ? FALLBACK_NAME : String(o.fallback);
  return fb;
}

function avatarShown(state) {
  return !!(
    state.miluVisible || state.placeholderVisible || state.blockVisible
  );
}

function onVehicleForLabel(state) {
  return !!(state.driving || state.sailing || state.steering);
}

/** 主角被藏且不是因載具時要隱藏名牌。 */
export function selfLabelVisible(state) {
  const s = state || {};
  if (!s.selfNameEnabled) return false;
  if (!playerVisible(s.mode)) return false;
  if (s.interiorBusy) return false;
  if (s.uiOverlay) return false;
  if (onVehicleForLabel(s)) return true;
  if (!avatarShown(s)) return false;
  const name = resolveSelfLabelName({
    storage: s.storage,
    loadNick: s.loadNick,
    selfName: s.selfName
  });
  return !!name;
}

function passengerHeadOffset(scale) {
  const sc = scale == null ? 1 : scale;
  if (sc < 0.999) {
    return BOAT_PASSENGER_HEIGHT * sc;
  }
  return SELF_LABEL_HEAD_OFFSET;
}

/**
 * 世界座標錨點。headY 為投影高度；kind 供測試與 debug。
 */
export function selfLabelAnchor(state) {
  const s = state || {};
  const offX = s.offsetX || 0;
  const offZ = s.offsetZ || 0;
  const scale = s.passengerScale == null ? 1 : s.passengerScale;
  const groundY = s.groundY == null ? s.P : s.groundY;
  const bobY = typeof s.boatBobY === 'number' ? s.boatBobY : 0;

  if (s.driving) {
    const x = (s.vehicleX == null ? s.playerX : s.vehicleX) + offX;
    const z = (s.vehicleZ == null ? s.playerZ : s.vehicleZ) + offZ;
    const gy = s.P == null ? groundY : s.P;
    const roofY = s.vehicleRoofY == null ? PINK_CAR_ROOF_Y_FALLBACK : s.vehicleRoofY;
    return Object.freeze({
      kind: 'vehicle',
      x: x,
      z: z,
      headY: vehicleSelfLabelHeadY(gy, roofY)
    });
  }

  if (s.sailing || s.steering) {
    const pose = s.boatPose || { x: s.playerX, z: s.playerZ, yaw: 0 };
    const hideAvatar = !avatarShown(s);
    if (hideAvatar) {
      const deckBase = bobY + BOAT_DECK_OFFSET;
      return Object.freeze({
        kind: 'boat',
        x: pose.x + offX,
        z: pose.z + offZ,
        headY: deckBase + BOAT_VEHICLE_LABEL_TOP
      });
    }
    const head = passengerHeadOffset(scale);
    return Object.freeze({
      kind: 'passenger',
      x: s.playerX + offX,
      z: s.playerZ + offZ,
      headY: groundY + head
    });
  }

  const head = passengerHeadOffset(scale);
  return Object.freeze({
    kind: 'player',
    x: (s.playerX || 0) + offX,
    z: (s.playerZ || 0) + offZ,
    headY: groundY + head
  });
}

export function projectInView(projected, camera, x, y, z, width, height) {
  if (!projected || !camera || typeof projected.set !== 'function') return null;
  projected.set(x, y, z);
  projected.project(camera);
  if (
    projected.z > 1
    || projected.x < -1.08
    || projected.x > 1.08
    || projected.y < -1.08
    || projected.y > 1.08
  ) {
    return null;
  }
  const w = width == null ? 1 : width;
  const h = height == null ? 1 : height;
  return {
    x: (projected.x * 0.5 + 0.5) * w,
    y: (-projected.y * 0.5 + 0.5) * h
  };
}

export function vehicleSelfLabelHeadY(groundY, roofY) {
  const gy = groundY == null ? 0 : groundY;
  const roof = roofY == null ? PINK_CAR_ROOF_Y_FALLBACK : roofY;
  return gy + roof + VEHICLE_SELF_LABEL_ABOVE_ROOF;
}

export function selfLabelPeerNudge(x, y, peers) {
  const list = peers || [];
  let nudge = 0;
  for (let i = 0; i < list.length; i++) {
    const p = list[i];
    if (!p || !p.visible) continue;
    if (Math.hypot((p.x || 0) - x, (p.y || 0) - y) < SELF_PEER_SCREEN_MIN_PX) {
      nudge = Math.max(nudge, SELF_PEER_SCREEN_NUDGE_PX);
    }
  }
  return nudge;
}

/** 自己名牌上移時，同儕名牌往下錯開（螢幕 px）。 */
export function peerNameScreenOffsetAway(selfLabelX, selfLabelY, peerX, peerY) {
  if (selfLabelX == null || selfLabelY == null) return 0;
  if (Math.hypot(peerX - selfLabelX, peerY - selfLabelY) >= SELF_PEER_SCREEN_MIN_PX) return 0;
  return SELF_PEER_SCREEN_NUDGE_PX;
}

export function selfLabelTransform(x, y) {
  return 'translate(' + x + 'px, ' + y + 'px) translate(-50%, -120%)';
}

export function selfBubbleExtraTransform(selfNameAbove) {
  if (!selfNameAbove) return '';
  return ' translateY(-' + SELF_BUBBLE_ABOVE_NAME_REM + 'rem)';
}

export function buildSelfLabelState(ctx) {
  const c = ctx || {};
  const phase = c.boatPhase;
  const boatPose = c.boatPose || (c.boatState ? boatWorldPose(c.boatState) : null);
  const bobY = c.boatElapsed != null
    ? dockedBob(c.boatElapsed, !!c.reduced)
    : (c.boatBobY || 0);
  const vehicle = c.vehicleState;
  return {
    mode: c.mode,
    storage: c.storage,
    loadNick: c.loadNick,
    selfName: c.selfName,
    selfNameEnabled: c.selfNameEnabled,
    interiorBusy: !!c.interiorBusy,
    uiOverlay: !!c.uiOverlay,
    miluVisible: !!c.miluVisible,
    placeholderVisible: !!c.placeholderVisible,
    blockVisible: !!c.blockVisible,
    driving: vehicle ? isDrivingPhase(vehicle.phase) : !!c.driving,
    sailing: phase ? isSailingPhase(phase) : !!c.sailing,
    steering: phase ? isSteeringPhase(phase) : !!c.steering,
    playerX: c.playerX,
    playerZ: c.playerZ,
    groundY: c.groundY,
    P: c.P,
    offsetX: c.offsetX,
    offsetZ: c.offsetZ,
    passengerScale: c.passengerScale == null
      ? boatPassengerScaleFactor(phase)
      : c.passengerScale,
    vehicleX: vehicle ? vehicle.carX : c.vehicleX,
    vehicleZ: vehicle ? vehicle.carZ : c.vehicleZ,
    vehicleRoofY: c.vehicleRoofY,
    boatPose: boatPose,
    boatBobY: bobY,
    peers: c.peers
  };
}

export function createSelfLabelPlacer(opts) {
  const doc = opts.document;
  const host = doc ? doc.getElementById('islandLabels') : null;
  let el = null;
  let textEl = null;
  let lastText = '';
  let debug = Object.freeze({
    visible: false,
    text: '',
    x: 0,
    y: 0,
    anchor: null
  });

  function ensure() {
    if (!doc || !host) return null;
    if (el && el.parentNode === host && textEl) return el;
    if (el && el.parentNode !== host) {
      el = null;
      textEl = null;
    }
    el = doc.createElement('div');
    el.className = 'island-self-name';
    el.hidden = true;
    const star = doc.createElement('span');
    star.className = 'island-self-name-star';
    star.setAttribute('aria-hidden', 'true');
    star.textContent = '★';
    textEl = doc.createElement('span');
    textEl.className = 'island-self-name-text';
    textEl.textContent = lastText;
    el.appendChild(star);
    el.appendChild(textEl);
    host.appendChild(el);
    return el;
  }

  function hide() {
    if (el) {
      el.hidden = true;
      el.style.visibility = 'hidden';
    }
    debug = Object.freeze({
      visible: false,
      text: lastText,
      x: debug.x,
      y: debug.y,
      anchor: debug.anchor
    });
  }

  function setText(name) {
    const next = name == null ? '' : String(name);
    if (!textEl) {
      lastText = next;
      return;
    }
    if (next === lastText) return;
    lastText = next;
    textEl.textContent = next;
  }

  /**
   * @returns {{ screenY: number, selfNameAbove: boolean, headY: number }|null}
   */
  function place(ctx) {
    const projected = ctx.projected;
    const camera = ctx.camera;
    const width = ctx.width;
    const height = ctx.height;
    const state = buildSelfLabelState(ctx);
    const anchor = selfLabelAnchor(state);
    const name = resolveSelfLabelName({
      storage: state.storage,
      loadNick: state.loadNick,
      selfName: state.selfName
    });
    setText(name);

    const show = selfLabelVisible(state);
    if (!show) {
      hide();
      return {
        selfNameAbove: false,
        headY: anchor.headY,
        labelX: null,
        labelY: null,
        screenY: null
      };
    }

    const node = ensure();
    if (!node) {
      hide();
      return {
        selfNameAbove: false,
        headY: anchor.headY,
        labelX: null,
        labelY: null,
        screenY: null
      };
    }

    const screen = projectInView(
      projected,
      camera,
      anchor.x,
      anchor.headY,
      anchor.z,
      width,
      height
    );
    if (!screen) {
      hide();
      return {
        selfNameAbove: false,
        headY: anchor.headY,
        labelX: null,
        labelY: null,
        screenY: null
      };
    }

    const nudge = selfLabelPeerNudge(screen.x, screen.y, ctx.peers);
    const y = screen.y - nudge;
    node.hidden = false;
    node.style.visibility = 'visible';
    node.style.transform = selfLabelTransform(screen.x, y);
    debug = Object.freeze({
      visible: true,
      text: name,
      x: screen.x,
      y: y,
      anchor: anchor
    });
    return {
      selfNameAbove: true,
      headY: anchor.headY,
      labelX: screen.x,
      labelY: y,
      screenY: screen.y,
      nudgePx: nudge
    };
  }

  function getDebug() {
    return debug;
  }

  function invalidateText() {
    lastText = '\0';
  }

  return {
    place: place,
    getDebug: getDebug,
    invalidateText: invalidateText
  };
}