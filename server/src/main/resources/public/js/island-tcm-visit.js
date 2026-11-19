// 推拿館門 → 經絡研習室，以及回島／回館後還原站姿（純邏輯，不依賴 Three.js／DOM）。
//
// sessionStorage 鍵名：punchclock.islandTcmReturn

import { buildingById } from './island-layout.js';

export const TCM_RETURN_STORAGE_KEY = 'punchclock.islandTcmReturn';
export const TCM_ATLAS_URL = '/atlas/tcm-3d/?set=head-neck-21&code=GV24%2B&from=island';

const TCM_BUILDING_ID = 'tcm-site';
const OUTSIDE_MARGIN = 0.15;
/** 回館站位：在半徑外留白，避免一落地又進研習室。 */
export const TCM_HALL_RETURN_MARGIN = 0.45;
const DEFAULT_PLAYER_RADIUS = 0.26;
/** 與 tcm-room.interacts 的 tcm-study 同步（避免與 island-interior 循環匯入）。 */
const TCM_STUDY_INTERACT = Object.freeze({ x: 0, z: 10.7, r: 1.8 });

function rotateY(x, z, yaw) {
  const c = Math.cos(yaw || 0);
  const s = Math.sin(yaw || 0);
  return { x: x * c + z * s, z: -x * s + z * c };
}

/** 站在推拿館門外、面向離開建物。 */
export function tcmReturnPose(building) {
  const b = building || buildingById(TCM_BUILDING_ID);
  if (!b || !b.door || !b.entry) return null;
  const front = rotateY(0, 1, b.yaw || 0);
  const step = (b.entry.r || 0.82) + OUTSIDE_MARGIN;
  return Object.freeze({
    x: b.door.x + front.x * step,
    z: b.door.z + front.z * step,
    yaw: Math.atan2(front.x, front.z)
  });
}

export function tcmReturnPayload(building, target) {
  const useTarget = target === 'hall' ? 'hall' : 'island';
  if (useTarget === 'hall') {
    const pose = tcmHallReturnPose();
    if (!pose) return null;
    return Object.freeze({
      explore: true,
      target: 'hall',
      buildingId: TCM_BUILDING_ID,
      x: pose.x,
      z: pose.z,
      yaw: pose.yaw
    });
  }
  const pose = tcmReturnPose(building);
  if (!pose) return null;
  return Object.freeze({
    explore: true,
    target: 'island',
    x: pose.x,
    z: pose.z,
    yaw: pose.yaw
  });
}

/** 站在北門研習室入口前、面向大廳（南）。 */
export function tcmHallReturnPose(room, playerRadius) {
  let study = TCM_STUDY_INTERACT;
  if (room && room.interacts) {
    for (let i = 0; i < room.interacts.length; i++) {
      if (room.interacts[i].id === 'tcm-study') {
        study = room.interacts[i];
        break;
      }
    }
  }
  const pr = playerRadius == null ? DEFAULT_PLAYER_RADIUS : playerRadius;
  const z = study.z - study.r - TCM_HALL_RETURN_MARGIN - pr;
  return Object.freeze({
    x: study.x,
    z: z,
    yaw: Math.PI
  });
}

export function writeTcmReturn(storage, building, options) {
  if (!storage) return false;
  const opts = options || {};
  const target = opts.target === 'hall' ? 'hall' : 'island';
  const payload = tcmReturnPayload(building, target);
  if (!payload) return false;
  try {
    storage.setItem(TCM_RETURN_STORAGE_KEY, JSON.stringify(payload));
    return true;
  } catch (_) {
    return false;
  }
}

export function readTcmReturn(storage) {
  if (!storage) return null;
  try {
    const raw = storage.getItem(TCM_RETURN_STORAGE_KEY);
    if (!raw) return null;
    const data = JSON.parse(raw);
    if (!data || data.explore !== true) return null;
    if (!Number.isFinite(data.x) || !Number.isFinite(data.z) || !Number.isFinite(data.yaw)) return null;
    const target = data.target === 'hall' ? 'hall' : 'island';
    const out = { x: data.x, z: data.z, yaw: data.yaw, explore: true, target: target };
    if (target === 'hall' && data.buildingId) out.buildingId = data.buildingId;
    return Object.freeze(out);
  } catch (_) {
    return null;
  }
}

export function clearTcmReturn(storage) {
  if (!storage) return;
  try {
    storage.removeItem(TCM_RETURN_STORAGE_KEY);
  } catch (_) {
    /* sessionStorage 不可用 */
  }
}

export function consumeTcmReturn(storage) {
  const payload = readTcmReturn(storage);
  clearTcmReturn(storage);
  return payload;
}