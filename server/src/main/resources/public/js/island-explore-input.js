import {
  MOVE_CODES,
  shouldCaptureMoveKey,
  shouldTriggerInteractKey,
  clampOrbitRadius,
  effectiveInteriorOrbitMin
} from './island-explore.js';
import {
  createCanvasPointerState,
  onPointerDownState,
  onPointerMoveState,
  onPointerUpState,
  onPointerCancelState,
  pinchOrbitRadiusStep
} from './island-pinch.js';
import { shouldOpenChatKey } from './island-chat.js';
import { shouldOpenMapWithKey } from './island-minimap.js';

/** 戶外互動分派；回傳 true 表示已處理，不應再開啟賭場。 */
export function applyOutdoorInteractZone(zone, actions) {
  if (!zone || !actions) return false;
  if (zone.action === 'openGraffiti') {
    actions.openGraffiti();
    return true;
  }
  if (zone.action === 'openRoadmap') {
    actions.openRoadmap();
    return true;
  }
  if (zone.action === 'enterInterior') {
    actions.enterInterior(zone.id);
    return true;
  }
  if (zone.action === 'sail') {
    actions.startSail(zone);
    return true;
  }
  if (zone.action === 'enterTcm') {
    if (typeof actions.enterTcm === 'function') actions.enterTcm();
    return true;
  }
  if (zone.action === 'npcTalk') {
    if (typeof actions.startNpcTalk === 'function') actions.startNpcTalk();
    return true;
  }
  if (typeof actions.isClosedHall === 'function' && actions.isClosedHall(zone.action)) {
    actions.showInteriorClosed();
    return true;
  }
  if (zone.action === 'enterCasino') {
    if (typeof actions.openCasino === 'function') actions.openCasino();
    return true;
  }
  return false;
}

/** 室內互動分派；回傳 true 表示已處理。 */
export function applyInteriorInteractZone(zone, actions) {
  if (!zone || !actions) return false;
  if (zone.action === 'exitInterior') {
    if (typeof actions.leaveInterior === 'function') actions.leaveInterior();
    return true;
  }
  if (zone.action === 'openCasinoBlackjack') {
    if (typeof actions.openCasinoBlackjack === 'function') actions.openCasinoBlackjack();
    return true;
  }
  if (zone.action === 'casinoSlots') {
    if (typeof actions.casinoSlots === 'function') actions.casinoSlots(zone);
    return true;
  }
  if (zone.action === 'casinoRoulette') {
    if (typeof actions.casinoRoulette === 'function') actions.casinoRoulette(zone);
    return true;
  }
  if (zone.action === 'enterTcmStudy') {
    if (typeof actions.enterTcmStudy === 'function') actions.enterTcmStudy();
    return true;
  }
  return false;
}

export function buildInteriorInteractActions(ctx) {
  return {
    leaveInterior: () => ctx.leaveInterior && ctx.leaveInterior(),
    openCasinoBlackjack: () => ctx.openCasinoBlackjack && ctx.openCasinoBlackjack(),
    casinoSlots: () => ctx.casinoArcadePoke && ctx.casinoArcadePoke('slots'),
    casinoRoulette: () => ctx.casinoArcadePoke && ctx.casinoArcadePoke('roulette'),
    enterTcmStudy: () => ctx.enterTcmStudy && ctx.enterTcmStudy()
  };
}

/** 戶外 E／Enter 互動用的 actions；與 applyOutdoorInteractZone 成對使用。 */
export function buildOutdoorInteractActions(ctx) {
  return {
    openGraffiti: () => ctx.openGraffiti(),
    openRoadmap: () => ctx.openRoadmap(),
    enterInterior: (id) => ctx.enterInterior(id),
    startSail: (z) => ctx.startSail && ctx.startSail(z),
    isClosedHall: (action) => ctx.isClosedHall(action),
    showInteriorClosed: () => ctx.showInteriorClosed(),
    openCasino: () => ctx.casino && ctx.casino.openLobby(),
    enterTcm: () => ctx.enterTcm && ctx.enterTcm(),
    startNpcTalk: () => {
      if (typeof ctx.startNpcTalk === 'function') return ctx.startNpcTalk();
      if (typeof ctx.onNpcTalkInteract === 'function') return ctx.onNpcTalkInteract();
      return false;
    }
  };
}

