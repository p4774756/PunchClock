// 開發筆電狀態格式化（頁面與研發樓螢幕共用）。

const MS_MIN = 60 * 1000;
const MS_HOUR = 60 * MS_MIN;
const MS_DAY = 24 * MS_HOUR;
const TAIPEI_OFFSET_MS = 8 * MS_HOUR;
const GITHUB_REPO = 'https://github.com/p4774756/PunchClock';
const LOGIN_PATH = '/login';
const TOAST_MS = 4000;

/** 今日 00:00（台北）對應的 UTC 時間戳。 */
export function taipeiTodayStartMs(nowMs) {
  const now = Number(nowMs);
  if (!Number.isFinite(now)) return 0;
  const shifted = now + TAIPEI_OFFSET_MS;
  const dayStart = Math.floor(shifted / MS_DAY) * MS_DAY;
  return dayStart - TAIPEI_OFFSET_MS;
}

function isMergedTimelineAction(action) {
  return action === '合併' || action === '已合併';
}

/** 從開發進度 timeline 篩當日台北已合併項目。 */
export function filterMergedToday(timeline, nowMs) {
  const list = Array.isArray(timeline) ? timeline : [];
  const start = taipeiTodayStartMs(nowMs);
  return list.filter((row) => {
    if (!row || !isMergedTimelineAction(row.action)) return false;
    const at = Number(row.at);
    return Number.isFinite(at) && at >= start;
  });
}

