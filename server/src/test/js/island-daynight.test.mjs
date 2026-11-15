// 晝夜混合純邏輯：node --test server/src/test/js/island-daynight.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const dayNightMod = await loadPublicModule('island-daynight.js');
const kitMod = await loadPublicModule('island-kit.js');
const { createDayNight } = dayNightMod;
const { nightHex } = kitMod;

test('夜間對照表有已知色，沒有的回傳 null', () => {
  assert.equal(nightHex(0xa8d06e), 0x3d5d42);
  assert.equal(nightHex(0xf4e6cf), 0x6e6252);
  assert.equal(nightHex(0x123456), null);
});

test('setTarget 後 step 以每幀 0.06 靠近目標', () => {
  const dayNight = createDayNight({ reduced: false });
  const seen = [];
  dayNight.onMix((mix) => seen.push(mix));
  dayNight.setTarget(1);
  assert.equal(dayNight.target, 1);
  assert.equal(dayNight.mix, 0);
  assert.deepEqual(seen, []);
  dayNight.step(0.016);
  assert.ok(Math.abs(dayNight.mix - 0.06) < 1e-12);
  dayNight.step(0.016);
  const second = 0.06 + (1 - 0.06) * 0.06;
  assert.ok(Math.abs(dayNight.mix - second) < 1e-12);
  assert.equal(seen.length, 2);
  assert.ok(Math.abs(seen[0] - 0.06) < 1e-12);
  assert.ok(Math.abs(seen[1] - second) < 1e-12);
});

test('減少動態或分頁隱藏時立刻跳到目標', () => {
  const reduced = createDayNight({ reduced: true });
  const seen = [];
  reduced.onMix((mix) => seen.push(mix));
  reduced.setTarget(1);
  assert.equal(reduced.mix, 1);
  assert.deepEqual(seen, [1]);

  let hidden = false;
  const paused = createDayNight({ reduced: false, hidden: () => hidden });
  const pausedSeen = [];
  paused.onMix((mix) => pausedSeen.push(mix));
  hidden = true;
  paused.setTarget(1);
  assert.equal(paused.mix, 1);
  assert.deepEqual(pausedSeen, [1]);
});

test('取消訂閱後不再收到混合值，其他訂閱者仍會收到', () => {
  const dayNight = createDayNight({ reduced: false });
  const seen = [];
  const off = dayNight.onMix((mix) => seen.push(['a', mix]));
  dayNight.onMix((mix) => {
    seen.push(['b', mix]);
    off();
  });
  dayNight.onMix((mix) => seen.push(['c', mix]));
  dayNight.setTarget(1);
  dayNight.step(0.016);
  assert.deepEqual(seen.map((item) => item[0]), ['a', 'b', 'c']);
  seen.length = 0;
  dayNight.step(0.016);
  assert.deepEqual(seen.map((item) => item[0]), ['b', 'c']);
});
