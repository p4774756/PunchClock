/** 任務分頁裝置列徽章：依打卡狀態顯示簡短文案（可單测）。 */
export function resolveCheckInHeadStatus(tasks) {
  const list = Array.isArray(tasks) ? tasks : [];
  if (list.some((t) => t && t.status === 'CHECKING_IN')) return 'checking';
  if (list.some((t) => t && t.status === 'SCHEDULED')) return 'scheduled';
  return 'idle';
}

export function buildDeviceHeadBadgeText(isConnected, transportLabel, tasks) {
  if (!isConnected) return '離線';
  const transport = transportLabel || '-';
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'checking') return '辦公室上班中 · ' + transport + ' · 打卡中';
  if (status === 'scheduled') return '辦公室上班中 · ' + transport + ' · 打卡已排程';
  return '辦公室上班中 · ' + transport + ' · 打卡未啟動';
}

export function buildDeviceHeadChipClass(isConnected, tasks) {
  if (!isConnected) return 'chip-offline';
  const status = resolveCheckInHeadStatus(tasks);
  if (status === 'idle') return 'chip-idle';
  return 'chip-online';
}

const deviceHeadBadgeApi = {
  resolveCheckInHeadStatus,
  buildDeviceHeadBadgeText,
  buildDeviceHeadChipClass
};

if (typeof globalThis !== 'undefined') {
  globalThis.PunchclockDeviceHeadBadge = deviceHeadBadgeApi;
}