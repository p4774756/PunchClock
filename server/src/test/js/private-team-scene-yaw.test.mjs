import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  PRIVATE_TEAM_YAW_MAX_DEG,
  PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT,
  PRIVATE_TEAM_DRAG_THRESHOLD_PX,
  clampPrivateTeamYaw,
  clampPrivateTeamYawViewport,
  privateTeamDragExceedsThreshold,
  privateTeamNarrowPortrait,
  privateTeamPanHintShouldShow,
  privateTeamYawFromDragDelta,
  privateTeamYawMaxRadians,
  privateTeamYawDragVelocityRadPerMs,
  privateTeamYawMomentumTick
} = await loadPublicModule('private-team-scene.js');

const YAW_MAX_RAD = (PRIVATE_TEAM_YAW_MAX_DEG * Math.PI) / 180;
const NARROW_YAW_MAX_RAD = (PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT * Math.PI) / 180;

test('水平視角偏航夾在左右上限', () => {
  assert.equal(PRIVATE_TEAM_YAW_MAX_DEG, 35);
  assert.ok(PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT > PRIVATE_TEAM_YAW_MAX_DEG);
  assert.equal(clampPrivateTeamYaw(0), 0);
  assert.ok(Math.abs(clampPrivateTeamYaw(YAW_MAX_RAD) - YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(-YAW_MAX_RAD) + YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(YAW_MAX_RAD * 2) - YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamYaw(-YAW_MAX_RAD * 2) + YAW_MAX_RAD) < 1e-9);
});

test('窄螢幕直向加大偏航上限且各視窗鬆手不回正', () => {
  assert.equal(privateTeamNarrowPortrait(393, 844), true);
  assert.equal(privateTeamNarrowPortrait(1280, 800), false);
  assert.equal(privateTeamNarrowPortrait(600, 400), false);
  assert.ok(Math.abs(privateTeamYawMaxRadians(393, 844) - NARROW_YAW_MAX_RAD) < 1e-9);
  assert.ok(Math.abs(privateTeamYawMaxRadians(1280, 800) - YAW_MAX_RAD) < 1e-9);
  const atEdge = clampPrivateTeamYawViewport(NARROW_YAW_MAX_RAD * 1.5, 393, 844);
  assert.ok(Math.abs(atEdge - NARROW_YAW_MAX_RAD) < 1e-6);
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
  assert.ok(Math.abs(halfDrag + YAW_MAX_RAD) < 1e-6);
  const over = privateTeamYawFromDragDelta(w * 2, w, 0);
  assert.ok(Math.abs(over + YAW_MAX_RAD) < 1e-6);
  const narrowHalf = privateTeamYawFromDragDelta(w / 2, w, 0, 844);
  assert.ok(Math.abs(narrowHalf + NARROW_YAW_MAX_RAD) < 1e-5);
});

test('桌面與窄螢幕慣性、提示條件', () => {
  const narrowVel = privateTeamYawDragVelocityRadPerMs(120, 80, 393, 844);
  assert.ok(narrowVel < 0);
  assert.equal(privateTeamYawDragVelocityRadPerMs(10, 8, 393, 844), 0);
  const narrowStep = privateTeamYawMomentumTick(-0.2, narrowVel, 32, 393, 844);
  assert.ok(Math.abs(narrowStep.yaw) >= 0.2);
  const desktopVel = privateTeamYawDragVelocityRadPerMs(120, 80, 1280, 800);
  assert.ok(desktopVel < 0);
  const desktopStep = privateTeamYawMomentumTick(0.15, desktopVel, 32, 1280, 800);
  assert.notEqual(desktopStep.yaw, 0.15);
  assert.ok(Math.abs(desktopStep.yaw) <= YAW_MAX_RAD + 1e-6);
  assert.ok(Math.abs(desktopStep.vel) < Math.abs(desktopVel));
  assert.equal(privateTeamPanHintShouldShow(true, false), true);
  assert.equal(privateTeamPanHintShouldShow(true, true), false);
  assert.equal(privateTeamPanHintShouldShow(false, false), false);
});