// milu 看板純邏輯：朝向選擇與幀切換。node --test server/src/test/js/island-milu.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('island-milu.js');
const X = await loadPublicModule('island-explore.js');

function idleStep(state, dt, extra) {
  return M.miluStep(state, dt, Object.assign({ moving: false, near: false, screenX: 0, screenZ: 0 }, extra));
}

function walkStep(state, dt, screenX, screenZ, extra) {
  return M.miluStep(state, dt, Object.assign({
    moving: true,
    near: false,
    screenX: screenX,
    screenZ: screenZ
  }, extra));
}

test('畫面軸是 cameraRelative 的反函數', () => {
  const theta = 0.35;
  const right = X.cameraRelative({ x: 1, z: 0 }, theta);
  const screenRight = M.screenAxes(right.x, right.z, theta);
  assert.ok(Math.abs(screenRight.x - 1) < 1e-9);
  assert.ok(Math.abs(screenRight.z) < 1e-9);

  const forward = X.cameraRelative({ x: 0, z: -1 }, theta);
  const screenForward = M.screenAxes(forward.x, forward.z, theta);
  assert.ok(screenForward.z < -0.9);
  assert.ok(Math.abs(screenForward.x) < 1e-9);
});

test('往右走用 right，往左走用 left', () => {
  let state = walkStep(M.createMiluState(), 0.125, 1, 0);
  assert.equal(state.mode, 'walk');
  assert.equal(state.facing, 'right');
  assert.equal(state.frame, 1);
  assert.equal(M.miluClipPath(state), 'walk/right_02.png');

  state = walkStep(state, 0.01, -1, 0);
  assert.equal(state.facing, 'left');
  assert.equal(M.miluClipPath(state), 'walk/left_01.png');
});

test('水平分量很小時沿用上次朝向', () => {
  let state = walkStep(M.createMiluState(), 0.02, -1, 0);
  assert.equal(state.facing, 'left');
  state = walkStep(state, 0.02, 0.1, -0.99);
  assert.equal(state.facing, 'left');
  assert.equal(M.resolveFacing('right', 0.05, 0.9), 'right');
});

test('沒有前／後圖時垂直移動退回左／右', () => {
  const state = walkStep(M.createMiluState(), 0.02, 0.05, -1);
  assert.equal(state.facing, 'right');
  assert.equal(M.miluClipPath(state).indexOf('walk/right_'), 0);
  assert.equal(M.resolveFacing('left', 0, -1), 'left');
});

test('補上前／後圖後，垂直分量為主就改用那組', () => {
  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 3.4,
    fps: { walk: 8, idle: 5, blink: 10, wave: 8 },
    clips: {
      walk: {
        left: ['walk/left_01.png'],
        right: ['walk/right_01.png'],
        front: ['walk/front_01.png', 'walk/front_02.png'],
        back: ['walk/back_01.png']
      },
      idle: { breath: ['idle/breath_01.png'], blink: ['idle/blink_01.png'] },
      wave: ['wave/01.png']
    }
  };
  let state = walkStep(M.createMiluState(), 0.01, 0.1, -1, { anim: anim });
  assert.equal(state.facing, 'front');
  assert.equal(M.miluClipPath(state, anim), 'walk/front_01.png');
  state = walkStep(state, 0.01, 0.1, 1, { anim: anim });
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state, anim), 'walk/back_01.png');
});

test('走路約 8fps，一秒推進八幀', () => {
  let state = walkStep(M.createMiluState(), 0.125, -1, 0);
  assert.equal(state.frame, 1);
  state = walkStep(state, 1, -1, 0);
  assert.equal(state.frame, 1);
  assert.equal(state.facing, 'left');
});

test('停下播呼吸，時間到眨眼一輪再回到呼吸', () => {
  let state = idleStep(M.createMiluState(), 0.2);
  assert.equal(state.mode, 'idle');
  assert.equal(state.blinking, false);
  assert.equal(M.miluClipPath(state), 'idle/breath_02.png');

  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 0.25,
    fps: { walk: 8, idle: 5, blink: 10, wave: 8 },
    clips: M.MILU_ANIM.clips
  };
  state = M.createMiluState();
  for (let i = 0; i < 5; i++) state = idleStep(state, 0.05, { anim: anim });
  assert.equal(state.blinking, true);
  assert.equal(M.miluClipPath(state, anim), 'idle/blink_01.png');
  state = idleStep(state, 0.1, { anim: anim });
  assert.equal(M.miluClipPath(state, anim), 'idle/blink_02.png');
  state = idleStep(state, 0.1, { anim: anim });
  assert.equal(state.blinking, false);
  assert.equal(M.miluClipPath(state, anim), 'idle/breath_01.png');
});

test('靠近揮手一次，離開前不再揮', () => {
  const npcs = [{ x: 0, z: 0 }];
  assert.equal(M.isNearWave(1, 0, npcs, [], M.MILU_ANIM), true);
  assert.equal(M.isNearWave(3, 0, npcs, [], M.MILU_ANIM), false);
  assert.equal(M.isNearWave(1.5, 0, [], [{ x: 0, z: 0, r: 1.2 }], M.MILU_ANIM), true);
  assert.equal(M.isNearWave(3, 0, [], [{ x: 0, z: 0, r: 1.2 }], M.MILU_ANIM), false);

  let state = M.miluStep(M.createMiluState(), 0.05, { moving: false, near: true, screenX: 0, screenZ: 0 });
  assert.equal(state.mode, 'wave');
  assert.equal(M.miluClipPath(state), 'wave/01.png');
  let guard = 0;
  while (!state.waved && guard < 30) {
    state = M.miluStep(state, 0.2, { moving: false, near: true, screenX: 0, screenZ: 0 });
    guard += 1;
  }
  assert.equal(state.waved, true);
  assert.notEqual(state.mode, 'wave');
  state = M.miluStep(state, 0.2, { moving: false, near: true, screenX: 0, screenZ: 0 });
  assert.equal(state.waved, true);
  assert.equal(state.mode, 'idle');

  state = M.miluStep(state, 0.05, { moving: false, near: false, screenX: 0, screenZ: 0 });
  assert.equal(state.waved, false);
  state = M.miluStep(state, 0.05, { moving: false, near: true, screenX: 0, screenZ: 0 });
  assert.equal(state.mode, 'wave');
});

test('reduced-motion 停掉待機呼吸與眨眼', () => {
  let state = M.createMiluState();
  for (let i = 0; i < 40; i++) state = idleStep(state, 0.1, { reduced: true });
  assert.equal(state.mode, 'idle');
  assert.equal(state.frame, 0);
  assert.equal(state.blinking, false);
  assert.equal(M.miluClipPath(state), 'idle/breath_01.png');

  state = walkStep(state, 0.02, -1, 0, { reduced: true });
  assert.equal(state.facing, 'left');
  assert.equal(M.miluClipPath(state).indexOf('walk/left_'), 0);
});

test('腳底貼齊本地 y = 0', () => {
  const height = M.MILU_ANIM.height;
  const footV = M.MILU_ANIM.footV;
  const offset = M.feetOffsetY(height, footV);
  const feetY = height * (footV - 0.5) + offset;
  assert.ok(Math.abs(feetY) < 1e-12);
  assert.ok(offset > 0);
});
