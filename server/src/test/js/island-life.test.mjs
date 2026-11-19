// 海空生物：node --test server/src/test/js/island-life.test.mjs
import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { join, dirname, basename } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(
  dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
  'main',
  'resources',
  'public'
);

const life = await loadPublicModule('island-life.js');
const {
  LIFE_MAX_CREATURES,
  LIFE_SEA_Y,
  LIFE_SKY_BASE,
  validateLifePlacements,
  lifePlacementAnchors,
  lifePlacementAllowed,
  lifeInstanceBudget,
  lifeRareKindAt,
  lifeGlowStrength,
  lifeKindActive,
  lifeCreatureGlow,
  lifeUpdateTick,
  createIslandLife,
  sampleFishSchool,
  distanceToBoatRoutes,
  LIFE_GLB_KINDS,
  lifeModelPath,
  LIFE_CREATURE_KINDS,
  validateLifePanoramaGlbPlacements,
  LIFE_PANORAMA_CAMERA,
  LIFE_PANORAMA_VIEWPORT,
  prepareLifeMaterials
} = life;

function mockThreeLite() {
  return {
    FrontSide: 0,
    Group: class {
      constructor() {
        this.children = [];
        this.position = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.rotation = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.visible = true;
        this.userData = {};
        this.scale = { setScalar() {} };
      }
      add(c) { this.children.push(c); }
      remove(c) {
        const i = this.children.indexOf(c);
        if (i >= 0) this.children.splice(i, 1);
      }
      traverse(fn) { fn(this); this.children.forEach(fn); }
    },
    MeshLambertMaterial: class {
      constructor(opts) {
        Object.assign(this, opts);
        this.userData = { dayE: 0, nightE: 0 };
        this.emissiveIntensity = 0;
      }
      dispose() {}
    },
    Mesh: class {
      constructor(geo, mat) {
        this.geometry = geo;
        this.material = mat;
        this.position = { set() {} };
        this.rotation = { set() {} };
        this.scale = { set() {}, setScalar() {} };
        this.children = [];
        this.userData = {};
        this.visible = true;
      }
      add(c) { this.children.push(c); }
    },
    SphereGeometry: class { dispose() {} },
    BoxGeometry: class { dispose() {} },
    CylinderGeometry: class { dispose() {} },
    ConeGeometry: class { dispose() {} },
    Color: class {
      constructor(v) { this.v = v; }
      clone() { return new this.constructor(this.v); }
      lerp() {}
    }
  };
}

describe('island-life 錨點', () => {
  test('全部錨點通過放置檢查', () => {
    const v = validateLifePlacements();
    assert.equal(v.ok, true, v.problems.join('; '));
  });

  test('錨點數量不超過上限', () => {
    assert.ok(lifePlacementAnchors().length <= LIFE_MAX_CREATURES);
  });

  test('每個錨點不在可行走區與航線上', () => {
    for (const a of lifePlacementAnchors()) {
      assert.ok(lifePlacementAllowed(a.x, a.z), `${a.kind} @ ${a.x},${a.z}`);
      assert.ok(distanceToBoatRoutes(a.x, a.z) >= 3.2);
    }
  });
});

describe('island-life reduced', () => {
  test('reduced 時預算縮小', () => {
    const b = lifeInstanceBudget(true, false);
    assert.equal(b.rare, 0);
    assert.ok(b.schools <= 1);
  });

  test('reduced 時稀有輪播停止', () => {
    assert.equal(lifeRareKindAt(100, true), null);
  });

  test('reduced 時夜間發光為 0', () => {
    assert.equal(lifeGlowStrength('jellyfish', 1, true), 0);
    assert.equal(lifeGlowStrength('phoenix', 1, true), 0);
  });

  test('reduced 時 update tick 不播動畫', () => {
    const tick = lifeUpdateTick(true, 0.016, 10);
    assert.equal(tick.animate, false);
    assert.equal(tick.dt, 0);
  });

  test('reduced 時神話生物不啟用', () => {
    for (const kind of ['kunpeng', 'kitsune', 'xuanwu', 'mermaid']) {
      assert.equal(lifeKindActive(kind, 1, true, 200, {}), false);
    }
  });
});

