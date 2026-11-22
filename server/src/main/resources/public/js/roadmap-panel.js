// 施工進度面板與告示牌文字。不依賴 Three.js。

import {
  applyGithubSourceLabel,
  createGithubLink,
  devStatusItemMatchesSearch,
  effectivePhaseStatus,
  mergeRoadmapDevStatusLink,
  parseRoadmapSearchQuery,
  phaseItemProgress,
  roadmapItemMatchesSearch,
  roadmapItemTotals,
  shouldShowDevStatusNavLink,
  viewerCanLink
} from './dev-machines-format.js';

export { effectivePhaseStatus, phaseItemProgress, roadmapItemTotals };

export function signCopy(doc) {
  const phases = (doc && doc.phases) || [];
  const numbered = phases.filter((phase) => phase && phase.phase != null);
  const current = doc && doc.current != null ? doc.current : null;
  if (current == null && numbered.length > 0 && numbered.every((phase) => effectivePhaseStatus(phase, current) === 'done')) {
    return { headline: '群島完工', title: '' };
  }
  if (current != null) {
    const phase = phases.find((item) => item && item.phase === current);
    return { headline: '第 ' + current + ' 期施工中', title: phase && phase.title ? phase.title : '' };
  }
  let latestActive = null;
  for (let i = 0; i < numbered.length; i++) {
    const phase = numbered[i];
    const status = effectivePhaseStatus(phase, current);
    if (status === 'active' || status === 'progress') {
      if (!latestActive || phase.phase > latestActive.phase) latestActive = phase;
    }
  }
  if (latestActive) {
    const st = effectivePhaseStatus(latestActive, current);
    const headline = st === 'active'
      ? '第 ' + latestActive.phase + ' 期施工中'
      : '第 ' + latestActive.phase + ' 期進行中';
    return {
      headline: headline,
      title: latestActive.title ? latestActive.title : ''
    };
  }
  return { headline: '施工進度', title: '' };
}

export function phaseStatus(phase, current) {
  return effectivePhaseStatus(phase, current);
}

const STATUS_LABEL = { done: '已完成', active: '施工中', progress: '進行中', pending: '未開工' };

function phaseStatusLabel(status) {
  return STATUS_LABEL[status] || STATUS_LABEL.pending;
}
const QUEUE_PREVIEW_LIMIT = 5;

function progressPercent(done, total) {
  if (!total || total <= 0) return 0;
  return Math.max(0, Math.min(100, Math.round((done / total) * 100)));
}

function setBar(node, done, total, status) {
  if (!node) return;
  const pct = progressPercent(done, total);
  node.style.width = pct + '%';
  node.classList.remove('is-done', 'is-pending');
  if (status === 'done') node.classList.add('is-done');
  else if (status === 'pending') node.classList.add('is-pending');
}

function phaseMark(status) {
  if (status === 'done') return '✓';
  if (status === 'active' || status === 'progress') return '▸';
  return '○';
}

function normalizeItem(item) {
  if (item == null) return { text: '', done: false, issue: null, pr: null };
  if (typeof item === 'string') return { text: item, done: false, issue: null, pr: null };
  return {
    text: item.text == null ? '' : String(item.text),
    done: !!item.done,
    issue: item.issue != null ? item.issue : null,
    pr: item.pr != null ? item.pr : null
  };
}

function renderRoadmapItemLi(doc, item, canLink, query, highlight) {
  const li = doc.createElement('li');
  if (highlight) li.classList.add('is-search-hit');
  const box = doc.createElement('span');
  box.className = 'roadmap-check' + (item.done ? ' is-on' : '');
  box.setAttribute('aria-hidden', 'true');
  li.appendChild(box);
  const text = doc.createElement('span');
  text.className = 'roadmap-item-text';
  if (item.issue != null) {
    text.appendChild(doc.createTextNode(item.text + ' '));
    text.appendChild(linkEl(
      doc,
      null,
      'https://github.com/p4774756/PunchClock/issues/' + item.issue,
      '#' + item.issue,
      canLink
    ));
  } else {
    text.textContent = item.text;
  }
  if (item.pr != null) {
    text.appendChild(doc.createTextNode(' '));
    text.appendChild(linkEl(
      doc,
      'roadmap-dev-pr',
      'https://github.com/p4774756/PunchClock/pull/' + item.pr,
      'p' + item.pr,
      canLink
    ));
  }
  li.appendChild(text);
  if (query && query.kind !== 'none' && !roadmapItemMatchesSearch(query, item)) {
    li.hidden = true;
  }
  return li;
}

