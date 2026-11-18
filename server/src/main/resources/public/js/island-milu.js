// 探索模式 milu 看板：朝向、幀與貼地計算不依賴 Three.js／DOM，供 node:test。
// 移動時依鏡頭相對方向切正面、背面、左、右；停下沿用上次朝向播待機，偶爾眨眼，靠近 NPC 或互動區揮一次手。
// 前／後走路與背面待機缺檔或載入失敗時退回左右走與正面待機，不會讓整隻看板失敗。
// 圖檔沿用桌面端 /pet/milu，由伺服器靜態路徑提供，不在這裡複製第二份。

const FACE_CENTER = Object.freeze({
  front: 0,
  right: Math.PI / 2,
  back: Math.PI,
  left: -Math.PI / 2
});

export const MILU_ANIM = Object.freeze({
  // 400×400 透明圖，腳底約在底邊往上 41px（桌面端 ANCHOR_Y 359）。flipY 時圖的底邊是 v=0。
  footV: 0.1025,
  height: 1.2,
  shadowRadius: 0.34,
  alphaTest: 0.5,
  faceDeadzone: 0.22,
  // 四個 90° 桶的半寬。連續走路時要再超出 faceHysteresis 才換向，避免對角線來回閃幀。
  faceBucket: Math.PI / 4,
  faceHysteresis: 12 * Math.PI / 180,
  waveNpcRadius: 1.75,
  waveZoneExtra: 0.55,
  fps: Object.freeze({ walk: 8, idle: 5, blink: 10, wave: 8 }),
  blinkEvery: 3.4,
  baseUrl: '/pet/milu/',
  clips: Object.freeze({
    walk: Object.freeze({
      left: Object.freeze(['walk/left_01.png', 'walk/left_02.png', 'walk/left_03.png', 'walk/left_04.png']),
      right: Object.freeze(['walk/right_01.png', 'walk/right_02.png', 'walk/right_03.png', 'walk/right_04.png']),
      front: Object.freeze(['walk/front_01.png', 'walk/front_02.png', 'walk/front_03.png', 'walk/front_04.png']),
      back: Object.freeze(['walk/back_01.png', 'walk/back_02.png', 'walk/back_03.png', 'walk/back_04.png'])
    }),
    idle: Object.freeze({
      breath: Object.freeze(['idle/breath_01.png', 'idle/breath_02.png', 'idle/breath_03.png', 'idle/breath_04.png']),
      blink: Object.freeze(['idle/blink_01.png', 'idle/blink_02.png']),
      backBreath: Object.freeze(['idle/back_breath_01.png', 'idle/back_breath_02.png', 'idle/back_breath_03.png', 'idle/back_breath_04.png']),
      backBlink: Object.freeze(['idle/back_blink_01.png', 'idle/back_blink_02.png'])
    }),
    wave: Object.freeze(['wave/01.png', 'wave/02.png', 'wave/03.png', 'wave/04.png'])
  })
});

export const BERRY_ANIM = Object.freeze({
  footV: MILU_ANIM.footV,
  height: MILU_ANIM.height,
  shadowRadius: MILU_ANIM.shadowRadius,
  alphaTest: MILU_ANIM.alphaTest,
  faceDeadzone: MILU_ANIM.faceDeadzone,
  faceBucket: MILU_ANIM.faceBucket,
  faceHysteresis: MILU_ANIM.faceHysteresis,
  waveNpcRadius: MILU_ANIM.waveNpcRadius,
  waveZoneExtra: MILU_ANIM.waveZoneExtra,
  fps: MILU_ANIM.fps,
  blinkEvery: MILU_ANIM.blinkEvery,
  baseUrl: '/pet/berry/',
  clips: MILU_ANIM.clips
});

export const ISLAND_AVATAR_IDS = Object.freeze(['milu', 'berry']);

const ISLAND_AVATAR_TABLE = Object.freeze({
  milu: MILU_ANIM,
  berry: BERRY_ANIM
});

/** 只接受 milu／berry，其餘當 milu。 */
export function normalizeIslandAvatar(id) {
  return id === 'berry' ? 'berry' : 'milu';
}

