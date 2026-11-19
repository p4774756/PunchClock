import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const T = await loadPublicModule('island-tcm-visit.js');
const L = await loadPublicModule('island-layout.js');
const X = await loadPublicModule('island-explore.js');
const Input = await loadPublicModule('island-explore-input.js');
const Interior = await loadPublicModule('island-interior.js');

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
  assert.equal(read.target, 'island');
  const consumed = T.consumeTcmReturn(shim);
  assert.ok(consumed);
  assert.equal(consumed.target, 'island');
  assert.equal(T.readTcmReturn(shim), null);
});

test('writeTcmReturn：hall 與 island 目標、舊資料缺 target 視為 island', () => {
  const storage = new Map();
  const shim = {
    setItem: (k, v) => storage.set(k, v),
    getItem: (k) => (storage.has(k) ? storage.get(k) : null),
    removeItem: (k) => storage.delete(k)
  };
  assert.equal(T.writeTcmReturn(shim, null, { target: 'hall' }), true);
  const hall = T.readTcmReturn(shim);
  assert.ok(hall);
  assert.equal(hall.target, 'hall');
  assert.equal(hall.buildingId, 'tcm-site');
  const room = Interior.interiorByBuilding('tcm-site');
  assert.equal(Interior.interiorCanStand(hall.x, hall.z, room, RADIUS), true);
  const session = Interior.createInteriorSession();
  session.enter('tcm-site', 0);
  const study = room.interacts.find((z) => z.id === 'tcm-study');
  assert.ok(study.r >= 1.6 && study.r <= 2.0);
  const dist = Math.hypot(hall.x - study.x, hall.z - study.z);
  assert.ok(dist > study.r + RADIUS, '回館站位不會立刻再進研習室');
  assert.equal(X.hitInteractZone(hall.x, hall.z, session.interactZones()), null);
  assert.ok(X.hitInteractZone(0, 9, session.interactZones()), '沿地毯較北處應能進研習室');
  shim.setItem(T.TCM_RETURN_STORAGE_KEY, JSON.stringify({
    explore: true,
    x: -14.8,
    z: 1.15,
    yaw: 1.57
  }));
  const legacy = T.readTcmReturn(shim);
  assert.equal(legacy.target, 'island');
});

test('enterTcm 不會落到開賭場', () => {
  let casinoOpened = false;
  let entered = false;
  const handled = Input.applyOutdoorInteractZone(
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
  assert.equal(L.isClosedHall('enterComputer'), false);
});