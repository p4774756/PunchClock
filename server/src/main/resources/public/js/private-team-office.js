// 首頁秘書：GET /api/office/public 的 tiger 欄位解析與小卡內容組裝。

import { formatTaipeiClock, viewerCanLink } from './dev-machines-format.js';
import {
  parseSecretaryOutfitId,
  secretarySpritePackForOutfit
} from './private-team-secretary-outfit.js';

const FORBIDDEN = /(?:ai\/issue-|\/Users\/|worktree|battery|charging|\.log\b)/i;

const DONE_PREVIEW_LIMIT = 3;
const QUEUE_PREVIEW_LIMIT = 5;
const VAGUE_PHRASES = new Set(['完成一項工作', '正在處理一項工作']);
const STAGE_LABELS = ['開發', '審核', 'CI', '合併'];
const STAGE_TO_SEGMENT = {
  '開發中': 0,
  '審查中': 1,
  '等 CI': 2,
  '修正中': 2,
  '有衝突': 2,
  '合併中': 3,
  // CI 全數通過、只等人合併：第 3 格完成、第 4 格 current。
  '等合併': 3
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

function parseMetricNumber(raw, min, max) {
  if (raw == null || raw === '') return null;
  const n = Number(raw);
  if (!Number.isFinite(n) || n < min || n > max) return null;
  return n;
}

export function tigerFromPublic(body) {
  if (!body || body.success !== true || !body.characters) return null;
  const tiger = body.characters.tiger;
  if (!tiger || typeof tiger !== 'object') return null;
  return {
    status: tiger.status == null ? '' : String(tiger.status).trim(),
    active: Array.isArray(tiger.active) ? tiger.active : [],
    completedToday: Array.isArray(tiger.completedToday) ? tiger.completedToday : [],
    queue: Array.isArray(tiger.queue) ? tiger.queue : [],
    cpuPercent: parseMetricNumber(tiger.cpuPercent, 0, 100),
    ramUsedGb: parseMetricNumber(tiger.ramUsedGb, 0, 2048),
    ramTotalGb: parseMetricNumber(tiger.ramTotalGb, 0, 2048),
    ramPercent: parseMetricNumber(tiger.ramPercent, 0, 100)
  };
}

/**
 * 讀 /api/office/public；失敗（斷線、非 2xx、Render 冷啟動回 HTML／503、success 非 true）會依序等待後重試。
 * 回傳最後一次的 body；全部失敗回 null。sleep 可注入（單測用）。
 */
export async function fetchOfficePublicWithRetry(fetchFn, opts) {
  const delays = (opts && opts.delays) || [1500, 4000, 9000];
  const sleep = (opts && opts.sleep) || ((ms) => new Promise((r) => setTimeout(r, ms)));
  if (typeof fetchFn !== 'function') return null;
  for (let i = 0; i <= delays.length; i++) {
    let body = null;
    try {
      const r = await fetchFn((opts && opts.url) || '/api/office/public');
      body = r && r.ok ? await r.json() : null;
    } catch (_e) {
      body = null;
    }
    if (tigerFromPublic(body)) return body;
    if (i < delays.length) await sleep(delays[i]);
  }
  return null;
}

export function secretarySystemMetricsVisible(tiger) {
  if (!tiger || typeof tiger !== 'object') return false;
  return tiger.cpuPercent != null || tiger.ramPercent != null
    || (tiger.ramUsedGb != null && tiger.ramTotalGb != null);
}

function formatMetricOneDecimal(n) {
  const v = Number(n);
  if (!Number.isFinite(v)) return '--';
  if (Math.abs(v - Math.round(v)) < 0.05) return String(Math.round(v));
  return v.toFixed(1);
}

function renderSystemMetrics(doc, tiger) {
  const wrap = el(doc, 'div', 'pt-status-sys');
  const cpu = tiger.cpuPercent;
  const ramPct = tiger.ramPercent;
  const hasRam = tiger.ramUsedGb != null && tiger.ramTotalGb != null;
  const showCpu = cpu != null;
  const showRam = hasRam || ramPct != null;

  if (showCpu) {
    wrap.appendChild(renderMetricBar(doc, 'CPU', cpu, cpu + '%', cpu));
  }
  if (showRam) {
    let pct = ramPct;
    if (pct == null && hasRam && tiger.ramTotalGb > 0) {
      pct = Math.round((tiger.ramUsedGb / tiger.ramTotalGb) * 100);
    }
    const valueText = hasRam
      ? formatMetricOneDecimal(tiger.ramUsedGb) + ' / ' + formatMetricOneDecimal(tiger.ramTotalGb)
        + 'G' + (pct != null ? ' (' + Math.round(pct) + '%)' : '')
      : (pct != null ? Math.round(pct) + '%' : '--');
    wrap.appendChild(renderMetricBar(doc, 'RAM', pct, valueText, pct));
  }
  return wrap;
}

function renderMetricBar(doc, shortLabel, fraction, text, warnAt) {
  const block = el(doc, 'div', 'pt-status-sys-row');
  const head = el(doc, 'div', 'pt-status-sys-head');
  const label = shortLabel.startsWith('RAM') ? 'RAM' : shortLabel;
  head.appendChild(el(doc, 'span', 'pt-status-sys-label', label));
  head.appendChild(el(doc, 'span', 'pt-status-sys-value', text));
  block.appendChild(head);
  const track = el(doc, 'div', 'pt-status-sys-track');
  const fill = el(doc, 'span', 'pt-status-sys-fill');
  const pct = fraction == null ? 0 : Math.min(100, Math.max(0, Number(fraction)));
  fill.style.width = fraction == null ? '0%' : pct + '%';
  if (warnAt != null && Number(warnAt) > 85) {
    block.className = 'pt-status-sys-row pt-status-sys-warn';
  }
  track.appendChild(fill);
  block.appendChild(track);
  return block;
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

/** 秘書「工作中」：舊文案、「工作中 ×N」與「…結界展開中」。 */
export function isWorkingStatus(status) {
  const s = status == null ? '' : String(status).trim();
  if (s.startsWith('工作中')) return true;
  return s.endsWith('結界展開中');
}

/** 秘書狀態對應立繪（工作中浴缸打字；待命／休息坐休憩區沙發，素材缺失時場景改用站姿立繪仍留沙發位）。 */
export function secretarySpriteUrlForStatus(status, outfitId = 'default') {
  const pack = secretarySpritePackForOutfit(parseSecretaryOutfitId(outfitId));
  const s = status == null ? '' : String(status).trim();
  if (isWorkingStatus(s)) return pack.typing;
  if (s === '待命中' || s === '待機' || s === '休息中') return pack.sofa;
  return pack.idle;
}

const GUARDIAN_BARRIER_NAMES = ['青龍', '白虎', '朱雀', '玄武'];

/** API 工作中狀態字串 → 秘書 3D 泡泡口吻（面板狀態列仍用 API 原字串）。 */
export function workingBubbleTextFromApiStatus(status) {
  const s = status == null ? '' : String(status).trim();
  for (const name of GUARDIAN_BARRIER_NAMES) {
    if (s === name + '結界展開中') return name + '正在努力中喔';
  }
  if (
    s === '二聖獸結界展開中' ||
    s === '三聖獸結界展開中' ||
    s === '四聖獸結界展開中' ||
    s === '聖獸結界展開中'
  ) {
    return '聖獸們都在努力中喔';
  }
  if (s.startsWith('工作中')) {
    const mult = /^工作中\s*×\s*(\d+)$/.exec(s);
    if (mult) {
      return Number(mult[1]) >= 2 ? '聖獸們都在努力中喔' : '聖獸正在努力中喔';
    }
    return '聖獸正在努力中喔';
  }
  if (s.endsWith('結界展開中')) {
    return '聖獸們都在努力中喔';
  }
  return '聖獸正在努力中喔';
}

/** 3D 泡泡用短標（工作中秘書口吻；待命／休息維持原字）。 */
export function bubbleTextForStatus(status) {
  const s = status == null ? '' : String(status).trim();
  if (isWorkingStatus(s)) return workingBubbleTextFromApiStatus(s);
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
  if (Number.isFinite(minutes) && minutes > 0 && item.stage !== '等合併') {
    out.push('已跑 ' + minutes + ' 分');
  }
  if (item.model && isSafePublicText(item.model)) {
    out.push(String(item.model));
  }
  return out;
}

/** 今日完成主文：只用公開 issue 標題（最多兩行由 CSS 換行）。 */
export function titleForDoneDisplay(row) {
  const item = row && typeof row === 'object' ? row : {};
  const title = item.title && isSafePublicText(item.title) ? String(item.title).trim() : '';
  return title || '';
}

/** 進行中主文：優先公開 issue 標題，否則沿用 phrase 規則。 */
export function titleForActiveDisplay(row) {
  const item = row && typeof row === 'object' ? row : {};
  const title = item.title && isSafePublicText(item.title) ? String(item.title).trim() : '';
  if (title) return title;
  return phraseForActiveDisplay(row);
}

/** @deprecated 請改 titleForDoneDisplay */
export function phraseForDoneDisplay(row) {
  return titleForDoneDisplay(row);
}

const MAX_DONE_DURATION_MINUTES = 30 * 24 * 60;

function formatDoneMinutes(minutes, prefix) {
  const m = Number(minutes);
  if (!Number.isFinite(m) || m < 0 || m > MAX_DONE_DURATION_MINUTES) return '';
  if (m === 0) return prefix + ' < 1 分';
  if (m < 60) return prefix + ' ' + m + ' 分';
  const hours = Math.floor(m / 60);
  const rem = m % 60;
  if (rem === 0) return prefix + ' ' + hours + ' 時';
  return prefix + ' ' + hours + ' 時 ' + rem + ' 分';
}

export function formatAiWorkDuration(minutes) {
  const m = Number(minutes);
  if (!Number.isFinite(m) || m < 1) return '';
  return formatDoneMinutes(minutes, 'AI 工作');
}

export function formatMergeSpanDuration(minutes) {
  return formatDoneMinutes(minutes, '開單到合併');
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

/** 已進入 CI／合併等後段，且本單略過審核（非僅依 round 數字）。 */
function reviewWasSkipped(item, stage) {
  const row = item && typeof item === 'object' ? item : {};
  const current = STAGE_TO_SEGMENT[stage] ?? 0;
  if (current < 2) return false;
  if (row.reviewSkipped === true) return true;
  const round = Number(row.round);
  return Number.isFinite(round) && round === 0;
}

/** 四段進度條狀態：done | current | pending | fail | skipped */
export function progressSegmentsForActive(item) {
  const row = item && typeof item === 'object' ? item : {};
  const stage = activeStageForItem(row);
  const current = STAGE_TO_SEGMENT[stage] ?? 0;
  const ciFail = activeCiFailed(row);
  const reviewSkipped = reviewWasSkipped(row, stage);
  return STAGE_LABELS.map((baseLabel, index) => {
    // PR 與 main 有衝突時 CI 不會跑：第三格改標「有衝突」，不顯示等 CI。
    const label = stage === '有衝突' && baseLabel === 'CI' ? '有衝突' : baseLabel;
    if (index < current) {
      if (label === '審核' && reviewSkipped) return { label, state: 'skipped' };
      return { label, state: 'done' };
    }
    if (index === current) {
      if (label === 'CI' && ciFail) return { label, state: 'fail' };
      if (stage === '等合併') return { label, state: 'current', waiting: true };
      return { label, state: 'current' };
    }
    return { label, state: 'pending' };
  });
}

function pipelineNodePrimaryLabel(seg, item) {
  const row = item && typeof item === 'object' ? item : {};
  const round = Number(row.round);
  if (seg.label === '審核' && seg.state !== 'skipped' && Number.isFinite(round) && round > 0) {
    return '審核 第 ' + round + ' 輪';
  }
  return seg.label;
}

function pipelineNodeCaption(seg) {
  if (seg.waiting) return '等合併';
  if (seg.state === 'fail') return '失敗';
  if (seg.state === 'skipped') return '已略過';
  return '';
}

function pipelineNodeIconChild(doc, seg, index) {
  if (seg.state === 'done') return el(doc, 'span', 'pt-status-progress-node-mark', '\u2713');
  if (seg.state === 'fail') return el(doc, 'span', 'pt-status-progress-node-mark', '\u2715');
  if (seg.state === 'skipped') return el(doc, 'span', 'pt-status-progress-node-mark', '\u2013');
  return el(doc, 'span', 'pt-status-progress-node-num', String(index + 1));
}

function pipelineNodeShowMinutes(seg) {
  return seg.state === 'current' && !seg.waiting;
}

export function progressAriaLabelForActive(item) {
  return statusLineForActive(item);
}

function connectorClassAfter(seg, next, index) {
  const base = 'pt-status-progress-connector';
  if (next && next.state === 'current') return base + ' is-flowing';
  if (seg.state === 'current' && index === 0) return base + ' is-flowing';
  if (seg.state === 'done') return base + ' is-complete';
  if (seg.state === 'skipped') return base + ' is-skipped';
  if (seg.state === 'fail') return base + ' is-blocked';
  return base + ' is-pending';
}

export function statusLineForActive(item) {
  const row = item && typeof item === 'object' ? item : {};
  const stage = activeStageForItem(row);
  if (activeCiFailed(row)) {
    return 'CI 失敗，等待處理';
  }
  const minutes = Number(row.minutes);
  const minText = Number.isFinite(minutes) && minutes > 0 ? '・' + minutes + ' 分' : '';
  const round = Number(row.round);
  const roundText = Number.isFinite(round) && round > 0 ? '・第 ' + round + ' 輪' : '';
  if (stage === '有衝突') return '有衝突，待解決';
  if (stage === '等合併') return '等合併';
  if (stage === '審查中') return '審核中' + roundText + minText;
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

function chainAvatarSlug(chain) {
  const c = chain == null ? '' : String(chain).trim();
  if (c === '青龍') return 'qinglong';
  if (c === '白虎') return 'baihu';
  if (c === '朱雀') return 'zhuque';
  if (c === '玄武') return 'xuanwu';
  return '';
}

function chainAvatarSrc(slug) {
  return '/assets/private-team/chain-' + slug + '-96.webp';
}

function chainAvatarSrcSet(slug) {
  const base = '/assets/private-team/chain-' + slug;
  return base + '-96.webp 1x, ' + base + '-192.webp 2x';
}

function renderStageProgress(doc, item) {
  const row = item && typeof item === 'object' ? item : {};
  const segments = progressSegmentsForActive(row);
  const track = el(doc, 'div', 'pt-status-progress');
  track.setAttribute('role', 'group');
  track.setAttribute('aria-label', progressAriaLabelForActive(row));
  segments.forEach((seg, index) => {
    const step = el(doc, 'div', 'pt-status-progress-step');
    const node = el(doc, 'div', 'pt-status-progress-node ' + seg.state + (seg.waiting ? ' is-waiting' : ''));
    const icon = el(doc, 'span', 'pt-status-progress-node-icon');
    icon.setAttribute('aria-hidden', 'true');
    icon.appendChild(pipelineNodeIconChild(doc, seg, index));
    node.appendChild(icon);
    node.appendChild(el(doc, 'span', 'pt-status-progress-node-label', pipelineNodePrimaryLabel(seg, row)));
    const caption = pipelineNodeCaption(seg);
    if (caption) {
      node.appendChild(el(doc, 'span', 'pt-status-progress-node-note', caption));
    }
    if (pipelineNodeShowMinutes(seg)) {
      const minutes = Number(row.minutes);
      if (Number.isFinite(minutes) && minutes > 0) {
        node.appendChild(el(doc, 'span', 'pt-status-progress-node-time', minutes + ' 分'));
      }
    }
    step.appendChild(node);
    if (index < segments.length - 1) {
      const conn = el(doc, 'span', connectorClassAfter(seg, segments[index + 1], index));
      conn.setAttribute('aria-hidden', 'true');
      step.appendChild(conn);
    }
    track.appendChild(step);
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
    const chainLine = el(doc, 'span', 'pt-status-chain-line');
    const dotCls = chainDotClass(chain);
    const dot = el(doc, 'span', 'pt-status-chain-dot ' + dotCls);
    dot.setAttribute('aria-hidden', 'true');
    const slug = chainAvatarSlug(chain);
    if (slug) {
      const img = el(doc, 'img', 'pt-status-chain-avatar ' + dotCls);
      img.setAttribute('width', '24');
      img.setAttribute('height', '24');
      img.setAttribute('alt', chain);
      img.setAttribute('loading', 'lazy');
      img.setAttribute('decoding', 'async');
      img.setAttribute('src', chainAvatarSrc(slug));
      img.setAttribute('srcset', chainAvatarSrcSet(slug));
      if (typeof img.addEventListener === 'function') {
        img.addEventListener('error', () => {
          chainLine.classList.add('is-avatar-error');
        });
      }
      chainLine.appendChild(img);
    }
    chainLine.appendChild(dot);
    chainLine.appendChild(el(doc, 'span', 'pt-status-chain-name', chain));
    head.appendChild(chainLine);
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
  const titleText = titleForActiveDisplay(row);
  if (titleText) {
    head.appendChild(el(doc, 'span', 'pt-status-stage-title', titleText));
  }
  wrap.appendChild(head);
  wrap.appendChild(renderStageProgress(doc, row));
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
    const aiWork = formatAiWorkDuration(row.aiWorkMinutes);
    const mergeSpan = formatMergeSpanDuration(row.durationMinutes);
    if (clock || aiWork || mergeSpan) {
      const head = el(doc, 'div', 'pt-status-done-head');
      if (clock) head.appendChild(el(doc, 'span', 'pt-status-done-time', clock));
      if (aiWork) head.appendChild(el(doc, 'span', 'pt-status-done-duration', aiWork));
      else if (mergeSpan) head.appendChild(el(doc, 'span', 'pt-status-done-duration', mergeSpan));
      if (aiWork && mergeSpan) {
        head.appendChild(el(doc, 'span', 'pt-status-done-span', mergeSpan));
      }
      li.appendChild(head);
    }
    const title = titleForDoneDisplay(row);
    if (title) li.appendChild(el(doc, 'span', 'pt-status-done-phrase', title));
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
    const head = el(doc, 'div', 'pt-status-queue-head');
    const issue = Number(row.issue);
    if (Number.isFinite(issue) && issue > 0) {
      const issueLink = linkIssue(doc, row, 'pt-status-link pt-status-stage-issue');
      if (issueLink) {
        head.appendChild(issueLink);
      } else {
        head.appendChild(el(doc, 'span', 'pt-status-stage-issue', '#' + issue));
      }
    }
    const title = row.title && isSafePublicText(row.title) ? String(row.title) : '';
    if (title) head.appendChild(el(doc, 'span', 'pt-status-queue-title', title));
    if (head.childNodes.length) li.appendChild(head);
    const waitingFor = Number(row.waitingFor);
    if (Number.isFinite(waitingFor) && waitingFor > 0) {
      li.appendChild(el(doc, 'span', 'pt-status-queue-after', '接在 #' + waitingFor + ' 後'));
    }
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
  if (secretarySystemMetricsVisible(tiger)) {
    stateWrap.appendChild(renderSystemMetrics(doc, tiger));
  }
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
  if (isWorkingStatus(s)) return '正在忙，點開上方泡泡可隨時看進度。';
  return '待命中，有任務會立刻開工。';
}