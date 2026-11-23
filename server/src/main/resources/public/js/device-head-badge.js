/** 任務分頁裝置列徽章：依打卡狀態顯示簡短文案（可單测）。 */
export function resolveCheckInHeadStatus(tasks) {
  const list = Array.isArray(tasks) ? tasks : [];
  if (list.some((t) => t && t.status === 'CHECKING_IN')) return 'checking';
  if (list.some((t) => t && t.status === 'SCHEDULED')) return 'scheduled';
  return 'idle';
}

export function buildDeviceHeadConnectionText(isConnected) {
  return isConnected ? '辦公室上班中' : '離線';
}

export function buildDeviceHeadConnectionChipClass(isConnected) {
  return isConnected ? 'chip-online' : 'chip-idle';
}

export function buildDeviceHeadCheckInText(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking') return '打卡中';
  if (status === 'scheduled') return '打卡已排程';
  return '未排打卡';
}

export function buildDeviceHeadCheckInChipClass(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'idle') return 'chip-idle';
  return 'chip-online';
}

/** @deprecated 相容舊呼叫；新 UI 請用連線／打卡分開的函式。 */
export function buildDeviceHeadBadgeText(isConnected, transportLabel, tasks) {
  if (!isConnected) return '離線';
  const checkIn = buildDeviceHeadCheckInText(true, tasks);
  return buildDeviceHeadConnectionText(true) + ' · ' + checkIn;
}

/** @deprecated 相容舊呼叫；新 UI 請用連線／打卡分開的函式。 */
export function buildDeviceHeadChipClass(isConnected, tasks) {
  return buildDeviceHeadConnectionChipClass(isConnected);
}

const deviceHeadBadgeApi = {
  resolveCheckInHeadStatus,
  buildDeviceHeadConnectionText,
  buildDeviceHeadConnectionChipClass,
  buildDeviceHeadCheckInText,
  buildDeviceHeadCheckInChipClass,
  buildDeviceHeadBadgeText,
  buildDeviceHeadChipClass
};

if (typeof globalThis !== 'undefined') {
  globalThis.PunchclockDeviceHeadBadge = deviceHeadBadgeApi;
}