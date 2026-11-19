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
import { G, P } from './island-kit.js';
import { MILU_ANIM } from './island-milu.js';
import { tcmReturnPose } from './island-tcm-visit.js';

const LAB_PHI_MIN = 0.32;
const LAB_PHI_MAX = 1.50;
export const LAB_FOV_DEG = 42;
export const DEFAULT_INTERIOR_FOV_DEG = 25;

export const CASINO_FOV_DEG = 42;
export const TCM_FOV_DEG = 42;
export const SHOP_FOV_DEG = 42;

export function interiorCameraFov(room) {
  if (room && room.id === 'lab-room') return LAB_FOV_DEG;
  if (room && room.id === 'casino-room') return CASINO_FOV_DEG;
  if (room && room.id === 'tcm-room') return TCM_FOV_DEG;
  if (room && room.id === 'shop-room') return SHOP_FOV_DEG;
  return DEFAULT_INTERIOR_FOV_DEG;
}

/** 與 island-camera.js exploreCameraPlacement 相同（研發樓 phi≥0.70，無抬頭俯仰混合）。 */
function labExploreCameraPlacement(lookAtX, lookAtY, lookAtZ, dist, safePhi, theta, groundY) {
  const horiz = Math.max(0.55, dist * Math.sin(safePhi));
  const cy = Math.max(groundY + 0.88, lookAtY + dist * Math.cos(safePhi));
  return {
    cx: lookAtX + horiz * Math.sin(theta),
    cy: cy,
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

export const INTERIOR_HINT_EXPLORE_DESK = 'WASD 或方向鍵移動 · 拖曳旋轉鏡頭 · Esc 退出探索';
export const INTERIOR_HINT_EXPLORE_TOUCH = '用左下角搖桿移動 · 拖曳旋轉鏡頭';
export const INTERIOR_HINT_INSIDE_DESK = 'WASD 或方向鍵移動 · Esc 或走到門口離開 · 工具列可一鍵離開';
export const INTERIOR_HINT_INSIDE_TOUCH = '搖桿移動 · 按右下「離開」出去';

/** 研發樓進房預設鏡頭（桌面／橫向）：站在南門內側往北看大廳，避開門框與中央數據柱。 */
export const LAB_INTERIOR_CAMERA = Object.freeze({ radius: 6.8, theta: Math.PI + 0.36, phi: 1.46 });

export const LAB_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 6.0, theta: Math.PI + 0.32, phi: 1.45 });

/** 鏡頭大廳焦點（桌面）；每幀與角色胸口混合，讓進房先看到挑高大廳而不是南牆特寫。 */
export const LAB_INTERIOR_LOOK_DESKTOP = Object.freeze({ x: 1.65, y: G + 1.55, z: 6.6 });
export const LAB_INTERIOR_LOOK_MOBILE = Object.freeze({ x: 1.2, y: G + 1.4, z: 5.8 });
/** 門口附近注視混合（朝胸口）放輕，避免把焦點拉回南牆、鏡頭穿牆。 */
export const LAB_INTERIOR_LOOK_CHEST_BLEND = 0.18;
export const LAB_INTERIOR_LOOK_DEPTH_BLEND = 0.12;
export const LAB_INTERIOR_LOOK_BLEND_MAX = 0.88;

/** 右上角工具列＋品牌區下緣到螢幕上緣至少約 24px（與 viewport 高度換算 NDC）。 */
export const LAB_INTERIOR_TOOLBAR_INSET_PX = 80;

const LAB_SCREEN = Object.freeze({
  cx: 0,
  cy: G + 0.86,
  cz: -1.74,
  hw: 0.95,
  hh: 0.64,
  frame: 0.12
});

const LAB_DOOR = Object.freeze({
  x: -1.5,
  doorW: 0.88,
  lintelY: G + 1.65,
  signY: G + 1.56,
  signX: -1.0,
  pillarY: G + 1.55,
  pillarHalfW: 0.47,
  matY: G + 0.14,
  matZ: -1.42,
  southZ: -1.85
});

/** 與 island-lab-interior.js 碰撞／遮擋對齊；挑高視覺另有吊顶，北牆剖面後不列入可見遮擋。 */
export const LAB_INTERIOR_WALL = Object.freeze({ h: 4.6, t: 0.12 });
export const INTERIOR_WALL_HEIGHT = LAB_INTERIOR_WALL.h;
export const INTERIOR_CAMERA_MARGIN_M = 3;
export const INTERIOR_CAMERA_HEIGHT_ABOVE_WALL = 1.5;

