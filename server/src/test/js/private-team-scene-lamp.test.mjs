import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const { privateTeamSceneLighting, PRIVATE_TEAM_TRAY_SURFACE_Y, PRIVATE_TEAM_WATER_Y } =
  await loadPublicModule('private-team-scene.js');

test('privateTeamSceneLighting 夜晚暖光檯燈', () => {
  const night = privateTeamSceneLighting('night');
  assert.ok(night.point > 0);
  assert.ok(night.spot > 0);
  assert.ok(night.spot < 7);
  assert.ok(night.spotPenumbra >= 0.65);
  assert.ok(night.haloOpacity > 0);
  assert.ok(night.shadeEmissive > 0);
  assert.ok(night.bulbOpacity > 0);
  assert.ok(night.amb < 0.5);
  assert.ok(night.dir < 0.5);
});

test('首頁秘書場景已移除名牌、泡泡錨點下移', () => {
  const src = sceneSrc;
  assert.doesNotMatch(src, /labelTexture/);
  assert.doesNotMatch(src, /namePlate|nameStand/);
  assert.match(src, /bubbleFloatMin = 1\.68/);
  assert.doesNotMatch(src, /bubbleFloatMin = 2\.2/);
});

test('下沉浴缸、托盤與單螢幕支架', () => {
  assert.ok(PRIVATE_TEAM_TRAY_SURFACE_Y > 0.45 && PRIVATE_TEAM_TRAY_SURFACE_Y < 0.52);
  assert.ok(PRIVATE_TEAM_WATER_Y < PRIVATE_TEAM_TRAY_SURFACE_Y);
  assert.ok(PRIVATE_TEAM_WATER_Y > 0.4 && PRIVATE_TEAM_WATER_Y < 0.5);
  const src = sceneSrc;
  assert.match(src, /bathtubWater/);
  assert.match(src, /bathtubRim/);
  assert.match(src, /bathtubFront/);
  assert.match(src, /bathtubInnerFront/);
  assert.match(src, /keyboardTray/);
  assert.match(src, /addMonitor\(root, monitorLook, -0\.96, 0\.94, 0\.98, 0\.02\)/);
  assert.match(src, /pickRole = 'monitor'/);
  assert.match(src, /clickables\.push\(monitors\[mi\]\.group\)/);
  assert.match(src, /updatePointerHover/);
  assert.match(src, /monitorHighlightActive/);
  assert.equal((src.match(/addMonitor\(root, monitorLook/g) || []).length, 1);
  assert.doesNotMatch(src, /addMonitor\(root, monitorLook, -0\.68/);
  assert.match(src, /basePlate/);
  assert.match(src, /bathPillow/);
  assert.doesNotMatch(src, /addBathPillow\(secretarySprite\)/);
  assert.match(src, /addBathPillow\(secretaryRoot\)/);
  assert.match(src, /applyUnderwaterTint/);
  assert.doesNotMatch(src, /rug-weave/);
  assert.doesNotMatch(src, /rugEdge/);
  assert.doesNotMatch(src, /remove_secretary_chair_for_bathtub/);
  assert.match(src, /tubSteam/);
});
