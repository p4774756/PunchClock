import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  PRIVATE_TEAM_PITCH_MAX_UP_DEG,
  PRIVATE_TEAM_PITCH_MAX_DOWN_DEG,
  clampPrivateTeamPitch,
  privateTeamPitchFromDragDelta,
  privateTeamPitchDragVelocityRadPerMs,
  privateTeamPitchMomentumTick
} = await loadPublicModule('private-team-scene.js');

const PITCH_UP_RAD = (PRIVATE_TEAM_PITCH_MAX_UP_DEG * Math.PI) / 180;
const PITCH_DOWN_RAD = (PRIVATE_TEAM_PITCH_MAX_DOWN_DEG * Math.PI) / 180;

test('俯仰角夾在上／下限內', () => {
  assert.equal(PRIVATE_TEAM_PITCH_MAX_UP_DEG, 20);
  assert.equal(PRIVATE_TEAM_PITCH_MAX_DOWN_DEG, 14);
  assert.equal(clampPrivateTeamPitch(0), 0);
  assert.ok(Math.abs(clampPrivateTeamPitch(PITCH_UP_RAD) - PITCH_UP_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamPitch(-PITCH_DOWN_RAD) + PITCH_DOWN_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamPitch(PITCH_UP_RAD * 2) - PITCH_UP_RAD) < 1e-9);
  assert.ok(Math.abs(clampPrivateTeamPitch(-PITCH_DOWN_RAD * 2) + PITCH_DOWN_RAD) < 1e-9);
});

test('垂直拖曳：上拉為正、下拉為負', () => {
  const h = 800;
  const upHalf = privateTeamPitchFromDragDelta(-h / 2, h, 0);
  assert.ok(upHalf > 0);
  const downHalf = privateTeamPitchFromDragDelta(h / 2, h, 0);
  assert.ok(downHalf < 0);
  const atUp = privateTeamPitchFromDragDelta(-h * 2, h, 0);
  assert.ok(Math.abs(atUp - PITCH_UP_RAD) < 1e-6);
  const atDown = privateTeamPitchFromDragDelta(h * 2, h, 0);
  assert.ok(Math.abs(atDown + PITCH_DOWN_RAD) < 1e-6);
});

test('俯仰慣性（窄螢幕與桌面）', () => {
  const velUp = privateTeamPitchDragVelocityRadPerMs(-120, 80, 844);
  assert.ok(velUp > 0);
  assert.equal(privateTeamPitchDragVelocityRadPerMs(-8, 8, 844), 0);
  const narrowStep = privateTeamPitchMomentumTick(0.05, velUp, 32, 844);
  assert.ok(narrowStep.pitch > 0.05);
  const desktopVel = privateTeamPitchDragVelocityRadPerMs(100, 80, 800);
  assert.ok(desktopVel < 0);
  const desktopStep = privateTeamPitchMomentumTick(-0.05, desktopVel, 32, 800);
  assert.ok(desktopStep.pitch < -0.05);
  assert.ok(desktopStep.pitch >= -PITCH_DOWN_RAD - 1e-6);
});