export function islandAvatarAnim(id) {
  return ISLAND_AVATAR_TABLE[normalizeIslandAvatar(id)];
}

export function createMiluState() {
  return {
    mode: 'idle',
    facing: 'right',
    frame: 0,
    acc: 0,
    blinkAcc: 0,
    blinking: false,
    waved: false
  };
}

// 平面預設以中心為原點。把腳底（v = footV）平移到本地 y = 0，角色才站在地上。
export function feetOffsetY(height, footV) {
  return height * (0.5 - footV);
}

// 世界速度轉回畫面軸。與 island-explore cameraRelative 互為反函數；screenX < 0 是往左。
export function screenAxes(worldX, worldZ, theta) {
  const len = Math.hypot(worldX, worldZ);
  if (!(len > 1e-8)) return { x: 0, z: 0 };
  const c = Math.cos(theta || 0);
  const s = Math.sin(theta || 0);
  const wx = worldX / len;
  const wz = worldZ / len;
  return {
    x: wx * c - wz * s,
    z: wx * s + wz * c
  };
}

function clipOf(anim, facing) {
  const walk = anim.clips.walk;
  const list = walk[facing];
  return list && list.length ? list : null;
}

function sideFacing(facing) {
  return facing === 'left' ? 'left' : 'right';
}

function validFacing(facing) {
  if (facing === 'left' || facing === 'right' || facing === 'front' || facing === 'back') return facing;
  return 'right';
}

function cfgNumber(cfg, key) {
  const value = cfg[key];
  return value == null ? MILU_ANIM[key] : value;
}

function wrapPi(rad) {
  let d = rad;
  const turn = Math.PI * 2;
  while (d > Math.PI) d -= turn;
  while (d < -Math.PI) d += turn;
  return d;
}

// 0 朝鏡頭（screenZ > 0），正角往右，±π 離鏡頭（搖桿往上、W）。半寬內才算正面／背面，剛好落在交界算左右。
function bucketFacing(angle, half) {
  const a = wrapPi(angle);
  const abs = Math.abs(a);
  if (abs < half) return 'front';
  if (abs > Math.PI - half) return 'back';
  return a > 0 ? 'right' : 'left';
}

function fallbackSide(prior, sx, ax, dead) {
  if (ax < dead) return sideFacing(prior);
  return sx < 0 ? 'left' : 'right';
}

// 角度 0 是朝鏡頭（screenZ > 0，鏡頭在角色後方，往上走是離鏡頭），π/2 是右。sticky 為 false 時用嚴格 90° 桶（剛起步）。
// 走路中要跨過桶邊界再加 faceHysteresis 才換向。前／後圖沒有時退回左／右，水平分量小於 deadzone 則沿用上次側向。
export function resolveFacing(prev, screenX, screenZ, anim, sticky) {
  const cfg = anim || MILU_ANIM;
  const sx = screenX || 0;
  const sz = screenZ || 0;
  const ax = Math.abs(sx);
  const az = Math.abs(sz);
  const prior = validFacing(prev);
  const dead = cfgNumber(cfg, 'faceDeadzone');
  if (!(ax > 1e-8 || az > 1e-8)) {
    if ((prior === 'front' || prior === 'back') && !clipOf(cfg, prior)) return sideFacing(prior);
    return prior;
  }

  const half = cfgNumber(cfg, 'faceBucket');
  const hyst = cfgNumber(cfg, 'faceHysteresis');
  const angle = Math.atan2(sx, sz);
  const hold = sticky !== false && prior === prev;
  let facing = prior;
  if (!hold || Math.abs(wrapPi(angle - FACE_CENTER[prior])) > half + hyst) {
    facing = bucketFacing(angle, half);
  }

  if (facing === 'front' || facing === 'back') {
    if (clipOf(cfg, facing)) return facing;
    return fallbackSide(prior, sx, ax, dead);
  }
  if (ax < dead && az <= ax) return sideFacing(prior);
  return facing;
}

