#!/usr/bin/env node
/** Upper-limb slices and the BodyParts3D credit Z-Anatomy still requires. */
import {gunzipSync} from 'node:zlib';
import {readFileSync} from 'node:fs';
import {join, dirname} from 'node:path';
import {fileURLToPath} from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const CREDIT = 'BodyParts3D - The Database Center for Life Science - CC-BY-SA 2.1 Japan';
const texts = {
 'public/ATTRIBUTION.md': readFileSync(join(root, 'public/ATTRIBUTION.md'), 'utf8'),
 'app/page.tsx': readFileSync(join(root, 'app/page.tsx'), 'utf8'),
 'app/muscle-studio-shell.tsx': readFileSync(join(root, 'app/muscle-studio-shell.tsx'), 'utf8'),
};
for (const [name, text] of Object.entries(texts)) {
 if (!text.includes(CREDIT)) throw new Error(`${name} is missing the BodyParts3D CC-BY-SA 2.1 Japan credit`);
}

const scene = readFileSync(join(root, 'app/muscle-studio-scene.tsx'), 'utf8');
const pointAt = scene.indexOf('point.distance <= muscle.distance');
const musclePick = scene.indexOf('onMusclePick({meshName');
if (pointAt < 0 || musclePick < 0 || pointAt > musclePick) {
 throw new Error('an acupoint hit must be chosen before a muscle pick');
}

function gltfOf(file) {
 const buf = gunzipSync(readFileSync(join(root, 'public/models/anatomy', file)));
 const jsonLen = buf.readUInt32LE(12);
 const json = JSON.parse(buf.subarray(20, 20 + jsonLen).toString('utf8'));
 const logical = json.buffers?.[0]?.byteLength ?? 0;
 for (const mesh of json.meshes || []) {
  for (const prim of mesh.primitives || []) {
   const draco = prim.extensions?.KHR_draco_mesh_compression;
   const view = draco ? json.bufferViews?.[draco.bufferView] : null;
   if (!view || (view.byteOffset || 0) + view.byteLength > logical) {
    throw new Error(`${file} has a broken Draco buffer on ${mesh.name}`);
   }
  }
 }
 return json;
}

function nodesOf(file) {
 return (gltfOf(file).nodes || []).map(n => n.name || '');
}

const armBone = nodesOf('arm-skeleton.glb.gz');
const armSup = nodesOf('arm-muscle-superficial.glb.gz');
const armDeep = nodesOf('arm-muscle-deep.glb.gz');
const headBone = nodesOf('head-neck-skeleton.glb.gz');
const trunkBone = nodesOf('trunk-skeleton.glb.gz');
const headSup = nodesOf('head-neck-muscle-superficial.glb.gz');

function has(names, pattern, label) {
 if (!names.some(n => pattern.test(n))) throw new Error(`${label} missing ${pattern}`);
}
function lacks(names, pattern, label) {
 const hit = names.find(n => pattern.test(n));
 if (hit) throw new Error(`${label} still contains ${hit}`);
}

has(armBone, /^Humerus\.[lr]$/, 'arm-skeleton');
has(armBone, /^Radius\.[lr]$/, 'arm-skeleton');
has(armBone, /^Ulna\.[lr]$/, 'arm-skeleton');
if (armBone.length < 10) throw new Error(`arm-skeleton has only ${armBone.length} nodes`);
has(armSup, /deltoid muscle/i, 'arm-muscle-superficial');
has(armSup, /biceps brachii/i, 'arm-muscle-superficial');
has(armDeep, /Brachialis muscle/, 'arm-muscle-deep');
if (armDeep.length < 4) throw new Error('arm-muscle-deep is not a usable muscle slice');
lacks(headBone, /^Humerus\./, 'head-neck-skeleton');
lacks(trunkBone, /^Radius\./, 'trunk-skeleton');
lacks(trunkBone, /^Ulna\./, 'trunk-skeleton');
lacks(headSup, /deltoid muscle|biceps brachii/i, 'head-neck-muscle-superficial');

const manifest = JSON.parse(readFileSync(join(root, 'data/anatomy-manifest.json'), 'utf8'));
if (!manifest.parts.some(p => p.id === 'arm-muscle-superficial')) {
 throw new Error('manifest is missing arm-muscle-superficial');
}
if (manifest.totalGzipBytes > manifest.limits.totalMax) throw new Error('anatomy gzip total over budget');
if (manifest.initialGzipBytes > manifest.limits.firstBootMax) throw new Error('anatomy first-open gzip over budget');
const initialSum = manifest.parts
 .filter(p => manifest.initialLoad.includes(p.id))
 .reduce((n, p) => n + p.bytes, 0);
if (initialSum !== manifest.initialGzipBytes) {
 throw new Error(`initialGzipBytes ${manifest.initialGzipBytes} != parts ${initialSum}`);
}
for (const p of manifest.parts) {
 const inFirstOpen = p.layer === 'skeleton' || p.layer === 'muscle-superficial';
 if (manifest.initialLoad.includes(p.id) !== inFirstOpen) {
  throw new Error(`${p.id} should ${inFirstOpen ? '' : 'not '}be in the first-open set`);
 }
}
if (!scene.includes('anatomyInitialPartIds()')) {
 throw new Error('muscle studio must gate the first open on anatomyInitialPartIds()');
}
const cardClear = scene.indexOf('onProgress(100)');
const laterLoop = scene.indexOf('for (const part of laterParts)');
if (cardClear < 0 || laterLoop < 0 || cardClear > laterLoop) {
 throw new Error('loading card must clear before deep anatomy parts download');
}

console.log(`Upper-limb slices and BodyParts3D credit ok (arm skeleton nodes ${armBone.length}).`);
