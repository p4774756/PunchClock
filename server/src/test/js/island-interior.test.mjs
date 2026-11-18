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
  assert.equal(L.buildingById('casino').entry.action, 'enterCasino');
  assert.equal(I.interiorByBuilding('tcm-site'), null);
});

test('研發樓室內完整，門口外可站且不壓進門互動區', () => {
  const room = I.interiorByBuilding('computer-site');
  assert.ok(room);
  assert.equal(room.id, 'lab-room');
  assert.ok(room.spawn && room.exit && room.outside && room.floor && room.blocks.length);
  assert.equal(I.interiorCanStand(room.spawn.x, room.spawn.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(room.exit.x, room.exit.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(room.blocks[0].minX + 0.05, room.blocks[0].minZ + 0.05, room, RADIUS), false);

  const hall = L.buildingById('computer-site');
  assert.equal(hall.entry.action, 'enterInterior');
  assert.equal(hall.entry.label, '進入研發樓');
  assert.equal(L.isClosedHall(hall.entry.action), false);
  assert.equal(X.canStand(hall.door.x, hall.door.z, RADIUS), true);
  assert.equal(X.canStand(room.outside.x, room.outside.z, RADIUS), true);

  const gap = Math.hypot(room.outside.x - hall.door.x, room.outside.z - hall.door.z);
  assert.ok(gap > hall.entry.r, '離開後的站點不在進門互動區裡');
  assert.equal(L.blocked(room.outside.x, room.outside.z, RADIUS), false);

  assert.ok(room.exit.x < -1.2, '出口在南牆門洞左側');
  assert.ok(Math.hypot(room.exit.x - room.spawn.x, room.exit.z - room.spawn.z) > room.exit.r,
    '出口離出生點有距離');
  for (let bi = 0; bi < room.blocks.length; bi++) {
    assert.equal(circleHitsRect(room.exit.x, room.exit.z, room.exit.r, room.blocks[bi]), false);
  }
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

test('研發樓進出狀態機與手搖店相同規則', () => {
  const session = I.createInteriorSession();
  const entered = session.enter('computer-site', 0);
  assert.equal(entered.ok, true);
  assert.equal(session.id(), 'lab-room');
  assert.equal(session.buildingId(), 'computer-site');
  const left = session.exit(800);
  assert.equal(left.ok, true);
  assert.equal(left.pose.x, I.interiorByBuilding('computer-site').outside.x);
  assert.equal(session.inside(), false);
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

test('研發樓出生朝向門口，有專用鏡頭與離開按鈕條件', () => {
  const room = I.interiorByBuilding('computer-site');
  assert.equal(room.spawn.yaw, Math.PI);
  assert.ok(room.spawn.z > -0.2, '出生點略靠房內北側，鏡頭看得到南牆門');
  assert.deepEqual(I.LAB_INTERIOR_CAMERA, { radius: 3.65, theta: 0.02, phi: 1.11 });
  assert.deepEqual(I.interiorFollowPose(room, 1.6), I.LAB_INTERIOR_CAMERA);
  assert.deepEqual(I.interiorFollowPose(room, 390 / 844), I.LAB_INTERIOR_CAMERA_MOBILE);
  assert.equal(I.interiorFollowPose(I.interiorByBuilding('shop')), null);

  const session = I.createInteriorSession();
  session.enter('computer-site', 0);
  assert.equal(I.interiorLeaveButtonLabel(session), '離開研發樓');
  assert.equal(I.shouldShowInteriorLeave({ inside: true, exploring: true, busy: false, overlay: false }), true);
  assert.equal(I.shouldShowInteriorLeave({ inside: true, exploring: true, busy: true, overlay: false }), false);
  assert.equal(I.shouldShowInteriorLeave({ inside: false, exploring: true, busy: false, overlay: false }), false);
  assert.equal(I.shouldShowInteriorLeave({ inside: true, exploring: true, busy: false, overlay: true }), false);
  assert.ok(I.exploreHintText(true, true).includes('離開'));
  assert.ok(I.exploreHintText(true, false).includes('Esc'));
  session.exit(500);
  assert.equal(I.interiorLeaveButtonLabel(session), '');
});

test('研發樓牆面 AABB 與視線遮擋：北牆剖面、門洞與室內站位', () => {
  const room = I.interiorByBuilding('computer-site');
  const floor = room.floor;
  const walls = I.labInteriorVisibleWallAABBs(floor);
  assert.ok(walls.length >= 5);
  const northFull = walls.some(
    (w) => w.maxY >= 1.7 && w.minZ >= floor.maxZ && (w.maxX - w.minX) > 2.5
  );
  assert.equal(northFull, false, '北牆整面不應列入可見遮擋');
  const pose = I.labInteriorCameraForAspect(1440 / 900);
  const look = I.LAB_INTERIOR_LOOK_DESKTOP;
  const eye = I.labInteriorCameraEye(pose, look, 1440 / 900);
  assert.equal(I.labInteriorCameraInsideFloor(eye, floor, 0.15), false);
  const side = I.labInteriorSideWallAABBs(floor);
  assert.ok(
    I.labInteriorSightClear(eye, { x: 0, y: I.LAB_INTERIOR_PLAYER_CHEST_Y, z: -1.74 }, side),
    '鏡頭到螢幕不穿過東西牆'
  );
  const samples = I.labInteriorPlayerSamplePositions(room);
  const southBlocks = walls.slice(3);
  for (let i = 0; i < samples.length; i++) {
    const s = samples[i];
    assert.equal(
      I.interiorCanStand(s.x, s.z, room, RADIUS),
      true,
      '採樣站位可站：' + s.id
    );
    const target = { x: s.x, y: I.LAB_INTERIOR_PLAYER_CHEST_Y, z: s.z };
    assert.ok(
      I.labInteriorSightClear(eye, target, side.concat(southBlocks)),
      '站位視線不被牆擋：' + s.id
    );
  }
  assert.equal(
    I.segmentIntersectsAabb(0, 1, 0, 2, 1, 0, { minX: 0.5, maxX: 1.5, minY: 0, maxY: 2, minZ: -0.1, maxZ: 0.1 }),
    true
  );
  assert.equal(
    I.segmentIntersectsAabb(0, 1, -2, 0, 1, 2, { minX: -1, maxX: 1, minY: 0, maxY: 2, minZ: 0.9, maxZ: 1.1 }),
    true
  );
  assert.equal(
    I.segmentIntersectsAabb(0, 1, -2, 0, 1, 0.5, { minX: -1, maxX: 1, minY: 0, maxY: 2, minZ: 0.9, maxZ: 1.1 }),
    false
  );
});

test('研發樓預設鏡頭：三種 viewport 螢幕與門口投影在 NDC 內', () => {
  const room = I.interiorByBuilding('computer-site');
  const viewports = [
    { aspect: 1440 / 900, height: 900 },
    { aspect: 1280 / 720, height: 720 },
    { aspect: 390 / 844, height: 844 }
  ];
  for (let i = 0; i < viewports.length; i++) {
    const vp = viewports[i];
    assert.equal(
      I.labInteriorFramingOk(vp.aspect, vp.height, room),
      true,
      'viewport ' + vp.aspect.toFixed(3)
    );
    const topLimit = I.labInteriorScreenTopNdcLimit(vp.height);
    const pose = I.labInteriorCameraForAspect(vp.aspect);
    const look = vp.aspect < 0.72 ? I.LAB_INTERIOR_LOOK_MOBILE : I.LAB_INTERIOR_LOOK_DESKTOP;
    const top = I.labInteriorProjectNdc(-0.795, 1.6, -1.74, pose, look, vp.aspect);
    assert.ok(top.y <= topLimit + 0.02, '螢幕上緣 NDC y 不得貼齊工具列');
    assert.ok(top.y <= 0.85, '螢幕上緣 NDC y ≤ 0.85');
  }
});

test('手搖店室內離開按鈕文案與探索提示', () => {
  const session = I.createInteriorSession();
  session.enter('shop', 0);
  assert.equal(I.interiorLeaveButtonLabel(session), '離開莫卡手搖');
  session.exit(200);
});