// 可選的前／後走路與背面待機載入失敗時清掉整組，下一幀就退回左右走或正面待機。
export function forgetOptionalClip(anim, rel) {
  const clips = anim && anim.clips;
  if (!clips || !rel) return false;
  const walk = clips.walk || {};
  const idle = clips.idle || {};
  const lists = [walk.front, walk.back, idle.backBreath, idle.backBlink];
  for (let i = 0; i < lists.length; i++) {
    const list = lists[i];
    if (!list) continue;
    for (let j = 0; j < list.length; j++) {
      if (list[j] !== rel) continue;
      list.length = 0;
      return true;
    }
  }
  return false;
}

export function isNearWave(x, z, npcs, zones, anim) {
  const cfg = anim || MILU_ANIM;
  if (npcs) {
    const r = cfg.waveNpcRadius;
    for (let i = 0; i < npcs.length; i++) {
      const npc = npcs[i];
      if (!npc) continue;
      if (Math.hypot(x - npc.x, z - npc.z) <= r) return true;
    }
  }
  if (zones) {
    for (let i = 0; i < zones.length; i++) {
      const zone = zones[i];
      if (!zone) continue;
      const reach = (zone.r == null ? 1 : zone.r) + cfg.waveZoneExtra;
      if (Math.hypot(x - zone.x, z - zone.z) <= reach) return true;
    }
  }
  return false;
}

function copyState(state) {
  const src = state || createMiluState();
  return {
    mode: src.mode,
    facing: src.facing,
    frame: src.frame,
    acc: src.acc,
    blinkAcc: src.blinkAcc,
    blinking: src.blinking,
    waved: src.waved
  };
}

function listOrNull(list) {
  return list && list.length ? list : null;
}

function backBreathOf(anim) {
  return listOrNull(anim.clips.idle.backBreath);
}

function backBlinkOf(anim) {
  return listOrNull(anim.clips.idle.backBlink);
}

function idleFrames(anim, state) {
  if (state.facing === 'back') {
    const breath = backBreathOf(anim);
    const blink = backBlinkOf(anim);
    if (state.blinking && blink) return blink;
    if (breath) return breath;
  }
  if (state.blinking) return anim.clips.idle.blink;
  return anim.clips.idle.breath;
}

// 已有背面待機但沒有背面眨眼時，不要改播正面的臉。
function idleCanBlink(anim, state) {
  if (state.facing === 'back' && backBreathOf(anim)) return !!backBlinkOf(anim);
  return !!listOrNull(anim.clips.idle.blink);
}

function framesFor(anim, state) {
  if (state.mode === 'wave') return anim.clips.wave;
  if (state.mode === 'walk') {
    return clipOf(anim, state.facing) || anim.clips.walk[sideFacing(state.facing)] || [];
  }
  return idleFrames(anim, state);
}

function fpsFor(anim, state) {
  if (state.mode === 'wave') return anim.fps.wave;
  if (state.mode === 'walk') return anim.fps.walk;
  if (state.blinking) return anim.fps.blink;
  return anim.fps.idle;
}

export function miluClipPath(state, anim) {
  const cfg = anim || MILU_ANIM;
  const list = framesFor(cfg, state || createMiluState());
  if (!list.length) return '';
  const index = state && state.frame > 0 ? state.frame : 0;
  return list[index] || list[0];
}

