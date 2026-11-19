// 開發筆電狀態格式化（頁面與研發樓螢幕共用）。

const MS_MIN = 60 * 1000;
const MS_HOUR = 60 * MS_MIN;
const MS_DAY = 24 * MS_HOUR;
const TAIPEI_OFFSET_MS = 8 * MS_HOUR;

export const MACHINE_ROLE = {
  office: '辦公室 Mac',
  home: '家用 Mac'
};

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
  const key = machine && machine.machine ? String(machine.machine) : '';
  if (key && MACHINE_ROLE[key]) return MACHINE_ROLE[key];
  const label = machine && machine.label ? String(machine.label) : '';
  if (label === '猛虎') return MACHINE_ROLE.office;
  if (label === '小貓') return MACHINE_ROLE.home;
  return '';
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

/** 子項全勾視為已完成（即使上級未勾）。 */
export function effectivePhaseStatus(phase, current) {
  if (!phase) return 'pending';
  const pr = phaseItemProgress(phase);
  if (pr.total > 0 && pr.done >= pr.total) return 'done';
  if (phase.done) return 'done';
  if (phase.phase != null && phase.phase === current) return 'active';
  return 'pending';
}

export function shouldFetchDevMachines(authenticated) {
  return authenticated === true;
}

export function shouldShowDevStatusNavLink(authenticated) {
  return authenticated === true;
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

export function formatMachineWork(machine, repo) {
  const m = machine || {};
  if (!m.online) {
    return { text: '—', href: null };
  }
  if (m.issue == null) {
    return { text: '閒置', href: null };
  }
  const href = issueUrl(m.issue, repo);
  if (m.agentRunning === true) {
    const title = m.issueTitle ? ' ' + String(m.issueTitle) : '';
    return { text: '正在跑 #' + m.issue + title, href: href };
  }
  return { text: '等待 review・#' + m.issue, href: href };
}

function issueUrl(issue, repo) {
  const base = repo || 'https://github.com/p4774756/PunchClock';
  return base.replace(/\/$/, '') + '/issues/' + issue;
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

/** 研發樓螢幕單行：小貓 在線・跑 #126 */
export function formatLabDevMachineLine(machine) {
  const m = machine || {};
  const label = m.label || m.machine || '';
  if (!m.online) return label + ' 離線';
  let work = '閒置';
  if (m.issue != null) {
    work = m.agentRunning === true ? '跑 #' + m.issue : '等待 review・#' + m.issue;
  }
  return label + ' 在線・' + work;
}

export function formatLabDevMachineLines(data) {
  if (!data || data.success === false || !Array.isArray(data.machines)) return [];
  return data.machines.slice(0, 2).map(formatLabDevMachineLine);
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

export function mergeRoadmapDevStatusLink(doc, container, authenticated) {
  if (!doc || !container) return;
  while (container.firstChild) container.removeChild(container.firstChild);
  if (!shouldShowDevStatusNavLink(authenticated)) {
    container.hidden = true;
    return;
  }
  container.hidden = false;
  const a = doc.createElement('a');
  a.className = 'roadmap-btn roadmap-btn-secondary';
  a.href = '/dev-status';
  a.textContent = '開發機狀態';
  container.appendChild(a);
}