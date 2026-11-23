import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  clampPrivateTeamZoom,
  PRIVATE_TEAM_ZOOM_MIN,
  PRIVATE_TEAM_ZOOM_MAX
} = await loadPublicModule('private-team-scene.js');

test('縮放倍數夾在上下限', () => {
  assert.equal(PRIVATE_TEAM_ZOOM_MIN, 0.72);
  assert.equal(PRIVATE_TEAM_ZOOM_MAX, 2.05);
  assert.equal(clampPrivateTeamZoom(0.5), PRIVATE_TEAM_ZOOM_MIN);
  assert.equal(clampPrivateTeamZoom(3), PRIVATE_TEAM_ZOOM_MAX);
  assert.equal(clampPrivateTeamZoom(1), 1);
});