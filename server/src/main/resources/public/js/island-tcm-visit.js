// 推拿館門 → 經絡研習室，以及回島後站在門外（純邏輯，不依賴 Three.js／DOM）。
//
// sessionStorage 鍵名：punchclock.islandTcmReturn

import { buildingById } from './island-layout.js';

export const TCM_RETURN_STORAGE_KEY = 'punchclock.islandTcmReturn';
export const TCM_ATLAS_URL = '/atlas/tcm-3d/?set=head-neck-21&code=GV24%2B&from=island';

const TCM_BUILDING_ID = 'tcm-site';
const OUTSIDE_MARGIN = 0.15;

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

export function tcmReturnPayload(building) {
  const pose = tcmReturnPose(building);
  if (!pose) return null;
  return Object.freeze({
    explore: true,
    x: pose.x,
    z: pose.z,
    yaw: pose.yaw
  });
}

export function writeTcmReturn(storage, building) {
  if (!storage) return false;
  const payload = tcmReturnPayload(building);
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
    return Object.freeze({ x: data.x, z: data.z, yaw: data.yaw, explore: true });
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