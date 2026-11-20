// 研發二樓 Live 虛擬辦公室（純邏輯，不依賴 DOM／Three.js）

export const LAB_OFFICE_POLL_MS = 30_000;

export const CHARACTER_IDS = Object.freeze([
  'oki',
  'tiger',
  'kitten',
  'reviewer',
  'artist3d'
]);

export const IDLE_SPOTS = Object.freeze([
  { id: 'sofa', label: '沙發撸貓' },
  { id: 'coffee', label: '咖啡吧' },
  { id: 'balcony', label: '海景陽台' }
]);

const ARTIST_KEYWORDS = [
  '3d', '3D', '銅人', 'blender', 'Blender', 'GLB', 'glb', '經絡', 'atlas', 'TCM', '美術', '模型'
];

const MACHINE_BY_CHAR = Object.freeze({
  tiger: 'office',
  kitten: 'home'
});

export function viewerCanOpenLabOffice(authFlag) {
  return authFlag === true;
}

export function machineById(machines, id) {
  const list = machines && machines.machines ? machines.machines : [];
  for (let i = 0; i < list.length; i++) {
    const row = list[i];
    if (row && row.machine === id) return row;
  }
  return null;
}

export function issueTitleLooksArtistic(title) {
  const t = title == null ? '' : String(title);
  if (!t) return false;
  for (let i = 0; i < ARTIST_KEYWORDS.length; i++) {
    if (t.includes(ARTIST_KEYWORDS[i])) return true;
  }
  return false;
}

function primaryRun(machine) {
  const runs = machine && machine.runs ? machine.runs : [];
  if (!runs.length) return null;
  let best = runs[0];
  let bestAt = best.startedAt == null ? 0 : Number(best.startedAt);
  for (let i = 1; i < runs.length; i++) {
    const r = runs[i];
    const at = r.startedAt == null ? 0 : Number(r.startedAt);
    if (at >= bestAt) {
      best = r;
      bestAt = at;
    }
  }
  return best;
}

export function isMachineWorking(machine) {
  if (!machine || !machine.online) return false;
  if (machine.agentRunning) return true;
  const run = primaryRun(machine);
  return !!(run && run.issue);
}

export function anyReviewActive(machinesPayload) {
  const list = machinesPayload && machinesPayload.machines ? machinesPayload.machines : [];
  for (let i = 0; i < list.length; i++) {
    const row = list[i];
    if (!row || !row.online) continue;
    if (row.role === 'review') return true;
    const run = primaryRun(row);
    if (run && run.role === 'review') return true;
  }
  return false;
}

export function pickIdleSpot(seedMs, charId) {
  const spots = IDLE_SPOTS;
  const n = spots.length;
  if (!n) return 'sofa';
  const base = Number(seedMs) || 0;
  let h = 0;
  const s = String(charId || '');
  for (let i = 0; i < s.length; i++) h = (h + s.charCodeAt(i) * 17) % 997;
  const idx = Math.abs(Math.floor((base / 180_000 + h) % n));
  return spots[idx].id;
}

export function formatElapsed(ms, nowMs) {
  const end = Number(nowMs);
  const start = Number(ms);
  if (!Number.isFinite(start) || start <= 0 || !Number.isFinite(end)) return '';
  const sec = Math.max(0, Math.floor((end - start) / 1000));
  const m = Math.floor(sec / 60);
  const s = sec % 60;
  if (m >= 60) {
    const h = Math.floor(m / 60);
    const mm = m % 60;
    return h + 'h' + mm + 'm';
  }
  return m + ':' + String(s).padStart(2, '0');
}

