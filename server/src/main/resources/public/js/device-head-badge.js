/** 裝置列排程狀態（可單测）。 */
export function resolveCheckInHeadStatus(tasks) {
  const list = Array.isArray(tasks) ? tasks : [];
  if (list.some((t) => t && t.status === 'CHECKING_IN')) return 'checking';
  if (list.some((t) => t && t.status === 'SCHEDULED')) return 'scheduled';
  return 'idle';
}

/** 卡片層級：active（任務啟動中）、idle（在線未啟動）、offline。 */
export function resolveDeviceCardTier(isConnected, tasks) {
  if (!isConnected) return 'offline';
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking' || status === 'scheduled') return 'active';
  return 'idle';
}

export function buildDeviceCardTierClass(isConnected, tasks) {
  return 'device-tier-' + resolveDeviceCardTier(isConnected, tasks);
}

export function buildDeviceHeadConnectionText(isConnected) {
  return isConnected ? '辦公室上班中' : '已離線';
}

export function buildDeviceHeadConnectionChipClass(isConnected) {
  return isConnected ? 'chip-online' : 'chip-idle';
}

export function buildDeviceHeadTaskLabelText(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking' || status === 'scheduled') return '任務執行中';
  return '未啟動';
}

export function buildDeviceHeadTaskChipClass(isConnected, tasks) {
  if (!isConnected) return null;
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking' || status === 'scheduled') return 'chip-task-active';
  return 'chip-task-idle';
}

export function buildDeviceHeadScheduleAriaLabel(isConnected, tasks) {
  return buildDeviceHeadTaskLabelText(isConnected, tasks);
}

export function buildDeviceHeadScheduleDotChipClass(isConnected, tasks) {
  return buildDeviceHeadTaskChipClass(isConnected, tasks);
}

/** @deprecated 相容舊呼叫；新 UI 用任務文字標籤。 */
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
  if (!isConnected) return '已離線';
  const task = buildDeviceHeadTaskLabelText(true, tasks);
  return buildDeviceHeadConnectionText(true) + (task ? ' · ' + task : '');
}

/** @deprecated 相容舊呼叫；新 UI 請用連線／打卡分開的函式。 */
export function buildDeviceHeadChipClass(isConnected, tasks) {
  return buildDeviceHeadConnectionChipClass(isConnected);
}

const deviceHeadBadgeApi = {
  resolveCheckInHeadStatus,
  resolveDeviceCardTier,
  buildDeviceCardTierClass,
  buildDeviceHeadConnectionText,
  buildDeviceHeadConnectionChipClass,
  buildDeviceHeadTaskLabelText,
  buildDeviceHeadTaskChipClass,
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