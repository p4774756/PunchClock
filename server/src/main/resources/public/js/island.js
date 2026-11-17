import * as THREE from 'three';
import {
  PLAYER_SPAWN,
  PLAYER_SPEED,
  MOVE_CODES,
  DEFAULT_INTERACT_ZONES,
  canStand,
  moveVector,
  cameraRelative,
  stepPlayer,
  smoothingFactor,
  createExploreFlow,
  controlsActive,
  playerVisible,
  shouldCaptureMoveKey,
  hitInteractZone,
  shouldShowInteractPrompt,
  shouldTriggerInteractKey,
  clampOrbitRadius
} from './island-explore.js';
import {
  PANORAMA,
  poseForIsland,
  isClosedHall,
  islandAt
} from './island-layout.js';
import { createCasinoController, CASINO_NAME } from './island-casino.js';
import { createGraffitiController } from './island-graffiti.js';
import { createRoadmapController } from './island-roadmap.js';
import {
  INTERIOR_ORIGIN,
  ISLAND_CAMERA_FAR,
  createInteriorSession,
  stepInInterior,
  shouldHideIslandLabels
} from './island-interior.js';
import {
  createMiluState,
  createMiluView,
  isNearWave,
  miluClipPath,
  miluStep,
  screenAxes
} from './island-milu.js';
import {
  HIDDEN_DISCONNECT_MS,
  cleanNick,
  createIslandPresence,
  createMotion,
  loadNick,
  pushTarget,
  sampleMotion,
  shouldSendPos
} from './island-presence.js';
import { createIslandChat, shouldOpenChatKey } from './island-chat.js';
import { createIslandAudio } from './island-audio.js';
import { G, P, createSceneKit } from './island-kit.js';
import { createDayNight } from './island-daynight.js';
import { createUpdateLoop } from './island-loop.js';
import {
  EXPLORE_POSE,
  INTERIOR_POSE,
  applyDragPhi,
  createPoses,
  createCameraRig
} from './island-camera.js';
import { buildIslandWorld } from './island-world.js';
import { buildIslandBuildings } from './island-buildings.js';
import { buildIslandProps, registerSceneMotion } from './island-props.js';
import { buildIslandBeach } from './island-beach.js';
import { buildIslandPeople } from './island-people.js';
import { createIslandSky } from './island-sky.js';
import { createIslandSea } from './island-sea.js';
import { createFootprintView } from './island-footprints.js';

const ZONES = ['devices', 'system', 'photos'];

const reduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
const stage = document.getElementById('islandStage');
const canvas = document.getElementById('islandCanvas');

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
}

if (stage && canvas) boot();

