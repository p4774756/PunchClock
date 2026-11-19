// node --test server/src/test/js/island-explore-input.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const {
  isTypingTarget,
  focusKind,
  getOrbitClamp,
  applyOutdoorInteractZone,
  applyInteriorInteractZone,
  buildOutdoorInteractActions,
  buildInteriorInteractActions,
  handleExploreKeydown,
  createExplorePointer
} = await loadPublicModule('island-explore-input.js');

const { applyDragPhi } = await loadPublicModule('island-camera.js');

test('isTypingTarget：input／textarea／contenteditable', () => {
  assert.equal(isTypingTarget(null), false);
  assert.equal(isTypingTarget({ tagName: 'DIV' }), false);
  assert.equal(isTypingTarget({ tagName: 'INPUT' }), true);
  assert.equal(isTypingTarget({ tagName: 'TEXTAREA' }), true);
  assert.equal(isTypingTarget({ tagName: 'SELECT' }), true);
  assert.equal(isTypingTarget({ tagName: 'DIV', isContentEditable: true }), true);
  assert.equal(
    isTypingTarget({
      tagName: 'DIV',
      closest: (sel) => (sel.indexOf('input') >= 0 ? { tagName: 'INPUT' } : null)
    }),
    true
  );
});

test('focusKind：畫布區為 explore，按鈕為 control', () => {
  const body = { tagName: 'BODY' };
  const docEl = { tagName: 'HTML' };
  globalThis.document = { body: body, documentElement: docEl };
  globalThis.window = globalThis;
  assert.equal(focusKind(null), 'explore');
  assert.equal(focusKind(body), 'explore');
  assert.equal(focusKind(docEl), 'explore');
  assert.equal(
    focusKind({
      tagName: 'DIV',
      closest: (sel) => (sel.indexOf('#islandStage') >= 0 ? { id: 'islandStage' } : null)
    }),
    'explore'
  );
  assert.equal(
    focusKind({
      tagName: 'BUTTON',
      closest: (sel) => (sel.indexOf('button') >= 0 ? {} : null)
    }),
    'control'
  );
  assert.equal(
    focusKind({
      tagName: 'SPAN',
      closest: (sel) => {
        if (sel.indexOf('#islandStage') >= 0) return null;
        if (sel.indexOf('button') >= 0) return {};
        return null;
      }
    }),
    'control'
  );
});

test('getOrbitClamp：室內跟隨鏡頭時帶 indoorMin', () => {
  const ctx = {
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    isInside: () => true,
    interiorMinRadius: 5.2
  };
  assert.deepEqual(getOrbitClamp(ctx), { followCam: true, indoorMin: 5.2 });
});

test('getOrbitClamp：室內下限不高於房間 pose 半徑', () => {
  const ctx = {
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    isInside: () => true,
    interiorMinRadius: 5.2,
    getInteriorPoseRadius: () => 3.7
  };
  assert.deepEqual(getOrbitClamp(ctx), { followCam: true, indoorMin: 3.7 });
});

test('getOrbitClamp：室外或非跟隨時 indoorMin 為 null', () => {
  const outdoor = {
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    isInside: () => false,
    interiorMinRadius: 6.5
  };
  assert.deepEqual(getOrbitClamp(outdoor), { followCam: true, indoorMin: null });

  const panel = {
    getMode: () => ({ exploring: false }),
    controlsActive: () => false,
    isInside: () => true,
    interiorMinRadius: 6.5
  };
  assert.deepEqual(getOrbitClamp(panel), { followCam: false, indoorMin: null });
});

test('buildOutdoorInteractActions：推拿館 enterTcm 會呼叫 ctx.enterTcm', () => {
  let entered = false;
  let casinoOpened = false;
  let interiorClosed = false;
  const actions = buildOutdoorInteractActions({
    openGraffiti: () => {},
    openRoadmap: () => {},
    enterInterior: () => {},
    startSail: () => {},
    isClosedHall: () => false,
    showInteriorClosed: () => { interiorClosed = true; },
    casino: { openLobby: () => { casinoOpened = true; } },
    enterTcm: () => { entered = true; }
  });
  const handled = applyOutdoorInteractZone({ action: 'enterTcm' }, actions);
  assert.equal(handled, true);
  assert.equal(entered, true);
  assert.equal(casinoOpened, false);
  assert.equal(interiorClosed, false);
});

