// 鏡頭控制純邏輯測試：node --test server/src/test/js/island-camera.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const kit = await loadPublicModule('island-kit.js');
const sky = await loadPublicModule('island-sky.js');
const C = await loadPublicModule('island-camera.js');

const G = kit.G;
const MOON_ELEVATION = sky.MOON_ELEVATION;
const MOON_AZIMUTH = sky.MOON_AZIMUTH;

const {
  EXPLORE_POSE,
  INTERIOR_POSE,
  EXPLORE_PHI_MIN,
  EXPLORE_PHI_MAX,
  PANORAMA_PHI_MIN,
  PANORAMA_PHI_MAX,
  DRAG_PHI_SENSITIVITY,
  LOOK_UP_PHI_FLOOR,
  LOOK_UP_PITCH_MAX,
  dragPhiDelta,
  applyDragPhi,
  exploreLookUpBlend,
  exploreFollowDistance,
  exploreTargetPitch,
  exploreSkyPitch,
  exploreAimAzimuth,
  exploreThetaFacing,
  exploreIdealViewPitch,
  wrapAngle,
  celestialNdcY,
  celestialNdcX,
  SUN_AZIMUTH,
  SUN_ELEVATION,
  ISLAND_FOV_DEG,
  CAMERA_LAMBDA,
  CAMERA_LAMBDA_EXPLORE
} = C;

const LOOK_AT_Y = G + 1.05;
const THETA = EXPLORE_POSE.theta;
const MOBILE_ASPECT = 390 / 844; // ≈0.46

function distFor(aspect) {
  return exploreFollowDistance(EXPLORE_POSE.radius, aspect);
}

