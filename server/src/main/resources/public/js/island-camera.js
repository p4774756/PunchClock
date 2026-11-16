import { G } from './island-kit.js';

export const EXPLORE_POSE = { radius: 9.2, theta: 0.35, phi: 0.98 };
// 室內比島小很多，沿用探索的俯角但把鏡頭拉近，才看得到沒有屋頂的房間。
export const INTERIOR_POSE = { radius: 5.2, theta: EXPLORE_POSE.theta, phi: 0.82 };
// 鏡頭指數平滑速率（1/秒）；60fps 時約等於舊的每幀 0.08／0.14。
export const CAMERA_LAMBDA = 5;
export const CAMERA_LAMBDA_EXPLORE = 9;
// 探索模式仰角：phi 愈小愈抬頭；0.32 介於過頭 0.25 與不足 0.40 之間。
export const EXPLORE_PHI_MIN = 0.32;
export const EXPLORE_PHI_MAX = 1.12;
// 接近預設俯視（0.98）仍略帶仰望，拉開可用拖曳帶。
export const LOOK_UP_PHI_HIGH = 0.92;

/** 0→1：探索模式抬頭強度（phi 愈小愈大）。 */
export function exploreLookUpBlend(safePhi) {
  if (safePhi >= LOOK_UP_PHI_HIGH) return 0;
  const span = LOOK_UP_PHI_HIGH - EXPLORE_PHI_MIN;
  if (span <= 0) return 0;
  const t = Math.max(0, Math.min(1, (LOOK_UP_PHI_HIGH - safePhi) / span));
  return t * t * (3 - 2 * t);
}

/** 探索模式仰望時視線目標上抬（Y）；FOV 25°。aspect<1 直向再加。 */
export function exploreLookUpYOffset(safePhi, aspect) {
  const eased = exploreLookUpBlend(safePhi);
  if (eased <= 0) return 0;
  let offset = eased * 12 + eased * eased * 9;
  const a = aspect == null ? 1 : aspect;
  if (a < 0.85) offset += eased * 3.5;
  if (a < 0.68) offset += eased * 2.5;
  return offset;
}

/** 抬頭時鏡頭降到角色肩後，搭配上抬視線才能望見地平線以上。 */
export function exploreFollowCameraY(lookAtY, dist, safePhi, groundY) {
  let cy = lookAtY + dist * Math.cos(safePhi);
  const eased = exploreLookUpBlend(safePhi);
  if (eased > 0) {
    const lowY = Math.max(groundY + 0.92, lookAtY + 0.48);
    cy = cy * (1 - eased) + lowY * eased;
  }
  return Math.max(groundY + 0.88, cy);
}

/** 估計鏡頭仰角（rad），供測試：>0 表示視線高過角色。 */
export function exploreSkyPitch(lookAtY, dist, safePhi, groundY, aspect) {
  const blend = exploreLookUpBlend(safePhi);
  const cy = exploreFollowCameraY(lookAtY, dist, safePhi, groundY);
  const lookY = lookAtY + exploreLookUpYOffset(safePhi, aspect);
  const horiz = Math.max(0.8, dist * Math.sin(safePhi));
  return Math.atan2(lookY - cy, horiz);
}

export function createPoses(panorama) {
  return {
    home: { look: panorama.look, radius: panorama.radius, theta: panorama.theta, phi: panorama.phi },
    devices: { look: [0.1, 0.9, 1.85], radius: 13.2, theta: 0.22, phi: 1.02 },
    system: { look: [-4.05, 1.0, -0.05], radius: 11.6, theta: -0.35, phi: 1.0 },
    photos: { look: [3.85, 1.05, 0.35], radius: 12.2, theta: 1.25, phi: 0.98 }
  };
}

export function createCameraRig(THREE, camera, hooks) {
  const reduced = !!(hooks && hooks.reduced);
  const groundY = hooks && hooks.groundY != null ? hooks.groundY : G;
  const following = hooks.following;
  const smoothingFactor = hooks.smoothingFactor;
  const home = hooks.initial;
  const lookAt = new THREE.Vector3();
  const targetLook = new THREE.Vector3();
  let radius = home.radius;
  let theta = home.theta;
  let phi = home.phi;
  let targetRadius = radius;
  let targetTheta = theta;
  let targetPhi = phi;

  function placeCamera() {
    const aspect = camera.aspect || 1;
    let dist = radius * 1.07;
    if (aspect < 1.02) dist *= Math.min(1.6, 1.02 / Math.max(aspect, 0.5));
    const followCam = following();
    // 探索模式允許仰望天空（phi 低至 EXPLORE_PHI_MIN），同時保護鏡頭不穿地。
    const safePhi = followCam ? Math.min(EXPLORE_PHI_MAX, Math.max(EXPLORE_PHI_MIN, phi)) : phi;
    const span = Math.sin(safePhi);
    let cy = followCam
      ? exploreFollowCameraY(lookAt.y, dist, safePhi, groundY)
      : lookAt.y + dist * Math.cos(safePhi);
    if (followCam && safePhi > LOOK_UP_PHI_HIGH) {
      cy = Math.max(groundY + 1.35, lookAt.y + dist * Math.cos(safePhi));
    }
    camera.position.set(
      lookAt.x + dist * span * Math.sin(theta),
      cy,
      lookAt.z + dist * span * Math.cos(theta)
    );
    const lookUpY = followCam
      ? lookAt.y + exploreLookUpYOffset(safePhi, aspect)
      : lookAt.y;
    camera.lookAt(lookAt.x, lookUpY, lookAt.z);
    camera.updateMatrixWorld(true);
  }

  function applyPose(pose, instant) {
    targetLook.set(pose.look[0], pose.look[1], pose.look[2]);
    targetRadius = pose.radius;
    targetTheta = pose.theta;
    targetPhi = pose.phi;
    if (instant || reduced) {
      lookAt.copy(targetLook);
      radius = targetRadius;
      theta = targetTheta;
      phi = targetPhi;
      placeCamera();
    }
  }

  function follow(x, y, z, pose, instant) {
    targetLook.set(x, y, z);
    targetRadius = pose.radius;
    targetTheta = pose.theta;
    targetPhi = pose.phi;
    if (instant || reduced) {
      lookAt.copy(targetLook);
      radius = targetRadius;
      theta = targetTheta;
      phi = targetPhi;
      placeCamera();
    }
  }

  function step(dt) {
    const k = smoothingFactor(following() ? CAMERA_LAMBDA_EXPLORE : CAMERA_LAMBDA, dt, reduced);
    lookAt.lerp(targetLook, k);
    radius += (targetRadius - radius) * k;
    let dTheta = targetTheta - theta;
    dTheta = Math.atan2(Math.sin(dTheta), Math.cos(dTheta));
    theta += dTheta * k;
    phi += (targetPhi - phi) * k;
    placeCamera();
  }

  return {
    get theta() { return theta; },
    get targetLook() { return targetLook; },
    get targetRadius() { return targetRadius; },
    set targetRadius(value) { targetRadius = value; },
    get targetTheta() { return targetTheta; },
    set targetTheta(value) { targetTheta = value; },
    get targetPhi() { return targetPhi; },
    set targetPhi(value) { targetPhi = value; },
    applyPose: applyPose,
    follow: follow,
    step: step
  };
}