/** 室內地板 AABB 向外擴張（XZ）與牆高 + 上緩衝（Y），供鏡頭約束測試。 */
export function interiorExpandedBounds(room, marginM, heightAboveWall) {
  if (!room || !room.floor) return null;
  const margin = marginM == null ? INTERIOR_CAMERA_MARGIN_M : marginM;
  const above = heightAboveWall == null ? INTERIOR_CAMERA_HEIGHT_ABOVE_WALL : heightAboveWall;
  const f = room.floor;
  return Object.freeze({
    minX: f.minX - margin,
    maxX: f.maxX + margin,
    minZ: f.minZ - margin,
    maxZ: f.maxZ + margin,
    minY: G,
    maxY: G + INTERIOR_WALL_HEIGHT + above
  });
}

export function interiorCameraWithinBounds(eye, bounds) {
  if (!eye || !bounds) return false;
  const ex = eye.x != null ? eye.x : eye.cx;
  const ey = eye.y != null ? eye.y : eye.cy;
  const ez = eye.z != null ? eye.z : eye.cz;
  return ex >= bounds.minX && ex <= bounds.maxX
    && ez >= bounds.minZ && ez <= bounds.maxZ
    && ey >= bounds.minY && ey <= bounds.maxY;
}

/**
 * 以預設進房鏡頭為中心再擴張 marginM（含 Z 北側剖面外），
 * 高度上限為牆高+heightAbove 與預設鏡頭高度取較大者。
 */
export function interiorLiftBoundsFromRest(room, restEye, marginM, heightAboveWall) {
  const base = interiorExpandedBounds(room, marginM, heightAboveWall);
  if (!base || !restEye) return base;
  const margin = marginM == null ? INTERIOR_CAMERA_MARGIN_M : marginM;
  const ex = restEye.x != null ? restEye.x : restEye.cx;
  const ey = restEye.y != null ? restEye.y : restEye.cy;
  const ez = restEye.z != null ? restEye.z : restEye.cz;
  if (![ex, ey, ez].every(Number.isFinite)) return base;
  return Object.freeze({
    minX: Math.min(base.minX, ex - margin),
    maxX: Math.max(base.maxX, ex + margin),
    minZ: Math.min(base.minZ, ez - margin),
    maxZ: Math.max(base.maxZ, ez + margin),
    minY: base.minY,
    maxY: Math.max(base.maxY, ey + 0.25)
  });
}

export const LAB_INTERIOR_PLAYER_CHEST_Y = G + 1.05;
export const LAB_INTERIOR_PLAYER_FEET_Y = P;
export const LAB_INTERIOR_PLAYER_HEAD_Y = P + MILU_ANIM.height;

export function labInteriorCameraForAspect(aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  if (a < 0.72) return LAB_INTERIOR_CAMERA_MOBILE;
  if (a > 1.72) {
    return Object.freeze({ radius: 6.9, theta: LAB_INTERIOR_CAMERA.theta, phi: 1.46 });
  }
  return LAB_INTERIOR_CAMERA;
}

export function interiorFollowPose(room, aspect) {
  if (room && room.id === 'lab-room') return labInteriorCameraForAspect(aspect);
  if (room && room.id === 'casino-room') return casinoInteriorCameraForAspect(aspect);
  if (room && room.id === 'tcm-room') return tcmInteriorCameraForAspect(aspect);
  if (room && room.id === 'shop-room') return shopInteriorCameraForAspect(aspect);
  return null;
}

/** 賭場進房預設鏡頭：站在南門內側往北看大廳的吊燈、拉霸列與 21 點桌。 */
export const CASINO_INTERIOR_CAMERA = Object.freeze({ radius: 5.75, theta: Math.PI, phi: 1.35 });
export const CASINO_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 3.7, theta: Math.PI, phi: 1.32 });
export const CASINO_INTERIOR_LOOK = Object.freeze({ x: 0.35, y: G + 2.0, z: 7.2 });
export const CASINO_INTERIOR_LOOK_MOBILE = Object.freeze({ x: 0.25, y: G + 1.9, z: 6.4 });

export function casinoInteriorCameraForAspect(aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  if (a < 0.72) return CASINO_INTERIOR_CAMERA_MOBILE;
  if (a > 1.72) {
    return Object.freeze({ radius: 5.9, theta: CASINO_INTERIOR_CAMERA.theta, phi: CASINO_INTERIOR_CAMERA.phi });
  }
  return CASINO_INTERIOR_CAMERA;
}

export function casinoInteriorLookTarget(room, player, aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  return a < 0.72 ? CASINO_INTERIOR_LOOK_MOBILE : CASINO_INTERIOR_LOOK;
}

export function casinoInteriorCameraEye(pose, look, aspect) {
  const used = pose || casinoInteriorCameraForAspect(aspect);
  const dist = labInteriorFollowDistance(used.radius, aspect);
  const place = labExploreCameraPlacement(look.x, look.y, look.z, dist, used.phi, used.theta, G);
  return Object.freeze({ x: place.cx, y: place.cy, z: place.cz });
}

