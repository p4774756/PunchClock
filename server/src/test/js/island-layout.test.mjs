// 群島清單推導：node --test server/src/test/js/island-layout.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-layout.js');

test('三座主題島連回主島，橋兩端都在島上', () => {
  assert.deepEqual(L.ISLANDS.map((island) => island.id), ['main', 'casino', 'tcm', 'computer']);
  assert.equal(L.BRIDGES.length, 3);
  for (const bridge of L.BRIDGES) {
    assert.equal(bridge.from, 'main');
    const from = L.islandById(bridge.from);
    const to = L.islandById(bridge.to);
    assert.ok(L.inIsland(bridge.ax, bridge.az, from, 0), bridge.id + ' 起點在主島');
    assert.ok(L.inIsland(bridge.bx, bridge.bz, to, 0), bridge.id + ' 終點在衛星島');
    assert.ok(bridge.halfWidth > 0.28, '橋面要比角色寬');
    const names = { casino: '博弈島', tcm: '中醫推拿島', computer: '電腦島' };
    assert.equal(to.name, names[to.id]);
  }
});

test('全景鏡頭罩得住每座島，太遠的島不會被算進去', () => {
  for (const island of L.ISLANDS) {
    assert.ok(L.islandInPanorama(island), island.id);
  }
  assert.equal(L.islandInPanorama({ x: 80, z: 0, visualR: 4 }), false);
});

test('建築碰撞與門口由清單推導，主島原有障礙順序不變', () => {
  const rects = L.blockRects();
  assert.equal(rects[0].id, 'shop');
  assert.equal(rects[6].id, 'stand');
  assert.equal(rects[7].id, 'photo-wall');
  const casino = L.buildingById('casino');
  assert.equal(casino.island, 'casino');
  assert.equal(casino.entry.action, 'enterCasino');
  assert.ok(casino.door);
  const cx = (casino.block.minX + casino.block.maxX) / 2;
  const cz = (casino.block.minZ + casino.block.maxZ) / 2;
  assert.ok(L.blocked(cx, cz, 0.28));
  assert.ok(!L.blocked(casino.door.x, casino.door.z, 0.28));
  assert.ok(L.walkable(casino.door.x, casino.door.z, 0.28));
  const zones = L.interactZones();
  const casinoZone = zones.find((zone) => zone.id === 'casino');
  assert.ok(casinoZone);
  assert.equal(casinoZone.label, '進入賭場');
  const wall = L.buildingById('graffiti-wall');
  assert.equal(wall.island, 'main');
  assert.equal(wall.label, '塗鴉牆');
  assert.equal(wall.deriveBlock, true);
  assert.equal(wall.entry.action, 'openGraffiti');
  assert.equal(wall.entry.label, '在牆上塗鴉');
  assert.ok(L.blocked(wall.x, wall.z, 0.29), '牆體不可穿越');
  assert.ok(L.walkable(wall.door.x, wall.door.z, 0.29), '牆前站得到');
  assert.ok(!L.blocked(wall.door.x, wall.door.z, 0.29));
  const wallZone = zones.find((zone) => zone.id === 'graffiti-wall');
  assert.ok(wallZone);
  assert.ok(Math.hypot(wall.door.x - wall.x, wall.door.z - wall.z) > wall.d / 2);
  const board = L.buildingById('construction-board');
  assert.equal(board.island, 'main');
  assert.equal(board.entry.action, 'openRoadmap');
  assert.equal(board.entry.label, '看施工進度');
  assert.ok(L.blocked(board.x, board.z, 0.29), '告示牌不可穿越');
  assert.ok(L.walkable(board.door.x, board.door.z, 0.29), '牌前站得到');
  assert.ok(!L.blocked(board.door.x, board.door.z, 0.29));
  const boardZone = zones.find((zone) => zone.id === 'construction-board');
  assert.ok(boardZone);
  const dock = L.WALK_PADS.find((pad) => pad.id === 'dock');
  assert.ok(board.block.maxX < dock.minX || board.block.minX > dock.maxX
    || board.block.maxZ < dock.minZ || board.block.minZ > dock.maxZ, '不壓碼頭');
  const computer = L.BRIDGES.find((bridge) => bridge.to === 'computer');
  assert.ok(!L.onBridge(board.x, board.z, computer, 0.29));
  assert.ok(!L.onBridge(board.door.x, board.door.z, computer, 0.29));
  for (const other of L.BUILDINGS) {
    if (other.id === board.id) continue;
    const overlap = board.block.minX < other.block.maxX && board.block.maxX > other.block.minX
      && board.block.minZ < other.block.maxZ && board.block.maxZ > other.block.minZ;
    assert.equal(overlap, false, '不和 ' + other.id + ' 重疊');
  }
  for (const zone of zones) {
    if (zone.id === boardZone.id) continue;
    const gap = Math.hypot(zone.x - boardZone.x, zone.z - boardZone.z);
    assert.ok(gap >= zone.r + boardZone.r, '互動區不和 ' + zone.id + ' 重疊');
  }
  const npc = [
    [-5.6, 4.7], [-1.8, 6.35], [2.1, 6.4], [5.6, 4.3],
    [6.05, 1.0], [5.3, -3.3], [1.1, -5.15], [-3.5, -5.05],
    [-6.15, -1.6], [-6.3, 2.1]
  ];
  function hitsBoard(x, z) {
    const rect = board.block;
    const dx = x - Math.max(rect.minX, Math.min(x, rect.maxX));
    const dz = z - Math.max(rect.minZ, Math.min(z, rect.maxZ));
    return dx * dx + dz * dz < 0.29 * 0.29;
  }
  for (let i = 0; i < npc.length; i++) {
    const from = npc[i];
    const to = npc[(i + 1) % npc.length];
    for (let step = 0; step <= 12; step++) {
      const t = step / 12;
      const x = from[0] + (to[0] - from[0]) * t;
      const z = from[1] + (to[1] - from[1]) * t;
      assert.equal(hitsBoard(x, z), false, 'NPC 路線被告示牌擋住');
    }
  }
  assert.equal(hitsBoard(2.15, 5.55), false, 'milu 出生點可站');
  for (const id of ['tcm-site', 'computer-site']) {
    const site = L.buildingById(id);
    assert.equal(site.deriveBlock, true);
    assert.equal(site.entry, null);
    assert.ok(L.blocked(site.x, site.z, 0.28), id + ' 本體不可穿越');
  }
});

