import * as THREE from 'three';
import {
  PLAYER_SPAWN,
  PLAYER_SPEED,
  DEFAULT_INTERACT_ZONES,
  canStand,
  moveVector,
  cameraRelative,
  stepPlayer,
  smoothingFactor,
  createExploreFlow,
  controlsActive,
  playerVisible,
  hitInteractZone,
  shouldShowInteractPrompt,
  hudBlocksInteract
} from './island-explore.js';
import {
  PANORAMA,
  poseForIsland,
  isClosedHall,
  islandAt,
  BOAT_STOPS
} from './island-layout.js';
import {
  createBoatState,
  startSailing,
  startSteering,
  stepBoat,
  stepSteering,
  stopSteering,
  abortSailing,
  abortSteering,
  isSailingPhase,
  isSteeringPhase,
  sampleRoute,
  routeProgress,
  berthForDock,
  dockedBob,
  boatWorldPose,
  disembarkNear,
  BOAT_DECK_OFFSET,
  BOAT_SEAT_LOCAL_X,
  BOAT_SEAT_LOCAL_Z,
  boatPassengerScaleFactor,
  boatWakeActive,
  routeForPhase
} from './island-boat.js';
import { trafficLightMeshColors, CITY_CENTER } from './island-city-data.js';
import { buildIslandCity } from './island-city-scene.js';
import {
  createVehicleState,
  enterVehicle,
  exitVehicle,
  forceExitVehicle,
  stepVehicle,
  isDrivingPhase,
  vehicleSpeedKmh,
  vehicleCameraLook,
  VEHICLE_FOLLOW_BACK,
  VEHICLE_DISMOUNT_SPEED
} from './island-vehicle.js';
import { createCasinoController, CASINO_NAME, promptDomUpdate } from './island-casino.js';
import { createGraffitiController } from './island-graffiti.js';
import { applyIslandOpenRoadmapParam } from './dev-machines-format.js';
import { createRoadmapController } from './island-roadmap.js';
import {
  INTERIOR_ORIGIN,
  ISLAND_CAMERA_FAR,
  createInteriorSession,
  stepInInterior,
  shouldHideIslandLabels,
  exploreHintText,
  interiorFollowPose,
  interiorLeaveButtonLabel,
  shouldShowInteriorLeave,
  labInteriorFollowDistance,
  labInteriorLookTarget,
  labInteriorRuntimeLookAt,
  casinoInteriorRuntimeLookAt,
  tcmInteriorRuntimeLookAt,
  shopInteriorRuntimeLookAt,
  interiorCameraFov
} from './island-interior.js';
import {
  avatarVisibility,
  createMiluState,
  createMiluView,
  feetOffsetY,
  islandAvatarAnim,
  isNearWave,
  MILU_ANIM,
  miluClipPath,
  miluStep,
  normalizeIslandAvatar,
  resolveFacing,
  screenAxes
} from './island-milu.js';
import { TCM_ATLAS_URL, consumeTcmReturn, writeTcmReturn } from './island-tcm-visit.js';
import { createLazyTcmFigureController, resolveTcmPickHit } from './island-tcm-figure-boot.js';
import { createLazyCareerCorridorController, resolveCareerPickHit } from './island-career-corridor-boot.js';
import {
  HIDDEN_DISCONNECT_MS,
  createIslandPresence,
  createMotion,
  loadAvatar,
  loadNick,
  pushTarget,
  sampleMotion,
  shouldSendPos
} from './island-presence.js';
import { createIslandChat } from './island-chat.js';
import { createIslandAudio, resolveTrackId } from './island-audio.js';
import { G, P, createSceneKit } from './island-kit.js';
import { createDayNight } from './island-daynight.js';
import { createUpdateLoop } from './island-loop.js';
import {
  EXPLORE_POSE,
  INTERIOR_POSE,
  applyDragPhi,
  createPoses,
  createCameraRig,
  exploreFollowDistance,
  interiorExploreFollowDistance
} from './island-camera.js';
import { buildIslandWorld } from './island-world.js';
import { exploreSkyColorHex } from './island-decor.js';
import { buildIslandBuildings } from './island-buildings.js';
import { buildIslandProps, registerSceneMotion } from './island-props.js';
import { buildIslandBeach } from './island-beach.js';
import { buildIslandPeople } from './island-people.js';
import {
  freezeNpcWalker,
  unfreezeNpcWalker,
  freezeNpcIdle,
  unfreezeNpcIdle,
  npcFacePlayer,
  getNpcMotionTime
} from './island-people.js';
import { shouldOfferNpcTalkPrompt, NPC_TALK_ACTION } from './island-people-data.js';
import { createIslandNpcTalk, npcTalkPromptLabel } from './island-npc-talk.js';
import { createIslandSky } from './island-sky.js';
import { createIslandSea } from './island-sea.js';
import { createIslandWeather, fogDistancesForCamera } from './island-weather.js';
import { createIslandLife, LIFE_PANORAMA_CAMERA } from './island-life.js';
import { createFootprintView } from './island-footprints.js';
import { createLifecycle } from './island-lifecycle.js';
import { createPeerLayer } from './island-peers.js';
import {
  createSelfLabelPlacer,
  loadSelfNameEnabled
} from './island-self-label.js';
import { placeBuildingLabels } from './island-labels.js';
import { createCornerUi } from './island-corner-ui.js';
import { createMinimapHud, syncPlayerAreaTitle } from './island-minimap.js';
import {
  createExplorePointer,
  createExploreKeys,
  createExploreJoystick
} from './island-explore-input.js';
import {
  createQualityAdvisor,
  createFpsRollingStats,
  formatExplorePerfOverlay,
  DEFAULT_MAX_PIXEL_RATIO,
  DEFAULT_SHADOW_MAP
} from './island-quality.js';
import { exploreLightAtMix } from './island-explore-light.js';
import {
  attachWebglContextHandlers,
  bindWebglNotice,
  readForceNoWebgl
} from './island-webgl-notice.js';

const ZONES = ['devices', 'system', 'photos'];

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const stage = document.getElementById('islandStage');
const canvas = document.getElementById('islandCanvas');
const webglNotice = bindWebglNotice({ root: document.getElementById('islandWebglNotice') });

function bootCasinoFallback() {
  const casino = createCasinoController({
    hooks: {
      beforeOpen: () => {
        const sheet = document.getElementById('islandSheet');
        if (sheet && !sheet.hidden && typeof window.switchDashTab === 'function') window.switchDashTab('');
      }
    }
  });
  const graffiti = createGraffitiController({});
  const roadmap = createRoadmapController({});
  window.PunchClockIsland = window.PunchClockIsland || {};
  window.PunchClockIsland.openCasino = () => casino.openLobby();
  window.PunchClockIsland.closeCasino = () => casino.closeLobby();
  window.PunchClockIsland.isCasinoOpen = () => casino.isOpen();
  window.PunchClockIsland.openGraffiti = () => graffiti.open();
  window.PunchClockIsland.closeGraffiti = () => graffiti.close();
  window.PunchClockIsland.openRoadmap = () => roadmap.open();
  window.PunchClockIsland.closeRoadmap = () => roadmap.close();
  applyIslandOpenRoadmapParam(window, () => roadmap.open());
}

if (stage && canvas) boot();

