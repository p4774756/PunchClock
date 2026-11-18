// 室內清單與進出狀態（不依賴 Three.js／DOM）。
//
// 一筆室內對一棟 BUILDINGS：
//   id、buildingId、spawn {x,z,yaw}、exit {x,z,r,label}、
//   outside {x,z,yaw}（島上門口外、面向離開建物）、floor、blocks。
// 座標是室內本地座標。INTERIOR_ORIGIN 只做世界平移，不改出生點與碰撞的相對座標。
// 原點放在鏡頭遠平面之外，沒有屋頂的房間轉頭才不會看見霧裡的群島。
//
// 之後加一棟可進入的建築：
//   1. BUILDINGS 加上 entry（門不在正面就用 entry.at，不要改其它建築的門）。
//   2. 在 INTERIORS 登記一筆。
//   3. island.js 的 INTERIOR_MODULES 補上該室內的 dynamic import。

import { cameraRelative, PLAYER_SPEED, MAX_STEP_DT } from './island-explore.js';
import { G } from './island-kit.js';

const LAB_PHI_MIN = 0.32;
const LAB_PHI_MAX = 1.12;
const LAB_FOV_DEG = 25;

function labExploreCameraPlacement(lookAtX, lookAtY, lookAtZ, dist, safePhi, theta, groundY) {
  const horiz = Math.max(0.55, dist * Math.sin(safePhi));
  const cy = lookAtY + dist * Math.cos(safePhi);
  return {
    cx: lookAtX + horiz * Math.sin(theta),
    cy: Math.max(groundY + 0.88, cy),
    cz: lookAtZ + horiz * Math.cos(theta),
    horiz: horiz
  };
}

export const ENTER_COOLDOWN_MS = 700;
// 與 island.js 的 PerspectiveCamera far、水面圓半徑對齊。跟隨鏡頭最多再往島拉一段。
export const ISLAND_CAMERA_FAR = 560;
export const ISLAND_VIEW_REACH = 180;
export const INTERIOR_CAMERA_PULL = 16;
export const INTERIOR_ORIGIN = Object.freeze({ x: 0, z: -900 });

export const INTERIOR_HINT_EXPLORE_DESK = 'WASD 或方向鍵移動 · 拖曳旋轉鏡頭';
export const INTERIOR_HINT_EXPLORE_TOUCH = '用左下角搖桿移動 · 拖曳旋轉鏡頭';
export const INTERIOR_HINT_INSIDE_DESK = 'WASD 或方向鍵移動 · Esc 或走到門口離開 · 工具列可一鍵離開';
export const INTERIOR_HINT_INSIDE_TOUCH = '搖桿移動 · 按右下「離開」出去';

/** 研發樓進房預設鏡頭（桌面／橫向）：南牆門口與進度螢幕同時入鏡。 */
export const LAB_INTERIOR_CAMERA = Object.freeze({ radius: 3.75, theta: 0.05, phi: 1.12 });

export const LAB_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 6.65, theta: 0, phi: 1.12 });

/** 鏡頭注視點：南牆門與螢幕之間，不跟著角色胸口偏移。 */
export const LAB_INTERIOR_LOOK_DESKTOP = Object.freeze({ x: -0.38, y: G + 1.08, z: -1.55 });
export const LAB_INTERIOR_LOOK_MOBILE = Object.freeze({ x: -0.5, y: G + 1.08, z: -1.55 });

/** 右上角工具列＋品牌區下緣到螢幕上緣至少約 24px（與 viewport 高度換算 NDC）。 */
export const LAB_INTERIOR_TOOLBAR_INSET_PX = 112;

const LAB_SCREEN = Object.freeze({
  cx: 0,
  cy: G + 0.84,
  cz: -1.74,
  hw: 0.795,
  hh: 0.56,
  frame: 0.12
});

const LAB_DOOR = Object.freeze({
  x: -1.5,
  lintelY: G + 1.65,
  signY: G + 1.56,
  signX: -1.0,
  pillarY: G + 1.55,
  pillarHalfW: 0.47,
  matY: G + 0.14,
  matZ: -1.42,
  southZ: -1.85
});

export function labInteriorCameraForAspect(aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  return a < 0.72 ? LAB_INTERIOR_CAMERA_MOBILE : LAB_INTERIOR_CAMERA;
}