function casinoInteriorPlayerLocal(player, origin) {
  const o = origin || INTERIOR_ORIGIN;
  if (!player) return { x: 0, z: 0.9 };
  const x = Number.isFinite(player.x) ? player.x : 0;
  const z = Number.isFinite(player.z) ? player.z : 0.9;
  if (Math.abs(z - o.z) < 80) return { x: x - o.x, z: z - o.z };
  return { x, z };
}

export function casinoInteriorRuntimeLookAt(room, player, aspect, origin) {
  if (!room || room.id !== 'casino-room') return null;
  const o = origin || INTERIOR_ORIGIN;
  const localPlayer = casinoInteriorPlayerLocal(player, o);
  const focal = casinoInteriorLookTarget(room, localPlayer, aspect);
  return Object.freeze({
    x: focal.x + o.x,
    y: focal.y,
    z: focal.z + o.z
  });
}

/** 推拿館進房預設鏡頭：站在南門內側往北看暖色醫館、床帳與經絡研習室門。 */
export const TCM_INTERIOR_CAMERA = Object.freeze({ radius: 5.75, theta: Math.PI, phi: 1.35 });
export const TCM_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 3.7, theta: Math.PI, phi: 1.32 });
export const TCM_INTERIOR_LOOK = Object.freeze({ x: 0.25, y: G + 2.0, z: 6.6 });
export const TCM_INTERIOR_LOOK_MOBILE = Object.freeze({ x: 0.2, y: G + 1.9, z: 5.8 });

export function tcmInteriorCameraForAspect(aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  if (a < 0.72) return TCM_INTERIOR_CAMERA_MOBILE;
  if (a > 1.72) {
    return Object.freeze({ radius: 5.9, theta: TCM_INTERIOR_CAMERA.theta, phi: TCM_INTERIOR_CAMERA.phi });
  }
  return TCM_INTERIOR_CAMERA;
}

export function tcmInteriorLookTarget(room, player, aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  return a < 0.72 ? TCM_INTERIOR_LOOK_MOBILE : TCM_INTERIOR_LOOK;
}

export function tcmInteriorCameraEye(pose, look, aspect) {
  const used = pose || tcmInteriorCameraForAspect(aspect);
  const dist = labInteriorFollowDistance(used.radius, aspect);
  const place = labExploreCameraPlacement(look.x, look.y, look.z, dist, used.phi, used.theta, G);
  return Object.freeze({ x: place.cx, y: place.cy, z: place.cz });
}

function tcmInteriorPlayerLocal(player, origin) {
  const o = origin || INTERIOR_ORIGIN;
  if (!player) return { x: 0, z: 0.9 };
  const x = Number.isFinite(player.x) ? player.x : 0;
  const z = Number.isFinite(player.z) ? player.z : 0.9;
  if (Math.abs(z - o.z) < 80) return { x: x - o.x, z: z - o.z };
  return { x, z };
}

export function tcmInteriorRuntimeLookAt(room, player, aspect, origin) {
  if (!room || room.id !== 'tcm-room') return null;
  const o = origin || INTERIOR_ORIGIN;
  const localPlayer = tcmInteriorPlayerLocal(player, o);
  const focal = tcmInteriorLookTarget(room, localPlayer, aspect);
  return Object.freeze({
    x: focal.x + o.x,
    y: focal.y,
    z: focal.z + o.z
  });
}

/** 神之島進房預設鏡頭：站在南門內側往北看吧台、座位與菜單燈箱。 */
export const SHOP_INTERIOR_CAMERA = Object.freeze({ radius: 5.75, theta: Math.PI, phi: 1.35 });
export const SHOP_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 3.7, theta: Math.PI, phi: 1.32 });
export const SHOP_INTERIOR_LOOK = Object.freeze({ x: 0.3, y: G + 2.0, z: 6.2 });
export const SHOP_INTERIOR_LOOK_MOBILE = Object.freeze({ x: 0.2, y: G + 1.9, z: 5.4 });

export function shopInteriorCameraForAspect(aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  if (a < 0.72) return SHOP_INTERIOR_CAMERA_MOBILE;
  if (a > 1.72) {
    return Object.freeze({ radius: 5.9, theta: SHOP_INTERIOR_CAMERA.theta, phi: SHOP_INTERIOR_CAMERA.phi });
  }
  return SHOP_INTERIOR_CAMERA;
}

export function shopInteriorLookTarget(room, player, aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  return a < 0.72 ? SHOP_INTERIOR_LOOK_MOBILE : SHOP_INTERIOR_LOOK;
}

export function shopInteriorCameraEye(pose, look, aspect) {
  const used = pose || shopInteriorCameraForAspect(aspect);
  const dist = labInteriorFollowDistance(used.radius, aspect);
  const place = labExploreCameraPlacement(look.x, look.y, look.z, dist, used.phi, used.theta, G);
  return Object.freeze({ x: place.cx, y: place.cy, z: place.cz });
}

