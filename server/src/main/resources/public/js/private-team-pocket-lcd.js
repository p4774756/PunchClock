/** 160×144 復古綠 LCD（窗邊預覽與彈窗共用繪製）。 */

import {
  MARY_PHASE,
  POCKET_MARY_RING,
  POCKET_MARY_RING_LEN,
  POCKET_MARY_SYMBOL_COUNT,
  createMaryState,
  maryPreviewState,
  maryTotalBet
} from './private-team-pocket-mary.js';

export const POCKET_LCD_W = 160;
export const POCKET_LCD_H = 144;

const PAL = {
  bg: '#9bbc0f',
  bgHi: '#8bac0f',
  ink: '#0f380f',
  inkLo: '#306230',
  lit: '#0f380f',
  dim: '#8bac0f'
};

/** 8×8 像素圖：1=主色 2=次色 */
const PX = {
  apple: [
    '...11...',
    '..1111..',
    '.112211.',
    '.112211.',
    '.112211.',
    '..1221..',
    '...11...',
    '........'
  ],
  orange: [
    '...11...',
    '..1221..',
    '.12221.',
    '.12221.',
    '.12221.',
    '..111..',
    '...1...',
    '........'
  ],
  lemon: [
    '...11...',
    '..1221..',
    '.12221.',
    '.12221.',
    '..1221..',
    '...11...',
    '..1.1..',
    '........'
  ],
  bell: [
    '..1111..',
    '.122221.',
    '.122221.',
    '.122221.',
    '..1221..',
    '...11...',
    '..1111..',
    '...11...'
  ],
  watermelon: [
    '..1111..',
    '.122221.',
    '.123321.',
    '.123321.',
    '.122221.',
    '..1111..',
    '...11...',
    '........'
  ],
  star: [
    '...1....',
    '..111...',
    '.11111.',
    '..111..',
    '.1.1.1.',
    '........',
    '........',
    '........'
  ],
  seven: [
    '.11111.',
    '.....1.',
    '....1..',
    '...1...',
    '..1....',
    '.1.....',
    '1......',
    '........'
  ],
  bar: [
    '.11111.',
    '.1...1.',
    '.11111.',
    '.1...1.',
    '.11111.',
    '........',
    '........',
    '........'
  ],
  lucky: [
    '...1....',
    '..1.1...',
    '.1...1..',
    '...1....',
    '..1.1...',
    '.1...1..',
    '...1....',
    '........'
  ],
  again: [
    '...11...',
    '..1..2..',
    '.1....2.',
    '......2.',
    '.2....1.',
    '..2..1..',
    '...22...',
    '........'
  ]
};

const SYM_KEYS = ['apple', 'orange', 'lemon', 'bell', 'watermelon', 'star', 'seven', 'bar'];

function drawScanlines(ctx) {
  ctx.fillStyle = PAL.bg;
  ctx.fillRect(0, 0, POCKET_LCD_W, POCKET_LCD_H);
  ctx.fillStyle = PAL.bgHi;
  for (let y = 0; y < POCKET_LCD_H; y += 4) {
    if (y % 8 === 0) ctx.fillRect(0, y, POCKET_LCD_W, 1);
  }
}

/** 外圈 24 格：上 8、右 4、下 8、左 4，等大正方格、順時針自左上起。 */
const RING_CELL = 14;
const RING_COLS = 8;
const RING_ROWS_SIDE = 4;
const RING_OX = (POCKET_LCD_W - RING_COLS * RING_CELL) / 2;
const RING_OY = 16;

