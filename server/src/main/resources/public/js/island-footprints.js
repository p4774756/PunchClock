import { G } from './island-kit.js';

/**
 * 純邏輯腳印軌跡：固定池循環重用，左右交替。
 * shoreDist < 0.6 時用較短 shoreLife（像被浪沖掉）。
 */
export function createFootprintTrail(opts) {
  opts = opts || {};
  const max = opts.max == null ? 40 : opts.max;
  const stride = opts.stride == null ? 0.34 : opts.stride;
  const life = opts.life == null ? 6 : opts.life;
  const shoreLife = opts.shoreLife == null ? 2 : opts.shoreLife;
  const halfWidth = opts.halfWidth == null ? 0.11 : opts.halfWidth;

  const prints = [];
  for (let i = 0; i < max; i++) {
    prints.push({
      x: 0,
      z: 0,
      yaw: 0,
      age: 0,
      life: life,
      alive: false,
      foot: 0,
      opacity: 0
    });
  }

  let cursor = 0;
  let accum = 0;
  let nextFoot = 0;
  let lastX = null;
  let lastZ = null;

  function clear() {
    for (let i = 0; i < prints.length; i++) {
      prints[i].alive = false;
      prints[i].opacity = 0;
      prints[i].age = 0;
    }
    accum = 0;
    nextFoot = 0;
    lastX = null;
    lastZ = null;
  }

  function visibleList() {
    const out = [];
    for (let i = 0; i < prints.length; i++) {
      const p = prints[i];
      if (!p.alive) continue;
      out.push({
        x: p.x,
        z: p.z,
        yaw: p.yaw,
        opacity: p.opacity,
        foot: p.foot,
        age: p.age,
        life: p.life
      });
    }
    return out;
  }

  function spawn(state) {
    const yaw = state.yaw || 0;
    const side = nextFoot === 0 ? -1 : 1;
    nextFoot = 1 - nextFoot;
    // 前進 (sin, cos)；左右垂直偏移 (±cos, ∓sin)
    const ox = Math.cos(yaw) * halfWidth * side;
    const oz = -Math.sin(yaw) * halfWidth * side;
    const shore = state.shoreDist != null && state.shoreDist < 0.6;
    const p = prints[cursor];
    cursor = (cursor + 1) % max;
    p.x = state.x + ox;
    p.z = state.z + oz;
    p.yaw = yaw;
    p.age = 0;
    p.life = shore ? shoreLife : life;
    p.alive = true;
    p.foot = side;
    p.opacity = 1;
  }

  function step(state, dt) {
    const reduced = !!(state && state.reduced);
    const dtSafe = dt > 0 ? dt : 0;

    for (let i = 0; i < prints.length; i++) {
      const p = prints[i];
      if (!p.alive) continue;
      p.age += dtSafe;
      if (p.age >= p.life) {
        p.alive = false;
        p.opacity = 0;
      } else if (reduced) {
        p.opacity = 1;
      } else {
        p.opacity = 1 - p.age / p.life;
      }
    }

    if (!state || !state.onSand || !state.moving) {
      accum = 0;
      if (state) {
        lastX = state.x;
        lastZ = state.z;
      }
      return visibleList();
    }

    if (lastX == null || lastZ == null) {
      lastX = state.x;
      lastZ = state.z;
      return visibleList();
    }

    const moved = Math.hypot(state.x - lastX, state.z - lastZ);
    lastX = state.x;
    lastZ = state.z;
    accum += moved;
    while (accum >= stride) {
      accum -= stride;
      spawn(state);
    }
    return visibleList();
  }

  return {
    step: step,
    clear: clear,
    get prints() { return prints; },
    get max() { return max; },
    get stride() { return stride; },
    get life() { return life; },
    get shoreLife() { return shoreLife; }
  };
}

/** 畫面：固定數量小平面池，貼在沙面上方。 */
export function createFootprintView(THREE, hooks) {
  const kit = hooks.kit;
  const scene = hooks.scene;
  const trail = createFootprintTrail(hooks.trailOpts || {});
  const max = trail.max;
  const group = new THREE.Group();
  group.name = 'footprints';
  const geo = new THREE.PlaneGeometry(0.14, 0.26);
  const meshes = [];

  for (let i = 0; i < max; i++) {
    const material = new THREE.MeshLambertMaterial({
      color: 0xc4a878,
      flatShading: true,
      transparent: true,
      opacity: 0,
      depthWrite: false
    });
    material.polygonOffset = true;
    material.polygonOffsetFactor = -2;
    material.polygonOffsetUnits = -2;
    material.userData.day = new THREE.Color(0xc4a878);
    material.userData.night = new THREE.Color(0x6a5a42);
    material.userData.dayE = 0;
    material.userData.nightE = 0;
    kit.addMaterial(material);
    const mesh = new THREE.Mesh(geo, material);
    mesh.rotation.x = -Math.PI / 2;
    mesh.position.y = G + 0.018;
    mesh.visible = false;
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    group.add(mesh);
    meshes.push(mesh);
  }
  scene.add(group);

  let hidden = false;

  function syncMeshes(list) {
    for (let i = 0; i < max; i++) {
      const mesh = meshes[i];
      const p = list[i];
      if (!p || hidden) {
        mesh.visible = false;
        mesh.material.opacity = 0;
        continue;
      }
      mesh.visible = p.opacity > 0.02;
      mesh.position.x = p.x;
      mesh.position.z = p.z;
      mesh.rotation.z = -(p.yaw || 0);
      mesh.material.opacity = Math.max(0, Math.min(1, p.opacity));
    }
  }

  function step(state, dt) {
    if (hidden) {
      trail.clear();
      syncMeshes([]);
      return [];
    }
    const list = trail.step(state, dt);
    // 依 prints 池順序對齊，方便固定 mesh 索引
    const ordered = [];
    const raw = trail.prints;
    for (let i = 0; i < raw.length; i++) {
      const p = raw[i];
      if (!p.alive) continue;
      ordered.push(p);
    }
    syncMeshes(ordered);
    return list;
  }

  function clear() {
    trail.clear();
    syncMeshes([]);
  }

  function setHidden(value) {
    hidden = !!value;
    if (hidden) clear();
    else group.visible = true;
    group.visible = !hidden;
  }

  return {
    trail: trail,
    group: group,
    step: step,
    clear: clear,
    setHidden: setHidden
  };
}