function appendPhaseItems(doc, parent, items, canLink, query) {
  const open = items.filter((it) => it && !it.done);
  const done = items.filter((it) => it && it.done);
  if (open.length) {
    const ul = doc.createElement('ul');
    open.forEach((item) => {
      const hit = !query || query.kind === 'none' || roadmapItemMatchesSearch(query, item);
      ul.appendChild(renderRoadmapItemLi(doc, item, canLink, query, hit && query && query.kind !== 'none'));
    });
    parent.appendChild(ul);
  }
  if (done.length) {
    const bucket = doc.createElement('details');
    bucket.className = 'roadmap-done-bucket';
    const hasVisibleDone = query && query.kind !== 'none'
      ? done.some((item) => roadmapItemMatchesSearch(query, item))
      : true;
    bucket.open = !!(query && query.kind !== 'none' && hasVisibleDone);
    const summary = doc.createElement('summary');
    summary.textContent = '已完成 ' + done.length + ' 項';
    bucket.appendChild(summary);
    const inner = doc.createElement('ul');
    done.forEach((item) => {
      const hit = !query || query.kind === 'none' || roadmapItemMatchesSearch(query, item);
      inner.appendChild(renderRoadmapItemLi(doc, item, canLink, query, hit && query && query.kind !== 'none'));
    });
    bucket.appendChild(inner);
    parent.appendChild(bucket);
  }
}

function shouldDefaultOpen(phase, status) {
  if (status === 'done') return false;
  if (status === 'active' || status === 'progress') return true;
  const pr = phaseItemProgress(phase);
  return pr.total > 0 && pr.done > 0 && pr.done < pr.total;
}

/** 台北時間（UTC+8），附「台北」標籤。 */
export function formatTaipeiTime(ms) {
  const n = Number(ms);
  if (!Number.isFinite(n) || n <= 0) return '';
  const d = new Date(n);
  const parts = new Intl.DateTimeFormat('zh-TW', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(d);
  const get = (type) => {
    const hit = parts.find((p) => p.type === type);
    return hit ? hit.value : '';
  };
  return get('year') + '/' + get('month') + '/' + get('day') + ' ' + get('hour') + ':' + get('minute') + ' 台北';
}

export function emptyDevStatusMessage(data) {
  if (!data) return '暫時讀不到開發進度';
  if (data.message) return String(data.message);
  if (data.stale) return '顯示的是上次讀到的開發進度';
  return '';
}

function clipDevText(text, maxLen) {
  const s = text == null ? '' : String(text);
  if (s.length <= maxLen) return s;
  return s.slice(0, Math.max(1, maxLen - 1)) + '…';
}

/** 研發樓螢幕單行排版：pair 後的中日文標題按字元截斷，不在空白處整段砍掉。 */
export function fitCanvasLine(ctx, text, maxW) {
  if (!text || !ctx || typeof ctx.measureText !== 'function') return text || '';
  if (ctx.measureText(text).width <= maxW) return text;
  const ell = '…';
  const headMatch = /^(.*[：:]\s*(?:i\d+|p\d+|\([^)]*\))[^\s]*\s*)/.exec(text);
  const minKeep = headMatch ? headMatch[1].length : 0;
  let fit = text.length;
  while (fit > minKeep && ctx.measureText(text.slice(0, fit) + ell).width > maxW) fit -= 1;
  if (fit < 1) return ell;
  let cut = fit;
  if (fit < text.length) {
    const chunk = text.slice(0, fit);
    const sp = chunk.lastIndexOf(' ');
    const keepHead = sp > minKeep && ctx.measureText(text.slice(0, sp) + ell).width <= maxW;
    if (keepHead) cut = sp;
  }
  return text.slice(0, cut) + ell;
}

function devFocusLine(label, item) {
  if (!item) return label + '：目前沒有';
  const pair = item.pair || '';
  const title = clipDevText(item.title || '', 16);
  const status = item.status ? '（' + item.status + '）' : '';
  const head = pair ? pair + ' ' : '';
  return label + '：' + head + title + status;
}

