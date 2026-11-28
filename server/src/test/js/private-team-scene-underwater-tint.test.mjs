import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const { stripUnderwaterTint, applyUnderwaterTint } = await loadPublicModule('private-team-scene-room.js');

test('stripUnderwaterTint 不得留下 null 的 onBeforeCompile', () => {
  const mat = {
    onBeforeCompile: () => {},
    customProgramCacheKey: () => 'old',
    needsUpdate: false
  };
  stripUnderwaterTint(mat);
  assert.notEqual(mat.onBeforeCompile, null);
  assert.ok(mat.onBeforeCompile === undefined || typeof mat.onBeforeCompile === 'function');
  assert.equal(mat.needsUpdate, true);
  assert.equal(mat.customProgramCacheKey(), 'meshbasic-vanilla');
});

test('applyUnderwaterTint 設回函式並標 needsUpdate', () => {
  const mat = { needsUpdate: false };
  const waterYUniform = { value: 0.47 };
  applyUnderwaterTint(mat, waterYUniform);
  assert.equal(typeof mat.onBeforeCompile, 'function');
  assert.equal(mat.needsUpdate, true);
  assert.equal(mat.customProgramCacheKey(), 'secretary-underwater-tint-v2');
});

test('場景 sync 僅在姿勢變化時切換水下著色', () => {
  const src = readPrivateTeamSceneBundle();
  assert.match(src, /poseChanged/);
  assert.doesNotMatch(src, /onBeforeCompile = null/);
});