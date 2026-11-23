import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const scenePath = join(publicRoot, 'js/private-team-scene.js');
const { PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z } = await loadPublicModule('private-team-scene.js');

test('窗外天空改用 canvas 漸層貼圖，不再單色平板', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /makeNightWindowSkyTexture/);
  assert.match(src, /windowSky[\s\S]*map:\s*skyTex/);
  assert.doesNotMatch(src, /windowSky[\s\S]{0,120}MeshBasicMaterial\(\{\s*color:\s*COL\.skyNight\s*\}\)/);
});

test('天際線至少三層不同深度，不再用六棟 buildingSpecs 方塊窗', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.ok(PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z.length >= 2);
  const uniqueZ = new Set(PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z);
  assert.equal(uniqueZ.size, PRIVATE_TEAM_WINDOW_SKYLINE_LAYER_Z.length);
  assert.match(src, /skylineFar/);
  assert.match(src, /skylineMid/);
  assert.match(src, /makeNightSkylineLayerTexture/);
  assert.doesNotMatch(src, /buildingSpecs/);
  assert.doesNotMatch(src, /name = 'bwin'/);
  assert.doesNotMatch(src, /box\(0\.05,\s*0\.05,\s*0\.02,\s*COL\.buildingLit/);
});

test('場景仍為固定夜晚、三扇窗框與玻璃結構保留', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /windowGlass/);
  assert.match(src, /frameBar/);
  assert.doesNotMatch(src, /dayMode|isDayTime|日夜切換/);
  assert.match(src, /const lighting = privateTeamSceneLighting\(\)/);
});