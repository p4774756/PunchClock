#!/usr/bin/env node
/** Adult standing pose: arms down; wrist bones and arm acupoints below shoulder height. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname,join} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as T from 'three';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {VRMLoaderPlugin,VRMUtils} from '@pixiv/three-vrm';
import {VRMHumanBoneName} from '@pixiv/three-vrm-core';
import {applyAdultStandingPose} from '../app/adult-rest-pose.ts';

globalThis.self = globalThis;
globalThis.URL = URL;
globalThis.webkitURL = URL;
if (!globalThis.document) {
  globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
}

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const sceneSrc = readFileSync(join(root, 'app/character-scene.tsx'), 'utf8');
assert.match(sceneSrc, /applyAdultStandingPose/, 'character scene must apply adult rest pose');
assert.match(sceneSrc, /primeAdultStandingPose/, 'character scene must maintain adult rest pose each frame');
assert.match(sceneSrc, /buildAdultPointBindings/, 'character scene must bind adult acupoints to bones');

const BONE_PRIORITY = [
  VRMHumanBoneName.LeftHand, VRMHumanBoneName.RightHand,
  VRMHumanBoneName.LeftLowerArm, VRMHumanBoneName.RightLowerArm,
  VRMHumanBoneName.LeftUpperArm, VRMHumanBoneName.RightUpperArm,
  VRMHumanBoneName.LeftShoulder, VRMHumanBoneName.RightShoulder,
  VRMHumanBoneName.Head, VRMHumanBoneName.Neck, VRMHumanBoneName.UpperChest,
  VRMHumanBoneName.Chest, VRMHumanBoneName.Spine, VRMHumanBoneName.Hips,
];

const adultPoints = JSON.parse(readFileSync(join(root, 'app/adult-points.json'), 'utf8')).points;
const pointByCode = new Map(adultPoints.map((p) => [p.code, p]));

const avatarPointTarget = (code, side) => {
  const p = pointByCode.get(code);
  if (!p) return null;
  return {
    position: [p.midline ? 0 : -p.position[0] * side, p.position[1], p.position[2]],
    normal: [p.midline ? p.normal[0] : -p.normal[0] * side, p.normal[1], p.normal[2]],
  };
};

const buildPointBindings = (vrm, characterRoot) => {
  characterRoot.updateMatrixWorld(true);
  const invRoot = new T.Matrix4().copy(characterRoot.matrixWorld).invert();
  const table = new Map();
  const seedWorld = new T.Vector3();
  const bonePos = new T.Vector3();
  const _inv = new T.Matrix4();
  const _n = new T.Vector3();
  for (const point of adultPoints) {
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
      _inv.copy(boneNode.matrixWorld).invert();
      const localPos = seedWorld.clone().applyMatrix4(_inv);
      _n.fromArray(seed.normal).transformDirection(characterRoot.matrixWorld).normalize();
      const localNormal = _n.clone().transformDirection(_inv).normalize();
      table.set(`${point.code}:${side}`, {bone: best, localPos, localNormal});
    }
  }
  const resolve = (code, side) => {
    const bind = table.get(`${code}:${side}`);
    if (!bind) return avatarPointTarget(code, side);
    const node = vrm.humanoid.getNormalizedBoneNode(bind.bone);
    if (!node) return avatarPointTarget(code, side);
    node.updateMatrixWorld(true);
    const pos = bind.localPos.clone().applyMatrix4(node.matrixWorld);
    invRoot.copy(characterRoot.matrixWorld).invert();
    pos.applyMatrix4(invRoot);
    return {position: pos.toArray(), normal: bind.localNormal.clone().transformDirection(node.matrixWorld).transformDirection(invRoot).normalize().toArray()};
  };
  return {resolve, refresh: () => { vrm.scene.updateMatrixWorld(true); }};
};

const glbBuf = readFileSync(join(root, 'public/models/anime-adult.glb'));
const loader = new GLTFLoader();
loader.register(parser => new VRMLoaderPlugin(parser));
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

const shoulder = vrm.humanoid.getNormalizedBoneNode(VRMHumanBoneName.LeftUpperArm);
const hand = vrm.humanoid.getNormalizedBoneNode(VRMHumanBoneName.LeftHand);
assert.ok(shoulder && hand, 'arm bones present');
const sh = new T.Vector3();
const hw = new T.Vector3();
shoulder.getWorldPosition(sh);
hand.getWorldPosition(hw);
assert.ok(hw.y < sh.y - 0.15, `hand y=${hw.y.toFixed(3)} should be well below shoulder y=${sh.y.toFixed(3)}`);

const upper = vrm.humanoid.getRawBoneNode(VRMHumanBoneName.LeftUpperArm);
const handBone = vrm.humanoid.getRawBoneNode(VRMHumanBoneName.LeftHand);
assert.ok(upper && handBone, 'raw arm bones present');
const h0 = new T.Vector3();
const h1 = new T.Vector3();
upper.getWorldPosition(h0);
handBone.getWorldPosition(h1);
assert.ok(h1.y < h0.y - 0.15, `raw hand y=${h1.y.toFixed(3)} must sit below upper arm y=${h0.y.toFixed(3)}`);

const resolveY = (code) => {
  const target = bindings.resolve(code, 1);
  assert.ok(target, `${code} must resolve after rest pose`);
  return target.position[1];
};
const li15y = resolveY('LI15');
const armBelowShoulderMin = 0.18;
for (const code of ['LU5', 'LU9', 'LI5', 'LI11', 'HT7']) {
  const y = resolveY(code);
  assert.ok(
    y < li15y - armBelowShoulderMin,
    `${code} y=${y.toFixed(3)} must be clearly below LI15 y=${li15y.toFixed(3)}`
  );
}

console.log('adult rest pose checks passed.');