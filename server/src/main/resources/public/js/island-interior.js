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

const LAB_PHI_MIN = 0.32;
const LAB_PHI_MAX = 1.12;
const LAB_FOV_DEG = 25;

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

export const INTERIOR_HINT_EXPLORE_DESK = 'WASD 或方向鍵移動 · 拖曳旋轉鏡頭';
export const INTERIOR_HINT_EXPLORE_TOUCH = '用左下角搖桿移動 · 拖曳旋轉鏡頭';
export const INTERIOR_HINT_INSIDE_DESK = 'WASD 或方向鍵移動 · Esc 或走到門口離開 · 工具列可一鍵離開';
export const INTERIOR_HINT_INSIDE_TOUCH = '搖桿移動 · 按右下「離開」出去';

/** 研發樓進房預設鏡頭（桌面／橫向）：北牆剖面後拉遠，南牆離開工具列。 */
export const LAB_INTERIOR_CAMERA = Object.freeze({ radius: 4.6, theta: 0.02, phi: 1.12 });

export const LAB_INTERIOR_CAMERA_MOBILE = Object.freeze({ radius: 11, theta: 0, phi: 1.12 });

/** 鏡頭南牆焦點（桌面）；每幀與角色胸口混合，手機維持純焦點。 */
export const LAB_INTERIOR_LOOK_DESKTOP = Object.freeze({ x: -0.25, y: G + 1.12, z: -0.85 });
export const LAB_INTERIOR_LOOK_MOBILE = Object.freeze({ x: -0.58, y: G + 1.08, z: -0.85 });
/** 門口附近注視混合（朝胸口）；往北愈深愈抬高，上限避免螢幕頂貼工具列。 */
export const LAB_INTERIOR_LOOK_CHEST_BLEND = 0.45;
export const LAB_INTERIOR_LOOK_DEPTH_BLEND = 0.55;
export const LAB_INTERIOR_LOOK_BLEND_MAX = 0.88;

/** 右上角工具列＋品牌區下緣到螢幕上緣至少約 24px（與 viewport 高度換算 NDC）。 */
export const LAB_INTERIOR_TOOLBAR_INSET_PX = 80;

