import { G, P } from './island-kit.js';
import { MILU_ANIM } from './island-milu.js';
import { MOON_ELEVATION } from './island-sky.js';

export const EXPLORE_POSE = { radius: 9.2, theta: 0.35, phi: 0.98 };
// 室內比島小很多，沿用探索的俯角但把鏡頭拉近，才看得到沒有屋頂的房間。
export const INTERIOR_POSE = { radius: 5.2, theta: EXPLORE_POSE.theta, phi: 0.82 };
export const CAMERA_LAMBDA = 5;
export const CAMERA_LAMBDA_EXPLORE = 9;
export const ISLAND_FOV_DEG = 25;

export const EXPLORE_PHI_MIN = 0.32;
export const EXPLORE_PHI_MAX = 1.12;
/** 全景俯視拖曳的 phi 夾限（與 island.js pointermove 既有值一致）。 */
export const PANORAMA_PHI_MIN = 0.58;
export const PANORAMA_PHI_MAX = 1.15;
/** 垂直拖曳對 phi 的靈敏度（不變）。 */
export const DRAG_PHI_SENSITIVITY = 0.0035;
/** @deprecated 測試相容；抬頭改由 `exploreViewLift` 相對各 pose 的 phiRest 計算。 */
export const LOOK_UP_PHI_FLOOR = 0.70;
/** 抬頭端鏡頭球面角（肩後低角度）。 */
export const EXPLORE_PHI_SHOULDER = 0.52;
/** 夜間極限抬頭視線仰角上限（rad），約 25°（對月亮）。 */
export const LOOK_UP_PITCH_MAX = 25 * Math.PI / 180;
/** 室內抬頭上限（rad），約水平微仰，足夠看到牆上螢幕。 */
export const INTERIOR_LOOK_UP_PITCH_MAX = 12 * Math.PI / 180;
/** 相鄰 5px 拖曳取樣時，仰角變化量上限（供測試；約 4.5°／5px）。 */
export const EXPLORE_PITCH_STEP_MAX = 0.08;
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

/**
 * 垂直拖曳造成的 phi 變化量。
 * 探索（followCam）：往下拖 dy>0 → 負 delta → phi 變小＝抬頭看天空。
 * 全景：維持「抓住世界拖」——往下拖 dy>0 → 負 delta → 抬頭。
 */
export function dragPhiDelta(dy, followCam) {
  return -dy * DRAG_PHI_SENSITIVITY;
}

/** 依模式套用垂直拖曳並夾在對應 phi 範圍內。 */
export function applyDragPhi(phi, dy, followCam) {
  const phiMin = followCam ? EXPLORE_PHI_MIN : PANORAMA_PHI_MIN;
  const phiMax = followCam ? EXPLORE_PHI_MAX : PANORAMA_PHI_MAX;
  return Math.min(phiMax, Math.max(phiMin, phi + dragPhiDelta(dy, followCam)));
}

/**
 * 0→1：從預設 pose.phi（phiRest）往 EXPLORE_PHI_MIN 拖曳時的抬頭強度。
 * phi ≥ phiRest 維持 0（可繼續往下拖加強俯視）。
 */
export function exploreViewLift(safePhi, phiRest) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  if (safePhi >= rest - 1e-6) return 0;
  const span = rest - EXPLORE_PHI_MIN;
  if (span <= 0) return 0;
  const t = Math.max(0, Math.min(1, (rest - safePhi) / span));
  return t * t * (3 - 2 * t);
}

/** @deprecated 請改用 `exploreViewLift(safePhi, phiRest)`。 */
export function exploreLookUpBlend(safePhi, phiRest) {
  return exploreViewLift(safePhi, phiRest != null ? phiRest : LOOK_UP_PHI_FLOOR);
}

export function exploreFollowDistance(radius, aspect) {
  let dist = radius * 1.07;
  if (aspect < 1.02) dist *= Math.min(1.6, 1.02 / Math.max(aspect, 0.5));
  return dist;
}

/** 一般室內跟隨距離：手機直向不比島上拉那麼遠。 */
export function interiorExploreFollowDistance(radius, aspect) {
  let dist = radius * 1.07;
  if (aspect < 1.02) {
    dist *= Math.min(1.22, Math.sqrt(1.02 / Math.max(aspect, 0.45)));
  }
  return dist;
}

