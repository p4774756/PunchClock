/** 任務分頁客戶端卡牌收合：Lucide 風格箭頭與鍵盤判斷（可單测）。 */
export const DEVICE_CHEVRON_CLASS = 'device-chevron';

export function deviceChevronMarkup(expanded) {
  const d = expanded ? 'm6 9 6 6 6-6' : 'm9 18 6-6-6-6';
  return '<svg class="' + DEVICE_CHEVRON_CLASS + '" xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="' + d + '"/></svg>';
}

export function deviceFoldLabel(expanded) {
  return expanded ? '收合' : '展開';
}

export function shouldToggleDeviceFold(key) {
  return key === 'Enter' || key === ' ';
}

const deviceFoldApi = {
  DEVICE_CHEVRON_CLASS,
  deviceChevronMarkup,
  deviceFoldLabel,
  shouldToggleDeviceFold
};

if (typeof globalThis !== 'undefined') {
  globalThis.PunchclockDeviceFold = deviceFoldApi;
}