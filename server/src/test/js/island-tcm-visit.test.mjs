import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const T = await loadPublicModule('island-tcm-visit.js');
const L = await loadPublicModule('island-layout.js');
const X = await loadPublicModule('island-explore.js');
const I = await loadPublicModule('island-explore-input.js');

const RADIUS = X.PLAYER_RADIUS;

function circleHitsPoint(x, z, r, px, pz) {
  const dx = x - px;
  const dz = z - pz;
  return dx * dx + dz * dz < r * r;
}

test('推拿館回島：門外可站、不在進門互動區', () => {
  const hall = L.buildingById('tcm-site');
  const pose = T.tcmReturnPose(hall);
  assert.ok(pose);
  assert.equal(X.canStand(pose.x, pose.z, RADIUS), true);
  const gap = Math.hypot(pose.x - hall.door.x, pose.z - hall.door.z);
  assert.ok(gap > hall.entry.r, '站點在互動半徑外');
  assert.equal(
    circleHitsPoint(hall.door.x, hall.door.z, hall.entry.r, pose.x, pose.z),
    false
  );
});

test('sessionStorage 往返：寫入、讀取、消耗後不再進探索', () => {
  const storage = new Map();
  const shim = {
    setItem: (k, v) => storage.set(k, v),
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    removeItem: (k) => storage.delete(k)
  };
  assert.equal(T.writeTcmReturn(shim), true);
  const read = T.readTcmReturn(shim);
  assert.ok(read);
  assert.equal(read.explore, true);
  const consumed = T.consumeTcmReturn(shim);
  assert.ok(consumed);
  assert.equal(T.readTcmReturn(shim), null);
});

test('enterTcm 不會落到開賭場', () => {
  let casinoOpened = false;
  let entered = false;
  const handled = I.applyOutdoorInteractZone(
    { action: 'enterTcm' },
    {
      enterTcm: () => { entered = true; },
      openCasino: () => { casinoOpened = true; }
    }
  );
  assert.equal(handled, true);
  assert.equal(entered, true);
  assert.equal(casinoOpened, false);
});

test('isClosedHall 無封閉館（研發樓已開室內）', () => {
  assert.equal(L.isClosedHall('enterTcm'), false);
  assert.equal(L.isClosedHall('enterInterior'), false);
  assert.equal(L.isClosedHall('enterComputer'), true);
});