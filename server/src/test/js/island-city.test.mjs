// node --test server/src/test/js/island-city.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-layout.js');
const C = await loadPublicModule('island-city-data.js');
const B = await loadPublicModule('island-boat.js');

test('城市島與既有島／橋／主航線不相交', () => {
  const city = L.islandById('city');
  assert.ok(city);
  assert.equal(city.name, '城市島');
  assert.ok(L.islandInPanorama(city));
  for (const island of L.ISLANDS) {
    if (island.id === 'city') continue;
    const dist = Math.hypot(city.x - island.x, city.z - island.z);
    assert.ok(dist > city.visualR + island.visualR - 0.02, island.id + ' 外觀不重疊');
  }
  for (const bridge of L.BRIDGES) {
    const midX = (bridge.ax + bridge.bx) / 2;
    const midZ = (bridge.az + bridge.bz) / 2;
    const dist = Math.hypot(city.x - midX, city.z - midZ);
    assert.ok(dist > city.r + bridge.halfWidth, bridge.id + ' 不碰城市島');
  }
  assert.ok(B.routeInOpenWater(L.BOAT_ROUTE, 0.29, 48));
  assert.ok(B.routeInOpenWater(L.BOAT_ROUTE_MAIN_CITY, 0.29, 48));
  for (let i = 0; i <= 24; i++) {
    const u = i / 24;
    const p = B.sampleRoute(u, L.BOAT_ROUTE_MAIN_CITY);
    assert.ok(!L.inIsland(p.x, p.z, city, 0.2), '城市航線不進島心 u=' + u);
  }
});

test('SEA_NAV_BOUNDS 含城市碼頭；BOAT_STOPS.city 上下船點', () => {
  const berth = L.BOAT_STOPS.city.berth;
  const board = L.BOAT_STOPS.city.board;
  const land = L.BOAT_STOPS.city.land;
  const b = L.SEA_NAV_BOUNDS;
  assert.ok(berth.x >= b.minX && berth.x <= b.maxX && berth.z >= b.minZ && berth.z <= b.maxZ);
  assert.ok(B.boatNavigable(berth.x, berth.z), '泊位在水域');
  assert.ok(!B.boatNavigable(land.x, land.z, 0.2), 'land 不在船可航行點');
  assert.ok(L.walkable(board.x, board.z, 0.29), 'board 在棧橋');
  assert.ok(L.walkable(land.x, land.z, 0.29), 'land 可站');
  assert.ok(!L.blocked(board.x, board.z, 0.29));
  const jetty = L.WALK_PADS.find((pad) => pad.id === 'city-jetty');
  assert.ok(jetty);
  assert.ok(L.inPad(land.x, land.z, jetty, 0.29));
});

test('disembarkNear 城市泊位相容', () => {
  const berth = L.BOAT_STOPS.city.berth;
  assert.equal(B.disembarkNear(berth.x, berth.z), 'city');
  assert.equal(B.disembarkNear(berth.x + 0.55, berth.z + 0.2), 'city');
  assert.equal(B.disembarkNear(L.BOAT_STOPS.main.berth.x, L.BOAT_STOPS.main.berth.z), 'main');
  assert.equal(B.disembarkNear(12.85, 3.75), null);
});

test('紅綠燈週期決定性', () => {
  assert.equal(C.trafficLightColor(0), 'red');
  const mid = C.TRAFFIC_RED_SEC + C.TRAFFIC_YELLOW_SEC + 1;
  assert.equal(C.trafficLightColor(mid), 'green');
  const cycle = C.TRAFFIC_RED_SEC + C.TRAFFIC_YELLOW_SEC + C.TRAFFIC_GREEN_SEC
    + C.TRAFFIC_GREEN_SEC + C.TRAFFIC_YELLOW_SEC;
  assert.equal(C.trafficLightColor(42.5), C.trafficLightColor(42.5 + cycle));
});

test('城市碰撞由資料驅動', () => {
  const rects = C.cityObstacleRects();
  assert.ok(rects.length >= 6);
  const cx = (rects[0].minX + rects[0].maxX) / 2;
  const cz = (rects[0].minZ + rects[0].maxZ) / 2;
  assert.ok(L.blocked(cx, cz, 0.29));
});

test('路人與機車路徑不穿牆、留在城市島', () => {
  const island = L.islandById('city');
  const walkerPaths = C.cityWalkerPaths();
  const motoPaths = C.cityMotoPaths();
  assert.ok(walkerPaths.length >= 4 && walkerPaths.length <= 8);
  assert.ok(motoPaths.length >= 2 && motoPaths.length <= 3);
  const samples = C.sampleCityPaths(walkerPaths.concat(motoPaths), 12);
  assert.ok(samples.length > 40);
  const car = C.pinkCarWorld();
  const carClear = C.PINK_CAR_PATH_CLEAR_R;
  for (let i = 0; i < samples.length; i++) {
    const x = samples[i][0];
    const z = samples[i][1];
    assert.ok(L.inIsland(x, z, island, 0.2), '樣點 ' + i + ' 應在城市島內');
    assert.ok(L.walkable(x, z, 0.16), '樣點 ' + i + ' 應可站立');
    assert.ok(!L.blocked(x, z, 0.16), '樣點 ' + i + ' 不應在障礙內');
    assert.ok(Math.hypot(x - car.x, z - car.z) >= carClear,
      '樣點 ' + i + ' 應遠離粉紅小車');
  }
});