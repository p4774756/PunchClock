// 首頁秘書：GET /api/office/public 的 tiger 欄位解析與小卡內容組裝。

import { formatTaipeiClock, viewerCanLink } from './dev-machines-format.js';

const FORBIDDEN = /(?:ai\/issue-|\/Users\/|worktree|battery|charging|\.log\b)/i;

const DONE_PREVIEW_LIMIT = 3;
const QUEUE_PREVIEW_LIMIT = 5;
const VAGUE_PHRASES = new Set(['完成一項工作', '正在處理一項工作']);

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

export function devQueueFromStatus(body) {
  if (!body || body.success === false) return [];
  const now = body.now && typeof body.now === 'object' ? body.now : {};
  const queue = Array.isArray(now.queue) ? now.queue : [];
  return queue.filter((row) => row && (row.title || row.pair || row.issue));
}

export function fetchSecretaryDevQueue(fetchFn) {
  if (!fetchFn) return Promise.resolve([]);
  return fetchFn('/api/dev-status')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null)
    .then((body) => devQueueFromStatus(body || {}));
}

/** 今日任務細條：以公開辦公室資料的進行中＋今日完成筆數計算；無資料則 null。 */
export function taskProgressFromTiger(tiger) {
  if (!tiger || typeof tiger !== 'object') return null;
  const active = Array.isArray(tiger.active) ? tiger.active.length : 0;
  const completed = Array.isArray(tiger.completedToday) ? tiger.completedToday.length : 0;
  const total = active + completed;
  if (total <= 0) return null;
  return {
    completed,
    total,
    fraction: Math.min(1, completed / total)
  };
}

const SPRITE_TYPING = '/assets/private-team/secretary-typing-a.webp';
const SPRITE_IDLE_TEA = '/assets/private-team/secretary-idle-tea.webp';
const SPRITE_SLEEP = '/assets/private-team/secretary-sleep.webp';

