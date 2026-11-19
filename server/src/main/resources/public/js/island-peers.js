// 遠端看板同儕、名牌與位置同步；由 island.js 注入場景／玩家／在線服務。

import { islandAvatarAnim, normalizeIslandAvatar } from './island-milu.js';

export function createPeerLayer(opts) {
  const THREE = opts.THREE;
  const scene = opts.scene;
  const camera = opts.camera;
  const projected = opts.projected;
  const P = opts.P;
  const reduced = !!opts.reduced;
  const createMiluView = opts.createMiluView;
  const createMiluState = opts.createMiluState;
  const miluStep = opts.miluStep;
  const miluClipPath = opts.miluClipPath;
  const screenAxes = opts.screenAxes;
  const createMotion = opts.createMotion;
  const pushTarget = opts.pushTarget;
  const sampleMotion = opts.sampleMotion;
  const shouldSendPos = opts.shouldSendPos;
  const getMode = opts.getMode;
  const getPlayer = opts.getPlayer;
  const getMiluVisible = opts.getMiluVisible;
  const getMiluPlaceholderVisible = opts.getMiluPlaceholderVisible;
  const getPlayerMeshVisible = opts.getPlayerMeshVisible;
  const currentScene = opts.currentScene;
  const presencePose = opts.presencePose;
  const peerOffset = opts.peerOffset;
  const getStageSize = opts.getStageSize;
  const getChat = opts.getChat;
  const getPresence = opts.getPresence;
  const document = opts.document;
  const getCam = opts.getCam || function () { return { theta: 0 }; };
  const syncPeerCountExternal = opts.syncPeerCount;
  const placeSelfLabel = opts.placeSelfLabel;

  const peers = new Map();
  const avatarTexturePools = new Map();
  let peerWelcomed = false;
  let peerSelfId = '';
  let peerScene = 'outdoor';
  let lastPosSent = null;

  function sharedTexturesForAvatar(avatarId) {
    const key = normalizeIslandAvatar(avatarId);
    let pool = avatarTexturePools.get(key);
    if (!pool) {
      pool = new Map();
      avatarTexturePools.set(key, pool);
    }
    return pool;
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
    const avatar = normalizeIslandAvatar(info && info.avatar);
    const anim = islandAvatarAnim(avatar);
    const view = createMiluView(THREE, { shared: sharedTexturesForAvatar(avatar), anim: anim });
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
      avatar: avatar,
      moving: !!info.moving,
      motion: createMotion(),
      miluState: createMiluState()
    };
  }

  function ensurePeerView(row, info) {
    const avatar = normalizeIslandAvatar(info && info.avatar);
    if (row.avatar === avatar) return row;
    scene.remove(row.view.object);
    if (row.view.dispose) row.view.dispose();
    const anim = islandAvatarAnim(avatar);
    const view = createMiluView(THREE, { shared: sharedTexturesForAvatar(avatar), anim: anim });
    tintRemote(view);
    view.object.visible = false;
    scene.add(view.object);
    row.view = view;
    row.avatar = avatar;
    row.miluState = createMiluState();
    return row;
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
      } else {
        ensurePeerView(row, info);
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
    if (syncPeerCountExternal) {
      syncPeerCountExternal();
      return;
    }
    const el = document.getElementById('islandPeerCount');
    if (!el) return;
    const mode = getMode();
    const show = !!(mode.exploring && peerWelcomed);
    el.hidden = !show;
    if (!show) return;
    el.textContent = '島上 ' + (peers.size + 1) + ' 人';
  }

  function stepPeers(dt) {
    const now = performance.now();
    const off = peerOffset();
    const cam = getCam();
    peers.forEach((row) => {
      const shown = sampleMotion(row.motion, now, reduced);
      const x = shown.x + off.x;
      const z = shown.z + off.z;
      row.view.object.position.set(x, P, z);
      const ready = row.view.ready();
      const failed = row.view.failed();
      row.view.object.visible = !!ready && !failed;
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
    const islandPresence = getPresence();
    const mode = getMode();
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
        avatar: pose.avatar,
        sentAt: now
      };
    }
  }

  function forceResendPos() {
    lastPosSent = null;
  }

  function placePeerLabels() {
    const size = getStageSize();
    const width = size.width || 1;
    const height = size.height || 1;
    const off = peerOffset();
    const now = performance.now();
    const peerPlaces = [];
    const mode = getMode();
    const player = getPlayer();
    const islandChat = getChat();
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
    let bubbleCtx = {
      x: player.x,
      y: P + 1.18,
      z: player.z,
      visible: !!(mode.exploring && (
        getMiluVisible()
        || (typeof getMiluPlaceholderVisible === 'function' && getMiluPlaceholderVisible())
        || getPlayerMeshVisible()
      )),
      width: width,
      height: height,
      camera: camera,
      projected: projected,
      peers: peerPlaces,
      selfNameAbove: false,
      selfBubbleExtra: ''
    };
    if (typeof placeSelfLabel === 'function') {
      const placed = placeSelfLabel({
        width: width,
        height: height,
        camera: camera,
        projected: projected,
        peers: peerPlaces
      });
      if (placed) {
        if (placed.headY != null) bubbleCtx.y = placed.headY;
        bubbleCtx.selfNameAbove = !!placed.selfNameAbove;
        if (placed.selfBubbleExtra) bubbleCtx.selfBubbleExtra = placed.selfBubbleExtra;
      }
    }
    if (islandChat) islandChat.placeBubbles(bubbleCtx);
  }

  function setWelcomed(value) {
    peerWelcomed = !!value;
  }

  function getWelcomed() {
    return peerWelcomed;
  }

  function setSelfId(id) {
    peerSelfId = id == null ? '' : String(id);
  }

  function getSelfId() {
    return peerSelfId;
  }

  function setPeerScene(sceneName) {
    peerScene = sceneName || 'outdoor';
  }

  function getPeerScene() {
    return peerScene;
  }

  function resetExploreState() {
    peerWelcomed = false;
    peerSelfId = '';
    peerScene = 'outdoor';
    lastPosSent = null;
    clearPeers();
  }

  function collectMinimapPeers(now) {
    const out = [];
    const off = peerOffset();
    const at = now == null ? performance.now() : now;
    peers.forEach((row) => {
      if (!row.view.object.visible) return;
      const shown = sampleMotion(row.motion, at, reduced);
      out.push(Object.freeze({
        x: shown.x + off.x,
        z: shown.z + off.z,
        yaw: shown.yaw || 0
      }));
    });
    return out;
  }

  return {
    peers: peers,
    applyPeerList: applyPeerList,
    clearPeers: clearPeers,
    stepPeers: stepPeers,
    syncPresence: syncPresence,
    forceResendPos: forceResendPos,
    placePeerLabels: placePeerLabels,
    syncPeerCount: syncPeerCount,
    setWelcomed: setWelcomed,
    getWelcomed: getWelcomed,
    setSelfId: setSelfId,
    getSelfId: getSelfId,
    setPeerScene: setPeerScene,
    getPeerScene: getPeerScene,
    resetExploreState: resetExploreState,
    collectMinimapPeers: collectMinimapPeers
  };
}