// 停下播呼吸，每隔 blinkEvery 秒眨眼一輪。靠近時揮手一次，離開後才重新武裝。
// reduced 只停掉待機呼吸與眨眼；走路仍換朝向，讓方向看得懂。
export function miluStep(state, dt, ctx) {
  const input = ctx || {};
  const anim = input.anim || MILU_ANIM;
  const next = copyState(state);
  const step = dt > 0 ? dt : 0;
  const moving = !!input.moving;
  const near = !!input.near;
  const reduced = !!input.reduced;

  if (!near) next.waved = false;

  if (next.mode === 'wave') {
    // 揮手播完才回到走路或待機。
  } else if (near && !next.waved) {
    next.mode = 'wave';
    next.frame = 0;
    next.acc = 0;
    next.blinking = false;
  } else if (moving) {
    const facing = resolveFacing(next.facing, input.screenX, input.screenZ, anim, next.mode === 'walk');
    if (next.mode !== 'walk' || facing !== next.facing) {
      next.frame = 0;
      next.acc = 0;
    }
    next.mode = 'walk';
    next.facing = facing;
    next.blinking = false;
  } else if (next.mode !== 'idle') {
    next.mode = 'idle';
    next.frame = 0;
    next.acc = 0;
    next.blinking = false;
  }

  if (reduced && next.mode === 'idle') {
    next.frame = 0;
    next.acc = 0;
    next.blinking = false;
    next.blinkAcc = 0;
    return next;
  }

  if (next.mode === 'idle' && !next.blinking) {
    next.blinkAcc += step;
    if (next.blinkAcc >= anim.blinkEvery && idleCanBlink(anim, next)) {
      next.blinking = true;
      next.frame = 0;
      next.acc = 0;
      next.blinkAcc = 0;
    }
  }

  const list = framesFor(anim, next);
  const count = list.length;
  if (!count) return next;
  const interval = 1 / fpsFor(anim, next);
  next.acc += step;
  while (next.acc >= interval) {
    next.acc -= interval;
    if (next.mode === 'wave') {
      if (next.frame + 1 >= count) {
        next.waved = true;
        next.mode = moving ? 'walk' : 'idle';
        next.frame = 0;
        next.acc = 0;
        next.blinking = false;
        if (moving) {
          next.facing = resolveFacing(next.facing, input.screenX, input.screenZ, anim, false);
        }
        break;
      }
      next.frame += 1;
    } else if (next.blinking) {
      if (next.frame + 1 >= count) {
        next.blinking = false;
        next.frame = 0;
        next.acc = 0;
        break;
      }
      next.frame += 1;
    } else {
      next.frame = (next.frame + 1) % count;
    }
  }
  if (next.frame >= count) next.frame = 0;
  return next;
}

export const MILU_LOAD = Object.freeze({
  maxRetries: 3,
  retryDelaysMs: Object.freeze([1000, 3000, 8000]),
  maxConcurrent: 4,
  bootstrap: Object.freeze([
    'idle/breath_01.png',
    'walk/front_01.png',
    'walk/front_02.png',
    'walk/front_03.png',
    'walk/front_04.png'
  ])
});

export function miluCollectPaths(anim) {
  return collectPaths(anim || MILU_ANIM);
}

export function miluPartitionLoadPaths(anim) {
  const groups = collectPaths(anim || MILU_ANIM);
  const bootstrap = MILU_LOAD.bootstrap.slice();
  const bootSet = new Set(bootstrap);
  const requiredRest = [];
  for (let i = 0; i < groups.required.length; i++) {
    const req = groups.required[i];
    if (!bootSet.has(req)) requiredRest.push(req);
  }
  const optional = [];
  for (let j = 0; j < groups.optional.length; j++) {
    const opt = groups.optional[j];
    if (!bootSet.has(opt)) optional.push(opt);
  }
  return {
    bootstrap: bootstrap,
    requiredRest: requiredRest,
    optional: optional
  };
}

export function miluOrderedLoadPlan(anim) {
  const parts = miluPartitionLoadPaths(anim);
  return parts.bootstrap.concat(parts.requiredRest, parts.optional);
}

export function miluRetryDelayMs(retryIndex) {
  const delays = MILU_LOAD.retryDelaysMs;
  return retryIndex >= 0 && retryIndex < delays.length ? delays[retryIndex] : -1;
}

export function miluShouldRetry(failureCount, maxRetries) {
  const limit = maxRetries == null ? MILU_LOAD.maxRetries : maxRetries;
  return failureCount > 0 && failureCount <= limit;
}

export function miluTextureUrl(baseUrl, rel, failureCount) {
  const url = (baseUrl || '') + rel;
  if (failureCount >= 2) {
    return url + (url.indexOf('?') >= 0 ? '&' : '?') + 'r=' + failureCount;
  }
  return url;
}

export function miluConcurrencySlots(inFlightCount, maxConcurrent) {
  const cap = maxConcurrent == null ? MILU_LOAD.maxConcurrent : maxConcurrent;
  return Math.max(0, cap - (inFlightCount || 0));
}

