// 探索模式純邏輯（不依賴 Three.js／DOM），供 island.js 使用並由 node:test 驗證。
// 可行走區、障礙與互動區來自 island-layout.js，不在這裡手寫座標。

import {
  MAIN_ISLAND,
  ISLANDS,
  BRIDGES,
  WALK_PADS,
  OBSTACLE_CIRCLES,
  THEME_CIRCLES,
  BUILDINGS,
  buildingById,
  blockRects,
  interactZones,
  walkable,
  blocked
} from './island-layout.js';

export const PLAYER_SPAWN = { x: 2.15, z: 5.55, yaw: Math.PI };
export const PLAYER_SPEED = 3.35;
// 看板角色裙擺比舊方塊人稍寬。只放大到仍能沿既有橋面與座位轉角走過，頭髮透明的部分不計入碰撞。
export const PLAYER_RADIUS = 0.29;
export const MAX_STEP_DT = 0.05;

export const WALK_ISLAND_R = MAIN_ISLAND.r;
export const WALK_RECTS = WALK_PADS;
export const BLOCK_RECTS = blockRects();
export const BLOCK_CIRCLES = OBSTACLE_CIRCLES.concat(THEME_CIRCLES);

const casinoBuilding = buildingById('casino');
// 賭場已搬到博弈島。門朝橋（本地 +Z 經 yaw 轉到世界座標），門前不納入碰撞。
export const CASINO = Object.freeze({
  id: casinoBuilding.id,
  x: casinoBuilding.x,
  z: casinoBuilding.z,
  w: casinoBuilding.w,
  d: casinoBuilding.d,
  yaw: casinoBuilding.yaw,
  block: casinoBuilding.block,
  door: Object.freeze({
    x: casinoBuilding.door.x,
    z: casinoBuilding.door.z,
    r: casinoBuilding.entry.r
  })
});

/** 預設互動區由建築 entry 推導。觸發須由玩家明確操作，不自動傳送。 */
export const DEFAULT_INTERACT_ZONES = interactZones();

export { ISLANDS, BRIDGES, BUILDINGS };

// 每個可行走區域各自內縮半徑：整個角色圓必須完全落在島、碼頭或橋面內。
export function isWalkable(x, z, radius = PLAYER_RADIUS) {
  return walkable(x, z, radius);
}

export function isBlocked(x, z, radius = PLAYER_RADIUS) {
  return blocked(x, z, radius);
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
