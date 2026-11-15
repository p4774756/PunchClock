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
  LOOK_UP_PHI_HIGH,
  exploreLookUpYOffset,
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
  assert.ok(EXPLORE_PHI_MIN >= 0.38 && EXPLORE_PHI_MIN <= 0.45, 'phi 下限應避免過頭');
  assert.ok(EXPLORE_PHI_MAX > 1, 'phi 最大值應允許俯視');
  assert.ok(EXPLORE_PHI_MAX <= 1.2, 'phi 最大值不應過度俯視');
  assert.ok(EXPLORE_PHI_MAX > EXPLORE_PHI_MIN, '最大值應大於最小值');
  assert.ok(EXPLORE_PHI_MAX - EXPLORE_PHI_MIN > 0.5, '範圍應足夠大');
});

test('exploreLookUpYOffset 漸進抬升、直向加強', () => {
  assert.equal(exploreLookUpYOffset(LOOK_UP_PHI_HIGH, 1), 0);
  const floor = exploreLookUpYOffset(EXPLORE_PHI_MIN, 1.6);
  const portrait = exploreLookUpYOffset(EXPLORE_PHI_MIN, 0.48);
  const midPhi = (EXPLORE_PHI_MIN + LOOK_UP_PHI_HIGH) * 0.5;
  const mid = exploreLookUpYOffset(midPhi, 1.6);
  assert.ok(floor > 5, '抬頭下限應有明顯上抬');
  assert.ok(portrait > floor, '直向應比橫向多上抬');
  assert.ok(mid > 2 && mid < floor, '中段應有穩定仰望帶');
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