describe('island-life 日夜', () => {
  test('水母夜間發光標記', () => {
    assert.ok(lifeCreatureGlow('jellyfish'));
    assert.ok(lifeCreatureGlow('phoenix'));
    assert.equal(lifeCreatureGlow('fishSchool'), false);
  });

  test('水母白天不發光、夜間有強度', () => {
    assert.equal(lifeGlowStrength('jellyfish', 0.2, false), 0);
    assert.ok(lifeGlowStrength('jellyfish', 0.9, false) > 0.3);
  });

  test('夜晚鳳凰可見、白天不可', () => {
    assert.equal(lifeKindActive('phoenix', 0.3, false, 0, {}), false);
    assert.equal(lifeKindActive('phoenix', 0.85, false, 0, {}), true);
  });

  test('魚群與鳥群白天常駐', () => {
    assert.equal(lifeKindActive('fishSchool', 0.1, false, 0, {}), true);
    assert.equal(lifeKindActive('birds', 0.1, false, 0, {}), true);
  });
});

describe('island-life 稀有一輪一隻', () => {
  test('同一時刻只啟用一種稀有', () => {
    const active = new Set();
    for (let e = 0; e < 500; e += 12) {
      active.add(lifeRareKindAt(e, false));
    }
    assert.ok(active.size > 3, '稀有輪播應涵蓋多種');
    for (let e = 0; e < 200; e += 0.5) {
      let count = 0;
      const rare = lifeRareKindAt(e, false);
      for (const a of lifePlacementAnchors()) {
        if (a.kind === rare) count++;
      }
      assert.ok(count <= 2);
    }
  });

  test('forceAll 示範時全部可見', () => {
    assert.equal(lifeKindActive('kunpeng', 0, false, 0, { forceAll: true }), true);
  });

  test('forceRare 示範可鎖定單一傳說物', () => {
    assert.equal(lifeKindActive('xuanwu', 0, false, 0, { forceRare: 'xuanwu' }), true);
    assert.equal(lifeKindActive('kunpeng', 0, false, 0, { forceRare: 'xuanwu' }), false);
    assert.equal(lifeKindActive('whale', 0, false, 0, { forceRare: 'xuanwu' }), false);
    assert.equal(lifeKindActive('fishSchool', 0.1, false, 0, { forceRare: 'whale' }), true);
    assert.equal(lifeKindActive('phoenix', 0.9, false, 0, { forceRare: 'whale' }), false);
  });
});

describe('island-life 純函式', () => {
  test('魚群路徑循環', () => {
    const anchor = { x: -14.5, z: 20.5, radius: 2.6 };
    const p0 = sampleFishSchool(anchor, 0, false);
    const p1 = sampleFishSchool(anchor, 10, false);
    assert.notStrictEqual(p0.x, p1.x);
    assert.ok(Math.hypot(p0.x - anchor.x, p0.z - anchor.z) <= anchor.radius + 0.01);
  });

  test('海面高度常數合理', () => {
    assert.ok(LIFE_SEA_Y < LIFE_SKY_BASE);
    assert.ok(LIFE_SEA_Y < -0.5);
  });
});

describe('island-life 全景投影 (#226 r2)', () => {
  test('GLB 錨點在 1280×720 全景內且避開島標籤與 HUD', () => {
    const v = validateLifePanoramaGlbPlacements();
    assert.equal(v.ok, true, v.problems.join('; '));
  });

  test('投影函式使用指定相機參數', () => {
    assert.equal(LIFE_PANORAMA_CAMERA.eye.x, 58.1);
    assert.equal(LIFE_PANORAMA_VIEWPORT.width, 1280);
    assert.equal(LIFE_PANORAMA_CAMERA.fovDeg, 25);
  });
});

const LIFE_GLB_FILES = Object.freeze({
  fishSchool: 'fish.glb',
  whale: 'whale.glb',
  octopus: 'octopus.glb',
  wenyao: 'wenyao.glb',
  xuanwu: 'xuanwu.glb',
  birds: 'seagull.glb',
  kunpeng: 'kunpeng.glb'
});

function readGlbJson(filePath) {
  const buf = readFileSync(filePath);
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  assert.equal(view.getUint32(0, true), 0x46546C67, 'glTF magic');
  const jsonLen = view.getUint32(12, true);
  const jsonText = new TextDecoder().decode(buf.subarray(20, 20 + jsonLen));
  return JSON.parse(jsonText);
}

function mat4Identity() {
  return new Float64Array([1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 1]);
}

function mat4Multiply(a, b) {
  const o = new Float64Array(16);
  for (let col = 0; col < 4; col++) {
    for (let row = 0; row < 4; row++) {
      let sum = 0;
      for (let k = 0; k < 4; k++) sum += a[k * 4 + row] * b[col * 4 + k];
      o[col * 4 + row] = sum;
    }
  }
  return o;
}

