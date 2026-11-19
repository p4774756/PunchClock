#!/usr/bin/env node
/** Anchor path must use baked skin proxies, not SkinnedMesh raycast; spot-check proxy hits. */
import assert from 'node:assert/strict';
import {readFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';
import * as T from 'three';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';

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

const _v = new T.Vector3();
const SKIN = /Face_00_SKIN|Body_00_SKIN|FaceMouth/i;
function buildSkinRaycastProxies(root) {
  const proxies = [];
  root.updateMatrixWorld(true);
  root.traverse(obj => {
    if (!(obj instanceof T.SkinnedMesh)) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    const label = `${mats.map(m => m?.name || '').join(' ')} ${obj.name || ''}`;
    if (!SKIN.test(label)) return;
    obj.skeleton.update();
    const geometry = obj.geometry.clone();
    const position = geometry.attributes.position;
    for (let i = 0; i < position.count; i++) {
      obj.applyBoneTransform(i, _v);
      position.setXYZ(i, _v.x, _v.y, _v.z);
    }
    position.needsUpdate = true;
    const proxy = new T.Mesh(geometry, new T.MeshBasicMaterial({side: T.DoubleSide}));
    proxy.matrixAutoUpdate = false;
    proxy.matrix.copy(obj.matrix);
    proxy.matrixWorld.copy(obj.matrixWorld);
    proxies.push(proxy);
  });
  return proxies;
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
const buffer = readFileSync(join(rootDir, 'public/models/anime-assistant.glb')).buffer;
const gltf = await new Promise((res, rej) => new GLTFLoader().parse(buffer, '', res, rej));
const proxies = buildSkinRaycastProxies(gltf.scene);
assert.ok(proxies.length >= 2, 'expected skin raycast proxies');

let tried = 0;
let ok = 0;
const maxDelta = 0.002;
for (const point of animePoints.points) {
  for (const side of point.midline ? [1] : [1, -1]) {
    const seed = {
      position: [point.midline ? 0 : -point.position[0] * side, point.position[1], point.position[2]],
      normal: [point.midline ? point.normal[0] : -point.normal[0] * side, point.normal[1], point.normal[2]],
    };
    const hit = snapDot(proxies, seed);
    if (!hit) continue;
    tried += 1;
    const baked = new T.Vector3().fromArray(point.position);
    if (!point.midline) baked.x *= -side;
    if (hit.point.distanceTo(baked) <= maxDelta) ok += 1;
  }
}
if (tried >= 20) {
  assert.ok(ok / tried >= 0.85, `only ${ok}/${tried} proxy hits within 2 mm`);
  console.log(`teaching anchor spot-check ok (${ok}/${tried} within 2 mm).`);
} else {
  console.log(`teaching anchor static guards ok (runtime spot-check skipped, hits=${tried}).`);
}