export function buildCharacterState(charId, ctx) {
  const c = ctx || {};
  const now = Number(c.now) || 0;
  const machines = c.machines;
  const dev = c.devStatus;
  const out = {
    id: charId,
    mode: 'idle',
    spot: pickIdleSpot(now, charId),
    bubble: '待機中',
    desk: false,
    accent: false
  };

  if (charId === 'oki') {
    const q = dev && dev.now && dev.now.queue ? dev.now.queue.length : 0;
    const cur = dev && dev.now && dev.now.current ? dev.now.current : null;
    const nx = dev && dev.now && dev.now.next ? dev.now.next : null;
    const parts = ['佇列 ' + q];
    if (cur && cur.issue) parts.push('進行 #' + cur.issue);
    else if (nx && nx.issue) parts.push('下一 #' + nx.issue);
    out.mode = 'plan';
    out.spot = 'coffee';
    out.bubble = parts.join(' · ');
    out.desk = false;
    return out;
  }

  if (charId === 'reviewer') {
    const reviewOn = anyReviewActive(machines);
    if (reviewOn) {
      out.mode = 'work';
      out.spot = 'desk-review';
      out.desk = true;
      const list = machines && machines.machines ? machines.machines : [];
      let model = '';
      let round = '';
      for (let i = 0; i < list.length; i++) {
        const row = list[i];
        if (row && row.role === 'review' && row.model) model = row.model;
        if (row && row.round != null) round = 'R' + row.round;
      }
      out.bubble = '審核中 ' + (round || '') + (model ? ' · ' + model : '');
      return out;
    }
    let verdict = '';
    const list = machines && machines.machines ? machines.machines : [];
    for (let i = 0; i < list.length; i++) {
      if (list[i] && list[i].verdict) verdict = list[i].verdict;
    }
    out.mode = 'idle';
    out.spot = pickIdleSpot(now, charId);
    out.bubble = verdict ? '最近 ' + verdict : '待機中';
    return out;
  }

  if (charId === 'artist3d') {
    const list = machines && machines.machines ? machines.machines : [];
    let activeTitle = '';
    for (let i = 0; i < list.length; i++) {
      const row = list[i];
      if (!row || !row.online) continue;
      if (row.issueTitle) activeTitle = row.issueTitle;
      const run = primaryRun(row);
      if (run && run.issueTitle) activeTitle = run.issueTitle;
    }
    const art = issueTitleLooksArtistic(activeTitle);
    if (art) {
      out.mode = 'work';
      out.spot = 'desk-art';
      out.desk = true;
      out.accent = true;
      out.bubble = '3D / 美術任務進行中';
      return out;
    }
    out.mode = 'idle';
    out.spot = 'desk-art';
    out.desk = true;
    out.bubble = '整理工作台';
    return out;
  }

  const machineId = MACHINE_BY_CHAR[charId];
  const row = machineById(machines, machineId);
  if (isMachineWorking(row)) {
    out.mode = 'work';
    out.spot = machineId === 'office' ? 'desk-tiger' : 'desk-kitten';
    out.desk = true;
    const issue = row.issue || (primaryRun(row) && primaryRun(row).issue);
    const round = row.round != null ? row.round : (primaryRun(row) && primaryRun(row).round);
    const model = row.model || (primaryRun(row) && primaryRun(row).model);
    const started = row.runStartedAt || (primaryRun(row) && primaryRun(row).startedAt);
    const bits = [];
    if (issue) bits.push('#' + issue);
    if (round != null) bits.push('R' + round);
    if (model) bits.push(model);
    const elapsed = formatElapsed(started, now);
    if (elapsed) bits.push(elapsed);
    out.bubble = bits.join(' · ') || '工作中';
    return out;
  }

  out.mode = 'idle';
  out.spot = pickIdleSpot(now, charId);
  out.bubble = row && row.online ? '待機中' : '離線';
  return out;
}

export function buildAllCharacterStates(ctx) {
  return CHARACTER_IDS.map((id) => buildCharacterState(id, ctx));
}

export function characterCardData(charId, ctx) {
  const c = ctx || {};
  const machines = c.machines;
  const base = buildCharacterState(charId, ctx);
  const data = {
    id: charId,
    state: base,
    machine: null,
    portrait: '/assets/lab-2f/portrait-' + charId + '.webp',
    chibi: '/assets/lab-2f/chibi-' + charId + '.png'
  };
  const machineId = MACHINE_BY_CHAR[charId];
  if (machineId) {
    data.machine = machineById(machines, machineId);
  }
  if (charId === 'oki') {
    data.machine = null;
    data.dev = c.devStatus;
  }
  return data;
}

export function labOfficeInteractZone() {
  return Object.freeze({
    id: 'lab-2f-stairs',
    x: -6.2,
    z: 10.9,
    r: 0.95,
    label: '上研發二樓',
    action: 'openLabOffice'
  });
}