export function isTypingTarget(target) {
  if (!target) return false;
  const tag = target.tagName;
  if (tag === 'INPUT' || tag === 'TEXTAREA' || tag === 'SELECT') return true;
  if (target.isContentEditable) return true;
  return !!(target.closest && target.closest('input, textarea, select, [contenteditable="true"]'));
}

// 'explore'：焦點在畫布、探索按鈕、搖桿或頁面本身；'control'：其它可操作的 UI 元件。
export function focusKind(target) {
  if (!target || target === document.body || target === document.documentElement || target === window) return 'explore';
  if (!target.closest) return 'explore';
  if (target.closest('#islandStage, #islandExplore, #islandJoystick')) return 'explore';
  if (target.closest('button, a[href], summary, [tabindex], [role="button"], [role="listbox"], [role="menu"], [role="option"], [role="tab"]')) return 'control';
  return 'explore';
}

export function getOrbitClamp(ctx) {
  const mode = ctx.getMode();
  const followCam = ctx.controlsActive(mode);
  if (!followCam || !ctx.isInside()) {
    return { followCam: followCam, indoorMin: null };
  }
  const poseR = typeof ctx.getInteriorPoseRadius === 'function' ? ctx.getInteriorPoseRadius() : null;
  const indoorMin = effectiveInteriorOrbitMin(ctx.interiorMinRadius, poseR);
  return { followCam: followCam, indoorMin: indoorMin };
}

export function createExplorePointer(opts) {
  const canvas = opts.canvas;
  const camera = opts.camera;
  const cam = opts.cam;
  const THREE = opts.THREE;
  const getMode = opts.mode || opts.getMode;
  const controlsActive = opts.controlsActive;
  const applyDragPhi = opts.applyDragPhi;
  const getPickables = opts.getPickables
    || (opts.hitZonePickables ? function () { return opts.hitZonePickables; } : function () { return []; });
  const onZoneClick = opts.onZoneClick || function () {};
  const onAfterDragEnd = opts.onAfterDragEnd;
  const isInside = opts.isInside || function () { return false; };
  const interiorMinRadius = opts.interiorMinRadius != null ? opts.interiorMinRadius : null;
  const getInteriorPoseRadius = opts.getInteriorPoseRadius;

  const raycaster = new THREE.Raycaster();
  const pointer = new THREE.Vector2();
  const ptr = createCanvasPointerState();

  function orbitClamp() {
    return getOrbitClamp({
      getMode: getMode,
      controlsActive: controlsActive,
      isInside: isInside,
      interiorMinRadius: interiorMinRadius,
      getInteriorPoseRadius: getInteriorPoseRadius
    });
  }

  function applyPinchRadius(curDist) {
    if (ptr.pinchStartRadius == null || ptr.pinchStartDist == null) return;
    const clamp = orbitClamp();
    const step = pinchOrbitRadiusStep(
      cam.targetRadius,
      ptr.pinchStartRadius,
      ptr.pinchStartDist,
      curDist,
      (raw) => clampOrbitRadius(raw, 0, clamp.followCam, clamp.indoorMin)
    );
    cam.targetRadius = step.radius;
    if (step.resetBaseline) {
      ptr.pinchStartRadius = step.radius;
      ptr.pinchStartDist = curDist;
    }
  }

  function ndc(event) {
    const rect = canvas.getBoundingClientRect();
    pointer.x = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pointer.y = -((event.clientY - rect.top) / rect.height) * 2 + 1;
  }

  const resolvePick = typeof opts.resolvePick === 'function' ? opts.resolvePick : null;

  function hitZone(event) {
    ndc(event);
    raycaster.setFromCamera(pointer, camera);
    const pickables = getPickables();
    const hits = raycaster.intersectObjects(pickables, false);
    if (!hits.length) return '';
    if (resolvePick) return resolvePick(hits[0]);
    const ud = hits[0].object.userData;
    return ud.zone || '';
  }

  function onPointerDown(event) {
    const down = onPointerDownState(ptr, event.pointerId, event.clientX, event.clientY);
    if (down.ignored) return;
    if (down.beginPinch) {
      ptr.pinchStartRadius = cam.targetRadius;
    }
    if (ptr.pointers.size > 0) canvas.style.cursor = 'grabbing';
    if (canvas.setPointerCapture) canvas.setPointerCapture(event.pointerId);
  }

  function onPointerMove(event) {
    const active = ptr.pointers.size > 0;
    if (!active) {
      canvas.style.cursor = hitZone(event) ? 'pointer' : 'grab';
      return;
    }
    const move = onPointerMoveState(ptr, event.pointerId, event.clientX, event.clientY);
    if (move.mode === 'ignore') return;
    if (move.mode === 'pinch') {
      applyPinchRadius(move.curDist);
      return;
    }
    if (move.mode === 'drag') {
      cam.targetTheta -= move.dx * 0.005;
      const followCam = controlsActive(getMode());
      cam.targetPhi = applyDragPhi(cam.targetPhi, move.dy, followCam);
    }
  }

  function onPointerUp(event) {
    const up = onPointerUpState(ptr, event.pointerId);
    if (up.remaining === 0) canvas.style.cursor = 'grab';
    if (canvas.releasePointerCapture) {
      try { canvas.releasePointerCapture(event.pointerId); } catch (_e) { /* already released */ }
    }
    if (up.ended) {
      if (up.allowClick) {
        const zone = hitZone(event);
        if (zone) onZoneClick(zone, event);
      }
      if (onAfterDragEnd) onAfterDragEnd({ moved: up.dragMoved, event: event });
    }
  }

  function onPointerCancel(event) {
    if (!event) return;
    const cancel = onPointerCancelState(ptr, event.pointerId);
    if (cancel.remaining === 0) canvas.style.cursor = 'grab';
    if (canvas.releasePointerCapture) {
      try { canvas.releasePointerCapture(event.pointerId); } catch (_e) { /* already released */ }
    }
    if (cancel.ended && onAfterDragEnd) {
      onAfterDragEnd({ moved: cancel.dragMoved, event: null });
    }
  }

  function onLostPointerCapture(event) {
    onPointerCancel(event);
  }

  function onPointerLeave() {
    if (ptr.pointers.size === 0) canvas.style.cursor = 'grab';
  }

  function onWheel(event) {
    event.preventDefault();
    const clamp = orbitClamp();
    cam.targetRadius = clampOrbitRadius(
      cam.targetRadius,
      event.deltaY * 0.01,
      clamp.followCam,
      clamp.indoorMin
    );
  }

  canvas.addEventListener('pointerdown', onPointerDown);
  canvas.addEventListener('pointermove', onPointerMove);
  canvas.addEventListener('pointerup', onPointerUp);
  canvas.addEventListener('pointercancel', onPointerCancel);
  canvas.addEventListener('lostpointercapture', onLostPointerCapture);
  canvas.addEventListener('pointerleave', onPointerLeave);
  canvas.addEventListener('wheel', onWheel, { passive: false });

  return function destroy() {
    canvas.removeEventListener('pointerdown', onPointerDown);
    canvas.removeEventListener('pointermove', onPointerMove);
    canvas.removeEventListener('pointerup', onPointerUp);
    canvas.removeEventListener('pointercancel', onPointerCancel);
    canvas.removeEventListener('lostpointercapture', onLostPointerCapture);
    canvas.removeEventListener('pointerleave', onPointerLeave);
    canvas.removeEventListener('wheel', onWheel);
  };
}

