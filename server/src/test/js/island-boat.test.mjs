// node --test server/src/test/js/island-boat.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const B = await loadPublicModule('island-boat.js');
const L = await loadPublicModule('island-layout.js');

test('狀態機：停靠 → 出航 → 抵達 → 回程', () => {
  let state = B.createBoatState('main');
  assert.equal(state.phase, 'docked-main');
  state = B.startSailing(state, 'main');
  assert.equal(state.phase, 'sailing-out');
  state = B.stepBoat(state, 2, { duration: 10 });
  assert.equal(state.phase, 'sailing-out');
  assert.ok(state.progress > 0);
  state = B.stepBoat(state, 12, { duration: 10 });
  assert.equal(state.phase, 'docked-beach');
  state = B.startSailing(state, 'beach');
  assert.equal(state.phase, 'sailing-back');
  state = B.stepBoat(state, 15, { duration: 10 });
  assert.equal(state.phase, 'docked-main');
});

test('暫停時航程不前進', () => {
  let state = B.startSailing(B.createBoatState('main'), 'main');
  const before = state.progress;
  state = B.stepBoat(state, 1, { paused: true, duration: 10 });
  assert.equal(state.progress, before);
});

test('reduced 時直接到對岸', () => {
  let state = B.startSailing(B.createBoatState('main'), 'main');
  state = B.stepBoat(state, 0.1, { reduced: true, duration: 10 });
  assert.equal(state.phase, 'docked-beach');
  state = B.startSailing(state, 'beach');
  state = B.stepBoat(state, 0.1, { reduced: true, duration: 10 });
  assert.equal(state.phase, 'docked-main');
});

test('sampleRoute 起訖點與航線點一致', () => {
  const pts = L.BOAT_ROUTE;
  const start = B.sampleRoute(0);
  const end = B.sampleRoute(1);
  assert.ok(Math.hypot(start.x - pts[0].x, start.z - pts[0].z) < 0.02);
  assert.ok(Math.hypot(end.x - pts[pts.length - 1].x, end.z - pts[pts.length - 1].z) < 0.02);
  const mid = B.sampleRoute(0.5);
  assert.ok(Math.hypot(mid.x - pts[1].x, mid.z - pts[1].z) < 0.25);
  const yaw = B.sampleRoute(0.25).yaw;
  assert.ok(Number.isFinite(yaw));
});

test('航線在海面上、不落在可走區', () => {
  assert.ok(B.routeInOpenWater(L.BOAT_ROUTE, 0.29, 48));
  for (let i = 0; i <= 48; i++) {
    const u = i / 48;
    const p = B.sampleRoute(u);
    assert.ok(!L.walkable(p.x, p.z, 0.29), 'u=' + u + ' 不應在陸地');
  }
});

test('下船點可站立', () => {
  assert.ok(B.disembarkPointsStand(0.29));
});

test('abortSailing 會抵達目的地', () => {
  let state = B.startSailing(B.createBoatState('main'), 'main');
  state = B.abortSailing(state);
  assert.equal(state.phase, 'docked-beach');
});

test('遊艇站位與船首軸向常數', () => {
  assert.ok(B.BOAT_BOW_LOCAL_Z > 0, '船首在本地 +Z');
  assert.ok(B.BOAT_STERN_DECK_Z < 0, '船尾甲板在本地 -Z');
  assert.equal(B.BOAT_SEAT_STERN_OFFSET, -B.BOAT_STERN_DECK_Z, '站位沿船尾對齊甲板中心');
  const sternDeckDepth = 0.4;
  const deckHalf = sternDeckDepth / 2;
  const seatLocalZ = -B.BOAT_SEAT_STERN_OFFSET;
  assert.ok(
    seatLocalZ >= B.BOAT_STERN_DECK_Z - deckHalf && seatLocalZ <= B.BOAT_STERN_DECK_Z + deckHalf,
    '站位落在船尾木甲板 Z 範圍內'
  );
  assert.equal(B.BOAT_DECK_OFFSET, 0.36);
  assert.ok(B.boatHullPartCount() <= B.BOAT_HULL_MESH_BUDGET);
  const stern = B.sampleRoute(0);
  const bowish = B.sampleRoute(0.08);
  const dx = bowish.x - stern.x;
  const dz = bowish.z - stern.z;
  const yaw = Math.atan2(dx, dz);
  assert.ok(Math.abs(yaw - stern.yaw) < 0.35, '航向沿 +Z 船首');
});