import { G } from './island-kit.js';

export const EXPLORE_POSE = { radius: 9.2, theta: 0.35, phi: 0.98 };
// 室內比島小很多，沿用探索的俯角但把鏡頭拉近，才看得到沒有屋頂的房間。
export const INTERIOR_POSE = { radius: 5.2, theta: EXPLORE_POSE.theta, phi: 0.82 };
// 鏡頭指數平滑速率（1/秒）；60fps 時約等於舊的每幀 0.08／0.14。
export const CAMERA_LAMBDA = 5;
export const CAMERA_LAMBDA_EXPLORE = 9;
// 探索模式仰角範圍（phi）：0.25 可仰望天空，1.12 為俯視極限。
// phi 是從垂直軸（Y）往下的角度，phi 越小鏡頭越高越往下看。
// 要仰望天空需要讓鏡頭在角色後方低處往前上看，但仍要避免穿地。
export const EXPLORE_PHI_MIN = 0.25;
export const EXPLORE_PHI_MAX = 1.12;

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
    let cy = lookAt.y + dist * Math.cos(safePhi);
    if (followCam) cy = Math.max(groundY + 1.35, cy);
    camera.position.set(
      lookAt.x + dist * span * Math.sin(theta),
      cy,
      lookAt.z + dist * span * Math.cos(theta)
    );
    // 仰望天空：當 phi 低於閾值時，將視線目標往上抬，讓鏡頭朝天空看
    const lookUpThreshold = 0.55;
    const lookUpY = followCam && safePhi < lookUpThreshold
      ? lookAt.y + (lookUpThreshold - safePhi) * 40
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