function normalizeCurrentList(raw) {
  if (Array.isArray(raw)) {
    return raw.filter((row) => row && (row.pair || row.title || row.issue));
  }
  if (raw && (raw.pair || raw.title || raw.issue)) {
    return [raw];
  }
  return [];
}

function currentMatchesSearch(query, list) {
  if (!query || query.kind === 'none') return true;
  return list.some((row) => devStatusItemMatchesSearch(query, row));
}

/** 研發樓室內螢幕用的純文字行（Canvas 繪製，不含 DOM）。 */
export function formatLabDevScreenLines(data, opts) {
  const maxQueue = (opts && opts.maxQueue) || 3;
  const detailHint = '詳細進度請到主島施工告示牌';
  if (!data || data.success === false) {
    const msg = emptyDevStatusMessage(data) || '開發進度暫時讀不到';
    return ['【開發進度】', '', msg, '', detailHint];
  }
  const now = data.now || {};
  const currentList = normalizeCurrentList(now.current);
  const hasCurrent = currentList.length > 0;
  const hasNext = !!(now.next && (now.next.pair || now.next.title));
  const queue = Array.isArray(now.queue) ? now.queue : [];
  const hasQueue = queue.some((entry) => entry && (entry.pair || entry.title));
  const stats = data.stats || {};
  if (!hasCurrent && !hasNext && !hasQueue) {
    const msg = emptyDevStatusMessage(data) || '開發進度暫時讀不到';
    return ['【開發進度】', '', msg, '', detailHint];
  }
  const lines = [];
  const staleNote = data.stale ? '（快取）' : '';
  lines.push('【開發進度】' + staleNote);
  lines.push('');
  if (hasCurrent) {
    lines.push('現在在做：');
    currentList.forEach((item) => {
      const pair = item.pair ? item.pair + ' ' : '';
      const title = clipDevText(item.title || '', 16);
      const status = item.status ? '（' + item.status + '）' : '';
      lines.push('  ' + pair + title + status);
    });
  } else {
    lines.push('現在在做：目前沒有');
  }
  lines.push(devFocusLine('下一張', now.next));
  lines.push('');
  lines.push('排隊：');
  if (!hasQueue) {
    lines.push('  尚無排隊標籤');
  } else {
    const slice = queue.slice(0, maxQueue);
    for (let i = 0; i < slice.length; i++) {
      const entry = slice[i] || {};
      const order = entry.order != null ? entry.order + '. ' : '';
      const pair = entry.pair || '';
      const title = clipDevText(entry.title || '', 12);
      const status = entry.status ? ' · ' + entry.status : '';
      lines.push('  ' + order + pair + ' ' + title + status);
    }
    if (queue.length > maxQueue) {
      lines.push('  …還有 ' + (queue.length - maxQueue) + ' 項');
    }
  }
  if (stats.mergedThisWeek != null || stats.openIssues != null) {
    lines.push('');
    const merged = stats.mergedThisWeek != null ? stats.mergedThisWeek : 0;
    const open = stats.openIssues != null ? stats.openIssues : 0;
    lines.push('本週合併 ' + merged + ' · 未關 issue ' + open);
  }
  lines.push('');
  lines.push(detailHint);
  return lines;
}

function clearChildren(el) {
  if (!el) return;
  while (el.firstChild) el.removeChild(el.firstChild);
}

function textEl(doc, tag, className, text) {
  const el = doc.createElement(tag);
  if (className) el.className = className;
  el.textContent = text == null ? '' : String(text);
  return el;
}

function linkEl(doc, className, href, text, canLink) {
  return createGithubLink(doc, {
    className: className || 'dev-link',
    href: href || '#',
    text: text == null ? '' : String(text),
    canLink: canLink
  });
}

function renderFocusCard(doc, root, label, item, canLink) {
  if (!root) return;
  clearChildren(root);
  root.appendChild(textEl(doc, 'span', 'roadmap-dev-kicker', label));
  if (!item) {
    root.appendChild(textEl(doc, 'p', 'roadmap-dev-empty', '目前沒有'));
    return;
  }
  const pair = linkEl(doc, 'roadmap-dev-pair', item.issueUrl || item.prUrl || '#', item.pair || '', canLink);
  root.appendChild(pair);
  root.appendChild(textEl(doc, 'p', 'roadmap-dev-title', item.title || ''));
  root.appendChild(textEl(doc, 'span', 'roadmap-dev-status', item.status || ''));
}