/**
 * 鍵盤探索輸入：移動鍵 → 互動 → 開聊天 → Escape 逐層關閉。
 * 暱稱彈窗 Enter／Esc 由 #islandNickInput 自己的 listener 處理（stopPropagation）。
 */
export function handleExploreKeydown(ctx) {
  const event = ctx.event;
  const mode = ctx.getMode();
  const player = ctx.player;

  if (MOVE_CODES[event.code]) {
    if (event.altKey || event.ctrlKey || event.metaKey) return false;
    const capture = shouldCaptureMoveKey({
      code: event.code,
      typing: isTypingTarget(event.target),
      active: ctx.controlsActive(mode),
      uiOpen: ctx.islandUiOpen(),
      focus: focusKind(event.target)
    });
    if (!capture) return false;
    player.keys[event.code] = true;
    event.preventDefault();
    return true;
  }

  const promptVisible = ctx.syncInteractPrompt();
  if (ctx.casino && shouldTriggerInteractKey({
    promptVisible: promptVisible,
    typing: isTypingTarget(event.target),
    focus: focusKind(event.target),
    code: event.code,
    key: event.key
  })) {
    // 焦點已在「進入賭場」按鈕時交給按鈕自己的 click／Enter，避免重複開啟。
    if (event.target && (event.target.id === 'islandInteract' || event.target.id === 'islandSteer')) return false;
    event.preventDefault();
    if (typeof ctx.isSteering === 'function' && ctx.isSteering()) {
      if (typeof ctx.disembarkSteer === 'function') ctx.disembarkSteer();
      return true;
    }
    const zone = ctx.hitInteractZone();
    if (ctx.isInside()) {
      if (zone) {
        const handled = applyInteriorInteractZone(zone, buildInteriorInteractActions(ctx));
        if (handled) return true;
      }
      return true;
    }
    if (zone && zone.action === 'npcTalk') {
      const handledTalk = applyOutdoorInteractZone(zone, buildOutdoorInteractActions(ctx));
      if (handledTalk) return true;
    }
    if (!zone && typeof ctx.onNpcTalkInteract === 'function' && ctx.onNpcTalkInteract()) {
      event.preventDefault();
      return true;
    }
    const handled = applyOutdoorInteractZone(zone, buildOutdoorInteractActions(ctx));
    if (!handled && ctx.casino) ctx.casino.openLobby();
    return true;
  }

  if (ctx.npcTalk && ctx.npcTalk.isOpen && ctx.npcTalk.isOpen()) {
    const key = event.key || '';
    const code = event.code || '';
    if (key === ' ' || code === 'Space' || key === 'Enter' || code === 'Enter') {
      if (!isTypingTarget(event.target) && focusKind(event.target) !== 'control') {
        event.preventDefault();
        ctx.npcTalk.advance();
        return true;
      }
    }
  }

  const doc = ctx.document || (typeof document !== 'undefined' ? document : null);
  const nickPopEl = doc && doc.getElementById('islandNickPop');
  const moreSheetEl = doc && doc.getElementById('islandMoreSheet');
  const mapSheetEl = doc && doc.getElementById('islandMapSheet');
  const avatarPopEl = doc && doc.getElementById('islandAvatarPop');
  if (shouldOpenMapWithKey({
    exploring: mode.exploring,
    typing: isTypingTarget(event.target),
    altKey: event.altKey,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey,
    key: event.key,
    mapOpen: !!(mapSheetEl && !mapSheetEl.hidden),
    moreOpen: !!(moreSheetEl && !moreSheetEl.hidden),
    nickOpen: !!(nickPopEl && !nickPopEl.hidden),
    chatOpen: !!(ctx.islandChat && ctx.islandChat.isOpen()),
    avatarOpen: !!(avatarPopEl && !avatarPopEl.hidden),
    sheetOpen: !!(doc && doc.getElementById('islandSheet') && !doc.getElementById('islandSheet').hidden),
    roadmapOpen: !!(ctx.roadmap && ctx.roadmap.isOpen()),
    casinoOpen: !!(ctx.casino && ctx.casino.isOpen()),
    graffitiOpen: !!(ctx.graffiti && ctx.graffiti.isOpen())
  })) {
    event.preventDefault();
    if (typeof ctx.openMap === 'function') ctx.openMap();
    return true;
  }
  if (shouldOpenChatKey({
    exploring: mode.exploring,
    uiOpen: ctx.islandUiOpen(),
    typing: isTypingTarget(event.target),
    nickOpen: !!(nickPopEl && !nickPopEl.hidden),
    chatOpen: !!(ctx.islandChat && ctx.islandChat.isOpen()),
    promptVisible: promptVisible,
    key: event.key,
    code: event.code,
    altKey: event.altKey,
    ctrlKey: event.ctrlKey,
    metaKey: event.metaKey
  })) {
    event.preventDefault();
    if (ctx.islandChat) ctx.islandChat.open();
    return true;
  }

  if (event.key !== 'Escape') return false;
  if (ctx.npcTalk && ctx.npcTalk.isOpen && ctx.npcTalk.isOpen()) {
    ctx.npcTalk.close();
    return true;
  }
  if (ctx.islandChat && ctx.islandChat.isOpen()) {
    ctx.islandChat.close();
    return true;
  }
  if (ctx.roadmap && ctx.roadmap.isOpen()) {
    ctx.roadmap.close();
    return true;
  }
  if (ctx.graffiti && ctx.graffiti.isOpen()) {
    ctx.graffiti.close();
    return true;
  }
  if (isTypingTarget(event.target)) return false;
  if (ctx.casino && ctx.casino.isTableOpen && ctx.casino.isTableOpen()) {
    ctx.casino.closeTable();
    return true;
  }
  if (ctx.casino && ctx.casino.isOpen()) {
    ctx.casino.closeLobby();
    return true;
  }
  const sheet = doc && doc.getElementById('islandSheet');
  if (sheet && !sheet.hidden && typeof ctx.switchDashTab === 'function') {
    ctx.switchDashTab('');
    return true;
  }
  if (doc) {
    const moreSheet = doc.getElementById('islandMoreSheet');
    if (moreSheet && !moreSheet.hidden) {
      event.preventDefault();
      if (typeof ctx.closeMore === 'function') ctx.closeMore();
      else moreSheet.hidden = true;
      return true;
    }
    const mapSheet = doc.getElementById('islandMapSheet');
    if (mapSheet && !mapSheet.hidden) {
      event.preventDefault();
      if (typeof ctx.closeMap === 'function') ctx.closeMap();
      else mapSheet.hidden = true;
      return true;
    }
    const listEl = doc.getElementById('islandList');
    if (listEl && !listEl.hidden) {
      event.preventDefault();
      if (typeof ctx.closeList === 'function') ctx.closeList();
      else {
        listEl.hidden = true;
        const listToggle = doc.getElementById('islandListBtn');
        if (listToggle) listToggle.setAttribute('aria-expanded', 'false');
      }
      return true;
    }
    const nickPopElOpen = doc.getElementById('islandNickPop');
    if (nickPopElOpen && !nickPopElOpen.hidden) {
      event.preventDefault();
      if (typeof ctx.setNickOpen === 'function') ctx.setNickOpen(false);
      else nickPopElOpen.hidden = true;
      return true;
    }
    const avatarPopEl = doc.getElementById('islandAvatarPop');
    if (avatarPopEl && !avatarPopEl.hidden) {
      event.preventDefault();
      if (typeof ctx.setAvatarOpen === 'function') ctx.setAvatarOpen(false);
      else avatarPopEl.hidden = true;
      return true;
    }
  }
  if (ctx.isInside()) {
    ctx.leaveInterior();
    return true;
  }
  if (mode.exploring) ctx.setExplore(false, false);
  return true;
}

