// 搭船往返主島棧橋與沙灘島：純邏輯（不依賴 Three.js／DOM）。
import { BOAT_ROUTE, BOAT_STOPS, walkable, blocked } from './island-layout.js';

export const BOAT_VOYAGE_SEC = 10;
/** 角色站位（船 Group 局部 Y，對齊船尾木質日光甲板頂面）。 */
export const BOAT_DECK_OFFSET = 0.36;
/** 船首特徵（本地 +Z），與 island-props buildBoatMesh 一致。 */
export const BOAT_BOW_LOCAL_Z = 0.88;
/** 船尾日光甲板中心（本地 Z）。 */
export const BOAT_STERN_DECK_Z = -0.58;
/** 航行時角色沿船尾（本地 -Z）偏移，對齊船尾甲板中心。 */
export const BOAT_SEAT_STERN_OFFSET = -BOAT_STERN_DECK_Z;
/** 船體網格預算（不含燈與浪花）。 */
export const BOAT_HULL_MESH_BUDGET = 20;

/** 遊艇船體零件數（不含桅燈與航行浪花），供測試對齊。 */
export function boatHullPartCount() {
  return 17;
}
export const BOAT_BOB_BASE = -0.62;
export const BOAT_BOB_AMP = 0.035;

export { BOAT_ROUTE, BOAT_STOPS };

export function createBoatState(dock) {
  const d = dock === 'beach' ? 'beach' : 'main';
  return Object.freeze({ phase: 'docked-' + d, progress: 0, dock: d });
}

export function isSailingPhase(phase) {
  return phase === 'sailing-out' || phase === 'sailing-back';
}

export function easeTrip(u) {
  const t = Math.max(0, Math.min(1, u));
  return t * t * (3 - 2 * t);
}

function positionAtRaw(t, pts) {
  if (pts.length < 2) return { x: 0, z: 0 };
  const u = Math.max(0, Math.min(1, t));
  if (pts.length === 2) {
    return {
      x: pts[0].x + (pts[1].x - pts[0].x) * u,
      z: pts[0].z + (pts[1].z - pts[0].z) * u
    };
  }
  const half = u < 0.5 ? u * 2 : (u - 0.5) * 2;
  if (u < 0.5) {
    return {
      x: pts[0].x + (pts[1].x - pts[0].x) * half,
      z: pts[0].z + (pts[1].z - pts[0].z) * half
    };
  }
  return {
    x: pts[1].x + (pts[2].x - pts[1].x) * half,
    z: pts[1].z + (pts[2].z - pts[1].z) * half
  };
}

export function sampleRoute(u, route) {
  const pts = route || BOAT_ROUTE;
  const t = easeTrip(Math.max(0, Math.min(1, u)));
  const pos = positionAtRaw(t, pts);
  const eps = 0.015;
  const ahead = positionAtRaw(Math.min(1, t + eps), pts);
  const behind = positionAtRaw(Math.max(0, t - eps), pts);
  const tx = ahead.x - behind.x;
  const tz = ahead.z - behind.z;
  let yaw = 0;
  if (tx * tx + tz * tz > 1e-8) yaw = Math.atan2(tx, tz);
  return { x: pos.x, z: pos.z, yaw: yaw };
}

export function routeProgress(phase, progress) {
  if (phase === 'sailing-out') return progress;
  if (phase === 'sailing-back') return 1 - progress;
  if (phase === 'docked-beach') return 1;
  return 0;
}

export function berthForDock(dock) {
  return BOAT_STOPS[dock] ? BOAT_STOPS[dock].berth : BOAT_STOPS.main.berth;
}

export function startSailing(state, fromStop) {
  if (!state) return state;
  if (fromStop === 'main' && state.phase === 'docked-main') {
    return Object.freeze({ phase: 'sailing-out', progress: 0, dock: 'main' });
  }
  if (fromStop === 'beach' && state.phase === 'docked-beach') {
    return Object.freeze({ phase: 'sailing-back', progress: 0, dock: 'beach' });
  }
  return state;
}

function dockedPhaseFor(dest) {
  return dest === 'beach' ? 'docked-beach' : 'docked-main';
}

export function voyageDestination(phase) {
  if (phase === 'sailing-out') return 'beach';
  if (phase === 'sailing-back') return 'main';
  return null;
}

export function stepBoat(state, dt, opts) {
  const options = opts || {};
  if (!state || !isSailingPhase(state.phase)) return state;
  if (options.paused) return state;
  const duration = options.duration == null ? BOAT_VOYAGE_SEC : options.duration;
  if (options.reduced) {
    const dest = voyageDestination(state.phase);
    return Object.freeze({ phase: dockedPhaseFor(dest), progress: 0, dock: dest });
  }
  const progress = state.progress + Math.max(0, dt) / duration;
  if (progress >= 1) {
    const dest = voyageDestination(state.phase);
    return Object.freeze({ phase: dockedPhaseFor(dest), progress: 0, dock: dest });
  }
  return Object.freeze({ phase: state.phase, progress: progress, dock: state.dock });
}

export function abortSailing(state) {
  if (!state || !isSailingPhase(state.phase)) return state;
  const dest = voyageDestination(state.phase);
  return Object.freeze({ phase: dockedPhaseFor(dest), progress: 0, dock: dest });
}

export function dockedBob(elapsed, reduced) {
  if (reduced) return BOAT_BOB_BASE;
  const t = elapsed || 0;
  return BOAT_BOB_BASE + Math.sin(t * 0.9 + 0.6) * BOAT_BOB_AMP;
}

export function routeInOpenWater(route, radius, samples) {
  const n = samples == null ? 40 : samples;
  const r = radius == null ? 0.35 : radius;
  for (let i = 0; i <= n; i++) {
    const u = i / n;
    const p = sampleRoute(u, route);
    if (walkable(p.x, p.z, r)) return false;
  }
  return true;
}

function canStandAt(x, z, radius) {
  const r = radius == null ? 0.29 : radius;
  return walkable(x, z, r) && !blocked(x, z, r);
}

export function disembarkPointsStand(radius) {
  const stops = BOAT_STOPS;
  const r = radius == null ? 0.29 : radius;
  return canStandAt(stops.main.land.x, stops.main.land.z, r)
    && canStandAt(stops.beach.land.x, stops.beach.land.z, r);
}