function renderCurrentBlock(doc, root, label, items, canLink) {
  if (!root) return;
  clearChildren(root);
  root.appendChild(textEl(doc, 'span', 'roadmap-dev-kicker', label));
  const list = Array.isArray(items) ? items.filter(Boolean) : [];
  if (!list.length) {
    root.appendChild(textEl(doc, 'p', 'roadmap-dev-empty', '目前沒有'));
    return;
  }
  const stack = doc.createElement('div');
  stack.className = 'roadmap-dev-current-stack';
  list.forEach((item) => {
    const block = doc.createElement('div');
    block.className = 'roadmap-dev-current-entry';
    block.appendChild(linkEl(doc, 'roadmap-dev-pair', item.issueUrl || item.prUrl || '#', item.pair || '', canLink));
    block.appendChild(textEl(doc, 'p', 'roadmap-dev-title', item.title || ''));
    block.appendChild(textEl(doc, 'span', 'roadmap-dev-status', item.status || ''));
    stack.appendChild(block);
  });
  root.appendChild(stack);
}

function renderQueue(doc, root, queue, canLink, query, queueOpts) {
  if (!root) return;
  clearChildren(root);
  const list = Array.isArray(queue) ? queue : [];
  const visible = query && query.kind !== 'none'
    ? list.filter((row) => row && devStatusItemMatchesSearch(query, row))
    : list;
  if (!visible.length) {
    root.appendChild(textEl(doc, 'p', 'roadmap-dev-empty', '尚無排隊標籤'));
    return;
  }
  const expanded = !!(queueOpts && queueOpts.expanded);
  const shown = expanded || visible.length <= QUEUE_PREVIEW_LIMIT
    ? visible
    : visible.slice(0, QUEUE_PREVIEW_LIMIT);
  const ol = doc.createElement('ol');
  ol.className = 'roadmap-dev-queue-list';
  shown.forEach((entry) => {
    const li = doc.createElement('li');
    if (query && query.kind !== 'none') li.classList.add('is-search-hit');
    const order = entry && entry.order != null ? entry.order : '';
    const item = entry || {};
    li.appendChild(textEl(doc, 'span', 'roadmap-dev-order', String(order)));
    li.appendChild(linkEl(doc, 'roadmap-dev-pair', item.issueUrl || '#', item.pair || '', canLink));
    li.appendChild(textEl(doc, 'span', 'roadmap-dev-qtitle', item.title || ''));
    li.appendChild(textEl(doc, 'span', 'roadmap-dev-status', item.status || ''));
    ol.appendChild(li);
  });
  root.appendChild(ol);
  if (visible.length > QUEUE_PREVIEW_LIMIT) {
    const btn = doc.createElement('button');
    btn.type = 'button';
    btn.className = 'roadmap-dev-queue-toggle';
    btn.textContent = expanded ? '收合' : ('顯示全部（' + visible.length + '）');
    btn.addEventListener('click', () => {
      if (queueOpts && typeof queueOpts.onToggle === 'function') queueOpts.onToggle();
    });
    root.appendChild(btn);
  }
}

function renderStats(doc, root, stats) {
  if (!root) return;
  clearChildren(root);
  const s = stats || {};
  root.appendChild(textEl(doc, 'p', 'roadmap-dev-week', s.weekLabel || '本週（週一 00:00 起，台北）'));
  const grid = doc.createElement('div');
  grid.className = 'roadmap-dev-stats-grid';
  const merged = doc.createElement('div');
  merged.appendChild(textEl(doc, 'strong', null, String(s.mergedThisWeek != null ? s.mergedThisWeek : 0)));
  merged.appendChild(textEl(doc, 'span', null, '本週合併'));
  const open = doc.createElement('div');
  open.appendChild(textEl(doc, 'strong', null, String(s.openIssues != null ? s.openIssues : 0)));
  open.appendChild(textEl(doc, 'span', null, '未關閉 issue'));
  grid.appendChild(merged);
  grid.appendChild(open);
  root.appendChild(grid);
}

