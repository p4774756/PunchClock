// node --test server/src/test/js/island-explore-input.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const {
  isTypingTarget,
  focusKind,
  getOrbitClamp,
  applyOutdoorInteractZone,
  buildOutdoorInteractActions,
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
    interiorMinRadius: 6.5
  };
  assert.deepEqual(getOrbitClamp(ctx), { followCam: true, indoorMin: 6.5 });
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

test('畫布拖曳只跟第一根指標：第二指 move 不改 phi／theta', () => {
  const cam = {
    targetTheta: 0.35,
    targetPhi: 0.98,
    targetRadius: 9.2
  };
  const listeners = {};
  const canvas = {
    style: { cursor: 'grab' },
    addEventListener(type, fn) { listeners[type] = fn; },
    removeEventListener() {},
    getBoundingClientRect: () => ({ left: 0, top: 0, width: 390, height: 844 }),
    setPointerCapture() {}
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
  assert.ok(typeof down === 'function' && typeof move === 'function');
  down({ pointerId: 1, clientX: 100, clientY: 200 });
  const theta0 = cam.targetTheta;
  const phi0 = cam.targetPhi;
  move({ pointerId: 1, clientX: 100, clientY: 180 });
  assert.notEqual(cam.targetPhi, phi0, '第一指應改變 phi');
  const phiAfterFirst = cam.targetPhi;
  const thetaAfterFirst = cam.targetTheta;
  down({ pointerId: 2, clientX: 50, clientY: 50 });
  move({ pointerId: 2, clientX: 50, clientY: 200 });
  assert.equal(cam.targetPhi, phiAfterFirst);
  assert.equal(cam.targetTheta, thetaAfterFirst);
  move({ pointerId: 1, clientX: 100, clientY: 160 });
  assert.notEqual(cam.targetPhi, phiAfterFirst, '第一指恢復後仍有效');
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