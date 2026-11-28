import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const { pocketButtonFromKeyboard, POCKET_BTN_KEYS, applyPocketShellFit } = await loadPublicModule(
  'private-team-pocket.js'
);
const {
  POCKET_LCD_W,
  POCKET_LCD_H,
  pocketMaryRingPos,
  pocketMaryRingHoleCenter
} = await loadPublicModule('private-team-pocket-lcd.js');

test('彈窗 HTML 與 CSS 結構', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  const pocketCss = css.slice(css.indexOf('Game Boy 彈窗'), css.indexOf('.lab-office-stage.pt-pocket-open'));
  const pocketJs = readFileSync(join(publicRoot, 'js/private-team-pocket.js'), 'utf8');
  assert.match(html, /id="pocketBoard"/);
  assert.match(html, /class="pt-pocket-fit"/);
  assert.match(html, /data-pocket-lcd/);
  assert.match(html, /data-pocket-btn="start"/);
  assert.match(css, /image-rendering:\s*pixelated/);
  assert.match(css, /\.pt-pocket-shell/);
  assert.doesNotMatch(pocketCss, /zoom:/);
  assert.match(pocketJs, /0\.92 \* v[wh]/);
  assert.match(pocketCss, /transform:\s*scale\(var\(--pt-pocket-scale\)\)/);
  assert.match(pocketCss, /--pt-pocket-design-h/);
  assert.match(pocketCss, /--pt-pocket-scale/);
});

test('首頁接上掌機彈窗與 3D 點擊', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /wireGameBoyModal/);
  assert.match(js, /target === 'handheld'/);
  assert.match(js, /pt-pocket-open/);
  assert.match(js, /pocketOpen/);
});

test('開啟掌機時載入 roadmap 遮罩 CSS，常駐表也有 overlay 定位', () => {
  const pocketJs = readFileSync(join(publicRoot, 'js/private-team-pocket.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.match(pocketJs, /ensureHomeRoadmapCss/);
  assert.match(css, /\.roadmap-board\s*\{[^}]*position:\s*fixed/s);
  assert.match(css, /\.roadmap-board\[hidden\]/);
});

test('鍵盤對應十字鍵與 A/B', () => {
  assert.equal(pocketButtonFromKeyboard({ code: 'ArrowUp' }), 'up');
  assert.equal(pocketButtonFromKeyboard({ code: 'KeyZ' }), 'a');
  assert.equal(pocketButtonFromKeyboard({ code: 'Enter' }), 'start');
  assert.equal(pocketButtonFromKeyboard({ code: 'Unknown' }), '');
  assert.equal(POCKET_BTN_KEYS.length, 8);
});

test('LCD 為 160×144', () => {
  assert.equal(POCKET_LCD_W, 160);
  assert.equal(POCKET_LCD_H, 144);
});

test('LCD 繪製小瑪莉外圈', () => {
  const lcd = readFileSync(join(publicRoot, 'js/private-team-pocket-lcd.js'), 'utf8');
  assert.match(lcd, /drawPocketMaryLcd/);
  assert.match(lcd, /POCKET_MARY_RING_LEN/);
  assert.doesNotMatch(lcd, /Math\.cos\(ang\)/);
});

test('小瑪莉外圈方形格位', () => {
  const p0 = pocketMaryRingPos(0);
  const p7 = pocketMaryRingPos(7);
  const p8 = pocketMaryRingPos(8);
  const p11 = pocketMaryRingPos(11);
  const p12 = pocketMaryRingPos(12);
  const p19 = pocketMaryRingPos(19);
  const p20 = pocketMaryRingPos(20);
  const p23 = pocketMaryRingPos(23);
  assert.equal(p0.y, p7.y);
  assert.equal(p7.x - p0.x, 7 * 14);
  assert.equal(p8.x, p7.x);
  assert.equal(p11.x, p8.x);
  assert.equal(p11.y - p8.y, 3 * 14);
  assert.equal(p12.y, p11.y + 14);
  assert.equal(p19.y, p12.y);
  assert.equal(p12.x - p19.x, 7 * 14);
  assert.equal(p20.x, p0.x);
  assert.equal(p23.x, p20.x);
  assert.equal(p20.y - p23.y, 3 * 14);
  const hole = pocketMaryRingHoleCenter();
  assert.ok(hole.x > p0.x && hole.x < p7.x);
  assert.ok(hole.y > p0.y && hole.y < p19.y);
});