// 不呼叫清單裡的 rotateY，改用 Three.js rotation.y 的公式獨立驗證。
function threeRotateY(x, z, yaw) {
  const c = Math.cos(yaw);
  const s = Math.sin(yaw);
  return { x: x * c + z * s, z: -x * s + z * c };
}

test('賭場門用 Three.js 的旋轉落在外觀正面，而且比建築中心更靠近橋頭', () => {
  const casino = L.buildingById('casino');
  const bridge = L.BRIDGES.find((item) => item.to === casino.island);
  const front = threeRotateY(0, 1, casino.yaw);
  const dx = casino.door.x - casino.x;
  const dz = casino.door.z - casino.z;
  const along = dx * front.x + dz * front.z;
  const lateral = dx * -front.z + dz * front.x;
  assert.ok(along > casino.d / 2, '門在外觀正面之外');
  assert.ok(Math.abs(lateral) < 1e-6, '門在正面中線');
  const doorDist = Math.hypot(casino.door.x - bridge.bx, casino.door.z - bridge.bz);
  const centerDist = Math.hypot(casino.x - bridge.bx, casino.z - bridge.bz);
  assert.ok(doorDist < centerDist, '門比建築中心更靠近橋頭');

  const site = L.buildingById('tcm-site');
  const tcmBridge = L.BRIDGES.find((item) => item.to === site.island);
  const sign = threeRotateY(0, site.d / 2, site.yaw);
  const signDist = Math.hypot(site.x + sign.x - tcmBridge.bx, site.z + sign.z - tcmBridge.bz);
  const siteDist = Math.hypot(site.x - tcmBridge.bx, site.z - tcmBridge.bz);
  assert.ok(signDist < siteDist, '中醫工地招牌比工地中心更靠近橋頭');
});

test('主島店面仍在原座標，賭場不在主島', () => {
  const shop = L.buildingById('shop');
  assert.equal(shop.x, 0);
  assert.equal(shop.z, -0.55);
  assert.equal(shop.deriveBlock, false);
  const casino = L.buildingById('casino');
  assert.ok(Math.hypot(casino.x, casino.z) > L.MAIN_ISLAND.r);
  assert.ok(L.walkable(3.55, -3.65, 0.28), '原賭場空地可走');
  assert.ok(!L.blocked(3.55, -3.65, 0.28));
});