function boot() {
  let renderer;
  try {
    renderer = new THREE.WebGLRenderer({ canvas, antialias: true });
  } catch (e) {
    renderer = null;
  }
  if (!renderer || !renderer.getContext()) {
    stage.classList.add('is-fallback');
    const list = document.getElementById('islandList');
    const listBtn = document.getElementById('islandListBtn');
    if (list) list.hidden = false;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'true');
    bootCasinoFallback();
    return;
  }

  renderer.outputColorSpace = THREE.SRGBColorSpace;
  renderer.toneMapping = THREE.ACESFilmicToneMapping;
  renderer.toneMappingExposure = 1.02;
  renderer.shadowMap.enabled = true;
  renderer.shadowMap.type = THREE.PCFSoftShadowMap;
  renderer.setPixelRatio(Math.min(window.devicePixelRatio || 1, 2));

  const scene = new THREE.Scene();
  const skyDay = new THREE.Color(0xd8f7f8);
  const skyNight = new THREE.Color(0x1a3e86);
  scene.background = skyDay.clone();
  scene.fog = new THREE.Fog(skyDay.clone(), 150, 320);

  const camera = new THREE.PerspectiveCamera(25, 1, 0.4, ISLAND_CAMERA_FAR);
  const clock = new THREE.Clock();

  const hemi = new THREE.HemisphereLight(0xfff8ef, 0xe7d8bc, 0.62);
  scene.add(hemi);
  const sun = new THREE.DirectionalLight(0xfff3d6, 1.42);
  sun.position.set(-9.5, 20.5, 11);
  sun.castShadow = true;
  sun.shadow.mapSize.set(2048, 2048);
  sun.shadow.camera.left = -36;
  sun.shadow.camera.right = 36;
  sun.shadow.camera.top = 36;
  sun.shadow.camera.bottom = -36;
  sun.shadow.camera.near = 2;
  sun.shadow.camera.far = 80;
  sun.shadow.bias = -0.00045;
  sun.shadow.normalBias = 0.032;
  sun.target.position.set(0, 0.4, 0);
  scene.add(sun);
  scene.add(sun.target);

  const hemiDay = new THREE.Color(0xfff8ef);
  const hemiNight = new THREE.Color(0x8eabd4);
  const groundDay = new THREE.Color(0xe7d8bc);
  const groundNight = new THREE.Color(0x243044);
  const sunDay = new THREE.Color(0xfff3d6);
  const sunNight = new THREE.Color(0xc5d4f2);

  const POSES = createPoses(PANORAMA);
  const anchors = {};
  const rings = {};
  const pickables = [];
  const bobs = [];
  const idles = [];
  const walkers = [];
  const clouds = [];
  const swimmers = [];
  const steams = [];
  const kit = createSceneKit(THREE, { scene: scene, bobs: bobs });
  const { addMesh, addBox, geo } = kit;
  const dayNight = createDayNight({
    reduced: reduced,
    hidden: () => document.hidden
  });
  dayNight.onMix((t) => {
    kit.forEachMaterial((material) => {
      material.color.copy(material.userData.day).lerp(material.userData.night, t);
      material.emissiveIntensity = material.userData.dayE + (material.userData.nightE - material.userData.dayE) * t;
    });
    scene.background.copy(skyDay).lerp(skyNight, t);
    scene.fog.color.copy(scene.background);
    scene.fog.near = 150 + (100 - 150) * t;
    scene.fog.far = 320 + (240 - 320) * t;
    hemi.color.copy(hemiDay).lerp(hemiNight, t);
    hemi.groundColor.copy(groundDay).lerp(groundNight, t);
    hemi.intensity = 0.62 + (0.38 - 0.62) * t;
    sun.color.copy(sunDay).lerp(sunNight, t);
    sun.intensity = 1.42 + (0.5 - 1.42) * t;
    renderer.toneMappingExposure = 1.02 + (0.94 - 1.02) * t;
    kit.forEachLamp((item) => {
      item.light.intensity = item.power * t;
    });
  });

  const worldResult = buildIslandWorld({ scene: scene, kit: kit });
  const sea = createIslandSea(THREE, { kit: kit, reduced: reduced });
  scene.add(sea.group);
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
    dayNight: dayNight
  });
  buildIslandProps({
    scene: scene,
    kit: kit,
    bobs: bobs,
    clouds: clouds,
    swimmers: swimmers
  });
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
    walkers: walkers
  });
  const makePerson = people.makePerson;
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
  registerSceneMotion(loop, {
    reduced: reduced,
    walkers: walkers,
    bobs: bobs,
    idles: idles,
    steams: steams,
    clouds: clouds,
    swimmers: swimmers,
    shaker: shaker,
    P: P
  });
  loop.register(function updateSea(dt, elapsed) {
    sea.update(dt, elapsed);
  });
  dayNight.onMix((t) => {
    sea.setMix(t);
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
    sky.tick(dt, elapsed);
  });

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

  const playerMesh = makePlayerAvatar();
  scene.add(playerMesh);
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
  playerMesh.position.set(player.x, P, player.z);
  playerMesh.rotation.y = player.yaw;
  playerMesh.visible = false;

  const milu = createMiluView(THREE);
  scene.add(milu.object);
  milu.object.visible = false;
  let miluState = createMiluState();
  const peers = new Map();
  let islandPresence = null;
  let islandChat = null;
  let peerWelcomed = false;
  let peerSelfId = '';
  let peerScene = 'outdoor';
  let lastPosSent = null;
  let peerHideTimer = 0;
  const MILU_NPCS = [
    { x: 0.15, z: 0.42 },
    { x: 0.05, z: 2.25 },
    { x: -0.05, z: 2.9 },
    { x: 0.12, z: 3.55 },
    { x: -4.85, z: 4.15 },
    { x: -4.85, z: 2.95 },
    { x: 4.55, z: 4.08 },
    { x: -0.15, z: 8.45 }
  ];

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

  function isTypingTarget(target) {
    if (!target) return false;
    const tag = target.tagName;
    if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
    if (target.isContentEditable) return true;
    return !!(target.closest && target.closest('input, textarea, select, [contenteditable="true"]'));
  }

  // 'explore'：焦點在畫布、探索按鈕、搖桿或頁面本身；'control'：其它可操作的 UI 元件。
  function focusKind(target) {
    if (!target || target === document.body || target === document.documentElement || target === window) return 'explore';
    if (!target.closest) return 'explore';
    if (target.closest('#islandStage, #islandExplore, #islandJoystick')) return 'explore';
    if (target.closest('button, a[href], summary, [tabindex], [role="button"], [role="listbox"], [role="menu"], [role="option"], [role="tab"]')) return 'control';
    return 'explore';
  }

  function sheetOpen() {
    const sheet = document.getElementById('islandSheet');
    return !!(sheet && !sheet.hidden);
  }

  function islandUiOpen() {
    const list = document.getElementById('islandList');
    return sheetOpen() || !!(list && !list.hidden) || casinoOpen() || graffitiOpen() || roadmapOpen();
  }

  let exploreSaved = null;
  let casino = null;
  let graffiti = null;
  let roadmap = null;
  const rooms = createInteriorSession();
  let roomView = null;
  let interiorBusy = false;
  let interiorGeneration = 0;
  const INTERIOR_MODULES = {
    shop: () => import('./island-shop-interior.js')
  };

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

  const cam = createCameraRig(THREE, camera, {
    reduced: reduced,
    groundY: G,
    following: () => controlsActive(mode),
    smoothingFactor: smoothingFactor,
    nightFactor: () => dayNight.mix,
    initial: POSES.home
  });
  let animating = false;
  const pointer = new THREE.Vector2();
  const raycaster = new THREE.Raycaster();
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

  function syncExploreUi() {
    const btn = document.getElementById('islandExplore');
    const stick = document.getElementById('islandJoystick');
    const hint = document.getElementById('islandExploreHint');
    const exploring = mode.exploring;
    document.body.classList.toggle('is-exploring', exploring);
    if (btn) {
      btn.setAttribute('aria-pressed', exploring ? 'true' : 'false');
      btn.setAttribute('aria-label', exploring ? '退出探索模式' : '進入探索模式');
      btn.textContent = exploring ? '退出' : '探索';
    }
    const overlay = casinoOpen() || graffitiOpen();
    if (stick) stick.hidden = !exploring || overlay;
    if (hint) hint.hidden = !exploring || overlay;
    if (islandChat) islandChat.setExploring(exploring && !overlay);
    syncInteractPrompt();
    syncPeerCount();
  }

  function syncInteractPrompt() {
    if (!casino) return false;
    const zone = hitInteractZone(player.x, player.z, promptZones());
    activeInteract = zone ? (zone.id || '') : '';
    const show = shouldShowInteractPrompt({
      exploring: mode.exploring,
      paused: mode.paused,
      uiOpen: sheetOpen() || !!(document.getElementById('islandList') && !document.getElementById('islandList').hidden),
      lobbyOpen: casinoOpen() || graffitiOpen() || roadmapOpen(),
      zoneId: activeInteract
    });
    casino.setPromptVisible(show, zone && zone.label ? zone.label : '進入賭場');
    return show;
  }

  function followPlayerCamera(instant) {
    const pose = rooms.inside() ? INTERIOR_POSE : EXPLORE_POSE;
    cam.follow(player.x, G + 1.05, player.z, pose, instant);
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
      playerMesh.position.set(player.x, P, player.z);
      playerMesh.rotation.y = player.yaw;
      dispatchMode('enter');
      peerScene = currentScene();
      if (islandPresence) islandPresence.connect();
      if (sheetOpen()) {
        dispatchMode('panelOpen');
      } else {
        setActive('');
        followPlayerCamera(instant);
      }
    } else {
      if (rooms.inside()) abortInterior();
      if (casino && casino.isOpen()) casino.closeLobby();
      dispatchMode('exit');
      peerWelcomed = false;
      peerSelfId = '';
      peerScene = 'outdoor';
      lastPosSent = null;
      clearPeers();
      if (islandChat) islandChat.clear();
      if (islandPresence) islandPresence.disconnect();
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

  function setMode(next) {
    const night = next === 'night';
    dayNight.setTarget(night ? 1 : 0);
    if (reduced || document.hidden) renderer.render(scene, camera);
    const dayBtn = document.getElementById('islandDay');
    const nightBtn = document.getElementById('islandNight');
    if (dayBtn) dayBtn.setAttribute('aria-pressed', night ? 'false' : 'true');
    if (nightBtn) nightBtn.setAttribute('aria-pressed', night ? 'true' : 'false');
    document.body.classList.toggle('is-night', night);
  }

  function resize() {
    const width = stage.clientWidth || window.innerWidth;
    const height = stage.clientHeight || window.innerHeight;
    camera.aspect = width / Math.max(height, 1);
    camera.fov = 25;
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
      scene: sceneName
    };
  }

  function peerOffset() {
    if (rooms.inside() && (!casino || !casino.isOpen())) return INTERIOR_ORIGIN;
    return { x: 0, z: 0 };
  }

  function clearPeers() {
    Array.from(peers.keys()).forEach(despawnPeer);
  }

  function despawnPeer(id) {
    const row = peers.get(id);
    if (!row) return;
    peers.delete(id);
    scene.remove(row.view.object);
    if (row.view.dispose) row.view.dispose();
    if (row.label && row.label.parentNode) row.label.parentNode.removeChild(row.label);
  }

  function tintRemote(view) {
    view.object.traverse((node) => {
      if (!node.material || !node.material.color || !(node.material.alphaTest > 0)) return;
      node.material.color.setHex(0xb7d4ff);
    });
  }

  function spawnPeer(info) {
    const view = createMiluView(THREE, { shared: milu.sharedTextures() });
    tintRemote(view);
    view.object.visible = false;
    scene.add(view.object);
    const label = document.createElement('div');
    label.className = 'island-peer-name';
    if (info.id) label.dataset.peerId = info.id;
    const nameEl = document.createElement('span');
    nameEl.className = 'island-peer-name-text';
    nameEl.textContent = info.name || '';
    label.appendChild(nameEl);
    const host = document.getElementById('islandLabels');
    if (host) host.appendChild(label);
    return {
      view: view,
      label: label,
      name: info.name || '',
      moving: !!info.moving,
      motion: createMotion(),
      miluState: createMiluState()
    };
  }

  function applyPeerList(list) {
    const seen = Object.create(null);
    const now = performance.now();
    const players = list || [];
    for (let i = 0; i < players.length; i++) {
      const info = players[i];
      if (!info || !info.id) continue;
      seen[info.id] = true;
      let row = peers.get(info.id);
      if (!row) {
        row = spawnPeer(info);
        peers.set(info.id, row);
      }
      row.name = info.name || '';
      row.moving = !!info.moving;
      if (row.label) {
        const nameEl = row.label.querySelector('.island-peer-name-text');
        if (nameEl) nameEl.textContent = row.name;
        else row.label.textContent = row.name;
      }
      pushTarget(row.motion, { x: info.x, z: info.z, yaw: info.yaw }, now);
    }
    Array.from(peers.keys()).forEach((id) => {
      if (!seen[id]) despawnPeer(id);
    });
    syncPeerCount();
  }

  function syncPeerCount() {
    const el = document.getElementById('islandPeerCount');
    if (!el) return;
    const show = !!(mode.exploring && peerWelcomed);
    el.hidden = !show;
    if (!show) return;
    el.textContent = '島上 ' + (peers.size + 1) + ' 人';
  }

  function stepPeers(dt) {
    const now = performance.now();
    const off = peerOffset();
    peers.forEach((row) => {
      const shown = sampleMotion(row.motion, now, reduced);
      const x = shown.x + off.x;
      const z = shown.z + off.z;
      row.view.object.position.set(x, P, z);
      const ready = row.view.ready();
      row.view.object.visible = !!ready;
      const moving = !!row.moving;
      const axes = screenAxes(moving ? Math.sin(shown.yaw) : 0, moving ? Math.cos(shown.yaw) : 0, cam.theta);
      const anim = row.view.anim();
      row.miluState = miluStep(row.miluState, dt, {
        moving: moving,
        screenX: axes.x,
        screenZ: axes.z,
        near: false,
        reduced: reduced,
        anim: anim
      });
      row.view.show(miluClipPath(row.miluState, anim));
      row.view.faceCamera(camera, x, z);
    });
  }

  function syncPresence(now) {
    if (!islandPresence || !mode.exploring) return;
    const pose = presencePose();
    if (pose.scene !== peerScene) {
      peerScene = pose.scene;
      clearPeers();
      lastPosSent = null;
      syncPeerCount();
    }
    if (!shouldSendPos(lastPosSent, pose, now)) return;
    if (islandPresence.sendPos(pose)) {
      lastPosSent = {
        x: pose.x,
        z: pose.z,
        yaw: pose.yaw,
        moving: pose.moving,
        scene: pose.scene,
        sentAt: now
      };
    }
  }

  function placePeerLabels() {
    const width = stage.clientWidth || 1;
    const height = stage.clientHeight || 1;
    const off = peerOffset();
    const now = performance.now();
    const peerPlaces = [];
    peers.forEach((row, id) => {
      const el = row.label;
      if (!el) return;
      if (!row.view.object.visible) {
        el.style.visibility = 'hidden';
        peerPlaces.push({ id: id, visible: false });
        return;
      }
      const shown = sampleMotion(row.motion, now, reduced);
      projected.set(shown.x + off.x, P + 1.18, shown.z + off.z);
      projected.project(camera);
      if (projected.z > 1 || projected.x < -1.08 || projected.x > 1.08 || projected.y < -1.08 || projected.y > 1.08) {
        el.style.visibility = 'hidden';
        peerPlaces.push({ id: id, visible: false });
        return;
      }
      const x = (projected.x * 0.5 + 0.5) * width;
      const y = (-projected.y * 0.5 + 0.5) * height;
      el.style.visibility = 'visible';
      el.style.transform = 'translate(' + x + 'px, ' + y + 'px) translate(-50%, -120%)';
      peerPlaces.push({ id: id, x: x, y: y, visible: true });
    });
    if (islandChat) {
      islandChat.placeBubbles({
        x: player.x,
        y: P + 1.18,
        z: player.z,
        visible: !!(mode.exploring && (milu.object.visible || playerMesh.visible)),
        width: width,
        height: height,
        camera: camera,
        projected: projected,
        peers: peerPlaces
      });
    }
  }

  function placeLabels() {
    if (shouldHideIslandLabels(rooms.inside(), interiorBusy)) {
      document.querySelectorAll('[data-anchor]').forEach((el) => {
        el.style.visibility = 'hidden';
      });
      placePeerLabels();
      return;
    }
    const width = stage.clientWidth || 1;
    const height = stage.clientHeight || 1;
    const far = cam.targetRadius > 42;
    const nodes = [];
    document.querySelectorAll('[data-anchor]').forEach((el) => {
      const minor = el.hasAttribute('data-minor');
      const rank = Number(el.getAttribute('data-rank') || 3);
      if (far && (minor || rank >= 2)) {
        el.style.visibility = 'hidden';
        return;
      }
      const anchor = anchors[el.getAttribute('data-anchor')];
      if (!anchor) return;
      anchor.getWorldPosition(projected);
      const dist = projected.distanceTo(camera.position);
      projected.project(camera);
      if (projected.z > 1 || projected.x < -1.08 || projected.x > 1.08 || projected.y < -1.08 || projected.y > 1.08) {
        el.style.visibility = 'hidden';
        return;
      }
      if (minor && dist > 32) {
        el.style.visibility = 'hidden';
        return;
      }
      nodes.push({
        el: el,
        x: (projected.x * 0.5 + 0.5) * width,
        y: (-projected.y * 0.5 + 0.5) * height,
        minor: minor,
        rank: rank
      });
    });
    nodes.sort((a, b) => a.rank - b.rank);
    const placed = [];
    nodes.forEach((node) => {
      const w = node.el.offsetWidth || 76;
      const h = node.el.offsetHeight || 28;
      let y = node.y;
      let hidden = false;
      for (let guard = 0; guard < 5; guard++) {
        const hit = placed.some((item) => {
          return Math.abs(item.x - node.x) < (item.w + w) * 0.48
            && Math.abs(item.y - y) < (item.h + h) * 0.5;
        });
        if (!hit) break;
        if (node.minor) {
          hidden = true;
          break;
        }
        y += h + 8;
      }
      if (hidden) {
        node.el.style.visibility = 'hidden';
        return;
      }
      node.el.style.visibility = 'visible';
      node.el.style.transform = 'translate(' + node.x + 'px, ' + y + 'px) translate(-50%, -120%)';
      placed.push({ x: node.x, y: y, w: w, h: h });
    });
    placePeerLabels();
  }

  function ndc(event) {
    const rect = canvas.getBoundingClientRect();
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  }

  function hitZone(event) {
    ndc(event);
    raycaster.setFromCamera(pointer, camera);
    const hits = raycaster.intersectObjects(pickables, false);
    return hits.length ? hits[0].object.userData.zone : '';
  }

  let dragging = false;
  let moved = false;
  let lastX = 0;
  let lastY = 0;

  canvas.addEventListener('pointerdown', (event) => {
    dragging = true;
    moved = false;
    lastX = event.clientX;
    lastY = event.clientY;
    canvas.style.cursor = 'grabbing';
    if (canvas.setPointerCapture) canvas.setPointerCapture(event.pointerId);
  });
  canvas.addEventListener('pointermove', (event) => {
    if (!dragging) {
      canvas.style.cursor = hitZone(event) ? 'pointer' : 'grab';
      return;
    }
    const dx = event.clientX - lastX;
    const dy = event.clientY - lastY;
    if (dx * dx + dy * dy > 9) moved = true;
    lastX = event.clientX;
    lastY = event.clientY;
    cam.targetTheta -= dx * 0.005;
    const followCam = controlsActive(mode);
    // 探索：往上拖＝抬頭；全景維持「抓住世界拖」。左右不變。
    cam.targetPhi = applyDragPhi(cam.targetPhi, dy, followCam);
  });
  canvas.addEventListener('pointerup', (event) => {
    dragging = false;
    canvas.style.cursor = 'grab';
    if (!moved) {
      const zone = hitZone(event);
      if (zone === 'graffiti-wall') {
        openGraffiti();
        return;
      }
      if (zone === 'construction-board') {
        openRoadmap();
        return;
      }
      if (zone && typeof window.switchDashTab === 'function') window.switchDashTab(zone);
    }
  });
  canvas.addEventListener('pointercancel', () => {
    dragging = false;
    canvas.style.cursor = 'grab';
  });
  canvas.addEventListener('pointerleave', () => {
    if (!dragging) canvas.style.cursor = 'grab';
  });
  canvas.addEventListener('wheel', (event) => {
    event.preventDefault();
    const followCam = controlsActive(mode);
    const indoorMin = followCam && rooms.inside() ? INTERIOR_POSE.radius : null;
    cam.targetRadius = clampOrbitRadius(cam.targetRadius, event.deltaY * 0.01, followCam, indoorMin);
  }, { passive: false });


  function miluNpcPoints() {
    const points = MILU_NPCS.slice();
    for (let i = 0; i < walkers.length; i++) {
      points.push({ x: walkers[i].obj.position.x, z: walkers[i].obj.position.z });
    }
    return points;
  }

  function syncAvatarVisible() {
    const show = playerVisible(mode);
    const useMilu = show && milu.ready() && !milu.failed();
    milu.object.visible = useMilu;
    playerMesh.visible = show && !useMilu;
  }

  function stepMilu(dt) {
    const axes = screenAxes(player.vx, player.vz, cam.theta);
    const anim = milu.anim();
    miluState = miluStep(miluState, dt, {
      moving: player.moving,
      screenX: axes.x,
      screenZ: axes.z,
      near: isNearWave(player.x, player.z, miluNpcPoints(), INTERACT_ZONES, anim),
      reduced: reduced,
      anim: anim
    });
    milu.show(miluClipPath(miluState, anim));
    milu.object.position.set(player.x, P, player.z);
    syncAvatarVisible();
  }

  function updatePlayer(dt) {
    if (!flow.canMove()) {
      if (rooms.inside()) rooms.setPaused(true);
      player.moving = false;
      player.vx = 0;
      player.vz = 0;
      playerBob = 0;
      playerMesh.position.set(player.x, P, player.z);
      playerMesh.rotation.y = player.yaw;
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
      playerMesh.position.set(player.x, P, player.z);
      playerMesh.rotation.y = player.yaw;
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
    playerMesh.position.set(player.x, P + bobY, player.z);
    playerMesh.rotation.y = player.yaw;
    stepMilu(dt);
    cam.targetLook.set(player.x, G + 1.05, player.z);
    syncInteractPrompt();
    syncFootprints(dt);
  }

  function frame() {
    const dt = Math.min(0.1, clock.getDelta() || 0);
    updatePlayer(dt);
    syncPresence(performance.now());
    cam.step(dt);
    milu.faceCamera(camera, player.x, player.z);
    stepPeers(dt);
    dayNight.step(dt);
    loop.tick(dt, clock.elapsedTime);
    renderer.render(scene, camera);
    placeLabels();
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
    if (islandAudio) islandAudio.onVisibilityChange(document.hidden);
  });
  window.addEventListener('blur', () => {
    clearMoveInput();
  });

  window.addEventListener('keydown', (event) => {
    if (MOVE_CODES[event.code]) {
      if (event.altKey || event.ctrlKey || event.metaKey) return;
      const capture = shouldCaptureMoveKey({
        code: event.code,
        typing: isTypingTarget(event.target),
        active: controlsActive(mode),
        uiOpen: islandUiOpen(),
        focus: focusKind(event.target)
      });
      if (!capture) return;
      player.keys[event.code] = true;
      event.preventDefault();
      return;
    }
    const promptVisible = syncInteractPrompt();
    if (casino && shouldTriggerInteractKey({
      promptVisible: promptVisible,
      typing: isTypingTarget(event.target),
      focus: focusKind(event.target),
      code: event.code,
      key: event.key
    })) {
      // 焦點已在「進入賭場」按鈕時交給按鈕自己的 click／Enter，避免重複開啟。
      if (event.target && event.target.id === 'islandInteract') return;
      event.preventDefault();
      const zone = hitInteractZone(player.x, player.z, promptZones());
      if (rooms.inside()) {
        if (zone && zone.action === 'exitInterior') leaveInterior();
        return;
      }
      if (zone && zone.action === 'openGraffiti') openGraffiti();
      else if (zone && zone.action === 'openRoadmap') openRoadmap();
      else if (zone && zone.action === 'enterInterior') enterInterior(zone.id);
      else if (zone && isClosedHall(zone.action)) showInteriorClosed();
      else if (casino) casino.openLobby();
      return;
    }
    const nickPopEl = document.getElementById('islandNickPop');
    if (shouldOpenChatKey({
      exploring: mode.exploring,
      uiOpen: islandUiOpen(),
      typing: isTypingTarget(event.target),
      nickOpen: !!(nickPopEl && !nickPopEl.hidden),
      chatOpen: !!(islandChat && islandChat.isOpen()),
      promptVisible: promptVisible,
      key: event.key,
      code: event.code,
      altKey: event.altKey,
      ctrlKey: event.ctrlKey,
      metaKey: event.metaKey
    })) {
      event.preventDefault();
      if (islandChat) islandChat.open();
      return;
    }
    if (event.key !== 'Escape') return;
    if (islandChat && islandChat.isOpen()) {
      islandChat.close();
      return;
    }
    if (roadmap && roadmap.isOpen()) {
      roadmap.close();
      return;
    }
    if (graffiti && graffiti.isOpen()) {
      graffiti.close();
      return;
    }
    if (isTypingTarget(event.target)) return;
    if (casino && casino.isOpen()) {
      casino.closeLobby();
      return;
    }
    const sheet = document.getElementById('islandSheet');
    if (sheet && !sheet.hidden && typeof window.switchDashTab === 'function') {
      window.switchDashTab('');
      return;
    }
    if (rooms.inside()) {
      leaveInterior();
      return;
    }
    if (mode.exploring) setExplore(false, false);
  });
  document.addEventListener('focusin', (event) => {
    if (isTypingTarget(event.target) || focusKind(event.target) !== 'explore') clearMoveInput();
  });
  window.addEventListener('keyup', (event) => {
    if (!MOVE_CODES[event.code]) return;
    player.keys[event.code] = false;
  });

  const stickRoot = document.getElementById('islandJoystick');
  const stickKnob = document.getElementById('islandJoystickKnob');
  let stickPointerId = null;
  function setStickFromEvent(event) {
    if (!stickRoot) return;
    const rect = stickRoot.getBoundingClientRect();
    const cx = rect.left + rect.width / 2;
    const cy = rect.top + rect.height / 2;
    const max = Math.min(rect.width, rect.height) * 0.34;
    let dx = event.clientX - cx;
    let dy = event.clientY - cy;
    const len = Math.hypot(dx, dy) || 1;
    if (len > max) {
      dx = dx / len * max;
      dy = dy / len * max;
    }
    player.stickX = dx / max;
    player.stickY = dy / max;
    player.stickActive = Math.hypot(player.stickX, player.stickY) > 0.08;
    if (stickKnob) stickKnob.style.transform = 'translate(' + dx + 'px, ' + dy + 'px)';
  }
  function endStick(event) {
    if (stickPointerId != null && event && event.pointerId !== stickPointerId) return;
    stickPointerId = null;
    player.stickX = 0;
    player.stickY = 0;
    player.stickActive = false;
    if (stickKnob) stickKnob.style.transform = 'translate(0px, 0px)';
  }
  if (stickRoot) {
    stickRoot.addEventListener('pointerdown', (event) => {
      if (!flow.canMove()) return;
      event.preventDefault();
      stickPointerId = event.pointerId;
      if (stickRoot.setPointerCapture) stickRoot.setPointerCapture(event.pointerId);
      setStickFromEvent(event);
    });
    stickRoot.addEventListener('pointermove', (event) => {
      if (stickPointerId !== event.pointerId) return;
      event.preventDefault();
      setStickFromEvent(event);
    });
    stickRoot.addEventListener('pointerup', endStick);
    stickRoot.addEventListener('pointercancel', endStick);
    stickRoot.addEventListener('lostpointercapture', endStick);
  }

  const list = document.getElementById('islandList');
  const listBtn = document.getElementById('islandListBtn');
  if (listBtn && list) {
    listBtn.addEventListener('click', () => {
      list.hidden = !list.hidden;
      listBtn.setAttribute('aria-expanded', list.hidden ? 'false' : 'true');
      syncInteractPrompt();
    });
    list.querySelectorAll('[data-island]').forEach((btn) => {
      btn.addEventListener('click', () => {
        const pose = poseForIsland(btn.getAttribute('data-island'));
        if (!pose) return;
        if (mode.exploring) setExplore(false, true);
        if (sheetOpen() && typeof window.switchDashTab === 'function') window.switchDashTab('');
        if (casino && casino.isOpen()) casino.closeLobby();
        applyPose(pose, true);
        setActive('');
        list.hidden = true;
        listBtn.setAttribute('aria-expanded', 'false');
        syncInteractPrompt();
      });
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
  const nightBtn = document.getElementById('islandNight');
  if (dayBtn) dayBtn.addEventListener('click', () => setMode('day'));
  if (nightBtn) nightBtn.addEventListener('click', () => setMode('night'));

  const islandAudio = createIslandAudio({
    tracks: { main: '/audio/bgm-main.ogg' },
    storage: typeof localStorage !== 'undefined' ? localStorage : null,
    volume: 0.35,
    ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 }
  });
  const audioBtn = document.getElementById('islandAudio');

  function syncAudioButton() {
    if (!audioBtn) return;
    const on = !islandAudio.muted;
    audioBtn.setAttribute('aria-pressed', on ? 'true' : 'false');
    audioBtn.setAttribute('aria-label', on ? '靜音背景音樂與海浪聲' : '開啟背景音樂與海浪聲');
    const iconEl = audioBtn.querySelector('.island-tool-audio-icon');
    if (iconEl) iconEl.textContent = on ? '🔊' : '🔇';
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

  function promptZones() {
    if (!rooms.inside()) return INTERACT_ZONES;
    const exit = rooms.exitZone();
    if (!exit) return [];
    return [{
      id: exit.id,
      x: exit.x + INTERIOR_ORIGIN.x,
      z: exit.z + INTERIOR_ORIGIN.z,
      r: exit.r,
      label: exit.label,
      action: exit.action
    }];
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
    playerMesh.position.set(player.x, P, player.z);
    playerMesh.rotation.y = player.yaw;
    milu.object.position.set(player.x, P, player.z);
    followPlayerCamera(true);
  }

  function mountRoom(view) {
    disposeRoom();
    roomView = view;
    view.group.position.set(INTERIOR_ORIGIN.x, 0, INTERIOR_ORIGIN.z);
    scene.add(view.group);
    view.materials.forEach((material) => kit.addMaterial(material));
    if (view.lamp) {
      view.lamp.light.intensity = view.lamp.power * dayNight.mix;
      kit.addLamp(view.lamp);
    }
    sky.setVisible(false);
    dayNight.refresh();
    islandAudio.setInsideRoom(true);
  }

  function disposeRoom() {
    if (!roomView) return;
    scene.remove(roomView.group);
    const mats = roomView.materials || [];
    for (let i = 0; i < mats.length; i++) kit.removeMaterial(mats[i]);
    if (roomView.lamp) kit.removeLamp(roomView.lamp);
    roomView.dispose();
    roomView = null;
    sky.setVisible(true);
    islandAudio.setInsideRoom(false);
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
    if (interiorBusy || rooms.inside() || !mode.exploring) return;
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
      mountRoom(mod.createInterior(THREE, { groundY: G }));
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
    syncInteractPrompt();
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
    syncInteractPrompt();
  }

  function openGraffiti() {
    if (casino && casino.isOpen()) casino.closeLobby();
    if (roadmap && roadmap.isOpen()) roadmap.close();
    if (graffiti) graffiti.open();
  }

  let closedHallTimer = 0;
  function showInteriorClosed() {
    const hint = document.getElementById('islandExploreHint');
    const button = document.getElementById('islandInteract');
    if (button) {
      button.textContent = '室內尚未開放';
      button.setAttribute('aria-label', '室內尚未開放');
    }
    if (!hint) return;
    hint.querySelectorAll('span').forEach((el) => {
      el.textContent = '室內尚未開放';
    });
    if (closedHallTimer) window.clearTimeout(closedHallTimer);
    closedHallTimer = window.setTimeout(() => {
      closedHallTimer = 0;
      const desk = hint.querySelector('.island-explore-hint-desk');
      const touch = hint.querySelector('.island-explore-hint-touch');
      if (desk) desk.textContent = 'WASD 或方向鍵移動 · 拖曳旋轉鏡頭';
      if (touch) touch.textContent = '用左下角搖桿移動 · 拖曳旋轉鏡頭';
    }, 1800);
  }

  function openRoadmap() {
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
      onPrompt: () => {
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
        if (isClosedHall(zone.action)) {
          showInteriorClosed();
          return true;
        }
        return false;
      }
    }
  });
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

  islandPresence = createIslandPresence({
    location: window.location,
    WebSocket: window.WebSocket,
    storage: window.localStorage,
    nick: () => loadNick(window.localStorage),
    scene: () => currentScene(),
    now: () => Date.now(),
    onWelcome: (msg) => {
      peerWelcomed = true;
      peerSelfId = msg.id || '';
      const input = document.getElementById('islandNickInput');
      if (input && !loadNick(window.localStorage) && document.activeElement !== input) {
        input.value = msg.name || '';
      }
      syncPeerCount();
    },
    onPlayers: (state) => {
      peerWelcomed = !!state.selfId;
      peerSelfId = state.selfId || '';
      applyPeerList(state.players);
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
    selfId: () => peerSelfId,
    now: () => Date.now()
  });

  const nickBtn = document.getElementById('islandNickBtn');
  const nickPop = document.getElementById('islandNickPop');
  const nickInput = document.getElementById('islandNickInput');
  const nickSave = document.getElementById('islandNickSave');
  if (nickInput) {
    const storedNick = loadNick(window.localStorage);
    if (storedNick) nickInput.value = storedNick;
  }
  function setNickOpen(open) {
    if (!nickPop || !nickBtn) return;
    nickPop.hidden = !open;
    nickBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open && nickInput) nickInput.focus();
  }
  function saveNick() {
    if (!nickInput || !islandPresence) return;
    const cleaned = cleanNick(nickInput.value);
    if (!cleaned) return;
    nickInput.value = cleaned;
    islandPresence.rename(cleaned);
    setNickOpen(false);
  }
  if (nickBtn && nickPop) {
    nickBtn.addEventListener('click', () => setNickOpen(nickPop.hidden));
  }
  if (nickSave) nickSave.addEventListener('click', saveNick);
  if (nickInput) {
    nickInput.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== 'Escape') return;
      event.preventDefault();
      event.stopPropagation();
      if (event.key === 'Enter') saveNick();
      else setNickOpen(false);
    });
  }

  window.PunchClockIsland = {
    focus: focus,
    reset: reset,
    setMode: setMode,
    setExplore: setExplore,
    isExploring: () => mode.exploring,
    openCasino: () => casino.openLobby(),
    closeCasino: () => casino.closeLobby(),
    isCasinoOpen: () => casino.isOpen(),
    openGraffiti: () => graffiti.open(),
    closeGraffiti: () => graffiti.close(),
    isGraffitiOpen: () => graffiti.isOpen(),
    openRoadmap: () => roadmap.open(),
    closeRoadmap: () => roadmap.close(),
    isRoadmapOpen: () => roadmap.isOpen(),
    casinoName: CASINO_NAME
  };

  resize();
  dayNight.refresh();
  syncExploreUi();
  const open = document.querySelector('.dash-panel.is-active');
  if (open && !open.hasAttribute('hidden')) {
    const id = open.id.replace('panel-', '');
    applyPose(POSES[id] || POSES.home, true);
    setActive(POSES[id] ? id : '');
  } else {
    applyPose(POSES.home, true);
  }
  renderer.render(scene, camera);
  placeLabels();
  if (!document.hidden) start();
}
