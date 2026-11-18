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

function animWithoutDepth() {
  const clips = M.MILU_ANIM.clips;
  return {
    faceDeadzone: M.MILU_ANIM.faceDeadzone,
    faceBucket: M.MILU_ANIM.faceBucket,
    faceHysteresis: M.MILU_ANIM.faceHysteresis,
    blinkEvery: M.MILU_ANIM.blinkEvery,
    fps: M.MILU_ANIM.fps,
    clips: {
      walk: {
        left: clips.walk.left,
        right: clips.walk.right,
        front: [],
        back: []
      },
      idle: clips.idle,
      wave: clips.wave
    }
  };
}

// 0° 朝鏡頭，90° 往右，180° 離鏡頭，-90° 往左。
function axesFromDegrees(deg) {
  const rad = deg * Math.PI / 180;
  return { x: Math.sin(rad), z: Math.cos(rad) };
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
  const anim = animWithoutDepth();
  let state = walkStep(M.createMiluState(), 0.02, -1, 0, { anim: anim });
  assert.equal(state.facing, 'left');
  state = walkStep(state, 0.02, 0.1, 0.99, { anim: anim });
  assert.equal(state.facing, 'left');
  assert.equal(M.resolveFacing('right', 0.05, -0.9, anim), 'right');
});

test('沒有前／後圖時垂直移動退回左／右', () => {
  const anim = animWithoutDepth();
  const state = walkStep(M.createMiluState(), 0.02, 0.05, 1, { anim: anim });
  assert.equal(state.facing, 'right');
  assert.equal(M.miluClipPath(state, anim).indexOf('walk/right_'), 0);
  assert.equal(M.resolveFacing('left', 0, 1, anim), 'left');
});

test('朝鏡頭走用 front，離鏡頭走用 back，左右仍用側向圖', () => {
  assert.equal(M.MILU_ANIM.clips.walk.front.length, 4);
  assert.equal(M.MILU_ANIM.clips.walk.front[0], 'walk/front_01.png');
  assert.equal(M.MILU_ANIM.clips.walk.back.length, 4);
  assert.equal(M.MILU_ANIM.clips.walk.back[0], 'walk/back_01.png');

  let state = walkStep(M.createMiluState(), 0.02, 0, 1);
  assert.equal(state.facing, 'front');
  assert.equal(M.miluClipPath(state).indexOf('walk/front_'), 0);

  state = walkStep(state, 0.02, 0, -1);
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state).indexOf('walk/back_'), 0);

  state = walkStep(state, 0.02, -1, 0);
  assert.equal(state.facing, 'left');
  assert.equal(M.miluClipPath(state).indexOf('walk/left_'), 0);

  state = walkStep(state, 0.02, 1, 0);
  assert.equal(state.facing, 'right');
  assert.equal(M.miluClipPath(state).indexOf('walk/right_'), 0);
});

test('非零 theta 時朝向跟畫面軸，不跟世界軸', () => {
  const theta = 1.2;
  const towardWorld = X.cameraRelative({ x: 0, z: 1 }, theta);
  const towardScreen = M.screenAxes(towardWorld.x, towardWorld.z, theta);
  let state = walkStep(M.createMiluState(), 0.02, towardScreen.x, towardScreen.z);
  assert.equal(state.facing, 'front');
  assert.equal(M.miluClipPath(state).indexOf('walk/front_'), 0);

  const raw = walkStep(M.createMiluState(), 0.02, towardWorld.x, towardWorld.z);
  assert.notEqual(raw.facing, 'front');

  const awayWorld = X.cameraRelative({ x: 0, z: -1 }, theta);
  const awayScreen = M.screenAxes(awayWorld.x, awayWorld.z, theta);
  state = walkStep(state, 0.02, awayScreen.x, awayScreen.z);
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state).indexOf('walk/back_'), 0);

  const leftWorld = X.cameraRelative({ x: -1, z: 0 }, theta);
  const leftScreen = M.screenAxes(leftWorld.x, leftWorld.z, theta);
  state = walkStep(state, 0.02, leftScreen.x, leftScreen.z);
  assert.equal(state.facing, 'left');

  const rightWorld = X.cameraRelative({ x: 1, z: 0 }, theta);
  const rightScreen = M.screenAxes(rightWorld.x, rightWorld.z, theta);
  state = walkStep(state, 0.02, rightScreen.x, rightScreen.z);
  assert.equal(state.facing, 'right');
});

