import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const css = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/css/lab-office.css'),
  'utf8'
);
const js = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/js/lab-office.js'),
  'utf8'
);
const L = await loadPublicModule('lab-office-logic.js');

test('場景熱點無常駐空白方塊圖示', () => {
  assert.match(css, /\.lab-office-prop::before\s*\{\s*content:\s*none;/);
  assert.doesNotMatch(css, /\.lab-office-prop-main/);
  assert.doesNotMatch(css, /\.lab-office-prop-label/);
  assert.doesNotMatch(css, /\.lab-office-prop-sub/);
});

test('打卡機改走今日狀態頁，下載桌維持下載頁', () => {
  assert.match(js, /clock:\s*'\/checkin'/);
  assert.match(js, /desk:\s*'\/download'/);
  assert.doesNotMatch(js, /download#checkin/);
});

test('主控台獨立熱點座標存在', () => {
  assert.ok(L.OFFICE_CONSOLE_POS);
  assert.ok(L.OFFICE_CONSOLE_POS.label);
  assert.ok(L.OFFICE_PROP_POS.guestbook);
  assert.ok(L.OFFICE_PROP_POS.clock.w > 0);
});