function shopInteriorPlayerLocal(player, origin) {
  const o = origin || INTERIOR_ORIGIN;
  if (!player) return { x: 0, z: 0.9 };
  const x = Number.isFinite(player.x) ? player.x : 0;
  const z = Number.isFinite(player.z) ? player.z : 0.9;
  if (Math.abs(z - o.z) < 80) return { x: x - o.x, z: z - o.z };
  return { x, z };
}

export function shopInteriorRuntimeLookAt(room, player, aspect, origin) {
  if (!room || room.id !== 'shop-room') return null;
  const o = origin || INTERIOR_ORIGIN;
  const localPlayer = shopInteriorPlayerLocal(player, o);
  const focal = shopInteriorLookTarget(room, localPlayer, aspect);
  return Object.freeze({
    x: focal.x + o.x,
    y: focal.y,
    z: focal.z + o.z
  });
}

/** 研發樓室內跟隨距離：手機直向不拉回太遠，避免螢幕與門口被切掉。 */
export function labInteriorFollowDistance(radius, aspect) {
  let dist = radius * 1.07;
  if (aspect < 1.02) {
    dist *= Math.min(1.22, Math.sqrt(1.02 / Math.max(aspect, 0.45)));
  }
  return dist;
}

function labInteriorBlendLookAt(focal, player, blend) {
  if (!player || blend <= 0) return focal;
  const px = Number.isFinite(player.x) ? player.x : focal.x;
  const pz = Number.isFinite(player.z) ? player.z : focal.z;
  return Object.freeze({
    x: focal.x + (px - focal.x) * blend,
    y: focal.y + (LAB_INTERIOR_PLAYER_CHEST_Y - focal.y) * blend,
    z: focal.z + (pz - focal.z) * blend
  });
}

/** 桌面：lerp(焦點, 胸口)；門口 ≈0.45，越往北愈高。 */
export function labInteriorChestBlend(room, player) {
  if (!player || !Number.isFinite(player.z)) return LAB_INTERIOR_LOOK_CHEST_BLEND;
  const doorZ = room && room.exit ? room.exit.z : -1.45;
  const northZ = room && room.floor
    ? Math.min(room.floor.maxZ, room.spawn.z + 4.35)
    : 6;
  const span = northZ - doorZ;
  if (!(span > 0.1)) return LAB_INTERIOR_LOOK_CHEST_BLEND;
  const depth = Math.max(0, player.z - doorZ) / span;
  return Math.min(
    LAB_INTERIOR_LOOK_BLEND_MAX,
    LAB_INTERIOR_LOOK_CHEST_BLEND + depth * LAB_INTERIOR_LOOK_DEPTH_BLEND
  );
}

export function labInteriorLookTarget(room, player, aspect) {
  const a = aspect == null || !Number.isFinite(aspect) ? 1.6 : aspect;
  const focal = a < 0.72 ? LAB_INTERIOR_LOOK_MOBILE : LAB_INTERIOR_LOOK_DESKTOP;
  if (a < 0.72) return focal;
  return labInteriorBlendLookAt(focal, player, labInteriorChestBlend(room, player));
}

function labInteriorPlayerLocal(player, origin) {
  if (!player) return player;
  const o = origin || INTERIOR_ORIGIN;
  const pz = player.z;
  if (!Number.isFinite(pz)) return player;
  const worldIndoor = Math.abs(pz - o.z) < 24;
  if (!worldIndoor) return player;
  return {
    x: Number.isFinite(player.x) ? player.x - o.x : player.x,
    z: pz - o.z
  };
}

/** 與 island.js follow／每幀 targetLook 相同：室內焦點平移到世界座標。 */
export function labInteriorRuntimeLookAt(room, player, aspect, origin) {
  if (!room || room.id !== 'lab-room') return null;
  const o = origin || INTERIOR_ORIGIN;
  const localPlayer = labInteriorPlayerLocal(player, o);
  const focal = labInteriorLookTarget(room, localPlayer, aspect);
  return Object.freeze({
    x: focal.x + o.x,
    y: focal.y,
    z: focal.z + o.z
  });
}

export function labInteriorScreenTopNdcLimit(viewportHeightPx) {
  const h = viewportHeightPx == null || viewportHeightPx < 1 ? 900 : viewportHeightPx;
  return Math.min(0.85, 1 - (2 * LAB_INTERIOR_TOOLBAR_INSET_PX) / h);
}

function labFovFactor() {
  return 1 / Math.tan((LAB_FOV_DEG * Math.PI / 180) / 2);
}

