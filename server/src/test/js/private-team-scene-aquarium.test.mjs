import assert from 'node:assert/strict';
import test from 'node:test';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';
import { loadPublicModule } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

const {
  PRIVATE_TEAM_TUB_RIM_TOP_Y,
  PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y,
  PRIVATE_TEAM_PITCH_ACCEPTANCE_DOWN_RAD,
  PRIVATE_TEAM_AQUARIUM_LAZY_PITCH_RAD,
  PRIVATE_TEAM_AQUARIUM_LAZY_CAMERA_Y,
  PRIVATE_TEAM_AQUARIUM_UNDER_GLOW,
  PRIVATE_TEAM_AQUARIUM_GLASS_OPACITY,
  shouldLazyBuildFloorAquarium,
  privateTeamBathWaterSurfaceOpacity
} = await loadPublicModule('private-team-scene-aquarium.js');

test('浴缸下沉與海底水族箱懶建立', () => {
  assert.ok(PRIVATE_TEAM_TUB_RIM_TOP_Y > 0.1 && PRIVATE_TEAM_TUB_RIM_TOP_Y < 0.16);
  assert.ok(PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y < -0.5);
  assert.equal(shouldLazyBuildFloorAquarium(0.1, 0), true);
  assert.equal(shouldLazyBuildFloorAquarium(2, 0), false);
  assert.equal(shouldLazyBuildFloorAquarium(1.5, -0.4), true);
  assert.equal(shouldLazyBuildFloorAquarium(2.4, -0.2), false);
  assert.ok(PRIVATE_TEAM_AQUARIUM_LAZY_CAMERA_Y > 1.2);
  assert.ok(PRIVATE_TEAM_AQUARIUM_LAZY_PITCH_RAD > -0.4 && PRIVATE_TEAM_AQUARIUM_LAZY_PITCH_RAD < -0.2);
  assert.ok(PRIVATE_TEAM_AQUARIUM_UNDER_GLOW >= 1);
  assert.ok(PRIVATE_TEAM_AQUARIUM_GLASS_OPACITY >= 0.3);
  const atAcceptance = privateTeamBathWaterSurfaceOpacity(-PRIVATE_TEAM_PITCH_ACCEPTANCE_DOWN_RAD);
  assert.ok(atAcceptance <= 0.28, '45° 俯視時水面應夠淡以露出腿部');
  assert.ok(privateTeamBathWaterSurfaceOpacity(0) > atAcceptance);
  assert.match(sceneSrc, /applyTubFloorAquariumPeek/);
  assert.match(sceneSrc, /aquariumUnderGlow/);
  assert.match(sceneSrc, /aquariumUnderHemi/);
  assert.match(sceneSrc, /privateTeamBathWaterSurfaceOpacity/);
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