function boot() {
  let renderer = null;
  const forceNoWebgl = readForceNoWebgl(window.location.search);
  if (!forceNoWebgl) {
    try {
      renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
    } catch (e) {
      renderer = null;
    }
  }
  if (!renderer || !renderer.getContext()) {
    stage.classList.add('is-fallback');
    const list = document.getElementById('islandList');
    const listBtn = document.getElementById('islandListBtn');
    if (list) list.hidden = false;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'true');
    webglNotice.present({ rendererOk: false, contextLost: false });
    bootCasinoFallback();
    return;
  }

  attachWebglContextHandlers(canvas, webglNotice);

  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  const maxDevicePixelRatio = Math.min(window.devicePixelRatio || 1, DEFAULT_MAX_PIXEL_RATIO);
  renderer.setPixelRatio(maxDevicePixelRatio);

  const scene = new THREE.Scene();
  const skyDay = new THREE.Color(0xd8f7f8);
  const skyNight = new THREE.Color(0x1a3e86);
  scene.background = skyDay.clone();
  scene.fog = new THREE.Fog(skyDay.clone(), 150, 320);
  const fogBase = { near: 150, far: 320 };
  const fogWashColor = new THREE.Color(0xc5ced8);
  let groundWetValue = 0;

  const camera = new THREE.PerspectiveCamera(25, 1, 0.4, ISLAND_CAMERA_FAR);
  const clock = new THREE.Clock();

  const hemi = new THREE.HemisphereLight(0xfff8ef, 0xe7d8bc, 0.62);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfff3d6, 1.42);
  sun.position.set(-9.5, 20.5, 11);
  sun.castShadow = true;
  sun.shadow.mapSize.set(DEFAULT_SHADOW_MAP, DEFAULT_SHADOW_MAP);
  sun.shadow.camera.left = -36;
  sun.shadow.camera.right = 36;
  sun.shadow.camera.top = 36;
  sun.shadow.camera.bottom = -36;
  sun.shadow.camera.near = 2;
  sun.shadow.camera.far = 80;
  sun.shadow.bias = -0.00045;
  sun.shadow.normalBias = 0.038;
  sun.shadow.radius = 2;
  sun.target.position.set(0, 0.4, 0);
  scene.add(sun);
  scene.add(sun.target);

  const hemiDay = new THREE.Color(0xfff8ef);
  const hemiNight = new THREE.Color(0xcaa882);
  const groundDay = new THREE.Color(0xe7d8bc);
  const groundNight = new THREE.Color(0x45404e);
  const sunDay = new THREE.Color(0xfff3d6);
  const sunNight = new THREE.Color(0xc5d4f2);

  const POSES = createPoses(PANORAMA);
  const anchors = {};
  const rings = {};
  const pickables = [];
  const bobs = [];
  const idles = [];
  const walkers = [];
  const sways = [];
  const swimmers = [];
  const steams = [];
  const kit = createSceneKit(THREE, { scene: scene, bobs: bobs });
  const { addMesh, addBox, geo, paint, picture } = kit;
  const dayNight = createDayNight({
    reduced: reduced,
    hidden: () => document.hidden
  });
  dayNight.onMix((t) => {
    kit.forEachMaterial((material) => {
      material.color.copy(material.userData.day).lerp(material.userData.night, t);
      material.emissiveIntensity = material.userData.dayE + (material.userData.nightE - material.userData.dayE) * t;
      if (material.userData.dayEnvI != null && material.envMap) {
        material.envMapIntensity =
          material.userData.dayEnvI + (material.userData.nightEnvI - material.userData.dayEnvI) * t;
      }
    });
    const decorNight = new THREE.Color(exploreSkyColorHex(t));
    scene.background.copy(skyDay).lerp(decorNight, t);
    scene.fog.color.copy(scene.background);
    fogBase.near = 150 + (100 - 150) * t;
    fogBase.far = 320 + (240 - 320) * t;
    hemi.color.copy(hemiDay).lerp(hemiNight, t);
    hemi.groundColor.copy(groundDay).lerp(groundNight, t);
    const light = exploreLightAtMix(t);
    hemi.intensity = light.hemiIntensity;
    sun.color.copy(sunDay).lerp(sunNight, t);
    sun.intensity = light.sunIntensity;
    renderer.toneMappingExposure = light.toneMappingExposure;
  });

  const quality = createQualityAdvisor({
    getDevicePixelRatio: () => window.devicePixelRatio || 1
  });

  dayNight.onMix((t) => {
    kit.forEachLamp((item) => kit.applyLampMix(item, t));
  });

  function applyQualitySettings(settings) {
    if (!settings) return;
    renderer.setPixelRatio(settings.pixelRatio);
    renderer.shadowMap.enabled = !!settings.shadowEnabled;
    const nextSize = settings.shadowMapSize || DEFAULT_SHADOW_MAP;
    if (sun.shadow.mapSize.x !== nextSize) {
      sun.shadow.mapSize.set(nextSize, nextSize);
      if (sun.shadow.map) {
        sun.shadow.map.dispose();
        sun.shadow.map = null;
      }
    }
  }

  applyQualitySettings(quality.settings());

  const perfEnabled = new URLSearchParams(window.location.search).get('perf') === '1';
  let perfEl = null;
  let perfRolling = null;
  const perfTierChanges = { down: 0, up: 0 };
  if (perfEnabled) {
    perfEl = document.createElement('div');
    perfEl.className = 'island-perf';
    perfEl.setAttribute('aria-hidden', 'true');
    document.body.appendChild(perfEl);
    perfRolling = createFpsRollingStats(10000);
  }

  const worldResult = buildIslandWorld({ scene: scene, kit: kit });
  const sea = createIslandSea(THREE, { kit: kit, reduced: reduced });
  scene.add(sea.group);
  const lifeQs = new URLSearchParams(window.location.search);
  const lifeRareParam = lifeQs.get('lifeRare');
  const life = createIslandLife(THREE, {
    kit: kit,
    reduced: reduced,
    forceRare: lifeRareParam,
    forceAll: lifeQs.get('forceAll') === '1',
    lifeSpout: lifeQs.get('lifeSpout') === '1'
  });
  life.ready.then(() => {
    window.__islandLifeBox3 = {
      worldSize(kind) {
        const box = new THREE.Box3();
        let node = null;
        for (let i = 0; i < life.creatures.length; i++) {
          const c = life.creatures[i];
          if (c.userData && c.userData.kind === kind) node = c;
        }
        if (!node) return null;
        box.setFromObject(node);
        const s = new THREE.Vector3();
        box.getSize(s);
        return { x: +s.x.toFixed(2), y: +s.y.toFixed(2), z: +s.z.toFixed(2) };
      }
    };
    window.__islandLifeMeshTable = function () {
      const rows = [];
      const walkKind = new Map();
      for (let i = 0; i < life.creatures.length; i++) {
        const creature = life.creatures[i];
        const kind = creature.userData && creature.userData.kind;
        if (!kind) continue;
        walkKind.set(creature, kind);
        creature.traverse((child) => {
          if (child === creature) return;
          walkKind.set(child, kind);
        });
      }
      life.group.traverse((child) => {
        if (!child.isMesh) return;
        const kind = (child.userData && child.userData.kind) || walkKind.get(child);
        if (!kind) return;
        const box = new THREE.Box3().setFromObject(child);
        const sz = new THREE.Vector3();
        box.getSize(sz);
        const mat = child.material;
        const col = mat && mat.color;
        rows.push({
          kind: kind,
          mesh: child.name || '(mesh)',
          color: col && col.getHexString ? '#' + col.getHexString() : '#?',
          x: +sz.x.toFixed(2),
          y: +sz.y.toFixed(2),
          z: +sz.z.toFixed(2)
        });
      });
      return rows;
    };
    if (lifeQs.get('lifeNight') === '1') {
      dayNight.setTarget(1);
      dayNight.refresh();
    }
    if (lifeQs.get('lifePanorama') === '1') {
      const c = LIFE_PANORAMA_CAMERA;
      camera.position.set(c.eye.x, c.eye.y, c.eye.z);
      camera.lookAt(c.eye.x + c.dir.x, c.eye.y + c.dir.y, c.eye.z + c.dir.z);
      camera.fov = c.fovDeg;
      camera.updateProjectionMatrix();
    } else if (lifeQs.get('lifeClose') === '1' && lifeRareParam) {
      const box = new THREE.Box3();
      let node = null;
      for (let i = 0; i < life.creatures.length; i++) {
        const c = life.creatures[i];
        if (c.userData && c.userData.kind === lifeRareParam) node = c;
      }
      if (node) {
        box.setFromObject(node);
        const center = box.getCenter(new THREE.Vector3());
        const size = box.getSize(new THREE.Vector3());
        const span = Math.max(size.x, size.y, size.z, 0.8);
        if (lifeQs.get('lifeCloseView') === 'top') {
          camera.position.set(center.x, center.y + span * 2.4, center.z);
        } else {
          camera.position.set(center.x + span * 1.7, center.y + span * 1.05, center.z + span * 1.5);
        }
        camera.lookAt(center);
        camera.fov = 30;
        camera.updateProjectionMatrix();
      }
    }
    if (lifeQs.get('lifeSpout') === '1') {
      life.update(0.016, 0.55);
    }
    window.__islandLifeReady = true;
  });
  scene.add(life.group);
  if (worldResult && worldResult.water) {
    worldResult.water.position.y = -0.78;
  }

  const buildings = buildIslandBuildings({
    scene: scene,
    kit: kit,
    anchors: anchors,
    rings: rings,
    pickables: pickables,
    steams: steams,
    dayNight: dayNight,
    reduced: reduced
  });
  const propResult = buildIslandProps({
    scene: scene,
    kit: kit,
    bobs: bobs,
    sways: sways,
    swimmers: swimmers
  });
  const boatMesh = propResult && propResult.boat;
  const tcmFigureAnchor = propResult && propResult.tcmFigureAnchor;
  buildIslandBeach({ scene: scene, kit: kit });
  const footprints = createFootprintView(THREE, {
    scene: scene,
    kit: kit,
    trailOpts: { max: 40, stride: 0.34, life: 6, shoreLife: 2 }
  });
  const people = buildIslandPeople({
    scene: scene,
    kit: kit,
    idles: idles,
    walkers: walkers,
    reduced: reduced
  });
  const makePerson = people.makePerson;
  const trafficMeshes = { lights: null };
  const pinkCarMesh = {};
  buildIslandCity({
    scene: scene,
    kit: kit,
    idles: idles,
    walkers: walkers,
    trafficMeshes: trafficMeshes,
    pinkCarMesh: pinkCarMesh,
    people: people,
    reduced: reduced,
    dayNight: dayNight
  });
  let vehicleState = createVehicleState();
  const vehicleHud = document.getElementById('islandVehicleHud');
  const vehicleSpeedEl = document.getElementById('islandVehicleSpeed');
  const shaker = buildings.shaker;
  function refreshGraffitiWall() {
    buildings.refreshGraffitiWall();
  }
  function paintRoadmapSign(copy) {
    buildings.paintRoadmapSign(copy);
  }

  const loop = createUpdateLoop({
    requestAnimationFrame: (cb) => window.requestAnimationFrame(cb),
    cancelAnimationFrame: (id) => window.cancelAnimationFrame(id)
  });
  let swayWeatherMul = 1;
  registerSceneMotion(loop, {
    reduced: reduced,
    walkers: walkers,
    bobs: bobs,
    idles: idles,
    steams: steams,
    sways: sways,
    swimmers: swimmers,
    shaker: shaker,
    P: P,
    getSwayMul: () => swayWeatherMul
  });
  loop.register(function updateSea(dt, elapsed) {
    sea.update(dt, elapsed);
  });
  loop.register(function updateLife(dt, elapsed) {
    life.update(dt, elapsed);
  });
  dayNight.onMix((t) => {
    sea.setMix(t);
    life.setMix(t);
  });
  dayNight.refresh();

  const sky = createIslandSky(THREE, {
    reduced: reduced,
    sunPos: sun.position
  });
  scene.add(sky.group);
  dayNight.onMix((t) => {
    sky.update(t, 0, camera);
  });
  loop.register((dt, elapsed) => {
    if (trafficMeshes.lights) {
      const colors = trafficLightMeshColors(elapsed);
      trafficMeshes.lights.red.material.color.setHex(colors.red);
      trafficMeshes.lights.yellow.material.color.setHex(colors.yellow);
      trafficMeshes.lights.green.material.color.setHex(colors.green);
    }
    if (pinkCarMesh.group && vehicleState) {
      pinkCarMesh.group.position.set(vehicleState.carX - CITY_CENTER.x, P, vehicleState.carZ - CITY_CENTER.z);
      pinkCarMesh.group.rotation.y = vehicleState.carYaw;
      pinkCarMesh.group.visible = mode.exploring;
    }
    sky.tick(dt, elapsed);
  });
  loop.register((dt, elapsed) => {
    boatElapsed = elapsed;
    const prev = boatState;
    if (isSailing() && !voyageUiPaused() && !reduced) {
      boatState = stepBoat(boatState, dt, { paused: false, reduced: false });
    }
    syncBoatMesh(elapsed);
    const wakePack = boatMesh && boatMesh.userData.wake;
    if (wakePack && wakePack.mesh) {
      const steerSpd = isSteering() ? Math.hypot(boatState.vx || 0, boatState.vz || 0) : 0;
      const showWake = boatWakeActive(boatState.phase, steerSpd, {
        reduced: reduced,
        voyagePaused: voyageUiPaused()
      });
      const wakeMesh = wakePack.mesh;
      wakeMesh.visible = showWake;
      if (showWake && !reduced) {
        const dummy = wakePack.dummy;
        const specs = wakePack.specs;
        const bob = Math.sin(elapsed * 2.8) * 0.004;
        for (let wi = 0; wi < specs.length; wi++) {
          const s = specs[wi];
          dummy.position.set(
            s.baseX + Math.sin(elapsed * 2.6 + s.phase) * 0.006,
            s.baseY + bob,
            s.baseZ + Math.sin(elapsed * 2.4 + s.phase) * 0.01
          );
          dummy.rotation.set(s.rx || 0, 0, s.rz || 0);
          dummy.scale.set(s.sx, s.sy, s.sz);
          dummy.updateMatrix();
          wakeMesh.setMatrixAt(wi, dummy.matrix);
        }
        wakeMesh.instanceMatrix.needsUpdate = true;
      }
    }
    if (isSailingPhase(prev.phase) && !isSailingPhase(boatState.phase)) {
      finishVoyageDisembark();
    }
  });

  const lifecycle = createLifecycle();

  function syncFootprints(dt) {
    if (rooms.inside() || interiorBusy) {
      footprints.setHidden(true);
      return;
    }
    footprints.setHidden(false);
    const here = islandAt(player.x, player.z);
    const onSand = !!(here && here.theme === 'beach');
    const shoreDist = onSand
      ? here.r - Math.hypot(player.x - here.x, player.z - here.z)
      : 99;
    footprints.step({
      x: player.x,
      z: player.z,
      yaw: player.yaw,
      moving: !!player.moving,
      onSand: onSand,
      shoreDist: shoreDist,
      reduced: reduced
    }, dt);
  }

  // Phase 2：賭場門口互動區（座標見 CASINO／DEFAULT_INTERACT_ZONES）；須點按鈕或 Enter／E，不自動傳送。
  const INTERACT_ZONES = DEFAULT_INTERACT_ZONES.slice();

  function makePlayerAvatar() {
    const group = makePerson({
      shirt: 0x1f4d73,
      pants: 0x3e4a5a,
      hair: 0x3a312c,
      hat: 0xf06a2a,
      scale: 1.22
    });
    addBox(group, 0.22, 0.28, 0.12, 0xf06a2a, 0, 0.52, -0.16);
    addBox(group, 0.16, 0.08, 0.04, 0xfff7ef, 0, 0.72, 0.12);
    const beacon = addMesh(group, geo('cyl', [0.08, 0.02, 0.1, 5, 0]), 0xf06a2a, 0, 1.28, 0, {
      shadow: false,
      receive: false,
      glow: 0xffb15a,
      glowAmount: 0.55
    });
    beacon.userData.playerBeacon = true;
    return group;
  }

  function makeMiluPlaceholder() {
    const group = new THREE.Group();
    group.name = 'miluPlaceholder';
    const shadow = new THREE.Mesh(
      new THREE.CircleGeometry(MILU_ANIM.shadowRadius, 24),
      new THREE.MeshBasicMaterial({
        color: 0x24180f,
        transparent: true,
        opacity: 0.32,
        depthWrite: false
      })
    );
    shadow.rotation.x = -Math.PI / 2;
    shadow.position.y = 0.015;
    shadow.renderOrder = 1;
    group.add(shadow);
    function roundRect(ctx, x, y, w, h, r) {
      const rad = Math.min(r, w / 2, h / 2);
      ctx.beginPath();
      ctx.moveTo(x + rad, y);
      ctx.arcTo(x + w, y, x + w, y + h, rad);
      ctx.arcTo(x + w, y + h, x, y + h, rad);
      ctx.arcTo(x, y + h, x, y, rad);
      ctx.arcTo(x, y, x + w, y, rad);
      ctx.closePath();
    }
    const phHeight = 1.28;
    const boardTex = paint(220, 280, (ctx, w, h) => {
      ctx.clearRect(0, 0, w, h);
      const cx = w / 2;
      const beaconY = 26;
      ctx.fillStyle = '#f06a2a';
      ctx.beginPath();
      ctx.moveTo(cx, beaconY - 12);
      ctx.lineTo(cx + 9, beaconY);
      ctx.lineTo(cx, beaconY + 12);
      ctx.lineTo(cx - 9, beaconY);
      ctx.closePath();
      ctx.fill();
      const bodyW = 104;
      const bodyH = 150;
      const bodyTop = 48;
      const bx = cx - bodyW / 2;
      roundRect(ctx, bx, bodyTop, bodyW, bodyH, 34);
      ctx.fillStyle = 'rgba(255, 243, 232, 0.85)';
      ctx.fill();
      ctx.strokeStyle = '#f06a2a';
      ctx.lineWidth = 3;
      ctx.stroke();
      const pillW = 96;
      const pillH = 30;
      const pillY = bodyTop + bodyH + 8;
      roundRect(ctx, cx - pillW / 2, pillY, pillW, pillH, pillH / 2);
      ctx.fillStyle = '#ffffff';
      ctx.fill();
      ctx.fillStyle = '#3a312c';
      ctx.font = 'bold 17px "PingFang TC", "Noto Sans TC", sans-serif';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.fillText('載入中…', cx, pillY + pillH / 2);
    });
    const boardGeo = new THREE.PlaneGeometry(phHeight, phHeight);
    boardGeo.translate(0, feetOffsetY(phHeight, MILU_ANIM.footV), 0);
    const billboardMat = new THREE.MeshBasicMaterial({
      map: boardTex,
      transparent: true,
      depthWrite: false,
      side: THREE.DoubleSide
    });
    const billboard = new THREE.Mesh(boardGeo, billboardMat);
    billboard.renderOrder = 2;
    group.add(billboard);
    group.userData.billboardMaterial = billboardMat;
    group.userData.placeholderPulseT = 0;
    return group;
  }

  const playerMesh = makePlayerAvatar();
  scene.add(playerMesh);
  const miluPlaceholder = makeMiluPlaceholder();
  scene.add(miluPlaceholder);
  const player = {
    x: PLAYER_SPAWN.x,
    z: PLAYER_SPAWN.z,
    yaw: PLAYER_SPAWN.yaw,
    vx: 0,
    vz: 0,
    moving: false,
    keys: Object.create(null),
    stickX: 0,
    stickY: 0,
    stickActive: false
  };
  function setPlayerAnchor(x, y, z, yaw) {
    playerMesh.position.set(x, y, z);
    miluPlaceholder.position.set(x, y, z);
    if (yaw != null) playerMesh.rotation.y = yaw;
  }

  setPlayerAnchor(player.x, P, player.z, player.yaw);
  playerMesh.visible = false;
  miluPlaceholder.visible = false;

  let localAvatarId = loadAvatar(window.localStorage);
  let localView = createMiluView(THREE, {
    deferLoad: true,
    anim: islandAvatarAnim(localAvatarId)
  });
  scene.add(localView.object);
  localView.object.visible = false;
  let miluState = createMiluState();
  let islandPresence = null;
  let islandChat = null;
  let peerLayer = null;
  let corner = null;
  const selfLabel = createSelfLabelPlacer({ document: document });
  if (typeof window !== 'undefined') {
    window.__islandSelfLabel = () => {
      const dbg = selfLabel.getDebug();
      return Object.assign({}, dbg, {
        vehicleRoofY: pinkCarMesh && pinkCarMesh.roofY,
        driving: isDriving()
      });
    };
  }

  function playerGroundY() {
    if (isSailing() || isSteering()) {
      return dockedBob(boatElapsed, reduced) + BOAT_DECK_OFFSET;
    }
    return P;
  }

  function selfNameEnabledNow() {
    if (corner && typeof corner.isSelfNameEnabled === 'function') {
      return corner.isSelfNameEnabled();
    }
    return loadSelfNameEnabled(window.localStorage);
  }

  let lastSelfLabelScreen = null;

  function placeSelfLabelFrame(partial) {
    const pack = partial || {};
    const placed = selfLabel.place({
      mode: mode,
      storage: window.localStorage,
      loadNick: loadNick,
      selfName: islandPresence && typeof islandPresence.getSelfName === 'function'
        ? islandPresence.getSelfName()
        : '',
      selfNameEnabled: selfNameEnabledNow(),
      interiorBusy: interiorBusy,
      uiOverlay: islandUiOpen()
        || !!(document.getElementById('islandMoreSheet') && !document.getElementById('islandMoreSheet').hidden),
      miluVisible: localView.object.visible,
      placeholderVisible: miluPlaceholder.visible,
      blockVisible: playerMesh.visible,
      playerX: player.x,
      playerZ: player.z,
      groundY: playerGroundY(),
      P: P,
      offsetX: 0,
      offsetZ: 0,
      passengerScale: boatPassengerScaleFactor(boatState.phase),
      vehicleState: vehicleState,
      boatState: boatState,
      boatPhase: boatState.phase,
      boatElapsed: boatElapsed,
      reduced: reduced,
      width: pack.width,
      height: pack.height,
      camera: pack.camera,
      projected: pack.projected,
      peers: pack.peers,
      vehicleRoofY: pinkCarMesh.roofY
    });
    if (!placed) {
      lastSelfLabelScreen = null;
      return null;
    }
    lastSelfLabelScreen = placed.selfNameAbove && placed.labelX != null
      ? { x: placed.labelX, y: placed.labelY }
      : null;
    return {
      headY: placed.headY,
      selfNameAbove: placed.selfNameAbove,
      labelX: placed.labelX,
      labelY: placed.labelY
    };
  }
  let minimapHud = null;
  let peerHideTimer = 0;

  function clearMoveInput() {
    Object.keys(player.keys).forEach((key) => {
      player.keys[key] = false;
    });
    player.stickX = 0;
    player.stickY = 0;
    player.stickActive = false;
    player.moving = false;
    player.vx = 0;
    player.vz = 0;
    const knob = document.getElementById('islandJoystickKnob');
    if (knob) knob.style.transform = 'translate(0px, 0px)';
  }

  function sheetOpen() {
    const sheet = document.getElementById('islandSheet');
    return !!(sheet && !sheet.hidden);
  }

  function listOrMapOpen() {
    const list = document.getElementById('islandList');
    const map = document.getElementById('islandMapSheet');
    return !!(list && !list.hidden) || !!(map && !map.hidden);
  }

  function exploreHudPanelsOpen() {
    const more = document.getElementById('islandMoreSheet');
    return hudBlocksInteract({
      bigMapOpen: false,
      moreOpen: !!(more && !more.hidden),
      chatOpen: !!(islandChat && islandChat.isOpen())
    });
  }

  function npcMotionT() {
    const t = getNpcMotionTime();
    return Number.isFinite(t) && t > 0 ? t : clock.elapsedTime;
  }

  function endNpcTalkResume() {
    if (activeNpcRecord && activeNpcRecord.kind === 'walker') {
      unfreezeNpcWalker(activeNpcRecord.row, npcMotionT(), reduced);
    } else if (activeNpcRecord && activeNpcRecord.kind === 'idle') {
      unfreezeNpcIdle(activeNpcRecord.row);
    }
    activeNpcRecord = null;
  }

  function closeNpcTalkIfOpen() {
    if (npcTalk && npcTalk.isOpen()) npcTalk.close();
  }

  function beginNpcTalk(npc) {
    if (!npc || !npcTalk) return false;
    const record = npc.record;
    if (record && record.row && record.row.obj) {
      npc.x = record.row.obj.position.x;
      npc.z = record.row.obj.position.z;
    }
    const now = performance.now();
    if (record && record.kind === 'walker') {
      freezeNpcWalker(record.row, npcMotionT(), reduced);
      record.row.talkGreetAt = now;
      record.row.state = npcFacePlayer(
        record.row.state,
        record.row.anim,
        player.x,
        player.z,
        npc.x,
        npc.z,
        cam.theta,
        false
      );
    } else if (record && record.kind === 'idle') {
      freezeNpcIdle(record.row);
      record.row.talkGreetAt = now;
      record.row.state = npcFacePlayer(
        record.row.state,
        record.row.anim,
        player.x,
        player.z,
        npc.x,
        npc.z,
        cam.theta,
        !!record.row.seated
      );
    }
    activeNpcRecord = record || null;
    const anim = localView.anim();
    const axes = screenAxes(npc.x - player.x, npc.z - player.z, cam.theta);
    miluState = Object.assign({}, miluState, {
      mode: 'wave',
      frame: 0,
      acc: 0,
      waved: false,
      facing: resolveFacing(miluState.facing, axes.x, axes.z, anim, false)
    });
    npcTalk.open(Object.assign({}, npc));
    syncInteractPrompt();
    return true;
  }

  function onNpcTalkInteract() {
    if (npcTalk && npcTalk.isOpen()) {
      npcTalk.advance();
      syncInteractPrompt();
      return true;
    }
    if (pendingNpcTalk) return beginNpcTalk(pendingNpcTalk);
    return false;
  }

  function startNpcTalk() {
    return onNpcTalkInteract();
  }

  function islandUiOpen() {
    const list = document.getElementById('islandList');
    const mapSheet = document.getElementById('islandMapSheet');
    return sheetOpen() || !!(list && !list.hidden) || !!(mapSheet && !mapSheet.hidden)
      || casinoOpen() || graffitiOpen() || roadmapOpen()
      || (career && career.isCardOpen());
  }

  const tcmFigure = createLazyTcmFigureController({
    scene: scene,
    kit: kit,
    renderer: renderer,
    pickables: pickables,
    reduced: reduced,
    anchor: tcmFigureAnchor,
    getPlayer: () => player,
    getTcmSiteModel: () => (buildings.getTcmSiteModel ? buildings.getTcmSiteModel() : null),
    islandUiOpen: islandUiOpen
  });
  const career = createLazyCareerCorridorController({
    scene: scene,
    kit: kit,
    pickables: pickables,
    reduced: reduced,
    getPlayer: () => player,
    islandUiOpen: islandUiOpen,
    enterInterior: (id) => enterInterior(id)
  });
  loop.register((dt) => {
    tcmFigure.tick();
    career.tick(dt);
    if (buildings.tickComputerSite) buildings.tickComputerSite(dt);
  });

  let exploreSaved = null;
  let casino = null;
  let graffiti = null;
  let roadmap = null;
  const rooms = createInteriorSession();
  const baseSunIntensity = sun.intensity;
  let islandWeather = createIslandWeather({
    THREE: THREE,
    reduced: reduced,
    scene: scene,
    sun: sun,
    loop: loop,
    baseSunIntensity: baseSunIntensity,
    getNightMix: () => dayNight.mix,
    getIsIndoor: () => rooms.inside(),
    getQualityTier: () => quality.tier,
    getViewportWidth: () => (stage && stage.clientWidth) || window.innerWidth || 1280,
    getCamera: () => camera,
    getFocus: () => cam.targetLook,
    onWaveMul: (mul) => sea.setWaveMul(mul),
    onSwayMul: (mul) => {
      swayWeatherMul = mul;
    },
    onFogBase: (nearMul, farMul, wash, nightMix, spanMul) => {
      // 全景鏡頭離場景上百公尺，固定 near/far 會把玩家與主島一起吃掉，
      // 所以霧距離要跟著鏡頭到注視點的距離走。
      const look = cam.targetLook;
      const camDist = Math.hypot(
        camera.position.x - look.x,
        camera.position.y - look.y,
        camera.position.z - look.z
      );
      const dist = fogDistancesForCamera(fogBase.near, fogBase.far, nearMul, farMul, camDist, spanMul);
      scene.fog.near = dist.near;
      scene.fog.far = dist.far;
      if (wash > 0) {
        scene.fog.color.copy(scene.background).lerp(fogWashColor, wash * (1 - nightMix * 0.25));
      } else {
        scene.fog.color.copy(scene.background);
      }
    },
    setGroundWet: (wet) => {
      const amount = Math.max(0, Math.min(1, Number(wet) || 0));
      const backToDry = amount === 0 && groundWetValue !== 0;
      if (!backToDry && Math.abs(amount - groundWetValue) < 0.05) return;
      groundWetValue = amount;
      kit.forEachMaterial((mat) => {
        if (!mat.userData || !mat.userData.day) return;
        if (!mat.userData.weatherDryDay) {
          mat.userData.weatherDryDay = mat.userData.day.clone();
        }
        // day 是日夜混色的基準，淋濕只壓暗它，天氣轉晴要能原封不動還原。
        mat.userData.day.copy(mat.userData.weatherDryDay).multiplyScalar(1 - amount * 0.22);
      });
      dayNight.refresh();
    }
  });
  let roomView = null;
  let interiorBusy = false;
  let interiorGeneration = 0;
  const INTERIOR_MODULES = {
    shop: () => import('./island-shop-interior.js'),
    'computer-site': () => import('./island-lab-interior.js'),
    casino: () => import('./island-casino-interior.js'),
    'tcm-site': () => import('./island-tcm-interior.js')
  };
  let arcadeToastTimer = 0;

  function casinoOpen() {
    return !!(casino && casino.isOpen());
  }

  function graffitiOpen() {
    return !!(graffiti && graffiti.isOpen());
  }

  function roadmapOpen() {
    return !!(roadmap && roadmap.isOpen());
  }

  const flow = createExploreFlow({
    onModeChange: (prev, next) => {
      mode = next;
      syncAvatarVisible();
      if (prev.exploring !== next.exploring || prev.lobby !== next.lobby) syncExploreUi();
      else syncInteractPrompt();
    },
    clearMoveInput: () => clearMoveInput(),
    followPlayer: () => {
      setActive('');
      followPlayerCamera(false);
    },
    showPose: (id) => {
      applyPose(POSES[id], false);
      setActive(id);
    },
    showHome: () => {
      applyPose(POSES.home, false);
      setActive('');
    },
    sheetOpen: () => sheetOpen(),
    lobbyOpen: () => casinoOpen() || graffitiOpen(),
    closeSheet: () => {
      if (typeof window.switchDashTab === 'function') window.switchDashTab('');
    }
  });
  let mode = flow.mode;

  function dispatchMode(action) {
    flow.dispatch(action);
  }
  let playerBob = 0;
  let activeInteract = '';
  let pendingNpcTalk = null;
  let activeNpcRecord = null;
  let npcTalk = null;
  let boatState = createBoatState('main');
  let boatElapsed = 0;
  const steerBtn = document.getElementById('islandSteer');
  let steerPromptState = {
    visible: steerBtn ? !steerBtn.hidden : false,
    label: steerBtn ? String(steerBtn.textContent || '') : ''
  };

  function setSteerPromptVisible(visible, label) {
    if (!steerBtn) return;
    const next = {
      visible: !!visible,
      label: label != null ? String(label) : steerPromptState.label
    };
    const patch = promptDomUpdate(steerPromptState, next);
    if (Object.keys(patch).length === 0) return;
    if ('hidden' in patch) steerBtn.hidden = patch.hidden;
    if ('ariaHidden' in patch) steerBtn.setAttribute('aria-hidden', patch.ariaHidden);
    if ('textContent' in patch) steerBtn.textContent = patch.textContent;
    if ('ariaLabel' in patch) steerBtn.setAttribute('aria-label', patch.ariaLabel);
    steerPromptState = next;
  }

  const cam = createCameraRig(THREE, camera, {
    reduced: reduced,
    groundY: G,
    following: () => controlsActive(mode),
    smoothingFactor: smoothingFactor,
    nightFactor: () => dayNight.mix,
    followDistance: (radius, aspect) => {
      if (rooms.inside()) {
        if (rooms.room() && (rooms.room().id === 'lab-room' || rooms.room().id === 'casino-room' || rooms.room().id === 'tcm-room' || rooms.room().id === 'shop-room')) {
          return labInteriorFollowDistance(radius, aspect);
        }
        return interiorExploreFollowDistance(radius, aspect);
      }
      return exploreFollowDistance(radius, aspect);
    },
    phiRest: () => {
      if (rooms.inside()) {
        const custom = interiorFollowPose(rooms.room(), camera.aspect);
        return (custom || INTERIOR_POSE).phi;
      }
      return EXPLORE_POSE.phi;
    },
    allowSky: () => !rooms.inside(),
    initial: POSES.home
  });
  if (typeof people.registerBillboardMotion === 'function') {
    people.registerBillboardMotion(loop, {
      reduced: reduced,
      walkers: walkers,
      idles: idles,
      P: P,
      camera: camera,
      camTheta: () => cam.theta
    });
  }
  let animating = false;
  const projected = new THREE.Vector3();
  let activeZone = '';

  function applyPose(pose, instant) {
    cam.applyPose(pose, instant);
  }

  function setActive(id) {
    activeZone = id || '';
    ZONES.forEach((zone) => {
      if (rings[zone]) rings[zone].visible = zone === activeZone;
    });
  }

  function showArcadeToast(message) {
    const hint = document.getElementById('islandExploreHint');
    if (!hint || !message) return;
    const desk = hint.querySelector('.island-explore-hint-desk');
    const touch = hint.querySelector('.island-explore-hint-touch');
    if (desk && desk.textContent !== message) desk.textContent = message;
    if (touch && touch.textContent !== message) touch.textContent = message;
    if (arcadeToastTimer) window.clearTimeout(arcadeToastTimer);
    arcadeToastTimer = window.setTimeout(() => {
      arcadeToastTimer = 0;
      syncExploreHint();
    }, 2200);
  }

  function casinoArcadePoke(kind, clawIndex) {
    if (!roomView || typeof roomView.poke !== 'function') return;
    const msg = roomView.poke(kind, clawIndex);
    if (msg) showArcadeToast(msg);
  }

  function syncExploreHint() {
    const hint = document.getElementById('islandExploreHint');
    if (!hint || closedHallTimer || arcadeToastTimer) return;
    const desk = hint.querySelector('.island-explore-hint-desk');
    const touch = hint.querySelector('.island-explore-hint-touch');
    const inside = rooms.inside();
    let deskText;
    let touchText;
    if (isSteering()) {
      deskText = '方向鍵或 WASD 開船，靠近碼頭可下船';
      touchText = '搖桿開船，靠近碼頭可下船';
    } else {
      const roomId = inside && rooms.inside() ? rooms.id() : null;
      deskText = exploreHintText(inside, false, roomId);
      touchText = exploreHintText(inside, true, roomId);
    }
    if (desk && desk.textContent !== deskText) desk.textContent = deskText;
    if (touch && touch.textContent !== touchText) touch.textContent = touchText;
  }

  function syncInteriorLeave() {
    const btn = document.getElementById('islandLeaveInterior');
    if (!btn) return;
    const overlay = casinoOpen() || graffitiOpen() || roadmapOpen() || isSailing() || isSteering();
    const show = shouldShowInteriorLeave({
      inside: rooms.inside(),
      exploring: mode.exploring,
      busy: interiorBusy,
      overlay: overlay
    });
    btn.hidden = !show;
    if (show) {
      const label = interiorLeaveButtonLabel(rooms);
      btn.textContent = label;
      btn.setAttribute('aria-label', label);
    }
  }

  function syncExploreUi() {
    const btn = document.getElementById('islandExplore');
    const stick = document.getElementById('islandJoystick');
    const hint = document.getElementById('islandExploreHint');
    const exploring = mode.exploring;
    document.body.classList.toggle('is-exploring', exploring);
    if (btn) {
      btn.setAttribute('aria-pressed', exploring ? 'true' : 'false');
      btn.setAttribute('aria-label', exploring ? '退出探索模式' : '進入探索模式');
      btn.hidden = false;
      const exploreLabel = btn.querySelector('.island-hud-primary-label');
      if (exploreLabel) exploreLabel.textContent = exploring ? '退出探索' : '探索';
      else btn.textContent = exploring ? '退出探索' : '探索';
    }
    const voyageBlock = casinoOpen() || graffitiOpen() || isSailing();
    if (stick) stick.hidden = !exploring || voyageBlock;
    if (hint) hint.hidden = !exploring || (voyageBlock && !isSteering());
    if (islandChat) islandChat.setExploring(exploring && !voyageBlock);
    syncExploreHint();
    syncInteriorLeave();
    syncInteractPrompt();
    if (corner) {
      corner.syncPeerCount();
      corner.syncMoreAudioLabel();
      corner.syncExploreHudLabels();
      corner.closeMore();
    }
    if (!exploring) {
      syncPlayerAreaTitle(document, { interiorRoom: null, player: { x: 0, z: 0 } }, null);
    }
  }

  function isSailing() {
    return isSailingPhase(boatState.phase);
  }

  function isSteering() {
    return isSteeringPhase(boatState.phase);
  }

  function canExploreMove() {
    return flow.canMove() && !isSailing();
  }

  function voyageUiPaused() {
    return mode.paused || islandUiOpen();
  }

  function syncBoatMesh(elapsed) {
    if (!boatMesh) return;
    const bobY = dockedBob(elapsed, reduced);
    if (isSailing()) {
      const u = routeProgress(boatState.phase, boatState.progress);
      const pose = sampleRoute(u);
      boatMesh.position.set(pose.x, bobY, pose.z);
      boatMesh.rotation.y = pose.yaw;
      return;
    }
    if (isSteering()) {
      const pose = boatWorldPose(boatState);
      boatMesh.position.set(pose.x, bobY, pose.z);
      boatMesh.rotation.y = pose.yaw;
      return;
    }
    const berth = berthForDock(boatState.dock);
    boatMesh.position.set(berth.x, bobY, berth.z);
    boatMesh.rotation.y = berth.yaw;
  }

  function landDisembarkYaw(dock) {
    if (dock === 'beach') return Math.PI;
    if (dock === 'city') return Math.PI * 0.55;
    return 0.45;
  }

  function finishVoyageDisembark() {
    const land = BOAT_STOPS[boatState.dock].land;
    player.x = land.x;
    player.z = land.z;
    player.yaw = landDisembarkYaw(boatState.dock);
    player.moving = false;
    player.vx = 0;
    player.vz = 0;
    setPlayerAnchor(player.x, P, player.z, player.yaw);
    syncPassengerScale();
    syncBoatMesh(boatElapsed);
    followPlayerCamera(true);
    syncInteractPrompt();
    syncExploreUi();
  }

  function finishSteerDisembark(dock) {
    const d = dock === 'beach' ? 'beach' : dock === 'city' ? 'city' : 'main';
    const land = BOAT_STOPS[d].land;
    player.x = land.x;
    player.z = land.z;
    player.yaw = landDisembarkYaw(d);
    player.moving = false;
    player.vx = 0;
    player.vz = 0;
    clearMoveInput();
    setPlayerAnchor(player.x, P, player.z, player.yaw);
    syncPassengerScale();
    syncBoatMesh(boatElapsed);
    followPlayerCamera(true);
    syncInteractPrompt();
    syncExploreUi();
  }

  function disembarkSteer() {
    if (!isSteering()) return;
    const dock = disembarkNear(boatState.x, boatState.z);
    if (!dock) return;
    boatState = stopSteering(boatState, dock);
    finishSteerDisembark(dock);
  }

  function abortSteerVoyage() {
    if (!isSteering()) return;
    const dock = boatState.steerDock === 'beach' ? 'beach'
      : boatState.steerDock === 'city' ? 'city' : 'main';
    boatState = abortSteering(boatState);
    finishSteerDisembark(dock);
  }

  function isDriving() {
    return isDrivingPhase(vehicleState.phase);
  }

  function syncVehicleHud() {
    const show = mode.exploring && isDriving() && !rooms.inside();
    if (vehicleHud) {
      vehicleHud.hidden = !show;
      vehicleHud.setAttribute('aria-hidden', show ? 'false' : 'true');
    }
    if (!show) return;
    if (vehicleSpeedEl) {
      vehicleSpeedEl.textContent = vehicleSpeedKmh(vehicleState.vx, vehicleState.vz) + ' 公里／時';
    }
  }

  async function startSteer(zone) {
    if (!zone || zone.action !== 'sail' || !mode.exploring || rooms.inside() || interiorBusy) return;
    if (isSailing() || isSteering() || isDriving()) return;
    const stop = zone.stop === 'beach' ? 'beach' : zone.stop === 'city' ? 'city' : 'main';
    const expected = stop === 'main' ? 'docked-main' : stop === 'city' ? 'docked-city' : 'docked-beach';
    if (boatState.phase !== expected) return;
    clearMoveInput();
    boatState = startSteering(boatState, stop);
    syncExploreUi();
    syncInteractPrompt();
    syncBoatMesh(boatElapsed);
    followPlayerCamera(true);
  }

  async function startSail(zone) {
    if (!zone || zone.action !== 'sail' || !mode.exploring || rooms.inside() || interiorBusy || isSailing() || isSteering() || isDriving()) return;
    const stop = zone.stop === 'beach' ? 'beach' : zone.stop === 'city' ? 'city' : 'main';
    const expected = stop === 'main' ? 'docked-main' : stop === 'city' ? 'docked-city' : 'docked-beach';
    if (boatState.phase !== expected) return;
    clearMoveInput();
    const dest = zone.dest || (stop === 'main' ? 'beach' : 'main');
    boatState = startSailing(boatState, stop, dest);
    syncExploreUi();
    syncInteractPrompt();
    if (reduced) {
      await fadeTo(true);
      boatState = stepBoat(boatState, 0, { paused: false, reduced: true });
      finishVoyageDisembark();
      await fadeTo(false);
    }
  }

  function abortBoatVoyage() {
    if (!isSailing()) return;
    boatState = abortSailing(boatState);
    finishVoyageDisembark();
  }

  function syncInteractPrompt() {
    if (!casino) return false;
    if (closedHallTimer) return false;
    if (isSteering()) {
      const dock = disembarkNear(boatState.x, boatState.z);
      const show = !!(dock && mode.exploring && !mode.paused && !sheetOpen() && !casinoOpen() && !graffitiOpen() && !roadmapOpen());
      casino.setPromptVisible(show, show ? '靠岸下船' : '');
      setSteerPromptVisible(false);
      return show;
    }
    if (isDriving()) {
      const spd = Math.hypot(vehicleState.vx, vehicleState.vz);
      const canExit = spd <= VEHICLE_DISMOUNT_SPEED;
      activeInteract = canExit ? 'driving-exit' : '';
      const showDrive = shouldShowInteractPrompt({
        exploring: mode.exploring,
        paused: mode.paused,
        uiOpen: isSailing() || sheetOpen() || listOrMapOpen() || exploreHudPanelsOpen(),
        lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
        zoneId: activeInteract
      });
      casino.setPromptVisible(showDrive && canExit, canExit ? '下車' : '');
      setSteerPromptVisible(false);
      syncVehicleHud();
      return showDrive && canExit;
    }
    const zone = hitInteractZone(player.x, player.z, promptZones());
    activeInteract = zone ? (zone.id || '') : '';
    const atBoatDock = !!(zone && zone.action === 'sail');
    const dockedOk = atBoatDock && (
      (zone.stop === 'main' && boatState.phase === 'docked-main')
      || (zone.stop === 'beach' && boatState.phase === 'docked-beach')
      || (zone.stop === 'city' && boatState.phase === 'docked-city')
    );
    const hudPanelsOpen = exploreHudPanelsOpen();
    const show = shouldShowInteractPrompt({
      exploring: mode.exploring,
      paused: mode.paused,
      uiOpen: isSailing() || sheetOpen() || listOrMapOpen() || hudPanelsOpen,
      lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
      zoneId: activeInteract
    });
    if (dockedOk) {
      casino.setPromptVisible(show, zone.label);
      setSteerPromptVisible(show, '自己開船');
      return show;
    }
    setSteerPromptVisible(false);
    if (zone && zone.action === 'openRoadmap') {
      casino.setPromptVisible(false);
      return false;
    }
    if (npcTalk && npcTalk.isOpen()) {
      const stepLabel = npcTalk.interactPromptLabel();
      activeInteract = 'npc-talk';
      if (stepLabel) {
        const showTalk = shouldShowInteractPrompt({
          exploring: mode.exploring,
          paused: mode.paused,
          uiOpen: isSailing() || sheetOpen() || listOrMapOpen() || hudPanelsOpen,
          lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
          zoneId: 'npc-talk'
        });
        casino.setPromptVisible(showTalk, stepLabel, stepLabel);
        return showTalk;
      }
      casino.setPromptVisible(false);
      return false;
    }
    if (!zone && !rooms.inside()) {
      const npcs = typeof people.collectTalkableNpcs === 'function' ? people.collectTalkableNpcs() : [];
      pendingNpcTalk = shouldOfferNpcTalkPrompt({
        exploring: mode.exploring,
        paused: mode.paused,
        indoor: false,
        driving: false,
        sailing: isSailing(),
        steering: false,
        uiOpen: isSailing() || sheetOpen() || listOrMapOpen() || hudPanelsOpen,
        lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
        chatOpen: !!(islandChat && islandChat.isOpen()),
        talkOpen: false,
        zone: null,
        px: player.x,
        pz: player.z,
        npcs: npcs,
        radius: MILU_ANIM.waveNpcRadius
      });
      if (pendingNpcTalk) {
        activeInteract = 'npc-talk';
        const showTalk = shouldShowInteractPrompt({
          exploring: mode.exploring,
          paused: mode.paused,
          uiOpen: isSailing() || sheetOpen() || listOrMapOpen() || hudPanelsOpen,
          lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
          zoneId: 'npc-talk'
        });
        casino.setPromptVisible(showTalk, '聊天', npcTalkPromptLabel(pendingNpcTalk.name));
        return showTalk;
      }
    } else {
      pendingNpcTalk = null;
    }
    casino.setPromptVisible(show, zone && zone.label ? zone.label : '進入賭場');
    return show;
  }

  function followVehicleCamera(instant) {
    const camLook = vehicleCameraLook(vehicleState);
    if (!camLook) {
      followPlayerCamera(instant);
      return;
    }
    const pose = Object.assign({}, EXPLORE_POSE, { radius: VEHICLE_FOLLOW_BACK });
    cam.follow(camLook.lookX, G + 1.05, camLook.lookZ, pose, instant);
    cam.targetLook.set(camLook.targetX, G + 0.95, camLook.targetZ);
  }

  function enterVehicleInteract() {
    if (isDriving() || isSailing() || isSteering()) return;
    closeNpcTalkIfOpen();
    const next = enterVehicle(vehicleState, player.x, player.z);
    if (!isDrivingPhase(next.phase)) return;
    vehicleState = next;
    player.x = vehicleState.carX;
    player.z = vehicleState.carZ;
    player.yaw = vehicleState.carYaw;
    player.vx = 0;
    player.vz = 0;
    clearMoveInput();
    followVehicleCamera(true);
    syncInteractPrompt();
    syncVehicleHud();
  }

  function disembarkVehicle() {
    if (!isDriving()) return;
    const pack = exitVehicle(vehicleState);
    vehicleState = pack.state;
    if (!pack.player) return;
    player.x = pack.player.x;
    player.z = pack.player.z;
    player.yaw = pack.player.yaw;
    player.vx = 0;
    player.vz = 0;
    clearMoveInput();
    followPlayerCamera(true);
    syncInteractPrompt();
    syncVehicleHud();
  }

  function followPlayerCamera(instant) {
    let pose = EXPLORE_POSE;
    let lookX = player.x;
    let lookY = G + 1.05;
    let lookZ = player.z;
    if (rooms.inside()) {
      const room = rooms.room();
      const custom = interiorFollowPose(room, camera.aspect);
      pose = custom || INTERIOR_POSE;
      if (room && room.id === 'lab-room') {
        const focal = labInteriorRuntimeLookAt(room, player, camera.aspect);
        lookX = focal.x;
        lookY = focal.y;
        lookZ = focal.z;
      } else if (room && room.id === 'casino-room') {
        const focal = casinoInteriorRuntimeLookAt(room, player, camera.aspect);
        lookX = focal.x;
        lookY = focal.y;
        lookZ = focal.z;
      } else if (room && room.id === 'tcm-room') {
        const focal = tcmInteriorRuntimeLookAt(room, player, camera.aspect);
        lookX = focal.x;
        lookY = focal.y;
        lookZ = focal.z;
      } else if (room && room.id === 'shop-room') {
        const focal = shopInteriorRuntimeLookAt(room, player, camera.aspect);
        lookX = focal.x;
        lookY = focal.y;
        lookZ = focal.z;
      }
    }
    const wantFov = interiorCameraFov(rooms.inside() ? rooms.room() : null);
    if (camera.fov !== wantFov) {
      camera.fov = wantFov;
      camera.updateProjectionMatrix();
    }
    cam.follow(lookX, lookY, lookZ, pose, instant);
  }

  function setExplore(next, instant) {
    const want = !!next;
    if (want === mode.exploring) {
      syncExploreUi();
      return;
    }
    if (want) {
      if (casino && casino.isOpen()) casino.closeLobby();
      exploreSaved = {
        look: [cam.targetLook.x, cam.targetLook.y, cam.targetLook.z],
        radius: cam.targetRadius,
        theta: cam.targetTheta,
        phi: cam.targetPhi,
        active: activeZone
      };
      if (!canStand(player.x, player.z)) {
        player.x = PLAYER_SPAWN.x;
        player.z = PLAYER_SPAWN.z;
        player.yaw = PLAYER_SPAWN.yaw;
      }
      setPlayerAnchor(player.x, P, player.z, player.yaw);
      dispatchMode('enter');
      if (peerLayer) peerLayer.setPeerScene(currentScene());
      lifecycle.emit('enterExplore', {});
      if (sheetOpen()) {
        dispatchMode('panelOpen');
      } else {
        setActive('');
        followPlayerCamera(instant);
      }
    } else {
      closeNpcTalkIfOpen();
      if (isDriving()) {
        const pack = exitVehicle(vehicleState, { maxSpeed: 999 });
        vehicleState = pack.state || forceExitVehicle(vehicleState);
        if (pack.player) {
          player.x = pack.player.x;
          player.z = pack.player.z;
          player.yaw = pack.player.yaw;
        }
        syncVehicleHud();
      }
      if (isSteering()) abortSteerVoyage();
      if (isSailing()) abortBoatVoyage();
      if (rooms.inside()) abortInterior();
      if (casino && casino.isOpen()) casino.closeLobby();
      dispatchMode('exit');
      lifecycle.emit('exitExplore', {});
      const saved = exploreSaved || POSES.home;
      exploreSaved = null;
      applyPose({
        look: saved.look || POSES.home.look,
        radius: saved.radius != null ? saved.radius : POSES.home.radius,
        theta: saved.theta != null ? saved.theta : POSES.home.theta,
        phi: saved.phi != null ? saved.phi : POSES.home.phi
      }, !!instant);
      setActive(saved.active || '');
    }
    syncExploreUi();
  }

  function focus(id) {
    const pose = POSES[id];
    if (!pose) return;
    // 探索中開啟管理面板：暫停移動、鏡頭轉到該區，關閉後回到角色身上（大廳開著則維持鎖定）。
    flow.focusPanel(id);
  }

  function reset() {
    flow.resetPanel();
  }

  let syncIslandBgm = null;

  function setMode(next) {
    const night = next === 'night';
    dayNight.setTarget(night ? 1 : 0);
    if (reduced || document.hidden) renderer.render(scene, camera);
    const dayBtn = document.getElementById('islandDay');
    if (dayBtn) {
      dayBtn.setAttribute('aria-pressed', night ? 'true' : 'false');
      dayBtn.setAttribute('aria-label', night ? '夜景，點擊切換日景' : '日景，點擊切換夜景');
    }
    document.body.classList.toggle('is-night', night);
    if (syncIslandBgm) syncIslandBgm();
  }

  function resize() {
    const width = stage.clientWidth || window.innerWidth;
    const height = stage.clientHeight || window.innerHeight;
    camera.aspect = width / Math.max(height, 1);
    camera.fov = interiorCameraFov(rooms.inside() ? rooms.room() : null);
    camera.updateProjectionMatrix();
    renderer.setSize(width, height, false);
  }

  function currentScene() {
    if (casino && casino.isOpen()) return 'casino';
    if (rooms.inside()) return rooms.buildingId() || 'shop';
    return 'outdoor';
  }

  function presencePose() {
    const sceneName = currentScene();
    const indoor = sceneName !== 'outdoor' && rooms.inside() && sceneName === rooms.buildingId();
    return {
      x: player.x - (indoor ? INTERIOR_ORIGIN.x : 0),
      z: player.z - (indoor ? INTERIOR_ORIGIN.z : 0),
      yaw: player.yaw,
      moving: !!player.moving,
      scene: sceneName,
      avatar: localAvatarId
    };
  }

  function replaceLocalAvatar(nextId) {
    const id = normalizeIslandAvatar(nextId);
    if (id === localAvatarId) return;
    scene.remove(localView.object);
    if (typeof localView.dispose === 'function') localView.dispose();
    miluFailToastShown = false;
    localAvatarId = id;
    miluState = createMiluState();
    localView = createMiluView(THREE, { deferLoad: true, anim: islandAvatarAnim(id) });
    scene.add(localView.object);
    localView.object.visible = false;
    if (mode.exploring) {
      localView.beginLoad();
      if (peerLayer) peerLayer.forceResendPos();
    }
    syncAvatarVisible();
  }

  function peerOffset() {
    if (rooms.inside() && (!casino || !casino.isOpen())) return INTERIOR_ORIGIN;
    return { x: 0, z: 0 };
  }

  let lastLabelAt = 0;

  function placeLabels(force) {
    if (!peerLayer) return;
    const now = performance.now();
    if (!force && now - lastLabelAt < 100) return;
    lastLabelAt = now;
    placeBuildingLabels({
      inside: rooms.inside(),
      interiorBusy: interiorBusy,
      shouldHideIslandLabels: shouldHideIslandLabels,
      far: cam.targetRadius > 42,
      camera: camera,
      anchors: anchors,
      projected: projected,
      stage: stage,
      document: document,
      now: now,
      placePeerLabels: () => peerLayer.placePeerLabels()
    });
  }

  function miluNpcPoints() {
    const points =
      typeof people.miluStaticPoints === 'function' ? people.miluStaticPoints() : [];
    for (let i = 0; i < walkers.length; i++) {
      points.push({ x: walkers[i].obj.position.x, z: walkers[i].obj.position.z });
    }
    return points;
  }

  let miluFailToastShown = false;

  function showMiluFailToast() {
    if (miluFailToastShown) return;
    miluFailToastShown = true;
    const el = document.createElement('div');
    el.setAttribute('role', 'status');
    el.setAttribute('aria-live', 'polite');
    el.textContent = '角色圖片載入失敗，已改用替代造型';
    Object.assign(el.style, {
      position: 'fixed',
      left: '50%',
      bottom: 'calc(1.25rem + env(safe-area-inset-bottom, 0px))',
      transform: 'translateX(-50%)',
      maxWidth: 'min(92vw, 22rem)',
      padding: '0.55rem 0.85rem',
      borderRadius: '0.65rem',
      background: 'rgba(22, 18, 14, 0.88)',
      color: '#fff7ef',
      fontSize: '0.92rem',
      lineHeight: '1.35',
      textAlign: 'center',
      zIndex: '12040',
      pointerEvents: 'none',
      boxShadow: '0 10px 28px rgba(0, 0, 0, 0.35)'
    });
    document.body.appendChild(el);
    window.setTimeout(() => {
      if (el.parentNode) el.parentNode.removeChild(el);
    }, 4200);
  }

  function syncPassengerScale() {
    const s = boatPassengerScaleFactor(boatState.phase);
    localView.object.scale.setScalar(s);
    miluPlaceholder.scale.setScalar(s);
  }

  function syncAvatarVisible() {
    const vis = avatarVisibility({
      show: playerVisible(mode) && !isDriving(),
      ready: localView.ready(),
      failed: localView.failed()
    });
    localView.object.visible = vis.milu;
    miluPlaceholder.visible = vis.placeholder;
    playerMesh.visible = vis.block;
    if (vis.block) showMiluFailToast();
  }

  function stepMilu(dt, groundY) {
    const y = groundY == null ? P : groundY;
    const axes = screenAxes(player.vx, player.vz, cam.theta);
    const anim = localView.anim();
    miluState = miluStep(miluState, dt, {
      moving: player.moving,
      screenX: axes.x,
      screenZ: axes.z,
      near: isNearWave(player.x, player.z, miluNpcPoints(), INTERACT_ZONES, anim),
      reduced: reduced,
      anim: anim
    });
    localView.show(miluClipPath(miluState, anim));
    localView.object.position.set(player.x, y, player.z);
    if (miluPlaceholder.visible) miluPlaceholder.position.set(player.x, y, player.z);
    syncAvatarVisible();
    syncPassengerScale();
  }

  function updatePlayer(dt) {
    if (isDriving()) {
      const paused = voyageUiPaused();
      const input = moveVector(player.keys, { active: player.stickActive, x: player.stickX, y: player.stickY });
      if (!paused) {
        const dir = input.moving ? cameraRelative(input, cam.theta) : { x: 0, z: 0 };
        vehicleState = stepVehicle(
          vehicleState,
          { x: dir.x, z: dir.z, moving: input.moving },
          dt,
          { paused: false, reduced: reduced }
        );
      } else {
        vehicleState = stepVehicle(vehicleState, { x: 0, z: 0, moving: false }, dt, { paused: true, reduced: reduced });
      }
      player.x = vehicleState.carX;
      player.z = vehicleState.carZ;
      player.yaw = vehicleState.carYaw;
      player.moving = !paused && input.moving;
      player.vx = paused ? 0 : vehicleState.vx;
      player.vz = paused ? 0 : vehicleState.vz;
      playerBob = 0;
      setPlayerAnchor(player.x, P, player.z, player.yaw);
      stepMilu(dt, P);
      syncAvatarVisible();
      followVehicleCamera(false);
      footprints.setHidden(true);
      syncInteractPrompt();
      syncVehicleHud();
      return;
    }
    syncVehicleHud();
    if (isSailing()) {
      player.moving = false;
      player.vx = 0;
      player.vz = 0;
      playerBob = 0;
      const u = routeProgress(boatState.phase, boatState.progress);
      const pose = sampleRoute(u, routeForPhase(boatState.phase));
      const deckY = dockedBob(boatElapsed, reduced) + BOAT_DECK_OFFSET;
      const fwdX = Math.sin(pose.yaw);
      const fwdZ = Math.cos(pose.yaw);
      const rightX = Math.cos(pose.yaw);
      const rightZ = -Math.sin(pose.yaw);
      const seatX = pose.x + fwdX * BOAT_SEAT_LOCAL_Z + rightX * BOAT_SEAT_LOCAL_X;
      const seatZ = pose.z + fwdZ * BOAT_SEAT_LOCAL_Z + rightZ * BOAT_SEAT_LOCAL_X;
      player.x = seatX;
      player.z = seatZ;
      player.yaw = pose.yaw;
      setPlayerAnchor(seatX, deckY, seatZ, pose.yaw);
      stepMilu(dt, deckY);
      cam.targetLook.set(pose.x, G + 1.12, pose.z);
      footprints.setHidden(true);
      return;
    }
    if (isSteering()) {
      const paused = voyageUiPaused();
      const input = moveVector(player.keys, { active: player.stickActive, x: player.stickX, y: player.stickY });
      if (!paused) {
        const dir = input.moving ? cameraRelative(input, cam.theta) : { x: 0, z: 0 };
        boatState = stepSteering(
          boatState,
          { x: dir.x, z: dir.z, moving: input.moving },
          dt,
          { paused: false, reduced: reduced }
        );
      } else {
        boatState = stepSteering(boatState, { x: 0, z: 0, moving: false }, dt, { paused: true, reduced: reduced });
      }
      const pose = boatWorldPose(boatState);
      const deckY = dockedBob(boatElapsed, reduced) + BOAT_DECK_OFFSET;
      const fwdX = Math.sin(pose.yaw);
      const fwdZ = Math.cos(pose.yaw);
      const rightX = Math.cos(pose.yaw);
      const rightZ = -Math.sin(pose.yaw);
      const seatX = pose.x + fwdX * BOAT_SEAT_LOCAL_Z + rightX * BOAT_SEAT_LOCAL_X;
      const seatZ = pose.z + fwdZ * BOAT_SEAT_LOCAL_Z + rightZ * BOAT_SEAT_LOCAL_X;
      player.x = seatX;
      player.z = seatZ;
      player.yaw = pose.yaw;
      player.moving = !paused && input.moving;
      player.vx = paused ? 0 : (boatState.vx || 0);
      player.vz = paused ? 0 : (boatState.vz || 0);
      playerBob = 0;
      setPlayerAnchor(seatX, deckY, seatZ, pose.yaw);
      stepMilu(dt, deckY);
      cam.targetLook.set(pose.x, G + 1.12, pose.z);
      footprints.setHidden(true);
      syncInteractPrompt();
      return;
    }
    if (!canExploreMove()) {
      if (rooms.inside()) rooms.setPaused(true);
      player.moving = false;
      player.vx = 0;
      player.vz = 0;
      playerBob = 0;
      setPlayerAnchor(player.x, P, player.z, player.yaw);
      stepMilu(dt);
      syncFootprints(dt);
      return;
    }
    if (rooms.inside()) rooms.setPaused(false);
    if (interiorBusy) {
      player.moving = false;
      player.vx = 0;
      player.vz = 0;
      playerBob = 0;
      setPlayerAnchor(player.x, P, player.z, player.yaw);
      stepMilu(dt);
      syncFootprints(dt);
      return;
    }
    const input = moveVector(player.keys, { active: player.stickActive, x: player.stickX, y: player.stickY });
    const next = rooms.inside()
      ? stepInInterior({
        x: player.x - INTERIOR_ORIGIN.x,
        z: player.z - INTERIOR_ORIGIN.z,
        yaw: player.yaw
      }, input, cam.theta, dt, rooms.room())
      : stepPlayer(player, input, cam.theta, dt);
    if (rooms.inside()) {
      next.x += INTERIOR_ORIGIN.x;
      next.z += INTERIOR_ORIGIN.z;
    }
    player.moving = next.moving;
    if (next.moving) {
      const dir = cameraRelative(input, cam.theta);
      player.x = next.x;
      player.z = next.z;
      player.yaw = next.yaw;
      player.vx = dir.x * PLAYER_SPEED;
      player.vz = dir.z * PLAYER_SPEED;
    } else {
      player.vx = 0;
      player.vz = 0;
    }
    if (!reduced && player.moving) playerBob += dt * 10;
    else playerBob = 0;
    const bobY = (!reduced && player.moving) ? Math.sin(playerBob) * 0.035 : 0;
    setPlayerAnchor(player.x, P + bobY, player.z, player.yaw);
    stepMilu(dt, P + bobY);
    const followRoom = rooms.inside() ? rooms.room() : null;
    if (followRoom && followRoom.id === 'lab-room') {
      const focal = labInteriorRuntimeLookAt(followRoom, player, camera.aspect);
      cam.targetLook.set(focal.x, focal.y, focal.z);
    } else if (followRoom && followRoom.id === 'casino-room') {
      const focal = casinoInteriorRuntimeLookAt(followRoom, player, camera.aspect);
      cam.targetLook.set(focal.x, focal.y, focal.z);
    } else if (followRoom && followRoom.id === 'tcm-room') {
      const focal = tcmInteriorRuntimeLookAt(followRoom, player, camera.aspect);
      cam.targetLook.set(focal.x, focal.y, focal.z);
    } else if (followRoom && followRoom.id === 'shop-room') {
      const focal = shopInteriorRuntimeLookAt(followRoom, player, camera.aspect);
      cam.targetLook.set(focal.x, focal.y, focal.z);
    } else {
      cam.targetLook.set(player.x, G + 1.05, player.z);
    }
    syncInteractPrompt();
    syncFootprints(dt);
    if (syncIslandBgm) syncIslandBgm();
  }

  function frame() {
    const dt = Math.min(0.1, clock.getDelta() || 0);
    const sample = quality.observeFrame(dt * 1000);
    if (sample.changed) {
      applyQualitySettings(sample.settings);
      life.setLowQuality(sample.tier === 'shadowOff' || sample.tier === 'shadowLow');
    }
    if (perfEl) {
      if (sample.tierDirection === 'down') perfTierChanges.down += 1;
      if (sample.tierDirection === 'up') perfTierChanges.up += 1;
      if (perfRolling) perfRolling.push(performance.now(), sample.fps);
      const weatherLine = islandWeather ? islandWeather.formatPerfLine() : '';
      perfEl.textContent = formatExplorePerfOverlay(
        sample,
        perfRolling ? perfRolling.snapshot() : null,
        perfTierChanges,
        weatherLine
      );
    }
    updatePlayer(dt);
    if (npcTalk && npcTalk.isOpen()) {
      npcTalk.tick(dt, player);
    }
    if (roomView && typeof roomView.tick === 'function') roomView.tick(dt);
    if (peerLayer) peerLayer.syncPresence(performance.now());
    cam.step(dt);
    localView.faceCamera(camera, player.x, player.z);
    if (miluPlaceholder.visible) {
      const dx = camera.position.x - player.x;
      const dz = camera.position.z - player.z;
      miluPlaceholder.rotation.y = Math.atan2(dx, dz);
      miluPlaceholder.rotation.x = 0;
      miluPlaceholder.rotation.z = 0;
      const billboardMat = miluPlaceholder.userData.billboardMaterial;
      if (billboardMat) {
        if (reduced) {
          billboardMat.opacity = 1;
        } else {
          miluPlaceholder.userData.placeholderPulseT += dt;
          const pulse = 0.5 + 0.5 * Math.sin(miluPlaceholder.userData.placeholderPulseT * 2.2);
          billboardMat.opacity = 0.7 + 0.3 * pulse;
        }
      }
    }
    if (peerLayer) peerLayer.stepPeers(dt);
    if (minimapHud) minimapHud.tick(performance.now());
    dayNight.step(dt);
    loop.tick(dt, clock.elapsedTime);
    renderer.render(scene, camera);
    placeSelfOverlaysEveryFrame();
    placeLabels();
  }

  function placeSelfOverlaysEveryFrame() {
    if (peerLayer && typeof peerLayer.placeSelfOverlays === 'function') {
      peerLayer.placeSelfOverlays();
      return;
    }
    const w = stage.clientWidth || 1;
    const h = stage.clientHeight || 1;
    const placed = placeSelfLabelFrame({
      width: w,
      height: h,
      camera: camera,
      projected: projected,
      peers: []
    });
    if (!islandChat) return;
    const bubbleCtx = {
      x: player.x,
      y: placed && placed.headY != null ? placed.headY : P + 1.18,
      z: player.z,
      visible: !!(mode.exploring && (
        localView.object.visible || miluPlaceholder.visible || playerMesh.visible
      )),
      width: w,
      height: h,
      camera: camera,
      projected: projected,
      peers: [],
      selfNameAbove: !!(placed && placed.selfNameAbove),
      labelX: placed && placed.labelX != null ? placed.labelX : null,
      labelY: placed && placed.labelY != null ? placed.labelY : null
    };
    islandChat.placeBubbles(bubbleCtx);
  }

  function start() {
    if (animating) return;
    animating = true;
    clock.getDelta();
    loop.start(frame);
  }

  function stop() {
    animating = false;
    loop.stop();
    clearMoveInput();
  }

  window.addEventListener('resize', resize);
  document.addEventListener('visibilitychange', () => {
    if (document.hidden) {
      stop();
      if (peerHideTimer) window.clearTimeout(peerHideTimer);
      peerHideTimer = window.setTimeout(() => {
        peerHideTimer = 0;
        if (document.hidden && islandPresence) islandPresence.disconnect();
      }, HIDDEN_DISCONNECT_MS);
    } else {
      if (peerHideTimer) {
        window.clearTimeout(peerHideTimer);
        peerHideTimer = 0;
      }
      start();
      if (mode.exploring && islandPresence) islandPresence.connect();
    }
    lifecycle.emit('visibilityChange', { hidden: document.hidden });
  });
  window.addEventListener('blur', () => {
    clearMoveInput();
  });

  const list = document.getElementById('islandList');
  function jumpToIsland(islandId) {
    const pose = poseForIsland(islandId);
    if (!pose) return;
    if (mode.exploring) setExplore(false, true);
    if (sheetOpen() && typeof window.switchDashTab === 'function') window.switchDashTab('');
    if (casino && casino.isOpen()) casino.closeLobby();
    applyPose(pose, true);
    setActive('');
    const listEl = document.getElementById('islandList');
    const listToggle = document.getElementById('islandListBtn');
    if (listEl) listEl.hidden = true;
    if (listToggle) listToggle.setAttribute('aria-expanded', 'false');
    if (corner) corner.closeMap();
    syncInteractPrompt();
  }

  const listBtn = document.getElementById('islandListBtn');
  const listClose = document.getElementById('islandListClose');
  let listPositionListener = null;

  function positionListPanel() {
    if (!list || list.hidden) return;
    const anchor = document.getElementById('islandMoreBtn');
    if (!anchor || !list.style || typeof list.style.setProperty !== 'function') return;
    const mm = typeof globalThis.matchMedia === 'function'
      ? globalThis.matchMedia('(min-width: 761px)')
      : null;
    if (!mm || !mm.matches) {
      list.style.removeProperty('--island-list-top');
      list.style.removeProperty('--island-list-right');
      return;
    }
    const rect = anchor.getBoundingClientRect();
    const vw = globalThis.innerWidth || 1280;
    list.style.setProperty('--island-list-top', `${rect.bottom + 8}px`);
    list.style.setProperty('--island-list-right', `${Math.max(0, vw - rect.right)}px`);
  }

  function bindListPosition(on) {
    const g = globalThis;
    if (!g || typeof g.addEventListener !== 'function') return;
    if (on && !listPositionListener) {
      listPositionListener = () => positionListPanel();
      g.addEventListener('resize', listPositionListener);
      g.addEventListener('scroll', listPositionListener, true);
    } else if (!on && listPositionListener) {
      g.removeEventListener('resize', listPositionListener);
      g.removeEventListener('scroll', listPositionListener, true);
      listPositionListener = null;
    }
  }

  function closeListPanel() {
    if (!list) return;
    list.hidden = true;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
    bindListPosition(false);
    syncInteractPrompt();
  }
  if (listClose) listClose.addEventListener('click', closeListPanel);
  if (listBtn && list) {
    listBtn.addEventListener('click', () => {
      const opening = list.hidden;
      list.hidden = !opening;
      listBtn.setAttribute('aria-expanded', opening ? 'true' : 'false');
      if (opening) {
        if (corner) corner.closeMore();
        positionListPanel();
        bindListPosition(true);
      } else {
        bindListPosition(false);
      }
      syncInteractPrompt();
    });
    list.querySelectorAll('[data-island]').forEach((btn) => {
      btn.addEventListener('click', () => jumpToIsland(btn.getAttribute('data-island')));
    });
  }
  const exploreBtn = document.getElementById('islandExplore');
  if (exploreBtn) {
    exploreBtn.addEventListener('click', () => {
      const next = !mode.exploring;
      if (next) {
        if (sheetOpen() && typeof window.switchDashTab === 'function') window.switchDashTab('');
        if (list) list.hidden = true;
        if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
      }
      setExplore(next, false);
    });
  }
  const leaveInteriorBtn = document.getElementById('islandLeaveInterior');
  if (leaveInteriorBtn) {
    leaveInteriorBtn.addEventListener('click', (event) => {
      event.preventDefault();
      leaveInterior();
    });
  }
  const home = document.getElementById('islandHome');
  if (home) {
    home.addEventListener('click', () => {
      if (mode.exploring) setExplore(false, false);
      if (typeof window.switchDashTab === 'function') window.switchDashTab('');
      else reset();
      if (list) list.hidden = true;
      if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
    });
  }
  const closeBtn = document.getElementById('islandSheetClose');
  if (closeBtn) {
    closeBtn.addEventListener('click', () => {
      if (typeof window.switchDashTab === 'function') window.switchDashTab('');
    });
  }
  const dayBtn = document.getElementById('islandDay');
  if (dayBtn) {
    dayBtn.addEventListener('click', () => {
      const night = document.body.classList.contains('is-night');
      setMode(night ? 'day' : 'night');
    });
  }

  const ISLAND_BGM_TRACKS = Object.freeze({
    main: '/audio/bgm-main.ogg',
    main_night: '/audio/bgm-main-night.ogg',
    casino: '/audio/bgm-casino.ogg',
    casino_night: '/audio/bgm-casino-night.ogg',
    tcm: '/audio/bgm-tcm.ogg',
    tcm_night: '/audio/bgm-tcm-night.ogg',
    computer: '/audio/bgm-computer.ogg',
    computer_night: '/audio/bgm-computer-night.ogg',
    city: '/audio/bgm-city.ogg',
    city_night: '/audio/bgm-city-night.ogg'
  });

  const islandAudio = createIslandAudio({
    tracks: ISLAND_BGM_TRACKS,
    storage: typeof localStorage !== 'undefined' ? localStorage : null,
    volume: 0.35,
    ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 }
  });

  let lastSyncedBgmTrackId = null;
  syncIslandBgm = function syncIslandBgmImpl() {
    if (!islandAudio.available) return;
    const night = dayNight.target >= 1;
    let islandId = null;
    let buildingId = null;
    if (rooms.inside()) {
      buildingId = rooms.buildingId();
    } else {
      const here = islandAt(player.x, player.z);
      islandId = here ? here.id : null;
    }
    const trackId = resolveTrackId({
      islandId,
      buildingId,
      night,
      tracks: ISLAND_BGM_TRACKS
    });
    if (trackId === lastSyncedBgmTrackId) return;
    lastSyncedBgmTrackId = trackId;
    islandAudio.setTrack(trackId);
  };
  const audioBtn = document.getElementById('islandAudio');

  function syncAudioButton() {
    if (!audioBtn) return;
    const on = !islandAudio.muted;
    audioBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
    audioBtn.setAttribute('aria-label', on ? '靜音背景音樂與海浪聲' : '開啟背景音樂與海浪聲');
    const iconEl = audioBtn.querySelector('.island-tool-audio-icon');
    if (iconEl) {
      iconEl.innerHTML = on
        ? '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M4 10v4h4l5 4V6L8 10H4zm12.5 2a3.5 3.5 0 0 0-2-3.15v6.3a3.5 3.5 0 0 0 2-3.15z"/></svg>'
        : '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="currentColor" d="M4 10v4h3l5 4V6L8 10H4zm14.3 2.3 2.1 2.1-1.4 1.4-2.1-2.1-2.1 2.1-1.4-1.4 2.1-2.1-2.1-2.1 1.4-1.4 2.1 2.1 2.1-2.1 1.4 1.4-2.1 2.1z"/></svg>';
    }
    if (corner) corner.syncMoreAudioLabel();
  }

  function showAudioButton() {
    if (audioBtn && audioBtn.hidden) {
      audioBtn.hidden = false;
      syncAudioButton();
    }
  }

  let audioListenersRemoved = false;
  function tryInitAudio() {
    if (!islandAudio.available) return;
    islandAudio.onUserInteraction();
    showAudioButton();
    const musicDone = islandAudio.playSucceeded;
    const ambienceDone = islandAudio.ambiencePlaySucceeded || !islandAudio._getAmbienceAudio();
    if ((musicDone && ambienceDone) || islandAudio.muted) {
      removeAudioListeners();
    }
  }

  function removeAudioListeners() {
    if (audioListenersRemoved) return;
    audioListenersRemoved = true;
    document.removeEventListener('pointerdown', tryInitAudio);
    document.removeEventListener('touchend', tryInitAudio);
    document.removeEventListener('keydown', tryInitAudio);
  }

  islandAudio.preload();
  fetch('/audio/bgm-main.ogg', { method: 'HEAD' })
    .then((res) => {
      if (res.ok) showAudioButton();
    })
    .catch(() => {});

  if (audioBtn) {
    audioBtn.addEventListener('click', () => {
      islandAudio.toggleMute();
      syncAudioButton();
      if (islandAudio.muted) removeAudioListeners();
    });
  }
  document.addEventListener('pointerdown', tryInitAudio);
  document.addEventListener('touchend', tryInitAudio);
  document.addEventListener('keydown', tryInitAudio);

  lifecycle.on('enterRoom', () => {
    sky.setVisible(false);
    life.setVisible(false);
    dayNight.refresh();
    islandAudio.setInsideRoom(true);
  });
  lifecycle.on('exitRoom', () => {
    sky.setVisible(true);
    life.setVisible(true);
    islandAudio.setInsideRoom(false);
  });
  lifecycle.on('visibilityChange', (p) => {
    islandAudio.onVisibilityChange(p.hidden);
    if (p.hidden) closeNpcTalkIfOpen();
  });

  function promptZones() {
    if (!rooms.inside()) return INTERACT_ZONES.concat(career.getInteractZones());
    const local = rooms.interactZones();
    return local.map((zone) => ({
      id: zone.id,
      x: zone.x + INTERIOR_ORIGIN.x,
      z: zone.z + INTERIOR_ORIGIN.z,
      r: zone.r,
      label: zone.label,
      action: zone.action,
      claw: zone.claw
    }));
  }

  function setFade(on) {
    const el = document.getElementById('islandFade');
    if (el) el.classList.toggle('is-on', !!on);
  }

  function fadeTo(on) {
    setFade(on);
    if (reduced) return Promise.resolve();
    return new Promise((resolve) => window.setTimeout(resolve, 280));
  }

  function closeOverlayPanels() {
    closeNpcTalkIfOpen();
    if (casino && casino.isOpen()) casino.closeLobby();
    if (graffiti && graffiti.isOpen()) graffiti.close();
    if (roadmap && roadmap.isOpen()) roadmap.close();
    if (sheetOpen() && typeof window.switchDashTab === 'function') window.switchDashTab('');
    const list = document.getElementById('islandList');
    const listBtn = document.getElementById('islandListBtn');
    if (list) list.hidden = true;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
  }

  function applyPlayerPose(pose, interior) {
    player.x = pose.x + (interior ? INTERIOR_ORIGIN.x : 0);
    player.z = pose.z + (interior ? INTERIOR_ORIGIN.z : 0);
    player.yaw = pose.yaw;
    player.moving = false;
    player.vx = 0;
    player.vz = 0;
    setPlayerAnchor(player.x, P, player.z, player.yaw);
    localView.object.position.set(player.x, P, player.z);
    stepMilu(0, P);
    followPlayerCamera(true);
  }

  function mountRoom(view) {
    disposeRoom();
    roomView = view;
    view.group.position.set(INTERIOR_ORIGIN.x, 0, INTERIOR_ORIGIN.z);
    scene.add(view.group);
    view.materials.forEach((material) => kit.addMaterial(material));
    if (view.lamp) {
      view.lamp.realLight = true;
      kit.applyLampMix(view.lamp, dayNight.mix);
      kit.addLamp(view.lamp);
    }
    lifecycle.emit('enterRoom', {});
  }

  function disposeRoom() {
    if (!roomView) return;
    scene.remove(roomView.group);
    const mats = roomView.materials || [];
    for (let i = 0; i < mats.length; i++) kit.removeMaterial(mats[i]);
    if (roomView.lamp) kit.removeLamp(roomView.lamp);
    roomView.dispose();
    roomView = null;
    lifecycle.emit('exitRoom', {});
  }

  function abortInterior() {
    interiorGeneration += 1;
    const left = rooms.inside() ? rooms.exit(performance.now()) : null;
    disposeRoom();
    setFade(false);
    interiorBusy = false;
    if (left && left.ok) applyPlayerPose(left.pose, false);
  }

  async function enterInterior(buildingId) {
    if (interiorBusy || rooms.inside() || !mode.exploring || isSailing() || isSteering()) return;
    if (!rooms.canEnter(buildingId, performance.now())) return;
    const ticket = ++interiorGeneration;
    interiorBusy = true;
    footprints.setHidden(true);
    clearMoveInput();
    closeOverlayPanels();
    placeLabels();
    await fadeTo(true);
    if (ticket !== interiorGeneration) return;
    try {
      const loader = INTERIOR_MODULES[buildingId];
      if (!loader) throw new Error('no interior');
      const mod = await loader();
      if (ticket !== interiorGeneration) return;
      mountRoom(mod.createInterior(THREE, { groundY: G, reducedMotion: reduced }));
      const entered = rooms.enter(buildingId, performance.now());
      if (!entered.ok) disposeRoom();
      else applyPlayerPose(entered.pose, true);
    } catch (err) {
      disposeRoom();
      if (rooms.inside()) rooms.exit(performance.now());
    }
    if (ticket !== interiorGeneration) return;
    await fadeTo(false);
    if (ticket !== interiorGeneration) return;
    interiorBusy = false;
    syncExploreHint();
    syncInteriorLeave();
    syncInteractPrompt();
  }

  async function enterTcm() {
    return enterInterior('tcm-site');
  }

  async function enterTcmHallFromReturn(pose) {
    if (interiorBusy || rooms.inside()) return;
    const buildingId = 'tcm-site';
    const ticket = ++interiorGeneration;
    interiorBusy = true;
    footprints.setHidden(true);
    clearMoveInput();
    closeOverlayPanels();
    placeLabels();
    try {
      const loader = INTERIOR_MODULES[buildingId];
      if (!loader) throw new Error('no interior');
      const mod = await loader();
      if (ticket !== interiorGeneration) return;
      mountRoom(mod.createInterior(THREE, { groundY: G, reducedMotion: reduced }));
      const entered = rooms.enter(buildingId, performance.now());
      if (!entered.ok) disposeRoom();
      else applyPlayerPose(pose, true);
    } catch (err) {
      disposeRoom();
      if (rooms.inside()) rooms.exit(performance.now());
    }
    if (ticket !== interiorGeneration) return;
    interiorBusy = false;
    syncExploreHint();
    syncInteriorLeave();
    syncInteractPrompt();
    followPlayerCamera(true);
  }

  async function enterTcmStudy() {
    if (interiorBusy || !mode.exploring || isSailing() || isSteering()) return;
    if (!rooms.inside() || !rooms.room() || rooms.room().id !== 'tcm-room') return;
    writeTcmReturn(window.sessionStorage, null, { target: 'hall' });
    const ticket = ++interiorGeneration;
    interiorBusy = true;
    clearMoveInput();
    closeOverlayPanels();
    placeLabels();
    await fadeTo(true);
    if (ticket !== interiorGeneration) return;
    window.location.assign(TCM_ATLAS_URL);
  }

  async function leaveInterior() {
    if (interiorBusy || !rooms.inside()) return;
    const ticket = ++interiorGeneration;
    interiorBusy = true;
    clearMoveInput();
    closeOverlayPanels();
    placeLabels();
    await fadeTo(true);
    if (ticket !== interiorGeneration) return;
    const left = rooms.exit(performance.now());
    disposeRoom();
    if (left.ok) applyPlayerPose(left.pose, false);
    await fadeTo(false);
    if (ticket !== interiorGeneration) return;
    interiorBusy = false;
    syncExploreHint();
    syncInteriorLeave();
    syncInteractPrompt();
  }

  function openGraffiti() {
    if (isSailing() || isSteering()) return;
    closeNpcTalkIfOpen();
    if (casino && casino.isOpen()) casino.closeLobby();
    if (roadmap && roadmap.isOpen()) roadmap.close();
    if (graffiti) graffiti.open();
  }

  let closedHallTimer = 0;
  function showInteriorClosed() {
    const hint = document.getElementById('islandExploreHint');
    const closedLabel = '室內尚未開放';
    if (casino) {
      casino.setPromptVisible(true, closedLabel);
    } else {
      const button = document.getElementById('islandInteract');
      if (button) {
        button.textContent = closedLabel;
        button.setAttribute('aria-label', closedLabel);
      }
    }
    if (!hint) return;
    hint.querySelectorAll('span').forEach((el) => {
      if (el.textContent !== closedLabel) el.textContent = closedLabel;
    });
    if (closedHallTimer) window.clearTimeout(closedHallTimer);
    closedHallTimer = window.setTimeout(() => {
      closedHallTimer = 0;
      syncExploreHint();
      syncInteractPrompt();
    }, 1800);
  }

  function openRoadmap() {
    if (isSailing() || isSteering()) return;
    closeNpcTalkIfOpen();
    if (casino && casino.isOpen()) casino.closeLobby();
    if (graffiti && graffiti.isOpen()) graffiti.close();
    if (roadmap) roadmap.open();
  }

  casino = createCasinoController({
    hooks: {
      beforeOpen: () => {
        if (graffiti && graffiti.isOpen()) graffiti.close();
        if (roadmap && roadmap.isOpen()) roadmap.close();
        flow.beforeLobbyOpen();
      },
      onLobbyChange: (state) => flow.lobbyChanged(state.open),
      onListCasino: () => {
        if (isSailing() || isSteering() || interiorBusy) return true;
        if (rooms.inside() && rooms.buildingId() === 'casino') return true;
        if (!mode.exploring) setExplore(true, false);
        if (rooms.inside()) return true;
        enterInterior('casino');
        return true;
      },
      onPrompt: () => {
        if (activeInteract === 'npc-talk') {
          onNpcTalkInteract();
          return true;
        }
        if (isSteering()) {
          disembarkSteer();
          return true;
        }
        if (isDriving() || activeInteract === 'driving-exit') {
          disembarkVehicle();
          return true;
        }
        const zone = hitInteractZone(player.x, player.z, promptZones());
        if (!zone) return false;
        if (zone.action === 'exitInterior') {
          leaveInterior();
          return true;
        }
        if (zone.action === 'enterInterior') {
          enterInterior(zone.id);
          return true;
        }
        if (zone.action === 'openGraffiti') {
          openGraffiti();
          return true;
        }
        if (zone.action === 'openRoadmap') {
          openRoadmap();
          return true;
        }
        if (zone.action === 'sail') {
          startSail(zone);
          return true;
        }
        if (zone.action === 'enterVehicle') {
          enterVehicleInteract();
          return true;
        }
        if (zone.action === 'enterTcm') {
          enterTcm();
          return true;
        }
        if (zone.action === 'enterTcmStudy') {
          enterTcmStudy();
          return true;
        }
        if (zone.action === 'openCareerCard') {
          career.showCard(zone.entryId);
          return true;
        }
        if (zone.action === 'openCasinoBlackjack') {
          casino.openBlackjackFromInterior();
          return true;
        }
        if (zone.action === 'casinoSlots') {
          casinoArcadePoke('slots');
          return true;
        }
        if (zone.action === 'casinoRoulette') {
          casinoArcadePoke('roulette');
          return true;
        }
        if (isClosedHall(zone.action)) {
          showInteriorClosed();
          return true;
        }
        return false;
      }
    }
  });
  if (steerBtn) {
    steerBtn.addEventListener('click', (event) => {
      event.preventDefault();
      const zone = hitInteractZone(player.x, player.z, promptZones());
      if (zone && zone.action === 'sail') startSteer(zone);
    });
  }
  graffiti = createGraffitiController({
    hooks: {
      onChange: (state) => {
        if (state.open) {
          if (casino && casino.isOpen()) casino.closeLobby();
          if (roadmap && roadmap.isOpen()) roadmap.close();
          flow.beforeLobbyOpen();
        } else if (!casinoOpen() && !roadmapOpen()) {
          flow.lobbyChanged(false);
        }
      },
      onWallChange: () => refreshGraffitiWall()
    }
  });
  roadmap = createRoadmapController({
    hooks: {
      onChange: (state) => {
        if (state.open) {
          if (casino && casino.isOpen()) casino.closeLobby();
          if (graffiti && graffiti.isOpen()) graffiti.close();
          flow.beforeLobbyOpen();
        } else if (!casinoOpen() && !graffitiOpen()) {
          flow.lobbyChanged(false);
        }
      },
      onData: (data, copy) => paintRoadmapSign(copy)
    }
  });
  roadmap.refresh();

  peerLayer = createPeerLayer({
    THREE: THREE,
    scene: scene,
    camera: camera,
    projected: projected,
    P: P,
    reduced: reduced,
    createMiluView: createMiluView,
    createMiluState: createMiluState,
    miluStep: miluStep,
    miluClipPath: miluClipPath,
    screenAxes: screenAxes,
    createMotion: createMotion,
    pushTarget: pushTarget,
    sampleMotion: sampleMotion,
    shouldSendPos: shouldSendPos,
    getMode: () => mode,
    getPlayer: () => player,
    getMiluVisible: () => localView.object.visible,
    getMiluPlaceholderVisible: () => miluPlaceholder.visible,
    getPlayerMeshVisible: () => playerMesh.visible,
    currentScene: currentScene,
    presencePose: presencePose,
    peerOffset: peerOffset,
    getStageSize: () => ({
      width: stage.clientWidth || 1,
      height: stage.clientHeight || 1
    }),
    getChat: () => islandChat,
    getPresence: () => islandPresence,
    document: document,
    getCam: () => cam,
    syncPeerCount: () => {
      if (corner) corner.syncPeerCount();
    },
    placeSelfLabel: (partial) => placeSelfLabelFrame(partial),
    getSelfLabelScreen: () => lastSelfLabelScreen
  });

  lifecycle.on('enterExplore', () => {
    if (typeof localView.beginLoad === 'function') localView.beginLoad();
    if (islandPresence) islandPresence.connect();
  });
  lifecycle.on('exitExplore', () => {
    if (peerLayer) peerLayer.resetExploreState();
    if (islandChat) islandChat.clear();
    if (islandPresence) islandPresence.disconnect();
  });

  islandPresence = createIslandPresence({
    location: window.location,
    WebSocket: window.WebSocket,
    storage: window.localStorage,
    nick: () => loadNick(window.localStorage),
    scene: () => currentScene(),
    now: () => Date.now(),
    onWelcome: (msg) => {
      selfLabel.invalidateText();
      peerLayer.setWelcomed(true);
      peerLayer.setSelfId(msg.id || '');
      const input = document.getElementById('islandNickInput');
      if (input && !loadNick(window.localStorage) && document.activeElement !== input) {
        input.value = msg.name || '';
      }
      if (corner) corner.syncPeerCount();
    },
    onPlayers: (state) => {
      peerLayer.setWelcomed(!!state.selfId);
      peerLayer.setSelfId(state.selfId || '');
      peerLayer.applyPeerList(state.players);
    },
    onSaid: (msg) => {
      if (islandChat) islandChat.onSaid(msg);
    },
    onSlow: () => {
      if (islandChat) islandChat.onSlow();
    }
  });
  islandChat = createIslandChat({
    document: document,
    presence: islandPresence,
    selfId: () => peerLayer.getSelfId(),
    now: () => Date.now()
  });
  if (islandChat && typeof islandChat.open === 'function') {
    const openChat = islandChat.open.bind(islandChat);
    islandChat.open = function () {
      closeNpcTalkIfOpen();
      return openChat();
    };
  }

  npcTalk = createIslandNpcTalk({
    document: document,
    window: window,
    reduced: reduced,
    getWorld: () => {
      if (!islandWeather) return null;
      const ws = islandWeather.getWorldState();
      return {
        weather: ws.weather,
        hour: ws.hour,
        festival: ws.festival,
        calendar: ws.calendar,
        news: ws.news
      };
    },
    hooks: {
      onClose: () => {
        endNpcTalkResume();
        syncInteractPrompt();
      }
    }
  });

  corner = createCornerUi({
    document: document,
    storage: window.localStorage,
    getPresence: () => islandPresence,
    getPeerCount: () => peerLayer.peers.size + 1,
    isExploring: () => mode.exploring,
    isWelcomed: () => peerLayer.getWelcomed(),
    getAudioMuted: () => islandAudio.muted,
    onAvatarChange: (id) => replaceLocalAvatar(id),
    onNickSaved: () => selfLabel.invalidateText(),
    onSelfNameToggle: () => selfLabel.invalidateText(),
    closeList: closeListPanel
  });
  corner.syncExploreHudLabels();

  minimapHud = createMinimapHud({
    document: document,
    window: window,
    isExploring: () => mode.exploring,
    openLargeMap: () => corner && corner.setMapOpen(true),
    onMapOpenChange: function () {},
    onJumpToIsland: (id) => jumpToIsland(id),
    getPaintState: function () {
      const room = rooms.inside() ? rooms.room() : null;
      const interior = rooms.inside();
      let paintPlayer;
      let worldPlayer;
      if (isDriving()) {
        worldPlayer = { x: vehicleState.carX, z: vehicleState.carZ, yaw: vehicleState.carYaw };
      } else {
        worldPlayer = { x: player.x, z: player.z, yaw: player.yaw };
      }
      if (interior) {
        paintPlayer = {
          x: player.x - INTERIOR_ORIGIN.x,
          z: player.z - INTERIOR_ORIGIN.z,
          yaw: player.yaw
        };
      } else if (isDriving()) {
        paintPlayer = worldPlayer;
      } else {
        paintPlayer = worldPlayer;
      }
      return {
        player: paintPlayer,
        worldPlayer: worldPlayer,
        interiorRoom: interior ? room : null,
        vehicleState: vehicleState,
        boatState: boatState,
        peers: peerLayer ? peerLayer.collectMinimapPeers(performance.now()) : [],
        nightMix: typeof dayNight.mix === 'number' ? dayNight.mix : (document.body.classList.contains('is-night') ? 1 : 0)
      };
    }
  });

  const exploreKeyCtx = {
    window: window,
    document: document,
    player: player,
    getMode: () => mode,
    controlsActive: controlsActive,
    islandUiOpen: islandUiOpen,
    syncInteractPrompt: syncInteractPrompt,
    casino: casino,
    graffiti: graffiti,
    roadmap: roadmap,
    hitInteractZone: () => {
      if (activeInteract === 'npc-talk' && (pendingNpcTalk || (npcTalk && npcTalk.isOpen()))) {
        return { id: 'npc-talk', action: NPC_TALK_ACTION, label: '聊天' };
      }
      return hitInteractZone(player.x, player.z, promptZones());
    },
    npcTalk: npcTalk,
    onNpcTalkInteract: onNpcTalkInteract,
    startNpcTalk: startNpcTalk,
    leaveInterior: leaveInterior,
    enterInterior: enterInterior,
    openGraffiti: openGraffiti,
    openRoadmap: openRoadmap,
    isClosedHall: isClosedHall,
    showInteriorClosed: showInteriorClosed,
    startSail: (zone) => startSail(zone),
    isSteering: () => isSteering(),
    disembarkSteer: () => disembarkSteer(),
    enterTcm: () => enterTcm(),
    enterTcmStudy: () => enterTcmStudy(),
    openCasinoBlackjack: () => casino.openBlackjackFromInterior(),
    casinoArcadePoke: (kind, claw) => casinoArcadePoke(kind, claw),
    isInside: () => rooms.inside(),
    islandChat: islandChat,
    setExplore: setExplore,
    switchDashTab: typeof window.switchDashTab === 'function' ? window.switchDashTab : null,
    clearMoveInput: clearMoveInput,
    closeMore: () => corner && corner.closeMore(),
    closeMap: () => corner && corner.closeMap(),
    closeList: closeListPanel,
    setNickOpen: (open) => corner && corner.setNickOpen(open),
    setAvatarOpen: (open) => corner && corner.setAvatarOpen(open),
    openMap: () => corner && corner.setMapOpen(true)
  };
  createExploreKeys(exploreKeyCtx);
  createExplorePointer({
    canvas: canvas,
    camera: camera,
    cam: cam,
    THREE: THREE,
    getMode: () => mode,
    controlsActive: controlsActive,
    applyDragPhi: applyDragPhi,
    getPickables: () => pickables,
    resolvePick: (hit) => {
      const careerHit = resolveCareerPickHit(hit);
      if (careerHit) return careerHit;
      return resolveTcmPickHit(hit);
    },
    onZoneClick: (zone) => {
      if (zone && typeof zone === 'object' && career.handleZoneClick(zone)) return;
      if (zone && typeof zone === 'object' && tcmFigure.handleZoneClick(zone)) return;
      if (zone === 'graffiti-wall') {
        openGraffiti();
        return;
      }
      if (zone === 'construction-board') {
        openRoadmap();
        return;
      }
      if (zone && typeof zone === 'string' && typeof window.switchDashTab === 'function') {
        window.switchDashTab(zone);
      }
    },
    isInside: () => rooms.inside(),
    interiorMinRadius: INTERIOR_POSE.radius,
    getInteriorPoseRadius: () => {
      if (!rooms.inside()) return null;
      const custom = interiorFollowPose(rooms.room(), camera.aspect);
      return (custom || INTERIOR_POSE).radius;
    }
  });
  if (typeof window !== 'undefined') {
    window.__islandCam = () => ({
      radius: cam.radius,
      targetRadius: cam.targetRadius,
      theta: cam.theta,
      targetTheta: cam.targetTheta,
      phi: cam.phi,
      targetPhi: cam.targetPhi,
      mode: mode,
      inside: rooms.inside()
    });
  }
  createExploreJoystick({
    stickRoot: document.getElementById('islandJoystick'),
    stickKnob: document.getElementById('islandJoystickKnob'),
    player: player,
    canMove: () => canExploreMove()
  });

  window.PunchClockIsland = {
    focus: focus,
    reset: reset,
    setMode: setMode,
    setExplore: setExplore,
    isExploring: () => mode.exploring,
    debugNpcs: perfEnabled && typeof people.collectTalkableNpcs === 'function'
      ? () => people.collectTalkableNpcs()
      : undefined,
    debugTeleport: perfEnabled
      ? (x, z, yaw) => {
        player.x = x;
        player.z = z;
        if (Number.isFinite(yaw)) player.yaw = yaw;
        setPlayerAnchor(player.x, P, player.z, player.yaw);
        localView.object.position.set(player.x, P, player.z);
        stepMilu(0, P);
        syncInteractPrompt();
      }
      : undefined,
    debugEnterInterior: perfEnabled ? (buildingId) => enterInterior(buildingId) : undefined,
    openCasino: () => casino.openLobby(),
    closeCasino: () => casino.closeLobby(),
    isCasinoOpen: () => casino.isOpen(),
    openGraffiti: () => graffiti.open(),
    closeGraffiti: () => graffiti.close(),
    isGraffitiOpen: () => graffiti.isOpen(),
    openRoadmap: () => roadmap.open(),
    closeRoadmap: () => roadmap.close(),
    isRoadmapOpen: () => roadmap.isOpen(),
    casinoName: CASINO_NAME,
    getBgmTrack: () => islandAudio.currentTrack
  };

  resize();
  dayNight.refresh();
  syncExploreUi();
  const tcmRestore = consumeTcmReturn(window.sessionStorage);
  const open = document.querySelector('.dash-panel.is-active');
  if (tcmRestore) {
    setExplore(true, true);
    if (tcmRestore.target === 'hall' && tcmRestore.buildingId === 'tcm-site') {
      void enterTcmHallFromReturn({
        x: tcmRestore.x,
        z: tcmRestore.z,
        yaw: tcmRestore.yaw
      });
    } else {
      player.x = tcmRestore.x;
      player.z = tcmRestore.z;
      player.yaw = tcmRestore.yaw;
      setPlayerAnchor(player.x, P, player.z, player.yaw);
      localView.object.position.set(player.x, P, player.z);
      stepMilu(0, P);
    }
  } else if (open && !open.hasAttribute('hidden')) {
    const id = open.id.replace('panel-', '');
    applyPose(POSES[id] || POSES.home, true);
    setActive(POSES[id] ? id : '');
  } else {
    applyPose(POSES.home, true);
  }
  renderer.render(scene, camera);
  placeLabels();
  if (!document.hidden) start();
  applyIslandOpenRoadmapParam(window, () => roadmap.open());

}
