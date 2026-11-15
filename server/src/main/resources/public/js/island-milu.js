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
  height: 1.42,
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

  const textures = new Map();
  let pending = 0;
  let failed = false;
  let loaded = false;
  let shown = '';

  function finishOne() {
    pending -= 1;
    if (pending <= 0 && !failed) loaded = true;
  }

  const loader = new THREE.TextureLoader();
  const groups = collectPaths(anim);
  const paths = groups.required.concat(groups.optional);
  const optional = Object.create(null);
  for (let i = 0; i < groups.optional.length; i++) optional[groups.optional[i]] = true;
  pending = paths.length;
  if (!pending) loaded = true;
  paths.forEach((rel) => {
    loader.load(baseUrl + rel, (tex) => {
      tex.colorSpace = THREE.SRGBColorSpace;
      tex.magFilter = THREE.LinearFilter;
      tex.minFilter = THREE.LinearFilter;
      tex.generateMipmaps = false;
      textures.set(rel, tex);
      finishOne();
    }, undefined, () => {
      if (optional[rel]) forgetOptionalClip(anim, rel);
      else failed = true;
      finishOne();
    });
  });

  function show(path) {
    if (!path || failed) return;
    const tex = textures.get(path);
    if (!tex || path === shown) return;
    material.map = tex;
    material.needsUpdate = true;
    shown = path;
  }

  // 只轉 yaw，看板保持直立面向鏡頭。lookAt 會連俯仰一起轉。
  function faceCamera(camera, x, z) {
    const dx = camera.position.x - x;
    const dz = camera.position.z - z;
    group.rotation.y = Math.atan2(dx, dz);
    group.rotation.x = 0;
    group.rotation.z = 0;
  }

  return {
    object: group,
    show: show,
    faceCamera: faceCamera,
    anim: () => anim,
    ready: () => loaded && !failed && !!material.map,
    failed: () => failed
  };
}
