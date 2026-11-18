// 搭船往返主島棧橋與沙灘島：純邏輯（不依賴 Three.js／DOM）。
import {
  BOAT_ROUTE,
  BOAT_STOPS,
  ISLANDS,
  BRIDGES,
  WALK_PADS,
  SEA_NAV_BOUNDS,
  walkable,
  blocked,
  onBridge
} from './island-layout.js';
import { smoothingFactor } from './island-explore.js';

export const BOAT_VOYAGE_SEC = 10;
/** 角色站位（船 Group 局部 Y，對齊船尾日光甲板頂面）。 */
export const BOAT_DECK_OFFSET = 0.41;
/** 船首特徵（本地 +Z），與 island-props buildBoatMesh 一致。 */
export const BOAT_BOW_LOCAL_Z = 1.02;
/** 船尾日光甲板中心（本地 Z）。 */
export const BOAT_STERN_DECK_Z = -0.68;
/** 日光甲板沿船寬（本地 X）尺寸，供站位測試。 */
export const BOAT_STERN_DECK_WIDTH = 0.58;
/** 航行時角色沿船尾（本地 -Z）偏移，對齊船尾甲板可站區（略偏船首避開泳池）。 */
export const BOAT_SEAT_STERN_OFFSET = 0.58;
/** 船體網格預算（不含燈與浪花）。 */
export const BOAT_HULL_MESH_BUDGET = 48;
/** 掌舵碰撞與靠岸判定用船體半徑。 */
export const BOAT_HULL_RADIUS = 0.44;
export const BOAT_STEER_SPEED = 2.15;
export const BOAT_STEER_ACCEL_LAMBDA = 7.5;
export const BOAT_YAW_LAMBDA = 9;
/** 靠岸下船：船心與泊位距離上限。 */
export const BOAT_DISEMBARK_RADIUS = 1.25;
/** 船上乘客縮放（不動陸上 MILU_ANIM.height）。 */
export const BOAT_PASSENGER_SCALE = 0.76;

/** 遊艇船體零件數（不含桅燈與航行浪花），供測試對齊。 */
export function boatHullPartCount() {
  return 28;
}

export function boatPassengerScaleFactor(phase) {
  return isOnBoatPhase(phase) ? BOAT_PASSENGER_SCALE : 1;
}

export const BOAT_BOB_BASE = -0.62;
export const BOAT_BOB_AMP = 0.035;

export { BOAT_ROUTE, BOAT_STOPS, SEA_NAV_BOUNDS };

export function isSteeringPhase(phase) {
  return phase === 'steering';
}

export function isOnBoatPhase(phase) {
  return isSailingPhase(phase) || isSteeringPhase(phase);
}

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

function freezeSteerFields(base) {
  return Object.freeze({
    phase: base.phase,
    progress: base.progress == null ? 0 : base.progress,
    dock: base.dock,
    x: base.x,
    z: base.z,
    yaw: base.yaw,
    vx: base.vx,
    vz: base.vz,
    steerDock: base.steerDock == null ? base.dock : base.steerDock
  });
}

/** 船心是否落在開阔水面（不含島陸地、橋面；棧橋附近水域仍可行）。 */
export function boatNavigable(x, z, hullRadius) {
  const r = hullRadius == null ? BOAT_HULL_RADIUS : hullRadius;
  const bounds = SEA_NAV_BOUNDS;
  if (x < bounds.minX + r || x > bounds.maxX - r
    || z < bounds.minZ + r || z > bounds.maxZ - r) {
    return false;
  }
  for (let i = 0; i < ISLANDS.length; i++) {
    const island = ISLANDS[i];
    const dx = x - island.x;
    const dz = z - island.z;
    const landR = island.r + r * 0.85;
    if (dx * dx + dz * dz < landR * landR) return false;
  }
  for (let j = 0; j < BRIDGES.length; j++) {
    if (onBridge(x, z, BRIDGES[j], r * 0.6)) return false;
  }
  if (walkable(x, z, r * 0.45)) return false;
  return true;
}

export function resolveBoatMove(fromX, fromZ, toX, toZ, hullRadius) {
  const r = hullRadius == null ? BOAT_HULL_RADIUS : hullRadius;
  if (boatNavigable(toX, toZ, r)) return { x: toX, z: toZ };
  if (boatNavigable(toX, fromZ, r)) return { x: toX, z: fromZ };
  if (boatNavigable(fromX, toZ, r)) return { x: fromX, z: toZ };
  return { x: fromX, z: fromZ };
}

export function disembarkNear(x, z, radius) {
  const lim = radius == null ? BOAT_DISEMBARK_RADIUS : radius;
  let best = null;
  let bestD = lim + 1;
  for (const key of ['main', 'beach']) {
    const berth = BOAT_STOPS[key].berth;
    const d = Math.hypot(x - berth.x, z - berth.z);
    if (d <= lim && d < bestD) {
      best = key;
      bestD = d;
    }
  }
  return best;
}