test('方向交界有滯後，來回微幅擺動不會閃幀', () => {
  let state = walkStep(M.createMiluState(), 0.02, 0, 1);
  assert.equal(state.facing, 'front');
  [50, 40, 50, 42, 48].forEach((deg) => {
    const axes = axesFromDegrees(deg);
    state = walkStep(state, 0.02, axes.x, axes.z);
    assert.equal(state.facing, 'front');
  });

  let intoRight = axesFromDegrees(70);
  state = walkStep(state, 0.02, intoRight.x, intoRight.z);
  assert.equal(state.facing, 'right');
  [55, 40, 50].forEach((deg) => {
    const axes = axesFromDegrees(deg);
    state = walkStep(state, 0.02, axes.x, axes.z);
    assert.equal(state.facing, 'right');
  });

  const intoFront = axesFromDegrees(20);
  state = walkStep(state, 0.02, intoFront.x, intoFront.z);
  assert.equal(state.facing, 'front');

  const started = axesFromDegrees(40);
  const first = walkStep(M.createMiluState(), 0.02, started.x, started.z);
  assert.equal(first.facing, 'front');
});

test('預設 MILU_ANIM 的 backBreath／backBlink 長度與檔名正確', () => {
  const backBreath = M.MILU_ANIM.clips.idle.backBreath;
  const backBlink = M.MILU_ANIM.clips.idle.backBlink;
  assert.equal(backBreath.length, 4);
  assert.equal(backBreath[0], 'idle/back_breath_01.png');
  assert.equal(backBreath[3], 'idle/back_breath_04.png');
  assert.equal(backBlink.length, 2);
  assert.equal(backBlink[0], 'idle/back_blink_01.png');
  assert.equal(backBlink[1], 'idle/back_blink_02.png');
});

test('停下沿用上次朝向；朝背面停下播 back_breath_*', () => {
  let state = walkStep(M.createMiluState(), 0.02, 0, 1);
  state = idleStep(state, 0.02);
  assert.equal(state.mode, 'idle');
  assert.equal(state.facing, 'front');
  assert.equal(M.miluClipPath(state).indexOf('idle/breath_'), 0);

  state = walkStep(state, 0.02, 0, -1);
  state = idleStep(state, 0.02);
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state).indexOf('idle/back_breath_'), 0);
});

test('朝背面停下，時間到眨眼播 back_blink_*，播完回到 back_breath_*', () => {
  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 0.2,
    fps: { walk: 8, idle: 5, blink: 10, wave: 8 },
    clips: {
      walk: {
        left: ['walk/left_01.png'],
        right: ['walk/right_01.png'],
        front: ['walk/front_01.png'],
        back: ['walk/back_01.png']
      },
      idle: {
        breath: ['idle/breath_01.png'],
        blink: ['idle/blink_01.png', 'idle/blink_02.png'],
        backBreath: ['idle/back_breath_01.png', 'idle/back_breath_02.png'],
        backBlink: ['idle/back_blink_01.png', 'idle/back_blink_02.png']
      },
      wave: ['wave/01.png']
    }
  };
  let state = walkStep(M.createMiluState(), 0.02, 0, -1, { anim: anim });
  state = idleStep(state, 0.02, { anim: anim });
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/back_breath_'), 0);

  for (let i = 0; i < 5; i++) state = idleStep(state, 0.05, { anim: anim });
  assert.equal(state.blinking, true);
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/back_blink_'), 0);

  let guard = 0;
  while (state.blinking && guard < 10) {
    state = idleStep(state, 0.1, { anim: anim });
    guard += 1;
  }
  assert.equal(state.blinking, false);
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/back_breath_'), 0);
});

