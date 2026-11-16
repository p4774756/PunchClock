// 鏡頭控制純邏輯測試：node --test server/src/test/js/island-camera.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const kit = await loadPublicModule('island-kit.js');
const sky = await loadPublicModule('island-sky.js');
const C = await loadPublicModule('island-camera.js');

const G = kit.G;
const MOON_ELEVATION = sky.MOON_ELEVATION;

const {
  EXPLORE_POSE,
  INTERIOR_POSE,
  EXPLORE_PHI_MIN,
  EXPLORE_PHI_MAX,
  LOOK_UP_PHI_FLOOR,
  LOOK_UP_PITCH_MAX,
  exploreLookUpBlend,
  exploreFollowDistance,
  exploreTargetPitch,
  exploreSkyPitch,
  celestialNdcY,
  SUN_AZIMUTH,
  SUN_ELEVATION,
  ISLAND_FOV_DEG,
  CAMERA_LAMBDA,
  CAMERA_LAMBDA_EXPLORE
} = C;

const LOOK_AT_Y = G + 1.05;
const THETA = EXPLORE_POSE.theta;

function distFor(aspect) {
  return exploreFollowDistance(EXPLORE_POSE.radius, aspect);
}

test('探索模式預設姿態已匯出', () => {
  assert.ok(typeof EXPLORE_POSE === 'object');
});

test('室內姿態已匯出', () => {
  assert.ok(INTERIOR_POSE.radius < EXPLORE_POSE.radius);
});

test('探索仰角範圍與 LOOK_UP_PHI_FLOOR', () => {
  assert.ok(EXPLORE_PHI_MIN >= 0.30 && EXPLORE_PHI_MIN <= 0.36);
  assert.equal(LOOK_UP_PHI_FLOOR, 0.70);
  assert.ok(EXPLORE_PHI_MAX > 1);
});

test('exploreLookUpBlend：0.70 以上為 0，下限為 1', () => {
  assert.equal(exploreLookUpBlend(0.72), 0);
  assert.equal(exploreLookUpBlend(EXPLORE_PHI_MIN), 1);
  const mid = exploreLookUpBlend((EXPLORE_PHI_MIN + LOOK_UP_PHI_FLOOR) * 0.5);
  assert.ok(mid > 0.4, '中段應有連續仰望帶');
});

test('極限抬頭仰角在 22°～30° 之間、不得接近 90°', () => {
  const pitch = exploreSkyPitch(LOOK_AT_Y, distFor(1.6), EXPLORE_PHI_MIN, G, 1.6, THETA);
  assert.ok(pitch >= 0.38 && pitch <= 0.52, `pitch=${pitch} 應在 0.38～0.52 rad`);
  assert.ok(pitch < 1.0, '不得接近正上方');
  assert.ok(Math.abs(pitch - LOOK_UP_PITCH_MAX) < 0.08);
});

test('月亮 NDC 在寬 phi 帶內可見（桌面 1.6 與直向 0.48）', () => {
  const aspects = [1.6, 0.48];
  for (const aspect of aspects) {
    const dist = distFor(aspect);
    let visibleCount = 0;
  let samples = 0;
    for (let phi = EXPLORE_PHI_MIN; phi <= LOOK_UP_PHI_FLOOR + 1e-6; phi += 0.03) {
      samples++;
      const pitch = exploreTargetPitch(phi, LOOK_AT_Y, dist, G, aspect, THETA);
      if (pitch == null) continue;
      const ndc = celestialNdcY(MOON_ELEVATION, pitch, ISLAND_FOV_DEG);
      if (Math.abs(ndc) < 0.85) visibleCount++;
    }
    assert.ok(samples > 10);
    assert.ok(visibleCount / samples >= 0.5,
      `aspect=${aspect} 至少一半拖曳點月亮 |NDC y|<0.85 (${visibleCount}/${samples})`);
  }
});

test('太陽參考仰角常數與 island.js 一致', () => {
  assert.ok(SUN_ELEVATION > 0.5);
  assert.ok(Math.abs(SUN_AZIMUTH - Math.atan2(-9.5, 11)) < 1e-6);
});

test('全景預設 phi 仍在探索俯視上限內', () => {
  assert.ok(EXPLORE_POSE.phi <= EXPLORE_PHI_MAX);
  assert.ok(EXPLORE_POSE.phi > EXPLORE_PHI_MIN);
});

test('鏡頭平滑常數已匯出', () => {
  assert.equal(CAMERA_LAMBDA, 5);
  assert.equal(CAMERA_LAMBDA_EXPLORE, 9);
});