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
  assert.equal(shop.entry.label, '進入神之島');
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
  assert.equal(L.buildingById('casino').entry.action, 'enterInterior');
  assert.equal(I.interiorByBuilding('tcm-site'), null);
});

test('研發樓大廳可走至機房與文物廳', () => {
  const room = I.interiorByBuilding('computer-site');
  assert.ok(room.floor.maxX - room.floor.minX > 14, '室內寬度遠大於舊盒子');
  assert.ok(room.floor.maxZ - room.floor.minZ > 12, '室內深度遠大於舊盒子');
  const samples = I.labInteriorPlayerSamplePositions(room);
  const lobby = samples.find((s) => s.id === 'lobby');
  const server = samples.find((s) => s.id === 'server');
  const museum = samples.find((s) => s.id === 'museum');
  assert.ok(lobby && server && museum);
  assert.equal(I.interiorCanStand(lobby.x, lobby.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(server.x, server.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(museum.x, museum.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(3.8, 5.2, room, RADIUS), true, '機房走道');
  assert.equal(I.interiorCanStand(-4.2, 8.6, room, RADIUS), true, '文物廳深處');
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
  assert.deepEqual(I.LAB_INTERIOR_CAMERA, { radius: 5.8, theta: 0.04, phi: 1.12 });
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
  const aspect = 1440 / 900;
  const pose = I.labInteriorCameraForAspect(aspect);
  const look = I.labInteriorLookTarget(room, { x: room.spawn.x, z: room.spawn.z }, aspect);
  const eye = I.labInteriorCameraEye(pose, look, aspect);
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
    const look = I.labInteriorLookTarget(room, { x: room.spawn.x, z: room.spawn.z }, vp.aspect);
    const frameTopY = 0.2 + 0.86 + 0.64 + 0.12;
    const frameTop = I.labInteriorProjectNdc(-0.675 - 0.12, frameTopY, -1.74, pose, look, vp.aspect);
    if (vp.aspect >= 0.72) {
      assert.ok(frameTop.y <= topLimit + 0.02, '螢幕外框上緣 NDC y 不得貼齊工具列');
    } else {
      assert.ok(frameTop.y <= topLimit + 0.02, '螢幕上緣 NDC y 不得貼齊工具列');
    }
    assert.ok(frameTop.y <= 0.85, '螢幕外框上緣 NDC y ≤ 0.85');
  }
});

test('研發樓桌面注視：南牆焦點與胸口混合，手機維持純焦點', () => {
  const room = I.interiorByBuilding('computer-site');
  const spawn = room.spawn;
  const player = { x: spawn.x, z: spawn.z };
  const desktopAspect = 1440 / 900;
  const mobileAspect = 390 / 844;
  const desktopLook = I.labInteriorLookTarget(room, player, desktopAspect);
  const pureFocal = I.LAB_INTERIOR_LOOK_DESKTOP;
  const pureChest = {
    x: spawn.x,
    y: I.LAB_INTERIOR_PLAYER_CHEST_Y,
    z: spawn.z
  };
  assert.notDeepEqual(desktopLook, pureFocal);
  assert.notDeepEqual(desktopLook, pureChest);
  assert.ok(
    Math.abs(desktopLook.x - pureFocal.x) > 0.01 && Math.abs(desktopLook.x - pureChest.x) > 0.01,
    '桌面 x 應在焦點與胸口之間'
  );
  assert.ok(
    I.labInteriorChestBlend(room, { x: room.exit.x, z: room.exit.z }) <= I.LAB_INTERIOR_LOOK_CHEST_BLEND + 0.02,
    '門口混合約 0.45'
  );
  assert.ok(
    I.labInteriorChestBlend(room, player) > I.LAB_INTERIOR_LOOK_CHEST_BLEND + 0.2,
    '出生點更靠胸口以露出全身'
  );
  assert.deepEqual(I.labInteriorLookTarget(room, player, mobileAspect), I.LAB_INTERIOR_LOOK_MOBILE);

  const height = 900;
  const pose = I.labInteriorCameraForAspect(desktopAspect);
  const frameTopY = 0.2 + 0.86 + 0.64 + 0.12;
  const lim = I.labInteriorScreenTopNdcLimit(height);
  const o = I.INTERIOR_ORIGIN;
  const worldPlayer = { x: player.x + o.x, z: player.z + o.z };
  const worldLook = I.labInteriorRuntimeLookAt(room, worldPlayer, desktopAspect);
  const localLook = {
    x: worldLook.x - o.x,
    y: worldLook.y,
    z: worldLook.z - o.z
  };
  const frameTop = I.labInteriorProjectNdc(
    -0.675 - 0.12,
    frameTopY,
    -1.74,
    pose,
    localLook,
    desktopAspect
  );
  const pxFromTop = (ndcY) => ((1 - ndcY) / 2) * height;
  assert.ok(pxFromTop(frameTop.y) >= 80, '混合注視時螢幕外框上緣 ≥80px');
  assert.ok(frameTop.y <= lim + 0.03, '螢幕外框不應貼齊工具列');
});

test('研發樓桌面世界座標：注視與鏡頭仍在室內附近', () => {
  const room = I.interiorByBuilding('computer-site');
  const o = I.INTERIOR_ORIGIN;
  const focalZ = I.LAB_INTERIOR_LOOK_DESKTOP.z;
  const aspects = [1440 / 900, 1280 / 720];
  const samples = I.labInteriorPlayerSamplePositions(room);
  const nearRoom = (x, z) => Math.hypot(x - o.x, z - o.z) < 15;
  const betweenPlayerAndSouth = (playerZ, lookZ) => {
    const lo = Math.min(playerZ, focalZ);
    const hi = Math.max(playerZ, focalZ);
    return lookZ >= lo - 0.01 && lookZ <= hi + 0.01;
  };
  for (let a = 0; a < aspects.length; a++) {
    const aspect = aspects[a];
    const pose = I.labInteriorCameraForAspect(aspect);
    for (let i = 0; i < samples.length; i++) {
      const s = samples[i];
      const worldPlayer = { x: s.x + o.x, z: s.z + o.z };
      const worldLook = I.labInteriorRuntimeLookAt(room, worldPlayer, aspect);
      assert.ok(worldLook, 'worldLook');
      assert.ok(
        nearRoom(worldLook.x, worldLook.z),
        `注視點距 INTERIOR_ORIGIN < 15：${s.id} aspect=${aspect.toFixed(3)}`
      );
      const localLook = {
        x: worldLook.x - o.x,
        y: worldLook.y,
        z: worldLook.z - o.z
      };
      assert.ok(
        betweenPlayerAndSouth(s.z, localLook.z),
        `注視點應在玩家與南牆之間：${s.id} z=${localLook.z.toFixed(3)}`
      );
      const eye = I.labInteriorCameraEye(pose, localLook, aspect);
      assert.ok(
        Math.hypot(eye.x, eye.z) < 15,
        `鏡頭距室內原點 < 15：${s.id} aspect=${aspect.toFixed(3)}`
      );
    }
  }
});

test('研發樓各站位頭腳投影在 NDC 內（桌面 1280／1440）', () => {
  const room = I.interiorByBuilding('computer-site');
  const aspects = [1440 / 900, 1280 / 720];
  const samples = I.labInteriorPlayerSamplePositions(room);
  const inside = (ndc) => ndc.vz > 0
    && ndc.y >= -0.94 && ndc.y <= 0.98
    && ndc.x >= -0.94 && ndc.x <= 0.94;
  for (let a = 0; a < aspects.length; a++) {
    const aspect = aspects[a];
    const pose = I.labInteriorCameraForAspect(aspect);
    for (let i = 0; i < samples.length; i++) {
      const s = samples[i];
      const look = I.labInteriorLookTarget(room, { x: s.x, z: s.z }, aspect);
      const head = I.labInteriorProjectNdc(
        s.x, I.LAB_INTERIOR_PLAYER_HEAD_Y, s.z, pose, look, aspect
      );
      const feet = I.labInteriorProjectNdc(
        s.x, I.LAB_INTERIOR_PLAYER_FEET_Y, s.z, pose, look, aspect
      );
      assert.ok(inside(head), `頭頂 NDC：${s.id} aspect=${aspect.toFixed(3)} y=${head.y.toFixed(3)}`);
      assert.ok(inside(feet), `腳底 NDC：${s.id} aspect=${aspect.toFixed(3)} y=${feet.y.toFixed(3)}`);
    }
  }
});

test('手搖店室內離開按鈕文案與探索提示', () => {
  const session = I.createInteriorSession();
  session.enter('shop', 0);
  assert.equal(I.interiorLeaveButtonLabel(session), '離開神之島');
  session.exit(200);
});

test('賭場室內資料完整，門口外可站且不壓進門區', () => {
  const room = I.interiorByBuilding('casino');
  assert.ok(room);
  assert.equal(room.id, 'casino-room');
  assert.ok(room.spawn && room.exit && room.outside && room.floor && room.blocks.length);
  assert.equal(I.interiorCanStand(room.spawn.x, room.spawn.z, room, RADIUS), true);
  assert.equal(I.interiorCanStand(room.exit.x, room.exit.z, room, RADIUS), true);

  const hall = L.buildingById('casino');
  assert.equal(hall.entry.action, 'enterInterior');
  assert.equal(hall.entry.label, '進入賭場');
  assert.equal(X.canStand(hall.door.x, hall.door.z, RADIUS), true);
  assert.equal(X.canStand(room.outside.x, room.outside.z, RADIUS), true);
  assert.equal(L.blocked(room.outside.x, room.outside.z, RADIUS), false);

  const gap = Math.hypot(room.outside.x - hall.door.x, room.outside.z - hall.door.z);
  assert.ok(gap > hall.entry.r, '離開後的站點不在進門互動區裡');

  const interacts = room.interacts || [];
  assert.ok(interacts.length >= 3);
  for (let i = 0; i < interacts.length; i++) {
    assert.equal(I.interiorCanStand(interacts[i].x, interacts[i].z, room, RADIUS), true,
      interacts[i].id + ' 互動點可站');
  }
});

test('賭場進出狀態機：開牌桌暫停、離開後 cooldown', () => {
  const session = I.createInteriorSession();
  const entered = session.enter('casino', 0);
  assert.equal(entered.ok, true);
  assert.equal(session.inside(), true);
  assert.equal(session.canMove(), true);
  assert.equal(session.id(), 'casino-room');

  session.setPaused(true);
  assert.equal(session.canMove(), false);
  session.setPaused(false);
  assert.equal(session.canMove(), true);

  const left = session.exit(500);
  assert.equal(left.ok, true);
  assert.equal(session.inside(), false);

  const early = session.enter('casino', 500 + I.ENTER_COOLDOWN_MS - 1);
  assert.equal(early.ok, false);
  assert.equal(early.reason, 'cooldown');
});

test('賭場室內互動區不重疊且含出口', () => {
  const room = I.interiorByBuilding('casino');
  const session = I.createInteriorSession();
  session.enter('casino', 0);
  const zones = session.interactZones();
  assert.ok(zones.length >= 4);
  const exit = zones.find((z) => z.action === 'exitInterior');
  assert.ok(exit);
  assert.equal(exit.label, '離開賭場');

  const world = I.interiorPromptZones(room);
  const bj = zones.find((z) => z.action === 'openCasinoBlackjack');
  const slots = zones.find((z) => z.action === 'casinoSlots');
  const roulette = zones.find((z) => z.action === 'casinoRoulette');
  assert.ok(bj && slots && roulette);

  for (let i = 0; i < zones.length; i++) {
    for (let j = i + 1; j < zones.length; j++) {
      const dist = Math.hypot(zones[i].x - zones[j].x, zones[i].z - zones[j].z);
      assert.ok(dist > zones[i].r + zones[j].r - 0.05,
        zones[i].id + ' 與 ' + zones[j].id + ' 互動區不重疊');
    }
  }
  assert.ok(world.length === zones.length);
  session.exit(1000);
});

test('賭場 21 點互動區在牌桌西側走道，進門不觸發', () => {
  const room = I.interiorByBuilding('casino');
  const bjInteract = (room.interacts || []).find((z) => z.id === 'casino-bj');
  assert.ok(bjInteract);
  const table = room.blocks.find((b) => b.id === 'bj-table');
  assert.ok(table);

  assert.ok(bjInteract.x < table.minX - 0.15,
    '互動圓心在桌子西側，不靠桌緣內側');
  assert.ok(bjInteract.x > table.minX - 0.65,
    '互動圓心仍靠近牌桌走道');
  assert.ok(bjInteract.z >= table.minZ && bjInteract.z <= table.maxZ,
    '互動圓心與牌桌 Z 範圍對齊');

  for (let bi = 0; bi < room.blocks.length; bi++) {
    assert.equal(circleHitsRect(bjInteract.x, bjInteract.z, 0.02, room.blocks[bi]), false,
      'casino-bj 圓心不在 ' + room.blocks[bi].id);
  }
  assert.equal(I.interiorCanStand(bjInteract.x, bjInteract.z, room, RADIUS), true);

  const spawnDist = Math.hypot(room.spawn.x - bjInteract.x, room.spawn.z - bjInteract.z);
  assert.ok(spawnDist > bjInteract.r,
    '出生點不在「玩 21 點」半徑內');

  const session = I.createInteriorSession();
  session.enter('casino', 0);
  const zones = session.interactZones();
  assert.equal(X.hitInteractZone(room.spawn.x, room.spawn.z, zones), null,
    '門口不應命中 21 點互動區');
  const bjZone = zones.find((z) => z.action === 'openCasinoBlackjack');
  assert.ok(X.hitInteractZone(bjInteract.x, bjInteract.z, zones));
  assert.equal(X.hitInteractZone(bjInteract.x, bjInteract.z, zones).action, 'openCasinoBlackjack');
  session.exit(1000);
});
