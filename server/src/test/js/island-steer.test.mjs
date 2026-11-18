// node --test server/src/test/js/island-steer.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const B = await loadPublicModule('island-boat.js');
const L = await loadPublicModule('island-layout.js');

test('狀態機：docked-main → steering → stopSteering → docked-*', () => {
  let state = B.createBoatState('main');
  assert.equal(state.phase, 'docked-main');
  state = B.startSteering(state, 'main');
  assert.equal(state.phase, 'steering');
  assert.ok(Number.isFinite(state.x));
  state = B.stopSteering(state, 'main');
  assert.equal(state.phase, 'docked-main');
  state = B.createBoatState('beach');
  state = B.startSteering(state, 'beach');
  assert.equal(state.phase, 'steering');
  state = B.stopSteering(state, 'beach');
  assert.equal(state.phase, 'docked-beach');
});

test('掌舵與自動航行互斥', () => {
  let state = B.startSteering(B.createBoatState('main'), 'main');
  assert.equal(state.phase, 'steering');
  state = B.startSailing(state, 'main');
  assert.equal(state.phase, 'steering');
  state = B.stopSteering(state, 'main');
  state = B.startSailing(state, 'main');
  assert.equal(state.phase, 'sailing-out');
  state = B.startSteering(state, 'main');
  assert.equal(state.phase, 'sailing-out');
});

test('boatNavigable：泊位、海路、島心、橋面、邊界', () => {
  const mainBerth = L.BOAT_STOPS.main.berth;
  const beachBerth = L.BOAT_STOPS.beach.berth;
  assert.equal(B.boatNavigable(mainBerth.x, mainBerth.z), true, '主島泊位');
  assert.equal(B.boatNavigable(beachBerth.x, beachBerth.z), true, '沙灘泊位');
  const mid = L.BOAT_ROUTE[1];
  assert.equal(B.boatNavigable(mid.x, mid.z), true, '航線中點');
  assert.equal(B.boatNavigable(L.ISLANDS[0].x, L.ISLANDS[0].z), false, '主島中心');
  const bridge = L.BRIDGES[0];
  const bx = (bridge.ax + bridge.bx) * 0.5;
  const bz = (bridge.az + bridge.bz) * 0.5;
  assert.equal(B.boatNavigable(bx, bz), false, '橋面中央');
  assert.equal(B.boatNavigable(L.SEA_NAV_BOUNDS.minX - 2, 0), false, '海域西界外');
  assert.equal(B.boatNavigable(L.SEA_NAV_BOUNDS.maxX + 2, 6), false, '海域東界外');
});

test('resolveBoatMove：朝島心推進不會穿陸', () => {
  const island = L.ISLANDS[0];
  const startX = island.x + island.r + 1.8;
  const startZ = island.z;
  const towardX = island.x + island.r * 0.2;
  const next = B.resolveBoatMove(startX, startZ, towardX, startZ);
  assert.ok(!L.walkable(next.x, next.z, 0.2));
  assert.ok(B.boatNavigable(next.x, next.z));
  assert.ok(Math.hypot(next.x - towardX, next.z - startZ) > 0.05 || next.x === startX);
});

test('靠岸判定：泊位半徑內／外', () => {
  const berth = L.BOAT_STOPS.main.berth;
  assert.equal(B.disembarkNear(berth.x, berth.z), 'main');
  assert.equal(B.disembarkNear(berth.x + 0.6, berth.z), 'main');
  assert.equal(B.disembarkNear(berth.x + 2.5, berth.z), null);
  assert.equal(B.disembarkNear(12.85, 3.75), null);
});

test('reduced：stepSteering 不保留慣性', () => {
  let state = B.startSteering(B.createBoatState('main'), 'main');
  state = B.stepSteering(state, { x: 1, z: 0, moving: true }, 0.2, { reduced: true });
  assert.ok(Math.abs(state.vx) > 0.01);
  state = B.stepSteering(state, { x: 0, z: 0, moving: false }, 0.2, { reduced: true });
  assert.equal(state.vx, 0);
  assert.equal(state.vz, 0);
});

test('voyageUiPaused 等價：清單開啟（islandUiOpen）時 stepSteering 速度歸零', () => {
  const voyageUiPaused = (mode, islandUiOpen) => mode.paused || islandUiOpen();
  let state = B.startSteering(B.createBoatState('main'), 'main');
  state = B.stepSteering(state, { x: 0, z: 1, moving: true }, 0.15, {});
  const hadV = Math.hypot(state.vx, state.vz);
  assert.ok(hadV > 0.05);
  const paused = voyageUiPaused({ paused: false }, () => true);
  assert.equal(paused, true, '清單等 UI 開啟應暫停');
  state = B.stepSteering(state, { x: 0, z: 1, moving: true }, 0.1, { paused: true });
  assert.equal(state.vx, 0);
  assert.equal(state.vz, 0);
});

test('voyageUiPaused 等價：paused 時 stepSteering 速度歸零', () => {
  let state = B.startSteering(B.createBoatState('main'), 'main');
  state = B.stepSteering(state, { x: 0, z: 1, moving: true }, 0.15, {});
  const hadV = Math.hypot(state.vx, state.vz);
  assert.ok(hadV > 0.05);
  state = B.stepSteering(state, { x: 0, z: 1, moving: true }, 0.1, { paused: true });
  assert.equal(state.vx, 0);
  assert.equal(state.vz, 0);
});

test('abortSteering 回到上船碼頭', () => {
  let state = B.startSteering(B.createBoatState('beach'), 'beach');
  state = B.stepSteering(state, { x: -0.4, z: 0.2, moving: true }, 2, {});
  state = B.abortSteering(state);
  assert.equal(state.phase, 'docked-beach');
});

test('加大船體半徑：仍進不了島心、泊位與靠岸仍可行', () => {
  const mainBerth = L.BOAT_STOPS.main.berth;
  const beachBerth = L.BOAT_STOPS.beach.berth;
  const r = B.BOAT_HULL_RADIUS;
  assert.equal(B.boatNavigable(mainBerth.x, mainBerth.z, r), true);
  assert.equal(B.boatNavigable(beachBerth.x, beachBerth.z, r), true);
  assert.equal(B.boatNavigable(L.ISLANDS[0].x, L.ISLANDS[0].z, r), false);
  assert.equal(B.disembarkNear(mainBerth.x, mainBerth.z, B.BOAT_DISEMBARK_RADIUS), 'main');
  const mid = L.BOAT_ROUTE[1];
  assert.equal(B.boatNavigable(mid.x, mid.z, r), true);
  const toward = B.resolveBoatMove(mid.x, mid.z, L.ISLANDS[0].x, mid.z, r);
  assert.ok(!L.walkable(toward.x, toward.z, 0.2));
});