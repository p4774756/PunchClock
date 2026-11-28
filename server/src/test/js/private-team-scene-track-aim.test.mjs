import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const THREE = await loadPublicModule('vendor/three.module.min.js');
const { trackHeadAimQuaternion } = await loadPublicModule('private-team-scene-gallery-lighting.js');
const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/js');

function aimDir(from, target) {
  const q = trackHeadAimQuaternion(new THREE.Vector3(...from), new THREE.Vector3(...target));
  return new THREE.Vector3(0, -1, 0).applyQuaternion(q);
}

test('軌道燈頭朝向不同目標時方向互不相同，且都往下', () => {
  const dirs = [
    aimDir([0.42, 2, -0.38], [-1.7, 1.2, 0]),
    aimDir([0.78, 2, -0.38], [0, 0, 0.64]),
    aimDir([1.14, 2, -0.38], [1.38, 0.38, -0.3]),
    aimDir([1.46, 2, -0.38], [0.86, 0.62, -1.3])
  ];
  for (const d of dirs) assert.ok(d.y < -0.35, '往下');
  for (let i = 0; i < dirs.length; i++) {
    for (let j = i + 1; j < dirs.length; j++) {
      assert.ok(dirs[i].angleTo(dirs[j]) > 0.25, `燈 ${i} 與 ${j} 方向要錯開`);
    }
  }
  // 正下方目標不會翻轉
  assert.ok(aimDir([0, 2, 0], [0, 0, 0]).y < -0.99);
});

test('窗邊櫃的小檯燈已移除、軌道燈頭用目標瞄準', () => {
  const lounge = readFileSync(join(publicRoot, 'private-team-scene-lounge.js'), 'utf8');
  const lighting = readFileSync(join(publicRoot, 'private-team-scene-gallery-lighting.js'), 'utf8');
  assert.doesNotMatch(lounge, /'loungeLamp'/);
  assert.doesNotMatch(lounge, /loungeLampBulb/);
  assert.match(lounge, /sofaFloorLamp/);
  assert.match(lighting, /headAims\[i\]/);
});
