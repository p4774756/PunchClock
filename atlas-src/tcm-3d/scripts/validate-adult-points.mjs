#!/usr/bin/env node
import assert from 'node:assert/strict';
import {existsSync,readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {spawnSync} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import * as T from 'three';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {VRMHumanBoneName} from '@pixiv/three-vrm-core';
import {applyAdultStandingPose} from '../app/adult-rest-pose.ts';

const root=join(dirname(fileURLToPath(import.meta.url)),'..');
const adultPath=join(root,'app/adult-points.json');
const bakePath=join(root,'scripts/bake-anime-points.mjs');
const glbPath=join(root,'public/models/anime-adult.glb');

assert(existsSync(adultPath),'adult-points.json must exist');
assert(existsSync(glbPath),'anime-adult.glb must be in repo for bake validation');

const adult=JSON.parse(readFileSync(adultPath,'utf8'));
const anime=JSON.parse(readFileSync(join(root,'app/anime-points.json'),'utf8'));

assert.equal(adult.model,'anime-adult.glb');
assert.equal(adult.faceAxis,'-Z');
assert.ok(adult.points.length>=83,`adult expects full baked set, got ${adult.points.length}`);
assert.equal(adult.points.length,anime.points.length,'adult and assistant should bake the same catalog codes');

const headNeck=adult.points.filter(p=>p.set==='head-neck-21');
const trunk=adult.points.filter(p=>p.set==='trunk-30');
assert.ok(headNeck.length>=21,`head-neck-21 on adult: ${headNeck.length}`);
assert.ok(trunk.length>=20,`trunk-30 on adult: ${trunk.length}`);

const charMode=readFileSync(join(root,'app/character-mode.ts'),'utf8');
const avatarsSrc=readFileSync(join(root,'app/avatars.ts'),'utf8');
const sceneSrc=readFileSync(join(root,'app/character-scene.tsx'),'utf8');
assert.match(charMode,/ADULT_POINTS/, 'character-mode exports ADULT_POINTS');
assert.match(avatarsSrc,/points:ADULT_POINTS/, 'adult avatar wires ADULT_POINTS');
assert.match(sceneSrc,/syncResolvedPoints/, 'adult acupoints must follow bones after standing pose');
const overlaySrc=readFileSync(join(root,'app/teaching-overlay.ts'),'utf8');
assert.match(overlaySrc,/getWorldPosition/, 'focus camera must follow marker world positions');
assert.match(sceneSrc,/skipSkinRaycastRefine:avatar\.id==='adult'/, 'adult must skip skin raycast re-snap');

if(!process.env.CI&&!process.env.SKIP_ADULT_BAKE_DRY){
 const dry=spawnSync(process.execPath,[bakePath,'--dry-run','--model','public/models/anime-adult.glb','--output','app/adult-points.json','--codes','GV20'],{
  cwd:root,encoding:'utf8',timeout:120_000,
 });
 if(dry.status===0){
  const after=JSON.parse(readFileSync(adultPath,'utf8'));
  assert.equal(after.points.length,adult.points.length,'dry-run adult bake must not shrink JSON');
 }else if(dry.error?.code==='ENOENT'||/Cannot find module/.test(dry.stderr??'')){
  console.log('adult bake dry-run skipped (three unavailable)');
 }else{
  assert.equal(dry.status,0,dry.stderr||dry.stdout);
 }
}

globalThis.self = globalThis;
globalThis.URL = URL;
globalThis.webkitURL = URL;
if (!globalThis.document) {
  globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
}

const BONE_PRIORITY = [
  VRMHumanBoneName.LeftHand, VRMHumanBoneName.RightHand,
  VRMHumanBoneName.LeftLowerArm, VRMHumanBoneName.RightLowerArm,
  VRMHumanBoneName.LeftUpperArm, VRMHumanBoneName.RightUpperArm,
  VRMHumanBoneName.LeftShoulder, VRMHumanBoneName.RightShoulder,
  VRMHumanBoneName.Head, VRMHumanBoneName.Neck, VRMHumanBoneName.UpperChest,
  VRMHumanBoneName.Chest, VRMHumanBoneName.Spine, VRMHumanBoneName.Hips,
  VRMHumanBoneName.LeftUpperLeg, VRMHumanBoneName.RightUpperLeg,
  VRMHumanBoneName.LeftLowerLeg, VRMHumanBoneName.RightLowerLeg,
  VRMHumanBoneName.LeftFoot, VRMHumanBoneName.RightFoot,
];

const pointByCode = new Map(adult.points.map((p) => [p.code, p]));
const avatarPointTarget = (code, side) => {
  const p = pointByCode.get(code);
  if (!p) return null;
  return {
    position: [p.midline ? 0 : -p.position[0] * side, p.position[1], p.position[2]],
    normal: [p.midline ? p.normal[0] : -p.normal[0] * side, p.normal[1], p.normal[2]],
  };
};

const buildPointBindings = (vrm, characterRoot) => {
  vrm.humanoid.update();
  characterRoot.updateMatrixWorld(true);
  vrm.scene.updateMatrixWorld(true);
  const worldToRoot = new T.Matrix4();
  const table = new Map();
  const seedWorld = new T.Vector3();
  const bonePos = new T.Vector3();
  const _inv = new T.Matrix4();
  const _n = new T.Vector3();
  for (const point of adult.points) {
    for (const side of point.midline ? [1] : [1, -1]) {
      const seed = avatarPointTarget(point.code, side);
      if (!seed) continue;
      seedWorld.fromArray(seed.position).applyMatrix4(characterRoot.matrixWorld);
      let best = null;
      let bestD = Infinity;
      for (const name of BONE_PRIORITY) {
        const node = vrm.humanoid.getNormalizedBoneNode(name);
        if (!node) continue;
        node.getWorldPosition(bonePos);
        const d = bonePos.distanceToSquared(seedWorld);
        if (d < bestD) {
          bestD = d;
          best = name;
        }
      }
      if (!best) continue;
      const boneNode = vrm.humanoid.getNormalizedBoneNode(best);
      boneNode.updateMatrixWorld(true);
      _inv.copy(boneNode.matrixWorld).invert();
      const localPos = seedWorld.clone().applyMatrix4(_inv);
      _n.fromArray(seed.normal).transformDirection(characterRoot.matrixWorld).normalize();
      const localNormal = _n.clone().transformDirection(_inv).normalize();
      table.set(`${point.code}:${side}`, {bone: best, localPos, localNormal});
    }
  }
  const syncSkeleton = () => {
    vrm.humanoid.update();
    characterRoot.updateMatrixWorld(true);
    vrm.scene.updateMatrixWorld(true);
  };
  const resolve = (code, side) => {
    const bind = table.get(`${code}:${side}`);
    if (!bind) return avatarPointTarget(code, side);
    const node = vrm.humanoid.getNormalizedBoneNode(bind.bone);
    if (!node) return avatarPointTarget(code, side);
    syncSkeleton();
    node.updateMatrixWorld(true);
    const pos = bind.localPos.clone().applyMatrix4(node.matrixWorld);
    worldToRoot.copy(characterRoot.matrixWorld).invert();
    pos.applyMatrix4(worldToRoot);
    return {
      position: pos.toArray(),
      normal: bind.localNormal.clone().transformDirection(node.matrixWorld).transformDirection(worldToRoot).normalize().toArray(),
    };
  };
  return {resolve, refresh: syncSkeleton};
};

const loader = new GLTFLoader();
loader.register((parser) => new VRMLoaderPlugin(parser));
const glbBuf = readFileSync(glbPath);
const gltf = await new Promise((resolve, reject) => {
  loader.parse(glbBuf.buffer, '', resolve, reject);
});
const vrm = gltf.userData.vrm;
assert.ok(vrm, 'adult glb must expose VRM');
VRMUtils.removeUnnecessaryVertices(gltf.scene);
VRMUtils.combineSkeletons(gltf.scene);
const characterRoot = new T.Group();
characterRoot.add(vrm.scene);
const bindings = buildPointBindings(vrm, characterRoot);
applyAdultStandingPose(vrm);
vrm.update(0);
characterRoot.updateMatrixWorld(true);
bindings.refresh();

const resolveY = (code, side = 1) => {
  const target = bindings.resolve(code, side);
  assert.ok(target, `${code} must resolve after standing pose`);
  return target.position[1];
};
const li15y = resolveY('LI15');
const armBelowShoulderMin = 0.15;
for (const code of ['LU5', 'LU9', 'LI5', 'LI11', 'HT7']) {
  const y = resolveY(code);
  assert.ok(
    y < li15y - armBelowShoulderMin,
    `${code} y=${y.toFixed(3)} must be clearly below LI15 y=${li15y.toFixed(3)} after standing pose`,
  );
}

const SKIN = /Body_00_SKIN|Face_00_SKIN/i;
const skinMeshes = [];
vrm.scene.traverse((obj) => {
  if (!(obj instanceof T.SkinnedMesh)) return;
  const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
  const label = `${mats.map((m) => m?.name || '').join(' ')} ${obj.name || ''}`;
  if (SKIN.test(label)) skinMeshes.push(obj);
});
assert.ok(skinMeshes.length >= 1, 'arm skin meshes expected');
const ray = new T.Raycaster();
for (const code of ['LU5', 'LI5']) {
  const target = bindings.resolve(code, 1);
  assert.ok(target, code);
  const pos = new T.Vector3().fromArray(target.position);
  const n = new T.Vector3().fromArray(target.normal).normalize();
  ray.set(pos.clone().addScaledVector(n, 0.02), n.clone().negate());
  ray.far = 0.12;
  const hit = ray.intersectObjects(skinMeshes, false)[0];
  assert.ok(hit && hit.point.distanceTo(pos) < 0.04, `${code} must sit near arm skin, not T-pose air`);
}

applyAdultStandingPose(vrm);
vrm.update(0);
const prodBindings = buildPointBindings(vrm, characterRoot);
prodBindings.refresh();
const meshBox = new T.Box3().setFromObject(vrm.scene);
const pointBox = new T.Box3();
const world = new T.Vector3();
let boundCount = 0;
for (const point of adult.points) {
  for (const side of point.midline ? [1] : [1, -1]) {
    const target = prodBindings.resolve(point.code, side);
    if (!target) continue;
    boundCount++;
    world.fromArray(target.position);
    characterRoot.localToWorld(world);
    pointBox.expandByPoint(world);
  }
}
assert.ok(boundCount >= adult.points.length, `expected bound sides, got ${boundCount}`);
assert.ok(meshBox.intersectsBox(pointBox), 'resolved acupoints must overlap adult mesh bounds');
const meshH = meshBox.max.y - meshBox.min.y;
const pointH = pointBox.max.y - pointBox.min.y;
assert.ok(pointH > meshH * 0.35, `acupoint span too collapsed: ${pointH.toFixed(3)}`);
assert.ok(
  pointBox.min.y > meshBox.min.y - 0.12,
  `acupoints must not shrink to feet (point min y=${pointBox.min.y.toFixed(3)})`,
);
assert.ok(
  pointBox.max.y <= meshBox.max.y + 0.08 && pointBox.min.y >= meshBox.min.y - 0.08,
  'acupoint AABB must track standing mesh, not origin',
);

const focusWorldY = (code, side = 1) => {
  const target = prodBindings.resolve(code, side);
  assert.ok(target, `${code} must resolve for focus chain`);
  const aim = new T.Vector3().fromArray(target.position);
  characterRoot.localToWorld(aim);
  return aim.y;
};
const gvY = focusWorldY('GV24+', 1);
const lu5Y = focusWorldY('LU5', 1);
assert.ok(gvY > meshBox.min.y + meshH * 0.72, `GV24+ focus y=${gvY.toFixed(3)} must sit in head band`);
assert.ok(lu5Y > meshBox.min.y + meshH * 0.42, `LU5 focus y=${lu5Y.toFixed(3)} must be above waist, not feet`);
assert.ok(lu5Y < gvY - 0.08, `LU5 focus y=${lu5Y.toFixed(3)} must sit below GV24+ y=${gvY.toFixed(3)}`);

console.log(`adult-points checks passed (${adult.points.length} codes).`);