function quatToMat4(q) {
  const [x, y, z, w] = q;
  const xx = x * x; const yy = y * y; const zz = z * z;
  const xy = x * y; const xz = x * z; const yz = y * z;
  const wx = w * x; const wy = w * y; const wz = w * z;
  const m = mat4Identity();
  m[0] = 1 - 2 * (yy + zz);
  m[4] = 2 * (xy - wz);
  m[8] = 2 * (xz + wy);
  m[1] = 2 * (xy + wz);
  m[5] = 1 - 2 * (xx + zz);
  m[9] = 2 * (yz - wx);
  m[2] = 2 * (xz - wy);
  m[6] = 2 * (yz + wx);
  m[10] = 1 - 2 * (xx + yy);
  return m;
}

function nodeLocalMatrix(node) {
  const m = mat4Identity();
  if (node.matrix && node.matrix.length === 16) {
    return Float64Array.from(node.matrix);
  }
  const t = node.translation || [0, 0, 0];
  const s = node.scale || [1, 1, 1];
  const r = node.rotation || [0, 0, 0, 1];
  const rm = quatToMat4(r);
  rm[12] = t[0] * s[0];
  rm[13] = t[1] * s[1];
  rm[14] = t[2] * s[2];
  m[0] *= s[0]; m[5] *= s[1]; m[10] *= s[2];
  return mat4Multiply(rm, m);
}

function transformPoint(m, x, y, z) {
  const px = m[0] * x + m[4] * y + m[8] * z + m[12];
  const py = m[1] * x + m[5] * y + m[9] * z + m[13];
  const pz = m[2] * x + m[6] * y + m[10] * z + m[14];
  return [px, py, pz];
}

function glbNodeBoundsFiltered(gltf, binChunk, skipNames) {
  const nodes = gltf.nodes || [];
  const meshes = gltf.meshes || [];
  const accessors = gltf.accessors || [];
  const bufferViews = gltf.bufferViews || [];
  const world = new Array(nodes.length);
  const visited = new Set();

  function dfs(i, parent) {
    if (visited.has(i)) return;
    visited.add(i);
    const local = nodeLocalMatrix(nodes[i]);
    world[i] = parent ? mat4Multiply(parent, local) : local;
    for (const c of nodes[i].children || []) dfs(c, world[i]);
  }
  for (const sc of gltf.scenes || [{ nodes: [0] }]) {
    for (const root of sc.nodes || []) dfs(root, null);
  }

  let minX = Infinity; let minY = Infinity; let minZ = Infinity;
  let maxX = -Infinity; let maxY = -Infinity; let maxZ = -Infinity;

  for (let ni = 0; ni < nodes.length; ni++) {
    const node = nodes[ni];
    if (node.mesh == null || world[ni] == null) continue;
    if (skipNames && skipNames.has(node.name || '')) continue;
    const mesh = meshes[node.mesh];
    if (!mesh) continue;
    for (const prim of mesh.primitives || []) {
      const posAcc = accessors[prim.attributes.POSITION];
      if (!posAcc) continue;
      const bv = bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const stride = bv.byteStride || 12;
      const dv = new DataView(binChunk.buffer, binChunk.byteOffset + byteOffset, bv.byteLength);
      for (let vi = 0; vi < posAcc.count; vi++) {
        const off = vi * stride;
        const [wx, wy, wz] = transformPoint(world[ni], dv.getFloat32(off, true), dv.getFloat32(off + 4, true), dv.getFloat32(off + 8, true));
        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
      }
    }
  }
  return { minY, maxY, sx: maxX - minX, sy: maxY - minY, sz: maxZ - minZ };
}

