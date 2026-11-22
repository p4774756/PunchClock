// 首頁秘書：GET /api/office/public 的 tiger 欄位解析與小卡內容組裝。

import { formatTaipeiClock } from './dev-machines-format.js';

const FORBIDDEN = /(?:ai\/issue-|\/Users\/|worktree|battery|charging|\.log\b)/i;

export function tigerFromPublic(body) {
  if (!body || body.success !== true || !body.characters) return null;
  const tiger = body.characters.tiger;
  if (!tiger || typeof tiger !== 'object') return null;
  return {
    status: tiger.status == null ? '' : String(tiger.status).trim(),
    active: Array.isArray(tiger.active) ? tiger.active : [],
    completedToday: Array.isArray(tiger.completedToday) ? tiger.completedToday : []
  };
}

/** 3D 泡泡用短標（工作中／待命中／休息中；保留「工作中 ×N」）。 */
export function bubbleTextForStatus(status) {
  const s = status == null ? '' : String(status).trim();
  if (s.startsWith('工作中')) return s;
  if (s === '待命中') return '待命中';
  if (s === '休息中') return '休息中';
  return '待命中';
}

export function isSafePublicText(text) {
  if (text == null) return false;
  const s = String(text);
  if (!s.trim()) return false;
  return !FORBIDDEN.test(s);
}

export function formatOfficeClock(isoOrMs) {
  if (isoOrMs == null) return '';
  if (typeof isoOrMs === 'number') return formatTaipeiClock(isoOrMs);
  const parsed = Date.parse(String(isoOrMs));
  if (!Number.isFinite(parsed)) return '';
  return formatTaipeiClock(parsed);
}

export function capsulesForActive(row) {
  const item = row && typeof row === 'object' ? row : {};
  const out = [];
  const round = Number(item.round);
  if (Number.isFinite(round) && round > 0) {
    out.push('第 ' + round + ' 輪');
  }
  const minutes = Number(item.minutes);
  if (Number.isFinite(minutes) && minutes > 0) {
    out.push('已跑 ' + minutes + ' 分');
  }
  if (item.model && isSafePublicText(item.model)) {
    out.push(String(item.model));
  }
  return out;
}

function el(doc, tag, className, text) {
  const node = doc.createElement(tag);
  if (className) node.className = className;
  if (text != null && text !== '') node.textContent = text;
  return node;
}

function linkIssue(doc, item) {
  const issue = Number(item.issue);
  if (!Number.isFinite(issue) || issue <= 0) return null;
  const a = el(doc, 'a', 'pt-status-link');
  a.textContent = '#' + issue;
  const href = item.issueUrl && isSafePublicText(item.issueUrl) ? String(item.issueUrl) : '';
  if (href) {
    a.href = href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
  } else {
    a.setAttribute('aria-disabled', 'true');
  }
  return a;
}

function linkPr(doc, item) {
  const pr = Number(item.pr);
  if (!Number.isFinite(pr) || pr <= 0) return null;
  const a = el(doc, 'a', 'pt-status-link');
  a.textContent = 'PR #' + pr;
  const href = item.prUrl && isSafePublicText(item.prUrl) ? String(item.prUrl) : '';
  if (href) {
    a.href = href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
  } else {
    a.setAttribute('aria-disabled', 'true');
  }
  return a;
}

export function renderActiveBlock(doc, item) {
  const wrap = el(doc, 'div', 'pt-status-active-item');
  const phrase = item && item.phrase && isSafePublicText(item.phrase) ? String(item.phrase) : '';
  wrap.appendChild(el(doc, 'p', 'pt-status-phrase', phrase || '正在處理任務'));
  const links = el(doc, 'div', 'pt-status-links');
  const issueLink = linkIssue(doc, item || {});
  const prLink = linkPr(doc, item || {});
  if (issueLink) links.appendChild(issueLink);
  if (prLink) links.appendChild(prLink);
  if (links.childNodes.length) wrap.appendChild(links);
  const caps = capsulesForActive(item);
  if (caps.length) {
    const row = el(doc, 'div', 'pt-status-capsules');
    caps.forEach((label) => row.appendChild(el(doc, 'span', 'pt-status-cap', label)));
    wrap.appendChild(row);
  }
  return wrap;
}

export function renderCompletedList(doc, items) {
  const ul = el(doc, 'ul', 'pt-status-done-list');
  const list = Array.isArray(items) ? items : [];
  if (!list.length) {
    const li = el(doc, 'li', 'pt-status-done-empty', '今天還沒有收工紀錄');
    ul.appendChild(li);
    return ul;
  }
  list.forEach((row) => {
    if (!row || typeof row !== 'object') return;
    const li = el(doc, 'li', 'pt-status-done-item');
    const clock = formatOfficeClock(row.at);
    if (clock) {
      li.appendChild(el(doc, 'span', 'pt-status-done-time', clock));
    }
    const phrase = row.phrase && isSafePublicText(row.phrase) ? String(row.phrase) : '';
    if (phrase) li.appendChild(el(doc, 'span', 'pt-status-done-phrase', phrase));
    const issueLink = linkIssue(doc, row);
    const prLink = linkPr(doc, row);
    if (issueLink || prLink) {
      const links = el(doc, 'span', 'pt-status-done-links');
      if (issueLink) links.appendChild(issueLink);
      if (prLink) links.appendChild(prLink);
      li.appendChild(links);
    }
    ul.appendChild(li);
  });
  return ul;
}

export function fillStatusCard(doc, bodyEl, tiger) {
  if (!bodyEl) return;
  bodyEl.replaceChildren();
  const view = tiger || { status: '', active: [], completedToday: [] };
  const activeWrap = el(doc, 'section', 'pt-status-section');
  activeWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '正在進行'));
  const actives = view.active.length ? view.active : [];
  if (actives.length) {
    actives.forEach((item) => activeWrap.appendChild(renderActiveBlock(doc, item)));
  } else {
    const idle = el(doc, 'p', 'pt-status-idle', statusIdleLine(view.status));
    activeWrap.appendChild(idle);
  }
  bodyEl.appendChild(activeWrap);

  const doneWrap = el(doc, 'section', 'pt-status-section');
  doneWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '今天完成'));
  doneWrap.appendChild(renderCompletedList(doc, view.completedToday));
  bodyEl.appendChild(doneWrap);
}

function statusIdleLine(status) {
  const s = status == null ? '' : String(status).trim();
  if (s === '休息中') return '猛虎今天先休息，晚點再來看我。';
  if (s.startsWith('工作中')) return '正在忙，點開上方泡泡可隨時看進度。';
  return '待命中，有任務會立刻開工。';
}