/** 俯視段（viewLift=0）放置角：不低於預設俯視，往下拖不會抬高鏡頭。 */
export function exploreDownwardPlacementPhi(safePhi, phiRest) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  return Math.min(safePhi, rest);
}

/** 用於球面放置的有效 phi：抬頭段插向肩後角，其餘沿用 safePhi。 */
export function explorePlacementPhi(safePhi, phiRest, viewLift) {
  if (viewLift <= 0) return exploreDownwardPlacementPhi(safePhi, phiRest);
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  return rest * (1 - viewLift) + EXPLORE_PHI_SHOULDER * viewLift;
}

/** 室內抬頭：phi 略往水平靠，不降到肩後（避免鏡頭穿出小房間）。 */
export const INTERIOR_PHI_LIFT_DELTA = 0.22;

export function interiorPlacementPhi(safePhi, phiRest, viewLift) {
  if (viewLift <= 0) return exploreDownwardPlacementPhi(safePhi, phiRest);
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  // 室內抬頭只改視線 pitch，鏡頭維持預設俯視位置（避免手機拉遠半徑把鏡頭抬出房間）。
  return rest;
}

/** 2D 主角 billboard 腳底／頭頂（milu／小莓共用 MILU_ANIM.height）。 */
export const EXPLORE_PLAYER_FEET_Y = P;
export const EXPLORE_PLAYER_HEAD_Y = P + MILU_ANIM.height;
/** 抬頭時鏡頭高度混合曲線：較快貼近低機位，中段就能收進全身。 */
export const EXPLORE_LIFT_HEIGHT_POWER = 3;
/** 僅在抬頭強度超過此值才夾 pitch，保留抬頭帶前段月亮可見性。 */
export const EXPLORE_PLAYER_PITCH_CAP_MIN_LIFT = 0.78;

export function exploreLiftCameraHeightBlend(viewLift) {
  const v = viewLift > 0 ? Math.min(1, viewLift) : 0;
  if (v <= 0) return 0;
  return 1 - Math.pow(1 - v, EXPLORE_LIFT_HEIGHT_POWER);
}

/** 戶外抬頭時鏡頭距離倍率（套用於既有 exploreFollowDistance 結果）。 */
export function exploreLiftDistanceScale(aspect, viewLift) {
  const v = viewLift > 0 ? viewLift : 0;
  if (v <= 0) return 1;
  const boost = aspect != null && aspect < 0.85 ? 0.18 : 0.12;
  return 1 + v * boost;
}

/** 戶外抬頭時略拉遠，配合低機位讓全身留在畫面裡。 */
export function exploreFollowDistanceForLift(radius, aspect, viewLift) {
  return exploreFollowDistance(radius, aspect) * exploreLiftDistanceScale(aspect, viewLift);
}

/**
 * 在當前鏡頭位置下，視線仰角上限：頭腳 NDC y 留在約 -0.92～0.98 內（與測試投影一致）。
 */
export function explorePitchCapForPlayer(placement, aspect) {
  const halfV = (ISLAND_FOV_DEG * Math.PI / 180) / 2;
  const h = Math.max(placement.horiz, 0.55);
  const eyeY = placement.cy;
  const feetE = Math.atan2(EXPLORE_PLAYER_FEET_Y - eyeY, h);
  const headE = Math.atan2(EXPLORE_PLAYER_HEAD_Y - eyeY, h);
  const narrow = aspect != null && aspect < 0.85;
  const feetBand = halfV * (narrow ? 0.88 : 0.918);
  const headBand = halfV * (narrow ? 0.95 : 0.968);
  return Math.min(feetE + feetBand, headE + headBand);
}

/** 抬頭時鏡頭降到角色後方低處（與 viewLift 連續插值）。 */
export function exploreFollowCameraY(lookAtY, dist, placementPhi, groundY, viewLift) {
  let cy = lookAtY + dist * Math.cos(placementPhi);
  const eased = viewLift > 0 ? viewLift : 0;
  if (eased > 0) {
    const lowY = Math.max(groundY + 0.02, lookAtY - 0.80);
    const heightT = exploreLiftCameraHeightBlend(eased);
    cy = cy * (1 - heightT) + lowY * heightT;
    return Math.max(groundY + 0.02, cy);
  }
  return Math.max(groundY + 0.88, cy);
}