export function createRoadmapController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const hooks = Object.assign({}, (opts && opts.hooks) || {});
  if (typeof hooks.onChange !== 'function' && opts && typeof opts.onChange === 'function') {
    hooks.onChange = opts.onChange;
  }
  const root = doc && doc.getElementById ? doc.getElementById('roadmapBoard') : null;
  const headline = root && root.querySelector('[data-roadmap-headline]');
  const titleEl = root && root.querySelector('[data-roadmap-title]');
  const listEl = root && root.querySelector('[data-roadmap-list]');
  const noteEl = root && root.querySelector('[data-roadmap-note]');
  const sourceEl = root && root.querySelector('[data-roadmap-source]');
  const backBtn = root && root.querySelector('[data-roadmap-back]');
  const totalLabel = root && root.querySelector('[data-roadmap-total-label]');
  const totalBar = root && root.querySelector('[data-roadmap-total-bar]');
  const searchInput = root && root.querySelector('[data-roadmap-search]');
  const searchClear = root && root.querySelector('[data-roadmap-search-clear]');
  const searchEmpty = root && root.querySelector('[data-roadmap-search-empty]');
  const tabButtons = root ? root.querySelectorAll('[data-roadmap-tab]') : [];
  const tabPanels = root ? root.querySelectorAll('[data-roadmap-panel]') : [];
  const currentEl = root && root.querySelector('[data-dev-current]');
  const nextEl = root && root.querySelector('[data-dev-next]');
  const queueEl = root && root.querySelector('[data-dev-queue]');
  const statsEl = root && root.querySelector('[data-dev-stats]');
  const devNoteEl = root && root.querySelector('[data-dev-note]');
  const devMachinesNav = root && root.querySelector('[data-dev-machines-nav]');
  const win = (opts && opts.win) || globalThis;
  let openState = false;
  let latest = null;
  let latestDev = null;
  let queueExpanded = false;
  let activeTab = 'phases';
  let canLinkGithub = false;
  let searchRaw = '';

  function searchQuery() {
    return parseRoadmapSearchQuery(searchRaw);
  }

  function updateSearchChrome() {
    const active = searchRaw.trim().length > 0;
    if (searchClear) searchClear.hidden = !active;
  }

  function repaintSearchEmpty(anyHit) {
    if (!searchEmpty) return;
    const q = searchQuery();
    searchEmpty.hidden = q.kind === 'none' || anyHit;
    searchEmpty.textContent = '找不到';
  }

  function setTab(tab) {
    activeTab = tab === 'dev' ? 'dev' : 'phases';
    tabButtons.forEach((btn) => {
      const on = btn.getAttribute('data-roadmap-tab') === activeTab;
      btn.classList.toggle('is-active', on);
      btn.setAttribute('aria-pressed', on ? 'true' : 'false');
    });
    tabPanels.forEach((panel) => {
      const show = panel.getAttribute('data-roadmap-panel') === activeTab;
      panel.hidden = !show;
    });
  }

  const tablist = root && root.querySelector('.roadmap-tabs');
  if (tablist) {
    tablist.addEventListener('click', (ev) => {
      const btn = ev.target.closest('[data-roadmap-tab]');
      if (!btn || !root.contains(btn)) return;
      ev.preventDefault();
      setTab(btn.getAttribute('data-roadmap-tab'));
    });
  }
  setTab('phases');

  if (searchInput) {
    searchInput.addEventListener('input', () => {
      searchRaw = searchInput.value || '';
      updateSearchChrome();
      if (latest) apply(latest);
      if (latestDev) applyDev(latestDev);
    });
  }
  if (searchClear) {
    searchClear.addEventListener('click', () => {
      searchRaw = '';
      if (searchInput) searchInput.value = '';
      updateSearchChrome();
      if (searchEmpty) searchEmpty.hidden = true;
      if (latest) apply(latest);
      if (latestDev) applyDev(latestDev);
      if (searchInput && typeof searchInput.focus === 'function') searchInput.focus();
    });
  }
  updateSearchChrome();

  function lockBackground(want) {
    const body = doc && doc.body;
    if (!body || !body.style) return;
    if (want) {
      const y = win && typeof win.scrollY === 'number' ? win.scrollY : 0;
      if (body.dataset) body.dataset.roadmapScrollY = String(y);
      body.style.top = (-y) + 'px';
      return;
    }
    const y = body.dataset ? Number(body.dataset.roadmapScrollY || 0) : 0;
    body.style.top = '';
    if (body.dataset) delete body.dataset.roadmapScrollY;
    if (win && typeof win.scrollTo === 'function') win.scrollTo(0, Number.isFinite(y) ? y : 0);
  }

  function setOpen(next) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (doc.body) doc.body.classList.toggle('roadmap-open', want);
    lockBackground(want);
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
    if (want) refresh();
  }

  function apply(data) {
    latest = data || null;
    const copy = signCopy(latest);
    if (headline) headline.textContent = copy.headline;
    if (titleEl) titleEl.textContent = copy.title || '';
    if (noteEl) {
      if (latest && latest.message && (!latest.phases || !latest.phases.length)) {
        noteEl.textContent = latest.message;
      } else if (latest && latest.stale) {
        noteEl.textContent = '顯示的是上次讀到的進度';
      } else {
        noteEl.textContent = '';
      }
    }
    if (sourceEl) {
      const href = latest && latest.source ? latest.source : 'https://github.com/p4774756/PunchClock/milestones';
      applyGithubSourceLabel(doc, sourceEl, href, canLinkGithub);
    }
    const phases = (latest && latest.phases) || [];
    const current = latest ? latest.current : null;
    const query = searchQuery();
    const totals = roadmapItemTotals(phases);
    if (totalLabel) {
      totalLabel.textContent = '子項合計 ' + totals.done + '/' + totals.total;
    }
    const totalStatus = totals.total > 0 && totals.done >= totals.total ? 'done' : 'active';
    setBar(totalBar, totals.done, totals.total, totalStatus);
    if (listEl) {
      while (listEl.firstChild) listEl.removeChild(listEl.firstChild);
      let anyPhaseHit = false;
      phases.forEach((phase) => {
        if (!phase) return;
        const items = (phase.items || []).map(normalizeItem);
        const phaseMatches = query.kind === 'none' || items.some((item) => roadmapItemMatchesSearch(query, item));
        if (query.kind !== 'none' && !phaseMatches) {
          return;
        }
        if (query.kind !== 'none' && phaseMatches) anyPhaseHit = true;
        const status = effectivePhaseStatus(phase, current);
        const pr = phaseItemProgress(phase);
        const row = doc.createElement('details');
        row.className = 'roadmap-phase is-' + status;
        row.open = query.kind !== 'none' && phaseMatches ? true : shouldDefaultOpen(phase, status);
        const summary = doc.createElement('summary');
        const mark = doc.createElement('span');
        mark.className = 'roadmap-mark';
        mark.textContent = phaseMark(status);
        const name = doc.createElement('span');
        name.className = 'roadmap-name';
        name.textContent = phase.phase == null
          ? (phase.title || '')
          : ('第 ' + phase.phase + ' 期 ' + (phase.title || ''));
        const prog = doc.createElement('div');
        prog.className = 'roadmap-phase-progress';
        const track = doc.createElement('div');
        track.className = 'roadmap-progress-track';
        const bar = doc.createElement('span');
        bar.className = 'roadmap-progress-bar';
        setBar(bar, pr.done, pr.total, status);
        track.appendChild(bar);
        prog.appendChild(track);
        const ratio = doc.createElement('span');
        ratio.className = 'roadmap-ratio';
        ratio.textContent = pr.done + '/' + pr.total;
        const tag = doc.createElement('span');
        tag.className = 'roadmap-tag';
        tag.textContent = phaseStatusLabel(status);
        summary.appendChild(mark);
        summary.appendChild(name);
        summary.appendChild(prog);
        summary.appendChild(ratio);
        summary.appendChild(tag);
        row.appendChild(summary);
        if (items.length) {
          appendPhaseItems(doc, row, items, canLinkGithub, query);
        }
        if (phase.progressNote) {
          row.appendChild(textEl(doc, 'p', 'roadmap-phase-note', phase.progressNote));
        }
        listEl.appendChild(row);
      });
      let devHit = false;
      if (latestDev && query.kind !== 'none') {
        const now = latestDev.now || {};
        const queue = Array.isArray(now.queue) ? now.queue : [];
        const currentList = normalizeCurrentList(now.current);
        devHit = currentList.some((row) => devStatusItemMatchesSearch(query, row))
          || (now.next && devStatusItemMatchesSearch(query, now.next))
          || queue.some((row) => devStatusItemMatchesSearch(query, row));
      }
      repaintSearchEmpty(anyPhaseHit || devHit || query.kind === 'none');
    }
    if (typeof hooks.onData === 'function') hooks.onData(latest, copy);
  }

  function applyDev(data) {
    latestDev = data || null;
    if (devNoteEl) {
      const msg = emptyDevStatusMessage(latestDev);
      if (latestDev && latestDev.message) {
        devNoteEl.textContent = msg;
      } else if (latestDev && latestDev.stale) {
        devNoteEl.textContent = '顯示的是上次讀到的開發進度';
      } else {
        devNoteEl.textContent = '';
      }
    }
    const now = (latestDev && latestDev.now) || {};
    const query = searchQuery();
    let currentList = normalizeCurrentList(now.current);
    if (query.kind !== 'none') {
      currentList = currentList.filter((row) => devStatusItemMatchesSearch(query, row));
    }
    const next = query.kind === 'none' || !now.next || devStatusItemMatchesSearch(query, now.next)
      ? now.next || null
      : null;
    renderCurrentBlock(doc, currentEl, '現在在做', currentList, canLinkGithub);
    renderFocusCard(doc, nextEl, '下一張', next, canLinkGithub);
    renderQueue(doc, queueEl, now.queue || [], canLinkGithub, query, {
      expanded: queueExpanded,
      onToggle: () => {
        queueExpanded = !queueExpanded;
        applyDev(latestDev);
      }
    });
    renderStats(doc, statsEl, latestDev && latestDev.stats);
    if (query.kind !== 'none') {
      let anyPhaseHit = false;
      const phases = (latest && latest.phases) || [];
      phases.forEach((phase) => {
        const items = (phase && phase.items ? phase.items : []).map(normalizeItem);
        if (items.some((item) => roadmapItemMatchesSearch(query, item))) anyPhaseHit = true;
      });
      const queue = Array.isArray(now.queue) ? now.queue : [];
      const allCurrent = normalizeCurrentList(now.current);
      const devHit = currentMatchesSearch(query, allCurrent)
        || (next && devStatusItemMatchesSearch(query, next))
        || queue.some((row) => devStatusItemMatchesSearch(query, row));
      repaintSearchEmpty(anyPhaseHit || devHit);
    }
  }

  function refreshAuthChrome() {
    const authed = !!(win && win.PUNCHCLOCK_AUTHENTICATED === true);
    canLinkGithub = viewerCanLink(authed);
    mergeRoadmapDevStatusLink(doc, devMachinesNav, { authenticated: authed });
    if (latest) apply(latest);
    if (latestDev) applyDev(latestDev);
  }

  function refresh() {
    refreshAuthChrome();
    const fetchFn = (opts && opts.fetch) || (globalThis.fetch ? globalThis.fetch.bind(globalThis) : null);
    if (!fetchFn) return;
    fetchFn('/api/roadmap').then((res) => res.json()).then((body) => {
      apply(body);
    }).catch(() => {
      apply({ success: false, phases: [], message: '暫時讀不到施工進度', stale: false, current: null });
    });
    fetchFn('/api/dev-status').then((res) => res.json()).then((body) => {
      applyDev(body);
    }).catch(() => {
      applyDev({ success: false, stale: false, now: { current: [], next: null, queue: [] }, timeline: [], stats: { weekLabel: '本週（週一 00:00 起，台北）', mergedThisWeek: 0, openIssues: 0 }, message: '暫時讀不到開發進度' });
    });
  }

  refreshAuthChrome();
  if (backBtn) backBtn.addEventListener('click', () => setOpen(false));
  function onEscapeKey(ev) {
    if (!openState || !ev) return;
    if (ev.key !== 'Escape' && ev.key !== 'Esc') return;
    if (typeof ev.preventDefault === 'function') ev.preventDefault();
    setOpen(false);
  }
  if (doc && typeof doc.addEventListener === 'function') {
    doc.addEventListener('keydown', onEscapeKey);
  }
  if (doc && doc.querySelectorAll) {
    doc.querySelectorAll('[data-open-roadmap]').forEach((el) => {
      el.addEventListener('click', () => setOpen(true));
    });
  }

  return {
    open() { setOpen(true); },
    close() { setOpen(false); },
    isOpen() { return openState; },
    refresh: refresh,
    apply: apply,
    applyDev: applyDev
  };
}