function glbNodeBounds(gltf, binChunk) {
  const nodes = gltf.nodes || [];
  const meshes = gltf.meshes || [];
  const accessors = gltf.accessors || [];
  const bufferViews = gltf.bufferViews || [];
  const world = new Array(nodes.length);
  const visited = new Set();

  function dfs(i, parent) {
    if (visited.has(i)) return;
    visited.add(i);
    const local = nodeLocalMatrix(nodes[i]);
    world[i] = parent ? mat4Multiply(parent, local) : local;
    for (const c of nodes[i].children || []) dfs(c, world[i]);
  }
  for (const sc of gltf.scenes || [{ nodes: [0] }]) {
    for (const root of sc.nodes || []) dfs(root, null);
  }

  let minX = Infinity; let minY = Infinity; let minZ = Infinity;
  let maxX = -Infinity; let maxY = -Infinity; let maxZ = -Infinity;
  let tris = 0;

  for (let ni = 0; ni < nodes.length; ni++) {
    const node = nodes[ni];
    if (node.mesh == null || world[ni] == null) continue;
    const mesh = meshes[node.mesh];
    if (!mesh) continue;
    for (const prim of mesh.primitives || []) {
      const posAcc = accessors[prim.attributes.POSITION];
      if (!posAcc) continue;
      const bv = bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const stride = bv.byteStride || 12;
      const dv = new DataView(binChunk.buffer, binChunk.byteOffset + byteOffset, bv.byteLength);
      for (let vi = 0; vi < posAcc.count; vi++) {
        const off = vi * stride;
        const x = dv.getFloat32(off, true);
        const y = dv.getFloat32(off + 4, true);
        const z = dv.getFloat32(off + 8, true);
        const [wx, wy, wz] = transformPoint(world[ni], x, y, z);
        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
      }
      if (prim.indices != null) {
        const iacc = accessors[prim.indices];
        tris += Math.floor(iacc.count / 3);
      } else {
        tris += Math.floor(posAcc.count / 3);
      }
    }
  }
  const sx = maxX - minX; const sy = maxY - minY; const sz = maxZ - minZ;
  return { sx, sy, sz, minY, maxY, tris };
}

function glbMeshNodeBounds(gltf, binChunk, nodeName) {
  const nodes = gltf.nodes || [];
  const meshes = gltf.meshes || [];
  const accessors = gltf.accessors || [];
  const bufferViews = gltf.bufferViews || [];
  const world = new Array(nodes.length);
  const visited = new Set();

  function dfs(i, parent) {
    if (visited.has(i)) return;
    visited.add(i);
    const local = nodeLocalMatrix(nodes[i]);
    world[i] = parent ? mat4Multiply(parent, local) : local;
    for (const c of nodes[i].children || []) dfs(c, world[i]);
  }
  for (const sc of gltf.scenes || [{ nodes: [0] }]) {
    for (const root of sc.nodes || []) dfs(root, null);
  }

  let minX = Infinity; let minY = Infinity; let minZ = Infinity;
  let maxX = -Infinity; let maxY = -Infinity; let maxZ = -Infinity;

  for (let ni = 0; ni < nodes.length; ni++) {
    const node = nodes[ni];
    if ((node.name || '') !== nodeName || node.mesh == null || world[ni] == null) continue;
    const mesh = meshes[node.mesh];
    if (!mesh) continue;
    for (const prim of mesh.primitives || []) {
      const posAcc = accessors[prim.attributes.POSITION];
      if (!posAcc) continue;
      const bv = bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const stride = bv.byteStride || 12;
      const dv = new DataView(binChunk.buffer, binChunk.byteOffset + byteOffset, bv.byteLength);
      for (let vi = 0; vi < posAcc.count; vi++) {
        const off = vi * stride;
        const x = dv.getFloat32(off, true);
        const y = dv.getFloat32(off + 4, true);
        const z = dv.getFloat32(off + 8, true);
        const [wx, wy, wz] = transformPoint(world[ni], x, y, z);
        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
      }
    }
  }
  return { sx: maxX - minX, sy: maxY - minY, sz: maxZ - minZ };
}

function glbMeshWorldBounds(gltf, binChunk, nodeName) {
  const nodes = gltf.nodes || [];
  const meshes = gltf.meshes || [];
  const accessors = gltf.accessors || [];
  const bufferViews = gltf.bufferViews || [];
  const world = new Array(nodes.length);
  const visited = new Set();
  function dfs(i, parent) {
    if (visited.has(i)) return;
    visited.add(i);
    const local = nodeLocalMatrix(nodes[i]);
    world[i] = parent ? mat4Multiply(parent, local) : local;
    for (const c of nodes[i].children || []) dfs(c, world[i]);
  }
  for (const sc of gltf.scenes || [{ nodes: [0] }]) {
    for (const root of sc.nodes || []) dfs(root, null);
  }
  let minX = Infinity; let minY = Infinity; let minZ = Infinity;
  let maxX = -Infinity; let maxY = -Infinity; let maxZ = -Infinity;
  let count = 0;
  for (let ni = 0; ni < nodes.length; ni++) {
    const node = nodes[ni];
    if ((node.name || '') !== nodeName || node.mesh == null || world[ni] == null) continue;
    const mesh = meshes[node.mesh];
    if (!mesh) continue;
    for (const prim of mesh.primitives || []) {
      const posAcc = accessors[prim.attributes.POSITION];
      if (!posAcc) continue;
      const bv = bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const stride = bv.byteStride || 12;
      const dv = new DataView(binChunk.buffer, binChunk.byteOffset + byteOffset, bv.byteLength);
      for (let vi = 0; vi < posAcc.count; vi++) {
        const off = vi * stride;
        const [wx, wy, wz] = transformPoint(
          world[ni], dv.getFloat32(off, true), dv.getFloat32(off + 4, true), dv.getFloat32(off + 8, true)
        );
        minX = Math.min(minX, wx); minY = Math.min(minY, wy); minZ = Math.min(minZ, wz);
        maxX = Math.max(maxX, wx); maxY = Math.max(maxY, wy); maxZ = Math.max(maxZ, wz);
        count += 1;
      }
    }
  }
  return {
    minX, minY, minZ, maxX, maxY, maxZ,
    sx: maxX - minX, sy: maxY - minY, sz: maxZ - minZ,
    count
  };
}

