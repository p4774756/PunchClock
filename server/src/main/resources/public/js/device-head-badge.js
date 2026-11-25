/** 裝置列排程狀態（可單测）。 */
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

export function buildDeviceHeadScheduleAriaLabel(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking') return '執行中';
  if (status === 'scheduled') return '已排程';
  return '未排程';
}

export function buildDeviceHeadScheduleDotChipClass(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'idle') return 'chip-idle';
  return 'chip-online';
}

/** @deprecated 相容舊呼叫；新 UI 用綠／灰點與 aria-label。 */
export function buildDeviceHeadCheckInText() {
  return null;
}

/** @deprecated 相容舊呼叫。 */
export function buildDeviceHeadCheckInChipClass(isConnected, tasks) {
  if (!isConnected) return null;
  return buildDeviceHeadScheduleDotChipClass(isConnected, tasks);
}

/** @deprecated 相容舊呼叫；新 UI 請用連線／打卡分開的函式。 */
export function buildDeviceHeadBadgeText(isConnected, transportLabel, tasks) {
  if (!isConnected) return '離線';
  const schedule = buildDeviceHeadScheduleAriaLabel(true, tasks);
  return buildDeviceHeadConnectionText(true) + (schedule ? ' · ' + schedule : '');
}

/** @deprecated 相容舊呼叫；新 UI 請用連線／打卡分開的函式。 */
export function buildDeviceHeadChipClass(isConnected, tasks) {
  return buildDeviceHeadConnectionChipClass(isConnected);
}

const deviceHeadBadgeApi = {
  resolveCheckInHeadStatus,
  buildDeviceHeadConnectionText,
  buildDeviceHeadConnectionChipClass,
  buildDeviceHeadScheduleAriaLabel,
  buildDeviceHeadScheduleDotChipClass,
  buildDeviceHeadCheckInText,
  buildDeviceHeadCheckInChipClass,
  buildDeviceHeadBadgeText,
  buildDeviceHeadChipClass
};

if (typeof globalThis !== 'undefined') {
  globalThis.PunchclockDeviceHeadBadge = deviceHeadBadgeApi;
}