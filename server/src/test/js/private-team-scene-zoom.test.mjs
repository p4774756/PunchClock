import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  clampPrivateTeamZoom,
  privateTeamPinchZoomFactor,
  privateTeamDefaultZoom,
  privateTeamDefaultFov,
  privateTeamDefaultYaw,
  PRIVATE_TEAM_ZOOM_MIN,
  PRIVATE_TEAM_ZOOM_MAX,
  PRIVATE_TEAM_CAMERA_FOV,
  PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT,
  PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT
} = await loadPublicModule('private-team-scene.js');

test('縮放倍數夾在上下限', () => {
  assert.equal(PRIVATE_TEAM_ZOOM_MIN, 0.72);
  assert.equal(PRIVATE_TEAM_ZOOM_MAX, 2.05);
  assert.equal(clampPrivateTeamZoom(0.5), PRIVATE_TEAM_ZOOM_MIN);
  assert.equal(clampPrivateTeamZoom(3), PRIVATE_TEAM_ZOOM_MAX);
  assert.equal(clampPrivateTeamZoom(1), 1);
});

test('窄螢幕直向預設拉遠全景，並以 FOV／偏航把窗台掌機收入畫面', () => {
  assert.equal(privateTeamDefaultZoom(390, 844), 1.52);
  assert.equal(privateTeamDefaultZoom(1200, 800), 1);
  assert.equal(privateTeamDefaultZoom(800, 600), 1);
  assert.equal(privateTeamDefaultFov(1280, 800), PRIVATE_TEAM_CAMERA_FOV);
  assert.equal(privateTeamDefaultFov(390, 844), PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT);
  assert.equal(privateTeamDefaultYaw(1280, 800), 0);
  assert.equal(privateTeamDefaultYaw(390, 844), PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT);
  assert.ok(PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT < 0);
  assert.ok(PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT > PRIVATE_TEAM_CAMERA_FOV);
});

test('雙指縮放：手指張開（距離變大）時 zoomFactor 變小', () => {
  const startZoom = 1;
  const startDist = 120;
  assert.ok(privateTeamPinchZoomFactor(startZoom, startDist, 180) < startZoom);
  assert.ok(privateTeamPinchZoomFactor(startZoom, startDist, 80) > startZoom);
  assert.equal(privateTeamPinchZoomFactor(startZoom, startDist, startDist), startZoom);
});