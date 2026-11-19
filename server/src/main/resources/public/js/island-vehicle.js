// 城市島粉紅小車（純邏輯，不依賴 Three.js／DOM）。
import {
  ISLANDS,
  islandAt,
  islandById,
  walkable,
  blocked
} from './island-layout.js';
import { smoothingFactor } from './island-explore.js';
import { pinkCarWorld, CITY_ID } from './island-city-data.js';
import { worldMapBounds, worldNorm } from './island-minimap.js';

export const VEHICLE_STEER_SPEED = 6.8;
export const VEHICLE_ACCEL_LAMBDA = 8.5;
export const VEHICLE_YAW_LAMBDA = 11;
export const VEHICLE_RADIUS = 0.34;
export const VEHICLE_DISMOUNT_SPEED = 0.55;
export const VEHICLE_FOLLOW_BACK = 5.8;
export const VEHICLE_FOLLOW_HEIGHT = 1.35;

export function isDrivingPhase(phase) {
  return phase === 'driving';
}

export function createVehicleState() {
  const car = pinkCarWorld();
  return Object.freeze({
    phase: 'on-foot',
    carX: car.x,
    carZ: car.z,
    carYaw: car.yaw,
    vx: 0,
    vz: 0
  });
}

export function vehicleSpeedKmh(vx, vz) {
  return Math.round(Math.hypot(vx || 0, vz || 0) * 11.2);
}

export function canEnterVehicle(playerX, playerZ, state) {
  if (!state || isDrivingPhase(state.phase)) return false;
  const car = pinkCarWorld();
  const dx = playerX - car.x;
  const dz = playerZ - car.z;
  return dx * dx + dz * dz <= 1.35 * 1.35;
}

function freezeVehicle(base) {
  return Object.freeze({
    phase: base.phase,
    carX: base.carX,
    carZ: base.carZ,
    carYaw: base.carYaw,
    vx: base.vx == null ? 0 : base.vx,
    vz: base.vz == null ? 0 : base.vz
  });
}

export function enterVehicle(state, playerX, playerZ) {
  if (!state || isDrivingPhase(state.phase)) return state;
  if (!canEnterVehicle(playerX, playerZ, state)) return state;
  return freezeVehicle({
    phase: 'driving',
    carX: state.carX,
    carZ: state.carZ,
    carYaw: state.carYaw,
    vx: 0,
    vz: 0
  });
}

export function exitVehicle(state, opts) {
  if (!state || !isDrivingPhase(state.phase)) return { state: state, player: null };
  const options = opts || {};
  const speed = Math.hypot(state.vx, state.vz);
  if (speed > (options.maxSpeed == null ? VEHICLE_DISMOUNT_SPEED : options.maxSpeed)) {
    return { state: state, player: null };
  }
  const yaw = state.carYaw;
  const side = options.side == null ? -1 : options.side;
  const offX = state.carX + Math.cos(yaw) * side * 0.95;
  const offZ = state.carZ - Math.sin(yaw) * side * 0.95;
  const next = freezeVehicle({
    phase: 'on-foot',
    carX: state.carX,
    carZ: state.carZ,
    carYaw: state.carYaw,
    vx: 0,
    vz: 0
  });
  return {
    state: next,
    player: Object.freeze({ x: offX, z: offZ, yaw: yaw + Math.PI * 0.5 * side })
  };
}

export function forceExitVehicle(state) {
  if (!state || !isDrivingPhase(state.phase)) return state;
  return freezeVehicle({
    phase: 'on-foot',
    carX: state.carX,
    carZ: state.carZ,
    carYaw: state.carYaw,
    vx: 0,
    vz: 0
  });
}

function onCityLand(x, z, radius) {
  const island = islandById(CITY_ID);
  if (!island) return false;
  const dx = x - island.x;
  const dz = z - island.z;
  const rr = island.r - (radius || 0);
  return rr > 0 && dx * dx + dz * dz <= rr * rr;
}

export function vehicleNavigable(x, z, hullRadius) {
  const r = hullRadius == null ? VEHICLE_RADIUS : hullRadius;
  if (!onCityLand(x, z, r)) return false;
  if (!walkable(x, z, r * 0.5)) return false;
  if (blocked(x, z, r)) return false;
  return true;
}

