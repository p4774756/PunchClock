import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

test('休憩區家具名稱與秘書沙發錨點不變', async () => {
  const {
    SECRETARY_SOFA_POSITION,
    SECRETARY_SHADOW_SOFA,
    SECRETARY_BUBBLE_SOFA,
    SECRETARY_STAGE_SOFA_SCALE,
    SECRETARY_STAGE_SOFA_OFFSET,
    secretarySofaPlacementForOutfit,
    vinylShouldSpin
  } = await loadPublicModule('private-team-scene-lounge.js');
  assert.deepEqual(SECRETARY_SOFA_POSITION, { x: 1.05, y: 0, z: -0.1 });
  assert.deepEqual(SECRETARY_SHADOW_SOFA, { x: -0.04, y: 0.072, z: 0.18 });
  assert.deepEqual(SECRETARY_BUBBLE_SOFA, { x: 1.18, z: 0.35 });
  const defSofa = secretarySofaPlacementForOutfit('default');
  assert.deepEqual(defSofa.position, SECRETARY_SOFA_POSITION);
  assert.equal(defSofa.scale, 1);
  assert.equal(defSofa.bubble.x, SECRETARY_BUBBLE_SOFA.x);
  const stageSofa = secretarySofaPlacementForOutfit('stage');
  assert.equal(stageSofa.scale, SECRETARY_STAGE_SOFA_SCALE);
  assert.ok(stageSofa.scale > 0 && stageSofa.scale < 1);
  assert.equal(stageSofa.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_STAGE_SOFA_OFFSET.x);
  assert.equal(stageSofa.position.y, SECRETARY_SOFA_POSITION.y + SECRETARY_STAGE_SOFA_OFFSET.y);
  assert.equal(stageSofa.position.z, SECRETARY_SOFA_POSITION.z + SECRETARY_STAGE_SOFA_OFFSET.z);
  assert.notDeepEqual(stageSofa.position, SECRETARY_SOFA_POSITION);
  // invalid outfit falls back to default placement, never mutates the shared sofa anchor
  assert.deepEqual(secretarySofaPlacementForOutfit('nope').position, SECRETARY_SOFA_POSITION);
  assert.equal(vinylShouldSpin('待命中', false), true);
  assert.equal(vinylShouldSpin('工作中', false), false);
  assert.equal(vinylShouldSpin('休息中', true), false);
  const src = sceneSrc;
  assert.match(src, /name = '焦糖棕皮沙發'/);
  assert.match(src, /name = 'walnutSideboard'/);
  assert.match(src, /name = 'vinylTurntable'/);
  assert.match(src, /platter\.name = 'vinylDisc'/);
  assert.match(src, /tickVinylSpin/);
  assert.match(src, /platter\.rotation\.y/);
  assert.match(src, /makeLeatherGrainTexture/);
  assert.match(src, /makeWalnutWoodTexture/);
});

test('場景在沙發姿勢依服裝套用相對偏移，且不寫死沙發錨點', () => {
  const src = sceneSrc;
  assert.match(src, /secretarySofaPlacementForOutfit\(secretaryOutfitId\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(sofaPlace\.scale, sofaPlace\.scale, sofaPlace\.scale\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(1, 1, 1\)/);
  assert.doesNotMatch(src, /SECRETARY_STAGE_SOFA_POSITION/);
});
