// node --test server/src/test/js/island-city.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';
import { BUILDING_GLOW } from '../../main/resources/public/js/island-buildings-data.js';

const _models = path.join(
  path.dirname(fileURLToPath(import.meta.url)),
  '..', '..', 'main', 'resources', 'public', 'models'
);

const here = dirname(fileURLToPath(import.meta.url));
const citySceneJs = readFileSync(
  join(here, '..', '..', 'main', 'resources', 'public', 'js', 'island-city-scene.js'),
  'utf8'
);

const M = await loadPublicModule('island-city-materials.js');

const L = await loadPublicModule('island-layout.js');
const C = await loadPublicModule('island-city-data.js');
const B = await loadPublicModule('island-boat.js');
const CAM = await loadPublicModule('island-camera.js');

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

test('城市機車為 milu 看板而非方塊人', () => {
  assert.doesNotMatch(citySceneJs, /0x3a4a5a/);
  assert.doesNotMatch(citySceneJs, /0x5a7ec8/);
  assert.match(citySceneJs, /spawnCityScooterWalkers/);
});

test('applyCityMaterials 共用 glTF 材質時保留 map 到 traverse 結束', () => {
  const sharedMap = { image: { width: 4, height: 4 }, disposed: false };
  const disposed = [];
  class Color {
    constructor(v) { this._hex = v; }
    getHex() { return this._hex; }
  }
  class OldMat {
    constructor() {
      this.name = 'tex::roof_tile::None::None';
      this.color = new Color(0xffffff);
      this.map = sharedMap;
    }
    dispose() { disposed.push(this); }
  }
  class RuntimeMat {
    constructor(opts) {
      this.map = opts.map || null;
    }
  }
  const old = new OldMat();
  let meshCount = 0;
  const kit = {
    mat(color, extra) {
      return new RuntimeMat(extra || {});
    }
  };
  const model = {
    traverse(fn) {
      const a = { isMesh: true, name: 'walls', material: old, castShadow: false, receiveShadow: false, userData: {} };
      const b = { isMesh: true, name: 'sign_face_arcade_frame', material: old, castShadow: false, receiveShadow: false, userData: {} };
      fn(a);
      fn(b);
      meshCount = 2;
    }
  };
  M.applyCityMaterials(model, kit, {});
  assert.equal(meshCount, 2);
  assert.equal(disposed.length, 1, '共用舊材質只 dispose 一次');
  assert.equal(sharedMap.disposed, false);
  assert.equal(old.map, sharedMap, 'traverse 期間不得清空共用 map');
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    assert.ok(obj.material.map === sharedMap, obj.name + ' 應保留 atlas map');
  });
  assert.doesNotMatch(
    readFileSync(join(here, '..', '..', 'main', 'resources', 'public', 'js', 'island-city-materials.js'), 'utf8'),
    /oldMat\.map\s*=\s*null/
  );
});

test('city.glb 與 city-pink-car.glb 已建置', () => {
  const cityPath = path.join(_models, 'city.glb');
  const carPath = path.join(_models, 'city-pink-car.glb');
  assert.ok(fs.statSync(cityPath).size > 80_000);
  assert.ok(fs.statSync(carPath).size > 15_000);
  assert.ok(BUILDING_GLOW.city);
  assert.ok(BUILDING_GLOW.city.neon_glow);
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

test('粉紅車審查鏡頭：玩家路邊站位、3/4 前後鏡頭方位', () => {
  const car = C.pinkCarWorld();
  const expectedYaw = 0.2 - Math.PI / 2;
  assert.ok(Math.abs(car.yaw - expectedYaw) < 1e-4, '車頭 yaw 應朝西');
  const headX = Math.sin(car.yaw);
  const headZ = Math.cos(car.yaw);
  const cos20 = Math.cos((20 * Math.PI) / 180);
  const rects = C.cityObstacleRects();

  function cameraInBuilding(cx, cz) {
    for (const r of rects) {
      if (cx >= r.minX && cx <= r.maxX && cz >= r.minZ && cz <= r.maxZ) return true;
    }
    return false;
  }

  function cameraPlacement(pose, anchorX, anchorZ) {
    const dist = CAM.exploreFollowDistance(pose.radius, 1280 / 720);
    return CAM.exploreCameraPlacement(
      anchorX, pose.look[1], anchorZ,
      dist, pose.phi, pose.theta, 0, 0
    );
  }

  function cameraOffsetFromCar(pose) {
    const st = pose.player;
    const place = cameraPlacement(pose, st.x, st.z);
    const offX = place.cx - car.x;
    const offZ = place.cz - car.z;
    const len = Math.hypot(offX, offZ);
    const dot = offX * headX + offZ * headZ;
    return { place, offX, offZ, len, dot, dotNorm: len > 1e-6 ? dot / len : 0 };
  }

  const poses = C.pinkCarReviewPoses();
  assert.ok(poses['pink-car-look']);
  assert.ok(poses['pink-car-look-front']);
  for (const key of Object.keys(poses)) {
    const p = poses[key];
    const st = p.player;
    const dCenter = Math.hypot(st.x - car.x, st.z - car.z);
    assert.ok(dCenter >= 0.9, key + ' 站位應離車心 ≥0.9');
    assert.ok(dCenter <= 1.35, key + ' 站位應在上車圈內');
    assert.ok(Math.abs(st.x - car.x) > 0.12 || Math.abs(st.z - car.z) > 0.12,
      key + ' 不可與車心重疊');
    assert.equal(p.look[0], car.x);
    assert.equal(p.look[2], car.z);
    assert.ok(p.look[1] >= 0.42 && p.look[1] <= 0.58, key + ' look 高度應對準車身');
    assert.ok(p.phi >= 0.82 && p.phi <= 1.0, key + ' phi 應為 3/4 俯角');
    assert.ok(p.radius >= 4.8 && p.radius <= 5.4, key + ' radius 應拉近審查');
    const standInBld = cameraInBuilding(st.x, st.z);
    assert.ok(!standInBld, key + ' 玩家站位不在建築內');
    const docPlace = cameraPlacement(p, p.look[0], p.look[2]);
    assert.ok(!cameraInBuilding(docPlace.cx, docPlace.cz), key + ' 車心錨點鏡頭不在建築內');
  }

  const rear = cameraOffsetFromCar(poses['pink-car-look']);
  assert.ok(rear.dot < 0, '後 3/4 鏡頭應在車尾側（內積 < 0）');
  assert.ok(rear.dotNorm > -cos20, '後 3/4 應偏離車軸 ≥20°（非正後）');

  const front = cameraOffsetFromCar(poses['pink-car-look-front']);
  assert.ok(front.dot > 0, '前 3/4 鏡頭應在車頭側（內積 > 0）');
  assert.ok(front.dotNorm < cos20, '前 3/4 應偏離車軸 ≥20°（非正前）');

  const rearStand = C.pinkCarReviewStand(-1);
  assert.ok(Math.abs(rearStand.x - (car.x - 0.21)) < 0.02);
  assert.ok(Math.abs(rearStand.z - (car.z - 1.03)) < 0.02);
});