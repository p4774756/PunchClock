/** 辦公室浮窗深連結與瀏覽器返回鍵（可單测）。 */
import { parseRoadmapOpenParam } from './dev-machines-format.js';

export function stripOfficeOpenSearch(search) {
  const raw = search == null ? '' : String(search);
  if (!raw || raw === '?') return '';
  const qs = raw.startsWith('?') ? raw.slice(1) : raw;
  if (!qs) return '';
  try {
    const params = new URLSearchParams(qs);
    params.delete('open');
    const next = params.toString();
    return next ? '?' + next : '';
  } catch (e) {
    return '';
  }
}

export function buildOfficeOpenUrl(pathname, openToken) {
  const path = pathname == null || pathname === '' ? '/' : String(pathname);
  const token = openToken == null ? '' : String(openToken).trim();
  if (!token) return path;
  return path + '?open=' + encodeURIComponent(token);
}

export function officeOpenFromPopstateHref(href) {
  if (!href) return '';
  try {
    const url = new URL(href, 'http://localhost');
    return parseRoadmapOpenParam(url.search) || '';
  } catch (e) {
    return '';
  }
}