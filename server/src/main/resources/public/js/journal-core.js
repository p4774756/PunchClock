/** 留言板純邏輯（可 node:test）。 */

export const NICKNAME_MAX = 12;
export const COMMENT_MAX = 200;
export const BODY_MAX = 280;
export const ANONYMOUS = '路過的旅人';

const LINK_RE = /(https?:\/\/|www\.|\[[^\]]+\]\([^)]+\))/i;
const CONTROL_RE = /[\u0000-\u001F\u007F]/g;

export function stripControls(raw) {
  return String(raw == null ? '' : raw).replace(CONTROL_RE, '');
}

export function normalizeNickname(raw) {
  const trimmed = stripControls(raw).trim();
  if (!trimmed) return ANONYMOUS;
  if (Array.from(trimmed).length > NICKNAME_MAX) return null;
  return trimmed;
}

export function containsForbiddenLink(text) {
  return LINK_RE.test(String(text == null ? '' : text));
}

export function escapeHtmlText(value) {
  return String(value == null ? '' : value)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

export function formatJournalWhen(ms) {
  const date = new Date(ms);
  if (Number.isNaN(date.getTime())) return '';
  const p = (n) => String(n).padStart(2, '0');
  return date.getFullYear() + '-' + p(date.getMonth() + 1) + '-' + p(date.getDate())
    + ' ' + p(date.getHours()) + ':' + p(date.getMinutes());
}