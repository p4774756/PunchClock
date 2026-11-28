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
  assert.ok(night.galleryMul >= 0.8);
  assert.ok(night.floorWash < 0.55);
});

test('美術館風嵌燈、軌道射燈與重點光圈', () => {
  const src = sceneSrc;
  assert.match(src, /galleryCeilingTrackRail/);
  assert.match(src, /galleryRecessedTub/);
  assert.match(src, /galleryTrackHeadSofa/);
  assert.match(src, /galleryAccentSpotBathtub/);
  assert.match(src, /galleryAccentSpotSofa/);
  assert.match(src, /galleryAccentSpotVinyl/);
  assert.match(src, /galleryAccentSpotPoster/);
  assert.match(src, /sofaBackIndirectWash/);
  const shadowCast = src.match(/castShadow\s*=\s*true/g);
  assert.ok(!shadowCast || shadowCast.length <= 1);
  assert.doesNotMatch(src, /galleryAccentSpot[\s\S]*castShadow\s*=\s*true/);
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
  assert.match(src, /hologramPanel/);
  // 回歸：privateTeamSeededRand 回傳函式，必須呼叫它，否則 floatSeed 為 NaN、面板位置變 NaN 而看不見
  assert.match(src, /floatSeed: privateTeamSeededRand\([^;]*\)\(\) \*/);
  assert.match(src, /hologramEmitter/);
  assert.match(src, /depthWrite: false/);
  assert.doesNotMatch(src, /basePlate/);
  assert.match(src, /bathPillow/);
  assert.doesNotMatch(src, /addBathPillow\(secretarySprite\)/);
  assert.match(src, /addBathPillow\(bathPillowTubAnchor\)/);
  assert.match(src, /applyUnderwaterTint/);
  assert.match(src, /rug-weave\.webp/);
  assert.match(src, /makeMarbleFloorTexture/);
  assert.doesNotMatch(src, /hole\.translate/);
  assert.match(src, /walnutSideboard/);
  assert.match(src, /vinylTurntable/);
  assert.doesNotMatch(src, /remove_secretary_chair_for_bathtub/);
  assert.match(src, /tubSteam/);
});