/** 可見牆面 AABB（min／max 含 x,y,z），供視線遮擋測試；不含北牆剖面與門洞。 */
export function labInteriorVisibleWallAABBs(floor) {
  if (!floor) return [];
  const wt = LAB_INTERIOR_WALL.t;
  const wh = LAB_INTERIOR_WALL.h;
  const y0 = G;
  const y1 = G + wh;
  const cz = (floor.minZ + floor.maxZ) / 2;
  const depth = floor.maxZ - floor.minZ;
  const southZ = floor.minZ - wt / 2;
  const halfDepth = (depth + wt) / 2;
  const doorX = LAB_DOOR.x;
  const halfDoor = LAB_DOOR.doorW / 2;
  const westEnd = doorX - halfDoor;
  const eastStart = doorX + halfDoor;
  const boxes = [];
  const push = (minX, maxX, minZ, maxZ) => {
    boxes.push(Object.freeze({ minX, maxX, minY: y0, maxY: y1, minZ, maxZ }));
  };
  push(floor.minX - wt, floor.minX, cz - halfDepth, cz + halfDepth);
  push(floor.maxX, floor.maxX + wt, cz - halfDepth, cz + halfDepth);
  const kickH = 0.14;
  boxes.push(Object.freeze({
    minX: floor.minX - wt,
    maxX: floor.maxX + wt,
    minY: y0,
    maxY: y0 + kickH,
    minZ: floor.maxZ,
    maxZ: floor.maxZ + wt
  }));
  if (westEnd > floor.minX + 0.02) {
    const w = westEnd - floor.minX;
    const cx = floor.minX + w / 2;
    push(cx - w / 2, cx + w / 2, southZ - wt / 2, southZ + wt / 2);
  }
  if (floor.maxX > eastStart + 0.02) {
    const w = floor.maxX - eastStart;
    const cx = eastStart + w / 2;
    push(cx - w / 2, cx + w / 2, southZ - wt / 2, southZ + wt / 2);
  }
  const lintelHalfW = (LAB_DOOR.doorW + 0.14) / 2;
  boxes.push(Object.freeze({
    minX: doorX - lintelHalfW,
    maxX: doorX + lintelHalfW,
    minY: LAB_DOOR.lintelY - 0.18,
    maxY: LAB_DOOR.lintelY + 0.06,
    minZ: southZ - wt / 2,
    maxZ: southZ + wt / 2
  }));
  return boxes;
}

/** 東／西牆與北側踢腳線：視線檢查南牆立面目標時用（不計入南牆本身）。 */
export function labInteriorSideWallAABBs(floor) {
  const all = labInteriorVisibleWallAABBs(floor);
  if (all.length < 3) return all;
  return all.slice(0, 3);
}

export function segmentIntersectsAabb(ox, oy, oz, tx, ty, tz, box) {
  let tmin = 0;
  let tmax = 1;
  const dx = tx - ox;
  const dy = ty - oy;
  const dz = tz - oz;
  const axes = [
    [dx, ox, box.minX, box.maxX],
    [dy, oy, box.minY, box.maxY],
    [dz, oz, box.minZ, box.maxZ]
  ];
  for (let i = 0; i < 3; i++) {
    const d = axes[i][0];
    const o = axes[i][1];
    const mn = axes[i][2];
    const mx = axes[i][3];
    if (Math.abs(d) < 1e-12) {
      if (o < mn || o > mx) return false;
      continue;
    }
    const inv = 1 / d;
    let t1 = (mn - o) * inv;
    let t2 = (mx - o) * inv;
    if (t1 > t2) {
      const tmp = t1;
      t1 = t2;
      t2 = tmp;
    }
    if (t1 > tmin) tmin = t1;
    if (t2 < tmax) tmax = t2;
    if (tmin > tmax) return false;
  }
  return tmin < 1 && tmax > 1e-4;
}

export function labInteriorCameraEye(pose, lookAt, aspect) {
  const dist = labInteriorFollowDistance(pose.radius, aspect);
  const phi = Math.min(LAB_PHI_MAX, Math.max(LAB_PHI_MIN, pose.phi));
  const placement = labExploreCameraPlacement(
    lookAt.x, lookAt.y, lookAt.z, dist, phi, pose.theta, G
  );
  return { x: placement.cx, y: placement.cy, z: placement.cz };
}

export function labInteriorSightClear(eye, target, wallBoxes) {
  const walls = wallBoxes || [];
  for (let i = 0; i < walls.length; i++) {
    if (segmentIntersectsAabb(eye.x, eye.y, eye.z, target.x, target.y, target.z, walls[i])) {
      return false;
    }
  }
  return true;
}

function labInteriorCameraFloorBounds(floor) {
  if (!floor) return floor;
  const depth = floor.maxZ - floor.minZ;
  if (depth <= 8.5) return floor;
  return Object.freeze({
    minX: floor.minX,
    maxX: floor.maxX,
    minZ: floor.minZ,
    maxZ: floor.maxZ
  });
}