function collectMeshVerts(gltf, binChunk, names, step = 2) {
  const out = [];
  for (const nm of names) {
    const nodes = gltf.nodes || [];
    const meshes = gltf.meshes || [];
    const accessors = gltf.accessors || [];
    const bufferViews = gltf.bufferViews || [];
    const world = new Array(nodes.length);
    const visited = new Set();
    function dfs(i, parent) {
      if (visited.has(i)) return;
      visited.add(i);
      const local = nodeLocalMatrix(nodes[i]);
      world[i] = parent ? mat4Multiply(parent, local) : local;
      for (const c of nodes[i].children || []) dfs(c, world[i]);
    }
    for (const sc of gltf.scenes || [{ nodes: [0] }]) {
      for (const root of sc.nodes || []) dfs(root, null);
    }
    for (let ni = 0; ni < nodes.length; ni++) {
      const node = nodes[ni];
      if ((node.name || '') !== nm || node.mesh == null) continue;
      const mesh = meshes[node.mesh];
      const posAcc = accessors[mesh.primitives[0].attributes.POSITION];
      const bv = bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const stride = bv.byteStride || 12;
      const dv = new DataView(binChunk.buffer, binChunk.byteOffset + byteOffset, bv.byteLength);
      for (let vi = 0; vi < posAcc.count; vi += step) {
        const off = vi * stride;
        out.push(transformPoint(
          world[ni], dv.getFloat32(off, true), dv.getFloat32(off + 4, true), dv.getFloat32(off + 8, true)
        ));
      }
    }
  }
  return out;
}

function snakeContactRatio(gltf, binChunk) {
  const turtle = collectMeshVerts(gltf, binChunk, ['xuan_shell', 'xuan_body'], 2);
  const snake = collectMeshVerts(gltf, binChunk, ['snake', 'snake_belly', 'snake_eye'], 3);
  if (!turtle.length || !snake.length) return 0;
  const snakeB = glbMeshNodeBounds(gltf, binChunk, 'snake');
  const half = Math.max(snakeB.sx, snakeB.sz) * 0.22;
  const thresh = Math.max(0.028, half * 0.52);
  let near = 0;
  for (const p of snake) {
    let best = Infinity;
    for (const q of turtle) {
      const dx = p[0] - q[0]; const dy = p[1] - q[1]; const dz = p[2] - q[2];
      best = Math.min(best, Math.sqrt(dx * dx + dy * dy + dz * dz));
    }
    if (best <= thresh) near += 1;
  }
  return near / snake.length;
}

function readGlbBin(filePath) {
  const buf = readFileSync(filePath);
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);
  const jsonLen = view.getUint32(12, true);
  let off = 20 + jsonLen;
  if (off % 4) off += 4 - (off % 4);
  const binLen = view.getUint32(off + 4, true);
  return buf.subarray(off + 8, off + 8 + binLen);
}

