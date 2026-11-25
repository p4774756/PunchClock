import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const THREE = await loadPublicModule('vendor/three.module.js');
const {
  BRONZE_POLISHED_GOLD,
  BRONZE_FIGURE_NATIVE_HEIGHT,
  BRONZE_ASSEMBLY_NATIVE_HEIGHT,
  BRONZE_NAMEPLATE_OFFSET_Y,
  bronzeAssemblyDisplayHeight,
  bronzeBodyMaterialFrom,
  bronzeFigureNativeHeightFromModel,
  bronzeScaleForFigureHeight,
  coerceBronzeHumanMaterials,
  isBronzeBodyMaterialName,
  isBronzePedestalMesh,
  shouldSkipBronzeMaterialCoerce
} = await loadPublicModule('private-team-scene.js');

function mesh(name, mat) {
  return { isMesh: true, name, material: mat };
}

function makeRoot(nodes) {
  return {
    traverse(fn) {
      nodes.forEach((n) => fn(n));
    }
  };
}

test('銅人縮放以不含台座的人像高對齊秘書立繪 1.42', () => {
  const scale = bronzeScaleForFigureHeight(BRONZE_FIGURE_NATIVE_HEIGHT);
  assert.ok(Math.abs(scale - 1.42 / 1.728) < 0.002);
  const assemblyH = bronzeAssemblyDisplayHeight();
  assert.ok(Math.abs(assemblyH - 1.578) < 0.02);
  const nameplateY = assemblyH + BRONZE_NAMEPLATE_OFFSET_Y;
  assert.ok(Math.abs(nameplateY - 1.658) < 0.02);
});

test('bronzeFigureNativeHeightFromModel 略過 pedestal', () => {
  const pedestalGeo = new THREE.BoxGeometry(1, 0.192, 1);
  const pedestal = new THREE.Mesh(pedestalGeo, new THREE.MeshStandardMaterial({ name: 'pedestal_metal' }));
  pedestal.name = 'pedestal_base';
  pedestal.position.y = 0.096;
  const bodyGeo = new THREE.BoxGeometry(1, 1.728, 1);
  const body = new THREE.Mesh(bodyGeo, new THREE.MeshStandardMaterial({ name: 'bronze_body' }));
  body.name = 'bronze_body';
  body.position.y = 0.192 + 1.728 / 2;
  const model = new THREE.Group();
  model.add(pedestal, body);
  const h = bronzeFigureNativeHeightFromModel(model);
  assert.ok(Math.abs(h - 1.728) < 0.02);
  assert.equal(isBronzePedestalMesh('pedestal_top', 'bronze'), true);
  assert.equal(isBronzePedestalMesh('bronze_body', 'bronze_body'), false);
});

test('首頁場景不再掛銅人展示', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /beginAtlasBronzeLoad\(\)/);
  assert.doesNotMatch(src, /atlasBronzeDisplay/);
  assert.doesNotMatch(src, /pickRole = 'atlas'/);
  assert.ok(BRONZE_ASSEMBLY_NATIVE_HEIGHT > BRONZE_FIGURE_NATIVE_HEIGHT);
});

test('isBronzeBodyMaterialName 含 bronze.001', () => {
  assert.equal(isBronzeBodyMaterialName('bronze.001'), true);
  assert.equal(isBronzeBodyMaterialName('bronze_body'), true);
  assert.equal(isBronzeBodyMaterialName('bronze_hair'), false);
});

test('shouldSkipBronzeMaterialCoerce 略過腰帶與經絡', () => {
  assert.equal(shouldSkipBronzeMaterialCoerce('sash_red', ''), true);
  assert.equal(shouldSkipBronzeMaterialCoerce('meridian_dark', ''), true);
  assert.equal(shouldSkipBronzeMaterialCoerce('bronze_hair', 'bronze_hair_shell'), false);
});

test('coerceBronzeHumanMaterials 頭髮改拋光金銅', () => {
  const body = new THREE.MeshStandardMaterial({ name: 'bronze.001', color: 0xffffff, metalness: 0.97, roughness: 0.2 });
  const hair = new THREE.MeshStandardMaterial({
    name: 'bronze_hair',
    color: new THREE.Color(0.089, 0.04, 0.012),
    metalness: 0.88,
    roughness: 0.32
  });
  const sash = new THREE.MeshStandardMaterial({ name: 'sash', color: 0x331100, metalness: 0.1, roughness: 0.8 });
  const meridian = new THREE.MeshStandardMaterial({ name: 'meridian_dark', color: 0x111111, metalness: 0.9, roughness: 0.4 });
  const root = makeRoot([
    mesh('bronze_body', body),
    mesh('bronze_hair_shell', hair),
    mesh('sash_mesh', sash),
    mesh('line', meridian)
  ]);
  assert.equal(bronzeBodyMaterialFrom(root), body);
  coerceBronzeHumanMaterials(root);
  assert.equal(hair.color.getHex(), BRONZE_POLISHED_GOLD);
  assert.ok(hair.metalness >= 0.85);
  assert.ok(hair.color.r > 0.5);
  assert.equal(sash.color.getHex(), 0x331100);
  assert.equal(meridian.color.getHex(), 0x111111);
});

test('首頁場景不再掛牆上施工進度板', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /progressTexture\(0\.62\)/);
  assert.doesNotMatch(src, /progMesh\.name = 'progressBoard'/);
  assert.doesNotMatch(src, /pickRole = 'progress'/);
});
