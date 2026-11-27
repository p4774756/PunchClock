import * as THREE from './vendor/three.module.js';
import { POCKET_LCD_H, POCKET_LCD_W, drawPocketMaryPreviewLcd } from './private-team-pocket-lcd.js';
import {
  SECRETARY_SPRITE_URL_TYPING,
  SECRETARY_SPRITE_URL_TYPING_B,
  SECRETARY_SPRITE_URL_WAVE,
  SECRETARY_SPRITE_URL_YEAH,
  SECRETARY_SPRITE_URL_IDLE,
  SECRETARY_SPRITE_URL_READ,
  SECRETARY_SPRITE_URL_PHONE,
  SECRETARY_SPRITE_URL_SLEEP,
  SECRETARY_CLAP_SPRITE_A,
  SECRETARY_CLAP_SPRITE_B,
  SECRETARY_CELEBRATE_STEPS,
  SECRETARY_STANDBY_IDLE_URLS,
  SECRETARY_DANCE_DURATION_MS,
  SECRETARY_IDLE_ROTATE_MS,
  SECRETARY_IDLE_DANCE_AFTER_MS,
  SECRETARY_IDLE_DANCE_COOLDOWN_MS,
  SECRETARY_TYPING_FRAME_MS,
  SECRETARY_WAVE_MS,
  SECRETARY_YEAH_MS,
  SECRETARY_SPRITE_HEIGHT,
  secretaryCelebrateStateAt,
  secretaryCelebrateFrameUrlAt,
  secretaryCelebrateJumpOffsetAt,
  secretaryTypingDisplayUrl,
  shouldTriggerIdleStandbyDance,
  pickNextStandbyIdleUrl,
  shouldIgnoreStandbyIdleSpriteUpdate,
  isSecretaryStandbyIdleUrl,
  isStandbyTigerStatus
} from './private-team-scene-secretary.js';

import {
  clampPrivateTeamZoom,
  privateTeamNarrowPortrait,
  PRIVATE_TEAM_CAMERA_FOV,
  privateTeamDefaultFov,
  privateTeamDefaultYaw,
  privateTeamDefaultZoom,
  privateTeamPinchZoomFactor,
  PRIVATE_TEAM_DRAG_THRESHOLD_PX,
  PRIVATE_TEAM_PAN_HINT_DISMISS_KEY,
  privateTeamOrbitYawReturnEnabled,
  privateTeamDragExceedsThreshold,
  privateTeamYawFromDragDelta,
  privateTeamPanHintShouldShow,
  privateTeamYawDragVelocityRadPerMs,
  privateTeamPitchDragVelocityRadPerMs,
  privateTeamYawMomentumTick,
  privateTeamPitchMomentumTick,
  privateTeamYawReturnProgress,
  privateTeamYawAtReturn,
  privateTeamPitchFromDragDelta,
  PRIVATE_TEAM_TRAY_SURFACE_Y,
  PRIVATE_TEAM_WATER_Y,
  PRIVATE_TEAM_ZOOM_MIN,
  PRIVATE_TEAM_ZOOM_MAX,
  PRIVATE_TEAM_YAW_MAX_DEG,
  PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT,
  PRIVATE_TEAM_YAW_RETURN_MS,
  privateTeamYawMaxRadians,
  clampPrivateTeamYaw,
  clampPrivateTeamYawViewport,
  PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT,
  PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT,
  PRIVATE_TEAM_PITCH_MAX_UP_DEG,
  PRIVATE_TEAM_PITCH_MAX_DOWN_DEG,
  clampPrivateTeamPitch
} from './private-team-scene-controls.js';

import {
  privateTeamSceneLighting,
  privateTeamCandleFlickerMultiplier,
  PRIVATE_TEAM_WINDOW_TEXTURE_URLS,
  privateTeamWindowPeriodFromTaipeiDate,
  privateTeamWindowCyclePreviewMode,
  privateTeamWindowPreviewToastLabel,
  makeWindowMoonlightFloorTexture,
  makeWindowMoonlightWallTexture,
  makeWindowTowerBeaconOverlayTexture,
  makeWindowVistaTrafficOverlayTexture,
  makeWindowRiverShimmerTexture,
  PRIVATE_TEAM_WINDOW_SKY_Z,
  PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX,
  privateTeamWindowSkylineParallaxOffset,
  privateTeamWindowAmbientMotionEnabled,
  privateTeamWindowTowerBeaconOpacity,
  privateTeamWindowAirplaneBeaconOpacity
} from './private-team-scene-window.js';

import {
  box,
  cyl,
  loadRepeatTex,
  makeStoneTileTexture,
  makeWaterRippleTexture,
  makeBathtubCausticsTexture,
  addRomanceCandle,
  addBathPillow,
  makeScreenPainter,
  makeBubbleDrawer,
  lampGlowTexture,
  softShadowTexture,
  spriteAspectFromTexture,
  addMonitor,
  makeHandheldPreviewLcdTexture,
  addWindowSillHandheld,
  addLowPolyPlant,
  applyUnderwaterTint,
  extrudedRoundedPlate,
  extrudedRoundedRing,
  FLOOR_SIZE,
  FLOOR_HALF,
  WALL_THICK,
  COL,
  LEFT_WALL_POSTER_URLS,
  nextLeftPosterIndex,
  readLeftPosterIndexFromStorage,
  formatLeftPosterIndexForStorage,
  PRIVATE_TEAM_LEFT_POSTER_IDX_KEY,
  parseLeftPosterIndex
} from './private-team-scene-room.js';

export {
  SECRETARY_SPRITE_URL_TYPING,
  SECRETARY_SPRITE_URL_TYPING_B,
  SECRETARY_SPRITE_URL_WAVE,
  SECRETARY_SPRITE_URL_YEAH,
  SECRETARY_SPRITE_URL_IDLE,
  SECRETARY_SPRITE_URL_READ,
  SECRETARY_SPRITE_URL_PHONE,
  SECRETARY_SPRITE_URL_SLEEP,
  SECRETARY_STANDBY_IDLE_URLS,
  SECRETARY_IDLE_ROTATE_MS,
  SECRETARY_CLAP_SPRITE_A,
  SECRETARY_CLAP_SPRITE_B,
  SECRETARY_CELEBRATE_STEPS,
  SECRETARY_DANCE_DURATION_MS,
  SECRETARY_IDLE_DANCE_AFTER_MS,
  SECRETARY_IDLE_DANCE_COOLDOWN_MS,
  secretaryCelebrateStateAt,
  secretaryCelebrateFrameUrlAt,
  secretaryCelebrateJumpOffsetAt,
  isStandbyTigerStatus,
  shouldTriggerIdleStandbyDance,
  secretaryTypingDisplayUrl,
  isSecretaryStandbyIdleUrl,
  shouldIgnoreStandbyIdleSpriteUpdate,
  pickNextStandbyIdleUrl,
  SECRETARY_TYPING_FRAME_MS,
  SECRETARY_WAVE_MS,
  SECRETARY_YEAH_MS,
  SECRETARY_SPRITE_HEIGHT,
  PRIVATE_TEAM_ZOOM_MIN,
  PRIVATE_TEAM_ZOOM_MAX,
  PRIVATE_TEAM_TRAY_SURFACE_Y,
  PRIVATE_TEAM_WATER_Y,
  clampPrivateTeamZoom,
  privateTeamNarrowPortrait,
  PRIVATE_TEAM_CAMERA_FOV,
  PRIVATE_TEAM_CAMERA_FOV_NARROW_PORTRAIT,
  PRIVATE_TEAM_DEFAULT_YAW_NARROW_PORTRAIT,
  privateTeamDefaultFov,
  privateTeamDefaultYaw,
  privateTeamDefaultZoom,
  privateTeamPinchZoomFactor,
  PRIVATE_TEAM_YAW_MAX_DEG,
  PRIVATE_TEAM_YAW_MAX_DEG_NARROW_PORTRAIT,
  PRIVATE_TEAM_DRAG_THRESHOLD_PX,
  PRIVATE_TEAM_YAW_RETURN_MS,
  PRIVATE_TEAM_PAN_HINT_DISMISS_KEY,
  privateTeamYawMaxRadians,
  privateTeamOrbitYawReturnEnabled,
  clampPrivateTeamYaw,
  clampPrivateTeamYawViewport,
  privateTeamDragExceedsThreshold,
  privateTeamYawFromDragDelta,
  privateTeamPanHintShouldShow,
  privateTeamYawDragVelocityRadPerMs,
  privateTeamYawMomentumTick,
  privateTeamYawReturnProgress,
  privateTeamYawAtReturn,
  PRIVATE_TEAM_PITCH_MAX_UP_DEG,
  PRIVATE_TEAM_PITCH_MAX_DOWN_DEG,
  clampPrivateTeamPitch,
  privateTeamPitchFromDragDelta,
  privateTeamPitchDragVelocityRadPerMs,
  privateTeamPitchMomentumTick,
  privateTeamSceneLighting,
  privateTeamCandleFlickerMultiplier,
  PRIVATE_TEAM_WINDOW_TEXTURE_URLS,
  privateTeamWindowPeriodFromTaipeiDate,
  privateTeamWindowCyclePreviewMode,
  privateTeamWindowPreviewToastLabel,
  PRIVATE_TEAM_WINDOW_SKY_Z,
  privateTeamWindowAmbientMotionEnabled,
  PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX,
  privateTeamWindowSkylineParallaxOffset,
  privateTeamWindowTowerBeaconOpacity,
  privateTeamWindowAirplaneBeaconOpacity,
  PRIVATE_TEAM_LEFT_POSTER_IDX_KEY,
  LEFT_WALL_POSTER_URLS,
  parseLeftPosterIndex,
  nextLeftPosterIndex,
  readLeftPosterIndexFromStorage,
  formatLeftPosterIndexForStorage
};

