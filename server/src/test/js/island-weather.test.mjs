// node --test server/src/test/js/island-weather.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const W = await loadPublicModule('island-weather.js');

test('weatherVisualTargets 對應', () => {
  const rain = W.weatherVisualTargets('rain');
  assert.ok(rain.fogNearMul < 1);
  assert.ok(rain.sunMul < 1);
  assert.equal(rain.waveMul, W.WAVE_MUL.rain);
  assert.ok(rain.rainActive);
  const storm = W.weatherVisualTargets('storm');
  assert.ok(storm.waveMul >= 1.8);
});

test('雨絲上限與畫質 tier', () => {
  assert.equal(W.rainStreakMaxForTier('high', false), 4000);
  assert.equal(W.rainStreakMaxForTier('shadowOff', false), 1200);
  assert.equal(W.rainStreakMaxForTier('high', true), 1200);
});

test('?weather= 覆寫解析', () => {
  const q = W.parseWorldWeatherQuery('?weather=storm&temp=32&news=0');
  assert.equal(q.weather, 'storm');
  assert.equal(q.tempC, 32);
  assert.equal(q.newsFlag, false);
});

test('reduced 時 blend 可直接到目標', () => {
  const mid = W.blendWeatherVisual('clear', 'storm', 0.5);
  assert.ok(mid.waveMul > 1 && mid.waveMul < 2.2);
});

test('mergeWorldPayload 覆寫優先', () => {
  const merged = W.mergeWorldPayload(
    { weather: { kind: 'clear', tempC: 20 }, news: [{ title: 'A' }] },
    { weather: 'fog', tempC: 18 },
    Date.now()
  );
  assert.equal(merged.weather.kind, 'fog');
  assert.equal(merged.weather.tempC, 18);
});