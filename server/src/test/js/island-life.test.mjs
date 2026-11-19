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
  LIFE_PANORAMA_VIEWPORT
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
  const longest = Math.max(b.sx, b.sy, b.sz);
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
    const ySpan = b.maxY - b.minY;
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
    const horizontal = new Set(['fishSchool', 'whale', 'kunpeng', 'wenyao', 'birds']);
    let totalBytes = 0;
    for (const kind of LIFE_GLB_KINDS) {
      const file = LIFE_GLB_FILES[kind];
      const fp = join(publicRoot, 'models', 'life', file);
      assertLifeGlbContract(fp, horizontal);
      totalBytes += readFileSync(fp).length;
    }
    assert.ok(totalBytes <= 2 * 1024 * 1024, 'life GLB total bytes');
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