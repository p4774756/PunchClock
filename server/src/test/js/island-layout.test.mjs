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
  assert.equal(zones.length, 1);
  assert.equal(zones[0].id, 'casino');
  assert.equal(zones[0].label, '進入賭場');
  for (const id of ['tcm-site', 'computer-site']) {
    const site = L.buildingById(id);
    assert.equal(site.deriveBlock, true);
    assert.equal(site.entry, null);
    assert.ok(L.blocked(site.x, site.z, 0.28), id + ' 本體不可穿越');
  }
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
