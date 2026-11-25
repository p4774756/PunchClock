// 首頁秘書：GET /api/office/public 的 tiger 欄位解析與小卡內容組裝。

import { formatTaipeiClock, viewerCanLink } from './dev-machines-format.js';

const FORBIDDEN = /(?:ai\/issue-|\/Users\/|worktree|battery|charging|\.log\b)/i;

const DONE_PREVIEW_LIMIT = 3;
const QUEUE_PREVIEW_LIMIT = 5;
const ACTIVE_TITLE_MAX = 24;
const VAGUE_PHRASES = new Set(['完成一項工作', '正在處理一項工作']);
const STAGE_LABELS = ['開發', '審核', 'CI', '合併'];
const STAGE_TO_SEGMENT = {
  '開發中': 0,
  '審查中': 1,
  '等 CI': 2,
  '修正中': 2,
  '合併中': 3
};

/** 今日完成列穩定鍵（供輪詢差量比對新完成，不需另打 dev-status）。 */
export function completedTodayKeys(list) {
  if (!Array.isArray(list)) return [];
  return list.map((row, i) => {
    if (!row || typeof row !== 'object') return 'idx:' + i;
    if (row.issue != null && String(row.issue).trim() !== '') return 'issue:' + row.issue;
    if (row.pr != null && String(row.pr).trim() !== '') return 'pr:' + row.pr;
    if (row.at) return 'at:' + String(row.at);
    return 'idx:' + i;
  });
}

export function hasNewCompletedToday(previousKeys, completedToday) {
  if (!Array.isArray(previousKeys)) return false;
  const nextKeys = completedTodayKeys(completedToday);
  return nextKeys.some((k) => previousKeys.indexOf(k) === -1);
}

export function tigerFromPublic(body) {
  if (!body || body.success !== true || !body.characters) return null;
  const tiger = body.characters.tiger;
  if (!tiger || typeof tiger !== 'object') return null;
  return {
    status: tiger.status == null ? '' : String(tiger.status).trim(),
    active: Array.isArray(tiger.active) ? tiger.active : [],
    completedToday: Array.isArray(tiger.completedToday) ? tiger.completedToday : [],
    queue: Array.isArray(tiger.queue) ? tiger.queue : []
  };
}

/** 合併 GitHub 與辦公室公開 API 的排隊列（以 issue 去重，心跳 waiter 優先補洞）。 */
export function mergeSecretaryQueues(githubQueue, tigerQueue) {
  const out = [];
  const seen = new Set();
  const push = (row) => {
    if (!row || typeof row !== 'object') return;
    const issue = Number(row.issue);
    const key = Number.isFinite(issue) && issue > 0 ? 'i:' + issue : 't:' + String(row.title || row.pair || '');
    if (seen.has(key)) return;
    seen.add(key);
    out.push(row);
  };
  (Array.isArray(tigerQueue) ? tigerQueue : []).forEach(push);
  (Array.isArray(githubQueue) ? githubQueue : []).forEach(push);
  return out;
}

export function devQueueFromStatus(body) {
  if (!body || body.success === false) return [];
  const now = body.now && typeof body.now === 'object' ? body.now : {};
  const queue = Array.isArray(now.queue) ? now.queue : [];
  return queue.filter((row) => row && (row.title || row.pair || row.issue));
}

export function fetchSecretaryDevQueue(fetchFn) {
  if (!fetchFn) return Promise.resolve([]);
  const office = fetchFn('/api/office/public')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null);
  const status = fetchFn('/api/dev-status')
    .then((r) => (r.ok ? r.json() : null))
    .catch(() => null);
  return Promise.all([office, status]).then(([officeBody, statusBody]) => {
    const tiger = tigerFromPublic(officeBody || {});
    const githubQ = devQueueFromStatus(statusBody || {});
    return mergeSecretaryQueues(githubQ, tiger ? tiger.queue : []);
  });
}