export function labInteriorCameraInsideFloor(eye, floor, inset) {
  if (!floor || !eye) return false;
  const m = inset == null ? 0.15 : inset;
  const bounds = labInteriorCameraFloorBounds(floor);
  return eye.x >= bounds.minX + m
    && eye.x <= bounds.maxX - m
    && eye.z >= bounds.minZ + m
    && eye.z <= bounds.maxZ - m
    && eye.y <= G + LAB_INTERIOR_WALL.h - 0.05;
}

/** 研發樓角落站位：門口、機櫃旁、工作台旁（供鏡頭與遮擋測試）。 */
export function labInteriorPlayerSamplePositions(room) {
  if (!room || room.id !== 'lab-room') return [];
  const northZ = room.floor ? Math.min(room.floor.maxZ - 0.45, 11.5) : 11.5;
  return Object.freeze([
    Object.freeze({ id: 'spawn', x: room.spawn.x, z: room.spawn.z }),
    Object.freeze({ id: 'door', x: room.exit.x, z: room.exit.z }),
    Object.freeze({ id: 'lobby', x: 0.25, z: 1.15 }),
    Object.freeze({ id: 'server', x: 2.45, z: 1.45 }),
    Object.freeze({ id: 'museum', x: -1.35, z: 1.75 }),
    Object.freeze({ id: 'north', x: 0.15, z: Math.min(3.85, northZ) })
  ]);
}

function interiorViewBasis(eyeX, eyeY, eyeZ, lookAt) {
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
  return { zx, zy, zz, xx, xy, xz, yx, yy, yz };
}

/** 已知鏡頭與注視點時，世界座標 → NDC（抬頭 viewLift 測試用）。 */
export function interiorProjectNdcFromEye(eye, lookAt, worldX, worldY, worldZ, aspect) {
  if (!eye || !lookAt) return { x: 99, y: 99, vz: -1 };
  return interiorProjectNdcWithBasis(
    interiorViewBasis(eye.x, eye.y, eye.z, lookAt), eye.x, eye.y, eye.z, worldX, worldY, worldZ, aspect
  );
}