export function resolveVehicleMove(fromX, fromZ, toX, toZ, hullRadius) {
  const r = hullRadius == null ? VEHICLE_RADIUS : hullRadius;
  if (vehicleNavigable(toX, toZ, r)) return { x: toX, z: toZ };
  if (vehicleNavigable(toX, fromZ, r)) return { x: toX, z: fromZ };
  if (vehicleNavigable(fromX, toZ, r)) return { x: fromX, z: toZ };
  return { x: fromX, z: fromZ };
}

export function stepVehicle(state, input, dt, opts) {
  const options = opts || {};
  if (!state || !isDrivingPhase(state.phase)) return state;
  const step = Math.max(0, dt || 0);
  let vx = state.vx || 0;
  let vz = state.vz || 0;
  let yaw = state.carYaw || 0;

  if (options.paused) {
    return freezeVehicle({
      phase: 'driving',
      carX: state.carX,
      carZ: state.carZ,
      carYaw: yaw,
      vx: 0,
      vz: 0
    });
  }

  const moving = input && input.moving;
  const speed = options.speed == null ? VEHICLE_STEER_SPEED : options.speed;
  let targetVx = 0;
  let targetVz = 0;
  if (moving) {
    targetVx = input.x * speed;
    targetVz = input.z * speed;
  }

  if (options.reduced) {
    vx = moving ? targetVx : 0;
    vz = moving ? targetVz : 0;
  } else {
    const kVel = smoothingFactor(VEHICLE_ACCEL_LAMBDA, step, false);
    vx += (targetVx - vx) * kVel;
    vz += (targetVz - vz) * kVel;
    if (!moving && Math.hypot(vx, vz) < 0.03) {
      vx = 0;
      vz = 0;
    }
  }

  let nx = state.carX + vx * step;
  let nz = state.carZ + vz * step;
  const moved = resolveVehicleMove(state.carX, state.carZ, nx, nz, VEHICLE_RADIUS);
  nx = moved.x;
  nz = moved.z;

  if (moving && (targetVx * targetVx + targetVz * targetVz > 1e-6)) {
    const targetYaw = Math.atan2(targetVx, targetVz);
    if (options.reduced) {
      yaw = targetYaw;
    } else {
      const kYaw = smoothingFactor(VEHICLE_YAW_LAMBDA, step, false);
      let dy = targetYaw - yaw;
      while (dy > Math.PI) dy -= Math.PI * 2;
      while (dy < -Math.PI) dy += Math.PI * 2;
      yaw += dy * kYaw;
    }
  } else if (!options.reduced && Math.hypot(vx, vz) > 0.05) {
    const driftYaw = Math.atan2(vx, vz);
    const kDrift = smoothingFactor(VEHICLE_YAW_LAMBDA * 0.65, step, false);
    let dy = driftYaw - yaw;
    while (dy > Math.PI) dy -= Math.PI * 2;
    while (dy < -Math.PI) dy += Math.PI * 2;
    yaw += dy * kDrift;
  }

  if (!vehicleNavigable(nx, nz, VEHICLE_RADIUS)) {
    nx = state.carX;
    nz = state.carZ;
    vx = 0;
    vz = 0;
  }

  return freezeVehicle({
    phase: 'driving',
    carX: nx,
    carZ: nz,
    carYaw: yaw,
    vx: vx,
    vz: vz
  });
}

export function vehicleCameraLook(state) {
  if (!state || !isDrivingPhase(state.phase)) return null;
  const yaw = state.carYaw;
  const backX = state.carX - Math.sin(yaw) * 1.2;
  const backZ = state.carZ - Math.cos(yaw) * 1.2;
  return Object.freeze({
    lookX: backX,
    lookY: 0,
    lookZ: backZ,
    targetX: state.carX,
    targetZ: state.carZ
  });
}

export function minimapBounds() {
  return worldMapBounds();
}

export function playerOnCityIsland(x, z) {
  const hit = islandAt(x, z, 0.1);
  return hit && hit.id === CITY_ID;
}

/** 縮小座標到 0..1 供小地圖。 */
export function minimapNorm(x, z) {
  return worldNorm(x, z);
}