export function createExploreKeys(opts) {
  const win = opts.window || (typeof window !== 'undefined' ? window : null);
  const doc = opts.document || (typeof document !== 'undefined' ? document : null);
  const player = opts.player;
  const clearMoveInput = opts.clearMoveInput;

  function onKeydown(event) {
    handleExploreKeydown(Object.assign({}, opts, { event: event }));
  }

  function onKeyup(event) {
    if (!MOVE_CODES[event.code]) return;
    player.keys[event.code] = false;
  }

  function onFocusIn(event) {
    if (isTypingTarget(event.target) || focusKind(event.target) !== 'explore') clearMoveInput();
  }

  if (win) win.addEventListener('keydown', onKeydown);
  if (win) win.addEventListener('keyup', onKeyup);
  if (doc) doc.addEventListener('focusin', onFocusIn);

  return function destroy() {
    if (win) win.removeEventListener('keydown', onKeydown);
    if (win) win.removeEventListener('keyup', onKeyup);
    if (doc) doc.removeEventListener('focusin', onFocusIn);
  };
}

export function createExploreJoystick(opts) {
  const stickRoot = opts.stickRoot;
  const stickKnob = opts.stickKnob;
  const player = opts.player;
  const canMove = opts.canMove || function () { return true; };
  if (!stickRoot) return function destroy() {};

  let stickPointerId = null;

  function setStickFromEvent(event) {
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

  function onStickDown(event) {
    if (!canMove()) return;
    event.preventDefault();
    stickPointerId = event.pointerId;
    if (stickRoot.setPointerCapture) stickRoot.setPointerCapture(event.pointerId);
    setStickFromEvent(event);
  }

  function onStickMove(event) {
    if (stickPointerId !== event.pointerId) return;
    event.preventDefault();
    setStickFromEvent(event);
  }

  stickRoot.addEventListener('pointerdown', onStickDown);
  stickRoot.addEventListener('pointermove', onStickMove);
  stickRoot.addEventListener('pointerup', endStick);
  stickRoot.addEventListener('pointercancel', endStick);
  stickRoot.addEventListener('lostpointercapture', endStick);

  return function destroy() {
    stickRoot.removeEventListener('pointerdown', onStickDown);
    stickRoot.removeEventListener('pointermove', onStickMove);
    stickRoot.removeEventListener('pointerup', endStick);
    stickRoot.removeEventListener('pointercancel', endStick);
    stickRoot.removeEventListener('lostpointercapture', endStick);
  };
}