test('鍵盤 E：推拿館互動區會呼叫 enterTcm 且不開賭場', () => {
  const body = { tagName: 'BODY' };
  globalThis.document = { body: body, documentElement: { tagName: 'HTML' } };
  globalThis.window = globalThis;

  let entered = false;
  let casinoOpened = false;
  let interiorClosed = false;
  const ctx = {
    event: {
      code: 'KeyE',
      key: 'e',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => true,
    casino: {
      openLobby: () => { casinoOpened = true; },
      isOpen: () => false,
      closeLobby: () => {}
    },
    hitInteractZone: () => ({ action: 'enterTcm' }),
    isInside: () => false,
    enterTcm: () => { entered = true; },
    openGraffiti: () => {},
    openRoadmap: () => {},
    enterInterior: () => {},
    isClosedHall: () => false,
    showInteriorClosed: () => { interiorClosed = true; },
    startSail: () => {},
    graffiti: null,
    roadmap: null,
    islandChat: null
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(entered, true);
  assert.equal(casinoOpened, false);
  assert.equal(interiorClosed, false);
});

test('畫布兩指捏合：phi／theta 不變、半徑依距離變化', () => {
  const cam = {
    targetTheta: 0.35,
    targetPhi: 0.98,
    targetRadius: 10
  };
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {},
    releasePointerCapture() {}
  };
  const camera = {};
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  createExplorePointer({
    canvas: canvas,
    camera: camera,
    cam: cam,
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi
  });
  const down = listeners.pointerdown;
  const move = listeners.pointermove;
  const up = listeners.pointerup;
  down({ pointerId: 1, clientX: 100, clientY: 300 });
  move({ pointerId: 1, clientX: 100, clientY: 280 });
  const phiDrag = cam.targetPhi;
  const thetaDrag = cam.targetTheta;
  down({ pointerId: 2, clientX: 200, clientY: 300 });
  move({ pointerId: 2, clientX: 300, clientY: 300 });
  assert.equal(cam.targetPhi, phiDrag);
  assert.equal(cam.targetTheta, thetaDrag);
  assert.ok(cam.targetRadius < 10, '張開應拉近');
  down({ pointerId: 1, clientX: 100, clientY: 300 });
  down({ pointerId: 2, clientX: 200, clientY: 300 });
  up({ pointerId: 2, clientX: 200, clientY: 300 });
  const thetaBefore = cam.targetTheta;
  move({ pointerId: 1, clientX: 130, clientY: 300 });
  assert.notEqual(cam.targetTheta, thetaBefore, '2→1 後單指水平拖曳仍有效');
});

test('createExplorePointer：室內手機 3.7 張開捏合不反向跳', () => {
  const cam = { targetTheta: 0, targetPhi: 1.1, targetRadius: 3.7 };
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {},
    releasePointerCapture() {}
  };
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  createExplorePointer({
    canvas: canvas,
    camera: {},
    cam: cam,
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi,
    isInside: () => true,
    interiorMinRadius: 5.2,
    getInteriorPoseRadius: () => 3.7
  });
  const down = listeners.pointerdown;
  const move = listeners.pointermove;
  down({ pointerId: 1, clientX: 80, clientY: 400 });
  down({ pointerId: 2, clientX: 200, clientY: 400 });
  const before = cam.targetRadius;
  move({ pointerId: 2, clientX: 280, clientY: 400 });
  assert.ok(cam.targetRadius <= before, '張開不可讓半徑變大');
  assert.notEqual(cam.targetRadius, 5.2, '不可跳到 INTERIOR_POSE 下限');
});

test('createExplorePointer：pointerup 後 lostpointercapture 只觸發一次 onAfterDragEnd', () => {
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {},
    releasePointerCapture() {}
  };
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  let dragEnds = 0;
  createExplorePointer({
    canvas: canvas,
    camera: {},
    cam: { targetTheta: 0, targetPhi: 0, targetRadius: 8 },
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi,
    onAfterDragEnd: () => { dragEnds += 1; }
  });
  listeners.pointerdown({ pointerId: 5, clientX: 10, clientY: 10 });
  listeners.pointermove({ pointerId: 5, clientX: 20, clientY: 10 });
  listeners.pointerup({ pointerId: 5, clientX: 20, clientY: 10 });
  listeners.lostpointercapture({ pointerId: 5, clientX: 20, clientY: 10 });
  assert.equal(dragEnds, 1);
});