export function mountPrivateTeamScene(canvas, opts) {
  const win = (opts && opts.win) || globalThis;
  const doc = (opts && opts.doc) || globalThis.document;

  const renderer = new THREE.WebGLRenderer({ canvas, antialias: true, alpha: false });
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  renderer.outputColorSpace = THREE.SRGBColorSpace;

  function scenePixelRatio() {
    const coarse =
      typeof win.matchMedia === 'function' && win.matchMedia('(pointer: coarse)').matches;
    const narrow = (win.innerWidth || 1024) < 640;
    const cap = coarse || narrow ? 1.5 : 2;
    return Math.min(win.devicePixelRatio || 1, cap);
  }
  renderer.setPixelRatio(scenePixelRatio());

  const scene = new THREE.Scene();
  const sceneNow = () => ((opts && opts.now) ? opts.now() : new Date());
  let windowPreviewMode = 'auto';

  const camLook = new THREE.Vector3(0, 0.92, -0.08);
  const camBase = new THREE.Vector3(3.25, 3.5, 4.15);
  const camYawAxis = new THREE.Vector3(0, 1, 0);
  let zoomFactor = 1;
  let userAdjustedZoom = false;
  let userAdjustedOrbit = false;
  let defaultZoomApplied = false;
  let yawRadians = 0;
  let pitchRadians = 0;
  let yawReturnFrom = 0;
  let pitchReturnFrom = 0;
  let yawReturnStartMs = null;
  let yawMomentumVel = 0;
  let pitchMomentumVel = 0;
  const camOrbitDir = new THREE.Vector3();
  const camPitchAxis = new THREE.Vector3();
  let panHintEl = null;
  let panHintHideTimer = 0;

  function sceneViewportSize() {
    const parent = canvas.parentElement;
    const w = parent ? parent.clientWidth : win.innerWidth || 0;
    const h = parent ? parent.clientHeight : win.innerHeight || 0;
    return { w, h };
  }

  function panHintDismissed() {
    try {
      return win.localStorage.getItem(PRIVATE_TEAM_PAN_HINT_DISMISS_KEY) === '1';
    } catch (_e) {
      return false;
    }
  }

  function dismissPanHint(persist) {
    if (panHintHideTimer) {
      win.clearTimeout(panHintHideTimer);
      panHintHideTimer = 0;
    }
    if (panHintEl) {
      panHintEl.hidden = true;
      panHintEl.setAttribute('aria-hidden', 'true');
    }
    if (persist) {
      try {
        win.localStorage.setItem(PRIVATE_TEAM_PAN_HINT_DISMISS_KEY, '1');
      } catch (_e) { /* noop */ }
    }
  }

  function syncPanHint() {
    const stage = canvas.parentElement;
    if (!stage) return;
    const vp = sceneViewportSize();
    const show = privateTeamPanHintShouldShow(privateTeamNarrowPortrait(vp.w, vp.h), panHintDismissed());
    if (!show) {
      if (panHintEl) panHintEl.hidden = true;
      return;
    }
    if (!panHintEl) {
      panHintEl = doc.createElement('div');
      panHintEl.className = 'pt-pan-hint';
      panHintEl.textContent = '左右滑動';
      panHintEl.setAttribute('aria-hidden', 'true');
      stage.appendChild(panHintEl);
      panHintHideTimer = win.setTimeout(() => dismissPanHint(true), 9000);
    }
    panHintEl.hidden = false;
  }

  const camera = new THREE.PerspectiveCamera(PRIVATE_TEAM_CAMERA_FOV, 1, 0.1, 80);
  function applyCameraView() {
    zoomFactor = clampPrivateTeamZoom(zoomFactor);
    camOrbitDir.copy(camBase).sub(camLook);
    camOrbitDir.applyAxisAngle(camYawAxis, yawRadians);
    camPitchAxis.crossVectors(camYawAxis, camOrbitDir);
    if (camPitchAxis.lengthSq() < 1e-12) {
      camPitchAxis.set(1, 0, 0);
    } else {
      camPitchAxis.normalize();
    }
    camOrbitDir.applyAxisAngle(camPitchAxis, pitchRadians);
    camera.position.copy(camLook).add(camOrbitDir.multiplyScalar(zoomFactor));
    camera.lookAt(camLook);
    camera.updateMatrixWorld(true);
  }
  function applyZoom(factor, fromUser) {
    zoomFactor = clampPrivateTeamZoom(factor);
    if (fromUser) userAdjustedZoom = true;
    applyCameraView();
  }
  applyCameraView();

  let windowVistaPeriod = privateTeamWindowPeriodFromTaipeiDate(sceneNow());
  const lighting = privateTeamSceneLighting(windowVistaPeriod);
  scene.background = new THREE.Color(lighting.background);
  const amb = new THREE.AmbientLight(lighting.ambColor, lighting.amb);
  const dir = new THREE.DirectionalLight(lighting.dirColor, lighting.dir + 0.06);
  dir.position.set(1.4, 6.2, -7.5);
  scene.add(amb, dir);

  const texLoader = new THREE.TextureLoader();
  const wallTex = loadRepeatTex(texLoader, 'wall.webp', 2.2, 1.4);
  const floorTex = loadRepeatTex(texLoader, 'floor-wood.webp', 2.4, 2.4);
  const deskTex = loadRepeatTex(texLoader, 'desk-wood.webp', 2.2, 1.2);

  const root = new THREE.Group();
  scene.add(root);

  const floorMat = new THREE.MeshLambertMaterial({ map: floorTex, color: 0xffffff });
  const floor = extrudedRoundedPlate(FLOOR_HALF, FLOOR_HALF, 0.035, 0.08, floorMat);
  floor.position.y = 0;
  root.add(floor);

  const TRAY_THICK = 0.036;
  const TRAY_TOP_Y = PRIVATE_TEAM_TRAY_SURFACE_Y;
  const trayZ = 0.12;
  const WATER_Y = PRIVATE_TEAM_WATER_Y;
  const RIM_TOP_Y = 0.62;
  const TUB_CX = 0;
  const TUB_CZ = 0.64;
  const TUB_HW = 0.84;
  const TUB_HD = 0.52;
  const WALL_T = 0.08;
  const IN_HW = TUB_HW - WALL_T;
  const IN_HD = TUB_HD - WALL_T;
  const outerH = RIM_TOP_Y - 0.02;
  const outerMidY = 0.02 + outerH / 2;
  const tileTex = makeStoneTileTexture(8, 3);
  const tileSideTex = makeStoneTileTexture(8, 4);
  const outerMat = new THREE.MeshLambertMaterial({ map: tileSideTex, color: COL.tub });
  const innerMat = new THREE.MeshLambertMaterial({ color: COL.tubInner });
  const rimMat = new THREE.MeshLambertMaterial({ map: tileTex, color: COL.tubRim });
  const waterYUniform = { value: WATER_Y };

  const tubFloor = box(IN_HW * 2 - 0.02, 0.03, IN_HD * 2 - 0.02, COL.tubInner);
  tubFloor.position.set(TUB_CX, 0.03, TUB_CZ);
  tubFloor.name = 'bathtubFloor';
  root.add(tubFloor);

  const frontZ = TUB_CZ + TUB_HD - WALL_T / 2;
  const backZTub = TUB_CZ - TUB_HD + WALL_T / 2;
  const tubFront = box(TUB_HW * 2, outerH, WALL_T, COL.tub);
  tubFront.material = outerMat.clone();
  tubFront.position.set(TUB_CX, outerMidY, frontZ);
  tubFront.name = 'bathtubFront';
  root.add(tubFront);
  const tubBack = box(TUB_HW * 2, outerH, WALL_T, COL.tub);
  tubBack.material = outerMat.clone();
  tubBack.position.set(TUB_CX, outerMidY, backZTub);
  tubBack.name = 'bathtubBack';
  root.add(tubBack);
  const tubLeft = box(WALL_T, outerH, TUB_HD * 2 - WALL_T * 2, COL.tub);
  tubLeft.material = outerMat.clone();
  tubLeft.position.set(TUB_CX - TUB_HW + WALL_T / 2, outerMidY, TUB_CZ);
  tubLeft.name = 'bathtubLeft';
  root.add(tubLeft);
  const tubRight = box(WALL_T, outerH, TUB_HD * 2 - WALL_T * 2, COL.tub);
  tubRight.material = outerMat.clone();
  tubRight.position.set(TUB_CX + TUB_HW - WALL_T / 2, outerMidY, TUB_CZ);
  tubRight.name = 'bathtubRight';
  root.add(tubRight);

  const rim = extrudedRoundedRing(TUB_HW + 0.03, TUB_HD + 0.03, IN_HW, IN_HD, 0.055, 0.07, rimMat);
  rim.position.set(TUB_CX, RIM_TOP_Y - 0.045, TUB_CZ);
  rim.name = 'bathtubRim';
  root.add(rim);
  const liningH = RIM_TOP_Y - 0.08;
  const liningY = 0.04 + liningH / 2;
  const liningT = 0.02;
  function addLining(name, w, h, d, x, y, z) {
    const mesh = box(w, h, d, COL.tubInner);
    mesh.position.set(x, y, z);
    mesh.name = name;
    root.add(mesh);
    return mesh;
  }
  addLining('bathtubInnerFront', IN_HW * 2, liningH, liningT, TUB_CX, liningY, TUB_CZ + IN_HD - liningT / 2);
  addLining('bathtubInnerBack', IN_HW * 2, liningH, liningT, TUB_CX, liningY, TUB_CZ - IN_HD + liningT / 2);
  addLining('bathtubInnerLeft', liningT, liningH, IN_HD * 2 - liningT * 2, TUB_CX - IN_HW + liningT / 2, liningY, TUB_CZ);
  addLining('bathtubInnerRight', liningT, liningH, IN_HD * 2 - liningT * 2, TUB_CX + IN_HW - liningT / 2, liningY, TUB_CZ);

  const waterMat = new THREE.MeshLambertMaterial({
    color: COL.water,
    transparent: true,
    opacity: 0.9,
    depthWrite: false
  });
  const water = extrudedRoundedPlate(IN_HW - 0.02, IN_HD - 0.02, 0.012, 0.05, waterMat);
  water.position.set(TUB_CX, WATER_Y, TUB_CZ);
  water.name = 'bathtubWater';
  water.renderOrder = 1;
  root.add(water);
  const waterRippleTex = makeWaterRippleTexture();
  const rippleMat = new THREE.MeshBasicMaterial({
    map: waterRippleTex,
    color: COL.waterHi,
    transparent: true,
    opacity: 0.28,
    depthWrite: false
  });
  const waterRipple = extrudedRoundedPlate(IN_HW - 0.04, IN_HD - 0.04, 0.004, 0.05, rippleMat);
  waterRipple.position.set(TUB_CX, WATER_Y + 0.008, TUB_CZ);
  waterRipple.name = 'bathtubWaterHighlight';
  waterRipple.renderOrder = 1;
  root.add(waterRipple);

  const causticsTex = makeBathtubCausticsTexture();
  const causticsMat = new THREE.MeshBasicMaterial({
    map: causticsTex,
    color: 0xb0dcff,
    transparent: true,
    opacity: 0.44,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const bathtubCaustics = new THREE.Mesh(new THREE.PlaneGeometry(1.42, 0.9), causticsMat);
  bathtubCaustics.rotation.x = -Math.PI / 2;
  bathtubCaustics.position.set(TUB_CX, 0.021, TUB_CZ + 0.52);
  bathtubCaustics.name = 'bathtubCaustics';
  root.add(bathtubCaustics);
  const tubFloorCaustics = new THREE.Mesh(new THREE.PlaneGeometry(0.55, 0.38), causticsMat.clone());
  tubFloorCaustics.rotation.x = -Math.PI / 2;
  tubFloorCaustics.position.set(TUB_CX, WATER_Y + 0.006, TUB_CZ);
  tubFloorCaustics.name = 'bathtubWaterCaustics';
  tubFloorCaustics.renderOrder = 2;
  root.add(tubFloorCaustics);

  const trayMat = new THREE.MeshLambertMaterial({ map: deskTex, color: COL.desk });
  const tray = extrudedRoundedPlate(0.54, 0.26, TRAY_THICK, 0.04, trayMat);
  tray.position.set(0, TRAY_TOP_Y - TRAY_THICK, trayZ);
  tray.name = 'keyboardTray';
  root.add(tray);
  const steamPuffs = [];
  const steamGroup = new THREE.Group();
  steamGroup.position.set(-0.08, WATER_Y + 0.04, TUB_CZ + 0.22);
  steamGroup.name = 'tubSteam';
  for (let si = 0; si < 4; si++) {
    const puffMat = new THREE.MeshBasicMaterial({
      color: 0xf8fbff,
      transparent: true,
      opacity: 0.07,
      depthWrite: false
    });
    const puff = new THREE.Mesh(new THREE.SphereGeometry(0.035 + si * 0.008, 6, 5), puffMat);
    puff.position.set((si - 1.5) * 0.055, si * 0.045, 0);
    steamGroup.add(puff);
    steamPuffs.push(puff);
  }
  root.add(steamGroup);

  const wallMat = new THREE.MeshLambertMaterial({ map: wallTex, color: 0xd8e4f4 });
  const backZ = -FLOOR_HALF;
  const leftX = -FLOOR_HALF;
  const wallY = 1.22;
  const wallH = 2.44;

  function wallSlab(w, h, x, y, z, depthZ) {
    const dz = depthZ == null ? WALL_THICK : depthZ;
    const m = new THREE.Mesh(new THREE.BoxGeometry(w, h, dz), wallMat);
    m.position.set(x, y, z);
    root.add(m);
  }

  const winW = 2.55;
  const winH = 1.72;
  const winBottom = 0.38;
  const winTop = winBottom + winH;
  const winLeft = -winW / 2;
  const winRight = winW / 2;

  wallSlab(FLOOR_HALF - winRight, wallH, (winRight + FLOOR_HALF) / 2, wallY, backZ - WALL_THICK / 2);
  wallSlab(FLOOR_HALF + winLeft, wallH, (winLeft - FLOOR_HALF) / 2, wallY, backZ - WALL_THICK / 2);
  wallSlab(winW, wallH - winTop, 0, winTop + (wallH - winTop) / 2 - 0.02, backZ - WALL_THICK / 2);
  wallSlab(winW, winBottom, 0, winBottom / 2, backZ - WALL_THICK / 2);
  wallSlab(WALL_THICK, wallH, leftX + WALL_THICK / 2, wallY, 0, FLOOR_SIZE);

  const tubBackWarmWash = new THREE.Mesh(
    new THREE.PlaneGeometry(1.55, 0.42),
    new THREE.MeshBasicMaterial({
      color: 0xffc890,
      transparent: true,
      opacity: 0.22,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
  );
  // 貼在浴缸後牆，不要放在天花板高度（常用機位會看成窗上橫條）。
  tubBackWarmWash.position.set(TUB_CX, 0.38, backZTub - WALL_THICK / 2 - 0.01);
  tubBackWarmWash.name = 'tubBackWarmWash';
  root.add(tubBackWarmWash);
  // 不再放天花板水平亮帶：從常用機位會看成窗戶上方的破圖橫條。暖色洗牆改由 tubBackWarmWash 承擔。

  const coarsePointer =
    typeof win.matchMedia === 'function' && win.matchMedia('(pointer: coarse)').matches;
  const narrowWin = (win.innerWidth || 1024) < 640;

  const vistaCenterY = winBottom + winH / 2;
  const vistaParallaxTargets = [];
  function trackVistaParallax(mesh, bx, by, bz, parallaxScale) {
    vistaParallaxTargets.push({ mesh, bx, by, bz, parallaxScale });
  }
  function syncWindowVistaParallax() {
    for (let ti = 0; ti < vistaParallaxTargets.length; ti++) {
      const t = vistaParallaxTargets[ti];
      const off = privateTeamWindowSkylineParallaxOffset(
        yawRadians,
        pitchRadians,
        t.parallaxScale,
        reducedMotion
      );
      t.mesh.position.set(t.bx + off.x, t.by + off.y, t.bz);
    }
  }

  const vistaTex = {};
  const vistaPeriodKeys = ['day', 'sunset', 'night'];
  for (let vi = 0; vi < vistaPeriodKeys.length; vi++) {
    const vk = vistaPeriodKeys[vi];
    const vurl = PRIVATE_TEAM_WINDOW_TEXTURE_URLS[vk];
    const vtx = texLoader.load(vurl);
    vtx.colorSpace = THREE.SRGBColorSpace;
    vistaTex[vk] = vtx;
  }

  const windowVistaMat = new THREE.MeshBasicMaterial({ map: vistaTex[windowVistaPeriod] });
  const windowSky = new THREE.Mesh(new THREE.PlaneGeometry(winW + 0.02, winH + 0.02), windowVistaMat);
  const windowSkyY = vistaCenterY;
  const windowSkyZ = backZ - WALL_THICK - PRIVATE_TEAM_WINDOW_SKY_Z;
  windowSky.position.set(0, windowSkyY, windowSkyZ);
  windowSky.name = 'windowVista';
  root.add(windowSky);
  trackVistaParallax(windowSky, 0, windowSkyY, windowSkyZ, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.sky);

  const towerBeacon = makeWindowTowerBeaconOverlayTexture();
  const windowVistaBeacon = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.02, winH + 0.02),
    new THREE.MeshBasicMaterial({
      map: towerBeacon.tex,
      transparent: true,
      depthWrite: false,
      opacity: 0.82,
      blending: THREE.AdditiveBlending
    })
  );
  const beaconZ = windowSkyZ + 0.008;
  windowVistaBeacon.position.set(0, windowSkyY, beaconZ);
  windowVistaBeacon.name = 'windowVistaBeacon';
  root.add(windowVistaBeacon);
  trackVistaParallax(windowVistaBeacon, 0, windowSkyY, beaconZ, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.flicker);

  const streetTrafficTex = makeWindowVistaTrafficOverlayTexture();
  const windowStreetTraffic = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.02, winH * 0.32),
    new THREE.MeshBasicMaterial({
      map: streetTrafficTex,
      transparent: true,
      depthWrite: false,
      opacity: 0.72,
      blending: THREE.AdditiveBlending
    })
  );
  const trafficY = winBottom + winH * 0.18;
  const trafficZ = beaconZ + 0.006;
  windowStreetTraffic.position.set(0, trafficY, trafficZ);
  windowStreetTraffic.name = 'windowStreetTraffic';
  root.add(windowStreetTraffic);
  trackVistaParallax(windowStreetTraffic, 0, trafficY, trafficZ, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.near);

  const riverShimmerTex = makeWindowRiverShimmerTexture();
  const windowVistaRiver = new THREE.Mesh(
    new THREE.PlaneGeometry(winW + 0.02, winH * 0.18),
    new THREE.MeshBasicMaterial({
      map: riverShimmerTex,
      transparent: true,
      depthWrite: false,
      opacity: 0.38,
      blending: THREE.AdditiveBlending
    })
  );
  const riverY = winBottom + winH * 0.08;
  const riverZ = trafficZ + 0.004;
  windowVistaRiver.position.set(0, riverY, riverZ);
  windowVistaRiver.name = 'windowVistaRiver';
  root.add(windowVistaRiver);
  trackVistaParallax(windowVistaRiver, 0, riverY, riverZ, PRIVATE_TEAM_WINDOW_SKYLINE_PARALLAX.mid);

  const glassY = winBottom + winH / 2;
  const glassMat = new THREE.MeshLambertMaterial({
    color: 0x6e88c8,
    emissive: 0x243878,
    emissiveIntensity: 0.42,
    transparent: true,
    opacity: 0.11,
    depthWrite: false
  });
  const paneW = (winW - 0.12) / 3;
  const glassZ = backZ + 0.02;
  const windowVistaClickables = [];
  [-paneW - 0.04, 0, paneW + 0.04].forEach((px) => {
    const glass = new THREE.Mesh(new THREE.BoxGeometry(paneW, winH - 0.08, 0.02), glassMat);
    glass.position.set(px, glassY, glassZ);
    glass.name = 'windowGlass';
    glass.userData.pickRole = 'windowVista';
    root.add(glass);
    windowVistaClickables.push(glass);
  });

  function frameBar(w, h, x, y) {
    const m = box(w, h, 0.09, COL.frame);
    m.position.set(x, y, backZ + 0.06);
    m.userData.pickRole = 'windowVista';
    root.add(m);
    windowVistaClickables.push(m);
  }
  frameBar(0.11, winH, winLeft - 0.055, glassY);
  frameBar(0.11, winH, winRight + 0.055, glassY);
  frameBar(winW + 0.14, 0.1, 0, winBottom - 0.02);
  frameBar(winW + 0.14, 0.1, 0, winTop + 0.02);
  frameBar(0.09, winH, -paneW / 2 - 0.02, glassY);
  frameBar(0.09, winH, paneW / 2 + 0.02, glassY);
  frameBar(winW + 0.14, 0.05, 0, glassY);

  const sillZ = backZ + 0.13;
  const sillY = winBottom + 0.03;
  const windowSill = box(winW + 0.04, 0.06, 0.16, COL.frame);
  windowSill.position.set(0, sillY, sillZ);
  windowSill.name = 'windowSill';
  root.add(windowSill);

  const sillPlantX = -0.38;
  const sillPlantPot = cyl(0.045, 0.038, 0.055, 8, COL.pot);
  sillPlantPot.position.set(sillPlantX, sillY + 0.05, sillZ + 0.06);
  sillPlantPot.name = 'windowSillPlant';
  root.add(sillPlantPot);
  const sillLeafMat = new THREE.MeshLambertMaterial({ color: COL.plant });
  for (let pi = 0; pi < 4; pi++) {
    const leaf = new THREE.Mesh(new THREE.SphereGeometry(0.032, 5, 4), sillLeafMat);
    const pa = (pi / 4) * Math.PI * 2;
    leaf.position.set(
      sillPlantX + Math.cos(pa) * 0.038,
      sillY + 0.1 + (pi % 2) * 0.025,
      sillZ + 0.06 + Math.sin(pa) * 0.032
    );
    leaf.scale.set(0.9, 0.45, 0.8);
    root.add(leaf);
  }

  const sillMugX = 0.4;
  const windowSillMug = new THREE.Mesh(
    new THREE.CylinderGeometry(0.048, 0.04, 0.1, 10),
    new THREE.MeshLambertMaterial({ color: COL.mug })
  );
  windowSillMug.position.set(sillMugX, sillY + 0.07, sillZ + 0.07);
  windowSillMug.name = 'windowSillMug';
  root.add(windowSillMug);

  const windowSillMugSteamPuffs = [];
  const windowSillMugSteam = new THREE.Group();
  windowSillMugSteam.position.set(sillMugX, sillY + 0.13, sillZ + 0.08);
  windowSillMugSteam.name = 'windowSillMugSteam';
  for (let si = 0; si < 3; si++) {
    const puffMat = new THREE.MeshBasicMaterial({
      color: 0xf0f6ff,
      transparent: true,
      opacity: 0.06,
      depthWrite: false
    });
    const puff = new THREE.Mesh(new THREE.SphereGeometry(0.022 + si * 0.006, 5, 4), puffMat);
    puff.position.set((si - 1) * 0.028, si * 0.032, 0);
    windowSillMugSteam.add(puff);
    windowSillMugSteamPuffs.push(puff);
  }
  root.add(windowSillMugSteam);

  const windowSillHandheld = addWindowSillHandheld(root, sillY, sillZ, sillMugX);

  const moonTex = makeWindowMoonlightFloorTexture();
  moonTex.repeat.set(1.1, 1);
  const moonFloorMat = new THREE.MeshBasicMaterial({
    map: moonTex,
    transparent: true,
    opacity: 0.9,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const windowMoonlight = new THREE.Mesh(new THREE.PlaneGeometry(2.28, 0.9), moonFloorMat);
  windowMoonlight.rotation.x = -Math.PI / 2;
  windowMoonlight.rotation.z = 0.34;
  windowMoonlight.position.set(0.28, 0.056, -0.98);
  windowMoonlight.name = 'windowMoonlight';
  root.add(windowMoonlight);
  const moonWallTex = makeWindowMoonlightWallTexture();
  const moonWallMat = new THREE.MeshBasicMaterial({
    map: moonWallTex,
    transparent: true,
    opacity: 0.52,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  // 貼在窗下牆帶，不要蓋到窗洞（舊版中心在 winBottom+0.22，大半落在窗玻璃上所以幾乎看不見）。
  const windowMoonlightWall = new THREE.Mesh(new THREE.PlaneGeometry(winW + 0.2, winBottom + 0.04), moonWallMat);
  windowMoonlightWall.position.set(0, winBottom / 2 + 0.01, backZ + 0.012);
  windowMoonlightWall.name = 'windowMoonlightWall';
  root.add(windowMoonlightWall);
  const flankW = FLOOR_HALF - winRight;
  const flankXOff = (winRight + FLOOR_HALF) / 2;
  const flankH = 1.55;
  const flankY = 0.92;
  const flankZ = backZ + 0.012;
  const flankMat = moonWallMat.clone();
  flankMat.opacity = 0.4;
  const windowMoonlightWallRight = new THREE.Mesh(new THREE.PlaneGeometry(flankW, flankH), flankMat);
  windowMoonlightWallRight.position.set(flankXOff, flankY, flankZ);
  windowMoonlightWallRight.name = 'windowMoonlightWallRight';
  root.add(windowMoonlightWallRight);
  const windowMoonlightWallLeftFlank = new THREE.Mesh(new THREE.PlaneGeometry(flankW, flankH), flankMat.clone());
  windowMoonlightWallLeftFlank.position.set(-flankXOff, flankY, flankZ);
  windowMoonlightWallLeftFlank.name = 'windowMoonlightWallLeftFlank';
  root.add(windowMoonlightWallLeftFlank);
  const leftWallMat = moonWallMat.clone();
  leftWallMat.opacity = 0.34;
  const windowMoonlightLeftWall = new THREE.Mesh(
    new THREE.PlaneGeometry(FLOOR_SIZE - 0.14, 1.68),
    leftWallMat
  );
  windowMoonlightLeftWall.rotation.y = Math.PI / 2;
  windowMoonlightLeftWall.position.set(leftX + WALL_THICK + 0.012, 1.02, 0);
  windowMoonlightLeftWall.name = 'windowMoonlightLeftWall';
  root.add(windowMoonlightLeftWall);

  const LEFT_POSTER_W = 0.82;
  const LEFT_POSTER_H = (LEFT_POSTER_W * 9) / 16;
  const leftPosterFrameW = 0.026;
  const leftPosterGroup = new THREE.Group();
  leftPosterGroup.name = 'leftWallPoster';
  leftPosterGroup.position.set(leftX + WALL_THICK + 0.01, 1.42, -0.32);
  leftPosterGroup.rotation.y = Math.PI / 2;
  leftPosterGroup.userData.pickRole = 'leftPoster';

  const posterTextures = [];
  for (let li = 0; li < LEFT_WALL_POSTER_URLS.length; li++) {
    const ptx = texLoader.load(LEFT_WALL_POSTER_URLS[li]);
    ptx.colorSpace = THREE.SRGBColorSpace;
    posterTextures.push(ptx);
  }
  let leftPosterIndex = readLeftPosterIndexFromStorage((key) => {
    try {
      return win.localStorage && win.localStorage.getItem(key);
    } catch (_e) {
      return null;
    }
  });
  const posterMat = new THREE.MeshLambertMaterial({
    map: posterTextures[leftPosterIndex] || posterTextures[0],
    color: 0xffffff,
    emissive: 0x000000,
    emissiveIntensity: 0
  });
  const posterPlane = new THREE.Mesh(new THREE.PlaneGeometry(LEFT_POSTER_W, LEFT_POSTER_H), posterMat);
  posterPlane.position.set(0, 0, 0.022);
  posterPlane.userData.pickRole = 'leftPoster';
  leftPosterGroup.add(posterPlane);

  const frameDk = 0x241c14;
  const frameMetal = 0x2c3438;
  function addPosterFrameBar(w, h, d, x, y, z) {
    const bar = box(w, h, d, frameDk);
    bar.position.set(x, y, z);
    leftPosterGroup.add(bar);
  }
  addPosterFrameBar(
    LEFT_POSTER_W + 0.04,
    leftPosterFrameW,
    0.032,
    0,
    LEFT_POSTER_H / 2 + leftPosterFrameW / 2,
    0.018
  );
  addPosterFrameBar(
    LEFT_POSTER_W + 0.04,
    leftPosterFrameW,
    0.032,
    0,
    -(LEFT_POSTER_H / 2 + leftPosterFrameW / 2),
    0.018
  );
  addPosterFrameBar(
    leftPosterFrameW,
    LEFT_POSTER_H,
    0.032,
    -(LEFT_POSTER_W / 2 + leftPosterFrameW / 2),
    0,
    0.018
  );
  addPosterFrameBar(
    leftPosterFrameW,
    LEFT_POSTER_H,
    0.032,
    LEFT_POSTER_W / 2 + leftPosterFrameW / 2,
    0,
    0.018
  );

  const picLightBar = box(LEFT_POSTER_W * 0.68, 0.016, 0.03, frameMetal);
  picLightBar.material.emissive = new THREE.Color(0xffb878);
  picLightBar.material.emissiveIntensity = 0.42;
  picLightBar.position.set(0, LEFT_POSTER_H / 2 + 0.12, 0.028);
  leftPosterGroup.add(picLightBar);

  const posterWarmWash = new THREE.Mesh(
    new THREE.PlaneGeometry(LEFT_POSTER_W + 0.06, LEFT_POSTER_H + 0.14),
    new THREE.MeshBasicMaterial({
      color: 0xffc888,
      transparent: true,
      opacity: 0.09,
      depthWrite: false,
      blending: THREE.AdditiveBlending
    })
  );
  posterWarmWash.position.set(0, -0.02, 0.018);
  leftPosterGroup.add(posterWarmWash);

  const posterPicLight = new THREE.PointLight(0xffc890, 0.32, 1.2, 2);
  posterPicLight.position.set(0, LEFT_POSTER_H / 2 + 0.1, 0.22);
  leftPosterGroup.add(posterPicLight);

  root.add(leftPosterGroup);

  const romanceCandles = [];
  romanceCandles.push(
    addRomanceCandle(root, -0.14, sillY + 0.1, sillZ + 0.1, 'windowSillCandle')
  );
  romanceCandles.push(
    addRomanceCandle(root, TUB_CX - 0.58, RIM_TOP_Y + 0.02, TUB_CZ + 0.34, 'bathtubEdgeCandle')
  );

  const monitorLook = new THREE.Vector3(0.02, 1.02, 0.72);
  const monitors = [addMonitor(root, monitorLook, -0.96, 0.94, 0.98, 0.02)];

  const lampGroup = new THREE.Group();
  lampGroup.position.set(0.78, TRAY_TOP_Y + 0.016, 0.36);
  lampGroup.rotation.y = 1.35;
  const lampBase = cyl(0.07, 0.095, 0.045, 12, COL.lampBase);
  lampBase.position.y = 0.022;
  lampGroup.add(lampBase);
  const lampStem = cyl(0.016, 0.016, 0.1, 8, COL.lampMetal);
  lampStem.position.y = 0.09;
  lampGroup.add(lampStem);
  const lampArm = box(0.22, 0.028, 0.028, COL.lampMetal);
  lampArm.position.set(0.07, 0.16, 0);
  lampArm.rotation.z = -0.22;
  lampGroup.add(lampArm);
  const lampShadeMat = new THREE.MeshLambertMaterial({
    color: COL.lampShade,
    emissive: 0xffb060,
    emissiveIntensity: lighting.shadeEmissive
  });
  const lampShade = new THREE.Mesh(
    new THREE.CylinderGeometry(0.05, 0.085, 0.075, 12),
    lampShadeMat
  );
  lampShade.position.set(0.16, 0.19, 0);
  lampGroup.add(lampShade);
  const lampBulbMat = new THREE.MeshBasicMaterial({
    color: 0xfff2d8,
    transparent: true,
    opacity: lighting.bulbOpacity,
    depthWrite: false
  });
  const lampBulb = new THREE.Mesh(new THREE.SphereGeometry(0.028, 10, 10), lampBulbMat);
  lampBulb.position.set(0.16, 0.142, 0);
  lampGroup.add(lampBulb);
  const lampHaloMat = new THREE.MeshBasicMaterial({
    map: lampGlowTexture(),
    transparent: true,
    opacity: lighting.haloOpacity,
    depthWrite: false,
    blending: THREE.AdditiveBlending
  });
  const lampHalo = new THREE.Mesh(new THREE.PlaneGeometry(0.36, 0.36), lampHaloMat);
  lampHalo.position.set(0.16, 0.18, 0.02);
  lampGroup.add(lampHalo);
  root.add(lampGroup);

  const lampLightLocal = new THREE.Vector3(0.16, 0.17, 0);
  const lampPoint = new THREE.PointLight(0xffc888, lighting.point, lighting.pointDistance, 1.65);
  lampPoint.position.copy(lampLightLocal);
  lampGroup.add(lampPoint);
  const lampSpot = new THREE.SpotLight(
    0xffc888,
    lighting.spot,
    lighting.spotDistance,
    Math.PI / 2.55,
    lighting.spotPenumbra,
    lighting.spotDecay
  );
  lampSpot.position.copy(lampLightLocal);
  const lampTarget = new THREE.Object3D();
  lampGroup.add(lampTarget);
  lampGroup.updateMatrixWorld(true);
  lampTarget.position.copy(lampGroup.worldToLocal(new THREE.Vector3(0.2, 0.52, 0.35)));
  lampSpot.target = lampTarget;
  lampGroup.add(lampSpot);

  let lampPointBase = lighting.point;
  let lampSpotBase = lighting.spot;
  let lampHaloBase = lighting.haloOpacity;
  let candlePeriodMul = lighting.candleMul;
  let lastAutoPeriodCheckMs = 0;
  let vistaToastEl = null;
  let vistaToastTimer = 0;

  function resolveVistaPeriod() {
    if (windowPreviewMode === 'auto') {
      return privateTeamWindowPeriodFromTaipeiDate(sceneNow());
    }
    return windowPreviewMode;
  }

  function showWindowVistaToast(mode) {
    const stage = canvas.parentElement;
    if (!stage) return;
    if (vistaToastTimer) {
      win.clearTimeout(vistaToastTimer);
      vistaToastTimer = 0;
    }
    if (!vistaToastEl) {
      vistaToastEl = doc.createElement('div');
      vistaToastEl.className = 'pt-window-vista-toast';
      vistaToastEl.setAttribute('role', 'status');
      vistaToastEl.setAttribute('aria-live', 'polite');
      stage.appendChild(vistaToastEl);
    }
    vistaToastEl.textContent = privateTeamWindowPreviewToastLabel(mode);
    vistaToastEl.hidden = false;
    vistaToastTimer = win.setTimeout(() => {
      if (vistaToastEl) vistaToastEl.hidden = true;
      vistaToastTimer = 0;
    }, 1800);
  }

  function applyWindowScenePeriod(period) {
    const lt = privateTeamSceneLighting(period);
    windowVistaPeriod = period;
    amb.color.setHex(lt.ambColor);
    amb.intensity = lt.amb;
    dir.color.setHex(lt.dirColor);
    dir.intensity = lt.dir + 0.06;
    if (scene.background && scene.background.isColor) {
      scene.background.setHex(lt.background);
    }
    if (vistaTex[period]) {
      windowVistaMat.map = vistaTex[period];
      windowVistaMat.needsUpdate = true;
    }
    moonFloorMat.opacity = lt.floorWash;
    moonWallMat.opacity = lt.wallWash;
    windowMoonlightWallRight.material.opacity = lt.wallWash * 0.78;
    windowMoonlightWallLeftFlank.material.opacity = lt.wallWash * 0.78;
    windowMoonlightLeftWall.material.opacity = lt.wallWash * 0.66;
    tubBackWarmWash.material.opacity = lt.tubWarm;
    lampPointBase = lt.point;
    lampSpotBase = lt.spot;
    lampHaloBase = lt.haloOpacity;
    lampShadeMat.emissiveIntensity = lt.shadeEmissive;
    lampBulbMat.opacity = lt.bulbOpacity;
    lampPoint.intensity = lt.point;
    lampSpot.intensity = lt.spot;
    lampHaloMat.opacity = lt.haloOpacity;
    candlePeriodMul = lt.candleMul;
    windowVistaBeacon.visible = period === 'night' || period === 'sunset';
    const riverOp = period === 'day' ? 0.22 : period === 'sunset' ? 0.32 : 0.38;
    windowVistaRiver.material.opacity = riverOp;
    windowStreetTraffic.material.opacity = period === 'day' ? 0.55 : 0.72;
  }

  applyWindowScenePeriod(windowVistaPeriod);

  let reducedMotion = false;
  let lastFrameMs = 0;

  addLowPolyPlant(root, -1.15, 0.82);

  const secretaryRoot = new THREE.Group();
  secretaryRoot.position.set(0.02, 0, 0.56);
  root.add(secretaryRoot);

  const shadowMat = new THREE.MeshBasicMaterial({
    map: softShadowTexture(),
    transparent: true,
    depthWrite: false
  });
  const shadow = new THREE.Mesh(new THREE.PlaneGeometry(1.16, 0.6), shadowMat);
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.set(0.06, 0.072, -0.42);
  shadow.renderOrder = 1;
  secretaryRoot.add(shadow);
  addBathPillow(secretaryRoot);

  let secretarySprite = null;
  const spriteBaseY = SECRETARY_SPRITE_HEIGHT / 2 + 0.02;
  let spriteAnimMode = 'idle';
  let statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
  let typingShowB = false;
  let lastTypingFlipMs = 0;
  let overlayActive = null;
  let overlayPending = null;
  let dancePlayback = null;
  let tigerStatus = '待命中';
  let standbySinceMs = performance.now();
  let lastIdleRotateMs = null;
  let lastIdleDanceMs = null;
  let danceDisplayFrame = -1;
  let currentSpriteUrl = '';
  let desiredSpriteUrl = '';
  let spriteLoadSeq = 0;
  const loader = new THREE.TextureLoader();

  (function preloadSecretaryCelebrateFrames() {
    const seen = new Set();
    for (let i = 0; i < SECRETARY_CELEBRATE_STEPS.length; i++) {
      const step = SECRETARY_CELEBRATE_STEPS[i];
      if (step.kind !== 'frame') continue;
      const url = step.url;
      if (!url || seen.has(url)) continue;
      seen.add(url);
      loader.load(url, () => {}, undefined, () => {});
    }
    for (let i = 0; i < SECRETARY_STANDBY_IDLE_URLS.length; i++) {
      const url = SECRETARY_STANDBY_IDLE_URLS[i];
      if (url) loader.load(url, () => {}, undefined, () => {});
    }
  })();

  function refreshPoseAnimMode() {
    spriteAnimMode = spriteAnimModeForUrl(statusBaseUrl);
  }

  function resolveRestSpriteUrl() {
    return secretaryTypingDisplayUrl(statusBaseUrl, typingShowB, reducedMotion);
  }

  function applySecretarySpriteUrl(url) {
    const next = url == null ? '' : String(url).trim();
    if (!next || next === desiredSpriteUrl) return;
    desiredSpriteUrl = next;
    const seq = ++spriteLoadSeq;
    loader.load(
      next,
      (tex) => {
        if (seq !== spriteLoadSeq || desiredSpriteUrl !== next) {
          disposeUnusedSpriteTexture(tex);
          return;
        }
        if (secretarySprite) retextureSecretarySprite(tex, next);
        else mountSecretarySprite(tex, next);
      },
      undefined,
      () => {
        if (seq === spriteLoadSeq) desiredSpriteUrl = currentSpriteUrl;
      }
    );
  }

  function refreshSecretaryDisplay() {
    if (dancePlayback) return;
    if (overlayActive) {
      applySecretarySpriteUrl(overlayActive.url);
      return;
    }
    applySecretarySpriteUrl(resolveRestSpriteUrl());
  }

  function stopSecretaryDance() {
    dancePlayback = null;
    danceDisplayFrame = -1;
    refreshSecretaryDisplay();
  }

  function playSecretaryDance(now) {
    if (reducedMotion) return;
    const t = Number.isFinite(now) ? now : performance.now();
    overlayActive = null;
    overlayPending = null;
    dancePlayback = { startMs: t };
    danceDisplayFrame = -1;
    const url = secretaryCelebrateFrameUrlAt(0);
    if (url) applySecretarySpriteUrl(url);
  }

  function noteTigerStatus(status, now) {
    const next = status == null ? '' : String(status).trim() || '待命中';
    const t = Number.isFinite(now) ? now : performance.now();
    const wasStandby = isStandbyTigerStatus(tigerStatus);
    tigerStatus = next;
    if (isStandbyTigerStatus(tigerStatus)) {
      if (!wasStandby || standbySinceMs == null) {
        standbySinceMs = t;
        lastIdleRotateMs = t;
      }
    } else {
      standbySinceMs = null;
      lastIdleRotateMs = null;
    }
  }

  function endSecretaryOverlay(now) {
    overlayActive = null;
    if (overlayPending) {
      const pending = overlayPending;
      overlayPending = null;
      overlayActive = { url: pending.url, until: now + pending.ms };
      applySecretarySpriteUrl(pending.url);
      return;
    }
    refreshSecretaryDisplay();
  }

  function enqueueSecretaryOverlay(url, ms, now) {
    if (overlayActive && overlayActive.url === url) {
      overlayActive.until = Math.max(overlayActive.until, now + ms);
      return;
    }
    if (overlayActive) {
      overlayPending = { url, ms };
      return;
    }
    overlayActive = { url, until: now + ms };
    applySecretarySpriteUrl(url);
  }

  function playSecretaryWave() {
    if (dancePlayback) return;
    enqueueSecretaryOverlay(SECRETARY_SPRITE_URL_WAVE, SECRETARY_WAVE_MS, performance.now());
  }

  function playSecretaryYeah() {
    if (dancePlayback) return;
    if (overlayActive && overlayActive.url === SECRETARY_SPRITE_URL_YEAH) {
      return;
    }
    enqueueSecretaryOverlay(SECRETARY_SPRITE_URL_YEAH, SECRETARY_YEAH_MS, performance.now());
  }

  function spriteAnimModeForUrl(url) {
    if (url === SECRETARY_SPRITE_URL_TYPING || url === SECRETARY_SPRITE_URL_TYPING_B) return 'working';
    if (url === SECRETARY_SPRITE_URL_SLEEP) return 'sleep';
    return 'idle';
  }

  function mountSecretarySprite(tex, url) {
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.premultiplyAlpha = true;
    const aspect = spriteAspectFromTexture(tex);
    const spriteW = SECRETARY_SPRITE_HEIGHT * aspect;
    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      transparent: true,
      alphaTest: 0.02,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    applyUnderwaterTint(mat, waterYUniform);
    secretarySprite = new THREE.Mesh(new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT), mat);
    secretarySprite.position.set(0, spriteBaseY, 0);
    secretarySprite.renderOrder = 2;
    secretarySprite.name = 'secretarySprite';
    secretaryRoot.add(secretarySprite);
    secretarySprite.userData.pickRole = 'secretary';
    if (clickables.indexOf(secretarySprite) === -1) {
      clickables.push(secretarySprite);
    }
    currentSpriteUrl = url;
  }

  function retextureSecretarySprite(tex, url) {
    tex.colorSpace = THREE.SRGBColorSpace;
    tex.premultiplyAlpha = true;
    const aspect = spriteAspectFromTexture(tex);
    const spriteW = SECRETARY_SPRITE_HEIGHT * aspect;
    const prev = secretarySprite.geometry;
    secretarySprite.geometry = new THREE.PlaneGeometry(spriteW, SECRETARY_SPRITE_HEIGHT);
    if (prev) prev.dispose();
    const oldMap = secretarySprite.material.map;
    secretarySprite.material.map = tex;
    secretarySprite.material.needsUpdate = true;
    if (oldMap && oldMap !== tex) oldMap.dispose();
    if (dancePlayback) {
      secretarySprite.position.x = 0;
      secretarySprite.position.z = 0;
    } else {
      secretarySprite.position.set(0, spriteBaseY, 0);
      secretarySprite.rotation.set(0, 0, 0);
    }
    currentSpriteUrl = url;
  }

  function disposeUnusedSpriteTexture(tex) {
    if (tex && typeof tex.dispose === 'function') tex.dispose();
  }

  function setSecretarySprite(url) {
    const next = url == null ? '' : String(url).trim();
    if (!next) return;
    if (shouldIgnoreStandbyIdleSpriteUpdate(statusBaseUrl, next)) return;
    if (next === statusBaseUrl) return;
    statusBaseUrl = next;
    typingShowB = false;
    lastTypingFlipMs = performance.now();
    refreshPoseAnimMode();
    refreshSecretaryDisplay();
  }

  refreshPoseAnimMode();
  applySecretarySpriteUrl(resolveRestSpriteUrl());

  const secretaryBubbleX = 0.56;
  const bubbleDrawer = makeBubbleDrawer();
  const bubbleMat = new THREE.MeshBasicMaterial({
    map: bubbleDrawer.tex,
    transparent: true,
    depthWrite: false
  });
  const bubble = new THREE.Mesh(new THREE.PlaneGeometry(0.78, 0.3), bubbleMat);
  const bubbleFloatMin = 1.68;
  const bubbleFloatSpan = 0.07;
  const bubbleRestY = bubbleFloatMin + bubbleFloatSpan / 2;
  bubble.position.set(secretaryBubbleX, bubbleRestY, 0.72);
  bubble.renderOrder = 3;
  bubble.name = 'secretaryBubble';
  bubble.userData.pickRole = 'secretary';
  root.add(bubble);

  const clickables = [windowSillHandheld.group, bubble, leftPosterGroup];
  for (let wi = 0; wi < windowVistaClickables.length; wi++) {
    clickables.push(windowVistaClickables[wi]);
  }
  for (let mi = 0; mi < monitors.length; mi++) {
    clickables.push(monitors[mi].group);
  }
  const raycaster = new THREE.Raycaster();
  raycaster.params.Line.threshold = 0.08;
  raycaster.params.Points.threshold = 0.08;
  const pointer = new THREE.Vector2();
  const onActivate = typeof (opts && opts.onActivate) === 'function' ? opts.onActivate : null;
  let sceneInputEnabled = true;
  let monitorHovered = false;
  let monitorHighlightUntil = 0;
  let handheldHovered = false;
  let handheldHighlightUntil = 0;
  let leftPosterHovered = false;
  let leftPosterHighlightUntil = 0;

  function applyLeftPosterHighlight(hovered, tapMs) {
    leftPosterHovered = !!hovered;
    if (tapMs) leftPosterHighlightUntil = tapMs;
    if (!posterMat || !posterMat.emissive) return;
    const on = leftPosterHovered || performance.now() < leftPosterHighlightUntil;
    posterMat.emissive.setHex(on ? 0x6a5040 : 0x000000);
    posterMat.emissiveIntensity = on ? 0.2 : 0;
  }

  function applyHandheldHighlight(hovered, tapMs) {
    handheldHovered = !!hovered;
    if (tapMs) handheldHighlightUntil = tapMs;
    const body = windowSillHandheld.body;
    const mat = body && body.material;
    if (!mat || !mat.emissive) return;
    const on = handheldHovered || performance.now() < handheldHighlightUntil;
    mat.emissive.setHex(on ? 0x4a8a48 : 0x000000);
    mat.emissiveIntensity = on ? 0.35 : 0;
  }

  function applyMonitorHighlight(hovered, tapMs) {
    monitorHovered = !!hovered;
    if (tapMs) monitorHighlightUntil = tapMs;
  }

  function monitorHighlightActive(now) {
    return monitorHovered || now < monitorHighlightUntil;
  }

  function setMonitorBezelHighlight(on) {
    for (let mi = 0; mi < monitors.length; mi++) {
      const bezel = monitors[mi].bezel;
      const mat = bezel && bezel.material;
      if (!mat || !mat.emissive) continue;
      mat.emissive.setHex(on ? 0x8ec8ff : 0x000000);
      mat.emissiveIntensity = on ? 0.65 : 0;
    }
  }

  function pickRoleFromObject(obj) {
    let cur = obj;
    while (cur) {
      const role = cur.userData && cur.userData.pickRole;
      if (role) return role;
      cur = cur.parent;
    }
    return null;
  }

  /** 多重重疊時優先回傳掌機，避免窗台區被對話泡泡等較近 pick 搶走。 */
  function pickRoleFromHits(hits) {
    let fallback = null;
    for (let i = 0; i < hits.length; i++) {
      const role = pickRoleFromObject(hits[i].object);
      if (!role) continue;
      if (role === 'handheld') return 'handheld';
      if (!fallback) fallback = role;
    }
    return fallback;
  }

  const activePointers = new Map();
  let pinchActive = false;
  let pinchStartDist = 0;
  let pinchStartZoom = 1;
  let orbitPointerId = null;
  let orbitStartX = 0;
  let orbitStartY = 0;
  let orbitStartYaw = 0;
  let orbitStartPitch = 0;
  let orbitDragged = false;
  let orbitPendingRole = null;
  let orbitLastMoveMs = 0;
  let orbitLastClientX = 0;
  let orbitLastClientY = 0;

  function pointerDistance() {
    const pts = [...activePointers.values()];
    if (pts.length < 2) return 0;
    const dx = pts[0].x - pts[1].x;
    const dy = pts[0].y - pts[1].y;
    return Math.hypot(dx, dy);
  }

  function pickTarget(clientX, clientY) {
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    pointer.x = ((clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((clientY - rect.top) / rect.height) * 2 + 1;
    camera.updateMatrixWorld(true);
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(clickables, true);
    if (!hits.length) return null;
    return pickRoleFromHits(hits);
  }

  const projectWorld = new THREE.Vector3();
  function projectPickRoleToClient(role) {
    let obj = null;
    if (role === 'handheld') obj = windowSillHandheld && windowSillHandheld.group;
    else if (role === 'secretary') obj = bubble;
    if (!obj) return null;
    camera.updateMatrixWorld(true);
    obj.updateWorldMatrix(true, false);
    obj.getWorldPosition(projectWorld);
    projectWorld.project(camera);
    const rect = canvas.getBoundingClientRect();
    if (!rect.width || !rect.height) return null;
    const visible =
      Math.abs(projectWorld.x) <= 1 &&
      Math.abs(projectWorld.y) <= 1 &&
      projectWorld.z >= -1 &&
      projectWorld.z <= 1;
    return {
      x: rect.left + ((projectWorld.x + 1) / 2) * rect.width,
      y: rect.top + ((1 - projectWorld.y) / 2) * rect.height,
      ndcX: projectWorld.x,
      ndcY: projectWorld.y,
      visible
    };
  }

  function resetOrbitGesture() {
    orbitPointerId = null;
    orbitPendingRole = null;
    orbitDragged = false;
  }

  function activatePickRole(target) {
    if (!target) return;
    if (target === 'windowVista') {
      windowPreviewMode = privateTeamWindowCyclePreviewMode(windowPreviewMode);
      showWindowVistaToast(windowPreviewMode);
      applyWindowScenePeriod(resolveVistaPeriod());
      return;
    }
    if (target === 'leftPoster') {
      leftPosterIndex = nextLeftPosterIndex(leftPosterIndex);
      if (posterTextures[leftPosterIndex]) posterMat.map = posterTextures[leftPosterIndex];
      try {
        if (win.localStorage) {
          win.localStorage.setItem(
            PRIVATE_TEAM_LEFT_POSTER_IDX_KEY,
            formatLeftPosterIndexForStorage(leftPosterIndex)
          );
        }
      } catch (_e) { /* noop */ }
      applyLeftPosterHighlight(true, performance.now() + 260);
      return;
    }
    if (target === 'secretary') playSecretaryWave();
    if (target === 'monitor') applyMonitorHighlight(true, performance.now() + 240);
    if (target === 'handheld') applyHandheldHighlight(true, performance.now() + 280);
    if (onActivate) onActivate(target);
  }

  function handlePointerDown(ev) {
    if (!sceneInputEnabled) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (activePointers.size >= 2) {
      pinchActive = true;
      pinchStartDist = pointerDistance();
      pinchStartZoom = zoomFactor;
      resetOrbitGesture();
      ev.preventDefault();
      return;
    }
    if (pinchActive) return;
    orbitPointerId = ev.pointerId;
    orbitStartX = ev.clientX;
    orbitStartY = ev.clientY;
    orbitStartYaw = yawRadians;
    orbitStartPitch = pitchRadians;
    orbitDragged = false;
    orbitPendingRole = pickTarget(ev.clientX, ev.clientY);
    orbitLastMoveMs = performance.now();
    orbitLastClientX = ev.clientX;
    orbitLastClientY = ev.clientY;
    yawReturnStartMs = null;
    yawMomentumVel = 0;
    pitchMomentumVel = 0;
    if (orbitPendingRole) {
      ev.preventDefault();
    }
  }

  function handlePointerMove(ev) {
    if (!activePointers.has(ev.pointerId)) return;
    activePointers.set(ev.pointerId, { x: ev.clientX, y: ev.clientY });
    if (pinchActive && activePointers.size >= 2 && pinchStartDist > 0) {
      const dist = pointerDistance();
      applyZoom(privateTeamPinchZoomFactor(pinchStartZoom, pinchStartDist, dist), true);
      ev.preventDefault();
      return;
    }
    if (
      !pinchActive &&
      sceneInputEnabled &&
      orbitPointerId === ev.pointerId &&
      activePointers.size === 1
    ) {
      const dx = ev.clientX - orbitStartX;
      const dy = ev.clientY - orbitStartY;
      if (!orbitDragged && privateTeamDragExceedsThreshold(dx, dy)) {
        orbitDragged = true;
        userAdjustedOrbit = true;
        try {
          canvas.setPointerCapture(ev.pointerId);
        } catch (_e) { /* noop */ }
      }
      if (orbitDragged) {
        const rect = canvas.getBoundingClientRect();
        const vp = sceneViewportSize();
        const now = performance.now();
        orbitLastClientX = ev.clientX;
        orbitLastClientY = ev.clientY;
        orbitLastMoveMs = now;
        const canvasH = vp.h || rect.height;
        yawRadians = privateTeamYawFromDragDelta(
          dx,
          rect.width,
          orbitStartYaw,
          canvasH
        );
        pitchRadians = privateTeamPitchFromDragDelta(dy, canvasH, orbitStartPitch);
        applyCameraView();
        ev.preventDefault();
        canvas.style.cursor = 'grabbing';
        return;
      }
    }
    if (!pinchActive && sceneInputEnabled && !orbitDragged) {
      updatePointerHover(ev.clientX, ev.clientY);
    }
  }

  function updatePointerHover(clientX, clientY) {
    if (!sceneInputEnabled || pinchActive || orbitDragged) {
      if (!sceneInputEnabled) {
        canvas.style.cursor = '';
        applyMonitorHighlight(false);
        applyHandheldHighlight(false);
        applyLeftPosterHighlight(false);
        setMonitorBezelHighlight(false);
      }
      return;
    }
    const hover = pickTarget(clientX, clientY);
    canvas.style.cursor = hover ? 'pointer' : '';
    const mon = hover === 'monitor';
    const hand = hover === 'handheld';
    const poster = hover === 'leftPoster';
    applyMonitorHighlight(mon);
    applyHandheldHighlight(hand);
    applyLeftPosterHighlight(poster);
    if (hand && !mon) {
      canvas.style.cursor = 'pointer';
    }
    setMonitorBezelHighlight(mon || monitorHighlightActive(performance.now()));
  }

  function handleHoverMove(ev) {
    if (orbitDragged || pinchActive) return;
    updatePointerHover(ev.clientX, ev.clientY);
  }

  function handlePointerLeave() {
    if (orbitDragged || pinchActive) return;
    canvas.style.cursor = '';
    applyMonitorHighlight(false);
    applyLeftPosterHighlight(false);
    setMonitorBezelHighlight(monitorHighlightActive(performance.now()));
  }

  function clearPointer(ev) {
    activePointers.delete(ev.pointerId);
    if (activePointers.size < 2) {
      pinchActive = false;
      pinchStartDist = 0;
    }
  }

  function handlePointerUp(ev) {
    if (ev.pointerId === orbitPointerId) {
      if (!orbitDragged && orbitPendingRole) {
        activatePickRole(orbitPendingRole);
      } else if (orbitDragged) {
        dismissPanHint(true);
        const vp = sceneViewportSize();
        const rect = canvas.getBoundingClientRect();
        const orbitNeedsReturn =
          privateTeamOrbitYawReturnEnabled(vp.w, vp.h) &&
          (Math.abs(yawRadians) > 1e-6 || Math.abs(pitchRadians) > 1e-6);
        if (orbitNeedsReturn) {
          yawReturnFrom = yawRadians;
          pitchReturnFrom = pitchRadians;
          yawReturnStartMs = performance.now();
        } else if (
          privateTeamNarrowPortrait(vp.w, vp.h) &&
          !reducedMotion
        ) {
          const dt = Math.max(performance.now() - orbitLastMoveMs, 16);
          const canvasH = vp.h || rect.height;
          const recentDx = ev.clientX - orbitLastClientX;
          const recentDy = ev.clientY - orbitLastClientY;
          yawMomentumVel = privateTeamYawDragVelocityRadPerMs(
            recentDx,
            dt,
            rect.width,
            canvasH
          );
          pitchMomentumVel = privateTeamPitchDragVelocityRadPerMs(recentDy, dt, canvasH);
        }
      }
      resetOrbitGesture();
      canvas.style.cursor = '';
      try {
        if (canvas.hasPointerCapture(ev.pointerId)) {
          canvas.releasePointerCapture(ev.pointerId);
        }
      } catch (_e) { /* noop */ }
    }
    clearPointer(ev);
  }

  function onWheel(ev) {
    if (!sceneInputEnabled) return;
    ev.preventDefault();
    const delta = ev.deltaY;
    const step = delta > 0 ? 0.045 : -0.045;
    applyZoom(zoomFactor * (1 + step), true);
  }

  canvas.addEventListener('pointerdown', handlePointerDown);
  canvas.addEventListener('pointermove', handlePointerMove);
  canvas.addEventListener('pointermove', handleHoverMove);
  canvas.addEventListener('pointerleave', handlePointerLeave);
  canvas.addEventListener('pointerup', handlePointerUp);
  canvas.addEventListener('pointercancel', handlePointerUp);
  canvas.addEventListener('wheel', onWheel, { passive: false });

  let running = true;
  let animationPaused = false;
  if (typeof win.matchMedia === 'function') {
    const mq = win.matchMedia('(prefers-reduced-motion: reduce)');
    reducedMotion = !!mq.matches;
    if (typeof mq.addEventListener === 'function') {
      mq.addEventListener('change', () => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        if (
          reducedMotion &&
          isStandbyTigerStatus(tigerStatus) &&
          isSecretaryStandbyIdleUrl(statusBaseUrl) &&
          statusBaseUrl !== SECRETARY_SPRITE_URL_IDLE
        ) {
          statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
          lastIdleRotateMs = null;
        }
        refreshSecretaryDisplay();
      });
    } else if (typeof mq.addListener === 'function') {
      mq.addListener(() => {
        reducedMotion = !!mq.matches;
        typingShowB = false;
        if (reducedMotion && dancePlayback) stopSecretaryDance();
        if (
          reducedMotion &&
          isStandbyTigerStatus(tigerStatus) &&
          isSecretaryStandbyIdleUrl(statusBaseUrl) &&
          statusBaseUrl !== SECRETARY_SPRITE_URL_IDLE
        ) {
          statusBaseUrl = SECRETARY_SPRITE_URL_IDLE;
          lastIdleRotateMs = null;
        }
        refreshSecretaryDisplay();
      });
    }
  }
  let raf = 0;
  let t0 = performance.now();
  function resize() {
    const parent = canvas.parentElement;
    const w = parent ? parent.clientWidth : 1;
    const h = parent ? parent.clientHeight : 1;
    renderer.setPixelRatio(scenePixelRatio());
    renderer.setSize(w, h, false);
    camera.aspect = w / Math.max(h, 1);
    camera.fov = privateTeamDefaultFov(w, h);
    camera.updateProjectionMatrix();
    if (!defaultZoomApplied && !userAdjustedZoom) {
      if (!userAdjustedOrbit) {
        yawRadians = privateTeamDefaultYaw(w, h);
        pitchRadians = 0;
      }
      applyZoom(privateTeamDefaultZoom(w, h));
      defaultZoomApplied = true;
    }
    syncPanHint();
  }

  function tick(now) {
    if (!running) return;
    raf = win.requestAnimationFrame(tick);
    const frameDt = lastFrameMs ? now - lastFrameMs : 0;
    lastFrameMs = now;
    if (animationPaused) return;
    if (dancePlayback) {
      const elapsed = now - dancePlayback.startMs;
      if (elapsed >= SECRETARY_DANCE_DURATION_MS) {
        stopSecretaryDance();
      } else {
        const st = secretaryCelebrateStateAt(elapsed);
        if (st.kind !== 'done') {
          const frameKey = st.kind === 'jump' ? 'jump' : st.url;
          if (frameKey !== danceDisplayFrame) {
            danceDisplayFrame = frameKey;
            if (st.url) applySecretarySpriteUrl(st.url);
          }
          dancePlayback.jumpOffset =
            st.kind === 'jump' ? secretaryCelebrateJumpOffsetAt(elapsed) : null;
        }
      }
    } else if (
      shouldTriggerIdleStandbyDance({
        status: tigerStatus,
        standbySinceMs,
        now,
        lastIdleDanceMs,
        dancing: false,
        reducedMotion
      })
    ) {
      lastIdleDanceMs = now;
      playSecretaryDance(now);
    }
    if (overlayActive && now >= overlayActive.until) {
      endSecretaryOverlay(now);
    } else if (
      !overlayActive &&
      !dancePlayback &&
      !reducedMotion &&
      statusBaseUrl === SECRETARY_SPRITE_URL_TYPING &&
      now - lastTypingFlipMs >= SECRETARY_TYPING_FRAME_MS
    ) {
      lastTypingFlipMs = now;
      typingShowB = !typingShowB;
      refreshSecretaryDisplay();
    } else if (
      !overlayActive &&
      !dancePlayback &&
      !reducedMotion &&
      isStandbyTigerStatus(tigerStatus) &&
      isSecretaryStandbyIdleUrl(statusBaseUrl) &&
      lastIdleRotateMs != null &&
      now - lastIdleRotateMs >= SECRETARY_IDLE_ROTATE_MS
    ) {
      lastIdleRotateMs = now;
      statusBaseUrl = pickNextStandbyIdleUrl(statusBaseUrl, Math.floor(Math.random() * 1e6));
      refreshPoseAnimMode();
      refreshSecretaryDisplay();
    }
    const elapsed = (now - t0) / 1000;
    const breath = Math.sin(elapsed * 1.65) * 0.014;
    const typeNudge = Math.sin(elapsed * 7.2) * 0.008;
    const lean = Math.sin(elapsed * 7.2 + 0.4) * 0.018;
    if (secretarySprite) {
      let extraY = 0;
      let leanZ = 0;
      if (!reducedMotion) {
        if (dancePlayback && dancePlayback.jumpOffset != null) {
          extraY = dancePlayback.jumpOffset;
        } else {
          extraY = breath;
          if (spriteAnimMode === 'working') {
            extraY += typeNudge;
            leanZ = lean;
          } else if (spriteAnimMode === 'sleep') {
            extraY = breath * 0.45;
          }
        }
      }
      secretarySprite.position.y = spriteBaseY + extraY;
      secretarySprite.rotation.z = leanZ;
      secretarySprite.lookAt(camera.position);
    }

    if (reducedMotion) {
      bubble.position.y = bubbleRestY;
      bubbleMat.opacity = 0.92;
    } else {
      const bubblePhase = (Math.sin(elapsed * 2.2) + 1) / 2;
      bubble.position.y = bubbleFloatMin + bubblePhase * bubbleFloatSpan;
      bubbleMat.opacity = 0.82 + bubblePhase * 0.18;
    }

    const pulse = (Math.sin(elapsed * 3) + 1) / 2;
    for (let mi = 0; mi < monitors.length; mi++) {
      monitors[mi].painter.draw(pulse);
      const hi = monitorHighlightActive(now);
      monitors[mi].screenGlow.intensity = (hi ? 0.72 : 0.22) + pulse * (hi ? 0.28 : 0.12);
      if (!monitorHovered && now >= monitorHighlightUntil) {
        setMonitorBezelHighlight(false);
      } else if (hi) {
        setMonitorBezelHighlight(true);
      }
    }

    if (reducedMotion) {
      for (let si = 0; si < steamPuffs.length; si++) {
        steamPuffs[si].material.opacity = 0;
      }
      rippleMat.opacity = 0.16;
      causticsMat.opacity = 0.28;
      tubFloorCaustics.material.opacity = 0.2;
      for (let mi = 0; mi < windowSillMugSteamPuffs.length; mi++) {
        windowSillMugSteamPuffs[mi].material.opacity = 0.045 + mi * 0.018;
        windowSillMugSteamPuffs[mi].position.y = mi * 0.032 + 0.018;
      }
    } else {
      for (let si = 0; si < steamPuffs.length; si++) {
        const steamPhase = (Math.sin(elapsed * 1.4 + si * 0.7) + 1) / 2;
        steamPuffs[si].material.opacity = 0.04 + steamPhase * 0.06;
        steamPuffs[si].position.y = si * 0.045 + steamPhase * 0.025;
      }
      for (let mi = 0; mi < windowSillMugSteamPuffs.length; mi++) {
        const mugPhase = (Math.sin(elapsed * 0.85 + mi * 1.1) + 1) / 2;
        windowSillMugSteamPuffs[mi].material.opacity = 0.03 + mugPhase * 0.07;
        windowSillMugSteamPuffs[mi].position.y = mi * 0.028 + mugPhase * 0.055;
      }
      waterRippleTex.offset.x = (elapsed * 0.03) % 1;
      waterRippleTex.offset.y = (elapsed * 0.045) % 1;
      rippleMat.opacity = 0.2 + ((Math.sin(elapsed * 1.1) + 1) / 2) * 0.12;
      const causticsRate = coarsePointer || narrowWin ? 0.012 : 0.028;
      causticsTex.offset.x = (elapsed * causticsRate) % 1;
      causticsTex.offset.y = (elapsed * causticsRate * 0.82) % 1;
      causticsMat.opacity = 0.38 + ((Math.sin(elapsed * 0.95) + 1) / 2) * 0.14;
      tubFloorCaustics.material.opacity = 0.28 + ((Math.sin(elapsed * 1.25) + 1) / 2) * 0.16;
    }

    const candleLowMotion = coarsePointer || narrowWin;
    for (let ci = 0; ci < romanceCandles.length; ci++) {
      const c = romanceCandles[ci];
      const flick = privateTeamCandleFlickerMultiplier(elapsed + ci * 0.37, reducedMotion, candleLowMotion);
      c.glow.intensity = c.baseIntensity * candlePeriodMul * flick;
      c.flameMat.opacity = reducedMotion ? 0.75 : 0.82 + (flick - 0.76) * 0.35;
    }

    if (lampPointBase > 0) {
      const lampPulse = reducedMotion ? 1 : 0.93 + ((Math.sin(elapsed * 3.6) + 1) / 2) * 0.07;
      lampPoint.intensity = lampPointBase * lampPulse;
      lampSpot.intensity = lampSpotBase * lampPulse;
      lampHaloMat.opacity =
        lampHaloBase * (reducedMotion ? 1 : 0.88 + ((Math.sin(elapsed * 3.6 + 0.8) + 1) / 2) * 0.12);
    }

    if (windowPreviewMode === 'auto' && now - lastAutoPeriodCheckMs >= 60000) {
      lastAutoPeriodCheckMs = now;
      const autoPeriod = privateTeamWindowPeriodFromTaipeiDate(sceneNow());
      if (autoPeriod !== windowVistaPeriod) {
        applyWindowScenePeriod(autoPeriod);
      }
    }

    if (privateTeamWindowAmbientMotionEnabled(reducedMotion)) {
      streetTrafficTex.offset.x = (elapsed * 0.048) % 1;
      riverShimmerTex.offset.x = (elapsed * 0.02) % 1;
      towerBeacon.paint(elapsed);
      towerBeacon.tex.needsUpdate = true;
      const beaconOp = privateTeamWindowTowerBeaconOpacity(elapsed, reducedMotion);
      windowVistaBeacon.material.opacity =
        windowVistaPeriod === 'day' ? 0 : beaconOp * (windowVistaPeriod === 'sunset' ? 0.62 : 0.82);
    } else {
      streetTrafficTex.offset.x = 0;
      riverShimmerTex.offset.x = 0;
      towerBeacon.paint(null);
      towerBeacon.tex.needsUpdate = true;
      windowVistaBeacon.material.opacity = windowVistaPeriod === 'night' ? 0.72 : 0;
    }

    if (
      !reducedMotion &&
      (Math.abs(yawMomentumVel) > 1e-6 || Math.abs(pitchMomentumVel) > 1e-6)
    ) {
      const vp = sceneViewportSize();
      if (privateTeamNarrowPortrait(vp.w, vp.h)) {
        const yawMotion = privateTeamYawMomentumTick(
          yawRadians,
          yawMomentumVel,
          frameDt,
          vp.w,
          vp.h
        );
        yawRadians = yawMotion.yaw;
        yawMomentumVel = yawMotion.vel;
        const pitchMotion = privateTeamPitchMomentumTick(
          pitchRadians,
          pitchMomentumVel,
          frameDt,
          vp.h
        );
        pitchRadians = pitchMotion.pitch;
        pitchMomentumVel = pitchMotion.vel;
        applyCameraView();
      } else {
        yawMomentumVel = 0;
        pitchMomentumVel = 0;
      }
    }

    if (yawReturnStartMs != null) {
      const progress = privateTeamYawReturnProgress(now - yawReturnStartMs, reducedMotion);
      yawRadians = privateTeamYawAtReturn(yawReturnFrom, progress);
      pitchRadians = privateTeamYawAtReturn(pitchReturnFrom, progress);
      if (progress >= 1) {
        yawRadians = 0;
        pitchRadians = 0;
        yawReturnStartMs = null;
        yawReturnFrom = 0;
        pitchReturnFrom = 0;
      }
      applyCameraView();
    }

    syncWindowVistaParallax();

    bubble.lookAt(camera.position);
    lampHalo.lookAt(camera.position);

    renderer.render(scene, camera);
  }

  resize();
  win.addEventListener('resize', resize);
  if (typeof ResizeObserver !== 'undefined' && canvas.parentElement) {
    const ro = new ResizeObserver(resize);
    ro.observe(canvas.parentElement);
  }
  raf = win.requestAnimationFrame(tick);

  return {
    resize,
    setZoom(factor) {
      applyZoom(factor);
      return zoomFactor;
    },
    getZoom() {
      return zoomFactor;
    },
    projectPickRoleToClient,
    setBubbleText(text) {
      bubbleDrawer.draw(text);
    },
    setReducedMotion(next) {
      reducedMotion = !!next;
      typingShowB = false;
      if (reducedMotion && dancePlayback) stopSecretaryDance();
      else refreshSecretaryDisplay();
    },
    setSecretarySprite,
    playSecretaryWave,
    playSecretaryYeah,
    playSecretaryDance,
    noteTigerStatus,
    setSceneInputEnabled(next) {
      sceneInputEnabled = !!next;
    },
    setSceneAnimationPaused(next) {
      animationPaused = !!next;
    },
    destroy() {
      running = false;
      win.cancelAnimationFrame(raf);
      win.removeEventListener('resize', resize);
      canvas.removeEventListener('pointerdown', handlePointerDown);
      canvas.removeEventListener('pointermove', handlePointerMove);
      canvas.removeEventListener('pointermove', handleHoverMove);
      canvas.removeEventListener('pointerleave', handlePointerLeave);
      canvas.removeEventListener('pointerup', handlePointerUp);
      canvas.removeEventListener('pointercancel', handlePointerUp);
      canvas.removeEventListener('wheel', onWheel);
      renderer.dispose();
    }
  };
}