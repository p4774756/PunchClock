import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const { privateTeamSceneLighting } = await loadPublicModule('private-team-scene.js');

test('privateTeamSceneLighting 白天關燈、夜晚暖光偏暗', () => {
  const day = privateTeamSceneLighting(true);
  const night = privateTeamSceneLighting(false);
  assert.equal(day.point, 0);
  assert.equal(day.spot, 0);
  assert.equal(day.bulbOpacity, 0);
  assert.equal(day.shadeEmissive, 0);
  assert.ok(night.point > 0);
  assert.ok(night.spot > 0);
  assert.ok(night.haloOpacity > 0);
  assert.ok(night.shadeEmissive > 0);
  assert.ok(night.amb < day.amb);
  assert.ok(night.dir < day.dir);
});
