import assert from 'node:assert/strict';
import test from 'node:test';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';
import { loadPublicModule } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

const {
  PRIVATE_TEAM_TUB_RIM_TOP_Y,
  PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y,
  shouldLazyBuildFloorAquarium
} = await loadPublicModule('private-team-scene-aquarium.js');

test('浴缸下沉與海底水族箱懶建立', () => {
  assert.ok(PRIVATE_TEAM_TUB_RIM_TOP_Y > 0.1 && PRIVATE_TEAM_TUB_RIM_TOP_Y < 0.16);
  assert.ok(PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y < -0.5);
  assert.equal(shouldLazyBuildFloorAquarium(0.1, 0), true);
  assert.equal(shouldLazyBuildFloorAquarium(2, 0), false);
  assert.equal(shouldLazyBuildFloorAquarium(0.8, -0.6), true);
  assert.match(sceneSrc, /floorAquarium/);
  assert.match(sceneSrc, /aquariumWaterVolume/);
  assert.match(sceneSrc, /aquariumGlassFront/);
  assert.match(sceneSrc, /buildFloorAquarium/);
  assert.match(sceneSrc, /shouldLazyBuildFloorAquarium/);
  assert.match(sceneSrc, /secretaryBathLegs/);
  assert.match(sceneSrc, /bathLegThigh/);
  assert.match(sceneSrc, /THREE\.DoubleSide/);
  assert.doesNotMatch(sceneSrc, /bathtubFloorLip/);
  assert.doesNotMatch(sceneSrc, /bathtubFloorGroove/);
});

test('stripUnderwaterTint 不再把 onBeforeCompile 設成 null', async () => {
  const { stripUnderwaterTint } = await loadPublicModule('private-team-scene-room.js');
  const mat = { onBeforeCompile: () => {}, customProgramCacheKey: () => 'x' };
  stripUnderwaterTint(mat);
  assert.equal('onBeforeCompile' in mat, false);
});