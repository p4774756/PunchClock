#!/usr/bin/env node
/** Anchor path must use baked skin proxies; CI spot-check proxy vs SkinnedMesh ≤2 mm. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as T from 'three';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';
import {buildSkinRaycastProxies} from '../app/skin-raycast-proxy.ts';

globalThis.self = globalThis;
globalThis.URL = URL;
globalThis.webkitURL = URL;
if (!globalThis.document) {
  globalThis.document = {createElementNS: () => ({style: {}, setAttribute() {}, getContext() { return null; }})};
}

const rootDir = join(dirname(fileURLToPath(import.meta.url)), '..');
const overlaySrc = readFileSync(join(rootDir, 'app/teaching-overlay.ts'), 'utf8');
const sceneSrc = readFileSync(join(rootDir, 'app/character-scene.tsx'), 'utf8');

assert.match(overlaySrc, /buildSkinRaycastProxies/, 'teaching overlay must bake skin proxies');
assert.match(overlaySrc, /applyCachedAnchors/, 'teaching overlay must cache anchor snapshot');
assert.doesNotMatch(overlaySrc, /intersectObjects\(skinMeshes/, 'must not raycast SkinnedMesh for anchors');
assert.doesNotMatch(sceneSrc, /teaching\.reanchor/, 'outfit switch must not re-snap with raycast');
assert.match(sceneSrc, /focus\(launchSim,camera,controls,\{force:true\}\)/, 'deep-link load must re-apply focus after compile');

const SKIN = /Face_00_SKIN|Body_00_SKIN|FaceMouth/i;

function stripGlbForNode(buffer) {
  const view = new DataView(buffer.buffer, buffer.byteOffset, buffer.byteLength);
  if (view.getUint32(0, true) !== 0x46546c67) throw new Error('not GLB');
  const jsonLen = view.getUint32(12, true);
  const json = JSON.parse(new TextDecoder().decode(buffer.subarray(20, 20 + jsonLen)));
  json.images = [];
  json.textures = [];
  json.samplers = [];
  const dropTextures = obj => {
    if (!obj || typeof obj !== 'object') return;
    for (const key of [...Object.keys(obj)]) {
      if (/Texture$/i.test(key) || key === 'normalTexture' || key === 'occlusionTexture' || key === 'emissiveTexture') {
        delete obj[key];
        continue;
      }
      const val = obj[key];
      if (val && typeof val === 'object') dropTextures(val);
    }
  };
  for (const mat of json.materials ?? []) dropTextures(mat);
  if (json.extensions?.VRM) dropTextures(json.extensions.VRM);
  const jsonBuf = Buffer.from(JSON.stringify(json));
  const jsonPad = (4 - (jsonBuf.length % 4)) % 4;
  const binStart = 20 + jsonLen;
  const binLen = view.getUint32(binStart, true);
  const bin = buffer.subarray(binStart + 8, binStart + 8 + binLen);
  const binPad = (4 - (bin.length % 4)) % 4;
  const total = 12 + 8 + jsonBuf.length + jsonPad + 8 + bin.length + binPad;
  const out = Buffer.alloc(total);
  let o = 0;
  out.writeUInt32LE(0x46546c67, o); o += 4;
  out.writeUInt32LE(2, o); o += 4;
  out.writeUInt32LE(total, o); o += 4;
  out.writeUInt32LE(jsonBuf.length + jsonPad, o); o += 4;
  out.writeUInt32LE(0x4e4f534a, o); o += 4;
  jsonBuf.copy(out, o); o += jsonBuf.length;
  if (jsonPad) out.fill(0x20, o, o + jsonPad), o += jsonPad;
  out.writeUInt32LE(bin.length + binPad, o); o += 4;
  out.writeUInt32LE(0x004e4942, o); o += 4;
  bin.copy(out, o);
  return out;
}

function snapDot(targets, seed) {
  const ray = new T.Raycaster();
  const v = new T.Vector3().fromArray(seed.position);
  const n = new T.Vector3().fromArray(seed.normal).normalize();
  const origin = v.clone().addScaledVector(n, 0.08);
  ray.set(origin, n.clone().negate());
  ray.far = 0.2;
  let hits = ray.intersectObjects(targets, false);
  let hit = hits.find(h => h.point.distanceTo(v) < 0.08) ?? hits[0];
  if (!hit) {
    ray.set(v.clone().add(new T.Vector3(0, 0, -0.12)), new T.Vector3(0, 0, 1));
    ray.far = 0.25;
    hit = ray.intersectObjects(targets, false)[0];
  }
  return hit;
}

const animePoints = JSON.parse(readFileSync(join(rootDir, 'app/anime-points.json'), 'utf8'));
const stripped = stripGlbForNode(readFileSync(join(rootDir, 'public/models/anime-assistant.glb')));
const gltf = await new Promise((res, rej) => new GLTFLoader().parse(stripped.buffer, '', res, rej));
gltf.scene.updateMatrixWorld(true);
const skinMeshes = [];
gltf.scene.traverse(obj => {
  if (!(obj instanceof T.SkinnedMesh)) return;
  const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
  const label = `${mats.map(m => m?.name || '').join(' ')} ${obj.name || ''}`;
  if (!SKIN.test(label)) return;
  obj.skeleton.update();
  skinMeshes.push(obj);
});
const proxies = buildSkinRaycastProxies(gltf.scene, SKIN);
assert.ok(proxies.length >= 2, 'expected skin raycast proxies');
assert.ok(skinMeshes.length >= 2, 'expected skin SkinnedMeshes');

let compared = 0;
let matched = 0;
const maxDelta = 0.002;
for (const point of animePoints.points) {
  for (const side of point.midline ? [1] : [1, -1]) {
    const seed = {
      position: [point.midline ? 0 : -point.position[0] * side, point.position[1], point.position[2]],
      normal: [point.midline ? point.normal[0] : -point.normal[0] * side, point.normal[1], point.normal[2]],
    };
    const skinHit = snapDot(skinMeshes, seed);
    const proxyHit = snapDot(proxies, seed);
    assert.ok(skinHit, `skin raycast must hit ${point.code}${point.midline ? '' : side > 0 ? ' L' : ' R'}`);
    assert.ok(proxyHit, `proxy raycast must hit ${point.code}`);
    compared += 1;
    if (proxyHit.point.distanceTo(skinHit.point) <= maxDelta) matched += 1;
  }
}
assert.ok(compared >= 80, `expected spot-checks, got ${compared}`);
assert.equal(matched, compared, `${matched}/${compared} proxy vs skin hits exceed 2 mm`);
console.log(`teaching anchor spot-check ok (${compared} proxy vs skin ≤2 mm).`);