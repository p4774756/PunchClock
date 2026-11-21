// 訪客簽名簿邏輯（供 node:test 與面板共用）

export const GUESTBOOK_DOODLE_WIDTH = 240;
export const GUESTBOOK_DOODLE_HEIGHT = 120;
export const NICKNAME_MAX = 20;
export const MESSAGE_MAX = 200;
export const EMOJI_KEYS = Object.freeze(['smile', 'heart', 'thumbs', 'angry']);
export const EMOJI_LABEL = Object.freeze({
  smile: '笑臉',
  heart: '愛心',
  thumbs: '讚',
  angry: '生氣'
});
export const GUESTBOOK_COLORS = Object.freeze([
  '#1f2430', '#e03c3c', '#2563eb', '#22a06b', '#f4a0c0', '#f06a2a'
]);
export const GUESTBOOK_BRUSHES = Object.freeze([2, 8]);
export const DEFAULT_PAGE_SIZE = 5;

export function stripControls(raw) {
  return String(raw == null ? '' : raw).replace(/[\u0000-\u001F\u007F]/g, '');
}

export function normalizeNickname(raw) {
  const trimmed = stripControls(raw).trim();
  if (!trimmed) return null;
  if (Array.from(trimmed).length > NICKNAME_MAX) return null;
  return trimmed;
}

export function normalizeMessage(raw) {
  const trimmed = stripControls(raw).trim();
  if (!trimmed) return null;
  if (Array.from(trimmed).length > MESSAGE_MAX) return null;
  return trimmed;
}

export function isBlankCanvas(imageData) {
  const data = imageData && imageData.data;
  if (!data || !data.length) return true;
  for (let i = 3; i < data.length; i += 4) {
    if (data[i] !== 0) return false;
  }
  return true;
}

export function formatGuestDate(ms) {
  const date = new Date(ms);
  if (Number.isNaN(date.getTime())) return '';
  const p = (n) => String(n).padStart(2, '0');
  return p(date.getMonth() + 1) + '/' + p(date.getDate());
}

export function emojiGlyph(key) {
  if (key === 'smile') return '☺';
  if (key === 'heart') return '♥';
  if (key === 'thumbs') return '讚';
  if (key === 'angry') return '怒';
  return '';
}