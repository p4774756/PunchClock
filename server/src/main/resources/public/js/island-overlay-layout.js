// 神之島名牌／聊天泡泡螢幕矩形與碰撞錯開（純函式，供 node:test）。

/** 與 island.css 名牌、泡泡近似一致。 */
export const DEFAULT_OVERLAY_METRICS = Object.freeze({
  remPx: 16,
  selfNameWidth: 118,
  selfNameHeight: 26,
  selfBubbleWidth: 104,
  selfBubbleHeight: 30,
  peerNameWidth: 110,
  peerNameHeight: 26,
  peerBubbleWidth: 108,
  peerBubbleHeight: 32,
  selfAnchorY: 1.2,
  peerBubbleLiftRem: 1.85,
  minGapPx: 4
});

export function rectIntersectionArea(a, b) {
  if (!a || !b) return 0;
  const w = Math.min(a.right, b.right) - Math.max(a.left, b.left);
  const h = Math.min(a.bottom, b.bottom) - Math.max(a.top, b.top);
  if (w <= 0 || h <= 0) return 0;
  return w * h;
}

/** 自己／同儕名牌：translate(-50%, -120%)。 */
export function nameplateScreenRect(x, anchorY, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const w = m.selfNameWidth;
  const h = m.selfNameHeight;
  const ay = m.selfAnchorY;
  return Object.freeze({
    left: x - w * 0.5,
    right: x + w * 0.5,
    top: anchorY - ay * h,
    bottom: anchorY,
    width: w,
    height: h
  });
}

export function peerNameplateScreenRect(x, anchorY, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const w = m.peerNameWidth;
  const h = m.peerNameHeight;
  const ay = m.selfAnchorY;
  return Object.freeze({
    left: x - w * 0.5,
    right: x + w * 0.5,
    top: anchorY - ay * h,
    bottom: anchorY,
    width: w,
    height: h
  });
}

/** 自己泡泡：translate(-50%, -120%)，可選額外 translateY(-extraPx)。 */
export function selfBubbleScreenRect(x, anchorY, extraPx, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const w = m.selfBubbleWidth;
  const h = m.selfBubbleHeight;
  const ay = m.selfAnchorY;
  const ex = extraPx == null ? 0 : extraPx;
  const bottom = anchorY - ex; /* extraUpPx：越大泡泡整體越往上 */
  return Object.freeze({
    left: x - w * 0.5,
    right: x + w * 0.5,
    top: bottom - ay * h,
    bottom: bottom,
    width: w,
    height: h
  });
}

/** 同儕泡泡：translate(-50%, -100%) translateY(-liftRem)。 */
export function peerBubbleScreenRect(x, labelAnchorY, extraOffsetY, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const w = m.peerBubbleWidth;
  const h = m.peerBubbleHeight;
  const lift = m.peerBubbleLiftRem * m.remPx;
  const upPx = extraOffsetY == null ? 0 : extraOffsetY;
  const bottom = labelAnchorY - lift - upPx;
  return Object.freeze({
    left: x - w * 0.5,
    right: x + w * 0.5,
    top: bottom - h,
    bottom: bottom,
    width: w,
    height: h
  });
}

/** 自己泡泡錨點：名牌上緣再往上 ≥ minGap（含 nudge 後的 labelY）。 */
export function selfBubbleAnchorFromNameplate(labelX, labelY, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const nameRect = nameplateScreenRect(labelX, labelY, m);
  return Object.freeze({
    x: labelX,
    anchorY: nameRect.top - m.minGapPx,
    extraUpPx: 0
  });
}

/**
 * 輸入各元素螢幕矩形腳本，輸出泡泡位移直到與固定名牌／彼此不相交。
 * @param {object} input
 * @param {boolean} input.selfNameVisible
 * @param {number} input.selfLabelX
 * @param {number} input.selfLabelY
 * @param {boolean} input.selfBubbleActive
 * @param {number} [input.selfHeadX] 名牌關時用世界投影頭部
 * @param {number} [input.selfHeadY]
 * @param {Array<{id:string,x:number,y:number,bubble:boolean}>} input.peers
 */
export function layoutIslandOverlays(input, metrics) {
  const m = metrics || DEFAULT_OVERLAY_METRICS;
  const in0 = input || {};
  const peers = in0.peers || [];

  let selfBubble = null;
  let selfNameRect = null;

  if (in0.selfNameVisible && in0.selfLabelY != null && in0.selfLabelX != null) {
    selfNameRect = nameplateScreenRect(in0.selfLabelX, in0.selfLabelY, m);
    if (in0.selfBubbleActive) {
      selfBubble = selfBubbleAnchorFromNameplate(in0.selfLabelX, in0.selfLabelY, m);
    }
  } else if (in0.selfBubbleActive && in0.selfHeadX != null && in0.selfHeadY != null) {
    selfBubble = Object.freeze({
      x: in0.selfHeadX,
      anchorY: in0.selfHeadY,
      extraUpPx: 0
    });
  }

  const peerNames = peers
    .filter((p) => p && p.visible !== false)
    .map((p) => Object.freeze({
      id: p.id,
      rect: peerNameplateScreenRect(p.x, p.y, m)
    }));

  const peerBubbleOffsets = Object.create(null);
  peers.forEach((p) => {
    if (!p || !p.id || !p.bubble || p.visible === false) return;
    peerBubbleOffsets[p.id] = 0;
  });

  const maxIter = 480;
  for (let iter = 0; iter < maxIter; iter++) {
    const selfBubbleRect = selfBubble
      ? selfBubbleScreenRect(selfBubble.x, selfBubble.anchorY, selfBubble.extraUpPx, m)
      : null;
    const rects = [];
    if (selfNameRect) rects.push({ kind: 'selfName', rect: selfNameRect });
    if (selfBubbleRect) rects.push({ kind: 'selfBubble', rect: selfBubbleRect });
    peerNames.forEach((row) => {
      rects.push({ kind: 'peerName', id: row.id, rect: row.rect });
    });
    Object.keys(peerBubbleOffsets).forEach((id) => {
      const peer = peers.find((p) => p && p.id === id);
      if (!peer) return;
      const rect = peerBubbleScreenRect(peer.x, peer.y, peerBubbleOffsets[id], m);
      rects.push({ kind: 'peerBubble', id: id, rect: rect });
    });

    let fixed = false;
    for (let i = 0; i < rects.length; i++) {
      for (let j = i + 1; j < rects.length; j++) {
        const a = rects[i];
        const b = rects[j];
        if (rectIntersectionArea(a.rect, b.rect) <= 0) continue;

        if (a.kind === 'peerBubble' || b.kind === 'peerBubble') {
          const pb = a.kind === 'peerBubble' ? a : b;
          peerBubbleOffsets[pb.id] = (peerBubbleOffsets[pb.id] || 0) + 1;
          fixed = true;
          break;
        }
        if ((a.kind === 'selfBubble' || b.kind === 'selfBubble') && selfBubble) {
          selfBubble = Object.freeze({
            x: selfBubble.x,
            anchorY: selfBubble.anchorY,
            extraUpPx: (selfBubble.extraUpPx || 0) + 1
          });
          fixed = true;
          break;
        }
      }
      if (fixed) break;
    }
    if (!fixed) break;
  }

  return Object.freeze({
    selfBubble: selfBubble,
    peerBubbleOffsetY: Object.freeze(Object.assign({}, peerBubbleOffsets))
  });
}