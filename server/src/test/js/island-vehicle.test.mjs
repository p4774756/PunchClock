// node --test server/src/test/js/island-vehicle.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const V = await loadPublicModule('island-vehicle.js');
const C = await loadPublicModule('island-city-data.js');

test('上車、駕駛、下車', () => {
  const car = C.pinkCarWorld();
  let state = V.createVehicleState();
  assert.equal(state.phase, 'on-foot');
  state = V.enterVehicle(state, car.x, car.z);
  assert.equal(state.phase, 'driving');
  assert.ok(V.isDrivingPhase(state.phase));
  const pack = V.exitVehicle(state);
  assert.equal(pack.state.phase, 'on-foot');
  assert.ok(pack.player);
  assert.ok(Math.hypot(pack.player.x - car.x, pack.player.z - car.z) < 1.5);
  assert.equal(pack.state.carX, car.x);
  assert.equal(pack.state.carZ, car.z);
});

test('沿矩形碰撞滑移、不可入海', () => {
  let state = V.createVehicleState();
  state = V.enterVehicle(state, state.carX, state.carZ);
  for (let i = 0; i < 12; i++) {
    state = V.stepVehicle(state, { x: 0, z: 1, moving: true }, 0.12, { reduced: false });
  }
  assert.ok(V.vehicleNavigable(state.carX, state.carZ));
  const towardSea = V.stepVehicle(state, { x: 0, z: 1, moving: true }, 0.25, { reduced: true });
  const island = { x: 0, z: -22, r: 7.65 };
  const dist = Math.hypot(towardSea.carX - island.x, towardSea.carZ - island.z);
  assert.ok(dist <= island.r + 0.05, '車不應穿出城市島');
});

test('reduced／暫停不留速度殘值', () => {
  let state = V.enterVehicle(V.createVehicleState(), C.pinkCarWorld().x, C.pinkCarWorld().z);
  state = V.stepVehicle(state, { x: 0, z: 1, moving: true }, 0.2, { reduced: false });
  assert.ok(Math.hypot(state.vx, state.vz) > 0.05);
  state = V.stepVehicle(state, { x: 0, z: 0, moving: false }, 0.1, { paused: true, reduced: true });
  assert.equal(state.vx, 0);
  assert.equal(state.vz, 0);
  state = V.stepVehicle(state, { x: 0, z: 1, moving: true }, 0.2, { reduced: true });
  assert.ok(Math.abs(state.vz) > 0.01);
  state = V.stepVehicle(state, { x: 0, z: 0, moving: false }, 0.1, { reduced: true });
  assert.equal(state.vx, 0);
  assert.equal(state.vz, 0);
});

test('粉紅小車路邊停靠可立即起步', () => {
  const car = C.pinkCarWorld();
  assert.ok(V.vehicleNavigable(car.x, car.z));
  let state = V.enterVehicle(V.createVehicleState(), car.x, car.z);
  const beforeX = state.carX;
  state = V.stepVehicle(state, { x: -1, z: 0, moving: true }, 0.2, { reduced: false });
  assert.ok(state.carX < beforeX - 0.02, '應能往西（朝車頭）駛離');
});

test('forceExitVehicle', () => {
  let state = V.enterVehicle(V.createVehicleState(), C.pinkCarWorld().x, C.pinkCarWorld().z);
  state = V.stepVehicle(state, { x: 1, z: 0, moving: true }, 0.2, {});
  state = V.forceExitVehicle(state);
  assert.equal(state.phase, 'on-foot');
  assert.equal(state.vx, 0);
});