export function exploreCameraPlacement(
  lookAtX, lookAtY, lookAtZ, dist, placementPhi, theta, groundY, viewLift
) {
  const lift = viewLift > 0 ? viewLift : 0;
  const horiz = Math.max(0.55, dist * Math.sin(placementPhi));
  const cy = exploreFollowCameraY(lookAtY, dist, placementPhi, groundY, lift);
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

/**
 * 目標視線仰角（rad）；viewLift=0 回 null 表示盯角色。
 * 仰角自預設俯視一路平滑增至天空（戶外）或室內上限，單調不反向。
 */
export function exploreTargetPitch(
  safePhi, lookAtY, dist, groundY, aspect, theta, bodyElevation, phiRest, allowSky
) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  const viewLift = exploreViewLift(safePhi, rest);
  if (viewLift <= 0) return null;
  const restPlacement = exploreCameraPlacement(
    0, lookAtY, 0, dist, rest, theta, groundY, 0
  );
  const pitch0 = naturalPitchToPlayer(lookAtY, restPlacement);
  const pitchMax = allowSky === false
    ? INTERIOR_LOOK_UP_PITCH_MAX
    : exploreIdealViewPitch(aspect, bodyElevation);
  return pitch0 + viewLift * (pitchMax - pitch0);
}

/**
 * 實際鏡頭用的視線仰角：在 {@link exploreTargetPitch} 之上收進主角，不影響 NDC／單調測試用的 pitch 值。
 */
export function exploreViewPitch(
  safePhi, lookAtY, dist, groundY, aspect, theta, phiRest, allowSky
) {
  const pitch = exploreTargetPitch(
    safePhi, lookAtY, dist, groundY, aspect, theta, null, phiRest, allowSky
  );
  if (pitch == null) return null;
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  const viewLift = exploreViewLift(safePhi, rest);
  if (viewLift < EXPLORE_PLAYER_PITCH_CAP_MIN_LIFT || allowSky === false) return pitch;
  const placementPhi = explorePlacementPhi(safePhi, rest, viewLift);
  const distUse = dist * exploreLiftDistanceScale(aspect, viewLift);
  const placement = exploreCameraPlacement(
    0, lookAtY, 0, distUse, placementPhi, theta, groundY, viewLift
  );
  const cap = explorePitchCapForPlayer(placement, aspect);
  if (Number.isFinite(cap) && pitch > cap + 0.01) return cap;
  return pitch;
}

/** 沿單向拖曳每 stepPx 取樣視線仰角（純函式，供測試）。totalDyPx 負值＝往上拖。 */
export function explorePitchAlongDrag(
  phiStart, totalDyPx, stepPx, lookAtY, dist, groundY, aspect, theta, bodyElevation, phiRest, allowSky
) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  const step = Math.max(1, stepPx);
  const steps = Math.abs(Math.floor(totalDyPx / step));
  const sign = totalDyPx < 0 ? -1 : 1;
  const samples = [];
  for (let i = 0; i <= steps; i++) {
    const dy = sign * step * i;
    const phi = applyDragPhi(phiStart, dy, true);
    const pitch = exploreSkyPitch(
      lookAtY, dist, phi, groundY, aspect, theta, bodyElevation, rest, allowSky
    );
    samples.push({ dyPx: dy, phi: phi, pitch: pitch });
  }
  return samples;
}

/** 由相機位置與目標仰角、方位角決定 lookAt 點（戶外預設 72；室內用較短距離）。 */
export function exploreLookPointFromPitch(placement, pitch, aimAzimuth, len) {
  if (len == null || !Number.isFinite(len)) len = 72;
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

/** 當前 phi 的視線仰角（供測試）；無抬頭時回傳自然俯視角。 */
export function exploreSkyPitch(
  lookAtY, dist, safePhi, groundY, aspect, theta, bodyElevation, phiRest, allowSky
) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  const viewLift = exploreViewLift(safePhi, rest);
  const distUse = allowSky === false ? dist : dist * exploreLiftDistanceScale(aspect, viewLift);
  const pitch = exploreTargetPitch(
    safePhi, lookAtY, dist, groundY, aspect, theta, bodyElevation, rest, allowSky
  );
  if (pitch != null) return pitch;
  const useInterior = allowSky === false;
  const placementPhi = useInterior
    ? interiorPlacementPhi(safePhi, rest, viewLift)
    : explorePlacementPhi(safePhi, rest, viewLift);
  const liftY = useInterior ? 0 : viewLift;
  const placement = exploreCameraPlacement(
    0, lookAtY, 0, distUse, placementPhi, theta, groundY, liftY
  );
  const pitchDown = naturalPitchToPlayer(lookAtY, placement);
  if (safePhi >= rest - 1e-6) {
    const restPlacement = exploreCameraPlacement(
      0, lookAtY, 0, dist, rest, theta, groundY, 0
    );
    const pitchRest = naturalPitchToPlayer(lookAtY, restPlacement);
    return Math.min(pitchDown, pitchRest);
  }
  return pitchDown;
}

