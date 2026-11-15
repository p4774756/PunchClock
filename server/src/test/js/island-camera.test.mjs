// 鏡頭控制純邏輯測試：node --test server/src/test/js/island-camera.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const C = await loadPublicModule('island-camera.js');

const {
  EXPLORE_POSE,
  INTERIOR_POSE,
  EXPLORE_PHI_MIN,
  EXPLORE_PHI_MAX,
  CAMERA_LAMBDA,
  CAMERA_LAMBDA_EXPLORE
} = C;

test('探索模式預設姿態已匯出', () => {
  assert.ok(typeof EXPLORE_POSE === 'object');
  assert.ok(typeof EXPLORE_POSE.radius === 'number');
  assert.ok(typeof EXPLORE_POSE.theta === 'number');
  assert.ok(typeof EXPLORE_POSE.phi === 'number');
});

test('室內姿態已匯出', () => {
  assert.ok(typeof INTERIOR_POSE === 'object');
  assert.ok(INTERIOR_POSE.radius < EXPLORE_POSE.radius, '室內鏡頭較近');
});

test('探索模式仰角範圍允許抬頭看天空', () => {
  assert.ok(EXPLORE_PHI_MIN > 0, 'phi 最小值應為正');
  assert.ok(EXPLORE_PHI_MIN < 0.4, 'phi 最小值應允許抬頭（約 0.25）');
  assert.ok(EXPLORE_PHI_MAX > 1, 'phi 最大值應允許俯視');
  assert.ok(EXPLORE_PHI_MAX <= 1.2, 'phi 最大值不應過度俯視');
  assert.ok(EXPLORE_PHI_MAX > EXPLORE_PHI_MIN, '最大值應大於最小值');
  assert.ok(EXPLORE_PHI_MAX - EXPLORE_PHI_MIN > 0.5, '範圍應足夠大');
});

test('探索模式仰角範圍足以仰望天空', () => {
  assert.ok(EXPLORE_PHI_MIN < 0.55, 'phi 最小值應低於仰望閾值 0.55');
  const lookUpAmount = (0.55 - EXPLORE_PHI_MIN) * 40;
  assert.ok(lookUpAmount > 5, `仰望抬升量 ${lookUpAmount.toFixed(1)} 應足夠看到天空`);
});

test('全景預設 phi 仍在探索俯視上限內', () => {
  assert.ok(EXPLORE_POSE.phi <= EXPLORE_PHI_MAX);
  assert.ok(EXPLORE_POSE.phi > EXPLORE_PHI_MIN);
});

test('鏡頭平滑常數已匯出', () => {
  assert.equal(CAMERA_LAMBDA, 5);
  assert.equal(CAMERA_LAMBDA_EXPLORE, 9);
});

test('室內姿態俯角合理', () => {
  assert.ok(INTERIOR_POSE.phi < EXPLORE_PHI_MAX, '室內俯角應在範圍內');
  assert.ok(INTERIOR_POSE.phi > EXPLORE_PHI_MIN, '室內俯角不應過度抬頭');
});