test('createExplorePointer：兩指捏合放開不觸發 onZoneClick', () => {
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {},
    releasePointerCapture() {}
  };
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [{ object: { userData: { zone: 'devices' } } }];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  let zoneHit = null;
  createExplorePointer({
    canvas: canvas,
    camera: {},
    cam: { targetTheta: 0, targetPhi: 0, targetRadius: 8 },
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi,
    onZoneClick: (z) => { zoneHit = z; }
  });
  listeners.pointerdown({ pointerId: 1, clientX: 0, clientY: 0 });
  listeners.pointerdown({ pointerId: 2, clientX: 120, clientY: 0 });
  listeners.pointerup({ pointerId: 1, clientX: 0, clientY: 0 });
  listeners.pointerup({ pointerId: 2, clientX: 120, clientY: 0 });
  assert.equal(zoneHit, null);
});

test('createExplorePointer：單指輕點仍觸發 onZoneClick', () => {
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {},
    releasePointerCapture() {}
  };
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [{ object: { userData: { zone: 'devices' } } }];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  let zoneHit = null;
  createExplorePointer({
    canvas: canvas,
    camera: {},
    cam: { targetTheta: 0, targetPhi: 0, targetRadius: 8 },
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi,
    onZoneClick: (z) => { zoneHit = z; }
  });
  listeners.pointerdown({ pointerId: 1, clientX: 50, clientY: 50 });
  listeners.pointerup({ pointerId: 1, clientX: 50, clientY: 50 });
  assert.equal(zoneHit, 'devices');
});