const LAB_SCREEN = Object.freeze({
  cx: 0,
  cy: G + 0.84,
  cz: -1.74,
  hw: 0.675,
  hh: 0.44,
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

/** 與 island-lab-interior.js 幾何對齊；北牆剖面後不列入可見遮擋。 */
export const LAB_INTERIOR_WALL = Object.freeze({ h: 2.2, t: 0.1 });

export const LAB_INTERIOR_PLAYER_CHEST_Y = G + 1.05;
export const LAB_INTERIOR_PLAYER_FEET_Y = P;
export const LAB_INTERIOR_PLAYER_HEAD_Y = P + MILU_ANIM.height;

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
  const northZ = room && room.floor ? room.floor.maxZ : 1.9;
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

/** 與 island.js follow／每幀 targetLook 相同：室內焦點平移到世界座標。 */
export function labInteriorRuntimeLookAt(room, player, aspect, origin) {
  if (!room || room.id !== 'lab-room') return null;
  const focal = labInteriorLookTarget(room, player, aspect);
  const o = origin || INTERIOR_ORIGIN;
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
    minY: G + wh - 0.36,
    maxY: G + wh - 0.08,
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

export function labInteriorCameraInsideFloor(eye, floor, inset) {
  if (!floor || !eye) return false;
  const m = inset == null ? 0.15 : inset;
  return eye.x >= floor.minX + m
    && eye.x <= floor.maxX - m
    && eye.z >= floor.minZ + m
    && eye.z <= floor.maxZ - m
    && eye.y <= G + LAB_INTERIOR_WALL.h - 0.05;
}

/** 研發樓角落站位：門口、機櫃旁、工作台旁（供鏡頭與遮擋測試）。 */
export function labInteriorPlayerSamplePositions(room) {
  if (!room || room.id !== 'lab-room') return [];
  const rack = room.blocks[0];
  const bench = room.blocks[1];
  return Object.freeze([
    Object.freeze({ id: 'spawn', x: room.spawn.x, z: room.spawn.z }),
    Object.freeze({ id: 'door', x: room.exit.x, z: room.exit.z }),
    Object.freeze({ id: 'rack', x: -1.15, z: 0.25 }),
    Object.freeze({ id: 'bench', x: 0.65, z: 0.0 }),
    Object.freeze({ id: 'north', x: 0, z: Math.min(1.61, room.floor.maxZ - 0.34) })
  ]);
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
export function labInteriorFramingOk(aspect, viewportHeightPx, room) {
  const pose = labInteriorCameraForAspect(aspect);
  const labRoom = room || INTERIORS.find((r) => r.id === 'lab-room');
  const framingPlayer = labRoom
    ? { x: labRoom.spawn.x, z: labRoom.spawn.z }
    : { x: 0, z: 0 };
  const look = labInteriorLookTarget(labRoom, framingPlayer, aspect);
  const lim = labInteriorScreenTopNdcLimit(viewportHeightPx);
  const pts = labLandmarkPoints();
  const out = {};
  for (const key of Object.keys(pts)) {
    const p = pts[key];
    out[key] = labInteriorProjectNdc(p[0], p[1], p[2], pose, look, aspect);
  }
  const span = out.scrTL.y - out.scrBL.y;
  if (aspect < 0.72) {
    const contentTopY = LAB_SCREEN.cy + LAB_SCREEN.hh;
    const contentTopL = labInteriorProjectNdc(
      LAB_SCREEN.cx - LAB_SCREEN.hw, contentTopY, LAB_SCREEN.cz, pose, look, aspect
    );
    const contentTopR = labInteriorProjectNdc(
      LAB_SCREEN.cx + LAB_SCREEN.hw, contentTopY, LAB_SCREEN.cz, pose, look, aspect
    );
    const titleContentY = contentTopY - 0.14;
    const titleTop = labInteriorProjectNdc(
      LAB_SCREEN.cx - LAB_SCREEN.hw + 0.25, titleContentY, LAB_SCREEN.cz, pose, look, aspect
    );
    if (contentTopL.y > lim + 0.001 || contentTopR.y > lim + 0.001 || titleTop.y > lim + 0.001) {
      return false;
    }
  } else if (out.scrTL.y > lim + 0.001 || out.scrTR.y > lim + 0.001) {
    return false;
  }
  if (aspect < 0.72) {
    const contentBot = labInteriorProjectNdc(
      LAB_SCREEN.cx - LAB_SCREEN.hw, LAB_SCREEN.cy - LAB_SCREEN.hh, LAB_SCREEN.cz, pose, look, aspect
    );
    const contentTopY = LAB_SCREEN.cy + LAB_SCREEN.hh;
    const contentTopL = labInteriorProjectNdc(
      LAB_SCREEN.cx - LAB_SCREEN.hw, contentTopY, LAB_SCREEN.cz, pose, look, aspect
    );
    const contentVertical = contentTopL.y - contentBot.y;
    if (contentVertical < 0.22) return false;
  } else if (span < 0.70) {
    return false;
  }
  const inside = (ndc) => ndc.vz > 0
    && ndc.y >= -0.94 && ndc.y <= 0.98
    && ndc.x >= -0.94 && ndc.x <= 0.94;
  if (!inside(out.title)) return false;
  if (aspect < 0.72) {
    const vis = (Math.min(out.scrTR.x, 0.92) - Math.max(out.scrTL.x, -0.92))
      / (out.scrTR.x - out.scrTL.x);
    if (!Number.isFinite(vis) || vis < 0.66) return false;
    if (!inside(out.doorSign) || !inside(out.pillarR) || !inside(out.mat)) return false;
  } else {
    const screenKeys = ['scrTL', 'scrTR', 'scrBL', 'scrBR', 'title'];
    for (let i = 0; i < screenKeys.length; i++) {
      if (!inside(out[screenKeys[i]])) return false;
    }
    if (!inside(out.lintel) || !inside(out.doorSign) || !inside(out.pillarR)) return false;
    if (out.pillarL.x < -1.1) return false;
    if (!inside(out.mat)) return false;
  }

  const floor = labRoom && labRoom.floor;
  const eye = labInteriorCameraEye(pose, look, aspect);
  const walls = floor ? labInteriorVisibleWallAABBs(floor) : [];
  const inRoom = floor ? labInteriorCameraInsideFloor(eye, floor, 0.15) : false;
  const chestY = LAB_INTERIOR_PLAYER_CHEST_Y;
  const sightTargets = [
    { x: LAB_SCREEN.cx, y: LAB_SCREEN.cy, z: LAB_SCREEN.cz },
    { x: LAB_DOOR.signX, y: LAB_DOOR.signY, z: LAB_DOOR.southZ - 0.03 },
    { x: LAB_DOOR.x, y: LAB_DOOR.pillarY, z: LAB_DOOR.southZ }
  ];
  const samples = labInteriorPlayerSamplePositions(labRoom);
  for (let i = 0; i < samples.length; i++) {
    const s = samples[i];
    const sampleLook = labInteriorLookTarget(labRoom, { x: s.x, z: s.z }, aspect);
    sightTargets.push({ x: s.x, y: chestY, z: s.z });
    const headNdc = labInteriorProjectNdc(
      s.x, LAB_INTERIOR_PLAYER_HEAD_Y, s.z, pose, sampleLook, aspect
    );
    const feetNdc = labInteriorProjectNdc(
      s.x, LAB_INTERIOR_PLAYER_FEET_Y, s.z, pose, sampleLook, aspect
    );
    if (!inside(headNdc) || !inside(feetNdc) || headNdc.vz < 0.02 || feetNdc.vz < 0.02) {
      return false;
    }
  }
  if (!inRoom) {
    const sideWalls = floor ? labInteriorSideWallAABBs(floor) : [];
    const southInterior = walls.filter((_, idx) => idx >= 3);
    for (let j = 0; j < sightTargets.length; j++) {
      const t = sightTargets[j];
      const onSouthFacade = t.z <= floor.minZ + 0.08;
      const blockers = onSouthFacade ? sideWalls : sideWalls.concat(southInterior);
      if (!labInteriorSightClear(eye, t, blockers)) return false;
    }
  }
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