/** 合併列表用的短時刻（HH:mm 台北）。 */
export function formatTaipeiClock(ms) {
  const n = Number(ms);
  if (!Number.isFinite(n) || n <= 0) return '';
  const d = new Date(n);
  const parts = new Intl.DateTimeFormat('zh-TW', {
    timeZone: 'Asia/Taipei',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).formatToParts(d);
  const get = (type) => {
    const hit = parts.find((p) => p.type === type);
    return hit ? hit.value : '';
  };
  return get('hour') + ':' + get('minute');
}

export function machineRole(machine) {
  const loc = machine && machine.location != null ? String(machine.location).trim() : '';
  return loc;
}

/** 期別子項進度（優先 API 欄位）。 */
export function phaseItemProgress(phase) {
  const p = phase || {};
  let done = p.itemsDone;
  let total = p.itemsTotal;
  if (!Number.isFinite(done) || !Number.isFinite(total)) {
    const items = Array.isArray(p.items) ? p.items : [];
    if (!items.length) {
      total = p.done ? 1 : 1;
      done = p.done ? 1 : 0;
    } else {
      total = items.length;
      done = items.filter((it) => it && it.done).length;
    }
  }
  return { done: Math.max(0, done), total: Math.max(0, total) };
}

export function roadmapItemTotals(phases) {
  const list = Array.isArray(phases) ? phases : [];
  let done = 0;
  let total = 0;
  list.forEach((phase) => {
    const pr = phaseItemProgress(phase);
    done += pr.done;
    total += pr.total;
  });
  return { done, total };
}

/** done 已完成；active 施工中（current）；progress 進行中（部分完成）；pending 未開工。 */
export function effectivePhaseStatus(phase, current) {
  if (!phase) return 'pending';
  const pr = phaseItemProgress(phase);
  if (pr.total > 0 && pr.done >= pr.total) return 'done';
  if (phase.done) return 'done';
  if (phase.phase != null && phase.phase === current) return 'active';
  if (pr.total > 0 && pr.done > 0 && pr.done < pr.total) return 'progress';
  return 'pending';
}

export function shouldFetchDevMachines(authenticated) {
  if (arguments.length === 0) {
    const w = typeof globalThis !== 'undefined' ? globalThis : null;
    return !!(w && w.PUNCHCLOCK_AUTHENTICATED === true);
  }
  return authenticated === true;
}

export function shouldShowDevStatusNavLink() {
  return false;
}

export function viewerCanLink(viewerOrAuth) {
  if (typeof viewerOrAuth === 'string') return viewerOrAuth === 'admin';
  return viewerOrAuth === true;
}

/** 最後回報：剛剛、N 分鐘前、N 小時前、尚無回報 */
export function formatLastSeenAgo(lastSeen, nowMs) {
  const now = Number(nowMs);
  if (!Number.isFinite(lastSeen) || lastSeen == null || lastSeen <= 0) {
    return '尚無回報';
  }
  const ago = Math.max(0, now - Number(lastSeen));
  if (ago < MS_MIN) return '剛剛';
  const mins = Math.floor(ago / MS_MIN);
  if (mins < 60) return mins + ' 分鐘前';
  const hours = Math.floor(ago / MS_HOUR);
  return hours + ' 小時前';
}

export function formatRunDuration(runStartedAt, nowMs) {
  const start = Number(runStartedAt);
  if (!Number.isFinite(start) || start <= 0) return '—';
  const now = Number(nowMs);
  const elapsed = Math.max(0, now - start);
  const mins = Math.floor(elapsed / MS_MIN);
  if (mins < 60) return '已跑 ' + mins + ' 分鐘';
  const hours = Math.floor(elapsed / MS_HOUR);
  const rem = mins - hours * 60;
  if (rem === 0) return '已跑 ' + hours + ' 小時';
  return '已跑 ' + hours + ' 小時 ' + rem + ' 分';
}

export function formatRoundLabel(round) {
  const n = Number(round);
  if (!Number.isFinite(n) || n === 0) return '初版';
  return '第 ' + n + ' 輪';
}

function issueUrl(issue, repo) {
  const base = repo || GITHUB_REPO;
  return base.replace(/\/$/, '') + '/issues/' + issue;
}

function clipTitle(title, max) {
  const s = title == null ? '' : String(title);
  if (s.length <= max) return s;
  return s.slice(0, Math.max(1, max - 1)) + '…';
}

export function formatMachineWork(machine, repo) {
  const m = machine || {};
  if (!m.online) {
    return { text: '—', href: null };
  }
  const runs = Array.isArray(m.runs) ? m.runs : [];
  if (runs.length > 0) {
    return { text: runs.length + ' 條進行中', href: null };
  }
  if (m.issue != null) {
    const href = issueUrl(m.issue, repo);
    if (m.agentRunning === true) {
      const title = m.issueTitle ? ' ' + String(m.issueTitle) : '';
      return { text: '正在跑 #' + m.issue + title, href: href };
    }
    return { text: '等待 review・#' + m.issue, href: href };
  }
  if (m.agentRunning === true) {
    return { text: 'Cursor 執行中（未標記工作）', href: null };
  }
  return { text: '閒置', href: null };
}

export function formatCursorLine(agentRunning) {
  if (agentRunning === true) return 'Cursor 執行中';
  if (agentRunning === false) return 'Cursor 未執行';
  return 'Cursor 狀態未知';
}

export function formatBatteryLine(machine) {
  const m = machine || {};
  if (m.battery == null) return '';
  let line = m.battery + '%';
  if (m.charging === true) line += '　充電中';
  return line;
}

function formatRunsIssueSummary(runs) {
  const list = Array.isArray(runs) ? runs : [];
  if (!list.length) return '閒置';
  const shown = list.slice(0, 3).map((r) => '#' + r.issue);
  let inner = shown.join(' ');
  if (list.length > 3) inner += ' …';
  return '跑 ' + list.length + ' 條（' + inner + '）';
}

/** 研發樓螢幕單行：小貓 在線・跑 #126 */
export function formatLabDevMachineLine(machine) {
  const m = machine || {};
  const label = m.label || m.machine || '';
  if (!m.online) return label + ' 離線';
  const runs = Array.isArray(m.runs) ? m.runs : [];
  let work;
  if (runs.length > 0) {
    work = formatRunsIssueSummary(runs);
  } else if (m.issue != null) {
    work = m.agentRunning === true ? '跑 #' + m.issue : '等待 review・#' + m.issue;
  } else if (m.agentRunning === true) {
    work = 'Cursor 執行中（未標記工作）';
  } else {
    work = '閒置';
  }
  return label + ' 在線・' + work;
}

export function formatLabDevMachineLines(data) {
  if (!data || data.success === false || !Array.isArray(data.machines)) return [];
  return data.machines.map(formatLabDevMachineLine);
}

/** 在既有螢幕行下方、detailHint 之前插入筆電狀態（最多 2 行） */
export function appendLabMachineLines(baseLines, machineLines) {
  const base = Array.isArray(baseLines) ? baseLines.slice() : [];
  const extra = Array.isArray(machineLines) ? machineLines.filter(Boolean).slice(0, 2) : [];
  if (!extra.length) return base;
  const hint = '詳細進度請到主島施工告示牌';
  const tlIdx = base.findIndex((line) => line && String(line).startsWith('【最近動態】'));
  const idx = base.lastIndexOf(hint);
  let insertAt = idx >= 0 ? idx : base.length;
  if (tlIdx >= 0 && (idx < 0 || tlIdx < insertAt)) insertAt = tlIdx;
  const before = base.slice(0, insertAt);
  const after = idx >= 0 ? base.slice(insertAt) : [];
  if (before.length && before[before.length - 1] !== '') {
    before.push('');
  }
  extra.forEach((line) => before.push(line));
  if (after.length) {
    before.push('');
  }
  return before.concat(after);
}

const DEV_STATUS_LOGIN_NEXT = '/login?next=/dev-status';

export function mergeRoadmapDevStatusLink(doc, container, opts) {
  if (!doc || !container) return;
  while (container.firstChild) container.removeChild(container.firstChild);
  if (!shouldShowDevStatusNavLink()) {
    container.hidden = true;
    return;
  }
  container.hidden = false;
  const o = opts || {};
  const authed = viewerCanLink(o.authenticated);
  const a = doc.createElement('a');
  a.className = 'roadmap-btn roadmap-btn-secondary';
  a.href = '/dev-status';
  a.textContent = '開發機狀態';
  if (!authed) {
    bindGuestDevStatusNav(a, doc, o);
  }
  container.appendChild(a);
}

function bindGuestDevStatusNav(node, doc, opts) {
  const loginHref = (opts && opts.loginHref) || DEV_STATUS_LOGIN_NEXT;
  const toastMessage = (opts && opts.toastMessage) || '開發機狀態需登入後查看';
  const open = (ev) => {
    if (ev) ev.preventDefault();
    showLoginRequiredToast(doc, loginHref, toastMessage);
  };
  node.addEventListener('click', open);
  node.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter' || ev.key === ' ') {
      ev.preventDefault();
      open(ev);
    }
  });
}

