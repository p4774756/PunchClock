// 自適應畫質純邏輯：node --test server/src/test/js/island-quality.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const Q = await loadPublicModule('island-quality.js');
const {
  createQualityAdvisor,
  qualityTierSettings,
  createFpsRollingStats,
  formatExplorePerfOverlay,
  DEFAULT_MAX_PIXEL_RATIO
} = Q;

test('預設高畫質：像素比上限 1.5、陰影 1024', () => {
  const s = qualityTierSettings('high', 2);
  assert.equal(s.pixelRatio, DEFAULT_MAX_PIXEL_RATIO);
  assert.equal(s.shadowEnabled, true);
  assert.equal(s.shadowMapSize, 1024);
});

test('連續掉幀會依序降階，順暢後恢復', () => {
  const advisor = createQualityAdvisor({ slowNeed: 3, fastNeed: 3, getDevicePixelRatio: () => 2 });
  assert.equal(advisor.tier, 'high');

  for (let i = 0; i < 3; i++) advisor.observeFrame(30);
  assert.equal(advisor.tier, 'dpr1');

  for (let i = 0; i < 3; i++) advisor.observeFrame(30);
  assert.equal(advisor.tier, 'shadowLow');

  for (let i = 0; i < 3; i++) advisor.observeFrame(30);
  assert.equal(advisor.tier, 'shadowOff');

  for (let i = 0; i < 3; i++) advisor.observeFrame(12);
  assert.equal(advisor.tier, 'shadowLow');

  advisor.reset();
  assert.equal(advisor.tier, 'high');
});

test('observeFrame 回報畫質升降方向', () => {
  const advisor = createQualityAdvisor({ slowNeed: 1, fastNeed: 1, getDevicePixelRatio: () => 2 });
  const down = advisor.observeFrame(30);
  assert.equal(down.tierDirection, 'down');
  const up = advisor.observeFrame(12);
  assert.equal(up.tierDirection, 'up');
});

test('滾動幀率統計與 perf 面板格式', () => {
  const rolling = createFpsRollingStats(1000);
  rolling.push(100, 60);
  rolling.push(150, 30);
  const snap = rolling.snapshot();
  assert.equal(snap.count, 2);
  assert.equal(snap.min, 30);
  assert.equal(snap.avg, 45);
  const text = formatExplorePerfOverlay(
    { fps: 55, label: '高' },
    snap,
    { down: 2, up: 1 }
  );
  assert.match(text, /幀率約 55/);
  assert.match(text, /平均 45/);
  assert.match(text, /最低 30/);
  assert.match(text, /降階：2 次/);
});