export function miluPathsToStart(queue, inFlight, skip, maxConcurrent) {
  const slots = miluConcurrencySlots(inFlight.size, maxConcurrent);
  const starts = [];
  if (slots <= 0) return starts;
  const queued = queue.slice();
  for (let i = 0; i < queued.length && starts.length < slots; i++) {
    const rel = queued[i];
    if (!rel || skip.has(rel) || inFlight.has(rel)) continue;
    starts.push(rel);
  }
  return starts;
}

function isPathCollection(loaded) {
  return !!(loaded && typeof loaded.has === 'function' && typeof loaded.size === 'number');
}

export function loadedPathSet(loaded) {
  if (isPathCollection(loaded)) return loaded;
  const set = new Set();
  if (!loaded) return set;
  if (typeof loaded.forEach === 'function') {
    loaded.forEach((value, key) => {
      if (value != null) set.add(key);
    });
    return set;
  }
  const list = Array.isArray(loaded) ? loaded : Object.keys(loaded);
  for (let i = 0; i < list.length; i++) set.add(list[i]);
  return set;
}

function allFrameLists(anim) {
  const cfg = anim || MILU_ANIM;
  const out = [];
  const walk = cfg.clips.walk || {};
  const keys = Object.keys(walk);
  for (let i = 0; i < keys.length; i++) {
    const list = walk[keys[i]];
    if (list && list.length) out.push(list);
  }
  const idle = cfg.clips.idle || {};
  const idleKeys = Object.keys(idle);
  for (let j = 0; j < idleKeys.length; j++) {
    const idleList = idle[idleKeys[j]];
    if (idleList && idleList.length) out.push(idleList);
  }
  if (cfg.clips.wave && cfg.clips.wave.length) out.push(cfg.clips.wave);
  return out;
}

function findListContaining(anim, rel) {
  const lists = allFrameLists(anim);
  for (let i = 0; i < lists.length; i++) {
    if (lists[i].indexOf(rel) >= 0) return lists[i];
  }
  return null;
}

function firstLoadedInList(list, loaded) {
  if (!list || !loaded || !loaded.size) return '';
  for (let i = 0; i < list.length; i++) {
    if (loaded.has(list[i])) return list[i];
  }
  return '';
}

function firstLoadedAny(anim, loaded) {
  const lists = allFrameLists(anim);
  for (let i = 0; i < lists.length; i++) {
    const hit = firstLoadedInList(lists[i], loaded);
    if (hit) return hit;
  }
  return '';
}

function pickNearestInList(list, idx, loaded) {
  if (!list || !list.length || !loaded || !loaded.size) return '';
  const at = idx < 0 ? 0 : idx;
  for (let d = 0; d < list.length; d++) {
    const left = at - d;
    if (left >= 0 && loaded.has(list[left])) return list[left];
    const right = at + d;
    if (d > 0 && right < list.length && loaded.has(list[right])) return list[right];
  }
  return '';
}

export function avatarVisibility({ show, ready, failed }) {
  const on = !!show;
  return {
    milu: on && !!ready,
    placeholder: on && !failed && !ready,
    block: on && !!failed
  };
}

export function miluResolveForShow(requested, anim, loaded, cache) {
  const row = cache || { requested: '', size: -1, resolved: '' };
  if (isPathCollection(loaded) && loaded.has(requested)) {
    row.requested = requested;
    row.size = loaded.size;
    row.resolved = requested;
    return { cache: row, path: requested };
  }
  const size = isPathCollection(loaded) ? loaded.size : 0;
  if (row.requested === requested && row.size === size) {
    return { cache: row, path: row.resolved };
  }
  row.resolved = resolveDisplayPath(requested, anim, loaded);
  row.requested = requested;
  row.size = size;
  return { cache: row, path: row.resolved };
}

export function resolveDisplayPath(requested, anim, loaded) {
  const loadedSet = loadedPathSet(loaded);
  if (!requested || !loadedSet.size) return '';
  if (loadedSet.has(requested)) return requested;
  const list = findListContaining(anim, requested);
  if (list) {
    const picked = pickNearestInList(list, list.indexOf(requested), loadedSet);
    if (picked) return picked;
  }
  const breath = (anim || MILU_ANIM).clips.idle.breath;
  const fromBreath = firstLoadedInList(breath, loadedSet);
  if (fromBreath) return fromBreath;
  return firstLoadedAny(anim, loadedSet);
}