/** 從 location.search 解析 ?open= 參數（純函式，可供測試）。 */
export function parseRoadmapOpenParam(search) {
  const raw = search == null ? '' : String(search);
  const qs = raw.startsWith('?') ? raw.slice(1) : raw;
  if (!qs) return '';
  try {
    return new URLSearchParams(qs).get('open') || '';
  } catch (e) {
    return '';
  }
}

export function shouldOpenRoadmapFromSearch(search) {
  return parseRoadmapOpenParam(search) === 'roadmap';
}

export function shouldOpenTasksFromSearch(search) {
  return parseRoadmapOpenParam(search) === 'tasks';
}

export function shouldOpenDownloadFromSearch(search) {
  return parseRoadmapOpenParam(search) === 'download';
}

export function shouldOpenJournalFromSearch(search) {
  return parseRoadmapOpenParam(search) === 'journal';
}

function stripOpenSearchFromUrl(win) {
  try {
    const url = new URL(win.location.href);
    url.searchParams.delete('open');
    const q = url.searchParams.toString();
    const next = url.pathname + (q ? '?' + q : '') + (url.hash || '');
    if (win.history && typeof win.history.replaceState === 'function') {
      win.history.replaceState(null, '', next);
    }
  } catch (e) {
    /* ignore */
  }
}

/** 帶 open=roadmap 時開路線圖並清掉 query。 */
export function applyRoadmapOpenParam(win, openRoadmapFn) {
  if (!win || !win.location || typeof openRoadmapFn !== 'function') return false;
  if (!shouldOpenRoadmapFromSearch(win.location.search)) return false;
  openRoadmapFn();
  stripOpenSearchFromUrl(win);
  return true;
}

/** 帶 open=tasks 時開任務視窗並清掉 query（訪客不自動開內容）。 */
export function applyTasksOpenParam(win, openTasksFn) {
  if (!win || !win.location || typeof openTasksFn !== 'function') return false;
  if (!shouldOpenTasksFromSearch(win.location.search)) return false;
  openTasksFn();
  stripOpenSearchFromUrl(win);
  return true;
}

/** 帶 open=download 時開下載浮窗並清掉 query。 */
export function applyDownloadOpenParam(win, openDownloadFn) {
  if (!win || !win.location || typeof openDownloadFn !== 'function') return false;
  if (!shouldOpenDownloadFromSearch(win.location.search)) return false;
  openDownloadFn();
  stripOpenSearchFromUrl(win);
  return true;
}

/** 帶 open=journal 時開心得牆並清掉 query。 */
export function applyJournalOpenParam(win, openJournalFn) {
  if (!win || !win.location || typeof openJournalFn !== 'function') return false;
  if (!shouldOpenJournalFromSearch(win.location.search)) return false;
  openJournalFn();
  stripOpenSearchFromUrl(win);
  return true;
}

let toastDismiss = null;

export function isLoginRequiredToastOpen(doc) {
  return !!(doc && doc.querySelector && doc.querySelector('[data-dev-login-toast]'));
}

