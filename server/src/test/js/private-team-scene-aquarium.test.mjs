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
test('泡澡 3D 腿髖部釘在精靈圖底緣，俯視也不脫節且腿伸進水族箱', async () => {
  const THREE = await loadPublicModule('vendor/three.module.min.js');
  const {
    addSecretaryBathLegs,
    syncSecretaryBathLegs,
    secretaryBathLegLength,
    PRIVATE_TEAM_BATH_LEG_BOTTOM_Y,
    PRIVATE_TEAM_BATH_HIP_FROM_SPRITE_BOTTOM
  } = await loadPublicModule('private-team-scene-aquarium.js');
  const H = 1.42;
  assert.ok(PRIVATE_TEAM_BATH_LEG_BOTTOM_Y < -0.4 && PRIVATE_TEAM_BATH_LEG_BOTTOM_Y > PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y);
  assert.ok(secretaryBathLegLength(0.14) > 0.7);
  assert.ok(secretaryBathLegLength(NaN) > 0);
  const cams = [
    [0, 1.4, 4.2], // 預設側視
    [2.4, 2.6, 2.6], // 斜俯
    [0.05, 5.5, 0.62], // 幾乎正上方
    [-3, 0.3, 1.2] // 貼近水面
  ];
  for (const c of cams) {
    const parent = new THREE.Group();
    parent.position.set(0.02, 0, 0.56);
    const sprite = new THREE.Mesh(new THREE.PlaneGeometry(1.15, H), new THREE.MeshBasicMaterial());
    sprite.position.set(0, H / 2 + 0.02, 0);
    parent.add(sprite);
    addSecretaryBathLegs(parent);
    const legs = parent.getObjectByName('secretaryBathLegs');
    parent.updateMatrixWorld(true);
    sprite.lookAt(new THREE.Vector3(c[0], c[1], c[2]));
    syncSecretaryBathLegs(parent, sprite, H);
    parent.updateMatrixWorld(true);
    const hipLocalY = -H / 2 + H * PRIVATE_TEAM_BATH_HIP_FROM_SPRITE_BOTTOM;
    const expect = sprite.localToWorld(new THREE.Vector3(-0.04, hipLocalY, 0));
    const pelvis = legs.getObjectByName('bathLegPelvis');
    const pw = pelvis.getWorldPosition(new THREE.Vector3());
    assert.ok(pw.distanceTo(expect) < 0.08, '髖部與精靈底緣距離 ' + pw.distanceTo(expect) + ' @' + c);
    const foot = legs.getObjectByName('bathLegFootL').getWorldPosition(new THREE.Vector3());
    assert.ok(foot.y <= PRIVATE_TEAM_BATH_LEG_BOTTOM_Y + 0.05, '腳底應深入水族箱 y=' + foot.y);
    assert.ok(foot.y > PRIVATE_TEAM_TUB_SHELL_BOTTOM_Y, '腳不應穿出水族箱底');
  }
  assert.match(sceneSrc, /syncSecretaryBathLegs\(secretaryRoot/);
});
