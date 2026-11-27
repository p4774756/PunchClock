/** 160×144 復古綠 LCD（窗邊預覽與彈窗共用繪製）。 */

import {
  MARY_PHASE,
  POCKET_MARY_RING,
  POCKET_MARY_RING_LEN,
  POCKET_MARY_SYMBOLS,
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

function drawScanlines(ctx) {
  ctx.fillStyle = PAL.bg;
  ctx.fillRect(0, 0, POCKET_LCD_W, POCKET_LCD_H);
  ctx.fillStyle = PAL.bgHi;
  for (let y = 0; y < POCKET_LCD_H; y += 4) {
    if (y % 8 === 0) ctx.fillRect(0, y, POCKET_LCD_W, 1);
  }
}

function ringPos(i) {
  const cx = POCKET_LCD_W / 2;
  const cy = POCKET_LCD_H / 2 + 4;
  const rx = 58;
  const ry = 44;
  const ang = -Math.PI / 2 + (i / POCKET_MARY_RING_LEN) * Math.PI * 2;
  return {
    x: cx + Math.cos(ang) * rx,
    y: cy + Math.sin(ang) * ry
  };
}

function cellLabel(cell) {
  if (!cell) return '?';
  if (cell.t === 'lucky') return '運';
  if (cell.t === 'again') return '轉';
  const sym = POCKET_MARY_SYMBOLS[cell.id];
  return sym ? sym.short : '?';
}

function drawRingCell(ctx, i, lit, flashOn) {
  const p = ringPos(i);
  const cell = POCKET_MARY_RING[i];
  const on = lit === i;
  const w = 14;
  const h = 11;
  const x = p.x - w / 2;
  const y = p.y - h / 2;
  ctx.fillStyle = on && flashOn ? PAL.ink : on ? PAL.inkLo : PAL.dim;
  if (on) ctx.fillRect(x - 1, y - 1, w + 2, h + 2);
  ctx.fillStyle = on ? PAL.bg : PAL.bgHi;
  ctx.fillRect(x, y, w, h);
  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 1;
  ctx.strokeRect(x, y, w, h);
  ctx.fillStyle = on ? PAL.ink : PAL.inkLo;
  ctx.font = '7px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'middle';
  ctx.fillText(cellLabel(cell), p.x, p.y);
}

export function drawPocketMaryLcd(ctx, state, frame) {
  if (!ctx) return;
  const st = state || createMaryState(0);
  const fr = frame != null ? Math.floor(Number(frame)) : 0;
  const flashOn = st.flash <= 0 || fr % 8 < 4;
  const lit = st.phase === MARY_PHASE.SPIN ? st.litIndex : st.stopIndex;

  drawScanlines(ctx);

  ctx.fillStyle = PAL.ink;
  ctx.font = 'bold 9px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  ctx.fillText('CREDIT ' + Math.max(0, Math.floor(st.credit)), POCKET_LCD_W / 2, 4);

  for (let i = 0; i < POCKET_MARY_RING_LEN; i++) {
    drawRingCell(ctx, i, lit, flashOn);
  }

  const cx = POCKET_LCD_W / 2;
  const cy = POCKET_LCD_H / 2 + 4;
  ctx.fillStyle = PAL.bgHi;
  ctx.fillRect(cx - 34, cy - 22, 68, 44);
  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 2;
  ctx.strokeRect(cx - 34, cy - 22, 68, 44);

  ctx.fillStyle = PAL.ink;
  ctx.font = 'bold 10px monospace';
  ctx.textAlign = 'center';
  ctx.fillText('WIN ' + Math.max(0, Math.floor(st.pendingWin)), cx, cy - 16);

  const cur = POCKET_MARY_SYMBOLS[st.cursor] || POCKET_MARY_SYMBOLS[0];
  const betAmt = Math.max(0, Math.floor((st.bets && st.bets[st.cursor]) || 0));
  ctx.font = '9px monospace';
  ctx.fillText(cur.short + '×' + cur.mult, cx, cy - 2);
  ctx.fillText('注' + betAmt, cx, cy + 10);

  ctx.font = '8px monospace';
  ctx.fillStyle = PAL.inkLo;
  ctx.fillText(st.status || '', cx, cy + 22);

  if (st.phase === MARY_PHASE.BET) {
    const total = maryTotalBet(st);
    ctx.fillText('總注' + total, cx, POCKET_LCD_H - 10);
  } else if (st.phase === MARY_PHASE.DOUBLE) {
    const hint = st.doublePick === 'small' ? '1-6小' : st.doublePick === 'big' ? '8-13大' : '左右押';
    ctx.fillText(hint + ' A比', cx, POCKET_LCD_H - 10);
  } else if (st.phase === MARY_PHASE.WIN) {
    ctx.fillText('A收 B比', cx, POCKET_LCD_H - 10);
  }
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