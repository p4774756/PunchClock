// 探索模式 milu 看板：朝向、幀與貼地計算不依賴 Three.js／DOM，供 node:test。
// 網頁只在移動中換左／右（或之後補上的前／後）；停下播呼吸，偶爾眨眼，靠近 NPC 或互動區揮一次手。
// 圖檔沿用桌面端 /pet/milu，由伺服器靜態路徑提供，不在這裡複製第二份。

export const MILU_ANIM = Object.freeze({
  // 400×400 透明圖，腳底約在底邊往上 41px（桌面端 ANCHOR_Y 359）。flipY 時圖的底邊是 v=0。
  footV: 0.1025,
  height: 1.42,
  shadowRadius: 0.34,
  alphaTest: 0.5,
  faceDeadzone: 0.22,
  waveNpcRadius: 1.75,
  waveZoneExtra: 0.55,
  fps: Object.freeze({ walk: 8, idle: 5, blink: 10, wave: 8 }),
  blinkEvery: 3.4,
  baseUrl: '/pet/milu/',
  clips: Object.freeze({
    walk: Object.freeze({
      left: Object.freeze(['walk/left_01.png', 'walk/left_02.png', 'walk/left_03.png', 'walk/left_04.png']),
      right: Object.freeze(['walk/right_01.png', 'walk/right_02.png', 'walk/right_03.png', 'walk/right_04.png']),
      front: Object.freeze([]),
      back: Object.freeze([])
    }),
    idle: Object.freeze({
      breath: Object.freeze(['idle/breath_01.png', 'idle/breath_02.png', 'idle/breath_03.png', 'idle/breath_04.png']),
      blink: Object.freeze(['idle/blink_01.png', 'idle/blink_02.png'])
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

// 水平分量小於 deadzone 時沿用上次朝向。垂直為主且前／後圖存在才改用那組，否則退回左／右。
export function resolveFacing(prev, screenX, screenZ, anim) {
  const cfg = anim || MILU_ANIM;
  const dead = cfg.faceDeadzone;
  const sx = screenX || 0;
  const sz = screenZ || 0;
  const ax = Math.abs(sx);
  const az = Math.abs(sz);
  let facing = prev === 'left' || prev === 'right' || prev === 'front' || prev === 'back' ? prev : 'right';
  if (az > ax) {
    const vertical = sz < 0 ? 'front' : 'back';
    if (clipOf(cfg, vertical)) return vertical;
  }
  if (ax < dead) {
    if ((facing === 'front' || facing === 'back') && clipOf(cfg, facing)) return facing;
    return sideFacing(facing);
  }
  return sx < 0 ? 'left' : 'right';
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

function framesFor(anim, state) {
  if (state.mode === 'wave') return anim.clips.wave;
  if (state.mode === 'walk') {
    return clipOf(anim, state.facing) || anim.clips.walk[sideFacing(state.facing)] || [];
  }
  if (state.blinking) return anim.clips.idle.blink;
  return anim.clips.idle.breath;
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
    const facing = resolveFacing(next.facing, input.screenX, input.screenZ, anim);
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
    if (next.blinkAcc >= anim.blinkEvery) {
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
          next.facing = resolveFacing(next.facing, input.screenX, input.screenZ, anim);
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

function uniquePaths(anim) {
  const out = [];
  const seen = Object.create(null);
  function add(list) {
    if (!list) return;
    for (let i = 0; i < list.length; i++) {
      if (!list[i] || seen[list[i]]) continue;
      seen[list[i]] = true;
      out.push(list[i]);
    }
  }
  const walk = anim.clips.walk;
  add(walk.left);
  add(walk.right);
  add(walk.front);
  add(walk.back);
  add(anim.clips.idle.breath);
  add(anim.clips.idle.blink);
  add(anim.clips.wave);
  return out;
}

// THREE 由呼叫端傳入，這個檔本身不 import three，node:test 才能載入純邏輯。
export function createMiluView(THREE, options) {
  const anim = (options && options.anim) || MILU_ANIM;
  const baseUrl = (options && options.baseUrl) || anim.baseUrl;
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
  const paths = uniquePaths(anim);
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
      failed = true;
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
    ready: () => loaded && !failed && !!material.map,
    failed: () => failed
  };
}