export function dismissLoginToast(doc) {
  if (toastDismiss) {
    toastDismiss();
    toastDismiss = null;
  }
  if (!doc || !doc.body) return;
  const existing = doc.querySelector('[data-dev-login-toast]');
  if (existing && existing.parentNode) {
    existing.parentNode.removeChild(existing);
  }
}

export function showLoginRequiredToast(doc, loginHref, message) {
  if (!doc || !doc.body) return;
  dismissLoginToast(doc);
  const href = loginHref || LOGIN_PATH;
  const wrap = doc.createElement('div');
  wrap.className = 'dev-login-toast maple-window';
  wrap.setAttribute('data-dev-login-toast', '1');
  wrap.setAttribute('role', 'status');
  const msg = doc.createElement('p');
  msg.className = 'dev-login-toast-msg';
  msg.textContent = message == null || message === '' ? '登入後才能查看連結' : String(message);
  const btn = doc.createElement('a');
  btn.className = 'dev-btn dev-btn-gold dev-login-toast-btn';
  btn.href = href;
  btn.textContent = '登入';
  const close = doc.createElement('button');
  close.type = 'button';
  close.className = 'dev-login-toast-close';
  close.setAttribute('aria-label', '關閉');
  close.textContent = '×';
  const teardown = () => {
    if (doc.removeEventListener) doc.removeEventListener('keydown', onKey, true);
    if (wrap.parentNode) wrap.parentNode.removeChild(wrap);
    if (toastDismiss === teardown) toastDismiss = null;
  };
  const onKey = (ev) => {
    if (ev.key !== 'Escape') return;
    if (!wrap.parentNode) return;
    if (ev.stopPropagation) ev.stopPropagation();
    if (ev.preventDefault) ev.preventDefault();
    teardown();
  };
  close.addEventListener('click', teardown);
  if (doc.addEventListener) doc.addEventListener('keydown', onKey, true);
  wrap.appendChild(msg);
  wrap.appendChild(btn);
  wrap.appendChild(close);
  doc.body.appendChild(wrap);
  toastDismiss = teardown;
  setTimeout(teardown, TOAST_MS);
}

function bindGuestLinkActivate(node, doc, loginHref) {
  const open = (ev) => {
    if (ev) ev.preventDefault();
    showLoginRequiredToast(doc, loginHref);
  };
  node.addEventListener('click', open);
  node.addEventListener('keydown', (ev) => {
    if (ev.key === 'Enter' || ev.key === ' ') {
      ev.preventDefault();
      open(ev);
    }
  });
}

/** 管理員：<a>；訪客：不可點的純文字＋ toast。 */
export function createGithubLink(doc, opts) {
  const o = opts || {};
  const text = o.text == null ? '' : String(o.text);
  const href = o.href || issueUrl(o.issue, o.repo);
  const canLink = viewerCanLink(o.canLink);
  if (canLink) {
    const a = doc.createElement('a');
    if (o.className) a.className = o.className;
    else a.className = 'dev-link';
    a.href = href;
    a.target = '_blank';
    a.rel = 'noopener noreferrer';
    a.textContent = text;
    return a;
  }
  const span = doc.createElement(o.tagName || 'span');
  span.className = (o.className ? o.className + ' ' : '') + 'dev-guest-ref';
  span.textContent = text;
  span.setAttribute('role', 'link');
  span.setAttribute('tabindex', '0');
  span.setAttribute('aria-disabled', 'true');
  bindGuestLinkActivate(span, doc, o.loginHref);
  return span;
}

export function parseRoadmapSearchQuery(raw) {
  const q = (raw == null ? '' : String(raw)).trim();
  if (!q) return { kind: 'none' };
  let m = /^i(\d+)$/i.exec(q);
  if (m) return { kind: 'issue', n: Number(m[1]) };
  m = /^#?(\d+)$/.exec(q);
  if (m) return { kind: 'issue', n: Number(m[1]) };
  m = /^p(\d+)$/i.exec(q);
  if (m) return { kind: 'pr', n: Number(m[1]) };
  m = /^pr\s*#?\s*(\d+)$/i.exec(q);
  if (m) return { kind: 'pr', n: Number(m[1]) };
  return { kind: 'text', text: q.toLowerCase() };
}

export function roadmapItemMatchesSearch(query, item) {
  if (!query || query.kind === 'none') return true;
  if (!item) return false;
  const issue = item.issue != null ? Number(item.issue) : null;
  const pr = item.pr != null ? Number(item.pr) : null;
  const title = (item.text == null ? '' : String(item.text)).toLowerCase();
  if (query.kind === 'issue') return issue === query.n;
  if (query.kind === 'pr') return pr === query.n;
  if (query.kind === 'text') return title.includes(query.text);
  return false;
}