function assertLifeGlbContract(filePath, horizontalKinds) {
  const gltf = readGlbJson(filePath);
  const name = basename(filePath);
  for (const node of gltf.nodes || []) {
    assert.notEqual((node.name || '').toLowerCase(), 'armature', name + ': Armature node');
    if (node.scale) {
      for (const s of node.scale) {
        assert.ok(Math.abs(s - 1) < 0.02, `${name} scale ${s}`);
      }
    }
    assert.equal(node.rotation == null || node.rotation.every((v, i) => (i < 3 ? Math.abs(v) < 0.02 : Math.abs(v - 1) < 0.02)), true,
      `${name} node rotation ${node.name}`);
  }
  assert.equal((gltf.animations || []).length, 0, name + ': animations');
  for (const mat of gltf.materials || []) {
    const pbr = mat.pbrMetallicRoughness || mat;
    assert.equal(pbr.baseColorTexture != null, false, name + ' baseColorTexture');
    assert.equal(mat.normalTexture != null, false, name + ' normalTexture');
  }
  const bin = readGlbBin(filePath);
  const b = glbNodeBounds(gltf, bin);
  const skipLong = name === 'whale.glb' ? new Set(['whale_spray']) : null;
  const bLong = skipLong ? glbNodeBoundsFiltered(gltf, bin, skipLong) : b;
  const longest = Math.max(bLong.sx, bLong.sy, bLong.sz);
  assert.ok(longest >= 0.95 && longest <= 1.05, `${name} longest ${longest}`);
  for (const node of gltf.nodes || []) {
    if (node.mesh == null) continue;
    const meshName = node.name || 'mesh';
    const mb = glbMeshNodeBounds(gltf, bin, meshName);
    const mn = Math.min(mb.sx, mb.sy, mb.sz);
    const mx = Math.max(mb.sx, mb.sy, mb.sz);
    assert.ok(mn >= 0.01 - 1e-6, `${name} ${meshName} min axis ${mn}`);
    assert.ok(mx >= 0.05 - 1e-6, `${name} ${meshName} max axis ${mx}`);
  }
  const kindKey = Object.entries(LIFE_GLB_FILES).find(([, f]) => f === name)?.[0];
  if (horizontalKinds && kindKey && horizontalKinds.has(kindKey)) {
    const skip = kindKey === 'whale' ? new Set(['whale_spray']) : null;
    const bb = skip ? glbNodeBoundsFiltered(gltf, bin, skip) : b;
    const ySpan = bb.maxY - bb.minY;
    assert.ok(ySpan <= longest * 0.4 + 0.02, `${name} glTF Y span ${ySpan} vs longest ${longest}`);
  }
  assert.ok(b.tris <= 3000, `${name} tris ${b.tris}`);
  assert.ok(readFileSync(filePath).length <= 300 * 1024, name + ' file size');
  return b;
}

