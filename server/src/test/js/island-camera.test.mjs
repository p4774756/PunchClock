// 鏡頭控制純邏輯測試：node --test server/src/test/js/island-camera.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const kit = await loadPublicModule('island-kit.js');
const C = await loadPublicModule('island-camera.js');
const G = kit.G;

const {
  EXPLORE_POSE,
  INTERIOR_POSE,
  EXPLORE_PHI_MIN,
  EXPLORE_PHI_MAX,
  LOOK_UP_PHI_HIGH,
  exploreLookUpBlend,
  exploreLookUpYOffset,
  exploreSkyPitch,
  CAMERA_LAMBDA,
  CAMERA_LAMBDA_EXPLORE
} = C;

const LOOK_AT_Y = G + 1.05;
const DIST = 9.2 * 1.07;

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
  assert.ok(EXPLORE_PHI_MIN >= 0.30 && EXPLORE_PHI_MIN <= 0.36, 'phi 下限在 0.25 與 0.40 之間');
  assert.ok(EXPLORE_PHI_MAX > 1);
  assert.ok(EXPLORE_PHI_MAX <= 1.2);
  assert.ok(LOOK_UP_PHI_HIGH >= 0.88, '仰望輔助應覆蓋大半拖曳帶');
  assert.ok(EXPLORE_PHI_MAX - EXPLORE_PHI_MIN > 0.5);
});

test('exploreLookUpBlend 在整段拖曳帶內漸進', () => {
  assert.equal(exploreLookUpBlend(LOOK_UP_PHI_HIGH), 0);
  assert.equal(exploreLookUpBlend(EXPLORE_PHI_MIN), 1);
  const midPhi = (EXPLORE_PHI_MIN + LOOK_UP_PHI_HIGH) * 0.5;
  const mid = exploreLookUpBlend(midPhi);
  assert.ok(mid > 0.35 && mid < 0.95, '中段應有可用仰望帶');
});

test('exploreLookUpYOffset 直向加強且上限足夠', () => {
  const floorWide = exploreLookUpYOffset(EXPLORE_PHI_MIN, 1.6);
  const floorTall = exploreLookUpYOffset(EXPLORE_PHI_MIN, 0.48);
  assert.ok(floorWide > 14, '桌面抬頭上限應足夠');
  assert.ok(floorTall > floorWide, '直向應多上抬');
  const midPhi = EXPLORE_PHI_MIN + (LOOK_UP_PHI_HIGH - EXPLORE_PHI_MIN) * 0.45;
  assert.ok(exploreLookUpYOffset(midPhi, 1.6) > 6, '中段應已開始看到天空');
});

test('exploreSkyPitch 抬頭時仰角足以看見月亮帶', () => {
  const pitchMax = exploreSkyPitch(LOOK_AT_Y, DIST, EXPLORE_PHI_MIN, G, 1.6);
  const pitchMid = exploreSkyPitch(
    LOOK_AT_Y,
    DIST,
    EXPLORE_PHI_MIN + (LOOK_UP_PHI_HIGH - EXPLORE_PHI_MIN) * 0.4,
    G,
    0.48
  );
  assert.ok(pitchMax > 0.45, `最高抬頭仰角 ${pitchMax} 應高於地平線`);
  assert.ok(pitchMid > 0.28, `中段仰角 ${pitchMid} 應可見天空`);
  assert.ok(exploreSkyPitch(LOOK_AT_Y, DIST, LOOK_UP_PHI_HIGH + 0.05, G, 1) < 0.15);
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
  assert.ok(INTERIOR_POSE.phi < EXPLORE_PHI_MAX);
  assert.ok(INTERIOR_POSE.phi > EXPLORE_PHI_MIN);
});