export function devStatusItemMatchesSearch(query, item) {
  if (!query || query.kind === 'none') return true;
  if (!item) return false;
  const issue = item.issue != null ? Number(item.issue) : null;
  const pr = item.pr != null ? Number(item.pr) : null;
  const title = (item.title == null ? '' : String(item.title)).toLowerCase();
  const pair = (item.pair == null ? '' : String(item.pair)).toLowerCase();
  const ref = (item.ref == null ? '' : String(item.ref)).toLowerCase();
  if (query.kind === 'issue') {
    return issue === query.n || pair.includes('i' + query.n) || ref.includes('i' + query.n);
  }
  if (query.kind === 'pr') {
    return pr === query.n || pair.includes('p' + query.n) || ref.includes('p' + query.n);
  }
  if (query.kind === 'text') {
    return title.includes(query.text) || pair.includes(query.text) || ref.includes(query.text);
  }
  return false;
}

export function applyGithubSourceLabel(doc, el, href, canLink) {
  if (!doc || !el) return;
  const url = href || GITHUB_REPO + '/milestones';
  let label;
  if (/\/milestones\/?$/.test(url)) {
    label = '資料來源：GitHub Milestones';
  } else {
    const match = /\/issues\/(\d+)\s*$/.exec(url);
    label = '資料來源：GitHub #' + (match ? match[1] : '');
  }
  el.classList.remove('dev-guest-ref');
  el.removeAttribute('role');
  el.removeAttribute('tabindex');
  el.removeAttribute('aria-disabled');
  if (viewerCanLink(canLink)) {
    if (el.tagName && String(el.tagName).toLowerCase() === 'a') {
      el.href = url;
      el.target = '_blank';
      el.rel = 'noopener noreferrer';
      el.textContent = label;
    } else {
      while (el.firstChild) el.removeChild(el.firstChild);
      const a = doc.createElement('a');
      a.href = url;
      a.target = '_blank';
      a.rel = 'noopener noreferrer';
      a.textContent = label;
      el.appendChild(a);
    }
  } else {
    el.removeAttribute('href');
    el.removeAttribute('target');
    el.removeAttribute('rel');
    el.textContent = label;
    el.classList.add('dev-guest-ref');
    el.setAttribute('role', 'link');
    el.setAttribute('tabindex', '0');
    el.setAttribute('aria-disabled', 'true');
    bindGuestLinkActivate(el, doc, LOGIN_PATH);
  }
}

export function formatShortSha(commit) {
  if (commit == null || commit === '') return '';
  return String(commit).slice(0, 7);
}

export function buildRunRows(doc, runs, nowMs, opts) {
  const list = Array.isArray(runs) ? runs : [];
  const o = opts || {};
  const canLink = o.canLink;
  const repo = o.repo || GITHUB_REPO;
  const ul = doc.createElement('ul');
  ul.className = 'dev-run-list';
  list.forEach((run) => {
    const li = doc.createElement('li');
    li.className = 'dev-run-row';
    const head = doc.createElement('div');
    head.className = 'dev-run-head';
    head.appendChild(createGithubLink(doc, {
      className: 'dev-run-issue',
      href: issueUrl(run.issue, repo),
      text: '#' + run.issue,
      canLink: canLink
    }));
    if (run.issueTitle) {
      const title = doc.createElement('span');
      title.className = 'dev-run-title';
      title.textContent = clipTitle(run.issueTitle, 42);
      head.appendChild(title);
    }
    li.appendChild(head);
    const chip = doc.createElement('span');
    chip.className = 'dev-round-chip';
    chip.textContent = formatRoundLabel(run.round);
    li.appendChild(chip);
    const meta = doc.createElement('div');
    meta.className = 'dev-run-meta';
    const dur = doc.createElement('span');
    dur.textContent = formatRunDuration(run.startedAt, nowMs);
    meta.appendChild(dur);
    if (run.branch) {
      const br = doc.createElement('span');
      br.className = 'dev-run-branch';
      br.textContent = run.branch;
      meta.appendChild(br);
    }
    if (run.commit) {
      const sha = doc.createElement('span');
      sha.className = 'dev-run-sha';
      sha.textContent = formatShortSha(run.commit);
      meta.appendChild(sha);
    }
    li.appendChild(meta);
    ul.appendChild(li);
  });
  return ul;
}