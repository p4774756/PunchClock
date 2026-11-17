// 生命週期匯流排：node --test server/src/test/js/island-lifecycle.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const { createLifecycle } = await loadPublicModule('island-lifecycle.js');

test('on／emit：依註冊順序呼叫', () => {
  const bus = createLifecycle();
  const order = [];
  bus.on('enterExplore', () => order.push('a'));
  bus.on('enterExplore', () => order.push('b'));
  bus.emit('enterExplore', { ok: true });
  assert.deepEqual(order, ['a', 'b']);
});

test('unsubscribe：之後不再收到', () => {
  const bus = createLifecycle();
  const seen = [];
  const off = bus.on('exitExplore', () => seen.push(1));
  bus.emit('exitExplore');
  off();
  bus.emit('exitExplore');
  assert.deepEqual(seen, [1]);
});

test('多訂閱者：互不影響', () => {
  const bus = createLifecycle();
  const a = [];
  const b = [];
  bus.on('enterRoom', (p) => a.push(p.id));
  bus.on('enterRoom', (p) => b.push(p));
  bus.emit('enterRoom', { id: 'shop' });
  assert.deepEqual(a, ['shop']);
  assert.deepEqual(b, [{ id: 'shop' }]);
});

test('exitRoom 與 visibilityChange 可並存', () => {
  const bus = createLifecycle();
  const rooms = [];
  const vis = [];
  bus.on('exitRoom', (p) => rooms.push(p.id));
  bus.on('visibilityChange', (p) => vis.push(p.hidden));
  bus.emit('exitRoom', { id: 'shop' });
  bus.emit('visibilityChange', { hidden: true });
  bus.emit('visibilityChange', { hidden: false });
  assert.deepEqual(rooms, ['shop']);
  assert.deepEqual(vis, [true, false]);
});

test('clear：移除所有訂閱', () => {
  const bus = createLifecycle();
  let n = 0;
  bus.on('enterExplore', () => { n += 1; });
  bus.on('exitRoom', () => { n += 1; });
  bus.clear();
  bus.emit('enterExplore');
  bus.emit('exitRoom', { id: 'x' });
  assert.equal(n, 0);
});