function interiorProjectNdcWithBasis(basis, eyeX, eyeY, eyeZ, worldX, worldY, worldZ, aspect) {
  const { zx, zy, zz, xx, xy, xz, yx, yy, yz } = basis;
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

/** 世界座標 → NDC（與探索鏡頭放置、25° FOV 一致）。 */
export function labInteriorProjectNdc(worldX, worldY, worldZ, pose, lookAt, aspect) {
  const dist = labInteriorFollowDistance(pose.radius, aspect);
  const phi = Math.min(LAB_PHI_MAX, Math.max(LAB_PHI_MIN, pose.phi));
  const placement = labExploreCameraPlacement(
    lookAt.x, lookAt.y, lookAt.z, dist, phi, pose.theta, G
  );
  const basis = interiorViewBasis(placement.cx, placement.cy, placement.cz, lookAt);
  return interiorProjectNdcWithBasis(
    basis, placement.cx, placement.cy, placement.cz, worldX, worldY, worldZ, aspect
  );
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
 * 依 viewport 檢查研發樓預設鏡頭：從南側往北看大廳，不被中央數據柱擋住，角色與大廳焦點在畫面內。
 */
export function labInteriorFramingOk(aspect, viewportHeightPx, room) {
  const pose = labInteriorCameraForAspect(aspect);
  const labRoom = room || INTERIORS.find((r) => r.id === 'lab-room');
  if (!labRoom || !labRoom.floor) return false;
  const player = { x: labRoom.spawn.x, z: labRoom.spawn.z };
  const look = labInteriorLookTarget(labRoom, player, aspect);
  const eye = labInteriorCameraEye(pose, look, aspect);
  if (!(eye.z < look.z - 1)) return false;
  if (!(eye.y < G + LAB_INTERIOR_WALL.h - 0.05)) return false;
  const inRoom = labInteriorCameraInsideFloor(eye, labRoom.floor, 0.05);
  const atDoorThreshold = eye.z >= labRoom.floor.minZ - 2.4 && eye.z <= labRoom.floor.minZ + 0.4
    && eye.x >= -2.05 && eye.x <= -0.95 && eye.y >= G + 0.8;
  if (!inRoom && !atDoorThreshold) return false;

  const pillar = (labRoom.blocks || []).find((b) => b.id === 'data-pillar');
  if (pillar && segmentIntersectsAabb(
    eye.x, eye.y, eye.z, look.x, look.y, look.z,
    { minX: pillar.minX, maxX: pillar.maxX, minY: G, maxY: G + LAB_INTERIOR_WALL.h, minZ: pillar.minZ, maxZ: pillar.maxZ }
  )) {
    return false;
  }
  const walls = labInteriorVisibleWallAABBs(labRoom.floor);
  if (!labInteriorSightClear(eye, { x: look.x, y: look.y, z: look.z }, walls)) return false;

  const insideHead = (ndc) => ndc.vz > 0.02
    && ndc.y >= -1.16 && ndc.y <= 0.98
    && ndc.x >= -0.98 && ndc.x <= 0.98;
  const insideFeet = (ndc) => ndc.vz > 0.02
    && ndc.y >= -1.85 && ndc.y <= 0.98
    && ndc.x >= -0.98 && ndc.x <= 0.98;
  const head = labInteriorProjectNdc(
    player.x, LAB_INTERIOR_PLAYER_HEAD_Y, player.z, pose, look, aspect
  );
  const feet = labInteriorProjectNdc(
    player.x, LAB_INTERIOR_PLAYER_FEET_Y, player.z, pose, look, aspect
  );
  if (!insideHead(head) || !insideFeet(feet)) return false;

  const hall = labInteriorProjectNdc(0.9, G + 2.2, 6.1, pose, look, aspect);
  const rack = labInteriorProjectNdc(6.3, G + 1.1, 5.4, pose, look, aspect);
  if (hall.vz <= 0.02) return false;
  if (!(insideHead(hall) || (rack.vz > 0.02 && rack.x < 0.98))) return false;
  return true;
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
    spawn: Object.freeze({ x: 0, z: 0.9, yaw: 0 }),
    exit: Object.freeze({ x: 0, z: -1.35, r: 0.58, label: '離開神之島' }),
    outside: Object.freeze({ x: -3.86, z: -1.4, yaw: -Math.PI / 2 }),
    floor: Object.freeze({ minX: -8.0, maxX: 8.0, minZ: -2.0, maxZ: 10.0 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'bar', minX: -4.7, maxX: 4.7, minZ: 8.2, maxZ: 9.55 }),
      Object.freeze({ id: 'seat-west-a', minX: -6.3, maxX: -4.3, minZ: 2.0, maxZ: 4.0 }),
      Object.freeze({ id: 'seat-west-b', minX: -6.3, maxX: -4.3, minZ: 4.8, maxZ: 6.8 }),
      Object.freeze({ id: 'seat-east-a', minX: 4.3, maxX: 6.3, minZ: 2.0, maxZ: 4.0 }),
      Object.freeze({ id: 'seat-east-b', minX: 4.3, maxX: 6.3, minZ: 4.8, maxZ: 6.8 }),
      Object.freeze({ id: 'fridge', minX: 6.4, maxX: 7.3, minZ: 7.1, maxZ: 9.35 })
    ])
  }),
  Object.freeze({
    id: 'lab-room',
    buildingId: 'computer-site',
    spawn: Object.freeze({ x: 0.15, z: 1.08, yaw: Math.PI }),
    exit: Object.freeze({ x: -1.5, z: -1.45, r: 0.58, label: '離開研發樓' }),
    outside: Object.freeze({ x: 0.45, z: 14.82, yaw: Math.PI }),
    floor: Object.freeze({ minX: -8.5, maxX: 8.5, minZ: -1.8, maxZ: 12.2 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'data-pillar', minX: -0.55, maxX: 0.55, minZ: 4.2, maxZ: 5.45 }),
      Object.freeze({ id: 'rack-a', minX: 5.05, maxX: 6.25, minZ: 1.4, maxZ: 2.85 }),
      Object.freeze({ id: 'rack-b', minX: 5.05, maxX: 6.25, minZ: 3.15, maxZ: 4.6 }),
      Object.freeze({ id: 'rack-c', minX: 5.05, maxX: 6.25, minZ: 4.9, maxZ: 6.35 }),
      Object.freeze({ id: 'rack-d', minX: 5.05, maxX: 6.25, minZ: 6.65, maxZ: 8.1 }),
      Object.freeze({ id: 'pdu-row', minX: 6.45, maxX: 7.15, minZ: 1.8, maxZ: 7.8 }),
      Object.freeze({ id: 'exhibit-a', minX: -7.05, maxX: -5.65, minZ: 7.2, maxZ: 8.35 }),
      Object.freeze({ id: 'exhibit-b', minX: -7.05, maxX: -5.65, minZ: 9.1, maxZ: 10.25 }),
      Object.freeze({ id: 'exhibit-c', minX: -7.05, maxX: -5.65, minZ: 11.0, maxZ: 12.15 }),
      Object.freeze({ id: 'exhibit-d', minX: -4.85, maxX: -3.45, minZ: 9.0, maxZ: 10.15 })
    ])
  }),
  Object.freeze({
    id: 'casino-room',
    buildingId: 'casino',
    spawn: Object.freeze({ x: 0, z: 0.9, yaw: 0 }),
    exit: Object.freeze({ x: 0, z: -1.4, r: 0.55, label: '離開賭場' }),
    outside: Object.freeze({ x: 13.12, z: -7.35, yaw: Math.PI / 2 }),
    floor: Object.freeze({ minX: -8.5, maxX: 8.5, minZ: -2.0, maxZ: 14.0 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'slots-row', minX: -7.7, maxX: -4.85, minZ: 0.2, maxZ: 11.4 }),
      Object.freeze({ id: 'roulette', minX: -1.25, maxX: 1.25, minZ: 8.35, maxZ: 10.75 }),
      Object.freeze({ id: 'bj-table', minX: 3.45, maxX: 5.55, minZ: 3.55, maxZ: 5.25 }),
      Object.freeze({ id: 'chip-bar', minX: 3.35, maxX: 5.35, minZ: 8.85, maxZ: 10.15 })
    ]),
    interacts: Object.freeze([
      Object.freeze({ id: 'casino-bj', x: 3.05, z: 4.4, r: 0.58, label: '玩 21 點', action: 'openCasinoBlackjack' }),
      Object.freeze({ id: 'casino-slots', x: -4.25, z: 4.2, r: 0.62, label: '拉霸機', action: 'casinoSlots' }),
      Object.freeze({ id: 'casino-roulette', x: 0, z: 7.7, r: 0.52, label: '輪盤桌', action: 'casinoRoulette' })
    ])
  }),
  Object.freeze({
    id: 'tcm-room',
    buildingId: 'tcm-site',
    spawn: Object.freeze({ x: 0, z: 0.9, yaw: 0 }),
    exit: Object.freeze({ x: 0, z: -1.4, r: 0.55, label: '離開推拿館' }),
    outside: Object.freeze((() => {
      const pose = tcmReturnPose();
      return pose || { x: -14.8, z: 1.15, yaw: Math.PI / 2 };
    })()),
    floor: Object.freeze({ minX: -8.0, maxX: 8.0, minZ: -2.0, maxZ: 12.5 }),
    blocks: Object.freeze([
      Object.freeze({ id: 'bed-west-a', minX: -6.05, maxX: -4.65, minZ: 2.15, maxZ: 4.45 }),
      Object.freeze({ id: 'bed-west-b', minX: -6.05, maxX: -4.65, minZ: 5.25, maxZ: 7.55 }),
      Object.freeze({ id: 'bed-east', minX: 4.65, maxX: 6.05, minZ: 2.15, maxZ: 4.45 }),
      Object.freeze({ id: 'cabinet', minX: 6.45, maxX: 7.25, minZ: 5.7, maxZ: 9.5 }),
      Object.freeze({ id: 'cupping-table', minX: 4.35, maxX: 5.4, minZ: 6.2, maxZ: 6.95 }),
      Object.freeze({ id: 'today-stand', minX: 4.15, maxX: 5.65, minZ: 9.55, maxZ: 10.25 }),
      Object.freeze({ id: 'screen-west-a', minX: -4.45, maxX: -2.65, minZ: 1.95, maxZ: 2.4 }),
      Object.freeze({ id: 'screen-west-b', minX: -4.45, maxX: -2.65, minZ: 4.95, maxZ: 5.4 })
    ]),
    interacts: Object.freeze([
      Object.freeze({ id: 'tcm-study', x: 0, z: 10.7, r: 0.7, label: '進入經絡研習室', action: 'enterTcmStudy' })
    ])
  })
]);

