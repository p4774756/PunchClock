// 探索模式純邏輯（不依賴 Three.js／DOM），供 island.js 使用並由 node:test 驗證。

export const PLAYER_SPAWN = { x: 2.15, z: 5.55, yaw: Math.PI };
export const PLAYER_SPEED = 3.35;
export const PLAYER_RADIUS = 0.28;
export const MAX_STEP_DT = 0.05;

export const WALK_ISLAND_R = 8.05;
export const WALK_RECTS = [
  { minX: -1.05, maxX: 0.85, minZ: 6.9, maxZ: 8.95 },
  { minX: 6.2, maxX: 9.55, minZ: 1.15, maxZ: 2.75 }
];
// 賭場本體（島嶼東南方空地、照片牆以南）；門朝 +Z（廣場），門前不納入碰撞。
export const CASINO = Object.freeze({
  id: 'casino',
  x: 3.55,
  z: -3.65,
  w: 2.4,
  d: 1.9,
  block: Object.freeze({ minX: 2.35, maxX: 4.75, minZ: -4.65, maxZ: -2.55 }),
  door: Object.freeze({ x: 3.55, z: -1.95, r: 1.2 })
});

export const BLOCK_RECTS = [
  { minX: -2.45, maxX: 2.45, minZ: -2.2, maxZ: 0.95 },
  { minX: -1.35, maxX: 1.35, minZ: 1.0, maxZ: 1.9 },
  { minX: -4.85, maxX: -3.2, minZ: -0.85, maxZ: 0.55 },
  { minX: 3.05, maxX: 5.25, minZ: -0.35, maxZ: 0.7 },
  { minX: -5.35, maxX: -4.35, minZ: 3.05, maxZ: 4.05 },
  { minX: 4.05, maxX: 5.05, minZ: 3.0, maxZ: 4.0 },
  { minX: 1.55, maxX: 2.75, minZ: 4.55, maxZ: 5.2 },
  { minX: -6.55, maxX: 6.55, minZ: -6.85, maxZ: -6.25 },
  CASINO.block
];
export const BLOCK_CIRCLES = [
  { x: -6.25, z: -5.45, r: 0.85 },
  { x: -4.5, z: -6.25, r: 0.75 },
  { x: 5.7, z: -5.65, r: 0.85 },
  { x: 6.45, z: -3.15, r: 0.7 },
  { x: -6.55, z: -1.35, r: 0.8 },
  { x: -6.35, z: 4.55, r: 0.7 },
  { x: 6.25, z: 5.15, r: 0.65 },
  { x: 1.7, z: -6.35, r: 0.55 },
  { x: -1.7, z: -6.45, r: 0.55 },
  { x: -5.7, z: 5.15, r: 0.45 },
  { x: 2.55, z: 4.35, r: 0.32 },
  { x: -2.7, z: -2.55, r: 0.32 },
  { x: -1.5, z: -2.5, r: 0.4 }
];

/** 預設互動區（賭場門口）。場景座標與 CASINO 一致；觸發須由玩家明確操作，不自動傳送。 */
export const DEFAULT_INTERACT_ZONES = Object.freeze([
  Object.freeze({
    id: CASINO.id,
    x: CASINO.door.x,
    z: CASINO.door.z,
    r: CASINO.door.r,
    label: '進入賭場',
    action: 'enterCasino'
  })
]);

// 每個可行走區域各自內縮半徑：整個角色圓必須完全落在島或碼頭內，不會半身泡水。
export function isWalkable(x, z, radius = PLAYER_RADIUS) {
  const islandR = WALK_ISLAND_R - radius;
  if (islandR > 0 && x * x + z * z <= islandR * islandR) return true;
  for (let i = 0; i < WALK_RECTS.length; i++) {
    const r = WALK_RECTS[i];
    if (x >= r.minX + radius && x <= r.maxX - radius && z >= r.minZ + radius && z <= r.maxZ - radius) return true;
  }
  return false;
}

export function isBlocked(x, z, radius = PLAYER_RADIUS) {
  for (let i = 0; i < BLOCK_RECTS.length; i++) {
    const r = BLOCK_RECTS[i];
    const dx = x - Math.max(r.minX, Math.min(x, r.maxX));
    const dz = z - Math.max(r.minZ, Math.min(z, r.maxZ));
    if (dx * dx + dz * dz < radius * radius) return true;
  }
  for (let i = 0; i < BLOCK_CIRCLES.length; i++) {
    const c = BLOCK_CIRCLES[i];
    const dx = x - c.x;
    const dz = z - c.z;
    const rr = c.r + radius;
    if (dx * dx + dz * dz < rr * rr) return true;
  }
  return false;
}

