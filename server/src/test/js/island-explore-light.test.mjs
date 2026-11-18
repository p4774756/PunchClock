// 探索戶外晝夜光量：node --test server/src/test/js/island-explore-light.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const {
  EXPLORE_LIGHT_DAY,
  EXPLORE_LIGHT_NIGHT,
  exploreLightAtMix
} = await loadPublicModule('island-explore-light.js');

test('白天端點維持原本數值', () => {
  const day = exploreLightAtMix(0);
  assert.equal(day.hemiIntensity, EXPLORE_LIGHT_DAY.hemiIntensity);
  assert.equal(day.sunIntensity, EXPLORE_LIGHT_DAY.sunIntensity);
  assert.equal(day.toneMappingExposure, EXPLORE_LIGHT_DAY.toneMappingExposure);
});

test('夜間端點高於 i80 前環境光（補點光縮減）', () => {
  const night = exploreLightAtMix(1);
  assert.equal(night.hemiIntensity, EXPLORE_LIGHT_NIGHT.hemiIntensity);
  assert.ok(night.hemiIntensity >= 0.65);
  assert.ok(night.sunIntensity > 0.5);
  assert.ok(night.toneMappingExposure >= 1.17);
});

test('混合線性內插', () => {
  const mid = exploreLightAtMix(0.5);
  const lo = exploreLightAtMix(0);
  const hi = exploreLightAtMix(1);
  assert.ok(Math.abs(mid.hemiIntensity - (lo.hemiIntensity + hi.hemiIntensity) / 2) < 1e-9);
});