function angleNear(a, b, tol = 1e-6) {
  return Math.abs(wrapAngle(a - b)) <= tol;
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

test('極限抬頭仰角在 22°～30° 之間、不得接近 90°（夜間月亮）', () => {
  const pitch = exploreSkyPitch(LOOK_AT_Y, distFor(1.6), EXPLORE_PHI_MIN, G, 1.6, THETA, MOON_ELEVATION);
  assert.ok(pitch >= 0.38 && pitch <= 0.52, `pitch=${pitch} 應在 0.38～0.52 rad`);
  assert.ok(pitch < 1.0, '不得接近正上方');
  assert.ok(Math.abs(pitch - LOOK_UP_PITCH_MAX) < 0.08);
});

test('月亮 NDC y 在寬 phi 帶內可見（桌面 1.6 與直向 0.48）', () => {
  const aspects = [1.6, 0.48];
  for (const aspect of aspects) {
    const dist = distFor(aspect);
    let visibleCount = 0;
    let samples = 0;
    for (let phi = EXPLORE_PHI_MIN; phi <= LOOK_UP_PHI_FLOOR + 1e-6; phi += 0.03) {
      samples++;
      const pitch = exploreTargetPitch(phi, LOOK_AT_Y, dist, G, aspect, THETA, MOON_ELEVATION);
      if (pitch == null) continue;
      const ndc = celestialNdcY(MOON_ELEVATION, pitch, ISLAND_FOV_DEG);
      if (Math.abs(ndc) < 0.85) visibleCount++;
    }
    assert.ok(samples > 10);
    assert.ok(visibleCount / samples >= 0.5,
      `aspect=${aspect} 至少一半拖曳點月亮 |NDC y|<0.85 (${visibleCount}/${samples})`);
  }
});

test('視線 yaw 基準為 theta+π：blend≈0 與 blend=1 都跟玩家水平方向', () => {
  const theta = 0.35;
  const near0 = exploreAimAzimuth(theta, 0.01, MOON_AZIMUTH);
  const at1 = exploreAimAzimuth(theta, 1, MOON_AZIMUTH);
  assert.ok(angleNear(near0, theta + Math.PI), `blend≈0 aim=${near0} 應≈theta+π`);
  assert.ok(angleNear(at1, theta + Math.PI), `blend=1 aim=${at1} 應≈theta+π，不可鎖月亮`);
  assert.ok(!angleNear(at1, MOON_AZIMUTH, 0.2), 'blend=1 不得鎖死在月亮方位');
});

test('blend=1 時改變 theta，視線水平方向跟著變', () => {
  const a = exploreAimAzimuth(0.2, 1, MOON_AZIMUTH);
  const b = exploreAimAzimuth(0.9, 1, MOON_AZIMUTH);
  assert.ok(Math.abs(wrapAngle(a - b)) > 0.5, '拖左右應改變視線 yaw');
});

test('面向月亮時，水平與垂直 NDC 在連續 phi 帶內可見（含 390×844）', () => {
  const aspects = [1.6, MOBILE_ASPECT];
  const theta = exploreThetaFacing(MOON_AZIMUTH);
  assert.ok(angleNear(exploreAimAzimuth(theta, 1), MOON_AZIMUTH));
  for (const aspect of aspects) {
    const dist = distFor(aspect);
    let visible = 0;
    let samples = 0;
    for (let phi = 0.32; phi <= 0.56 + 1e-6; phi += 0.02) {
      samples++;
      const blend = exploreLookUpBlend(phi);
      if (blend <= 0) continue;
      const pitch = exploreTargetPitch(phi, LOOK_AT_Y, dist, G, aspect, theta, MOON_ELEVATION);
      const aim = exploreAimAzimuth(theta, blend, MOON_AZIMUTH);
      const ndcY = celestialNdcY(MOON_ELEVATION, pitch, ISLAND_FOV_DEG);
      const ndcX = celestialNdcX(MOON_AZIMUTH, aim, aspect, ISLAND_FOV_DEG);
      if (Math.abs(ndcY) < 0.92 && Math.abs(ndcX) < 0.92) visible++;
    }
    assert.ok(samples >= 10, `aspect=${aspect} samples`);
    assert.ok(visible / samples >= 0.85,
      `面向月亮 aspect=${aspect} 連續 phi 0.32～0.56 應多半可見 (${visible}/${samples})`);
  }
});

test('白天極限抬頭：太陽仰角進入畫面（勿再用月亮 20°）', () => {
  const aspects = [1.6, MOBILE_ASPECT];
  const theta = exploreThetaFacing(SUN_AZIMUTH);
  for (const aspect of aspects) {
    const moonish = exploreIdealViewPitch(aspect, MOON_ELEVATION);
    const sunny = exploreIdealViewPitch(aspect, SUN_ELEVATION);
    assert.ok(sunny > moonish + 0.2, '白天理想 pitch 應明顯高於夜間月亮');
    const pitch = exploreSkyPitch(
      LOOK_AT_Y, distFor(aspect), EXPLORE_PHI_MIN, G, aspect, theta, SUN_ELEVATION
    );
    const ndcY = celestialNdcY(SUN_ELEVATION, pitch, ISLAND_FOV_DEG);
    const aim = exploreAimAzimuth(theta, 1, SUN_AZIMUTH);
    const ndcX = celestialNdcX(SUN_AZIMUTH, aim, aspect, ISLAND_FOV_DEG);
    assert.ok(Math.abs(ndcY) < 0.95, `白天太陽 |NDC y|=${ndcY} aspect=${aspect}`);
    assert.ok(Math.abs(ndcX) < 0.95, `白天太陽 |NDC x|=${ndcX} aspect=${aspect}`);
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

test('dragPhiDelta：探索往上拖（dy<0）phi 變小，全景往上拖 phi 變大', () => {
  assert.ok(dragPhiDelta(-10, true) < 0, '探索往上＝抬頭＝phi 減');
  assert.ok(dragPhiDelta(10, true) > 0, '探索往下＝低頭＝phi 增');
  assert.ok(dragPhiDelta(-10, false) > 0, '全景往上維持抓住世界＝phi 增');
  assert.ok(dragPhiDelta(10, false) < 0, '全景往下＝抬頭＝phi 減');
  assert.equal(dragPhiDelta(-10, true), -10 * DRAG_PHI_SENSITIVITY);
  assert.equal(dragPhiDelta(-10, false), 10 * DRAG_PHI_SENSITIVITY);
});

test('applyDragPhi：探索 dy<0 時 phi 變小並夾在探索範圍', () => {
  const mid = (EXPLORE_PHI_MIN + EXPLORE_PHI_MAX) * 0.5;
  const next = applyDragPhi(mid, -20, true);
  assert.ok(next < mid);
  assert.ok(next >= EXPLORE_PHI_MIN);
  assert.ok(next <= EXPLORE_PHI_MAX);
  assert.equal(applyDragPhi(EXPLORE_PHI_MIN, -1000, true), EXPLORE_PHI_MIN);
  assert.equal(applyDragPhi(EXPLORE_PHI_MAX, 1000, true), EXPLORE_PHI_MAX);
});

test('applyDragPhi：全景 dy<0 時 phi 變大並夾在全景範圍', () => {
  const mid = (PANORAMA_PHI_MIN + PANORAMA_PHI_MAX) * 0.5;
  const next = applyDragPhi(mid, -20, false);
  assert.ok(next > mid);
  assert.ok(next >= PANORAMA_PHI_MIN);
  assert.ok(next <= PANORAMA_PHI_MAX);
  assert.equal(applyDragPhi(PANORAMA_PHI_MIN, 1000, false), PANORAMA_PHI_MIN);
  assert.equal(applyDragPhi(PANORAMA_PHI_MAX, -1000, false), PANORAMA_PHI_MAX);
});
