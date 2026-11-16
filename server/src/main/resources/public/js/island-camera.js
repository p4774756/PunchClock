import { G } from './island-kit.js';
import { MOON_ELEVATION } from './island-sky.js';

export const EXPLORE_POSE = { radius: 9.2, theta: 0.35, phi: 0.98 };
// 室內比島小很多，沿用探索的俯角但把鏡頭拉近，才看得到沒有屋頂的房間。
export const INTERIOR_POSE = { radius: 5.2, theta: EXPLORE_POSE.theta, phi: 0.82 };
export const CAMERA_LAMBDA = 5;
export const CAMERA_LAMBDA_EXPLORE = 9;
export const ISLAND_FOV_DEG = 25;

export const EXPLORE_PHI_MIN = 0.32;
export const EXPLORE_PHI_MAX = 1.12;
/** phi 高於此維持原本俯視角色，不抬頭。 */
export const LOOK_UP_PHI_FLOOR = 0.70;
/** 夜間極限抬頭視線仰角上限（rad），約 25°（對月亮）。 */
export const LOOK_UP_PITCH_MAX = 25 * Math.PI / 180;
/** blend 高於此維持天空視角仰角，避免中段又盯回角色。 */
export const LOOK_UP_SKY_BLEND = 0.32;
/** 太陽參考方位（與 island.js sun.position 一致）。 */
export const SUN_AZIMUTH = Math.atan2(-9.5, 11);
export const SUN_ELEVATION = Math.atan2(20.5, Math.hypot(9.5, 11));

/** 正規化到 (-π, π]。 */
export function wrapAngle(a) {
  return Math.atan2(Math.sin(a), Math.cos(a));
}

/**
 * 探索視線水平方位：相機在角色後方（theta），看向角色／前方天空為 theta+π。
 * 不鎖月亮／太陽；抬頭只改 pitch，左右拖維持玩家自己的水平方向。
 * celestialAzimuth 保留參數相容，刻意忽略。
 */
export function exploreAimAzimuth(theta, _blend, _celestialAzimuth) {
  return wrapAngle(theta + Math.PI);
}

/** 面向某天空體時應設的 orbit theta（使視線方位 = bodyAzimuth）。 */
export function exploreThetaFacing(bodyAzimuth) {
  return wrapAngle(bodyAzimuth - Math.PI);
}

/** 0→1：phi 從 LOOK_UP_PHI_FLOOR 降到 EXPLORE_PHI_MIN 時的抬頭強度。 */
export function exploreLookUpBlend(safePhi) {
  if (safePhi >= LOOK_UP_PHI_FLOOR) return 0;
  const span = LOOK_UP_PHI_FLOOR - EXPLORE_PHI_MIN;
  if (span <= 0) return 0;
  const t = Math.max(0, Math.min(1, (LOOK_UP_PHI_FLOOR - safePhi) / span));
  return t * t * (3 - 2 * t);
}

export function exploreFollowDistance(radius, aspect) {
  let dist = radius * 1.07;
  if (aspect < 1.02) dist *= Math.min(1.6, 1.02 / Math.max(aspect, 0.5));
  return dist;
}

/** 抬頭時鏡頭降到角色肩後（保留前版手感）。 */
export function exploreFollowCameraY(lookAtY, dist, safePhi, groundY) {
  let cy = lookAtY + dist * Math.cos(safePhi);
  const eased = exploreLookUpBlend(safePhi);
  if (eased > 0) {
    const lowY = Math.max(groundY + 0.92, lookAtY + 0.48);
    cy = cy * (1 - eased) + lowY * eased;
  }
  return Math.max(groundY + 0.88, cy);
}

export function exploreCameraPlacement(lookAtX, lookAtY, lookAtZ, dist, safePhi, theta, groundY) {
  const horiz = Math.max(0.55, dist * Math.sin(safePhi));
  const cy = exploreFollowCameraY(lookAtY, dist, safePhi, groundY);
  return {
    cx: lookAtX + horiz * Math.sin(theta),
    cy: cy,
    cz: lookAtZ + horiz * Math.cos(theta),
    horiz: horiz
  };
}

function naturalPitchToPlayer(lookAtY, placement) {
  return Math.atan2(lookAtY - placement.cy, placement.horiz);
}

/**
 * 由天空體高度與豎直 FOV 推算理想視線仰角。
 * 夜間對月亮約 23～25°；白天改用太陽仰角，才能把太陽收入畫面。
 */
