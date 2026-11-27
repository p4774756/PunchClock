import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const { pocketButtonFromKeyboard, POCKET_BTN_KEYS } = await loadPublicModule('private-team-pocket.js');
const { POCKET_LCD_W, POCKET_LCD_H } = await loadPublicModule('private-team-pocket-lcd.js');

test('彈窗 HTML 與 CSS 結構', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.match(html, /id="pocketBoard"/);
  assert.match(html, /data-pocket-lcd/);
  assert.match(html, /data-pocket-btn="start"/);
  assert.match(css, /image-rendering:\s*pixelated/);
  assert.match(css, /\.pt-pocket-shell/);
});

test('首頁接上掌機彈窗與 3D 點擊', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /wireGameBoyModal/);
  assert.match(js, /target === 'handheld'/);
  assert.match(js, /pt-pocket-open/);
  assert.match(js, /pocketOpen/);
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