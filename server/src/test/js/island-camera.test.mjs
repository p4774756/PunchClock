// 鏡頭控制純邏輯測試：node --test server/src/test/js/island-camera.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const kit = await loadPublicModule('island-kit.js');
const sky = await loadPublicModule('island-sky.js');
const C = await loadPublicModule('island-camera.js');
const I = await loadPublicModule('island-interior.js');

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
  exploreViewLift,
  exploreFollowDistance,
  interiorExploreFollowDistance,
  exploreTargetPitch,
  exploreSkyPitch,
  explorePitchAlongDrag,
  exploreFollowCameraFrame,
  EXPLORE_PITCH_STEP_MAX,
  INTERIOR_LOOK_UP_PITCH_MAX,
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

test('exploreViewLift：預設 phi 以上為 0，下限為 1', () => {
  assert.equal(exploreViewLift(EXPLORE_POSE.phi, EXPLORE_POSE.phi), 0);
  assert.equal(exploreViewLift(EXPLORE_POSE.phi + 0.05, EXPLORE_POSE.phi), 0);
  assert.equal(exploreViewLift(EXPLORE_PHI_MIN, EXPLORE_POSE.phi), 1);
  const mid = exploreViewLift((EXPLORE_PHI_MIN + EXPLORE_POSE.phi) * 0.5, EXPLORE_POSE.phi);
  assert.ok(mid > 0.4, '中段應有連續仰望帶');
});

test('極限抬頭仰角在 22°～30° 之間、不得接近 90°（夜間月亮）', () => {
  const pitch = exploreSkyPitch(LOOK_AT_Y, distFor(1.6), EXPLORE_PHI_MIN, G, 1.6, THETA, MOON_ELEVATION);
  assert.ok(pitch >= 0.38 && pitch <= 0.52, `pitch=${pitch} 應在 0.38～0.52 rad`);
  assert.ok(pitch < 1.0, '不得接近正上方');
  assert.ok(Math.abs(pitch - LOOK_UP_PITCH_MAX) < 0.08);
});

