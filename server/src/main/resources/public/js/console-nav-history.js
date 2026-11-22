/** 主控台分頁 hash 與施工進度 query，供還原上一頁／下一頁（可單测）。 */
import { parseRoadmapOpenParam, shouldOpenRoadmapFromSearch } from './dev-machines-format.js';

export function consoleHashForTab(tabId) {
  const token = tabId == null ? '' : String(tabId).trim();
  if (!token) return '';
  return '#' + token;
}

export function stripRoadmapOpenSearch(search) {
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

export function buildConsoleTasksUrl(pathname, hashTab) {
  const path = pathname == null || pathname === '' ? '/console' : String(pathname);
  const hash = consoleHashForTab(hashTab || 'devices');
  return path + hash;
}

export function buildConsoleRoadmapUrl(pathname) {
  const path = pathname == null || pathname === '' ? '/console' : String(pathname);
  return path + '?open=roadmap';
}

export function shouldConsoleRoadmapFromLocation(location) {
  if (!location) return false;
  return shouldOpenRoadmapFromSearch(location.search || '');
}

export function roadmapOpenFromPopstateHref(href) {
  if (!href) return false;
  try {
    const url = new URL(href, 'http://localhost');
    return parseRoadmapOpenParam(url.search) === 'roadmap';
  } catch (e) {
    return false;
  }
}