export function miluReadyFromState(state) {
  const row = state || {};
  return !row.failed && (row.loadedCount || 0) > 0;
}

export function miluFailedFromState(state, anim) {
  const row = state || {};
  if (row.failed) return true;
  const required = miluCollectPaths(anim).required;
  const exhausted = row.exhausted || [];
  const done = Object.create(null);
  for (let i = 0; i < exhausted.length; i++) done[exhausted[i]] = true;
  if (!required.length) return false;
  for (let j = 0; j < required.length; j++) {
    if (!done[required[j]]) return false;
  }
  return true;
}

function collectPaths(anim) {
  const required = [];
  const optional = [];
  const seen = Object.create(null);
  function add(list, bucket) {
    if (!list) return;
    for (let i = 0; i < list.length; i++) {
      const rel = list[i];
      if (!rel || seen[rel]) continue;
      seen[rel] = true;
      bucket.push(rel);
    }
  }
  const walk = anim.clips.walk || {};
  const idle = anim.clips.idle || {};
  add(walk.left, required);
  add(walk.right, required);
  add(idle.breath, required);
  add(idle.blink, required);
  add(anim.clips.wave, required);
  add(walk.front, optional);
  add(walk.back, optional);
  add(idle.backBreath, optional);
  add(idle.backBlink, optional);
  return { required: required, optional: optional };
}

function miluLoadMeta(textures, create) {
  if (!textures._miluLoad) {
    textures._miluLoad = create ? {
      failed: false,
      started: false,
      timers: [],
      queue: [],
      inFlight: new Set(),
      attempts: new Map(),
      exhausted: new Set(),
      settled: new Set(),
      optional: Object.create(null),
      phase: 'bootstrap'
    } : null;
  }
  return textures._miluLoad;
}

function copyList(list) {
  return list && list.length ? list.slice() : [];
}

// 載入失敗時要改得到幀清單，不能改到凍結的 MILU_ANIM。
function liveAnimFrom(anim) {
  const clips = anim.clips || {};
  const walk = clips.walk || {};
  const idle = clips.idle || {};
  return {
    faceDeadzone: anim.faceDeadzone,
    faceBucket: anim.faceBucket,
    faceHysteresis: anim.faceHysteresis,
    blinkEvery: anim.blinkEvery,
    fps: anim.fps,
    footV: anim.footV,
    height: anim.height,
    shadowRadius: anim.shadowRadius,
    alphaTest: anim.alphaTest,
    waveNpcRadius: anim.waveNpcRadius,
    waveZoneExtra: anim.waveZoneExtra,
    baseUrl: anim.baseUrl,
    clips: {
      walk: {
        left: copyList(walk.left),
        right: copyList(walk.right),
        front: copyList(walk.front),
        back: copyList(walk.back)
      },
      idle: {
        breath: copyList(idle.breath),
        blink: copyList(idle.blink),
        backBreath: copyList(idle.backBreath),
        backBlink: copyList(idle.backBlink)
      },
      wave: copyList(clips.wave)
    }
  };
}

