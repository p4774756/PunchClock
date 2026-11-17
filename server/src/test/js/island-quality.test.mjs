// 自適應畫質純邏輯：node --test server/src/test/js/island-quality.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const Q = await loadPublicModule('island-quality.js');
const { createQualityAdvisor, qualityTierSettings, DEFAULT_MAX_PIXEL_RATIO } = Q;

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