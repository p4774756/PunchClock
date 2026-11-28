import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const THREE = await loadPublicModule('vendor/three.module.min.js');
const {
  PRIVATE_TEAM_FISH_SCHOOLS,
  PRIVATE_TEAM_SEAWEED_CLUMPS,
  buildFish,
  updateFish,
  buildSeaweed,
  swaySeaweed
} = await loadPublicModule('private-team-scene-aquarium.js');

function fishWorld(fish, i) {
  const m = new THREE.Matrix4();
  fish.body.getMatrixAt(i, m);
  return new THREE.Vector3().setFromMatrixPosition(m);
}

test('魚分成多群，身體／尾鰭／背鰭用 InstancedMesh，只有 3 個 draw call', () => {
  const total = PRIVATE_TEAM_FISH_SCHOOLS.reduce((n, s) => n + s.count, 0);
  assert.ok(PRIVATE_TEAM_FISH_SCHOOLS.length >= 3);
  assert.ok(PRIVATE_TEAM_FISH_SCHOOLS.filter((s) => s.count >= 4).length >= 3);
  const group = new THREE.Group();
  const fish = buildFish(group);
  assert.equal(fish.group.children.length, 3);
  for (const m of [fish.body, fish.tail, fish.fin]) {
    assert.equal(m.isInstancedMesh, true);
    assert.equal(m.count, total);
  }
});

test('同一群的魚互相靠近，會隨時間游動且不超出水族箱', () => {
  const group = new THREE.Group();
  const fish = buildFish(group);
  updateFish(fish, 0);
  const a0 = fishWorld(fish, 0);
  const a1 = fishWorld(fish, 1);
  assert.ok(a0.distanceTo(a1) < 0.9, '同群距離');
  let far = 0;
  for (let t = 1; t <= 12; t++) {
    updateFish(fish, t);
    far = Math.max(far, fishWorld(fish, 0).distanceTo(a0));
  }
  assert.ok(far > 0.5, '有在游');
  for (let t = 0; t < 120; t += 3) {
    updateFish(fish, t);
    for (let i = 0; i < fish.body.count; i++) {
      const p = fishWorld(fish, i);
      assert.ok(Math.abs(p.x) < 1.6 && Math.abs(p.z - 0.04) < 1.6, 'x/z 在箱內');
      assert.ok(p.y < -0.4 && p.y > -3.0, 'y 在水中');
    }
  }
});

test('水草多叢、高度不同；尖端擺動幅度大於根部', () => {
  const group = new THREE.Group();
  const blades = buildSeaweed(group, -3.08);
  assert.equal(blades.length, PRIVATE_TEAM_SEAWEED_CLUMPS.length * 3);
  const heights = new Set(blades.map((b) => b.userData.sway.h.toFixed(2)));
  assert.ok(heights.size >= 4);
  const b = blades[0];
  const pos = b.geometry.attributes.position;
  const base = Array.from(b.userData.sway.base);
  swaySeaweed(blades, 2.5);
  let rootMove = 0;
  let tipMove = 0;
  let tipY = -Infinity;
  for (let i = 0; i < pos.count; i++) tipY = Math.max(tipY, base[i * 3 + 1]);
  for (let i = 0; i < pos.count; i++) {
    const d = Math.hypot(pos.getX(i) - base[i * 3], pos.getZ(i) - base[i * 3 + 2]);
    if (base[i * 3 + 1] < 1e-6) rootMove = Math.max(rootMove, d);
    if (Math.abs(base[i * 3 + 1] - tipY) < 1e-6) tipMove = Math.max(tipMove, d);
  }
  assert.ok(rootMove < 1e-6, '根部固定');
  assert.ok(tipMove > 0.02, '尖端會動');
});