export function canStand(x, z, radius = PLAYER_RADIUS) {
  return isWalkable(x, z, radius) && !isBlocked(x, z, radius);
}

// 撞牆時沿單軸滑動；完全卡住則原地不動。
export function resolveMove(fromX, fromZ, toX, toZ, radius = PLAYER_RADIUS) {
  if (canStand(toX, toZ, radius)) return { x: toX, z: toZ };
  if (canStand(toX, fromZ, radius)) return { x: toX, z: fromZ };
  if (canStand(fromX, toZ, radius)) return { x: fromX, z: toZ };
  return { x: fromX, z: fromZ };
}

export function moveVector(keys, stick) {
  let x = 0;
  let z = 0;
  if (keys) {
    if (keys.KeyW || keys.ArrowUp) z -= 1;
    if (keys.KeyS || keys.ArrowDown) z += 1;
    if (keys.KeyA || keys.ArrowLeft) x -= 1;
    if (keys.KeyD || keys.ArrowRight) x += 1;
  }
  if (stick && stick.active) {
    x += stick.x;
    z += stick.y;
  }
  const len = Math.hypot(x, z);
  if (len < 0.08) return { x: 0, z: 0, moving: false };
  return { x: x / len, z: z / len, moving: true };
}

// 輸入向量（螢幕上／下／左／右）轉為以鏡頭 theta 為準的世界 XZ 方向。
export function cameraRelative(input, theta) {
  const s = Math.sin(theta);
  const c = Math.cos(theta);
  return {
    x: input.x * c + input.z * s,
    z: -input.x * s + input.z * c
  };
}

export function stepPlayer(pos, input, theta, dt, radius = PLAYER_RADIUS) {
  if (!input.moving) return { x: pos.x, z: pos.z, yaw: pos.yaw, moving: false };
  const dir = cameraRelative(input, theta);
  const step = PLAYER_SPEED * Math.min(Math.max(dt, 0), MAX_STEP_DT);
  const next = resolveMove(pos.x, pos.z, pos.x + dir.x * step, pos.z + dir.z * step, radius);
  return { x: next.x, z: next.z, yaw: Math.atan2(dir.x, dir.z), moving: true };
}

// 指數平滑係數：與 FPS 無關，同一段時間內收斂比例相同。reduced-motion 直接到位。
export function smoothingFactor(lambda, dt, reduced) {
  if (reduced) return 1;
  if (!(dt > 0)) return 0;
  return 1 - Math.exp(-lambda * dt);
}

// 探索模式狀態：exploring = 已進入探索；paused = 管理面板開啟中；lobby = 賭場大廳開啟中。
// lobby 只由大廳自己的開關改變，面板 panelClose／enter／exit 都不會解除，避免大廳下背景移動。
export function exploreState(state, action) {
  const s = state || { exploring: false, paused: false, lobby: false };
  const lobby = !!s.lobby;
  switch (action) {
    case 'enter': return { exploring: true, paused: false, lobby: lobby };
    case 'exit': return { exploring: false, paused: false, lobby: lobby };
    case 'panelOpen': return s.exploring ? { exploring: true, paused: true, lobby: lobby } : s;
    case 'panelClose': return { exploring: !!s.exploring, paused: false, lobby: lobby };
    case 'lobbyOpen': return { exploring: !!s.exploring, paused: !!s.paused, lobby: true };
    case 'lobbyClose': return { exploring: !!s.exploring, paused: !!s.paused, lobby: false };
    default: return s;
  }
}

export function controlsActive(state) {
  return !!(state && state.exploring && !state.paused && !state.lobby);
}

/**
 * 探索模式與管理面板、賭場大廳的協調流程（不依賴 Three.js／DOM）。
 * ui 回呼：
 *   onModeChange(prev, next)、clearMoveInput()
 *   followPlayer()：鏡頭回到角色身上；showPose(id)：鏡頭轉到面板區；showHome()：回全景
 *   sheetOpen()、lobbyOpen()：目前 DOM 狀態；closeSheet()：關閉管理面板（會回呼 resetPanel）
 */
