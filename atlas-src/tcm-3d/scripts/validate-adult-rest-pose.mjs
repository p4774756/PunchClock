#!/usr/bin/env node
/** Adult standing pose: arms down; wrist bones below shoulder height. */
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
assert.match(sceneSrc, /buildAdultPointBindings/, 'character scene must bind adult acupoints to bones');

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
const model = vrm.scene;
const rootGroup = new T.Group();
rootGroup.add(model);
applyAdultStandingPose(vrm);
vrm.update(0);
rootGroup.updateMatrixWorld(true);

const shoulder = vrm.humanoid.getNormalizedBoneNode(VRMHumanBoneName.LeftUpperArm);
const hand = vrm.humanoid.getNormalizedBoneNode(VRMHumanBoneName.LeftHand);
assert.ok(shoulder && hand, 'arm bones present');
const sh = new T.Vector3();
const hw = new T.Vector3();
shoulder.getWorldPosition(sh);
hand.getWorldPosition(hw);
assert.ok(hw.y < sh.y - 0.12, `hand y=${hw.y.toFixed(3)} should be well below shoulder y=${sh.y.toFixed(3)}`);

const upper = vrm.humanoid.getRawBoneNode('J_Bip_L_UpperArm') ?? vrm.humanoid.getRawBoneNode(VRMHumanBoneName.LeftUpperArm);
if (upper) {
  const handBone = vrm.humanoid.getRawBoneNode('J_Bip_L_Hand');
  if (handBone) {
    const h0 = new T.Vector3();
    const h1 = new T.Vector3();
    upper.getWorldPosition(h0);
    handBone.getWorldPosition(h1);
    assert.ok(h1.y < h0.y - 0.05, 'raw hand should not stay at T-pose shoulder height');
  }
}

console.log('adult rest pose checks passed.');