/** 秘書狀態對應立繪（工作中打字 A、待命中喝茶、休息中趴睡）。 */
export function secretarySpriteUrlForStatus(status) {
  const s = status == null ? '' : String(status).trim();
  if (s.startsWith('工作中')) return SPRITE_TYPING;
  if (s === '休息中') return SPRITE_SLEEP;
  if (s === '待命中' || s === '待機') return SPRITE_IDLE_TEA;
  return SPRITE_IDLE_TEA;
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

/** 今日完成列說明：去掉「正在」、略過空泛 fallback（伺服器應已改標題）。 */
export function phraseForDoneDisplay(row) {
  const item = row && typeof row === 'object' ? row : {};
  let phrase = item.phrase && isSafePublicText(item.phrase) ? String(item.phrase).trim() : '';
  if (phrase.startsWith('正在')) {
    phrase = phrase.slice(2).trim();
  }
  if (!phrase || VAGUE_PHRASES.has(phrase)) {
    const title = item.title && isSafePublicText(item.title) ? String(item.title).trim() : '';
    if (title) return title;
  }
  return phrase;
}

function el(doc, tag, className, text) {
  const node = doc.createElement(tag);
  if (className) node.className = className;
  if (text != null && text !== '') node.textContent = text;
  return node;
}

function linkPr(doc, item, canLink) {
  const pr = Number(item.pr);
  if (!Number.isFinite(pr) || pr <= 0) return null;
  const a = el(doc, 'a', 'pt-status-link');
  a.textContent = 'PR #' + pr;
  const href = item.prUrl && isSafePublicText(item.prUrl) ? String(item.prUrl) : '';
  const authed = viewerCanLink(canLink);
  if (href && authed) {
    a.href = href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
  } else {
    a.setAttribute('aria-disabled', 'true');
  }
  return a;
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

export function renderActiveBlock(doc, item) {
  const wrap = el(doc, 'div', 'pt-status-active-item');
  const phrase = item && item.phrase && isSafePublicText(item.phrase) ? String(item.phrase) : '';
  wrap.appendChild(el(doc, 'p', 'pt-status-phrase', phrase || '正在處理任務'));
  const links = el(doc, 'div', 'pt-status-links');
  const issueLink = linkIssue(doc, item || {});
  const prLink = linkPr(doc, item || {}, true);
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

function appendExpandButton(doc, parent, expanded, count, onToggle) {
  const btn = el(doc, 'button', 'pt-status-expand');
  btn.type = 'button';
  btn.textContent = expanded ? '收合' : ('顯示全部（' + count + '）');
  if (typeof btn.addEventListener === 'function' && typeof onToggle === 'function') {
    btn.addEventListener('click', onToggle);
  }
  parent.appendChild(btn);
}

export function renderCompletedList(doc, items, ui) {
  const ul = el(doc, 'ul', 'pt-status-done-list');
  const list = Array.isArray(items) ? items : [];
  const opts = ui && typeof ui === 'object' ? ui : {};
  const expanded = !!opts.doneExpanded;
  if (!list.length) {
    const li = el(doc, 'li', 'pt-status-done-empty', '今天還沒有收工紀錄');
    ul.appendChild(li);
    return ul;
  }
  const shown = expanded || list.length <= DONE_PREVIEW_LIMIT
    ? list
    : list.slice(0, DONE_PREVIEW_LIMIT);
  const canLink = opts.canLinkGithub;
  shown.forEach((row) => {
    if (!row || typeof row !== 'object') return;
    const li = el(doc, 'li', 'pt-status-done-item');
    const clock = formatOfficeClock(row.at);
    if (clock) {
      li.appendChild(el(doc, 'span', 'pt-status-done-time', clock));
    }
    const phrase = phraseForDoneDisplay(row);
    if (phrase) li.appendChild(el(doc, 'span', 'pt-status-done-phrase', phrase));
    const prLink = linkPr(doc, row, canLink);
    if (prLink) li.appendChild(prLink);
    ul.appendChild(li);
  });
  if (list.length > DONE_PREVIEW_LIMIT) {
    appendExpandButton(doc, ul, expanded, list.length, opts.onToggleDone);
    if (expanded) ul.className = 'pt-status-done-list is-scroll';
  }
  return ul;
}

export function renderQueueList(doc, queue, ui) {
  const root = el(doc, 'div', 'pt-status-queue-block');
  const list = Array.isArray(queue) ? queue : [];
  const opts = ui && typeof ui === 'object' ? ui : {};
  const expanded = !!opts.queueExpanded;
  if (!list.length) {
    root.appendChild(el(doc, 'p', 'pt-status-queue-empty', '目前沒有排隊'));
    return root;
  }
  const shown = expanded || list.length <= QUEUE_PREVIEW_LIMIT
    ? list
    : list.slice(0, QUEUE_PREVIEW_LIMIT);
  const ul = el(doc, 'ul', 'pt-status-queue-list');
  const canLink = opts.canLinkGithub;
  shown.forEach((row) => {
    if (!row || typeof row !== 'object') return;
    const li = el(doc, 'li', 'pt-status-queue-item');
    const title = row.title && isSafePublicText(row.title) ? String(row.title) : '';
    if (title) li.appendChild(el(doc, 'span', 'pt-status-queue-title', title));
    const prLink = linkPr(doc, row, canLink);
    if (prLink) li.appendChild(prLink);
    ul.appendChild(li);
  });
  if (expanded) ul.className = 'pt-status-queue-list is-scroll';
  root.appendChild(ul);
  if (list.length > QUEUE_PREVIEW_LIMIT) {
    appendExpandButton(doc, root, expanded, list.length, opts.onToggleQueue);
  }
  return root;
}

export function fillStatusCard(doc, bodyEl, view, opts) {
  if (!bodyEl) return;
  bodyEl.replaceChildren();
  const bag = view && typeof view === 'object' ? view : {};
  const tiger = bag.tiger && typeof bag.tiger === 'object'
    ? bag.tiger
    : (bag.status != null || bag.active || bag.completedToday ? bag : { status: '', active: [], completedToday: [] });
  const queue = Array.isArray(bag.queue) ? bag.queue : [];
  const uiOpts = Object.assign({}, opts, {
    canLinkGithub: opts && opts.canLinkGithub
  });

  const stateWrap = el(doc, 'section', 'pt-status-section');
  stateWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '狀態'));
  const statusText = tiger.status == null || tiger.status === '' ? '待命中' : String(tiger.status);
  stateWrap.appendChild(el(doc, 'p', 'pt-status-state', statusText));
  bodyEl.appendChild(stateWrap);

  const activeWrap = el(doc, 'section', 'pt-status-section');
  activeWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '正在進行'));
  const actives = tiger.active && tiger.active.length ? tiger.active : [];
  if (actives.length) {
    actives.forEach((item) => activeWrap.appendChild(renderActiveBlock(doc, item)));
  } else {
    const idle = el(doc, 'p', 'pt-status-idle', statusIdleLine(tiger.status));
    activeWrap.appendChild(idle);
  }
  bodyEl.appendChild(activeWrap);

  const queueWrap = el(doc, 'section', 'pt-status-section');
  queueWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '排隊'));
  queueWrap.appendChild(renderQueueList(doc, queue, uiOpts));
  bodyEl.appendChild(queueWrap);

  const doneWrap = el(doc, 'section', 'pt-status-section');
  doneWrap.appendChild(el(doc, 'h3', 'pt-status-heading', '今天完成'));
  doneWrap.appendChild(renderCompletedList(doc, tiger.completedToday, uiOpts));
  bodyEl.appendChild(doneWrap);
}

function statusIdleLine(status) {
  const s = status == null ? '' : String(status).trim();
  if (s === '休息中') return '今天先休息，晚點再來看我。';
  if (s.startsWith('工作中')) return '正在忙，點開上方泡泡可隨時看進度。';
  return '待命中，有任務會立刻開工。';
}