test('月亮 NDC y 在抬頭帶內可見（桌面 1.6 與直向 0.48）', () => {
  const aspects = [1.6, 0.48];
  const phiRest = EXPLORE_POSE.phi;
  for (const aspect of aspects) {
    const dist = distFor(aspect);
    let visibleCount = 0;
    let samples = 0;
    for (let phi = EXPLORE_PHI_MIN; phi <= phiRest - 0.05 + 1e-6; phi += 0.03) {
      samples++;
      const pitch = exploreTargetPitch(
        phi, LOOK_AT_Y, dist, G, aspect, THETA, MOON_ELEVATION, phiRest, true
      );
      if (pitch == null) continue;
      const ndc = celestialNdcY(MOON_ELEVATION, pitch, ISLAND_FOV_DEG);
      if (Math.abs(ndc) < 0.85) visibleCount++;
    }
    assert.ok(samples > 10);
    const highLiftVisible = exploreTargetPitch(
      EXPLORE_PHI_MIN, LOOK_AT_Y, dist, G, aspect, THETA, MOON_ELEVATION, phiRest, true
    );
    const ndcTop = celestialNdcY(MOON_ELEVATION, highLiftVisible, ISLAND_FOV_DEG);
    assert.ok(Math.abs(ndcTop) < 0.85, `極限抬頭 aspect=${aspect} |NDC y|=${ndcTop}`);
    assert.ok(visibleCount / samples >= 0.25,
      `aspect=${aspect} 抬頭帶部分可見月亮 (${visibleCount}/${samples})`);
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

test('面向月亮時，水平與垂直 NDC 在連續抬頭帶內可見（含 390×844）', () => {
  const aspects = [1.6, MOBILE_ASPECT];
  const phiRest = EXPLORE_POSE.phi;
  const theta = exploreThetaFacing(MOON_AZIMUTH);
  assert.ok(angleNear(exploreAimAzimuth(theta, 1), MOON_AZIMUTH));
  for (const aspect of aspects) {
    const dist = distFor(aspect);
    let visible = 0;
    let samples = 0;
    for (let phi = EXPLORE_PHI_MIN; phi <= phiRest - 0.08 + 1e-6; phi += 0.02) {
      samples++;
      const lift = exploreViewLift(phi, phiRest);
      if (lift <= 0) continue;
      const pitch = exploreTargetPitch(
        phi, LOOK_AT_Y, dist, G, aspect, theta, MOON_ELEVATION, phiRest, true
      );
      const aim = exploreAimAzimuth(theta, lift, MOON_AZIMUTH);
      const ndcY = celestialNdcY(MOON_ELEVATION, pitch, ISLAND_FOV_DEG);
      const ndcX = celestialNdcX(MOON_AZIMUTH, aim, aspect, ISLAND_FOV_DEG);
      if (Math.abs(ndcY) < 0.92 && Math.abs(ndcX) < 0.92) visible++;
    }
    assert.ok(samples >= 10, `aspect=${aspect} samples`);
    const topPitch = exploreTargetPitch(
      EXPLORE_PHI_MIN, LOOK_AT_Y, dist, G, aspect, theta, MOON_ELEVATION, phiRest, true
    );
    const topNdcY = celestialNdcY(MOON_ELEVATION, topPitch, ISLAND_FOV_DEG);
  const topNdcX = celestialNdcX(MOON_AZIMUTH, exploreAimAzimuth(theta, 1), aspect, ISLAND_FOV_DEG);
    assert.ok(Math.abs(topNdcY) < 0.92 && Math.abs(topNdcX) < 0.92,
      `極限抬頭 aspect=${aspect} 月亮應在畫面內`);
    assert.ok(visible / (samples - 2) >= 0.35,
      `面向月亮 aspect=${aspect} 抬頭帶應有部分可見 (${visible}/${samples})`);
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

test('往上拖曳：島上與室內仰角單調遞增、相鄰 5px 無跳變', () => {
  const cases = [
  { label: 'island', phiStart: EXPLORE_POSE.phi, phiRest: EXPLORE_POSE.phi, allowSky: true, dist: distFor(MOBILE_ASPECT) },
  { label: 'interior', phiStart: INTERIOR_POSE.phi, phiRest: INTERIOR_POSE.phi, allowSky: false,
      dist: interiorExploreFollowDistance(INTERIOR_POSE.radius, MOBILE_ASPECT) }
  ];
  for (const c of cases) {
    const samples = explorePitchAlongDrag(
      c.phiStart, -150, 5, LOOK_AT_Y, c.dist, G, MOBILE_ASPECT, THETA, MOON_ELEVATION, c.phiRest, c.allowSky
    );
    assert.ok(samples.length >= 20, c.label);
    for (let i = 1; i < samples.length; i++) {
      assert.ok(
        samples[i].pitch + 1e-6 >= samples[i - 1].pitch,
        `${c.label} dy=${samples[i].dyPx} 仰角應不低於前一步 (${samples[i - 1].pitch} → ${samples[i].pitch})`
      );
      const dPitch = Math.abs(samples[i].pitch - samples[i - 1].pitch);
      assert.ok(
        dPitch <= EXPLORE_PITCH_STEP_MAX + 1e-5,
        `${c.label} 相鄰 5px 仰角變化 ${dPitch} 超過上限`
      );
    }
    const top = samples[samples.length - 1].pitch;
    if (!c.allowSky) {
      assert.ok(top <= INTERIOR_LOOK_UP_PITCH_MAX + 0.02, `室內抬頭上限 pitch=${top}`);
    }
  }
});

test('從抬頭狀態往下拖：仰角單調遞減', () => {
  const dist = distFor(1.6);
  const phiRest = EXPLORE_POSE.phi;
  const samples = explorePitchAlongDrag(
    EXPLORE_PHI_MIN, 150, 5, LOOK_AT_Y, dist, G, 1.6, THETA, MOON_ELEVATION, phiRest, true
  );
  for (let i = 1; i < samples.length; i++) {
    assert.ok(
      samples[i].pitch <= samples[i - 1].pitch + 1e-6,
      `往下拖 dy=${samples[i].dyPx} 仰角應遞減 (${samples[i - 1].pitch} → ${samples[i].pitch})`
    );
  }
});

test('從任何起始 phi 往下拖：每步仰角不增加（含越過 phiRest 的俯視段）', () => {
  const dist = distFor(MOBILE_ASPECT);
  const phiRest = EXPLORE_POSE.phi;
  const starts = [EXPLORE_PHI_MIN, phiRest, (phiRest + EXPLORE_PHI_MAX) * 0.5, EXPLORE_PHI_MAX];
  for (const phiStart of starts) {
    const samples = explorePitchAlongDrag(
      phiStart, 210, 10, LOOK_AT_Y, dist, G, MOBILE_ASPECT, THETA, MOON_ELEVATION, phiRest, true
    );
    for (let i = 1; i < samples.length; i++) {
      assert.ok(
        samples[i].pitch <= samples[i - 1].pitch + 1e-6,
        `起點 phi=${phiStart} dy=${samples[i].dyPx} 仰角不得上升 ` +
          `(${samples[i - 1].pitch} → ${samples[i].pitch})`
      );
    }
  }
});

test('手機直向室內跟隨距離不超過島上放大倍率', () => {
  const islandDist = exploreFollowDistance(INTERIOR_POSE.radius, MOBILE_ASPECT);
  const indoorDist = interiorExploreFollowDistance(INTERIOR_POSE.radius, MOBILE_ASPECT);
  assert.ok(indoorDist < islandDist, `indoor=${indoorDist} island=${islandDist}`);
});

test('研發樓手機：全 viewLift 鏡頭在擴張 AABB 內，角色與螢幕仍在畫面', () => {
  const room = I.interiorByBuilding('computer-site');
  const pose = I.labInteriorCameraForAspect(MOBILE_ASPECT);
  const phiRest = pose.phi;
  const dist = I.labInteriorFollowDistance(pose.radius, MOBILE_ASPECT);
  const o = I.INTERIOR_ORIGIN;
  const playerLocal = { x: room.spawn.x, z: room.spawn.z };
  const lookLocal = I.labInteriorLookTarget(room, playerLocal, MOBILE_ASPECT);
  const restFrame = exploreFollowCameraFrame(
    lookLocal.x, lookLocal.y, lookLocal.z, dist, phiRest, pose.theta, G, MOBILE_ASPECT, phiRest, false,
    MOON_ELEVATION
  );
  const bounds = I.interiorLiftBoundsFromRest(room, restFrame.placement);
  const playerWorld = { x: playerLocal.x + o.x, z: playerLocal.z + o.z };
  const inside = (ndc) => ndc.vz > 0.02 && ndc.y >= -0.94 && ndc.y <= 0.98 && ndc.x >= -0.94 && ndc.x <= 0.94;
  const screenLocal = { cx: 0, cy: G + 0.84, cz: -1.74 };
  for (let dy = 0; dy >= -160; dy -= 10) {
    const phi = applyDragPhi(phiRest, dy, true);
    const frame = exploreFollowCameraFrame(
      lookLocal.x, lookLocal.y, lookLocal.z, dist, phi, pose.theta, G, MOBILE_ASPECT, phiRest, false,
      MOON_ELEVATION
    );
    const eye = frame.placement;
    assert.ok(
      I.interiorCameraWithinBounds(eye, bounds),
      `dy=${dy} 鏡頭應在擴張 AABB 內 (${eye.cx}, ${eye.cy}, ${eye.cz})`
    );
    const viewTarget = { x: lookLocal.x + o.x, y: lookLocal.y, z: lookLocal.z + o.z };
    const eyeWorld = { x: eye.cx + o.x, y: eye.cy, z: eye.cz + o.z };
    const headNdc = I.interiorProjectNdcFromEye(
      eyeWorld, viewTarget,
      playerWorld.x, I.LAB_INTERIOR_PLAYER_HEAD_Y, playerWorld.z, MOBILE_ASPECT
    );
    const scrNdc = I.interiorProjectNdcFromEye(
      eyeWorld, viewTarget,
      screenLocal.cx + o.x, screenLocal.cy, screenLocal.cz + o.z, MOBILE_ASPECT
    );
    assert.ok(inside(headNdc), `dy=${dy} 角色頭頂應在畫面內`);
    assert.ok(inside(scrNdc), `dy=${dy} 南牆螢幕中心應在畫面內`);
  }
});

test('手搖店室內：全 viewLift 鏡頭在擴張 AABB 內、角色在畫面內', () => {
  const room = I.interiorByBuilding('shop');
  const aspect = MOBILE_ASPECT;
  const phiRest = INTERIOR_POSE.phi;
  const dist = interiorExploreFollowDistance(INTERIOR_POSE.radius, aspect);
  const o = I.INTERIOR_ORIGIN;
  const local = { x: room.spawn.x, z: room.spawn.z };
  const lookLocal = { x: local.x, y: LOOK_AT_Y, z: local.z };
  const restFrame = exploreFollowCameraFrame(
    lookLocal.x, lookLocal.y, lookLocal.z, dist, phiRest, INTERIOR_POSE.theta, G, aspect, phiRest, false,
    MOON_ELEVATION
  );
  const bounds = I.interiorLiftBoundsFromRest(room, restFrame.placement);
  const inside = (ndc) => ndc.vz > 0.02 && ndc.y >= -0.94 && ndc.y <= 0.98 && ndc.x >= -0.94 && ndc.x <= 0.94;
  for (let dy = 0; dy >= -160; dy -= 10) {
    const phi = applyDragPhi(phiRest, dy, true);
    const frame = exploreFollowCameraFrame(
      lookLocal.x, lookLocal.y, lookLocal.z, dist, phi, INTERIOR_POSE.theta, G, aspect, phiRest, false,
      MOON_ELEVATION
    );
    const eye = frame.placement;
    assert.ok(
      I.interiorCameraWithinBounds(eye, bounds),
      `shop dy=${dy} 鏡頭應在擴張 AABB 內`
    );
    const viewTarget = { x: lookLocal.x + o.x, y: lookLocal.y, z: lookLocal.z + o.z };
    const eyeWorld = { x: eye.cx + o.x, y: eye.cy, z: eye.cz + o.z };
    const headNdc = I.interiorProjectNdcFromEye(
      eyeWorld, viewTarget,
      lookLocal.x + o.x, I.LAB_INTERIOR_PLAYER_HEAD_Y, lookLocal.z + o.z, MOBILE_ASPECT
    );
    assert.ok(inside(headNdc), `shop dy=${dy} 角色應在畫面內`);
  }
});
