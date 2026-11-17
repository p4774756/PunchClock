import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const I = await loadPublicModule('island-interior.js');
const L = await loadPublicModule('island-layout.js');
const X = await loadPublicModule('island-explore.js');

const RADIUS = X.PLAYER_RADIUS;

function circleHitsRect(x, z, r, rect) {
  const dx = x - Math.max(rect.minX, Math.min(x, rect.maxX));
  const dz = z - Math.max(rect.minZ, Math.min(z, rect.maxZ));
  return dx * dx + dz * dz < r * r;
}

test('室內清單完整，手搖店西牆門口可站且不壓櫃台', () => {
  const room = I.interiorByBuilding('shop');
  assert.ok(room);
  assert.equal(room.id, 'shop-room');
  assert.ok(room.spawn && room.exit && room.outside && room.floor && room.blocks.length);
  assert.equal(I.interiorCanStand(room.spawn.x, room.spawn.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(room.exit.x, room.exit.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(room.blocks[0].minX + 0.05, room.blocks[0].minZ + 0.05, room, RADIUS), false);

  const shop = L.buildingById('shop');
  assert.equal(shop.entry.action, 'enterInterior');
  assert.equal(shop.entry.label, '進入莫卡手搖');
  assert.equal(shop.deriveBlock, false);
  assert.ok(shop.door.x < shop.x - shop.w / 2, '門口在西牆外，不是預設的正面 +Z');
  assert.equal(X.canStand(shop.door.x, shop.door.z, RADIUS), true);
  assert.equal(X.canStand(room.outside.x, room.outside.z, RADIUS), true);

  const counter = L.OBSTACLE_RECTS.find((rect) => rect.id === 'counter');
  assert.equal(circleHitsRect(shop.door.x, shop.door.z, shop.entry.r, counter), false);
  const shopBlock = L.OBSTACLE_RECTS.find((rect) => rect.id === 'shop');
  assert.equal(circleHitsRect(shop.door.x, shop.door.z, shop.entry.r, shopBlock), false);

  const gap = Math.hypot(room.outside.x - shop.door.x, room.outside.z - shop.door.z);
  assert.ok(gap > shop.entry.r, '離開後的站點不在進門互動區裡');

  assert.equal(L.isClosedHall(L.buildingById('tcm-site').entry.action), false);
  assert.equal(L.isClosedHall(L.buildingById('computer-site').entry.action), true);
  assert.equal(I.interiorByBuilding('tcm-site'), null);
  assert.equal(I.interiorByBuilding('computer-site'), null);
  assert.equal(L.buildingById('casino').entry.action, 'enterCasino');
});

test('進出狀態機：室內可移動、面板暫停、不可重入、離開後清空', () => {
  const session = I.createInteriorSession();
  const entered = session.enter('shop', 0);
  assert.equal(entered.ok, true);
  assert.equal(session.inside(), true);
  assert.equal(session.canMove(), true);
  assert.equal(session.id(), 'shop-room');
  assert.ok(session.blocks().length > 0);

  session.setPaused(true);
  assert.equal(session.inside(), true);
  assert.equal(session.canMove(), false);
  session.setPaused(false);
  assert.equal(session.canMove(), true);

  const again = session.enter('shop', 10);
  assert.equal(again.ok, false);
  assert.equal(again.reason, 'inside');
  assert.equal(session.id(), 'shop-room');

  const left = session.exit(1000);
  assert.equal(left.ok, true);
  assert.equal(session.inside(), false);
  assert.equal(session.id(), null);
  assert.equal(session.buildingId(), null);
  assert.equal(session.blocks(), null);
  assert.equal(session.canMove(), false);

  const cooled = session.enter('shop', 1000 + I.ENTER_COOLDOWN_MS - 1);
  assert.equal(cooled.ok, false);
  assert.equal(cooled.reason, 'cooldown');
  assert.equal(session.inside(), false);

  const later = session.enter('shop', 1000 + I.ENTER_COOLDOWN_MS);
  assert.equal(later.ok, true);
  assert.equal(session.id(), 'shop-room');
});

test('進入放到室內出生點，離開放到門前並面向離開建物', () => {
  const room = I.interiorByBuilding('shop');
  const shop = L.buildingById('shop');
  const session = I.createInteriorSession();
  const entered = session.enter('shop', 0);
  assert.deepEqual(entered.pose, {
    x: room.spawn.x,
    z: room.spawn.z,
    yaw: room.spawn.yaw
  });

  const left = session.exit(5000);
  assert.equal(left.pose.x, room.outside.x);
  assert.equal(left.pose.z, room.outside.z);
  assert.equal(left.pose.yaw, room.outside.yaw);
  assert.ok(left.pose.x < shop.door.x);
  const facingX = Math.sin(left.pose.yaw);
  assert.ok(facingX < 0, '朝西，背對店面');

  const pushed = I.stepInInterior(
    { x: room.floor.minX + RADIUS + 0.05, z: 0, yaw: 0 },
    { x: -1, z: 0, moving: true },
    0,
    1,
    room,
    RADIUS
  );
  assert.equal(I.interiorCanStand(pushed.x, pushed.z, room, RADIUS), true);
  assert.ok(pushed.x >= room.floor.minX + RADIUS - 0.001);
  assert.equal(L.blocked(room.spawn.x, room.spawn.z, RADIUS), true, '出生點在島上店裡，室內碰撞不能沿用島上的');
  assert.equal(I.interiorCanStand(room.spawn.x, room.spawn.z, room, RADIUS), true);
});

test('室內原點在鏡頭遠平面外，出生點與碰撞仍是本地座標', () => {
  const room = I.interiorByBuilding('shop');
  assert.deepEqual(room.spawn, { x: 0, z: 0.2, yaw: Math.PI });
  assert.equal(room.floor.minX, -2.35);
  assert.equal(room.blocks[0].minX, -1.25);
  assert.equal(I.interiorClearsFarPlane(I.INTERIOR_ORIGIN, I.ISLAND_CAMERA_FAR), true);
  assert.equal(I.interiorClearsFarPlane({ x: 0, z: -220 }, I.ISLAND_CAMERA_FAR), false);
  const worldZ = I.INTERIOR_ORIGIN.z + room.spawn.z;
  assert.ok(Math.abs(worldZ) > I.ISLAND_CAMERA_FAR);
});

test('島上標籤在室內或轉場中都要隱藏', () => {
  assert.equal(I.shouldHideIslandLabels(true, false), true);
  assert.equal(I.shouldHideIslandLabels(false, true), true);
  assert.equal(I.shouldHideIslandLabels(true, true), true);
  assert.equal(I.shouldHideIslandLabels(false, false), false);
});
