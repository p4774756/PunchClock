import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const { privateTeamSceneLighting, PRIVATE_TEAM_TRAY_SURFACE_Y } = await loadPublicModule(
  'private-team-scene.js'
);

test('privateTeamSceneLighting 夜晚暖光檯燈', () => {
  const night = privateTeamSceneLighting();
  assert.ok(night.point > 0);
  assert.ok(night.spot > 0);
  assert.ok(night.haloOpacity > 0);
  assert.ok(night.shadeEmissive > 0);
  assert.ok(night.bulbOpacity > 0);
  assert.ok(night.amb < 0.5);
  assert.ok(night.dir < 0.5);
});

test('首頁秘書場景已移除名牌、泡泡錨點下移', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /labelTexture/);
  assert.doesNotMatch(src, /namePlate|nameStand/);
  assert.match(src, /bubbleFloatMin = 1\.68/);
  assert.doesNotMatch(src, /bubbleFloatMin = 2\.2/);
});

test('下沉浴缸、托盤與雙螢幕支架', () => {
  assert.ok(PRIVATE_TEAM_TRAY_SURFACE_Y > 0.45 && PRIVATE_TEAM_TRAY_SURFACE_Y < 0.52);
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.match(src, /bathtubWater/);
  assert.match(src, /keyboardTray/);
  assert.match(src, /addMonitor\(root, secretaryLook, -0\.34/);
  assert.match(src, /addMonitor\(root, secretaryLook, 0\.34/);
  assert.match(src, /basePlate/);
  assert.doesNotMatch(src, /rug-weave/);
  assert.doesNotMatch(src, /rugEdge/);
  assert.match(src, /tubSteam/);
});