export function exploreIdealViewPitch(aspect, bodyElevation) {
  const elev = bodyElevation == null ? MOON_ELEVATION : bodyElevation;
  const halfV = (ISLAND_FOV_DEG * Math.PI / 180) / 2;
  let pitch = elev + halfV * 0.28;
  if (aspect < 0.85) pitch -= (0.85 - aspect) * 0.14;
  if (aspect < 0.68) pitch -= (0.68 - Math.min(aspect, 0.68)) * 0.10;
  const lo = Math.min(0.38, elev * 0.55);
  // 月亮仍受 LOOK_UP_PITCH_MAX；太陽仰角較高時放寬上限。
  const hi = Math.max(LOOK_UP_PITCH_MAX, elev + halfV * 0.45);
  return Math.max(lo, Math.min(hi, pitch));
}

/** 目標視線仰角（rad）；blend=0 回 null 表示盯角色。 */
export function exploreTargetPitch(safePhi, lookAtY, dist, groundY, aspect, theta, bodyElevation) {
  const blend = exploreLookUpBlend(safePhi);
  if (blend <= 0) return null;
  const placement = exploreCameraPlacement(0, lookAtY, 0, dist, safePhi, theta, groundY);
  const natural = naturalPitchToPlayer(lookAtY, placement);
  const skyPitch = exploreIdealViewPitch(aspect, bodyElevation);
  const ease = blend >= LOOK_UP_SKY_BLEND
    ? 1
    : Math.max(0, Math.min(1, blend / LOOK_UP_SKY_BLEND));
  return natural * (1 - ease) + skyPitch * ease;
}

/** 由相機位置與目標仰角、方位角決定 lookAt 點（距離 72）。 */
export function exploreLookPointFromPitch(placement, pitch, aimAzimuth) {
  const len = 72;
  const cosP = Math.cos(pitch);
  const sinP = Math.sin(pitch);
  return {
    x: placement.cx + cosP * Math.sin(aimAzimuth) * len,
    y: placement.cy + sinP * len,
    z: placement.cz + cosP * Math.cos(aimAzimuth) * len
  };
}

/** 天空體仰角在畫面上的 NDC y（豎直 FOV）；相機仰角 pitch、體仰角 bodyEl。 */
export function celestialNdcY(bodyElevation, viewPitch, fovDeg) {
  const halfV = (fovDeg * Math.PI / 180) / 2;
  return (bodyElevation - viewPitch) / halfV;
}

/** 天空體相對視線方位的水平 NDC x（水平半視角 = 豎直半視角 × aspect）。 */
export function celestialNdcX(bodyAzimuth, viewAzimuth, aspect, fovDeg) {
  const halfH = ((fovDeg * Math.PI / 180) / 2) * Math.max(aspect, 1e-3);
  const d = wrapAngle(bodyAzimuth - viewAzimuth);
  return d / halfH;
}

/** 極限抬頭時的視線仰角（供測試）。 */
export function exploreSkyPitch(lookAtY, dist, safePhi, groundY, aspect, theta, bodyElevation) {
  const pitch = exploreTargetPitch(safePhi, lookAtY, dist, groundY, aspect, theta, bodyElevation);
  if (pitch != null) return pitch;
  const placement = exploreCameraPlacement(0, lookAtY, 0, dist, safePhi, theta, groundY);
  return naturalPitchToPlayer(lookAtY, placement);
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
  const nightFactor = typeof hooks.nightFactor === 'function' ? hooks.nightFactor : () => 1;
  const home = hooks.initial;
  const lookAt = new THREE.Vector3();
  const targetLook = new THREE.Vector3();
  let radius = home.radius;
  let theta = home.theta;
  let phi = home.phi;
  let targetRadius = radius;
  let targetTheta = theta;
  let targetPhi = phi;

  function celestialElevation() {
    // mix≥0.5 偏夜：用月亮；白天用太陽，勿再按月亮 20° 算。
    return nightFactor() >= 0.5 ? MOON_ELEVATION : SUN_ELEVATION;
  }

  function placeCamera() {
    const aspect = camera.aspect || 1;
    const dist = exploreFollowDistance(radius, aspect);
    const followCam = following();
    const safePhi = followCam ? Math.min(EXPLORE_PHI_MAX, Math.max(EXPLORE_PHI_MIN, phi)) : phi;
    const placement = exploreCameraPlacement(
      lookAt.x, lookAt.y, lookAt.z, dist, safePhi, theta, groundY
    );
    camera.position.set(placement.cx, placement.cy, placement.cz);
    const blend = followCam ? exploreLookUpBlend(safePhi) : 0;
    if (blend > 0) {
      const pitch = exploreTargetPitch(
        safePhi, lookAt.y, dist, groundY, aspect, theta, celestialElevation()
      );
      // 視線 yaw 永遠跟玩家水平方向（theta+π），不鎖月亮／太陽方位。
      const aim = exploreAimAzimuth(theta, blend);
      const point = exploreLookPointFromPitch(placement, pitch, aim);
      camera.lookAt(point.x, point.y, point.z);
    } else {
      camera.lookAt(lookAt);
    }
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
