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
  { id: 'lounge', label: '休憩區' }
]);

/** 百分比對舞台（桌機與手機同一組，對到城市夜景家具）。錨點是角色腳底／坐姿下緣。 */
export const SPOT_POS = Object.freeze({
  'desk-oki': { x: 44, y: 43 },
  'desk-tiger': { x: 49, y: 79 },
  'desk-review': { x: 63, y: 51 },
  coffee: { x: 11, y: 76 },
  sofa: { x: 23, y: 63 },
  lounge: { x: 30, y: 82 },
  beanbag: { x: 18, y: 86 },
  meeting: { x: 38, y: 88 },
  'idle-review': { x: 71, y: 84 }
});

export const SPRITE_NATURAL = Object.freeze({
  oki: { w: 217, h: 434 },
  tiger: { w: 274, h: 424 },
  reviewer: { w: 244, h: 422 }
});

export function viewerCanOpenLabOffice(authFlag) {
  return authFlag === true;
}

/** 首頁可點物件（百分比座標＋命中框，對舞台）。對到新背景的家具，不是舊海景。 */
export const OFFICE_PROP_POS = Object.freeze({
  guestbook: { x: 12, y: 69, w: 9, h: 12, label: '簽名簿' },
  clock: { x: 18, y: 63, w: 11, h: 16, label: '打卡機' },
  bookshelf: { x: 34, y: 34, w: 12, h: 18, label: '書櫃' },
  whiteboard: { x: 15, y: 40, w: 11, h: 14, label: '白板' },
  desk: { x: 66, y: 68, w: 12, h: 14, label: '下載 App' }
});

/** 白板旁主控台小熱點（百分比）。 */
export const OFFICE_CONSOLE_POS = Object.freeze({ x: 20, y: 45, w: 8, h: 10, label: '主控台' });

function isOnlineRow(row, now) {
  if (!row || !row.online) return false;
  const seen = Number(row.lastSeen);
  if (Number.isFinite(seen) && seen > 0 && Number.isFinite(now)) {
    return now - seen <= 180_000;
  }
  return !!row.online;
}

function loopsOnMachine(row) {
  const runs = row && row.runs ? row.runs : [];
  if (runs.length) return runs.length;
  if (row && (row.agentRunning || row.issue)) return 1;
  return 0;
}

export function countActiveLoops(machinesPayload, now) {
  const list = machinesPayload && machinesPayload.machines ? machinesPayload.machines : [];
  let total = 0;
  for (let i = 0; i < list.length; i++) {
    const row = list[i];
    if (!isOnlineRow(row, now)) continue;
    const n = loopsOnMachine(row);
    if (n > 0) total += n;
  }
  return total;
}

function countReviewOnMachine(row) {
  const runs = row && row.runs ? row.runs : [];
  let n = 0;
  for (let i = 0; i < runs.length; i++) {
    if (runs[i] && runs[i].role === 'review') n++;
  }
  if (!n && row && row.role === 'review') n = 1;
  return n;
}

export function countReviewActive(machinesPayload, now) {
  const list = machinesPayload && machinesPayload.machines ? machinesPayload.machines : [];
  let total = 0;
  for (let i = 0; i < list.length; i++) {
    const row = list[i];
    if (!isOnlineRow(row, now)) continue;
    total += countReviewOnMachine(row);
  }
  return total;
}

export function chibiAssetPath(charId, mode) {
  const work = mode === 'work';
  return '/assets/lab-2f/chibi-' + charId + (work ? '-work' : '-idle') + '.png';
}

export function tigerBubbleText(machinesPayload, now) {
  const n = countActiveLoops(machinesPayload, now);
  if (n <= 0) return '待機中';
  return n === 1 ? '工作中' : '工作中 ×' + n;
}

export function reviewerBubbleText(machinesPayload, now) {
  const n = countReviewActive(machinesPayload, now);
  if (n <= 0) return '待機中';
  return n === 1 ? '審核中' : '審核中 ×' + n;
}

