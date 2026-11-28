import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

/** 浴缸外緣半寬（private-team-scene.js TUB_HW），右緣世界 X ≈ 0.84 */
const TUB_RIGHT_EDGE_X = 0.84;

test('休憩區家具名稱與秘書沙發錨點', async () => {
  const {
    SECRETARY_SOFA_POSITION,
    SECRETARY_SHADOW_SOFA,
    SECRETARY_BUBBLE_SOFA,
    SECRETARY_STAGE_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_OFFSET,
    secretarySofaShadowLocal,
    SECRETARY_STAGE_SOFA_OFFSET,
    secretarySofaPlacementForOutfit,
    SOFA_HALF_WIDTH_X,
    SOFA_ARM_RADIUS,
    vinylShouldSpin
  } = await loadPublicModule('private-team-scene-lounge.js');
  assert.deepEqual(SECRETARY_SOFA_POSITION, { x: 1.34, y: 0, z: -0.24 });
  assert.deepEqual(SECRETARY_SHADOW_SOFA, { x: -0.03, y: 0.072, z: 0.16 });
  assert.deepEqual(SECRETARY_BUBBLE_SOFA, { x: 1.46, z: 0.26 });
  const sofaLeftX = SECRETARY_SOFA_POSITION.x - SOFA_HALF_WIDTH_X - SOFA_ARM_RADIUS;
  assert.ok(sofaLeftX > TUB_RIGHT_EDGE_X + 0.04, '沙發左緣應與浴缸右緣留有 X 向空隙');
  const defSofa = secretarySofaPlacementForOutfit('default');
  const stagePlaceForShadow = secretarySofaPlacementForOutfit('stage');
  assert.equal(defSofa.scale, SECRETARY_DEFAULT_SOFA_SCALE);
  assert.ok(defSofa.scale > 0 && defSofa.scale < 1, '預設服立繪已去背，需縮小坐上座墊');
  assert.equal(defSofa.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_DEFAULT_SOFA_OFFSET.x);
  assert.equal(defSofa.position.y, SECRETARY_SOFA_POSITION.y + SECRETARY_DEFAULT_SOFA_OFFSET.y);
  assert.equal(defSofa.position.z, SECRETARY_SOFA_POSITION.z + SECRETARY_DEFAULT_SOFA_OFFSET.z);
  const shadowLocal = secretarySofaShadowLocal(stagePlaceForShadow);
  assert.ok(Number.isFinite(shadowLocal.x) && Number.isFinite(shadowLocal.z));
  // 陰影的世界高度要貼著地毯，不能因立繪下移而沉進地板
  assert.ok(stagePlaceForShadow.position.y + shadowLocal.y * stagePlaceForShadow.scale > 0.04);
  assert.equal(defSofa.bubble.x, SECRETARY_BUBBLE_SOFA.x);
  const stageSofa = secretarySofaPlacementForOutfit('stage');
  assert.equal(stageSofa.scale, SECRETARY_STAGE_SOFA_SCALE);
  assert.ok(stageSofa.scale > 0 && stageSofa.scale < 1);
  assert.equal(stageSofa.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_STAGE_SOFA_OFFSET.x);
  assert.equal(stageSofa.position.y, SECRETARY_SOFA_POSITION.y + SECRETARY_STAGE_SOFA_OFFSET.y);
  assert.equal(stageSofa.position.z, SECRETARY_SOFA_POSITION.z + SECRETARY_STAGE_SOFA_OFFSET.z);
  assert.notDeepEqual(stageSofa.position, SECRETARY_SOFA_POSITION);
  assert.deepEqual(secretarySofaPlacementForOutfit('nope').position, defSofa.position);
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
  assert.match(src, /vinylRecordSleeve/);
  assert.match(src, /sofaRollArm/);
  assert.match(src, /sofaBackCushion/);
  assert.match(src, /sofaSeatCushion/);
  assert.match(src, /makeRugColorTexture/);
});

test('場景在沙發姿勢依服裝套用相對偏移，且不寫死沙發錨點', () => {
  const src = sceneSrc;
  assert.match(src, /secretarySofaPlacementForOutfit\(secretaryOutfitId\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(sc, sc, sc\)/);
  assert.match(src, /\[px, py, pz, scale\]\.every\(Number\.isFinite\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(1, 1, 1\)/);
  assert.doesNotMatch(src, /SECRETARY_STAGE_SOFA_POSITION/);
});

test('訪客／無效服裝的沙發擺位永遠是有限數字', async () => {
  const {
    secretarySofaPlacementForOutfit,
    SECRETARY_SOFA_POSITION,
    SECRETARY_DEFAULT_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_OFFSET
  } = await loadPublicModule(
    'private-team-scene-lounge.js'
  );
  const samples = [undefined, null, '', 'default', 'stage', 'STAGE', 'foo', 0, {}, '  stage  '];
  for (const id of samples) {
    const place = secretarySofaPlacementForOutfit(id);
    assert.equal(typeof place.scale, 'number');
    assert.ok(Number.isFinite(place.scale) && place.scale > 0, String(id) + ' scale');
    for (const axis of ['x', 'y', 'z']) {
      assert.ok(Number.isFinite(place.position[axis]), String(id) + ' pos.' + axis);
    }
    assert.ok(Number.isFinite(place.bubble.x), String(id) + ' bubble.x');
    assert.ok(Number.isFinite(place.bubble.z), String(id) + ' bubble.z');
    if (place.bubble.y != null) {
      assert.ok(Number.isFinite(place.bubble.y), String(id) + ' bubble.y');
    }
  }
  const def = secretarySofaPlacementForOutfit('default');
  const visitor = secretarySofaPlacementForOutfit(undefined);
  assert.deepEqual(visitor, def);
  assert.equal(visitor.scale, SECRETARY_DEFAULT_SOFA_SCALE);
  assert.equal(visitor.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_DEFAULT_SOFA_OFFSET.x);
  assert.deepEqual(secretarySofaPlacementForOutfit(''), def);
  assert.deepEqual(secretarySofaPlacementForOutfit('foo'), def);
});
