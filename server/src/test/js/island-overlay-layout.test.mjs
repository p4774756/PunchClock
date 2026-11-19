// node --test server/src/test/js/island-overlay-layout.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const O = await loadPublicModule('island-overlay-layout.js');
const L = await loadPublicModule('island-self-label.js');

const m = O.DEFAULT_OVERLAY_METRICS;

function allRects(layout, input) {
  const rects = [];
  if (input.selfNameVisible && input.selfLabelX != null) {
    rects.push(O.nameplateScreenRect(input.selfLabelX, input.selfLabelY, m));
  }
  if (layout.selfBubble) {
    rects.push(O.selfBubbleScreenRect(
      layout.selfBubble.x,
      layout.selfBubble.anchorY,
      layout.selfBubble.extraUpPx,
      m
    ));
  }
  (input.peers || []).forEach((p) => {
    if (p.visible === false) return;
    rects.push(O.peerNameplateScreenRect(p.x, p.y, m));
    if (p.bubble) {
      const up = layout.peerBubbleOffsetY[p.id] || 0;
      rects.push(O.peerBubbleScreenRect(p.x, p.y, up, m));
    }
  });
  return rects;
}

function peerDisplayY(selfX, selfY, peerX, peerY) {
  return peerY + L.peerNameScreenOffsetAway(selfX, selfY, peerX, peerY);
}

function assertNoOverlap(layout, input) {
  const rects = allRects(layout, input);
  for (let i = 0; i < rects.length; i++) {
    for (let j = i + 1; j < rects.length; j++) {
      const area = O.rectIntersectionArea(rects[i], rects[j]);
      assert.equal(area, 0, 'rect ' + i + ' vs ' + j + ' overlap=' + area);
    }
  }
}

test('自己泡泡以含 nudge 的名牌 Y 為基準，不與自己名牌相交', () => {
  const headY = 420;
  const nudge = L.SELF_PEER_SCREEN_NUDGE_PX;
  const labelY = headY - nudge;
  const input = {
    selfNameVisible: true,
    selfLabelX: 640,
    selfLabelY: labelY,
    selfBubbleActive: true,
    selfHeadX: 640,
    selfHeadY: headY,
    peers: []
  };
  const layout = O.layoutIslandOverlays(input, m);
  assert.ok(layout.selfBubble);
  const nameRect = O.nameplateScreenRect(640, labelY, m);
  const bubbleRect = O.selfBubbleScreenRect(
    layout.selfBubble.x,
    layout.selfBubble.anchorY,
    layout.selfBubble.extraUpPx,
    m
  );
  assert.equal(O.rectIntersectionArea(nameRect, bubbleRect), 0);
  assert.ok(bubbleRect.bottom <= nameRect.top - m.minGapPx + 0.5);
});

test('0.3m／0.9m／同點：雙方發話時四矩形兩兩不相交', () => {
  const base = {
    selfNameVisible: true,
    selfLabelX: 500,
    selfBubbleActive: true,
    selfHeadX: 500,
    peers: [{ id: 'p1', x: 500, y: 500, visible: true, bubble: true }]
  };

  const selfYClose = 500 - L.SELF_PEER_SCREEN_NUDGE_PX;
  const peerRawClose = { x: 508, y: 502 };
  const close = {
    ...base,
    selfLabelY: selfYClose,
    selfHeadY: 500,
    peers: [{
      id: 'p1',
      x: peerRawClose.x,
      y: peerDisplayY(500, selfYClose, peerRawClose.x, peerRawClose.y),
      visible: true,
      bubble: true
    }]
  };
  assertNoOverlap(O.layoutIslandOverlays(close, m), close);

  const mid = {
    ...base,
    selfLabelY: 480,
    selfHeadY: 480,
    peers: [{ id: 'p1', x: 640, y: 472, visible: true, bubble: true }]
  };
  assertNoOverlap(O.layoutIslandOverlays(mid, m), mid);

  const selfYStack = 500 - L.SELF_PEER_SCREEN_NUDGE_PX;
  const stack = {
    ...base,
    selfLabelY: selfYStack,
    selfHeadY: 500,
    peers: [{
      id: 'p1',
      x: 500,
      y: peerDisplayY(500, selfYStack, 500, 500),
      visible: true,
      bubble: true
    }]
  };
  assertNoOverlap(O.layoutIslandOverlays(stack, m), stack);
});

test('名牌關閉時自己泡泡落在頭部投影、不套用名牌位移', () => {
  const input = {
    selfNameVisible: false,
    selfBubbleActive: true,
    selfHeadX: 320,
    selfHeadY: 280,
    peers: []
  };
  const layout = O.layoutIslandOverlays(input, m);
  assert.deepEqual(layout.selfBubble, { x: 320, anchorY: 280, extraUpPx: 0 });
});