// THREE 由呼叫端傳入，這個檔本身不 import three，node:test 才能載入純邏輯。
export function createMiluView(THREE, options) {
  const source = (options && options.anim) || MILU_ANIM;
  const anim = liveAnimFrom(source);
  const baseUrl = (options && options.baseUrl) || source.baseUrl || anim.baseUrl;
  const group = new THREE.Group();
  group.name = 'milu';
  const height = anim.height;
  const geo = new THREE.PlaneGeometry(height, height);
  geo.translate(0, feetOffsetY(height, anim.footV), 0);
  const material = new THREE.MeshBasicMaterial({
    transparent: true,
    alphaTest: anim.alphaTest,
    depthWrite: true,
    side: THREE.DoubleSide,
    color: 0xffffff
  });
  const mesh = new THREE.Mesh(geo, material);
  mesh.castShadow = false;
  mesh.receiveShadow = false;
  mesh.renderOrder = 2;
  group.add(mesh);

  const shadow = new THREE.Mesh(
    new THREE.CircleGeometry(anim.shadowRadius, 24),
    new THREE.MeshBasicMaterial({
      color: 0x24180f,
      transparent: true,
      opacity: 0.32,
      depthWrite: false
    })
  );
  shadow.rotation.x = -Math.PI / 2;
  shadow.position.y = 0.015;
  shadow.renderOrder = 1;
  group.add(shadow);

  // shared 是另一隻 milu 已經在載的貼圖表。其他人的分身共用，避免每人各下一份。
  const shared = options && options.shared;
  const textures = shared || new Map();
  const loadMeta = miluLoadMeta(textures, !shared);
  let shown = '';
  let disposed = false;
  let displayResolveCache = { requested: '', size: -1, resolved: '' };

  const deferLoad = !!(options && options.deferLoad) && !shared;

  function isOptional(rel) {
    return !!(loadMeta && loadMeta.optional[rel]);
  }

  function markOptionalMap() {
    if (!loadMeta || loadMeta.optionalMapped) return;
    const groups = collectPaths(anim);
    for (let i = 0; i < groups.optional.length; i++) loadMeta.optional[groups.optional[i]] = true;
    loadMeta.optionalMapped = true;
  }

  function settlePath(rel) {
    if (!loadMeta) return;
    loadMeta.settled.add(rel);
    const parts = miluPartitionLoadPaths(anim);
    if (loadMeta.phase === 'bootstrap') {
      let done = true;
      for (let i = 0; i < parts.bootstrap.length; i++) {
        if (!loadMeta.settled.has(parts.bootstrap[i])) {
          done = false;
          break;
        }
      }
      if (done) {
        loadMeta.phase = 'required';
        enqueuePaths(parts.requiredRest);
      }
    } else if (loadMeta.phase === 'required') {
      const req = collectPaths(anim).required;
      let allReq = true;
      for (let j = 0; j < req.length; j++) {
        if (!loadMeta.settled.has(req[j])) {
          allReq = false;
          break;
        }
      }
      if (allReq) {
        loadMeta.phase = 'optional';
        enqueuePaths(parts.optional);
      }
    }
    syncFailed();
  }

  function syncFailed() {
    if (!loadMeta) return;
    const req = collectPaths(anim).required;
    if (!req.length) {
      loadMeta.failed = false;
      return;
    }
    let allGone = true;
    for (let i = 0; i < req.length; i++) {
      if (!loadMeta.exhausted.has(req[i])) {
        allGone = false;
        break;
      }
    }
    loadMeta.failed = allGone;
  }

  function enqueuePaths(list) {
    if (!loadMeta || !list) return;
    for (let i = 0; i < list.length; i++) {
      const rel = list[i];
      if (!rel || textures.has(rel) || loadMeta.exhausted.has(rel)) continue;
      if (loadMeta.queue.indexOf(rel) < 0 && !loadMeta.inFlight.has(rel)) loadMeta.queue.push(rel);
    }
  }

  function pumpQueue() {
    if (!loadMeta || disposed) return;
    while (loadMeta.inFlight.size < MILU_LOAD.maxConcurrent && loadMeta.queue.length > 0) {
      let picked = -1;
      for (let i = 0; i < loadMeta.queue.length; i++) {
        const rel = loadMeta.queue[i];
        if (textures.has(rel) || loadMeta.exhausted.has(rel) || loadMeta.inFlight.has(rel)) continue;
        picked = i;
        break;
      }
      if (picked < 0) {
        loadMeta.queue = loadMeta.queue.filter((rel) => {
          return !textures.has(rel) && !loadMeta.exhausted.has(rel) && !loadMeta.inFlight.has(rel);
        });
        break;
      }
      beginLoad(loadMeta.queue.splice(picked, 1)[0]);
    }
  }

  function beginLoad(rel) {
    if (!loadMeta || disposed || textures.has(rel)) return;
    loadMeta.inFlight.add(rel);
    const failures = loadMeta.attempts.get(rel) || 0;
    const url = miluTextureUrl(baseUrl, rel, failures);
    const loader = new THREE.TextureLoader();
    loader.load(url, (tex) => {
      loadMeta.inFlight.delete(rel);
      if (disposed) {
        tex.dispose();
        pumpQueue();
        return;
      }
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.magFilter = THREE.LinearFilter;
      tex.minFilter = THREE.LinearFilter;
      tex.generateMipmaps = false;
      textures.set(rel, tex);
      loadMeta.settled.add(rel);
      settlePath(rel);
      if (shown) refreshShown();
      pumpQueue();
    }, undefined, () => {
      loadMeta.inFlight.delete(rel);
      if (disposed) return;
      const count = (loadMeta.attempts.get(rel) || 0) + 1;
      loadMeta.attempts.set(rel, count);
      if (miluShouldRetry(count, MILU_LOAD.maxRetries)) {
        const delay = miluRetryDelayMs(count - 1);
        const timer = setTimeout(() => {
          if (disposed || textures.has(rel) || loadMeta.exhausted.has(rel)) return;
          if (loadMeta.queue.indexOf(rel) < 0) loadMeta.queue.push(rel);
          pumpQueue();
        }, delay);
        loadMeta.timers.push(timer);
      } else {
        loadMeta.exhausted.add(rel);
        if (isOptional(rel)) forgetOptionalClip(anim, rel);
        settlePath(rel);
      }
      pumpQueue();
    });
  }

  function startTextureLoads() {
    if (shared || disposed || !loadMeta || loadMeta.started) return;
    loadMeta.started = true;
    markOptionalMap();
    const parts = miluPartitionLoadPaths(anim);
    enqueuePaths(parts.bootstrap);
    pumpQueue();
  }

  if (!shared && !deferLoad) startTextureLoads();

  function refreshShown() {
    if (!shown) return;
    const hit = miluResolveForShow(shown, anim, textures, displayResolveCache);
    displayResolveCache = hit.cache;
    const path = hit.path;
    const tex = path ? textures.get(path) : null;
    if (!tex) return;
    if (path === shown && material.map === tex) return;
    material.map = tex;
    material.needsUpdate = true;
    shown = path;
  }

  function show(path) {
    if (disposed || !path) return;
    if (loadMeta && loadMeta.failed && textures.size === 0) return;
    const hit = miluResolveForShow(path, anim, textures, displayResolveCache);
    displayResolveCache = hit.cache;
    const resolved = hit.path;
    if (!resolved) return;
    const tex = textures.get(resolved);
    if (!tex) return;
    if (resolved === shown && material.map === tex) return;
    material.map = tex;
    material.needsUpdate = true;
    shown = resolved;
  }

  // 只轉 yaw，看板保持直立面向鏡頭。lookAt 會連俯仰一起轉。
  function faceCamera(camera, x, z) {
    const dx = camera.position.x - x;
    const dz = camera.position.z - z;
    group.rotation.y = Math.atan2(dx, dz);
    group.rotation.x = 0;
    group.rotation.z = 0;
  }

  // 共用貼圖留給還在場上的 milu。這隻自己的幾何和材質一定釋放。
  function dispose() {
    if (disposed) return;
    disposed = true;
    if (loadMeta) {
      for (let i = 0; i < loadMeta.timers.length; i++) clearTimeout(loadMeta.timers[i]);
      loadMeta.timers.length = 0;
      loadMeta.inFlight.clear();
      loadMeta.queue.length = 0;
    }
    material.map = null;
    material.dispose();
    geo.dispose();
    shadow.geometry.dispose();
    shadow.material.dispose();
    if (!shared) {
      textures.forEach((tex) => tex.dispose());
      textures.clear();
      textures._miluLoad = null;
    }
  }

  function ready() {
    return textures.size > 0 && !failed();
  }

  function failed() {
    return !!(loadMeta && loadMeta.failed);
  }

  return {
    object: group,
    show: show,
    faceCamera: faceCamera,
    anim: () => anim,
    beginLoad: startTextureLoads,
    ready: ready,
    failed: failed,
    sharedTextures: () => textures,
    dispose: dispose
  };
}
