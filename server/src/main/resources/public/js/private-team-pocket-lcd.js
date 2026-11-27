/** 160×144 復古綠 LCD（窗邊預覽與彈窗共用繪製）。 */

export const POCKET_LCD_W = 160;
export const POCKET_LCD_H = 144;

const PAL = {
  bg: '#9bbc0f',
  bgHi: '#8bac0f',
  ink: '#0f380f',
  inkLo: '#306230'
};

export function drawPocketIdleLcd(ctx, opts) {
  if (!ctx) return;
  const chips = opts && opts.chips != null ? Math.max(0, Math.floor(Number(opts.chips))) : 0;
  const pressed = (opts && opts.pressedKeys) || {};
  const frame = opts && opts.frame != null ? Math.floor(Number(opts.frame)) : 0;

  ctx.fillStyle = PAL.bg;
  ctx.fillRect(0, 0, POCKET_LCD_W, POCKET_LCD_H);

  ctx.fillStyle = PAL.bgHi;
  for (let y = 0; y < POCKET_LCD_H; y += 4) {
    if (y % 8 === 0) ctx.fillRect(0, y, POCKET_LCD_W, 1);
  }

  const titleY = 28 + (frame % 24 < 12 ? 0 : 1);
  ctx.fillStyle = PAL.ink;
  ctx.font = 'bold 14px monospace';
  ctx.textAlign = 'center';
  ctx.textBaseline = 'top';
  ctx.fillText('小瑪莉', POCKET_LCD_W / 2, titleY);

  ctx.font = '10px monospace';
  ctx.fillStyle = PAL.inkLo;
  ctx.fillText('POCKET', POCKET_LCD_W / 2, titleY + 20);

  ctx.strokeStyle = PAL.ink;
  ctx.lineWidth = 2;
  ctx.strokeRect(24, 58, POCKET_LCD_W - 48, 36);

  ctx.fillStyle = PAL.ink;
  ctx.font = '11px monospace';
  ctx.fillText('籌碼 ' + chips, POCKET_LCD_W / 2, 68);

  if (chips <= 0) {
    ctx.font = '9px monospace';
    ctx.fillText('按 Start 補幣', POCKET_LCD_W / 2, 82);
  } else {
    ctx.font = '9px monospace';
    ctx.fillText('待機中', POCKET_LCD_W / 2, 82);
  }

  const keys = ['up', 'down', 'left', 'right', 'a', 'b', 'start', 'select'];
  let hint = '';
  for (let i = 0; i < keys.length; i++) {
    if (pressed[keys[i]]) {
      hint = keys[i].toUpperCase();
      break;
    }
  }
  if (hint) {
    ctx.fillStyle = PAL.ink;
    ctx.font = '9px monospace';
    ctx.fillText('[' + hint + ']', POCKET_LCD_W / 2, POCKET_LCD_H - 14);
  }
}

export function makePocketLcdCanvas(doc, win) {
  const d = doc || globalThis.document;
  const c = d && d.createElement ? d.createElement('canvas') : null;
  if (!c) return null;
  c.width = POCKET_LCD_W;
  c.height = POCKET_LCD_H;
  return c;
}