describe('island-life GLB 種類 (#226)', () => {
  test('每個 GLB 種類皆有對應模型檔', () => {
    assert.equal(LIFE_GLB_KINDS.length, 7);
    for (const kind of LIFE_GLB_KINDS) {
      const path = lifeModelPath(kind);
      assert.ok(path && path.startsWith('/models/life/'), kind + ' → ' + path);
      assert.ok(existsSync(join(publicRoot, path)), 'missing ' + path);
    }
    const removed = ['chiru', 'seaSerpent', 'qingniao', 'phoenix', 'wyvern'];
    for (const kind of removed) {
      assert.equal(lifeModelPath(kind), null, kind + ' 不應有 GLB');
    }
  });

  test('每個 GLB 無骨架縮放／無貼圖／正規化最長軸', () => {
    const horizontal = new Set(['fishSchool', 'whale', 'kunpeng', 'wenyao']);
    let totalBytes = 0;
    for (const kind of LIFE_GLB_KINDS) {
      const file = LIFE_GLB_FILES[kind];
      const fp = join(publicRoot, 'models', 'life', file);
      assertLifeGlbContract(fp, horizontal);
      totalBytes += readFileSync(fp).length;
    }
    assert.ok(totalBytes <= 2 * 1024 * 1024, 'life GLB total bytes');
  });

  test('r5 鯤身體／章魚觸手／文鰩尾軸 (#226)', () => {
    const binK = readGlbBin(join(publicRoot, 'models', 'life', 'kunpeng.glb'));
    const gtK = readGlbJson(join(publicRoot, 'models', 'life', 'kunpeng.glb'));
    const back = glbMeshNodeBounds(gtK, binK, 'kun_back');
    const bodyLong = Math.max(back.sx, back.sy, back.sz);
    for (const part of ['kun_clouds', 'kun_fin']) {
      const mb = glbMeshNodeBounds(gtK, binK, part);
      const partLong = Math.max(mb.sx, mb.sy, mb.sz);
      assert.ok(bodyLong + 1e-4 >= partLong, `kunpeng ${part} ${partLong} vs body ${bodyLong}`);
    }
    const binO = readGlbBin(join(publicRoot, 'models', 'life', 'octopus.glb'));
    const gtO = readGlbJson(join(publicRoot, 'models', 'life', 'octopus.glb'));
    const arms = glbMeshNodeBounds(gtO, binO, 'octo_arms');
    assert.ok(arms.sy <= arms.sx + 1e-4 && arms.sy <= arms.sz + 1e-4, `octo_arms Y min ${arms.sy} vs ${arms.sx}×${arms.sz}`);
    const binW = readGlbBin(join(publicRoot, 'models', 'life', 'wenyao.glb'));
    const gtW = readGlbJson(join(publicRoot, 'models', 'life', 'wenyao.glb'));
    const tail = glbMeshNodeBounds(gtW, binW, 'wen_tail');
    const body = glbMeshNodeBounds(gtW, binW, 'wen_body');
    const fishLong = Math.max(body.sx, body.sy, body.sz);
    const tailCtrY = (() => {
      const node = gtW.nodes.find((n) => n.name === 'wen_tail');
      const mesh = gtW.meshes[node.mesh];
      const posAcc = gtW.accessors[mesh.primitives[0].attributes.POSITION];
      const bv = gtW.bufferViews[posAcc.bufferView];
      const byteOffset = (bv.byteOffset || 0) + (posAcc.byteOffset || 0);
      const dv = new DataView(binW.buffer, binW.byteOffset + byteOffset, bv.byteLength);
      let sy = 0;
      for (let i = 0; i < posAcc.count; i++) sy += dv.getFloat32(i * 12 + 4, true);
      return sy / posAcc.count;
    })();
    const bodyMesh = gtW.nodes.find((n) => n.name === 'wen_body');
    const posAccB = gtW.accessors[gtW.meshes[bodyMesh.mesh].primitives[0].attributes.POSITION];
    const bvB = gtW.bufferViews[posAccB.bufferView];
    const dvB = new DataView(binW.buffer, binW.byteOffset + (bvB.byteOffset || 0) + (posAccB.byteOffset || 0), bvB.byteLength);
    let minYB = Infinity; let maxYB = -Infinity;
    for (let i = 0; i < posAccB.count; i++) {
      const y = dvB.getFloat32(i * 12 + 4, true);
      minYB = Math.min(minYB, y); maxYB = Math.max(maxYB, y);
    }
    const bodyEnd = body.sy >= body.sx && body.sy >= body.sz ? minYB : maxYB;
    assert.ok(Math.abs(tailCtrY - bodyEnd) <= fishLong * 0.2 + 0.05, `wenyao tail y=${tailCtrY} vs body end ${bodyEnd}`);
    assert.ok(Math.max(tail.sx, tail.sy, tail.sz) <= fishLong * 0.55 + 0.05, 'wenyao tail size');
  });

  test('r7 玄武蛇／金紋／海鷗 (#226)', () => {
    const binX = readGlbBin(join(publicRoot, 'models', 'life', 'xuanwu.glb'));
    const gtX = readGlbJson(join(publicRoot, 'models', 'life', 'xuanwu.glb'));
    const gold = glbMeshWorldBounds(gtX, binX, 'xuan_gold');
    const shell = glbMeshWorldBounds(gtX, binX, 'xuan_shell');
    const goldLong = Math.max(gold.sx, gold.sy, gold.sz);
    assert.ok(goldLong > 0.05, `xuan_gold bbox ${goldLong}`);
    assert.ok(gold.minX >= shell.minX - 0.03 && gold.maxX <= shell.maxX + 0.03, 'xuan_gold X in shell');
    assert.ok(gold.minZ >= shell.minZ - 0.02, `xuan_gold on shell top minZ ${gold.minZ} shell ${shell.minZ}`);
    assert.ok(gold.sz <= shell.sz + 0.12, `xuan_gold height ${gold.sz} vs shell ${shell.sz}`);

    const contact = snakeContactRatio(gtX, binX);
    assert.ok(contact >= 0.60, `snake contact ratio ${contact}`);
    const snakeB = glbMeshNodeBounds(gtX, binX, 'snake');
    const turtleW = Math.max(shell.sx, shell.sy);
    const snakeW = Math.max(snakeB.sx, snakeB.sy);
    assert.ok(snakeW <= turtleW * 1.25 + 0.02, `snake width ${snakeW} vs turtle ${turtleW}`);
    const eyeB = glbMeshNodeBounds(gtX, binX, 'snake_eye');
    assert.ok(Math.max(eyeB.sx, eyeB.sy, eyeB.sz) <= turtleW * 0.12,
      `snake_eye max axis ${Math.max(eyeB.sx, eyeB.sy, eyeB.sz)} vs turtle ${turtleW}`);
    const goldVerts = collectMeshVerts(gtX, binX, ['xuan_gold'], 3);
    const shellCtrX = (shell.minX + shell.maxX) * 0.5;
    const shellCtrY = (shell.minY + shell.maxY) * 0.5;
    const shellHalf = Math.max(shell.sx, shell.sy) * 0.5;
    for (const p of goldVerts) {
      const dx = p[0] - shellCtrX;
      const dy = p[1] - shellCtrY;
      assert.ok(Math.hypot(dx, dy) <= shellHalf * 0.92 + 0.02,
        `xuan_gold vertex r ${Math.hypot(dx, dy)} vs shell ${shellHalf}`);
    }
    const THREE = mockThreeLite();
    THREE.Color = class {
      constructor() { this.r = 1; this.g = 1; this.b = 1; }
      setRGB(r, g, b) { this.r = r; this.g = g; this.b = b; }
      clone() { const c = new THREE.Color(); c.r = this.r; c.g = this.g; c.b = this.b; return c; }
      copy(o) { this.r = o.r; this.g = o.g; this.b = o.b; return this; }
    };
    const mats = {};
    const birdRoot = {
      traverse(fn) {
        const body = { isMesh: true, name: 'gull_body', geometry: { attributes: { position: {} } },
          material: { color: new THREE.Color(), emissive: new THREE.Color(), roughness: 0.8, metalness: 0, userData: {} },
          castShadow: false, receiveShadow: false };
        const tip = { isMesh: true, name: 'gull_tip', geometry: { attributes: { position: {} } },
          material: { color: new THREE.Color(), emissive: new THREE.Color(), roughness: 0.8, metalness: 0, userData: {} },
          castShadow: false, receiveShadow: false };
        fn(body);
        fn(tip);
        mats.body = body.material;
        mats.tip = tip.material;
      }
    };
    prepareLifeMaterials(THREE, birdRoot, 'birds');
    assert.ok(mats.body.emissiveIntensity >= 0.42);
    assert.ok(mats.body.userData.dayEmissiveMin >= 0.42);
    assert.equal(mats.tip.emissiveIntensity, 0);

    const binG = readGlbBin(join(publicRoot, 'models', 'life', 'seagull.glb'));
    const gtG = readGlbJson(join(publicRoot, 'models', 'life', 'seagull.glb'));
    const bird = glbNodeBounds(gtG, binG);
    const beak = glbMeshWorldBounds(gtG, binG, 'gull_beak');
    const tip = glbMeshWorldBounds(gtG, binG, 'gull_tip');
    assert.ok(beak.sx <= bird.sx * 0.15 + 0.02, `gull_beak sx ${beak.sx}`);
    assert.ok(beak.sy <= bird.sy * 0.15 + 0.02, `gull_beak sy ${beak.sy}`);
    assert.ok(beak.sz <= bird.sz * 0.15 + 0.02, `gull_beak sz ${beak.sz}`);
    assert.ok(tip.count >= 12, `gull_tip verts ${tip.count}`);
    const tipOut = Math.max(Math.abs(tip.minX), Math.abs(tip.maxX));
    assert.ok(tipOut >= bird.sx * 0.32, `gull_tip outer ${tipOut}`);

    const binW = readGlbBin(join(publicRoot, 'models', 'life', 'wenyao.glb'));
    const gtW = readGlbJson(join(publicRoot, 'models', 'life', 'wenyao.glb'));
    const body = glbMeshNodeBounds(gtW, binW, 'wen_body');
    const bodyLong = Math.max(body.sx, body.sy, body.sz);
    const wingL = glbMeshNodeBounds(gtW, binW, 'wen_wing_l');
    const wingR = glbMeshNodeBounds(gtW, binW, 'wen_wing_r');
    const wingSpan = wingL.sx + wingR.sx;
    const runtimeScale = 2.25;
    assert.ok(wingSpan >= bodyLong * 1.75, `wenyao wing span ${wingSpan} vs body ${bodyLong}`);
    assert.ok(wingSpan * runtimeScale >= bodyLong * runtimeScale * 1.75, 'wenyao runtime wing/body ratio');
    assert.ok(wingSpan * runtimeScale >= 1.9 && wingSpan * runtimeScale <= 2.6, `wenyao runtime wingspan ${wingSpan * runtimeScale}`);
  });
});

describe('island-life createIslandLife', () => {
  test('建立與 dispose 不拋錯', () => {
    const THREE = mockThreeLite();
    const view = createIslandLife(THREE, { reduced: false, procedural: true });
    assert.ok(view.group);
    assert.ok(view.creatures.length > 0);
    view.update(0.016, 1);
    view.setMix(0.8);
    view.dispose();
    assert.equal(view.creatures.length, 0);
  });

  test('reduced 時 update 不拋錯', () => {
    const THREE = mockThreeLite();
    const view = createIslandLife(THREE, { reduced: true, procedural: true });
    for (let i = 0; i < 30; i++) {
      view.update(0.016, i * 0.016);
    }
    view.dispose();
  });
});