test('鍵盤 E：掌舵中可靠岸時呼叫 disembarkSteer、不開賭場', () => {
  const body = { tagName: 'BODY' };
  globalThis.document = { body: body, documentElement: { tagName: 'HTML' } };
  globalThis.window = globalThis;

  let disembarked = false;
  let casinoOpened = false;
  let outdoorHandled = false;
  const ctx = {
    event: {
      code: 'KeyE',
      key: 'e',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => true,
    casino: {
      openLobby: () => { casinoOpened = true; },
      isOpen: () => false,
      closeLobby: () => {}
    },
    isSteering: () => true,
    disembarkSteer: () => { disembarked = true; },
    hitInteractZone: () => ({ action: 'sail', stop: 'main' }),
    isInside: () => false,
    enterTcm: () => {},
    openGraffiti: () => {},
    openRoadmap: () => {},
    enterInterior: () => {},
    isClosedHall: () => false,
    showInteriorClosed: () => {},
    startSail: () => { outdoorHandled = true; },
    graffiti: null,
    roadmap: null,
    islandChat: null
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(disembarked, true);
  assert.equal(casinoOpened, false);
  assert.equal(outdoorHandled, false);
});

test('鍵盤 Enter：掌舵中不可靠岸時不開賭場、不觸發戶外 sail', () => {
  const body = { tagName: 'BODY' };
  globalThis.document = { body: body, documentElement: { tagName: 'HTML' } };
  globalThis.window = globalThis;

  let disembarked = false;
  let casinoOpened = false;
  let sailed = false;
  const ctx = {
    event: {
      code: 'Enter',
      key: 'Enter',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => true,
    casino: {
      openLobby: () => { casinoOpened = true; },
      isOpen: () => false,
      closeLobby: () => {}
    },
    isSteering: () => true,
    disembarkSteer: () => { disembarked = true; },
    hitInteractZone: () => null,
    isInside: () => false,
    enterTcm: () => {},
    openGraffiti: () => {},
    openRoadmap: () => {},
    enterInterior: () => {},
    isClosedHall: () => false,
    showInteriorClosed: () => {},
    startSail: () => { sailed = true; },
    graffiti: null,
    roadmap: null,
    islandChat: null
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(disembarked, true);
  assert.equal(casinoOpened, false);
  assert.equal(sailed, false);
});

test('鍵盤 E：焦點在 islandInteract 時交給按鈕、不重複 disembark', () => {
  const btn = { tagName: 'BUTTON', id: 'islandInteract' };
  globalThis.document = { body: btn, documentElement: { tagName: 'HTML' } };
  globalThis.window = globalThis;

  let disembarked = false;
  const ctx = {
    event: {
      code: 'KeyE',
      key: 'e',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: btn,
      preventDefault() {}
    },
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => true,
    casino: { openLobby: () => {}, isOpen: () => false, closeLobby: () => {} },
    isSteering: () => true,
    disembarkSteer: () => { disembarked = true; },
    hitInteractZone: () => null,
    isInside: () => false,
    graffiti: null,
    roadmap: null,
    islandChat: null
  };
  assert.equal(handleExploreKeydown(ctx), false);
  assert.equal(disembarked, false);
});

test('室內 applyInteriorInteractZone：21 點與離開', () => {
  let bj = false;
  let left = false;
  const actions = buildInteriorInteractActions({
    openCasinoBlackjack: () => { bj = true; },
    leaveInterior: () => { left = true; },
    casinoArcadePoke: () => {}
  });
  assert.equal(applyInteriorInteractZone({ action: 'openCasinoBlackjack' }, actions), true);
  assert.equal(bj, true);
  assert.equal(applyInteriorInteractZone({ action: 'exitInterior' }, actions), true);
  assert.equal(left, true);
});

test('鍵盤 E：室內玩 21 點不觸發 leaveInterior', () => {
  const body = { tagName: 'BODY' };
  globalThis.document = { body: body, documentElement: { tagName: 'HTML' } };
  globalThis.window = globalThis;

  let bj = false;
  let left = false;
  const ctx = {
    event: {
      code: 'KeyE',
      key: 'e',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => true,
    casino: {
      openLobby: () => {},
      isOpen: () => false,
      closeLobby: () => {},
      isTableOpen: () => false,
      closeTable: () => {}
    },
    hitInteractZone: () => ({ action: 'openCasinoBlackjack' }),
    isInside: () => true,
    openCasinoBlackjack: () => { bj = true; },
    leaveInterior: () => { left = true; },
    casinoArcadePoke: () => {},
    graffiti: null,
    roadmap: null,
    islandChat: null
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(bj, true);
  assert.equal(left, false);
});

test('createExplorePointer：resolvePick 優先於 userData.zone', () => {
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 800, height: 600 }),
    setPointerCapture() {}
  };
  const THREE = {
    Raycaster: function () {
      this.setFromCamera = () => {};
      this.intersectObjects = () => [{ object: { userData: { zone: 'legacy' } }, instanceId: 0 }];
    },
    Vector2: function () { this.x = 0; this.y = 0; }
  };
  let picked = null;
  createExplorePointer({
    canvas: canvas,
    camera: {},
    cam: { targetTheta: 0, targetPhi: 0, targetRadius: 8 },
    THREE: THREE,
    getMode: () => ({ exploring: true }),
    controlsActive: () => true,
    applyDragPhi: applyDragPhi,
    resolvePick: (hit) => {
      picked = hit;
      return { type: 'tcm-point', instanceKey: 'LI4:1' };
    },
    onZoneClick: (zone) => {
      assert.deepEqual(zone, { type: 'tcm-point', instanceKey: 'LI4:1' });
    }
  });
  const down = listeners.pointerdown;
  const up = listeners.pointerup;
  down({ pointerId: 1, clientX: 40, clientY: 40 });
  up({ pointerId: 1, clientX: 40, clientY: 40 });
  assert.ok(picked);
});

test('sail 互動不會落到開賭場', () => {
  let casinoOpened = false;
  let sailed = false;
  const handled = applyOutdoorInteractZone(
    { action: 'sail', stop: 'main' },
    {
      startSail: () => { sailed = true; },
      openCasino: () => { casinoOpened = true; }
    }
  );
  assert.equal(handled, true);
  assert.equal(sailed, true);
  assert.equal(casinoOpened, false);
});
test('探索中 Esc 關選單：只關視窗、不呼叫 setExplore', () => {
  const body = { tagName: 'BODY' };
  const moreSheet = { id: 'islandMoreSheet', hidden: false };
  globalThis.document = {
    body: body,
    documentElement: { tagName: 'HTML' },
    getElementById: (id) => (id === 'islandMoreSheet' ? moreSheet : null)
  };
  globalThis.window = globalThis;
  let exploreOff = false;
  let closedMore = false;
  const ctx = {
    event: {
      key: 'Escape',
      code: 'Escape',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    document: globalThis.document,
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => false,
    casino: { isOpen: () => false, isTableOpen: () => false, closeLobby: () => {}, closeTable: () => {} },
    isInside: () => false,
    graffiti: null,
    roadmap: null,
    islandChat: { isOpen: () => false, close: () => {} },
    closeMore: () => {
      closedMore = true;
      moreSheet.hidden = true;
    },
    setExplore: () => {
      exploreOff = true;
    }
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(closedMore, true);
  assert.equal(exploreOff, false);
});

test('室內 applyInteriorInteractZone：進入經絡研習室', () => {
  let went = false;
  const actions = {
    enterTcmStudy: () => { went = true; }
  };
  const handled = applyInteriorInteractZone({ action: 'enterTcmStudy' }, actions);
  assert.equal(handled, true);
  assert.equal(went, true);
});
