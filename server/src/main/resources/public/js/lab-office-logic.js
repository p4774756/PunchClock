// 研發二樓 Live 虛擬辦公室（純邏輯，不依賴 DOM／Three.js）

export const LAB_OFFICE_POLL_MS = 30_000;

export const CHARACTER_IDS = Object.freeze([
  'oki',
  'tiger',
  'reviewer'
]);

export const IDLE_SPOTS = Object.freeze([
  { id: 'sofa', label: '沙發撸貓' },
  { id: 'coffee', label: '咖啡吧' },
  { id: 'balcony', label: '海景陽台' }
]);

/** 與 lab-office.js 站位、lab-office.css 手機覆寫同一組數字。百分比對舞台。 */
export const SPOT_POS = Object.freeze({
  'desk-tiger': { x: 22, y: 80 },
  'desk-review': { x: 50, y: 64 },
  coffee: { x: 84, y: 70 },
  sofa: { x: 16, y: 76 },
  balcony: { x: 36, y: 86 },
  beanbag: { x: 68, y: 82 },
  meeting: { x: 48, y: 48 },
  // 審核員待機：畫面右半、會議室與咖啡吧之間的空地。不要放回沙發（16%）旁邊。
  'idle-review': { x: 68, y: 86 }
});

export const MOBILE_CHAR_POS = Object.freeze({
  tiger: { x: 18, y: 82 },
  reviewer: { x: 50, y: 82 },
  oki: { x: 82, y: 82 }
});

export const SPRITE_NATURAL = Object.freeze({
  oki: { w: 98, h: 220 },
  tiger: { w: 99, h: 220 },
  reviewer: { w: 107, h: 220 }
});

const MACHINE_BY_CHAR = Object.freeze({
  tiger: 'office'
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
        if (!row) continue;
        const run = primaryRun(row);
        if (row.role === 'review' && row.model) model = row.model;
        if (run && run.role === 'review') {
          if (run.model) model = run.model;
          if (run.round != null) round = 'R' + run.round;
        }
        if (row.round != null && (row.role === 'review' || (run && run.role === 'review'))) {
          round = 'R' + row.round;
        }
      }
      const bits = ['審核中'];
      if (round) bits.push(round);
      if (model) bits.push(model);
      out.bubble = bits.join(' · ');
      return out;
    }
    let verdict = '';
    const list = machines && machines.machines ? machines.machines : [];
    for (let i = 0; i < list.length; i++) {
      if (list[i] && list[i].verdict) verdict = list[i].verdict;
    }
    out.mode = 'idle';
    out.spot = 'idle-review';
    out.bubble = verdict ? '最近 ' + verdict : '待機中';
    return out;
  }

  const machineId = MACHINE_BY_CHAR[charId];
  const row = machineById(machines, machineId);
  if (isMachineWorking(row)) {
    out.mode = 'work';
    out.spot = 'desk-tiger';
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
  return resolveSpotCollisions(CHARACTER_IDS.map((id) => buildCharacterState(id, ctx)));
}

const FLEX_SPOTS = ['sofa', 'balcony', 'beanbag', 'meeting'];

/** 軍師固定咖啡吧；有桌子的人固定工位；其餘空位不可疊在同一點。 */
export function resolveSpotCollisions(states) {
  const list = states || [];
  const used = new Set();
  const flex = [];
  for (let i = 0; i < list.length; i++) {
    const st = list[i];
    const fixed = st.id === 'oki' || st.desk;
    if (fixed) used.add(st.spot);
    else flex.push(st);
  }
  for (let i = 0; i < flex.length; i++) {
    const st = flex[i];
    if (st.spot && st.spot !== 'coffee' && !used.has(st.spot)) {
      used.add(st.spot);
      continue;
    }
    for (let j = 0; j < FLEX_SPOTS.length; j++) {
      if (!used.has(FLEX_SPOTS[j])) {
        st.spot = FLEX_SPOTS[j];
        used.add(st.spot);
        break;
      }
    }
  }
  return list;
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

function officeStageSize(viewportW, viewportH) {
  const frameW = Math.min(1280, Math.max(0, viewportW - 16 - 12));
  const maxH = Math.max(48, viewportH - 120);
  let h = frameW * 9 / 16;
  if (h > maxH) h = maxH;
  return { w: frameW, h: h };
}

function spriteHeightPx(stageH, viewportW) {
  if (viewportW <= 520) return 42;
  return Math.max(48, Math.min(120, stageH * 0.3));
}

/** 對齊審核量到的氣泡外框：桌機「#254 · R4 · grok-4.7 · 1:35」約 157×28。 */
function textWidthPx(text, fontSize) {
  const s = String(text || '');
  let w = 0;
  for (let i = 0; i < s.length; i++) {
    const c = s.charCodeAt(i);
    if (c > 255) w += fontSize;
    else if (c === 32) w += fontSize * 0.34;
    else w += fontSize * 0.62;
  }
  return w;
}

function bubbleMetrics(viewportW) {
  if (viewportW <= 520) {
    return { font: 10, lh: 1.2, padX: 3, padY: 2, border: 2, max: 80, min: 54.4 };
  }
  return { font: 12, lh: 1.3, padX: 6, padY: 4, border: 2, max: 152, min: 54.4 };
}

export function rectsIntersect(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

/** 三人與氣泡在舞台上的外框。手機寬走 CSS 覆寫的百分比，桌機走站位。 */
export function officeLayoutRects(states, viewportW, viewportH) {
  const stage = officeStageSize(viewportW, viewportH);
  const hSprite = spriteHeightPx(stage.h, viewportW);
  const spec = bubbleMetrics(viewportW);
  const mobile = viewportW <= 520;
  const rects = [];
  const list = states || [];
  for (let i = 0; i < list.length; i++) {
    const st = list[i];
    const pos = mobile
      ? (MOBILE_CHAR_POS[st.id] || MOBILE_CHAR_POS.reviewer)
      : (SPOT_POS[st.spot] || SPOT_POS.sofa);
    const ax = stage.w * pos.x / 100;
    const ay = stage.h * pos.y / 100;
    const nat = SPRITE_NATURAL[st.id] || { w: 100, h: 220 };
    const sw = nat.w * hSprite / nat.h;
    const char = { x: ax - sw / 2, y: ay - hSprite, w: sw, h: hSprite };
    const raw = textWidthPx(st.bubble, spec.font);
    const lines = Math.max(1, Math.ceil(raw / spec.max - 1e-6));
    const contentW = Math.min(spec.max, Math.max(spec.min, raw));
    const bw = contentW + spec.padX * 2 + spec.border * 2;
    const bh = lines * spec.font * spec.lh + spec.padY * 2 + spec.border * 2;
    const bubble = { x: ax - bw / 2, y: char.y - 4 - bh, w: bw, h: bh };
    rects.push({ id: st.id, spot: st.spot, char: char, bubble: bubble });
  }
  return { stage: stage, rects: rects };
}