// 塗鴉牆純邏輯：署名、空白畫布、牆面格子。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const G = await loadPublicModule('island-graffiti.js');

test('署名去掉空白與控制字元，空白用路過的旅人', () => {
  assert.equal(G.normalizeSignature('  小明  '), '小明');
  assert.equal(G.normalizeSignature('阿\u0007明'), '阿明');
  assert.equal(G.normalizeSignature('   '), G.ANONYMOUS_SIGNATURE);
  assert.equal(G.normalizeSignature(null), G.ANONYMOUS_SIGNATURE);
  assert.equal(G.normalizeSignature('\u0000'), G.ANONYMOUS_SIGNATURE);
});

test('署名超過 12 字不能用', () => {
  assert.equal(G.normalizeSignature('一二三四五六七八九十甲乙丙'), null);
  assert.equal(G.normalizeSignature('一二三四五六七八九十甲乙'), '一二三四五六七八九十甲乙');
});

test('完全透明的畫布是空白，有筆畫就不是', () => {
  const empty = { data: new Uint8ClampedArray(16) };
  assert.equal(G.isBlankCanvas(empty), true);
  const ink = { data: new Uint8ClampedArray([0, 0, 0, 0, 10, 20, 30, 255]) };
  assert.equal(G.isBlankCanvas(ink), false);
  assert.equal(G.isBlankCanvas(null), true);
});

test('牆面只排最新的 12 幅，空位是空白磚', () => {
  const items = [];
  for (let i = 0; i < 3; i++) items.push({ id: 'g' + i });
  const slots = G.wallSlots(items);
  assert.equal(slots.length, G.WALL_SLOTS);
  assert.equal(slots[0].id, 'g0');
  assert.equal(slots[2].id, 'g2');
  assert.equal(slots[3], null);
  assert.equal(slots[11], null);
});

test('日期格式是本地年月日時分', () => {
  const ms = new Date(2026, 0, 2, 3, 4).getTime();
  assert.equal(G.formatGraffitiDate(ms), '2026-01-02 03:04');
  assert.equal(G.formatGraffitiDate('nope'), '');
});