export function interiorByBuilding(buildingId) {
  for (let i = 0; i < INTERIORS.length; i++) {
    if (INTERIORS[i].buildingId === buildingId) return INTERIORS[i];
  }
  return null;
}

/** 室內互動區世界座標（含原點平移）；供提示與 hitInteractZone 測試。 */
export function interiorPromptZones(room, origin) {
  if (!room) return [];
  const o = origin || INTERIOR_ORIGIN;
  const zones = [];
  const exit = room.exit;
  if (exit) {
    zones.push(Object.freeze({
      id: room.id + '-exit',
      x: exit.x + o.x,
      z: exit.z + o.z,
      r: exit.r,
      label: exit.label,
      action: 'exitInterior'
    }));
  }
  const list = room.interacts || [];
  for (let i = 0; i < list.length; i++) {
    const iz = list[i];
    zones.push(Object.freeze({
      id: iz.id,
      x: iz.x + o.x,
      z: iz.z + o.z,
      r: iz.r,
      label: iz.label,
      action: iz.action,
      claw: iz.claw
    }));
  }
  return Object.freeze(zones);
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
    },
    interactZones() {
      if (!active) return [];
      const zones = [];
      const exit = this.exitZone();
      if (exit) zones.push(exit);
      const list = active.def.interacts || [];
      for (let i = 0; i < list.length; i++) {
        const iz = list[i];
        zones.push({
          id: iz.id,
          x: iz.x,
          z: iz.z,
          r: iz.r,
          label: iz.label,
          action: iz.action,
          claw: iz.claw
        });
      }
      return zones;
    }
  };
}
