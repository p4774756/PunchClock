// node --test server/src/test/js/island-minimap.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('island-minimap.js');
const L = await loadPublicModule('island-layout.js');
const C = await loadPublicModule('island-city-data.js');

test('世界座標 ↔ 地圖：主島、城市島在上、電腦島在下', () => {
  const b = M.worldMapBounds();
  const w = 200;
  const h = 160;
  const main = L.islandById('main');
  const city = L.islandById('city');
  const computer = L.islandById('computer');
  const tcm = L.islandById('tcm');

  const pMain = M.worldToMap(main.x, main.z, b, w, h);
  const pCity = M.worldToMap(city.x, city.z, b, w, h);
  const pComputer = M.worldToMap(computer.x, computer.z, b, w, h);
  const pTcm = M.worldToMap(tcm.x, tcm.z, b, w, h);

  assert.ok(pCity.py < pComputer.py, '城市島（−Z）應在電腦島上方');
  assert.ok(pCity.py < pMain.py, '城市島比主島更靠北（畫面上方）');
  assert.ok(pTcm.px < pMain.px, '推拿島在西側');

  const back = M.mapToWorld(pMain.px, pMain.py, b, w, h);
  assert.ok(Math.abs(back.x - main.x) < 0.05);
  assert.ok(Math.abs(back.z - main.z) < 0.05);

  assert.deepEqual(M.worldNorm(0, 0), M.worldNorm(main.x, main.z));
  const cityNorm = M.worldNorm(city.x, city.z);
  const compNorm = M.worldNorm(computer.x, computer.z);
  assert.ok(cityNorm.v < compNorm.v);
});

test('區域名稱：島上、橋上、海域與室內', () => {
  assert.equal(M.resolveAreaName({ x: 0, z: 0 }), '神之島');
  assert.equal(M.resolveAreaName({ x: 0, z: -22 }), '城市島');
  const bridge = L.BRIDGES[0];
  const midX = (bridge.ax + bridge.bx) * 0.5;
  const midZ = (bridge.az + bridge.bz) * 0.5;
  assert.equal(M.resolveAreaName({ x: midX, z: midZ }), '橋上');
  assert.equal(M.resolveAreaName({ x: 40, z: 40 }), '海域');

  const tcmRoom = M.interiorByRoomId('tcm-room');
  assert.equal(M.resolveAreaName({ interiorRoom: tcmRoom }), '推拿館');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('casino-room') }), '賭場');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('shop-room') }), '神之島');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('lab-room') }), '研發樓');
});

test('室內標記含推拿館經絡研習室門', () => {
  const room = M.interiorByRoomId('tcm-room');
  const markers = M.collectInteriorMarkers(room);
  const study = markers.find((mk) => mk.id === 'tcm-study');
  assert.ok(study, '應有 tcm-study 互動點');
  assert.equal(study.label, '進入經絡研習室');
  const iz = (room.interacts || []).find((row) => row.id === 'tcm-study');
  assert.equal(study.x, iz.x);
  assert.equal(study.z, iz.z);
});

test('室外標記從模組讀取（門、碼頭、粉紅車、施工牌）', () => {
  const doors = M.collectDoorMarkers();
  const tcmDoor = doors.find((d) => d.id === 'tcm-site');
  const board = doors.find((d) => d.id === 'construction-board');
  assert.ok(tcmDoor);
  assert.ok(board);
  const layoutBoard = L.BUILDINGS.find((b) => b.id === 'construction-board');
  assert.equal(board.x, layoutBoard.door.x);
  assert.equal(board.z, layoutBoard.door.z);

  const boats = M.collectBoatMarkers();
  assert.ok(boats.some((b) => b.id === 'main-berth'));
  assert.ok(boats.some((b) => b.id === 'city-board'));

  const car = M.collectCarMarker(null);
  const pink = C.pinkCarWorld();
  assert.equal(car.x, pink.x);
  assert.equal(car.z, pink.z);
});

test('shouldOpenMapWithKey：輸入框聚焦不攔截，探索中可按 M', () => {
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: true,
    key: 'M'
  }), false);
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: false,
    key: 'M',
    mapOpen: false,
    moreOpen: false
  }), true);
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: false,
    key: 'M',
    mapOpen: true
  }), false);
});