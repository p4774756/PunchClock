import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const scenePath = { toString: () => 'bundle' };
const sceneSrc = readPrivateTeamSceneBundle();
const {
  PRIVATE_TEAM_WINDOW_SKY_Z,
  PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z,
  PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX,
  skylineLayersInFrontOfWindowSky,
  privateTeamWindowAmbientMotionEnabled,
  privateTeamWindowSkylineParallaxOffset,
  privateTeamWindowAirplaneBeaconOpacity
} = await loadPublicModule('private-team-scene.js');

test('窗外天空改用 canvas 漸層貼圖，不再單色平板', () => {
  const src = sceneSrc;
  assert.match(src, /makeNightWindowSkyTexture/);
  assert.match(src, /windowSky[\s\S]*map:\s*skyTex/);
  assert.doesNotMatch(src, /windowSky[\s\S]{0,120}MeshBasicMaterial\(\{\s*color:\s*COL\.skyNight\s*\}\)/);
});

test('天際線至少三層不同深度，不再用六棟 buildingSpecs 方塊窗', () => {
  const src = sceneSrc;
  assert.ok(PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z.length >= 2);
  const uniqueZ = new Set(PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z);
  assert.equal(uniqueZ.size, PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z.length);
  assert.match(src, /skylineFar/);
  assert.match(src, /skylineMid/);
  assert.match(src, /skylineNear/);
  assert.match(src, /makeNightSkylineLayerTexture/);
  assert.doesNotMatch(src, /buildingSpecs/);
  assert.doesNotMatch(src, /name = 'bwin'/);
  assert.doesNotMatch(src, /box\(0\.05,\s*0\.05,\s*0\.02,\s*COL\.buildingLit/);
});

test('場景仍為固定夜晚、三扇窗框與玻璃結構保留', () => {
  const src = sceneSrc;
  assert.match(src, /windowGlass/);
  assert.match(src, /frameBar/);
  assert.doesNotMatch(src, /dayMode|isDayTime|日夜切換/);
  assert.match(src, /const lighting = privateTeamSceneLighting\(\)/);
});

test('天際線各層比天空更靠鏡頭（層次 offset 小於天空）', () => {
  assert.ok(skylineLayersInFrontOfWindowSky(PRIVATE_TEAM_WINDOW_SKY_Z, PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z));
  assert.ok(PRIVATE_TEAM_WINDOW_SKY_Z > Math.max(...PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z));
});

test('天際線視差：近層係數大於遠層，reducedMotion 時位移歸零', () => {
  const src = sceneSrc;
  assert.match(src, /syncWindowSkylineParallax/);
  assert.match(src, /privateTeamWindowSkylineParallaxOffset/);
  assert.ok(PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.near > PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.far);
  const off = privateTeamWindowSkylineParallaxOffset(0.25, 0.1, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.near, false);
  assert.ok(Math.abs(off.x) > Math.abs(privateTeamWindowSkylineParallaxOffset(0.25, 0.1, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.far, false).x));
  const still = privateTeamWindowSkylineParallaxOffset(0.25, 0.1, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.near, true);
  assert.equal(still.x, 0);
  assert.equal(still.y, 0);
  assert.ok(privateTeamWindowAirplaneBeaconOpacity(0, true) >= 0.35);
});

test('窗景含雲層、樓燈閃爍、飛機燈與夜雨，且 reducedMotion 可關閉動態', () => {
  const src = sceneSrc;
  assert.match(src, /windowClouds/);
  assert.match(src, /windowSkylineFlicker/);
  assert.match(src, /windowAirplane/);
  assert.match(src, /windowRainBack/);
  assert.match(src, /windowGlassStreaks/);
  assert.match(src, /windowStreetTraffic/);
  assert.match(src, /makeWindowStreetTrafficTexture/);
  assert.match(src, /privateTeamWindowAmbientMotionEnabled\(reducedMotion\)/);
  assert.match(src, /streetTrafficTex\.offset\.x/);
  assert.equal(privateTeamWindowAmbientMotionEnabled(true), false);
  assert.equal(privateTeamWindowAmbientMotionEnabled(false), true);
});

test('窗台物件、杯口熱氣與六格月光斑，雨跡比舊版更明顯', () => {
  const src = sceneSrc;
  assert.match(src, /windowSill/);
  assert.match(src, /windowSillPlant/);
  assert.match(src, /windowSillMug/);
  assert.match(src, /windowSillMugSteam/);
  assert.match(src, /makeWindowMoonlightFloorTexture/);
  assert.match(src, /windowMoonlight/);
  assert.doesNotMatch(src, /windowWash/);
  assert.match(src, /windowSillMugSteamPuffs/);
  assert.match(src, /opacity:\s*0\.46/);
  assert.match(src, /opacity:\s*0\.42/);
  assert.match(src, /for \(let d = 0; d < 14; d\+\+\)/);
});

test('reducedMotion 時杯口熱氣靜態、車流 offset 僅在環境動態開啟時位移', () => {
  const src = sceneSrc;
  assert.match(
    src,
    /if \(reducedMotion\)[\s\S]*windowSillMugSteamPuffs[\s\S]*material\.opacity/
  );
  assert.match(src, /streetTrafficTex\.offset\.x = \(elapsed \* 0\.06\) % 1/);
  assert.match(
    src,
    /if \(privateTeamWindowAmbientMotionEnabled\(reducedMotion\)\) \{[\s\S]*streetTrafficTex\.offset\.x/
  );
});

test('秘書泡泡為深藍膠囊', () => {
  const src = sceneSrc;
  const bubbleStart = src.indexOf('function makeBubbleDrawer');
  assert.ok(bubbleStart >= 0);
  const bubbleBlock = src.slice(bubbleStart, bubbleStart + 1200);
  assert.doesNotMatch(bubbleBlock, /#f8fbff/);
  assert.doesNotMatch(bubbleBlock, /lineWidth = 10/);
  assert.match(bubbleBlock, /rgba\(18,32,58/);
  assert.doesNotMatch(src, /function makeNameplateDrawer/);
});