export function startSteering(state, fromStop) {
  if (!state) return state;
  if (isSailingPhase(state.phase) || isSteeringPhase(state.phase)) return state;
  const stop = fromStop === 'beach' ? 'beach' : 'main';
  const expected = stop === 'main' ? 'docked-main' : 'docked-beach';
  if (state.phase !== expected) return state;
  const berth = berthForDock(stop);
  return freezeSteerFields({
    phase: 'steering',
    progress: 0,
    dock: stop,
    steerDock: stop,
    x: berth.x,
    z: berth.z,
    yaw: berth.yaw,
    vx: 0,
    vz: 0
  });
}

export function startSailing(state, fromStop) {
  if (!state) return state;
  if (isSteeringPhase(state.phase)) return state;
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

export function stopSteering(state, dock) {
  if (!state || !isSteeringPhase(state.phase)) return state;
  const d = dock === 'beach' ? 'beach' : 'main';
  return Object.freeze({ phase: dockedPhaseFor(d), progress: 0, dock: d });
}

export function abortSteering(state) {
  if (!state || !isSteeringPhase(state.phase)) return state;
  const dock = state.steerDock === 'beach' ? 'beach' : 'main';
  return stopSteering(state, dock);
}

/** input: { x, z, moving } 為鏡頭相對世界方向；input.theta 可選。 */
export function stepSteering(state, input, dt, opts) {
  const options = opts || {};
  if (!state || !isSteeringPhase(state.phase)) return state;
  const reduced = !!options.reduced;
  const step = Math.max(0, dt || 0);
  let vx = state.vx || 0;
  let vz = state.vz || 0;
  let yaw = state.yaw || 0;
  const speed = options.speed == null ? BOAT_STEER_SPEED : options.speed;

  if (options.paused) {
    return freezeSteerFields({
      phase: 'steering',
      progress: 0,
      dock: state.dock,
      steerDock: state.steerDock,
      x: state.x,
      z: state.z,
      yaw: yaw,
      vx: 0,
      vz: 0
    });
  }

  const moving = input && input.moving;
  let targetVx = 0;
  let targetVz = 0;
  if (moving) {
    targetVx = input.x * speed;
    targetVz = input.z * speed;
  }

  if (reduced) {
    vx = moving ? targetVx : 0;
    vz = moving ? targetVz : 0;
  } else {
    const kVel = smoothingFactor(BOAT_STEER_ACCEL_LAMBDA, step, false);
    vx += (targetVx - vx) * kVel;
    vz += (targetVz - vz) * kVel;
    if (!moving && Math.hypot(vx, vz) < 0.02) {
      vx = 0;
      vz = 0;
    }
  }

  let nx = state.x + vx * step;
  let nz = state.z + vz * step;
  const moved = resolveBoatMove(state.x, state.z, nx, nz, BOAT_HULL_RADIUS);
  nx = moved.x;
  nz = moved.z;

  if (moving && (targetVx * targetVx + targetVz * targetVz > 1e-6)) {
    const targetYaw = Math.atan2(targetVx, targetVz);
    if (reduced) {
      yaw = targetYaw;
    } else {
      const kYaw = smoothingFactor(BOAT_YAW_LAMBDA, step, false);
      let dy = targetYaw - yaw;
      while (dy > Math.PI) dy -= Math.PI * 2;
      while (dy < -Math.PI) dy += Math.PI * 2;
      yaw += dy * kYaw;
    }
  } else if (!reduced && Math.hypot(vx, vz) > 0.04) {
    const driftYaw = Math.atan2(vx, vz);
    const kDrift = smoothingFactor(BOAT_YAW_LAMBDA * 0.6, step, false);
    let dy = driftYaw - yaw;
    while (dy > Math.PI) dy -= Math.PI * 2;
    while (dy < -Math.PI) dy += Math.PI * 2;
    yaw += dy * kDrift;
  }

  if (!boatNavigable(nx, nz, BOAT_HULL_RADIUS)) {
    nx = state.x;
    nz = state.z;
    vx = 0;
    vz = 0;
  }

  return freezeSteerFields({
    phase: 'steering',
    progress: 0,
    dock: state.dock,
    steerDock: state.steerDock,
    x: nx,
    z: nz,
    yaw: yaw,
    vx: vx,
    vz: vz
  });
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

/** 掌舵／自動航行時船的世界姿態。 */
export function boatWorldPose(state) {
  if (!state) return { x: 0, z: 0, yaw: 0 };
  if (isSteeringPhase(state.phase)) {
    return { x: state.x, z: state.z, yaw: state.yaw || 0 };
  }
  if (isSailingPhase(state.phase)) {
    const u = routeProgress(state.phase, state.progress);
    return sampleRoute(u);
  }
  const berth = berthForDock(state.dock);
  return { x: berth.x, z: berth.z, yaw: berth.yaw };
}

export function pierPadsForNav() {
  return WALK_PADS;
}

/** 航行／掌舵時是否應顯示船尾浪花（純邏輯，不含 reduced-motion）。 */
export function boatWakeActive(phase, speed, opts) {
  const options = opts || {};
  if (options.reduced) return false;
  if (options.voyagePaused && isSailingPhase(phase)) return false;
  if (isSailingPhase(phase)) return true;
  if (isSteeringPhase(phase)) {
    const lim = options.minSpeed == null ? 0.07 : options.minSpeed;
    return (speed || 0) > lim;
  }
  return false;
}