test('只有 backBreath、沒有 backBlink 時，不要改播正面眨眼', () => {
  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 0.1,
    fps: { walk: 8, idle: 5, blink: 10, wave: 8 },
    clips: {
      walk: {
        left: ['walk/left_01.png'],
        right: ['walk/right_01.png'],
        front: ['walk/front_01.png'],
        back: ['walk/back_01.png']
      },
      idle: {
        breath: ['idle/breath_01.png'],
        blink: ['idle/blink_01.png', 'idle/blink_02.png'],
        backBreath: ['idle/back_breath_01.png'],
        backBlink: []
      },
      wave: ['wave/01.png']
    }
  };
  let state = walkStep(M.createMiluState(), 0.02, 0, -1, { anim: anim });
  state = idleStep(state, 0.02, { anim: anim });
  assert.equal(state.facing, 'back');

  for (let i = 0; i < 20; i++) state = idleStep(state, 0.05, { anim: anim });
  assert.equal(state.blinking, false);
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/back_breath_'), 0);
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/blink_'), -1);
});

test('forgetOptionalClip 清掉 backBreath 後退回正面呼吸；清 backBlink 不影響 backBreath', () => {
  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 3.4,
    fps: M.MILU_ANIM.fps,
    clips: {
      walk: {
        left: ['walk/left_01.png'],
        right: ['walk/right_01.png'],
        front: ['walk/front_01.png'],
        back: ['walk/back_01.png']
      },
      idle: {
        breath: ['idle/breath_01.png'],
        blink: ['idle/blink_01.png'],
        backBreath: ['idle/back_breath_01.png', 'idle/back_breath_02.png'],
        backBlink: ['idle/back_blink_01.png']
      },
      wave: ['wave/01.png']
    }
  };

  assert.equal(M.forgetOptionalClip(anim, 'idle/back_blink_01.png'), true);
  assert.equal(anim.clips.idle.backBlink.length, 0);
  assert.equal(anim.clips.idle.backBreath.length, 2);

  let state = walkStep(M.createMiluState(), 0.02, 0, -1, { anim: anim });
  state = idleStep(state, 0.02, { anim: anim });
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/back_breath_'), 0);

  assert.equal(M.forgetOptionalClip(anim, 'idle/back_breath_01.png'), true);
  assert.equal(anim.clips.idle.backBreath.length, 0);

  state = walkStep(M.createMiluState(), 0.02, 0, -1, { anim: anim });
  state = idleStep(state, 0.02, { anim: anim });
  assert.equal(state.facing, 'back');
  assert.equal(M.miluClipPath(state, anim).indexOf('idle/breath_'), 0);
});

