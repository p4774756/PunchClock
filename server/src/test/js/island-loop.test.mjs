// 動畫迴圈純邏輯：node --test server/src/test/js/island-loop.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const loopMod = await loadPublicModule('island-loop.js');
const { createUpdateLoop } = loopMod;

test('tick 依登記順序傳入 dt 與 elapsed', () => {
  const loop = createUpdateLoop({
    requestAnimationFrame() { return 1; },
    cancelAnimationFrame() {}
  });
  const seen = [];
  loop.register((dt, elapsed) => seen.push(['a', dt, elapsed]));
  loop.register((dt, elapsed) => seen.push(['b', dt, elapsed]));
  loop.tick(0.1, 2);
  assert.deepEqual(seen, [['a', 0.1, 2], ['b', 0.1, 2]]);
});

test('更新函式取消自己時不會跳過後面的', () => {
  const loop = createUpdateLoop({
    requestAnimationFrame() { return 1; },
    cancelAnimationFrame() {}
  });
  const seen = [];
  let offA = () => {};
  offA = loop.register((dt, elapsed) => {
    seen.push(['a', dt, elapsed]);
    offA();
  });
  loop.register((dt, elapsed) => seen.push(['b', dt, elapsed]));
  loop.tick(0.1, 2);
  assert.deepEqual(seen, [['a', 0.1, 2], ['b', 0.1, 2]]);
  seen.length = 0;
  loop.tick(0.2, 3);
  assert.deepEqual(seen, [['b', 0.2, 3]]);
});

test('unregister 之後不再呼叫', () => {
  const loop = createUpdateLoop({
    requestAnimationFrame() { return 1; },
    cancelAnimationFrame() {}
  });
  const seen = [];
  const fn = () => seen.push('a');
  loop.register(fn);
  loop.register(() => seen.push('b'));
  loop.unregister(fn);
  loop.tick(0, 0);
  assert.deepEqual(seen, ['b']);
});

test('stop 之後排程中的畫面不再執行', () => {
  const queued = [];
  let cancelled = 0;
  const loop = createUpdateLoop({
    requestAnimationFrame(cb) {
      queued.push(cb);
      return queued.length;
    },
    cancelAnimationFrame() { cancelled += 1; }
  });
  let calls = 0;
  loop.start(() => { calls += 1; });
  loop.start(() => { calls += 1; });
  assert.equal(queued.length, 1);
  queued[0]();
  assert.equal(calls, 1);
  assert.equal(queued.length, 2);
  loop.stop();
  assert.equal(cancelled, 1);
  queued[1]();
  assert.equal(calls, 1);
});
