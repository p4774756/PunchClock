// 探索模式純邏輯測試：node --test server/src/test/js/island-explore.test.mjs
// 由 IslandExploreLogicTest 在 mvn test 時執行。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const modulePath = join(here, '..', '..', 'main', 'resources', 'public', 'js', 'island-explore.js');
// 以 data URL 載入，避免依賴 package.json "type" 或 Node 的模組格式偵測。
const source = readFileSync(modulePath, 'utf8');
const X = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

const R = X.PLAYER_RADIUS;
const EPS = 1e-9;

function rawWalkable(x, z) {
  return X.isWalkable(x, z, 0);
}

test('出生點可站立且不在障礙物內', () => {
  assert.ok(X.canStand(X.PLAYER_SPAWN.x, X.PLAYER_SPAWN.z));
  assert.ok(X.isWalkable(X.PLAYER_SPAWN.x, X.PLAYER_SPAWN.z));
  assert.ok(!X.isBlocked(X.PLAYER_SPAWN.x, X.PLAYER_SPAWN.z));
});

test('島嶼邊界依角色半徑內縮', () => {
  const edge = X.WALK_ISLAND_R;
  assert.ok(rawWalkable(edge - 0.1, 0), '中心點仍在島上');
  assert.ok(!X.isWalkable(edge - 0.1, 0), '但身體會伸進水裡');
  assert.ok(X.isWalkable(edge - R - 0.01, 0));
  assert.ok(!X.isWalkable(edge - R + 0.01, 0));
});

test('碼頭矩形同樣依半徑內縮', () => {
  const dock = X.WALK_RECTS[0];
  assert.ok(rawWalkable(dock.maxX - 0.05, 8.5));
  assert.ok(!X.isWalkable(dock.maxX - 0.05, 8.5));
  assert.ok(X.isWalkable(0, dock.maxZ - R - 0.01));
  assert.ok(!X.isWalkable(0, dock.maxZ - R + 0.01));

  const pier = X.WALK_RECTS[1];
  assert.ok(!X.isWalkable(pier.maxX - 0.1, 2));
  assert.ok(X.isWalkable(pier.maxX - R - 0.01, 2));
});

test('碼頭與島嶼內縮後仍然相連', () => {
  for (let z = 6; z <= X.WALK_RECTS[0].maxZ - R; z += 0.02) {
    assert.ok(X.isWalkable(0, z), 'dock z=' + z.toFixed(2));
  }
  for (let x = 6; x <= X.WALK_RECTS[1].maxX - R; x += 0.02) {
    assert.ok(X.isWalkable(x, 2), 'pier x=' + x.toFixed(2));
  }
});

test('任何可行走位置，整個角色圓都在原始陸地內', () => {
  let checked = 0;
  for (let x = -9; x <= 10; x += 0.1) {
    for (let z = -9; z <= 9.5; z += 0.1) {
      if (!X.isWalkable(x, z)) continue;
      checked++;
      for (let i = 0; i < 24; i++) {
        const a = (i / 24) * Math.PI * 2;
        const px = x + Math.cos(a) * (R - EPS);
        const pz = z + Math.sin(a) * (R - EPS);
        assert.ok(rawWalkable(px, pz), `(${x.toFixed(2)}, ${z.toFixed(2)}) 邊緣 (${px.toFixed(3)}, ${pz.toFixed(3)}) 在水裡`);
      }
    }
  }
  assert.ok(checked > 1000);
});

test('矩形障礙物用真實距離，轉角不會過度阻擋', () => {
  const r = X.BLOCK_RECTS[6];
  const d = (R + 0.02) / Math.SQRT2;
  assert.ok(!X.isBlocked(r.maxX + d, r.maxZ + d), '對角線外剛好不碰到');
  assert.ok(X.isBlocked((r.minX + r.maxX) / 2, r.maxZ + R - 0.02), '邊緣內側要擋住');
  assert.ok(X.isBlocked((r.minX + r.maxX) / 2, (r.minZ + r.maxZ) / 2), '矩形內部要擋住');
});