export function buildCharacterStatesFromPublic(publicPayload, now) {
  const chars = publicPayload && publicPayload.characters ? publicPayload.characters : {};
  const list = [];
  for (let i = 0; i < CHARACTER_IDS.length; i++) {
    const id = CHARACTER_IDS[i];
    const row = chars[id] || {};
    const status = String(row.status || '待機');
    const working = status.startsWith('工作中');
    const reviewing = status.startsWith('審核中');
    const base = {
      id,
      mode: working || reviewing ? 'work' : 'idle',
      bubble: status,
      desk: false,
      accent: false,
      spot: pickIdleSpot(now, id)
    };
    if (id === 'oki') {
      if (working) {
        base.spot = 'desk-oki';
        base.mode = 'work';
        base.desk = true;
      } else {
        base.spot = 'coffee';
        base.mode = status.startsWith('佇列') ? 'plan' : 'idle';
      }
    } else if (id === 'tiger' && working) {
      base.spot = 'desk-tiger';
      base.desk = true;
    } else if (id === 'reviewer' && reviewing) {
      base.spot = 'desk-review';
      base.desk = true;
    }
    list.push(base);
  }
  return resolveSpotCollisions(list);
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
  return countReviewActive(machinesPayload, Date.now()) > 0;
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
    const working = !!(cur && cur.issue);
    out.bubble = working ? '工作中' : '佇列 ' + q;
    if (working) {
      out.mode = 'work';
      out.spot = 'desk-oki';
      out.desk = true;
    } else {
      out.mode = 'plan';
      out.spot = 'coffee';
    }
    return out;
  }

  if (charId === 'reviewer') {
    const reviewN = countReviewActive(machines, now);
    if (reviewN > 0) {
      out.mode = 'work';
      out.spot = 'desk-review';
      out.desk = true;
      out.bubble = reviewerBubbleText(machines, now);
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

  if (charId === 'tiger') {
    const n = countActiveLoops(machines, now);
    if (n > 0) {
      out.mode = 'work';
      out.spot = 'desk-tiger';
      out.desk = true;
      out.bubble = tigerBubbleText(machines, now);
      return out;
    }
    const anyOnline = machines && machines.machines
      ? machines.machines.some((row) => isOnlineRow(row, now))
      : false;
    out.mode = 'idle';
    out.spot = pickIdleSpot(now, charId);
    out.bubble = anyOnline ? '待機中' : '離線';
    return out;
  }

  return out;
}

export function buildAllCharacterStates(ctx) {
  return resolveSpotCollisions(CHARACTER_IDS.map((id) => buildCharacterState(id, ctx)));
}

const FLEX_SPOTS = ['sofa', 'lounge', 'beanbag', 'meeting', 'idle-review'];

const CLEAR_VIEWPORTS = [
  { w: 1280, h: 720 },
  { w: 390, h: 844 }
];

function spotOverlapsPlaced(candidate, placed) {
  if (!placed.length) return false;
  const probe = placed.concat([candidate]);
  for (let v = 0; v < CLEAR_VIEWPORTS.length; v++) {
    const vp = CLEAR_VIEWPORTS[v];
    const rects = officeLayoutRects(probe, vp.w, vp.h).rects;
    const mine = rects[rects.length - 1];
    for (let i = 0; i < rects.length - 1; i++) {
      const other = rects[i];
      if (rectsIntersect(mine.char, other.char)
        || rectsIntersect(mine.bubble, other.bubble)
        || rectsIntersect(mine.bubble, other.char)
        || rectsIntersect(other.bubble, mine.char)) {
        return true;
      }
    }
  }
  return false;
}

/** 工位與第一個咖啡點只留一人；其餘空位在桌機與手機都不可疊到。 */
export function resolveSpotCollisions(states) {
  const list = states || [];
  const used = new Set();
  const placed = [];
  const flex = [];
  for (let i = 0; i < list.length; i++) {
    const st = list[i];
    const claimCoffee = st.spot === 'coffee' && !used.has('coffee');
    const anchored = !!(st.desk || st.spot === 'desk-oki' || claimCoffee);
    if (anchored && st.spot && !used.has(st.spot)) {
      used.add(st.spot);
      placed.push(st);
    } else {
      flex.push(st);
    }
  }
  for (let i = 0; i < flex.length; i++) {
    const st = flex[i];
    const candidates = [];
    if (st.spot && !used.has(st.spot)) candidates.push(st.spot);
    for (let j = 0; j < FLEX_SPOTS.length; j++) {
      const id = FLEX_SPOTS[j];
      if (!used.has(id) && candidates.indexOf(id) < 0) candidates.push(id);
    }
    let chosen = candidates.length ? candidates[candidates.length - 1] : (st.spot || 'sofa');
    for (let j = 0; j < candidates.length; j++) {
      const probe = { id: st.id, spot: candidates[j], bubble: st.bubble };
      if (!spotOverlapsPlaced(probe, placed)) {
        chosen = candidates[j];
        break;
      }
    }
    st.spot = chosen;
    if (chosen) used.add(chosen);
    placed.push(st);
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
    machines: null,
    portrait: '/assets/lab-2f/portrait-' + charId + '.webp',
    chibi: chibiAssetPath(charId, base.mode)
  };
  if (charId === 'tiger') {
    data.machines = machines && machines.machines ? machines.machines.slice() : [];
  }
  if (charId === 'oki') {
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
  if (viewportW <= 520) return 44;
  return Math.max(48, Math.min(112, stageH * 0.24));
}

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
    return { font: 10, lh: 1.2, padX: 3, padY: 2, border: 2, max: 74, min: 32 };
  }
  return { font: 12, lh: 1.3, padX: 6, padY: 4, border: 2, max: 152, min: 48 };
}

export function rectsIntersect(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export function officeLayoutRects(states, viewportW, viewportH) {
  const stage = officeStageSize(viewportW, viewportH);
  const hSprite = spriteHeightPx(stage.h, viewportW);
  const spec = bubbleMetrics(viewportW);
  const rects = [];
  const list = states || [];
  for (let i = 0; i < list.length; i++) {
    const st = list[i];
    const pos = SPOT_POS[st.spot] || SPOT_POS.sofa;
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