/** 探索跟隨鏡頭一幀（純函式，供測試／室內約束）。 */
export function exploreFollowCameraFrame(
  lookAtX, lookAtY, lookAtZ, dist, safePhi, theta, groundY, aspect,
  phiRest, allowSky, bodyElevation
) {
  const rest = phiRest != null && Number.isFinite(phiRest) ? phiRest : EXPLORE_POSE.phi;
  const viewLift = exploreViewLift(safePhi, rest);
  const indoor = allowSky === false;
  const distUse = indoor ? dist : dist * exploreLiftDistanceScale(aspect, viewLift);
  const placementPhi = indoor
    ? interiorPlacementPhi(safePhi, rest, viewLift)
    : explorePlacementPhi(safePhi, rest, viewLift);
  const liftY = indoor ? 0 : viewLift;
  const placement = exploreCameraPlacement(
    lookAtX, lookAtY, lookAtZ, distUse, placementPhi, theta, groundY, liftY
  );
  const pitch = exploreSkyPitch(
    lookAtY, dist, safePhi, groundY, aspect, theta, bodyElevation, rest, allowSky
  );
  let lookPoint = null;
  if (viewLift > 0 && allowSky !== false) {
    const viewPitch = exploreViewPitch(
      safePhi, lookAtY, dist, groundY, aspect, theta, rest, allowSky
    );
    const aim = exploreAimAzimuth(theta, viewLift);
    if (viewPitch != null) {
      lookPoint = exploreLookPointFromPitch(placement, viewPitch, aim, 72);
    }
  }
  return { placement, placementPhi, viewLift, pitch, lookPoint };
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
  const followDistance = typeof hooks.followDistance === 'function'
    ? hooks.followDistance
    : exploreFollowDistance;
  const phiRestHook = typeof hooks.phiRest === 'function' ? hooks.phiRest : () => EXPLORE_POSE.phi;
  const allowSkyHook = typeof hooks.allowSky === 'function' ? hooks.allowSky : () => true;
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
    const followCam = following();
    const safePhi = followCam ? Math.min(EXPLORE_PHI_MAX, Math.max(EXPLORE_PHI_MIN, phi)) : phi;
    const phiRest = followCam ? phiRestHook() : EXPLORE_POSE.phi;
    const allowSky = allowSkyHook();
    const viewLift = followCam ? exploreViewLift(safePhi, phiRest) : 0;
    const baseDist = followDistance(radius, aspect);
    const dist = followCam && allowSky
      ? baseDist * exploreLiftDistanceScale(aspect, viewLift)
      : baseDist;
    const placementPhi = followCam
      ? (allowSky
        ? explorePlacementPhi(safePhi, phiRest, viewLift)
        : interiorPlacementPhi(safePhi, phiRest, viewLift))
      : safePhi;
    const liftY = followCam && !allowSky ? 0 : viewLift;
    const placement = exploreCameraPlacement(
      lookAt.x, lookAt.y, lookAt.z, dist, placementPhi, theta, groundY, liftY
    );
    camera.position.set(placement.cx, placement.cy, placement.cz);
    if (viewLift > 0) {
      const viewPitch = exploreViewPitch(
        safePhi, lookAt.y, baseDist, groundY, aspect, theta, phiRest, allowSky
      );
      // 視線 yaw 永遠跟玩家水平方向（theta+π），不鎖月亮／太陽方位。
      const aim = exploreAimAzimuth(theta, viewLift);
      if (allowSky && viewPitch != null) {
        const point = exploreLookPointFromPitch(placement, viewPitch, aim, 72);
        camera.lookAt(point.x, point.y, point.z);
      } else {
        camera.lookAt(lookAt);
      }
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
    get radius() { return radius; },
    get theta() { return theta; },
    get phi() { return phi; },
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
