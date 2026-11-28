import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  PRIVATE_TEAM_SCENE_TARGET_FPS,
  privateTeamSceneFrameIntervalMs,
  privateTeamSceneRenderDue,
  privateTeamSceneAnimationPaused
} = await loadPublicModule('private-team-scene-controls.js');

const { HOLOGRAM_HUD_REDRAW_MS, shouldRedrawHologramHud } = await loadPublicModule(
  'private-team-scene-room.js'
);

test('場景目標幀間隔為 60 FPS', () => {
  assert.equal(PRIVATE_TEAM_SCENE_TARGET_FPS, 60);
  assert.ok(Math.abs(privateTeamSceneFrameIntervalMs() - 1000 / 60) < 1e-9);
});

test('privateTeamSceneRenderDue 依上次 render 時刻節流', () => {
  const base = 1000;
  const step = privateTeamSceneFrameIntervalMs();
  assert.equal(privateTeamSceneRenderDue(0, base), true);
  assert.equal(privateTeamSceneRenderDue(base, base + 10), false);
  assert.equal(privateTeamSceneRenderDue(base, base + step + 0.01), true);
  assert.equal(privateTeamSceneRenderDue(base, base + 1), false);
});

test('modal 與分頁 hidden 合取暫停', () => {
  assert.equal(privateTeamSceneAnimationPaused(false, false), false);
  assert.equal(privateTeamSceneAnimationPaused(true, false), true);
  assert.equal(privateTeamSceneAnimationPaused(false, true), true);
  assert.equal(privateTeamSceneAnimationPaused(true, true), true);
});

test('全息 HUD 貼圖重繪節流約每秒', () => {
  assert.equal(HOLOGRAM_HUD_REDRAW_MS, 1000);
  assert.equal(shouldRedrawHologramHud(0, 200), true);
  assert.equal(shouldRedrawHologramHud(100, 500), false);
  assert.equal(shouldRedrawHologramHud(100, 1101), true);
});