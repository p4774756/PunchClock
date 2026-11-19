// node --test server/src/test/js/island-pinch.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const pinch = await loadPublicModule('island-pinch.js');
const { clampOrbitRadius } = await loadPublicModule('island-explore.js');

const {
  PINCH_MIN_DIST_PX,
  pointerPairDistance,
  pinchRadiusFromRatio,
  createCanvasPointerState,
  onPointerDownState,
  onPointerMoveState,
  onPointerUpState,
  onPointerCancelState
} = pinch;

test('pinchRadiusFromRatio：張開半徑變小、合攏變大', () => {
  const start = 12;
  const d0 = 100;
  assert.ok(pinchRadiusFromRatio(start, d0, 200) < start);
  assert.ok(pinchRadiusFromRatio(start, d0, 50) > start);
  assert.equal(pinchRadiusFromRatio(start, d0, d0), start);
});

test('pinchRadiusFromRatio：距離低於下限不改變', () => {
  const start = 9;
  assert.equal(pinchRadiusFromRatio(start, 5, 80), start);
  assert.equal(pinchRadiusFromRatio(start, 80, 3), start);
  assert.equal(pinchRadiusFromRatio(start, PINCH_MIN_DIST_PX - 0.1, 120), start);
});

test('pinch + clampOrbitRadius：跟隨鏡頭與室內下限', () => {
  const interiorMin = 7;
  const rawWide = pinchRadiusFromRatio(10, 100, 50);
  const clamped = clampOrbitRadius(rawWide, 0, true, interiorMin);
  assert.ok(clamped >= interiorMin);
  assert.ok(clamped <= 16);
  const rawTight = pinchRadiusFromRatio(15, 120, 240);
  const clampedIn = clampOrbitRadius(rawTight, 0, true, interiorMin);
  assert.ok(clampedIn >= interiorMin);
});

test('狀態機：單指輕點可觸發 zone click', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 10, 20);
  const up = onPointerUpState(s, 1);
  assert.equal(up.allowClick, true);
  assert.equal(up.dragMoved, false);
});

test('狀態機：兩指捏合後放開不觸發 click、moved 為 true', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 0, 0);
  onPointerDownState(s, 2, 100, 0);
  onPointerUpState(s, 1);
  const up = onPointerUpState(s, 2);
  assert.equal(up.allowClick, false);
  assert.equal(up.dragMoved, true);
});

test('狀態機：2→1 指重設 last，續拖不跳', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 50, 50);
  onPointerDownState(s, 2, 150, 50);
  onPointerUpState(s, 2);
  assert.equal(s.pointers.size, 1);
  const move1 = onPointerMoveState(s, 1, 52, 52);
  assert.equal(move1.mode, 'drag');
  assert.equal(move1.dx, 2);
  assert.equal(move1.dy, 2);
  assert.equal(s.lastX, 52);
  assert.equal(s.lastY, 52);
  const move2 = onPointerMoveState(s, 1, 80, 80);
  assert.equal(move2.dx, 28);
  assert.equal(move2.dy, 28);
});

test('狀態機：捏合時只有 pinch 模式、不產生 drag 增量', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 0, 0);
  onPointerDownState(s, 2, 100, 0);
  const move = onPointerMoveState(s, 1, 50, 0);
  assert.equal(move.mode, 'pinch');
  assert.ok(move.curDist > 0);
});

test('狀態機：pointercancel 清除並可再單指', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 10, 10);
  onPointerDownState(s, 2, 110, 10);
  onPointerCancelState(s, 1);
  onPointerCancelState(s, 2);
  assert.equal(s.pointers.size, 0);
  onPointerDownState(s, 3, 20, 20);
  const up = onPointerUpState(s, 3);
  assert.equal(up.allowClick, true);
});

test('狀態機：第三指略過', () => {
  const s = createCanvasPointerState();
  onPointerDownState(s, 1, 0, 0);
  onPointerDownState(s, 2, 100, 0);
  const third = onPointerDownState(s, 3, 50, 50);
  assert.equal(third.ignored, true);
  assert.equal(s.pointers.size, 2);
});

test('pointerPairDistance', () => {
  assert.equal(pointerPairDistance({ x: 0, y: 0 }, { x: 3, y: 4 }), 5);
});