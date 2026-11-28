import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

test('休憩區家具名稱與秘書沙發錨點不變', async () => {
  const {
    SECRETARY_SOFA_POSITION,
    SECRETARY_SHADOW_SOFA,
    SECRETARY_BUBBLE_SOFA,
    vinylShouldSpin
  } = await loadPublicModule('private-team-scene-lounge.js');
  assert.deepEqual(SECRETARY_SOFA_POSITION, { x: 1.05, y: 0, z: -0.1 });
  assert.deepEqual(SECRETARY_SHADOW_SOFA, { x: -0.04, y: 0.072, z: 0.18 });
  assert.deepEqual(SECRETARY_BUBBLE_SOFA, { x: 1.18, z: 0.35 });
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