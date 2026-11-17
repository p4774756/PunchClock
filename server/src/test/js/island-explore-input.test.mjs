// node --test server/src/test/js/island-explore-input.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const { isTypingTarget, focusKind, getOrbitClamp } = await loadPublicModule('island-explore-input.js');

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