test('圓形障礙物考慮角色半徑', () => {
  const c = X.BLOCK_CIRCLES[0];
  assert.ok(X.isBlocked(c.x - c.r - R + 0.01, c.z));
  assert.ok(!X.isBlocked(c.x - c.r - R - 0.01, c.z));
});

test('resolveMove：撞牆沿軸滑動，完全卡住則不動', () => {
  const s = X.PLAYER_SPAWN;
  const slid = X.resolveMove(s.x, s.z, s.x + 0.1, s.z - 0.15);
  assert.equal(slid.x, s.x + 0.1);
  assert.equal(slid.z, s.z);

  const free = X.resolveMove(s.x, s.z, s.x - 0.1, s.z + 0.1);
  assert.deepEqual(free, { x: s.x - 0.1, z: s.z + 0.1 });

  const edge = X.WALK_ISLAND_R - R - 0.001;
  const stuck = X.resolveMove(edge, 0, edge + 0.5, 0);
  assert.deepEqual(stuck, { x: edge, z: 0 });
});

test('一直往外走也不會走出可行走範圍', () => {
  for (const theta of [0, 0.35, Math.PI / 2, Math.PI, -2.2]) {
    for (const input of [X.moveVector({ KeyW: true }), X.moveVector({ KeyD: true }), X.moveVector({ KeyS: true, KeyA: true })]) {
      let pos = { x: X.PLAYER_SPAWN.x, z: X.PLAYER_SPAWN.z, yaw: 0 };
      for (let i = 0; i < 600; i++) {
        pos = X.stepPlayer(pos, input, theta, 1 / 60);
        assert.ok(X.canStand(pos.x, pos.z), `theta=${theta} step=${i} (${pos.x}, ${pos.z})`);
      }
    }
  }
});

test('moveVector：鍵盤與搖桿輸入正規化', () => {
  assert.deepEqual(X.moveVector({}, null), { x: 0, z: 0, moving: false });
  assert.deepEqual(X.moveVector({ KeyW: true }), { x: 0, z: -1, moving: true });
  assert.deepEqual(X.moveVector({ ArrowRight: true }), { x: 1, z: 0, moving: true });
  assert.equal(X.moveVector({ KeyW: true, KeyS: true }).moving, false);

  const diag = X.moveVector({ KeyW: true, KeyD: true });
  assert.ok(Math.abs(Math.hypot(diag.x, diag.z) - 1) < EPS);
  assert.ok(diag.x > 0 && diag.z < 0);

  assert.equal(X.moveVector({}, { active: true, x: 0.05, y: 0.02 }).moving, false, '死區');
  assert.equal(X.moveVector({}, { active: false, x: 1, y: 0 }).moving, false);
  const stick = X.moveVector({}, { active: true, x: 0.3, y: 0.4 });
  assert.ok(Math.abs(stick.x - 0.6) < EPS && Math.abs(stick.z - 0.8) < EPS);
});

test('cameraRelative：依鏡頭方向旋轉', () => {
  const fwd = { x: 0, z: -1 };
  const a = X.cameraRelative(fwd, 0);
  assert.ok(Math.abs(a.x) < EPS && Math.abs(a.z + 1) < EPS);
  const b = X.cameraRelative(fwd, Math.PI / 2);
  assert.ok(Math.abs(b.x + 1) < EPS && Math.abs(b.z) < EPS);
  const c = X.cameraRelative({ x: 1, z: 0 }, Math.PI);
  assert.ok(Math.abs(c.x + 1) < EPS && Math.abs(c.z) < EPS);
});

test('stepPlayer：單步 dt 有上限、靜止時不動', () => {
  const pos = { x: X.PLAYER_SPAWN.x, z: X.PLAYER_SPAWN.z, yaw: 1 };
  const idle = X.stepPlayer(pos, X.moveVector({}), 0, 1 / 60);
  assert.deepEqual(idle, { x: pos.x, z: pos.z, yaw: 1, moving: false });

  const big = X.stepPlayer(pos, X.moveVector({ KeyA: true }), 0, 1);
  const moved = Math.hypot(big.x - pos.x, big.z - pos.z);
  assert.ok(Math.abs(moved - X.PLAYER_SPEED * X.MAX_STEP_DT) < 1e-6);
});