export function pocketMaryRingPos(i) {
  const idx = ((Math.floor(Number(i)) % POCKET_MARY_RING_LEN) + POCKET_MARY_RING_LEN) % POCKET_MARY_RING_LEN;
  let x;
  let y;
  if (idx < 8) {
    x = RING_OX + idx * RING_CELL;
    y = RING_OY;
  } else if (idx < 12) {
    x = RING_OX + (RING_COLS - 1) * RING_CELL;
    y = RING_OY + RING_CELL + (idx - 8) * RING_CELL;
  } else if (idx < 20) {
    x = RING_OX + (19 - idx) * RING_CELL;
    y = RING_OY + RING_CELL + RING_ROWS_SIDE * RING_CELL;
  } else {
    x = RING_OX;
    y = RING_OY + RING_CELL + (23 - idx) * RING_CELL;
  }
  return { x: x + RING_CELL / 2, y: y + RING_CELL / 2 };
}

function ringPos(i) {
  return pocketMaryRingPos(i);
}

export function pocketMaryRingHoleCenter() {
  return {
    x: POCKET_LCD_W / 2,
    y: RING_OY + RING_CELL + (RING_ROWS_SIDE * RING_CELL) / 2
  };
}

function spriteKey(cell) {
  if (!cell) return null;
  if (cell.t === 'lucky') return 'lucky';
  if (cell.t === 'again') return 'again';
  const id = Math.floor(cell.id);
  if (id >= 0 && id < SYM_KEYS.length) return SYM_KEYS[id];
  return 'apple';
}

function drawPixelSprite(ctx, key, cx, cy, on, flashOn) {
  const rows = PX[key];
  if (!rows) return;
  const sz = 8;
  const x0 = cx - sz / 2;
  const y0 = cy - sz / 2;
  const ink = on && flashOn ? PAL.bg : on ? PAL.bgHi : PAL.ink;
  const inkLo = on && flashOn ? PAL.bgHi : on ? PAL.dim : PAL.inkLo;
  for (let y = 0; y < rows.length; y++) {
    const row = rows[y];
    for (let x = 0; x < row.length; x++) {
      const c = row[x];
      if (c === '.') continue;
      ctx.fillStyle = c === '2' ? inkLo : ink;
      ctx.fillRect(x0 + x, y0 + y, 1, 1);
    }
  }
}

function drawRingCell(ctx, i, lit, flashOn) {
  const p = ringPos(i);
  const cell = POCKET_MARY_RING[i];
  const on = lit === i;
  const w = RING_CELL;
  const h = RING_CELL;
  const x = p.x - w / 2;
  const y = p.y - h / 2;
  ctx.fillStyle = on && flashOn ? PAL.ink : on ? PAL.inkLo : PAL.dim;
  if (on) ctx.fillRect(x - 1, y - 1, w + 2, h + 2);
  ctx.fillStyle = on ? (flashOn ? PAL.ink : PAL.inkLo) : PAL.bgHi;
  ctx.fillRect(x, y, w, h);
  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 1;
  ctx.strokeRect(x, y, w, h);
  drawPixelSprite(ctx, spriteKey(cell), p.x, p.y, on, flashOn);
}

function drawMaryTitle(ctx, cx, cy, frame) {
  const fr = frame % 48;
  const shift = fr < 8 ? 0 : fr < 16 ? 1 : 0;
  ctx.fillStyle = PAL.bgHi;
  ctx.fillRect(cx - 30 - shift, cy - 9, 60, 12);
  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 1;
  ctx.strokeRect(cx - 30 - shift, cy - 9, 60, 12);
  ctx.fillStyle = PAL.ink;
  ctx.font = 'bold 9px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText('小瑪莉', cx - shift, cy - 3);
}