export function createExploreFlow(ui) {
  const hooks = ui || {};
  let mode = exploreState(null, '');

  function call(name, a, b) {
    if (typeof hooks[name] === 'function') return hooks[name](a, b);
    return undefined;
  }

  function dispatch(action) {
    const prev = mode;
    mode = exploreState(mode, action);
    if (!controlsActive(mode)) call('clearMoveInput');
    call('onModeChange', prev, mode);
    return mode;
  }

  return {
    get mode() { return mode; },
    dispatch: dispatch,
    canMove() {
      return controlsActive(mode) && !call('sheetOpen') && !call('lobbyOpen');
    },
    focusPanel(id) {
      dispatch('panelOpen');
      call('showPose', id);
    },
    resetPanel() {
      if (mode.paused) {
        dispatch('panelClose');
        if (controlsActive(mode)) call('followPlayer');
        return;
      }
      if (mode.exploring) return;
      call('showHome');
    },
    // 先鎖住移動，再關面板：面板 reset 只解除 paused，不會解除 lobby。
    beforeLobbyOpen() {
      call('clearMoveInput');
      dispatch('lobbyOpen');
      if (call('sheetOpen')) call('closeSheet');
    },
    // 大廳唯一的狀態轉移入口：返回按鈕、Escape、程式呼叫都經過這裡。
    lobbyChanged(open) {
      const wasLobby = mode.lobby;
      dispatch(open ? 'lobbyOpen' : 'lobbyClose');
      if (!open && wasLobby && controlsActive(mode)) call('followPlayer');
    }
  };
}

export function playerVisible(state) {
  return !!(state && state.exploring);
}

export const MOVE_CODES = Object.freeze({
  KeyW: true, KeyA: true, KeyS: true, KeyD: true,
  ArrowUp: true, ArrowLeft: true, ArrowDown: true, ArrowRight: true
});

/**
 * 移動鍵是否交給探索模式。優先順序：
 * 1. 輸入欄位（input／textarea／select／contenteditable）一律交給 UI。
 * 2. 非探索中、或面板／清單開啟時不攔截。
 * 3. 焦點在其它按鈕／連結等 UI 控制項時，方向鍵保留給 UI（捲動、清單導覽），WASD 仍可移動。
 * 4. 焦點在 body、畫布、探索按鈕或搖桿時全部攔截。
 */
export function shouldCaptureMoveKey(ctx) {
  if (!ctx || !MOVE_CODES[ctx.code]) return false;
  if (ctx.typing) return false;
  if (!ctx.active || ctx.uiOpen) return false;
  if (ctx.focus === 'control' && ctx.code.indexOf('Arrow') === 0) return false;
  return true;
}

/** 回傳目前所在的互動區（距離優先），否則 null。zones: [{ id, x, z, r }] */
export function hitInteractZone(x, z, zones) {
  if (!zones || !zones.length) return null;
  let best = null;
  let bestDist = Infinity;
  for (let i = 0; i < zones.length; i++) {
    const zone = zones[i];
    if (!zone) continue;
    const dx = x - zone.x;
    const dz = z - zone.z;
    const dist = dx * dx + dz * dz;
    const r = zone.r == null ? 1 : zone.r;
    if (dist <= r * r && dist < bestDist) {
      best = zone;
      bestDist = dist;
    }
  }
  return best;
}

/**
 * 入口提示是否顯示。須探索中、未暫停、未開面板／大廳，且在互動區內。
 * 不自動進入——僅決定提示可見性。
 */
export function shouldShowInteractPrompt(ctx) {
  if (!ctx) return false;
  if (!ctx.exploring || ctx.paused || ctx.uiOpen || ctx.lobbyOpen) return false;
  return !!(ctx.zoneId);
}

/** Enter／E 是否觸發目前互動提示（不搶輸入框與其它可聚焦 UI）。 */
export function shouldTriggerInteractKey(ctx) {
  if (!ctx || !ctx.promptVisible) return false;
  if (ctx.typing) return false;
  if (ctx.focus === 'control') return false;
  const code = ctx.code || '';
  const key = ctx.key || '';
  return code === 'Enter' || code === 'KeyE' || key === 'Enter' || key === 'e' || key === 'E';
}