test('smoothingFactor：與 FPS 無關，reduced-motion 即時到位', () => {
  assert.equal(X.smoothingFactor(5, 1 / 60, true), 1);
  assert.equal(X.smoothingFactor(5, 0, false), 0);

  const residual = (fps, seconds) => {
    let v = 1;
    const k = X.smoothingFactor(9, 1 / fps, false);
    for (let i = 0; i < fps * seconds; i++) v -= v * k;
    return v;
  };
  const r30 = residual(30, 1);
  const r60 = residual(60, 1);
  const r144 = residual(144, 1);
  assert.ok(Math.abs(r30 - r60) < 1e-9);
  assert.ok(Math.abs(r60 - r144) < 1e-9);
  assert.ok(Math.abs(r60 - Math.exp(-9)) < 1e-9);

  assert.ok(Math.abs(X.smoothingFactor(5, 1 / 60, false) - 0.08) < 0.002);
  assert.ok(Math.abs(X.smoothingFactor(9, 1 / 60, false) - 0.14) < 0.002);
});

test('exploreState：全景預設、面板開啟暫停、關閉後恢復', () => {
  const init = X.exploreState(null, '');
  assert.deepEqual(init, { exploring: false, paused: false });
  assert.equal(X.playerVisible(init), false, '全景模式不顯示角色');
  assert.equal(X.controlsActive(init), false);

  assert.deepEqual(X.exploreState(init, 'panelOpen'), init, '非探索時開面板不影響');

  const on = X.exploreState(init, 'enter');
  assert.equal(X.playerVisible(on), true);
  assert.equal(X.controlsActive(on), true);

  const paused = X.exploreState(on, 'panelOpen');
  assert.equal(X.controlsActive(paused), false, '面板開啟時不可移動');
  assert.equal(X.playerVisible(paused), true);

  const resumed = X.exploreState(paused, 'panelClose');
  assert.deepEqual(resumed, on);

  const off = X.exploreState(paused, 'exit');
  assert.deepEqual(off, init);
  assert.equal(X.playerVisible(off), false);
});

test('shouldCaptureMoveKey：焦點與 UI 優先順序', () => {
  const base = { code: 'ArrowUp', typing: false, active: true, uiOpen: false, focus: 'explore' };
  assert.equal(X.shouldCaptureMoveKey(base), true);
  assert.equal(X.shouldCaptureMoveKey({ ...base, code: 'KeyQ' }), false);
  assert.equal(X.shouldCaptureMoveKey({ ...base, typing: true }), false, '輸入欄位優先');
  assert.equal(X.shouldCaptureMoveKey({ ...base, active: false }), false, '非探索或暫停');
  assert.equal(X.shouldCaptureMoveKey({ ...base, uiOpen: true }), false, '面板或清單開啟');
  assert.equal(X.shouldCaptureMoveKey({ ...base, focus: 'control' }), false, '方向鍵留給按鈕／連結');
  assert.equal(X.shouldCaptureMoveKey({ ...base, focus: 'control', code: 'KeyW' }), true, 'WASD 在按鈕上仍可移動');
  assert.equal(X.shouldCaptureMoveKey({ ...base, typing: true, code: 'KeyW' }), false);
});

test('賭場本體是障礙、門口可站立且在互動半徑內', () => {
  const c = X.CASINO;
  assert.ok(X.isBlocked((c.block.minX + c.block.maxX) / 2, (c.block.minZ + c.block.maxZ) / 2), '賭場內部不可站');
  assert.ok(X.canStand(c.door.x, c.door.z), '門口可站立');
  assert.ok(!X.isBlocked(c.door.x, c.door.z));
  const zone = X.hitInteractZone(c.door.x, c.door.z, X.DEFAULT_INTERACT_ZONES);
  assert.ok(zone);
  assert.equal(zone.id, 'casino');
  assert.equal(zone.action, 'enterCasino');
  assert.ok(!X.hitInteractZone(c.door.x, c.door.z + c.door.r + 0.2, X.DEFAULT_INTERACT_ZONES));
});