test('前後圖載入失敗時清掉該方向並退回左右', () => {
  const anim = {
    faceDeadzone: 0.22,
    blinkEvery: 3.4,
    fps: M.MILU_ANIM.fps,
    clips: {
      walk: {
        left: ['walk/left_01.png'],
        right: ['walk/right_01.png'],
        front: ['walk/front_01.png', 'walk/front_02.png'],
        back: ['walk/back_01.png']
      },
      idle: { breath: ['idle/breath_01.png'], blink: ['idle/blink_01.png'] }
    }
  };
  assert.equal(M.forgetOptionalClip(anim, 'walk/front_02.png'), true);
  assert.equal(anim.clips.walk.front.length, 0);
  assert.equal(anim.clips.walk.back.length, 1);
  assert.equal(M.forgetOptionalClip(anim, 'walk/left_01.png'), false);
  assert.equal(M.resolveFacing('front', 0, 1, anim), 'right');
  const state = walkStep(M.createMiluState(), 0.02, 0, 1, { anim: anim });
  assert.equal(state.facing, 'right');
  assert.equal(M.miluClipPath(state, anim), 'walk/right_01.png');
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
  let state = walkStep(M.createMiluState(), 0.01, 0.1, 1, { anim: anim });
  assert.equal(state.facing, 'front');
  assert.equal(M.miluClipPath(state, anim), 'walk/front_01.png');
  state = walkStep(state, 0.01, 0.1, -1, { anim: anim });
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

test('載入優先順序：第一批是最小集合，其餘 required 再接 optional', () => {
  const plan = M.miluOrderedLoadPlan(M.MILU_ANIM);
  const parts = M.miluPartitionLoadPaths(M.MILU_ANIM);
  assert.deepEqual(parts.bootstrap, M.MILU_LOAD.bootstrap);
  assert.equal(plan[0], 'idle/breath_01.png');
  assert.deepEqual(plan.slice(0, parts.bootstrap.length), parts.bootstrap);
  const optionalStart = plan.indexOf('walk/back_01.png');
  const requiredTail = plan.indexOf('walk/left_04.png');
  assert.ok(optionalStart > requiredTail);
  assert.ok(parts.requiredRest.indexOf('walk/front_01.png') < 0);
});

test('重試間隔 1／3／8 秒，超過 3 次不再重試', () => {
  assert.equal(M.miluRetryDelayMs(0), 1000);
  assert.equal(M.miluRetryDelayMs(1), 3000);
  assert.equal(M.miluRetryDelayMs(2), 8000);
  assert.equal(M.miluRetryDelayMs(3), -1);
  assert.equal(M.miluShouldRetry(1), true);
  assert.equal(M.miluShouldRetry(3), true);
  assert.equal(M.miluShouldRetry(4), false);
});

test('第 2 次重試起加 cache-busting query', () => {
  assert.equal(M.miluTextureUrl('/pet/milu/', 'idle/breath_01.png', 0), '/pet/milu/idle/breath_01.png');
  assert.equal(M.miluTextureUrl('/pet/milu/', 'idle/breath_01.png', 1), '/pet/milu/idle/breath_01.png');
  assert.equal(M.miluTextureUrl('/pet/milu/', 'idle/breath_01.png', 2), '/pet/milu/idle/breath_01.png?r=2');
});

test('併發上限不超過 MILU_LOAD.maxConcurrent', () => {
  assert.equal(M.miluConcurrencySlots(0), 4);
  assert.equal(M.miluConcurrencySlots(3), 1);
  assert.equal(M.miluConcurrencySlots(4), 0);
  const inFlight = new Set(['walk/left_01.png']);
  const skip = new Set();
  const queue = ['walk/left_02.png', 'walk/left_03.png', 'walk/left_04.png'];
  const starts = M.miluPathsToStart(queue, inFlight, skip);
  assert.equal(starts.length, 3);
});

test('已成功的圖不再排入併發啟動', () => {
  const inFlight = new Set();
  const skip = new Set(['idle/breath_01.png']);
  const queue = ['idle/breath_01.png', 'walk/front_01.png'];
  const starts = M.miluPathsToStart(queue, inFlight, skip);
  assert.deepEqual(starts, ['walk/front_01.png']);
});

test('最近可用幀：同動作已載幀 → idle breath → 任何已載幀', () => {
  const loaded = new Set(['idle/breath_01.png', 'walk/right_02.png']);
  assert.equal(M.resolveDisplayPath('walk/right_04.png', M.MILU_ANIM, loaded), 'walk/right_02.png');
  assert.equal(M.resolveDisplayPath('walk/left_01.png', M.MILU_ANIM, loaded), 'idle/breath_01.png');
  assert.equal(M.resolveDisplayPath('wave/03.png', M.MILU_ANIM, loaded), 'idle/breath_01.png');
});

test('ready／failed 判斷', () => {
  assert.equal(M.miluReadyFromState({ loadedCount: 1, failed: false }), true);
  assert.equal(M.miluReadyFromState({ loadedCount: 0, failed: false }), false);
  assert.equal(M.miluReadyFromState({ loadedCount: 3, failed: true }), false);
  const required = M.miluCollectPaths(M.MILU_ANIM).required;
  assert.equal(M.miluFailedFromState({ exhausted: required.slice(0, 2) }), false);
  assert.equal(M.miluFailedFromState({ exhausted: required }), true);
  assert.equal(M.miluFailedFromState({ failed: true }), true);
});

test('搖桿往上（W）是離鏡頭，要播背面；往下是朝鏡頭，播正面', () => {
  const theta = 0.7;
  const up = X.cameraRelative(X.moveVector({ KeyW: true }), theta);
  const upScreen = M.screenAxes(up.x, up.z, theta);
  let state = walkStep(M.createMiluState(), 0.02, upScreen.x, upScreen.z);
  assert.equal(state.facing, 'back');

  const down = X.cameraRelative(X.moveVector({ KeyS: true }), theta);
  const downScreen = M.screenAxes(down.x, down.z, theta);
  state = walkStep(M.createMiluState(), 0.02, downScreen.x, downScreen.z);
  assert.equal(state.facing, 'front');
});