/** 3D 左牆進度面板文案（須先 isSafePublicText 過濾）。 */
export function progressBoardViewFromTiger(tiger, queueCount) {
  const bag = tiger && typeof tiger === 'object' ? tiger : { status: '', active: [], completedToday: [] };
  const actives = Array.isArray(bag.active) ? bag.active : [];
  const active = actives.length ? actives[0] : null;
  const completed = Array.isArray(bag.completedToday) ? bag.completedToday.length : 0;
  const activeN = actives.length;
  const queue = Math.max(0, Number(queueCount) || 0);
  const todayTotal = completed + activeN + queue;
  const view = {
    statusLine: '待命中',
    issueLine: '',
    phraseLine: '',
    metaLine: '',
    todayLabel: '',
    todayFraction: null
  };
  if (active) {
    const issue = Number(active.issue);
    if (Number.isFinite(issue) && issue > 0) {
      view.issueLine = '#' + issue;
    }
    view.phraseLine = phraseForActiveDisplay(active);
    if (view.phraseLine === ACTIVE_DISPLAY_FALLBACK) {
      view.phraseLine = '進行中';
    }
    const caps = capsulesForActive(active);
    if (caps.length) view.metaLine = caps.join(' · ');
    view.statusLine = '進行中';
  } else if (completed > 0) {
    view.metaLine = '今日已完成 ' + completed + ' 項';
  }
  if (todayTotal > 0) {
    view.todayLabel = '今日工作量';
    view.todayFraction = Math.min(1, completed / todayTotal);
    view.todayCountText = completed + '/' + todayTotal;
  }
  return view;
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
  if (item.stage && isSafePublicText(item.stage)) {
    out.push(String(item.stage));
  }
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

const ACTIVE_DISPLAY_FALLBACK = '正在處理一項工作';

/** 進行中主文：空泛 phrase 時改顯示 title。 */
export function phraseForActiveDisplay(row) {
  const item = row && typeof row === 'object' ? row : {};
  const phrase = item.phrase && isSafePublicText(item.phrase) ? String(item.phrase).trim() : '';
  if (phrase && !VAGUE_PHRASES.has(phrase)) {
    return phrase;
  }
  const title = item.title && isSafePublicText(item.title) ? String(item.title).trim() : '';
  if (title) return title;
  if (phrase && VAGUE_PHRASES.has(phrase)) {
    const issue = Number(item.issue);
    if (Number.isFinite(issue) && issue > 0) return '';
  }
  return phrase || ACTIVE_DISPLAY_FALLBACK;
}

/** 進行中卡片標題截短（約 24 全形字）。 */
export function truncateActiveTitle(text, max = ACTIVE_TITLE_MAX) {
  const s = text == null ? '' : String(text).trim();
  if (!s) return '';
  let width = 0;
  let i = 0;
  for (; i < s.length; ) {
    const cp = s.codePointAt(i);
    const w = cp > 0xff ? 2 : 1;
    if (width + w > max) {
      return s.slice(0, i) + '…';
    }
    width += w;
    i += cp > 0xffff ? 2 : 1;
  }
  return s;
}

export function activeStageForItem(item) {
  const row = item && typeof item === 'object' ? item : {};
  const stage = row.stage && isSafePublicText(row.stage) ? String(row.stage).trim() : '開發中';
  return STAGE_TO_SEGMENT[stage] == null ? '開發中' : stage;
}

export function activeCiFailed(item) {
  const row = item && typeof item === 'object' ? item : {};
  if (row.failed === true) return true;
  const stage = activeStageForItem(row);
  if (stage !== '等 CI' && stage !== '修正中') return false;
  const verdict = row.verdict != null ? String(row.verdict).trim() : '';
  return verdict === 'FAIL';
}

function reviewWasCompleted(item, stage) {
  const round = Number(item.round);
  return Number.isFinite(round) && round >= 2;
}

/** 四段進度條狀態：done | current | pending | fail */
export function progressSegmentsForActive(item) {
  const row = item && typeof item === 'object' ? item : {};
  const stage = activeStageForItem(row);
  const current = STAGE_TO_SEGMENT[stage] ?? 0;
  const ciFail = activeCiFailed(row);
  const reviewDone = reviewWasCompleted(row, stage);
  return STAGE_LABELS.map((label, index) => {
    if (index < current) {
      if (label === '審核' && !reviewDone) return { label, state: 'pending' };
      return { label, state: 'done' };
    }
    if (index === current) {
      if (label === 'CI' && ciFail) return { label, state: 'fail' };
      return { label, state: 'current' };
    }
    return { label, state: 'pending' };
  });
}

export function statusLineForActive(item) {
  const row = item && typeof item === 'object' ? item : {};
  const stage = activeStageForItem(row);
  if (activeCiFailed(row)) {
    return 'CI 失敗，等待處理';
  }
  const minutes = Number(row.minutes);
  const minText = Number.isFinite(minutes) && minutes > 0 ? '・' + minutes + ' 分' : '';
  if (stage === '審查中') return '審核中' + minText;
  if (stage === '等 CI') return '等 CI' + minText;
  return stage + minText;
}

function chainDotClass(chain) {
  const c = chain == null ? '' : String(chain).trim();
  if (c === '青龍') return 'is-azure';
  if (c === '白虎') return 'is-white';
  if (c === '朱雀') return 'is-vermilion';
  if (c === '玄武') return 'is-obsidian';
  return '';
}

function renderStageProgress(doc, item) {
  const track = el(doc, 'div', 'pt-status-progress');
  progressSegmentsForActive(item).forEach((seg) => {
    const node = el(doc, 'div', 'pt-status-progress-seg ' + seg.state);
    node.appendChild(el(doc, 'span', 'pt-status-progress-fill'));
    node.appendChild(el(doc, 'span', 'pt-status-progress-label', seg.label));
    track.appendChild(node);
  });
  return track;
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

function linkIssue(doc, item, linkClass) {
  const issue = Number(item.issue);
  if (!Number.isFinite(issue) || issue <= 0) return null;
  const a = el(doc, 'a', linkClass || 'pt-status-link');
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
  const row = item && typeof item === 'object' ? item : {};
  const wrap = el(doc, 'div', 'pt-status-active-item pt-status-stage-card');
  const head = el(doc, 'div', 'pt-status-stage-head');
  const chain = row.chain && isSafePublicText(row.chain) ? String(row.chain).trim() : '';
  if (chain) {
    const dot = el(doc, 'span', 'pt-status-chain-dot ' + chainDotClass(chain));
    dot.setAttribute('aria-hidden', 'true');
    head.appendChild(dot);
  }
  const issue = Number(row.issue);
  if (Number.isFinite(issue) && issue > 0) {
    const issueLink = linkIssue(doc, row, 'pt-status-link pt-status-stage-issue');
    if (issueLink) {
      head.appendChild(issueLink);
    } else {
      head.appendChild(el(doc, 'span', 'pt-status-stage-issue', '#' + issue));
    }
  }
  const titleText = truncateActiveTitle(phraseForActiveDisplay(row));
  if (titleText) {
    head.appendChild(el(doc, 'span', 'pt-status-stage-title', titleText));
  }
  wrap.appendChild(head);
  wrap.appendChild(renderStageProgress(doc, row));
  wrap.appendChild(el(doc, 'p', 'pt-status-stage-meta', statusLineForActive(row)));
  const links = el(doc, 'div', 'pt-status-links');
  const prLink = linkPr(doc, row, true);
  if (prLink) links.appendChild(prLink);
  if (links.childNodes.length) wrap.appendChild(links);
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

/** 依捲動位置切換狀態卡內文上下可捲提示（搭配 .pt-status-scroll）。 */
export function syncStatusBodyScroll(scrollEl, bodyEl) {
  if (!scrollEl || !bodyEl || typeof scrollEl.classList?.toggle !== 'function') return;
  const maxScroll = bodyEl.scrollHeight - bodyEl.clientHeight;
  const canScroll = maxScroll > 2;
  const scrollTop = bodyEl.scrollTop || 0;
  const atBottom = scrollTop >= maxScroll - 2;
  const atTop = scrollTop <= 1;
  scrollEl.classList.toggle('can-scroll-below', canScroll && !atBottom);
  scrollEl.classList.toggle('can-scroll-above', canScroll && !atTop);
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
  if (opts && opts.flashCompleted) {
    doneWrap.className = 'pt-status-section pt-status-done-flash';
  }
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