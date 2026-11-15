// 室內清單與進出狀態（不依賴 Three.js／DOM）。
//
// 一筆室內對一棟 BUILDINGS：
//   id、buildingId、spawn {x,z,yaw}、exit {x,z,r,label}、
//   outside {x,z,yaw}（島上門口外、面向離開建物）、floor、blocks。
// 座標是室內本地座標，原點由 INTERIOR_ORIGIN 平移到島外，避免和島上碰撞重疊。
//
// 之後加一棟可進入的建築：
//   1. BUILDINGS 加上 entry（門不在正面就用 entry.at，不要改其它建築的門）。
//   2. 在 INTERIORS 登記一筆。
//   3. island.js 的 INTERIOR_MODULES 補上該室內的 dynamic import。

import { cameraRelative, PLAYER_SPEED, MAX_STEP_DT } from './island-explore.js';

export const ENTER_COOLDOWN_MS = 700;
export const INTERIOR_ORIGIN = Object.freeze({ x: 0, z: -220 });

export const INTERIORS = Object.freeze([
  Object.freeze({
    id: 'shop-room',
    buildingId: 'shop',
    spawn: Object.freeze({ x: 0, z: 0.2, yaw: Math.PI }),
    exit: Object.freeze({ x: 0, z: -1.35, r: 0.58, label: '離開莫卡手搖' }),
    outside: Object.freeze({ x: -3.86, z: -1.4, yaw: -Math.PI / 2 }),
    floor: Object.freeze({ minX: -2.35, maxX: 2.35, minZ: -1.9, maxZ: 2.05 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'counter', minX: -1.25, maxX: 0.2, minZ: 0.75, maxZ: 1.4 }),
      Object.freeze({ id: 'table', minX: 1.15, maxX: 1.9, minZ: -0.4, maxZ: 0.3 })
    ])
  })
]);

export function interiorByBuilding(buildingId) {
  for (let i = 0; i < INTERIORS.length; i++) {
    if (INTERIORS[i].buildingId === buildingId) return INTERIORS[i];
  }
  return null;
}

function rectHits(x, z, rect, radius) {
  const dx = x - Math.max(rect.minX, Math.min(x, rect.maxX));
  const dz = z - Math.max(rect.minZ, Math.min(z, rect.maxZ));
  const inset = radius || 0;
  return dx * dx + dz * dz < inset * inset;
}

/** 室內地板內縮後可站，且不撞該房間的家具。不看島上的 walkable／blocked。 */
export function interiorCanStand(x, z, room, radius) {
  if (!room || !room.floor) return false;
  const r = radius || 0;
  const floor = room.floor;
  if (x < floor.minX + r || x > floor.maxX - r || z < floor.minZ + r || z > floor.maxZ - r) return false;
  const blocks = room.blocks || [];
  for (let i = 0; i < blocks.length; i++) {
    if (rectHits(x, z, blocks[i], r)) return false;
  }
  return true;
}

function resolveInterior(fromX, fromZ, toX, toZ, room, radius) {
  if (interiorCanStand(toX, toZ, room, radius)) return { x: toX, z: toZ };
  if (interiorCanStand(toX, fromZ, room, radius)) return { x: toX, z: fromZ };
  if (interiorCanStand(fromX, toZ, room, radius)) return { x: fromX, z: toZ };
  return { x: fromX, z: fromZ };
}

export function stepInInterior(pos, input, theta, dt, room, radius) {
  const stand = radius == null ? 0.29 : radius;
  if (!pos || !input || !input.moving || !room) {
    return { x: pos ? pos.x : 0, z: pos ? pos.z : 0, yaw: pos ? pos.yaw : 0, moving: false };
  }
  const dir = cameraRelative(input, theta);
  const step = PLAYER_SPEED * Math.min(Math.max(dt, 0), MAX_STEP_DT);
  const next = resolveInterior(pos.x, pos.z, pos.x + dir.x * step, pos.z + dir.z * step, room, stand);
  return { x: next.x, z: next.z, yaw: Math.atan2(dir.x, dir.z), moving: true };
}

export function createInteriorSession(list) {
  const defs = list || INTERIORS;
  let active = null;
  let cooldownUntil = 0;

  function find(buildingId) {
    for (let i = 0; i < defs.length; i++) {
      if (defs[i].buildingId === buildingId) return defs[i];
    }
    return null;
  }

  return {
    inside() { return !!active; },
    id() { return active ? active.id : null; },
    buildingId() { return active ? active.buildingId : null; },
    paused() { return !!(active && active.paused); },
    blocks() { return active ? active.blocks : null; },
    room() { return active ? active.def : null; },
    canMove() { return !!(active && !active.paused); },
    canEnter(buildingId, now) {
      if (active) return false;
      if (now < cooldownUntil) return false;
      return !!find(buildingId);
    },
    enter(buildingId, now) {
      if (active) return { ok: false, reason: 'inside' };
      if (now < cooldownUntil) return { ok: false, reason: 'cooldown' };
      const def = find(buildingId);
      if (!def) return { ok: false, reason: 'missing' };
      active = {
        id: def.id,
        buildingId: def.buildingId,
        def: def,
        x: def.spawn.x,
        z: def.spawn.z,
        yaw: def.spawn.yaw,
        paused: false,
        blocks: def.blocks
      };
      return {
        ok: true,
        pose: { x: def.spawn.x, z: def.spawn.z, yaw: def.spawn.yaw }
      };
    },
    exit(now) {
      if (!active) return { ok: false, reason: 'outside' };
      const pose = {
        x: active.def.outside.x,
        z: active.def.outside.z,
        yaw: active.def.outside.yaw
      };
      active = null;
      cooldownUntil = (now || 0) + ENTER_COOLDOWN_MS;
      return { ok: true, pose: pose };
    },
    setPaused(paused) {
      if (!active) return;
      active.paused = !!paused;
    },
    exitZone() {
      if (!active) return null;
      const exit = active.def.exit;
      return {
        id: active.id + '-exit',
        x: exit.x,
        z: exit.z,
        r: exit.r,
        label: exit.label,
        action: 'exitInterior'
      };
    }
  };
}