test('從出生點可走到賭場門口（不穿模）', () => {
  // 繞過照片牆東側再西轉進門前走廊（直穿會卡在櫃檯／照片牆）。
  const waypoints = [
    { x: 5.15, z: 4.85 },
    { x: 6.05, z: 1.15 },
    { x: 5.45, z: -1.95 },
    { x: X.CASINO.door.x, z: X.CASINO.door.z }
  ];
  for (const p of [{ x: X.PLAYER_SPAWN.x, z: X.PLAYER_SPAWN.z }, ...waypoints]) {
    assert.ok(X.canStand(p.x, p.z), `途經點可站 (${p.x}, ${p.z})`);
  }
  let pos = { x: X.PLAYER_SPAWN.x, z: X.PLAYER_SPAWN.z, yaw: 0 };
  for (const target of waypoints) {
    let guard = 0;
    while (Math.hypot(target.x - pos.x, target.z - pos.z) > 0.2 && guard < 1800) {
      const dx = target.x - pos.x;
      const dz = target.z - pos.z;
      const len = Math.hypot(dx, dz) || 1;
      pos = X.stepPlayer(pos, { x: dx / len, z: dz / len, moving: true }, 0, 1 / 60);
      assert.ok(X.canStand(pos.x, pos.z), `wp (${target.x},${target.z}) step ${guard} @ (${pos.x.toFixed(2)}, ${pos.z.toFixed(2)})`);
      guard++;
    }
    assert.ok(Math.hypot(target.x - pos.x, target.z - pos.z) <= 0.25, `應靠近途經點 (${target.x}, ${target.z})`);
  }
  assert.ok(X.canStand(X.CASINO.door.x, X.CASINO.door.z));
  assert.ok(X.hitInteractZone(pos.x, pos.z, X.DEFAULT_INTERACT_ZONES));
});

test('互動提示：僅探索中且未開面板／大廳時顯示', () => {
  assert.equal(X.shouldShowInteractPrompt({
    exploring: true, paused: false, uiOpen: false, lobbyOpen: false, zoneId: 'casino'
  }), true);
  assert.equal(X.shouldShowInteractPrompt({
    exploring: true, paused: true, uiOpen: false, lobbyOpen: false, zoneId: 'casino'
  }), false);
  assert.equal(X.shouldShowInteractPrompt({
    exploring: true, paused: false, uiOpen: true, lobbyOpen: false, zoneId: 'casino'
  }), false);
  assert.equal(X.shouldShowInteractPrompt({
    exploring: true, paused: false, uiOpen: false, lobbyOpen: true, zoneId: 'casino'
  }), false);
  assert.equal(X.shouldShowInteractPrompt({
    exploring: false, paused: false, uiOpen: false, lobbyOpen: false, zoneId: 'casino'
  }), false);
  assert.equal(X.shouldShowInteractPrompt({
    exploring: true, paused: false, uiOpen: false, lobbyOpen: false, zoneId: ''
  }), false);
});

test('Enter／E 觸發互動：不搶輸入框與其它 UI', () => {
  const base = { promptVisible: true, typing: false, focus: 'explore', code: 'Enter', key: 'Enter' };
  assert.equal(X.shouldTriggerInteractKey(base), true);
  assert.equal(X.shouldTriggerInteractKey({ ...base, code: 'KeyE', key: 'e' }), true);
  assert.equal(X.shouldTriggerInteractKey({ ...base, promptVisible: false }), false);
  assert.equal(X.shouldTriggerInteractKey({ ...base, typing: true }), false);
  assert.equal(X.shouldTriggerInteractKey({ ...base, focus: 'control' }), false);
  assert.equal(X.shouldTriggerInteractKey({ ...base, code: 'KeyQ', key: 'q' }), false);
});
