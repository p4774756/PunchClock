/** 兩指距離下限（px），低於此不更新捏合半徑，避免除以近零。 */
export const PINCH_MIN_DIST_PX = 10;

export function pointerPairDistance(a, b) {
  const dx = (a.x || 0) - (b.x || 0);
  const dy = (a.y || 0) - (b.y || 0);
  return Math.hypot(dx, dy);
}

/**
 * 依捏合起點的比例算目標半徑：張開（curDist 變大）→ 半徑變小；合攏 → 半徑變大。
 * startRadius × startDist ÷ curDist
 */
export function pinchRadiusFromRatio(startRadius, startDist, curDist, minDist = PINCH_MIN_DIST_PX) {
  if (!Number.isFinite(startRadius) || startRadius <= 0) return startRadius;
  if (!Number.isFinite(startDist) || !Number.isFinite(curDist)) return startRadius;
  if (startDist < minDist || curDist < minDist) return startRadius;
  return startRadius * startDist / curDist;
}

export function createCanvasPointerState() {
  return {
    pointers: new Map(),
    pinchStartRadius: null,
    pinchStartDist: null,
    hadMultiTouch: false,
    moved: false,
    lastX: 0,
    lastY: 0
  };
}

export function onPointerDownState(state, pointerId, x, y) {
  if (state.pointers.size >= 2 && !state.pointers.has(pointerId)) {
    return { ignored: true, beginPinch: false };
  }
  const wasEmpty = state.pointers.size === 0;
  state.pointers.set(pointerId, { x, y });
  if (wasEmpty) {
    state.moved = false;
    state.hadMultiTouch = false;
    state.pinchStartRadius = null;
    state.pinchStartDist = null;
    state.lastX = x;
    state.lastY = y;
    return { ignored: false, beginPinch: false };
  }
  if (state.pointers.size === 2) {
    state.hadMultiTouch = true;
    state.moved = true;
    const pts = [...state.pointers.values()];
    state.pinchStartDist = pointerPairDistance(pts[0], pts[1]);
    return { ignored: false, beginPinch: true };
  }
  return { ignored: false, beginPinch: false };
}

export function onPointerMoveState(state, pointerId, x, y) {
  if (!state.pointers.has(pointerId)) return { mode: 'ignore' };
  state.pointers.set(pointerId, { x, y });
  if (state.pointers.size >= 2) {
    const pts = [...state.pointers.values()];
    const curDist = pointerPairDistance(pts[0], pts[1]);
    return { mode: 'pinch', curDist: curDist };
  }
  if (state.pointers.size === 1) {
    const dx = x - state.lastX;
    const dy = y - state.lastY;
    if (dx * dx + dy * dy > 9) state.moved = true;
    state.lastX = x;
    state.lastY = y;
    return { mode: 'drag', dx, dy };
  }
  return { mode: 'none' };
}

function afterRemovePointer(state) {
  if (state.pointers.size === 1) {
    const only = state.pointers.values().next().value;
    state.lastX = only.x;
    state.lastY = only.y;
    state.pinchStartRadius = null;
    state.pinchStartDist = null;
  }
  const ended = state.pointers.size === 0;
  const dragMoved = state.hadMultiTouch || state.moved;
  const allowClick = ended && !state.hadMultiTouch && !state.moved;
  if (ended) {
    state.hadMultiTouch = false;
    state.pinchStartRadius = null;
    state.pinchStartDist = null;
  }
  return { ended, allowClick, dragMoved, remaining: state.pointers.size };
}

export function onPointerUpState(state, pointerId) {
  if (!state.pointers.has(pointerId)) {
    return { ended: state.pointers.size === 0, allowClick: false, dragMoved: false, remaining: state.pointers.size };
  }
  state.pointers.delete(pointerId);
  return afterRemovePointer(state);
}

export function onPointerCancelState(state, pointerId) {
  if (!state.pointers.has(pointerId)) {
    return { ended: state.pointers.size === 0, dragMoved: false, remaining: state.pointers.size };
  }
  state.pointers.delete(pointerId);
  const out = afterRemovePointer(state);
  return { ended: out.ended, dragMoved: out.dragMoved, remaining: out.remaining };
}