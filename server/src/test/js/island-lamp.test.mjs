// 島嶼路燈光暈／點光純邏輯：node --test server/src/test/js/island-lamp.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const {
  MAX_LAMP_POINT_LIGHTS,
  lampHaloOpacity,
  lampGroundHaloRadius,
  lampPointIntensity,
  applyLampMixState
} = await loadPublicModule('island-kit.js');

test('白天地面光暈完全不可見', () => {
  assert.equal(lampHaloOpacity(16, 0), 0);
  assert.equal(lampHaloOpacity(8, 0), 0);
});

test('夜間光暈隨 power 與 mix 放大', () => {
  const full = lampHaloOpacity(16, 1);
  const half = lampHaloOpacity(16, 0.5);
  assert.ok(full > 0.55);
  assert.ok(Math.abs(half - full * 0.5) < 1e-9);
  assert.ok(lampHaloOpacity(8, 1) < full);
});

test('地面光暈半徑隨 power 增大', () => {
  assert.ok(lampGroundHaloRadius(12) > lampGroundHaloRadius(6));
  assert.ok(lampGroundHaloRadius(10) > 1.5);
});

test('關閉點光時強度為 0，開啟時與 power·mix 成正比', () => {
  assert.equal(lampPointIntensity(16, 1, false), 0);
  assert.equal(lampPointIntensity(16, 1, true), 16);
  assert.equal(lampPointIntensity(9, 0.4, true), 3.6);
});

test('applyLampMixState 同步光暈與點光', () => {
  const haloMat = { opacity: 0.5 };
  const light = { intensity: 99 };
  const item = {
    power: 10,
    realLight: true,
    halo: { material: haloMat },
    light: light
  };
  applyLampMixState(item, 0.5, true);
  assert.equal(haloMat.opacity, lampHaloOpacity(10, 0.5));
  assert.equal(light.intensity, 5);
  applyLampMixState(item, 1, true);
  assert.equal(light.intensity, 10);
  assert.ok(haloMat.opacity > 0);
});

test('最低畫質仍保留點光強度（不依 pointLightsEnabled 關燈）', () => {
  assert.equal(lampPointIntensity(12, 0.5, true), 6);
  assert.equal(lampPointIntensity(12, 0.5, false), 0);
});

test('場景 PointLight 上限為 4', () => {
  assert.equal(MAX_LAMP_POINT_LIGHTS, 4);
});