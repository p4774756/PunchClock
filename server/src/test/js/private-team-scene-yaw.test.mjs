import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  PRIVATE_TEAM_YAW_MAX_DEG,
  PRIVATE_TEAM_DRAG_THRESHOLD_PX,
  clampPrivateTeamYaw,
  privateTeamDragExceedsThreshold,
  privateTeamYawFromDragDelta,
  privateTeamYawReturnProgress,
  privateTeamYawAtReturn
} = await loadPublicModule('private-team-scene.js');

const YAW_MAX_RAD = (PRIVATE_TEAM_YAW_MAX_DEG * Math.PI) / 180;

test('水平視角偏航夾在左右上限', () => {
  assert.equal(PRIVATE_TEAM_YAW_MAX_DEG, 35);
  assert.equal(clampPrivateTeamYaw(0), 0);
  assert.ok(Math.abs(clampPrivateTeamYaw(YAW_MAX_RAD) - YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(-YAW_MAX_RAD) + YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(YAW_MAX_RAD * 2) - YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(-YAW_MAX_RAD * 2) + YAW_MAX_RAD) < 1e-9);
});

test('拖曳門檻：6px 內仍視為點擊', () => {
  assert.equal(PRIVATE_TEAM_DRAG_THRESHOLD_PX, 6);
  assert.equal(privateTeamDragExceedsThreshold(6, 0), false);
  assert.equal(privateTeamDragExceedsThreshold(-6, 0), false);
  assert.equal(privateTeamDragExceedsThreshold(0, 6), false);
  assert.equal(privateTeamDragExceedsThreshold(3, 5), false);
  assert.equal(privateTeamDragExceedsThreshold(7, 0), true);
  assert.equal(privateTeamDragExceedsThreshold(5, 5), true);
});

test('拖曳偏航隨畫布寬度換算且受上限約束', () => {
  const w = 400;
  const halfDrag = privateTeamYawFromDragDelta(w / 2, w, 0);
  assert.ok(Math.abs(halfDrag - YAW_MAX_RAD) < 1e-6);
  const over = privateTeamYawFromDragDelta(w * 2, w, 0);
  assert.ok(Math.abs(over - YAW_MAX_RAD) < 1e-6);
});

test('放手回正進度', () => {
  assert.equal(privateTeamYawReturnProgress(0, false), 0);
  assert.equal(privateTeamYawReturnProgress(600, false), 1);
  assert.equal(privateTeamYawReturnProgress(100, true), 1);
  const mid = privateTeamYawAtReturn(0.5, privateTeamYawReturnProgress(300, false));
  assert.ok(mid > 0 && mid < 0.5);
  assert.equal(privateTeamYawAtReturn(0.4, 1), 0);
});