export function interiorFollowPose(room, aspect) {
  if (room && room.id === 'lab-room') return labInteriorCameraForAspect(aspect);
  return null;
}

/** 研發樓室內跟隨距離：手機直向不拉回太遠，避免螢幕與門口被切掉。 */
export function labInteriorFollowDistance(radius, aspect) {
  let dist = radius * 1.07;
  if (aspect < 1.02) {
    dist *= Math.min(1.22, Math.sqrt(1.02 / Math.max(aspect, 0.45)));
  }
  return dist;
}

export function labInteriorLookTarget(_room, _player, aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  return a < 0.72 ? LAB_INTERIOR_LOOK_MOBILE : LAB_INTERIOR_LOOK_DESKTOP;
}

export function labInteriorScreenTopNdcLimit(viewportHeightPx) {
  const h = viewportHeightPx == null || viewportHeightPx < 1 ? 900 : viewportHeightPx;
  return Math.min(0.85, 1 - (2 * LAB_INTERIOR_TOOLBAR_INSET_PX) / h);
}

function labFovFactor() {
  return 1 / Math.tan((LAB_FOV_DEG * Math.PI / 180) / 2);
}

/** 世界座標 → NDC（與探索鏡頭放置、25° FOV 一致）。 */
export function labInteriorProjectNdc(worldX, worldY, worldZ, pose, lookAt, aspect) {
  const dist = labInteriorFollowDistance(pose.radius, aspect);
  const phi = Math.min(LAB_PHI_MAX, Math.max(LAB_PHI_MIN, pose.phi));
  const placement = labExploreCameraPlacement(
    lookAt.x, lookAt.y, lookAt.z, dist, phi, pose.theta, G
  );
  const eyeX = placement.cx;
  const eyeY = placement.cy;
  const eyeZ = placement.cz;
  const dx = eyeX - lookAt.x;
  const dy = eyeY - lookAt.y;
  const dz = eyeZ - lookAt.z;
  const len = Math.hypot(dx, dy, dz) || 1;
  const zx = dx / len;
  const zy = dy / len;
  const zz = dz / len;
  let ux = 0;
  let uy = 1;
  let uz = 0;
  let xx = uy * zz - uz * zy;
  let xy = uz * zx - ux * zz;
  let xz = ux * zy - uy * zx;
  const xlen = Math.hypot(xx, xy, xz) || 1;
  xx /= xlen;
  xy /= xlen;
  xz /= xlen;
  const yx = zy * xz - zz * xy;
  const yy = zz * xx - zx * xz;
  const yz = zx * xy - zy * xx;
  const wx = worldX - eyeX;
  const wy = worldY - eyeY;
  const wz = worldZ - eyeZ;
  const vx = wx * xx + wy * xy + wz * xz;
  const vy = wx * yx + wy * yy + wz * yz;
  const vz = -(wx * zx + wy * zy + wz * zz);
  if (vz <= 0.001) return { x: 99, y: 99, vz: vz };
  const f = labFovFactor();
  const safeAspect = aspect == null || aspect < 1e-3 ? 1 : aspect;
  return {
    x: (vx / vz) * f / safeAspect,
    y: (vy / vz) * f,
    vz: vz
  };
}

function labLandmarkPoints() {
  const s = LAB_SCREEN;
  const d = LAB_DOOR;
  return {
    scrTL: [s.cx - s.hw - s.frame, s.cy + s.hh + s.frame, s.cz],
    scrTR: [s.cx + s.hw + s.frame, s.cy + s.hh + s.frame, s.cz],
    scrBL: [s.cx - s.hw - s.frame, s.cy - s.hh - s.frame, s.cz],
    scrBR: [s.cx + s.hw + s.frame, s.cy - s.hh - s.frame, s.cz],
    title: [s.cx - s.hw + 0.25, s.cy + s.hh - 0.14, s.cz],
    lintel: [d.x, d.lintelY, d.southZ],
    doorSign: [d.signX, d.signY, d.southZ - 0.03],
    pillarL: [d.x - d.pillarHalfW, d.pillarY, d.southZ],
    pillarR: [d.x + d.pillarHalfW, d.pillarY, d.southZ],
    mat: [d.signX, d.matY, d.matZ]
  };
}

/**
 * 依 viewport 寬高比與高度檢查研發樓預設鏡頭：螢幕上緣留白、門口標示可見、手機螢幕至少 2/3 入鏡。
 */
