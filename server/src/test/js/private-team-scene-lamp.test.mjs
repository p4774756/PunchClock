import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
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

test('首頁秘書場景已移除名牌、泡泡錨點下移', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /labelTexture/);
  assert.doesNotMatch(src, /namePlate|nameStand/);
  assert.match(src, /bubbleFloatMin = 1\.68/);
  assert.doesNotMatch(src, /bubbleFloatMin = 2\.2/);
});