function drawBetRow(ctx, st, lit, flashOn) {
  const yIcon = POCKET_LCD_H - 18;
  const yNum = POCKET_LCD_H - 8;
  const n = POCKET_MARY_SYMBOL_COUNT;
  const slotW = POCKET_LCD_W / n;
  const bets = st.bets || [];
  for (let id = 0; id < n; id++) {
    const cx = slotW * id + slotW / 2;
    const on = st.phase !== MARY_PHASE.BET && lit >= 0 && POCKET_MARY_RING[lit] &&
      POCKET_MARY_RING[lit].t === 'sym' && POCKET_MARY_RING[lit].id === id;
    const cur = st.phase === MARY_PHASE.BET && st.cursor === id;
    if (cur) {
      ctx.fillStyle = PAL.inkLo;
      ctx.fillRect(cx - 9, yIcon - 10, 18, 20);
    }
    drawPixelSprite(ctx, SYM_KEYS[id], cx, yIcon - 2, on || cur, flashOn);
    const amt = Math.max(0, Math.floor(bets[id] || 0));
    ctx.fillStyle = PAL.ink;
    ctx.font = '6px monospace';
    ctx.textAlign = 'center';
    ctx.textBaseline = 'top';
    ctx.fillText(String(amt), cx, yNum);
  }
}

export function drawPocketMaryLcd(ctx, state, frame) {
  if (!ctx) return;
  const st = state || createMaryState(0);
  const fr = frame != null ? Math.floor(Number(frame)) : 0;
  const flashOn = st.flash <= 0 || fr % 8 < 4;
  const lit = st.phase === MARY_PHASE.SPIN ? st.litIndex : st.stopIndex;

  drawScanlines(ctx);

  ctx.fillStyle = PAL.ink;
  ctx.font = 'bold 8px monospace';
  ctx.textAlign = 'left';
  ctx.textBaseline = 'top';
  ctx.fillText('CREDIT ' + Math.max(0, Math.floor(st.credit)), 4, 3);
  ctx.textAlign = 'right';
  ctx.fillText('WIN ' + Math.max(0, Math.floor(st.pendingWin)), POCKET_LCD_W - 4, 3);

  for (let i = 0; i < POCKET_MARY_RING_LEN; i++) {
    drawRingCell(ctx, i, lit, flashOn);
  }

  const { x: cx, y: cy } = pocketMaryRingHoleCenter();
  ctx.fillStyle = PAL.bgHi;
  ctx.fillRect(cx - 32, cy - 14, 64, 28);
  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 1;
  ctx.strokeRect(cx - 32, cy - 14, 64, 28);

  drawMaryTitle(ctx, cx, cy - 6, fr);

  ctx.fillStyle = PAL.ink;
  ctx.font = '7px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  const statusShort = st.status || '';
  ctx.fillText(statusShort.slice(0, 10), cx, cy + 8);

  if (st.phase === MARY_PHASE.BET) {
    const total = maryTotalBet(st);
    ctx.font = '6px monospace';
    ctx.fillText('總' + total, cx, cy + 17);
  } else if (st.phase === MARY_PHASE.DOUBLE) {
    const hint = st.doublePick === 'small' ? '1-6小' : st.doublePick === 'big' ? '8-13大' : '左右押';
    ctx.font = '6px monospace';
    ctx.fillText(hint + ' A比', cx, cy + 17);
  } else if (st.phase === MARY_PHASE.WIN) {
    ctx.font = '6px monospace';
    ctx.fillText('A收 B比', cx, cy + 17);
  }

  drawBetRow(ctx, st, lit, flashOn);
}

export function drawPocketIdleLcd(ctx, opts) {
  const chips = opts && opts.chips != null ? Math.max(0, Math.floor(Number(opts.chips))) : 0;
  const st = createMaryState(chips);
  const frame = opts && opts.frame != null ? Math.floor(Number(opts.frame)) : 0;
  const pressed = (opts && opts.pressedKeys) || {};
  drawPocketMaryLcd(ctx, st, frame);
  if (pressed.start || pressed.a) {
    /* 按鍵閃爍由外框處理 */
  }
}

export function drawPocketMaryPreviewLcd(ctx) {
  drawPocketMaryLcd(ctx, maryPreviewState(), 0);
}

export function makePocketLcdCanvas(doc, win) {
  const d = doc || globalThis.document;
  const c = d && d.createElement ? d.createElement('canvas') : null;
  if (!c) return null;
  c.width = POCKET_LCD_W;
  c.height = POCKET_LCD_H;
  return c;
}