export function labInteriorFramingOk(aspect, viewportHeightPx) {
  const pose = labInteriorCameraForAspect(aspect);
  const look = aspect < 0.72 ? LAB_INTERIOR_LOOK_MOBILE : LAB_INTERIOR_LOOK_DESKTOP;
  const lim = labInteriorScreenTopNdcLimit(viewportHeightPx);
  const pts = labLandmarkPoints();
  const out = {};
  for (const key of Object.keys(pts)) {
    const p = pts[key];
    out[key] = labInteriorProjectNdc(p[0], p[1], p[2], pose, look, aspect);
  }
  const span = out.scrTL.y - out.scrBL.y;
  if (out.scrTL.y > lim + 0.001 || out.scrTR.y > lim + 0.001 || out.title.y > lim + 0.001) {
    return false;
  }
  if (span < (aspect < 0.72 ? 0.52 : 0.78)) return false;
  const inside = (ndc) => ndc.vz > 0
    && ndc.y >= -0.94 && ndc.y <= 0.98
    && ndc.x >= -0.94 && ndc.x <= 0.94;
  if (!inside(out.title)) return false;
  if (aspect < 0.72) {
    const vis = (Math.min(out.scrTR.x, 0.92) - Math.max(out.scrTL.x, -0.92))
      / (out.scrTR.x - out.scrTL.x);
    if (!Number.isFinite(vis) || vis < 0.66) return false;
    if (!inside(out.doorSign) || !inside(out.pillarR) || !inside(out.mat)) return false;
    return true;
  }
  const screenKeys = ['scrTL', 'scrTR', 'scrBL', 'scrBR', 'title'];
  for (let i = 0; i < screenKeys.length; i++) {
    if (!inside(out[screenKeys[i]])) return false;
  }
  if (!inside(out.lintel) || !inside(out.doorSign) || !inside(out.pillarR)) return false;
  if (out.pillarL.x < -1.1) return false;
  return inside(out.mat);
}

export function shouldShowInteriorLeave(ctx) {
  if (!ctx) return false;
  return !!(ctx.inside && ctx.exploring && !ctx.busy && !ctx.overlay);
}

export function interiorLeaveButtonLabel(session) {
  if (!session || typeof session.inside !== 'function' || !session.inside()) return '';
  if (typeof session.exitZone !== 'function') return '離開室內';
  const zone = session.exitZone();
  return zone && zone.label ? zone.label : '離開室內';
}

export function exploreHintText(inside, touch) {
  if (inside) return touch ? INTERIOR_HINT_INSIDE_TOUCH : INTERIOR_HINT_INSIDE_DESK;
  return touch ? INTERIOR_HINT_EXPLORE_TOUCH : INTERIOR_HINT_EXPLORE_DESK;
}

/** 室內原點離島夠遠：鏡頭往島拉近後，島上最靠近的點仍在遠平面外。 */
export function interiorClearsFarPlane(origin, far, reach, pull) {
  const point = origin || INTERIOR_ORIGIN;
  const dist = Math.hypot(point.x || 0, point.z || 0);
  const limit = far == null ? ISLAND_CAMERA_FAR : far;
  const span = reach == null ? ISLAND_VIEW_REACH : reach;
  const toward = pull == null ? INTERIOR_CAMERA_PULL : pull;
  return dist - span - toward > limit;
}

/** 轉場（interiorBusy）或已在室內時，島上 data-anchor 標籤要藏起來，避免浮在淡出黑幕上。 */
export function shouldHideIslandLabels(inside, busy) {
  return !!(inside || busy);
}

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
  }),
  Object.freeze({
    id: 'lab-room',
    buildingId: 'computer-site',
    spawn: Object.freeze({ x: 0.05, z: 0.35, yaw: Math.PI }),
    exit: Object.freeze({ x: -1.5, z: -1.45, r: 0.58, label: '離開研發樓' }),
    outside: Object.freeze({ x: 0.45, z: 14.82, yaw: Math.PI }),
    floor: Object.freeze({ minX: -2.15, maxX: 2.15, minZ: -1.8, maxZ: 1.9 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'rack', minX: -2.05, maxX: -1.2, minZ: 0.55, maxZ: 1.45 }),
      Object.freeze({ id: 'bench', minX: 1.05, maxX: 2.05, minZ: -0.55, maxZ: 0.35 })
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
