// 海面與天空裝飾生物（#162 / #199）：可測試純邏輯 + Three.js 場景掛載。
import {
  walkable,
  ISLANDS,
  SEA_NAV_BOUNDS,
  BOAT_ROUTE,
  BOAT_ROUTE_MAIN_CITY
} from './island-layout.js';
import { PLAYER_SPAWN } from './island-explore.js';
import { waveHeight } from './island-sea.js';

export const LIFE_SEA_Y = -0.74;
export const LIFE_MAX_CREATURES = 44;
export const LIFE_SPAWN_CLEARANCE = 4.2;
export const LIFE_ROUTE_CLEARANCE = 3.2;
export const LIFE_WALK_CLEARANCE = 1.15;
export const LIFE_SKY_BASE = 4.5;

const RARE_CYCLE_SEC = 48;

export const LIFE_CREATURE_KINDS = Object.freeze([
  'fishSchool',
  'whale',
  'octopus',
  'mermaid',
  'seaDragon',
  'jellyfish',
  'wenyao',
  'chiru',
  'xuanwu',
  'seaSerpent',
  'birds',
  'qingniao',
  'kunpeng',
  'kitsune',
  'phoenix',
  'wyvern',
  'bifang',
  'chenghuang'
]);

/** 概念圖 GLB（#226 v3，自授權低多邊模型） */
export const LIFE_GLB_KINDS = Object.freeze([
  'fishSchool',
  'whale',
  'octopus',
  'wenyao',
  'xuanwu',
  'birds',
  'kunpeng'
]);

/** #162 舊種：保留生成邏輯，預設縮小隱藏（#199） */
export const LIFE_LEGACY_HIDDEN_KINDS = Object.freeze([
  'mermaid',
  'seaDragon',
  'jellyfish',
  'kitsune',
  'bifang',
  'chenghuang'
]);

const LIFE_GLB_FILES = Object.freeze({
  fishSchool: 'fish.glb',
  whale: 'whale.glb',
  octopus: 'octopus.glb',
  wenyao: 'wenyao.glb',
  xuanwu: 'xuanwu.glb',
  birds: 'seagull.glb',
  kunpeng: 'kunpeng.glb'
});

/** GLB 最長軸已正規化為 1；此係數為世界尺寸目標（#226 r2） */
export const LIFE_GLB_SCALE = Object.freeze({
  fishSchool: 1.55,
  whale: 7.5,
  octopus: 2.5,
  wenyao: 2.25,
  xuanwu: 5.0,
  birds: 1.65,
  kunpeng: 9.0
});

/** 顯示偏移（僅視覺 group；錨點測試仍用 anchor.x/z） */
const LIFE_KIND_VIEW_OFFSET = Object.freeze({
  whale: { x: 0.5, z: 0.8 },
  xuanwu: { x: 1.2, z: 0.6 },
  kunpeng: { x: -1.5, z: 0.5 } // 配合較低飛行高度避開右上 HUD
});

const LIFE_KIND_YAW = Object.freeze({
  whale: 0.95,
  xuanwu: 0.4,
  kunpeng: 0.35,
  seaSerpent: 0.25,
  chiru: -0.35,
  wenyao: 0.35
});

/** 全景驗收相機（1280×720 實測） */
export const LIFE_PANORAMA_VIEWPORT = Object.freeze({
  width: 1280,
  height: 720
});

export const LIFE_PANORAMA_CAMERA = Object.freeze({
  eye: Object.freeze({ x: 58.1, y: 59.7, z: 81.9 }),
  dir: Object.freeze({ x: -0.501, y: -0.506, z: -0.702 }),
  fovDeg: 25
});

/** 島名標籤與 HUD 禁止重疊的螢幕矩形 */
export const LIFE_SCREEN_EXCLUSIONS = Object.freeze([
  Object.freeze({ x: 615, y: 289, w: 72, h: 29 }),
  Object.freeze({ x: 852, y: 321, w: 72, h: 29 }),
  Object.freeze({ x: 406, y: 231, w: 99, h: 29 }),
  Object.freeze({ x: 458, y: 396, w: 72, h: 29 }),
  Object.freeze({ x: 843, y: 441, w: 72, h: 29 }),
  Object.freeze({ x: 766, y: 191, w: 72, h: 29 }),
  Object.freeze({ x: 0, y: 0, w: 240, h: 160 }),
  Object.freeze({ x: 840, y: 0, w: 440, h: 70 }),
  Object.freeze({ x: 760, y: 640, w: 520, h: 80 })
]);

/** 世界半徑估計（AABB 半軸；略大以保守避讓） */
export const LIFE_KIND_HALF_EXTENTS = Object.freeze({
  fishSchool: Object.freeze({ x: 2.05, y: 0.22, z: 1.65 }),
  whale: Object.freeze({ x: 0.95, y: 0.55, z: 3.85 }),
  octopus: Object.freeze({ x: 1.35, y: 0.65, z: 1.35 }),
  wenyao: Object.freeze({ x: 1.12, y: 0.4, z: 0.92 }),
  xuanwu: Object.freeze({ x: 2.65, y: 0.72, z: 2.35 }),
  birds: Object.freeze({ x: 2.25, y: 0.75, z: 1.45 }),
  kunpeng: Object.freeze({ x: 1.1, y: 0.9, z: 4.75 })
});

/** 海面生物：身體或特效露出水面（世界 Y，相對錨點 xz） */
const LIFE_SEA_MOUNT_Y = Object.freeze({
  fishSchool: 0.06,
  whale: 0.55,
  octopus: 0.18,
  wenyao: 0.12,
  xuanwu: 0.32
});

export function lifeModelPath(kind) {
  const file = LIFE_GLB_FILES[kind];
  if (!file) return null;
  return '/models/life/' + file;
}

const COMMON_KINDS = Object.freeze(['fishSchool', 'birds', 'octopus']);
const NIGHT_GLOW_KINDS = Object.freeze(['jellyfish', 'phoenix']);
const RARE_KINDS = Object.freeze([
  'whale',
  'mermaid',
  'seaDragon',
  'wenyao',
  'xuanwu',
  'kunpeng',
  'kitsune',
  'bifang',
  'chenghuang'
]);

/** 固定錨點（世界 xz）；生成物不得改 island-layout 座標。 */
export function lifePlacementAnchors() {
  return Object.freeze([
    Object.freeze({ kind: 'fishSchool', x: -14.5, z: 20.5, radius: 2.6, realm: 'sea' }),
    Object.freeze({ kind: 'fishSchool', x: -13.0, z: -15.5, radius: 2.2, realm: 'sea' }),
    Object.freeze({ kind: 'fishSchool', x: 9.5, z: 25.5, radius: 2.5, realm: 'sea' }),
    Object.freeze({ kind: 'whale', x: -26.5, z: 7.5, realm: 'sea' }),
    Object.freeze({ kind: 'octopus', x: 30.5, z: -2.5, realm: 'sea' }),
    Object.freeze({ kind: 'mermaid', x: 25.5, z: 12.0, realm: 'sea' }),
    Object.freeze({ kind: 'seaDragon', x: -23.5, z: -4.5, realm: 'sea' }),
    Object.freeze({ kind: 'jellyfish', x: 12.0, z: 12.5, realm: 'sea' }),
    Object.freeze({ kind: 'jellyfish', x: -10.5, z: -19.8, realm: 'sea' }),
    Object.freeze({ kind: 'jellyfish', x: 28.5, z: -1.5, realm: 'sea' }),
    Object.freeze({ kind: 'wenyao', x: -15.5, z: 23.0, realm: 'sky' }),
    Object.freeze({ kind: 'xuanwu', x: -18.0, z: -22.5, realm: 'sea' }),
    Object.freeze({ kind: 'birds', x: -22.0, z: 8.0, radius: 5.5, realm: 'sky' }),
    Object.freeze({ kind: 'birds', x: 26.25, z: -13.75, radius: 4.8, realm: 'sky' }),
    Object.freeze({ kind: 'kunpeng', x: -26.0, z: -2.0, realm: 'sky' }),
    Object.freeze({ kind: 'kitsune', x: -18.0, z: 16.0, realm: 'sky' }),
    Object.freeze({ kind: 'bifang', x: 6.0, z: 20.0, realm: 'sky' }),
    Object.freeze({ kind: 'chenghuang', x: -14.0, z: -12.0, realm: 'sky' })
  ]);
}

export function distPointSegment(px, pz, ax, az, bx, bz) {
  const dx = bx - ax;
  const dz = bz - az;
  const len2 = dx * dx + dz * dz;
  if (len2 < 1e-9) return Math.hypot(px - ax, pz - az);
  let t = ((px - ax) * dx + (pz - az) * dz) / len2;
  t = Math.max(0, Math.min(1, t));
  const qx = ax + dx * t;
  const qz = az + dz * t;
  return Math.hypot(px - qx, pz - qz);
}

export function distanceToBoatRoutes(x, z) {
  let best = Infinity;
  for (let i = 0; i < BOAT_ROUTE.length - 1; i++) {
    const a = BOAT_ROUTE[i];
    const b = BOAT_ROUTE[i + 1];
    best = Math.min(best, distPointSegment(x, z, a.x, a.z, b.x, b.z));
  }
  for (let j = 0; j < BOAT_ROUTE_MAIN_CITY.length - 1; j++) {
    const a = BOAT_ROUTE_MAIN_CITY[j];
    const b = BOAT_ROUTE_MAIN_CITY[j + 1];
    best = Math.min(best, distPointSegment(x, z, a.x, a.z, b.x, b.z));
  }
  return best;
}

export function inSeaBounds(x, z, margin) {
  const m = margin || 0;
  return x >= SEA_NAV_BOUNDS.minX - m && x <= SEA_NAV_BOUNDS.maxX + m
    && z >= SEA_NAV_BOUNDS.minZ - m && z <= SEA_NAV_BOUNDS.maxZ + m;
}

export function lifePlacementAllowed(x, z, clearance) {
  const pad = clearance == null ? LIFE_WALK_CLEARANCE : clearance;
  if (walkable(x, z, pad)) return false;
  if (Math.hypot(x - PLAYER_SPAWN.x, z - PLAYER_SPAWN.z) < LIFE_SPAWN_CLEARANCE) return false;
  if (distanceToBoatRoutes(x, z) < LIFE_ROUTE_CLEARANCE) return false;
  if (!inSeaBounds(x, z, 4)) return false;
  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const dist = Math.hypot(x - isl.x, z - isl.z);
    if (dist < isl.visualR + pad * 0.65) return false;
  }
  return true;
}

export function validateLifePlacements() {
  const anchors = lifePlacementAnchors();
  const problems = [];
  if (anchors.length > LIFE_MAX_CREATURES) {
    problems.push('錨點超過上限');
  }
  for (let i = 0; i < anchors.length; i++) {
    const a = anchors[i];
    if (!LIFE_CREATURE_KINDS.includes(a.kind)) {
      problems.push('未知種類 ' + a.kind);
    }
    if (!lifePlacementAllowed(a.x, a.z, LIFE_WALK_CLEARANCE)) {
      problems.push('錨點不可放 ' + a.kind + ' @' + a.x + ',' + a.z);
    }
  }
  return Object.freeze({ ok: problems.length === 0, problems: Object.freeze(problems) });
}

function lifeVec3Norm(v) {
  const len = Math.hypot(v.x, v.y, v.z) || 1;
  return { x: v.x / len, y: v.y / len, z: v.z / len };
}

function lifeVec3Cross(a, b) {
  return {
    x: a.y * b.z - a.z * b.y,
    y: a.z * b.x - a.x * b.z,
    z: a.x * b.y - a.y * b.x
  };
}

function lifeVec3Dot(a, b) {
  return a.x * b.x + a.y * b.y + a.z * b.z;
}

export function lifeCreatureWorldCenter(anchor, kind) {
  const off = LIFE_KIND_VIEW_OFFSET[kind] || null;
  const dx = off ? off.x : 0;
  const dz = off ? off.z : 0;
  const realm = anchor.realm || 'sea';
  let y;
  if (realm === 'sea') {
    const seaOff = LIFE_SEA_MOUNT_Y[kind];
    y = LIFE_SEA_Y + (seaOff != null ? seaOff : 0);
  } else {
    y = LIFE_SKY_BASE + (kind === 'kunpeng' ? 6 : kind === 'wenyao' ? 5.5 : 6);
  }
  return Object.freeze({ x: anchor.x + dx, y: y, z: anchor.z + dz });
}

export function lifeProjectWorldPoint(wx, wy, wz, cam, viewport) {
  const eye = cam.eye;
  const forward = lifeVec3Norm(cam.dir);
  let right = lifeVec3Cross(forward, { x: 0, y: 1, z: 0 });
  const rlen = Math.hypot(right.x, right.y, right.z);
  if (rlen < 1e-6) right = { x: 1, y: 0, z: 0 };
  else right = { x: right.x / rlen, y: right.y / rlen, z: right.z / rlen };
  const up = lifeVec3Norm(lifeVec3Cross(right, forward));
  const rel = { x: wx - eye.x, y: wy - eye.y, z: wz - eye.z };
  const vz = lifeVec3Dot(rel, forward);
  if (vz <= 0.05) return null;
  const vx = lifeVec3Dot(rel, right);
  const vy = lifeVec3Dot(rel, up);
  const tanHalf = Math.tan(((cam.fovDeg || 25) * Math.PI) / 180 / 2);
  const aspect = viewport.width / viewport.height;
  const ndcX = vx / (vz * tanHalf * aspect);
  const ndcY = vy / (vz * tanHalf);
  return Object.freeze({
    x: (ndcX * 0.5 + 0.5) * viewport.width,
    y: (1 - (ndcY * 0.5 + 0.5)) * viewport.height
  });
}

export function lifeProjectCreatureScreenBox(anchor, kind, cam, viewport) {
  const center = lifeCreatureWorldCenter(anchor, kind);
  const half = LIFE_KIND_HALF_EXTENTS[kind];
  if (!half) return null;
  let minX = Infinity;
  let minY = Infinity;
  let maxX = -Infinity;
  let maxY = -Infinity;
  let any = false;
  const sx = [center.x - half.x, center.x + half.x];
  const sy = [center.y - half.y, center.y + half.y];
  const sz = [center.z - half.z, center.z + half.z];
  for (let ix = 0; ix < 2; ix++) {
    for (let iy = 0; iy < 2; iy++) {
      for (let iz = 0; iz < 2; iz++) {
        const p = lifeProjectWorldPoint(sx[ix], sy[iy], sz[iz], cam, viewport);
        if (!p) continue;
        any = true;
        minX = Math.min(minX, p.x);
        minY = Math.min(minY, p.y);
        maxX = Math.max(maxX, p.x);
        maxY = Math.max(maxY, p.y);
      }
    }
  }
  if (!any) return null;
  return Object.freeze({ x: minX, y: minY, w: maxX - minX, h: maxY - minY });
}

function rectsOverlap(a, b) {
  return a.x < b.x + b.w && a.x + a.w > b.x && a.y < b.y + b.h && a.y + a.h > b.y;
}

export function lifeCreaturePanoramaPlacementOk(anchor, kind, cam, viewport, exclusions) {
  const box = lifeProjectCreatureScreenBox(anchor, kind, cam, viewport);
  if (!box) return { ok: false, reason: 'behind camera or missing extents' };
  const pad = 4;
  if (box.x < pad || box.y < pad
    || box.x + box.w > viewport.width - pad
    || box.y + box.h > viewport.height - pad) {
    return { ok: false, reason: 'off screen', box: box };
  }
  const blocks = exclusions || LIFE_SCREEN_EXCLUSIONS;
  for (let i = 0; i < blocks.length; i++) {
    if (rectsOverlap(box, blocks[i])) {
      return { ok: false, reason: 'overlap exclusion', box: box };
    }
  }
  return { ok: true, box: box };
}

export function validateLifePanoramaGlbPlacements() {
  const cam = LIFE_PANORAMA_CAMERA;
  const vp = LIFE_PANORAMA_VIEWPORT;
  const problems = [];
  for (const anchor of lifePlacementAnchors()) {
    if (!LIFE_GLB_KINDS.includes(anchor.kind)) continue;
    const r = lifeCreaturePanoramaPlacementOk(anchor, anchor.kind, cam, vp);
    if (!r.ok) {
      problems.push(anchor.kind + '@' + anchor.x + ',' + anchor.z + ': ' + r.reason);
    }
  }
  return Object.freeze({ ok: problems.length === 0, problems: Object.freeze(problems) });
}

export function lifeInstanceBudget(reduced, lowQuality) {
  if (reduced) return { common: 1, rare: 0, schools: 1, birds: 1 };
  if (lowQuality) return { common: 3, rare: 1, schools: 2, birds: 1 };
  return { common: COMMON_KINDS.length, rare: 1, schools: 3, birds: 2 };
}

export function lifeRareIndex(elapsed, reduced) {
  if (reduced) return -1;
  const e = Math.max(0, Number(elapsed) || 0);
  return Math.floor(e / RARE_CYCLE_SEC) % RARE_KINDS.length;
}

export function lifeRareKindAt(elapsed, reduced) {
  const idx = lifeRareIndex(elapsed, reduced);
  if (idx < 0) return null;
  return RARE_KINDS[idx];
}

export function lifeCreatureGlow(kind) {
  return NIGHT_GLOW_KINDS.indexOf(kind) >= 0;
}

export function lifeGlowStrength(kind, mix, reduced) {
  if (reduced || !lifeCreatureGlow(kind)) return 0;
  const t = Math.max(0, Math.min(1, Number(mix) || 0));
  if (t < 0.35) return 0;
  return (t - 0.35) / 0.65;
}

export function lifeKindActive(kind, mix, reduced, elapsed, opts) {
  const o = opts || {};
  if (o.forceRare) {
    if (kind === o.forceRare) return !reduced;
    if (COMMON_KINDS.includes(kind)) {
      return !reduced || kind === 'fishSchool' || kind === 'birds';
    }
    return false;
  }
  if (o.forceAll) return true;
  if (COMMON_KINDS.includes(kind)) return !reduced || kind === 'fishSchool' || kind === 'birds';
  if (NIGHT_GLOW_KINDS.includes(kind)) {
    const t = Math.max(0, Math.min(1, Number(mix) || 0));
    return !reduced && t >= 0.4;
  }
  if (kind === 'wyvern') {
    const t = Math.max(0, Math.min(1, Number(mix) || 0));
    return !reduced && t >= 0.55;
  }
  if (RARE_KINDS.includes(kind)) {
    if (reduced) return false;
    return lifeRareKindAt(elapsed, reduced) === kind;
  }
  return false;
}

export function sampleFishSchool(anchor, t, reduced) {
  const speed = reduced ? 0 : 0.35;
  const angle = t * speed + (anchor.x * 0.17);
  const r = anchor.radius || 2.5;
  return {
    x: anchor.x + Math.cos(angle) * r,
    z: anchor.z + Math.sin(angle) * r * 0.82,
    yaw: angle + Math.PI * 0.5
  };
}

export function lifeUpdateTick(reduced, dt, elapsed) {
  const d = Math.max(0, Number(dt) || 0);
  const e = Math.max(0, Number(elapsed) || 0);
  if (reduced) {
    return { elapsed: e, rare: null, animate: false, dt: 0 };
  }
  return { elapsed: e + d, rare: lifeRareKindAt(e, false), animate: true, dt: d };
}

function addDisposable(list, item) {
  if (item) list.push(item);
}

let lifeGltfLoader = null;
const lifeGlbCache = new Map();

async function getLifeLoader() {
  if (!lifeGltfLoader) {
    const mod = await import('three/addons/loaders/GLTFLoader.js');
    lifeGltfLoader = new mod.GLTFLoader();
  }
  return lifeGltfLoader;
}

function fetchLifeGlb(url) {
  if (lifeGlbCache.has(url)) return lifeGlbCache.get(url);
  const promise = getLifeLoader().then((loader) => new Promise((resolve, reject) => {
    loader.load(url, resolve, undefined, reject);
  }));
  lifeGlbCache.set(url, promise);
  return promise;
}

function forEachMesh(root, fn) {
  root.traverse((child) => {
    if (child.isMesh || child.type === 'Mesh') fn(child);
  });
}

function isCreatureBodyMesh(mesh) {
  const n = (mesh.name || '').toLowerCase();
  if (!n || n.includes('icosphere') || n.includes('cube')) return false;
  if (n.includes('eye')) return true;
  return mesh.geometry && mesh.geometry.attributes && mesh.geometry.attributes.position;
}

function prepareLifeMaterials(THREE, root, kind) {
  forEachMesh(root, (mesh) => {
    if (!isCreatureBodyMesh(mesh)) {
      mesh.visible = false;
      return;
    }
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    const n = (mesh.name || '').toLowerCase();
    const sprayMesh = n.includes('spray');
    const cloudMesh = n.includes('cloud') || n.includes('kun_cloud');
    const mats = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
    for (let i = 0; i < mats.length; i++) {
      const mat = mats[i];
      if (!mat) continue;
      mat.flatShading = true;
      if (mat.map) {
        mat.map.dispose();
        mat.map = null;
      }
      if (mesh.geometry && mesh.geometry.attributes.color) {
        mat.vertexColors = true;
      } else {
        mat.vertexColors = false;
      }
      if (!mat.color) mat.color = new THREE.Color(0xffffff);
      if (sprayMesh) {
        mat.color.setRGB(0.95, 0.98, 1);
        mat.transparent = true;
        mat.opacity = 0.82;
      } else if (cloudMesh) {
        mat.color.setRGB(0.98, 0.99, 1);
        mat.transparent = true;
        mat.opacity = 0.68;
      }
      const base = mat.color.clone();
      mat.userData.day = base;
      mat.userData.night = base.clone();
      mat.userData.dayE = mat.emissiveIntensity || 0;
      const nightKinds = {
        fishSchool: 0.22,
        whale: 0.1,
        octopus: 0.12,
        wenyao: 0.14,
        xuanwu: 0.1,
        birds: 0.08,
        kunpeng: 0.16
      };
      const nightE = nightKinds[kind] || 0.08;
      mat.userData.nightE = sprayMesh ? 0.2 : nightE;
      if (sprayMesh && mat.emissive) mat.emissive.setRGB(0.75, 0.9, 1);
    }
  });
}

function lifeViewOffset(kind) {
  return LIFE_KIND_VIEW_OFFSET[kind] || null;
}

function applyLifeViewPosition(group, anchor, kind) {
  const off = lifeViewOffset(kind);
  const dx = off ? off.x : 0;
  const dz = off ? off.z : 0;
  const realm = anchor.realm || 'sea';
  if (realm === 'sea') {
    const seaOff = LIFE_SEA_MOUNT_Y[kind];
    group.position.set(anchor.x + dx, LIFE_SEA_Y + (seaOff != null ? seaOff : 0), anchor.z + dz);
  } else {
    group.position.set(anchor.x + dx, LIFE_SKY_BASE + (kind === 'kunpeng' ? 6 : 6), anchor.z + dz);
  }
  const yaw = LIFE_KIND_YAW[kind];
  if (yaw != null) group.rotation.y = yaw;
  group.userData.viewOffset = { x: dx, z: dz };
}

function prepSkinnedRoot(THREE, gltf, scale) {
  const root = gltf.scene;
  root.scale.setScalar(scale || 1);
  root.rotation.x = 0;
  root.rotation.y = 0;
  root.rotation.z = 0;
  root.traverse((o) => {
    o.frustumCulled = true;
  });
  return { root: root, mixer: null, animations: [] };
}

function firstMeshPair(root) {
  let geometry = null;
  let material = null;
  forEachMesh(root, (mesh) => {
    if (geometry || !isCreatureBodyMesh(mesh)) return;
    geometry = mesh.geometry;
    material = mesh.material;
  });
  return { geometry: geometry, material: material };
}

function buildFishSchoolInstanced(THREE, kit, anchor, fishRoot) {
  const group = new THREE.Group();
  group.userData.kind = 'fishSchool';
  group.userData.anchor = anchor;
  const pair = firstMeshPair(fishRoot);
  if (!pair.geometry) return group;
  const count = 14;
  const useInstanced = !fishRoot.isSkinnedMesh
    && !(pair.geometry.attributes.skinIndex || pair.geometry.attributes.skinWeight);
  if (useInstanced) {
    const mesh = new THREE.InstancedMesh(pair.geometry, pair.material, count);
    mesh.castShadow = false;
    mesh.receiveShadow = false;
    const dummy = new THREE.Object3D();
    for (let i = 0; i < count; i++) {
      dummy.position.set(
        (i % 4) * 1.05 - 1.58,
        (i % 3) * 0.08 - 0.04,
        (Math.floor(i / 4) % 4) * 0.82 - 1.1
      );
      dummy.rotation.set(0, (i % 5) * 0.38 + 0.2, (i % 3) * 0.04);
      dummy.scale.setScalar(0.92 + (i % 4) * 0.12);
      dummy.updateMatrix();
      mesh.setMatrixAt(i, dummy.matrix);
    }
    mesh.instanceMatrix.needsUpdate = true;
    group.add(mesh);
  } else {
    for (let i = 0; i < count; i++) {
      const fish = fishRoot.clone(true);
      fish.position.set(
        (i % 4) * 1.05 - 1.58,
        (i % 3) * 0.08 - 0.04,
        (Math.floor(i / 4) % 4) * 0.82 - 1.1
      );
      fish.rotation.set(0, (i % 5) * 0.38 + 0.2, (i % 3) * 0.04);
      fish.scale.setScalar(0.92 + (i % 4) * 0.12);
      group.add(fish);
    }
  }
  const seaY = LIFE_SEA_MOUNT_Y.fishSchool != null ? LIFE_SEA_MOUNT_Y.fishSchool : -0.02;
  group.position.set(anchor.x, LIFE_SEA_Y + seaY, anchor.z);
  return group;
}

function buildBirdsInstanced(THREE, kit, anchor, gullRoot) {
  const group = new THREE.Group();
  group.userData.kind = 'birds';
  group.userData.anchor = anchor;
  const pair = firstMeshPair(gullRoot);
  if (!pair.geometry) return group;
  const count = 10;
  const mesh = new THREE.InstancedMesh(pair.geometry, pair.material, count);
  mesh.castShadow = false;
  const dummy = new THREE.Object3D();
  for (let i = 0; i < count; i++) {
    dummy.position.set(
      (i % 5) * 0.65 - 1.3,
      (i % 2) * 0.55 + 0.1,
      (Math.floor(i / 5)) * 0.9 - 0.45
    );
    dummy.rotation.set(0, (i % 4) * 0.35, 0);
    dummy.scale.setScalar(1.1 + (i % 3) * 0.18);
    dummy.updateMatrix();
    mesh.setMatrixAt(i, dummy.matrix);
  }
  mesh.instanceMatrix.needsUpdate = true;
  group.add(mesh);
  group.position.set(anchor.x, LIFE_SKY_BASE + 2.5, anchor.z);
  return group;
}

function mountGlbCreature(THREE, kit, anchor, kind, disposables, mixers) {
  const group = new THREE.Group();
  group.userData.kind = kind;
  group.userData.anchor = anchor;
  const scale = LIFE_GLB_SCALE[kind] || 2;
  if (kind === 'phoenix') group.userData.glowKind = true;
  applyLifeViewPosition(group, anchor, kind);
  group.userData.glbMount = true;
  group.userData.glbScale = scale;
  return group;
}

function emissiveMat(THREE, kit, color, glow, parent) {
  if (kit) {
    return kit.mat(color, { glow: glow || 0, glowAmount: 0.55, shadow: false });
  }
  const mat = new THREE.MeshLambertMaterial({
    color: color,
    flatShading: true,
    emissive: new THREE.Color(glow || 0),
    emissiveIntensity: 0.35
  });
  mat.userData.day = new THREE.Color(color);
  mat.userData.night = new THREE.Color(color);
  mat.userData.dayE = 0;
  mat.userData.nightE = glow ? 0.5 : 0;
  if (parent && parent.userData) parent.userData.materials = parent.userData.materials || [];
  return mat;
}

function buildFishSchool(THREE, kit, anchor, disposables) {
  const group = new THREE.Group();
  group.userData.kind = 'fishSchool';
  const bodyColor = 0x4aa8c8;
  for (let i = 0; i < 6; i++) {
    const fish = new THREE.Group();
    if (kit) {
      kit.addMesh(fish, kit.geo('sph', [0.12, 5, 4]), bodyColor, 0, 0, 0, { shadow: false, scale: [1.6, 0.65, 0.85] });
      kit.addMesh(fish, kit.geo('cone', [0.08, 0.22, 4]), 0x3d8aaa, -0.18, 0, 0, { shadow: false, rot: [0, 0, Math.PI] });
    } else {
      const m = new THREE.Mesh(
        new THREE.SphereGeometry(0.12, 5, 4),
        emissiveMat(THREE, null, bodyColor)
      );
      m.scale.set(1.6, 0.65, 0.85);
      fish.add(m);
      addDisposable(disposables, m.geometry);
      addDisposable(disposables, m.material);
    }
    fish.position.set((i % 3) * 0.35 - 0.35, (i >> 2) * 0.2, (i % 2) * 0.4 - 0.2);
    group.add(fish);
  }
  group.position.set(anchor.x, LIFE_SEA_Y - 0.08, anchor.z);
  group.userData.anchor = anchor;
  return group;
}

function buildWhale(THREE, kit, anchor, disposables) {
  const group = new THREE.Group();
  group.userData.kind = 'whale';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [1.6, 8, 6]), 0x5a7a9a, 0, 0, 0, { shadow: false, scale: [2.2, 0.55, 0.9] });
    kit.addMesh(group, kit.geo('cone', [0.35, 0.9, 5]), 0x4a6a88, 1.6, 0.1, 0, { shadow: false, rot: [0, 0, -Math.PI * 0.5] });
  }
  const spray = kit
    ? kit.addMesh(group, kit.geo('cyl', [0.04, 0.02, 0.7, 4, 0]), 0xc8e8ff, 0, 0.65, 0, { shadow: false, opacity: 0.65 })
  : null;
  if (spray) spray.visible = false;
  group.userData.spray = spray;
  group.position.set(anchor.x, LIFE_SEA_Y - 0.15, anchor.z);
  group.rotation.y = 0.45;
  return group;
}

function buildOctopus(THREE, kit, anchor, disposables) {
  const group = new THREE.Group();
  group.userData.kind = 'octopus';
  const headColor = 0xc76b9a;
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.42, 7, 6]), headColor, 0, 0.2, 0, { shadow: false });
    for (let i = 0; i < 5; i++) {
      const a = (i / 5) * Math.PI * 2;
      kit.addMesh(group, kit.geo('cyl', [0.05, 0.03, 0.55, 4, 0]), 0xb85a88,
        Math.cos(a) * 0.28, 0.02, Math.sin(a) * 0.28, { shadow: false, rot: [0.5, a, 0] });
    }
  }
  group.position.set(anchor.x, LIFE_SEA_Y + 0.02, anchor.z);
  return group;
}

function buildMermaid(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'mermaid';
  if (kit) {
    kit.addBox(group, 0.35, 0.18, 0.55, 0x4ec4b5, 0, -0.05, 0, { shadow: false });
    kit.addMesh(group, kit.geo('sph', [0.18, 6, 5]), 0xf5c4a8, 0, 0.22, 0, { shadow: false });
    kit.addMesh(group, kit.geo('sph', [0.12, 5, 4]), 0xe8a090, 0.22, 0.2, 0, { shadow: false, scale: [1.2, 0.5, 0.8] });
  }
  group.position.set(anchor.x, LIFE_SEA_Y + 0.12, anchor.z);
  return group;
}

function buildSeaDragon(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'seaDragon';
  const segColor = 0x2a9a88;
  for (let i = 0; i < 7; i++) {
    if (kit) {
      kit.addMesh(group, kit.geo('sph', [0.22, 6, 5]), segColor, i * 0.38 - 1.2, Math.sin(i * 0.45) * 0.12, 0,
        { shadow: false, scale: [1.1 - i * 0.08, 0.75, 0.75] });
    }
  }
  if (kit) {
    kit.addMesh(group, kit.geo('cone', [0.12, 0.35, 4]), 0x1f7a6a, 1.55, 0.15, 0, { shadow: false });
  }
  group.position.set(anchor.x, LIFE_SEA_Y - 0.05, anchor.z);
  group.rotation.y = -0.35;
  return group;
}

function buildJellyfish(THREE, kit, anchor, disposables) {
  const group = new THREE.Group();
  group.userData.kind = 'jellyfish';
  const glow = 0x8866ff;
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.22, 7, 5]), 0x7b5fd4, 0, 0.18, 0,
      { shadow: false, glow: glow, glowAmount: 0.75, scale: [1.1, 0.55, 1.1] });
    for (let s = 0; s < 4; s++) {
      kit.addMesh(group, kit.geo('cyl', [0.02, 0.01, 0.45, 3, 0]), 0x9f88ff,
        (s - 1.5) * 0.08, -0.05, 0, { shadow: false, glow: glow, glowAmount: 0.4 });
    }
  }
  group.userData.glowKind = true;
  group.position.set(anchor.x, LIFE_SEA_Y + 0.08, anchor.z);
  return group;
}

function buildWenyao(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'wenyao';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.35, 6, 5]), 0xe8e4dc, 0, 0, 0,
      { shadow: false, scale: [1.5, 0.55, 0.75] });
    kit.addMesh(group, kit.geo('cone', [0.08, 0.2, 3]), 0xd84a3a, 0.42, 0.02, 0, { shadow: false });
    kit.addMesh(group, kit.geo('box', [0.65, 0.04, 0.35]), 0x9cc8e8, 0, 0.12, 0, { shadow: false, opacity: 0.85 });
    kit.addMesh(group, kit.geo('box', [0.65, 0.04, 0.35]), 0x9cc8e8, 0, 0.12, -0.28, { shadow: false, opacity: 0.85 });
  }
  group.position.set(anchor.x, LIFE_SEA_Y + 0.55, anchor.z);
  return group;
}

function buildChiru(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'chiru';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.28, 6, 5]), 0xe85a5a, 0, -0.05, 0, { shadow: false, scale: [1.4, 0.7, 0.85] });
    kit.addMesh(group, kit.geo('sph', [0.14, 5, 4]), 0xf0c8a8, 0.22, 0.08, 0, { shadow: false });
  }
  group.position.set(anchor.x, LIFE_SEA_Y - 0.02, anchor.z);
  return group;
}

function buildXuanwu(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'xuanwu';
  group.scale.setScalar(2.8);
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [1.1, 8, 6]), 0x3d4a5a, 0, 0.1, 0, { shadow: false, scale: [1.3, 0.45, 1.1] });
    kit.addMesh(group, kit.geo('cyl', [0.12, 0.1, 1.8, 5, 0]), 0x4a5a6a, 0.3, 0.55, 0.15,
      { shadow: false, rot: [0.4, 0.2, 0.6] });
  }
  group.position.set(anchor.x, LIFE_SEA_Y + 0.05, anchor.z);
  return group;
}

function buildSeaSerpent(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'seaSerpent';
  for (let i = 0; i < 5; i++) {
    if (kit) {
      kit.addMesh(group, kit.geo('sph', [0.35, 6, 5]), 0x3a6a5a, i * 0.55 - 1.1, Math.sin(i * 0.9) * 0.35, 0,
        { shadow: false, scale: [0.85, 0.55, 0.85] });
    }
  }
  group.position.set(anchor.x, LIFE_SEA_Y + 0.15, anchor.z);
  group.rotation.y = -0.8;
  return group;
}

function buildBirds(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'birds';
  group.userData.anchor = anchor;
  for (let i = 0; i < 5; i++) {
    const bird = new THREE.Group();
    if (kit) {
      kit.addMesh(bird, kit.geo('cone', [0.12, 0.18, 3]), 0xf0f0f8, 0, 0, 0, { shadow: false, rot: [Math.PI, 0, 0] });
      kit.addMesh(bird, kit.geo('box', [0.22, 0.02, 0.08]), 0xe8e8f0, 0.05, 0.02, 0, { shadow: false });
    }
    bird.position.set((i - 2) * 0.45, (i % 2) * 0.25, (i % 3) * 0.2);
    group.add(bird);
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 2.5, anchor.z);
  return group;
}

function buildKunpeng(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'kunpeng';
  group.scale.setScalar(3.2);
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.8, 6, 5]), 0x2a3a4a, 0, 0, 0,
      { shadow: false, opacity: 0.55, scale: [2.2, 0.5, 1.2] });
    kit.addMesh(group, kit.geo('box', [2.5, 0.06, 0.9]), 0x2a3a4a, 0, 0.25, 0, { shadow: false, opacity: 0.5 });
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 12, anchor.z);
  return group;
}

function buildKitsune(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'kitsune';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.2, 6, 5]), 0xf5e6c8, 0, 0, 0, { shadow: false });
    for (let t = 0; t < 9; t++) {
      const ang = (t / 9) * Math.PI * 2;
      kit.addMesh(group, kit.geo('cone', [0.04, 0.35, 3]), 0xfff0d8,
        Math.cos(ang) * 0.18, 0.12, Math.sin(ang) * 0.18, { shadow: false, rot: [0.6, ang, 0] });
    }
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 6, anchor.z);
  return group;
}

function buildPhoenix(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'phoenix';
  const glow = 0xff8844;
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.22, 6, 5]), 0xffaa66, 0, 0, 0, { shadow: false, glow: glow, glowAmount: 0.85 });
    kit.addMesh(group, kit.geo('cone', [0.08, 0.55, 4]), 0xff6622, -0.2, 0, 0, { shadow: false, glow: glow });
    kit.addMesh(group, kit.geo('cone', [0.08, 0.55, 4]), 0xff6622, 0.2, 0, 0, { shadow: false, glow: glow });
    kit.addMesh(group, kit.geo('box', [0.05, 0.75, 0.15]), 0xffcc88, 0, -0.2, 0, { shadow: false, glow: glow, glowAmount: 0.65 });
  }
  group.userData.glowKind = true;
  group.position.set(anchor.x, LIFE_SKY_BASE + 7, anchor.z);
  return group;
}

function buildWyvern(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'wyvern';
  group.scale.setScalar(1.8);
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.35, 6, 5]), 0x1a2030, 0, 0, 0, { shadow: false, opacity: 0.72, scale: [1.5, 0.5, 0.9] });
    kit.addMesh(group, kit.geo('box', [1.1, 0.04, 0.5]), 0x1a2030, 0, 0.2, 0, { shadow: false, opacity: 0.65 });
    kit.addMesh(group, kit.geo('cone', [0.1, 0.35, 4]), 0x1a2030, 0.55, 0.1, 0, { shadow: false, opacity: 0.7 });
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 9, anchor.z);
  return group;
}

function buildBifang(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'bifang';
  if (kit) {
    kit.addMesh(group, kit.geo('cone', [0.14, 0.55, 4]), 0xf0f0f8, 0, 0.12, 0, { shadow: false, rot: [Math.PI, 0, 0] });
    kit.addMesh(group, kit.geo('cyl', [0.04, 0.03, 0.55, 4, 0]), 0xe03030, 0, -0.18, 0, { shadow: false });
    kit.addMesh(group, kit.geo('cyl', [0.03, 0.02, 0.4, 3, 0]), 0xe8e8f0, 0, -0.22, 0.06, { shadow: false });
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 5.5, anchor.z);
  return group;
}

function buildChenghuang(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'chenghuang';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.22, 6, 5]), 0xe8c050, 0, 0.05, 0, { shadow: false, glow: 0xffd878, glowAmount: 0.35 });
    kit.addMesh(group, kit.geo('cone', [0.06, 0.2, 3]), 0xd4a838, 0.15, 0.18, 0, { shadow: false });
    kit.addMesh(group, kit.geo('cone', [0.06, 0.2, 3]), 0xd4a838, -0.15, 0.18, 0, { shadow: false });
    kit.addMesh(group, kit.geo('cyl', [0.04, 0.04, 0.5, 4, 0]), 0xd4a838, 0, -0.08, 0, { shadow: false });
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 5, anchor.z);
  return group;
}

function buildQingniao(THREE, kit, anchor) {
  const group = new THREE.Group();
  group.userData.kind = 'qingniao';
  if (kit) {
    kit.addMesh(group, kit.geo('sph', [0.16, 6, 5]), 0x48b8f0, 0, 0, 0, { shadow: false });
    kit.addMesh(group, kit.geo('cone', [0.06, 0.35, 4]), 0x88d8ff, -0.28, 0.05, 0, { shadow: false, rot: [0.15, 0, 0] });
    kit.addMesh(group, kit.geo('box', [0.22, 0.05, 0.12]), 0x58c8f0, 0.1, 0.08, 0.08, { shadow: false });
    kit.addMesh(group, kit.geo('box', [0.22, 0.05, 0.12]), 0x58c8f0, 0.1, -0.08, 0.08, { shadow: false });
  }
  group.position.set(anchor.x, LIFE_SKY_BASE + 6.5, anchor.z);
  return group;
}

async function hydrateLifeGlbModels(THREE, kit, creatures, mixers) {
  const shared = {};
  for (let i = 0; i < 2; i++) {
    const kind = i === 0 ? 'fishSchool' : 'birds';
    const url = lifeModelPath(kind);
    if (!url) continue;
    try {
      const gltf = await fetchLifeGlb(url);
      const prep = prepSkinnedRoot(THREE, gltf, LIFE_GLB_SCALE[kind]);
      prepareLifeMaterials(THREE, prep.root, kind);
      shared[kind] = prep.root;
    } catch (err) {
      console.warn('life glb shared', kind, err);
    }
  }

  const solo = {};
  const soloKinds = LIFE_GLB_KINDS.filter((k) => k !== 'fishSchool' && k !== 'birds');
  await Promise.all(soloKinds.map(async (kind) => {
    const url = lifeModelPath(kind);
    if (!url) return;
    try {
      const gltf = await fetchLifeGlb(url);
      solo[kind] = prepSkinnedRoot(THREE, gltf, LIFE_GLB_SCALE[kind]);
    } catch (err) {
      console.warn('life glb', kind, err);
    }
  }));

  for (let c = 0; c < creatures.length; c++) {
    const node = creatures[c];
    if (!node.userData.glbMount) continue;
    const kind = node.userData.kind;
    const anchor = node.userData.anchor;

    if (kind === 'fishSchool' && shared.fishSchool) {
      const school = buildFishSchoolInstanced(THREE, kit, anchor, shared.fishSchool);
      node.position.copy(school.position);
      for (let ch = 0; ch < school.children.length; ch++) {
        node.add(school.children[ch]);
      }
      continue;
    }
    if (kind === 'birds' && shared.birds) {
      const flock = buildBirdsInstanced(THREE, kit, anchor, shared.birds);
      node.position.copy(flock.position);
      for (let ch = 0; ch < flock.children.length; ch++) {
        node.add(flock.children[ch]);
      }
      continue;
    }

    const prep = solo[kind];
    if (!prep) continue;
    prepareLifeMaterials(THREE, prep.root, kind);
    node.add(prep.root);
    if (prep.mixer) mixers.push({ mixer: prep.mixer, root: prep.root });
    if (kind === 'whale') {
      let spray = null;
      prep.root.traverse((child) => {
        if (!child.isMesh) return;
        const n = (child.name || '').toLowerCase();
        if (n.includes('spray')) spray = child;
      });
      node.userData.spray = spray;
    }
    if (kind === 'phoenix') node.userData.glowKind = true;
  }
}

const BUILDERS = {
  fishSchool: buildFishSchool,
  whale: buildWhale,
  octopus: buildOctopus,
  mermaid: buildMermaid,
  seaDragon: buildSeaDragon,
  jellyfish: buildJellyfish,
  wenyao: buildWenyao,
  chiru: buildChiru,
  xuanwu: buildXuanwu,
  seaSerpent: buildSeaSerpent,
  birds: buildBirds,
  qingniao: buildQingniao,
  kunpeng: buildKunpeng,
  kitsune: buildKitsune,
  phoenix: buildPhoenix,
  wyvern: buildWyvern,
  bifang: buildBifang,
  chenghuang: buildChenghuang
};

export function createIslandLife(THREE, opts) {
  const reduced = !!(opts && opts.reduced);
  const kit = opts && opts.kit;
  let lowQuality = !!(opts && opts.lowQuality);
  const forceAll = !!(opts && opts.forceAll);
  const forceRare = opts && opts.forceRare ? String(opts.forceRare) : '';
  const useGlb = !(opts && opts.procedural);
  const disposables = [];
  const creatures = [];
  const mixers = [];

  const group = new THREE.Group();
  group.name = 'islandLife';

  const anchors = lifePlacementAnchors();
  for (let i = 0; i < anchors.length; i++) {
    const anchor = anchors[i];
    const kind = anchor.kind;
    let obj;
    if (useGlb && LIFE_GLB_KINDS.includes(kind)) {
      obj = mountGlbCreature(THREE, kit, anchor, kind, disposables, mixers);
    } else {
      const build = BUILDERS[kind];
      if (!build) continue;
      obj = build(THREE, kit, anchor, disposables);
    }
    obj.userData.anchor = anchor;
    obj.castShadow = false;
    obj.receiveShadow = false;
    if (LIFE_LEGACY_HIDDEN_KINDS.includes(kind)) {
      obj.scale.setScalar(0.001);
      obj.userData.legacyHidden = true;
    }
    creatures.push(obj);
    group.add(obj);
  }

  let mixValue = 0;
  let disposed = false;
  let ready = Promise.resolve();
  if (useGlb) {
    ready = hydrateLifeGlbModels(THREE, kit, creatures, mixers);
  }

  function applyVisibility(elapsed) {
    const budget = lifeInstanceBudget(reduced, lowQuality);
    let schoolCount = 0;
    let birdCount = 0;
    for (let c = 0; c < creatures.length; c++) {
      const node = creatures[c];
      const kind = node.userData.kind;
      let visible = lifeKindActive(kind, mixValue, reduced, elapsed, {
        forceAll: forceAll,
        forceRare: forceRare || null
      });
      if (node.userData.legacyHidden && !forceAll) visible = false;
      if (kind === 'fishSchool') {
        if (visible) {
          schoolCount += 1;
          if (schoolCount > budget.schools) visible = false;
        }
      }
      if (kind === 'birds') {
        if (visible) {
          birdCount += 1;
          if (birdCount > budget.birds) visible = false;
        }
      }
      node.visible = visible;
    }
  }

  function applyGlow() {
    for (let c = 0; c < creatures.length; c++) {
      const node = creatures[c];
      if (!node.userData.glowKind || !node.visible) continue;
      const strength = lifeGlowStrength(node.userData.kind, mixValue, reduced);
      node.traverse((child) => {
        if (child.material && child.material.emissiveIntensity != null) {
          child.material.emissiveIntensity = strength * (reduced ? 0 : 0.55);
        }
        if (child.material && child.material.userData) {
          child.material.userData.nightE = strength * 0.75;
        }
      });
    }
  }

  function setMix(t) {
    mixValue = Math.max(0, Math.min(1, Number(t) || 0));
    applyGlow();
  }

  function setLowQuality(flag) {
    lowQuality = !!flag;
  }

  function setVisible(visible) {
    group.visible = !!visible;
  }

  function update(dt, elapsed) {
    if (disposed) return;
    const tick = lifeUpdateTick(reduced, dt, elapsed);
    applyVisibility(tick.elapsed);
    applyGlow();
    if (!tick.animate) return;

    const animDt = tick.dt || 0;
    for (let m = 0; m < mixers.length; m++) {
      const entry = mixers[m];
      if (entry.mixer) entry.mixer.update(animDt);
    }

    for (let c = 0; c < creatures.length; c++) {
      const node = creatures[c];
      if (!node.visible) continue;
      const kind = node.userData.kind;
      const anchor = node.userData.anchor || { x: node.position.x, z: node.position.z };

      if (kind === 'fishSchool') {
        const pos = sampleFishSchool(anchor, tick.elapsed, false);
        const wave = waveHeight(pos.x, pos.z, tick.elapsed, false);
        const fishY = LIFE_SEA_MOUNT_Y.fishSchool != null ? LIFE_SEA_MOUNT_Y.fishSchool : -0.02;
        node.position.set(pos.x, LIFE_SEA_Y + fishY + wave * 0.3, pos.z);
        node.rotation.y = pos.yaw;
      } else if (kind === 'birds') {
        const pos = sampleFishSchool(anchor, tick.elapsed * 0.55, false);
        node.position.set(pos.x, LIFE_SKY_BASE + 2.8 + Math.sin(tick.elapsed * 0.4) * 0.35, pos.z);
        node.rotation.y = pos.yaw;
      } else if (kind === 'octopus') {
        node.rotation.y = Math.sin(tick.elapsed * 0.25) * 0.15;
      } else if (kind === 'xuanwu') {
        node.rotation.y = (LIFE_KIND_YAW.xuanwu || 0) + Math.sin(tick.elapsed * 0.12) * 0.06;
      } else if (kind === 'whale') {
        node.rotation.x = Math.sin(tick.elapsed * 0.14) * 0.05;
        const cycle = tick.elapsed % 5.0;
        const breach = cycle < 1.6;
        const spout = cycle > 0.2 && cycle < 2.4;
        if (node.userData.spray) {
          node.userData.spray.visible = spout;
          node.userData.spray.scale.setScalar(spout ? 1.35 + Math.sin(cycle * 4) * 0.08 : 0.01);
        }
        const vo = node.userData.viewOffset || { x: 0, z: 0 };
        const w = waveHeight(anchor.x + vo.x, anchor.z + vo.z, tick.elapsed, false);
        const whaleBase = LIFE_SEA_MOUNT_Y.whale != null ? LIFE_SEA_MOUNT_Y.whale : 0.42;
        node.position.y = LIFE_SEA_Y + whaleBase + (breach ? 0.32 : -0.02) + w * 0.2;
        const baseYaw = LIFE_KIND_YAW.whale != null ? LIFE_KIND_YAW.whale : 0;
        node.rotation.y = baseYaw + Math.sin(tick.elapsed * 0.06) * 0.08;
      } else if (kind === 'seaDragon') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.12) * 1.8;
        node.position.z = anchor.z + Math.cos(tick.elapsed * 0.1) * 1.2;
      } else if (kind === 'jellyfish') {
        const w = waveHeight(anchor.x, anchor.z, tick.elapsed, false);
        node.position.y = LIFE_SEA_Y + 0.08 + w * 0.15 + Math.sin(tick.elapsed * 0.8) * 0.08;
      } else if (kind === 'wenyao') {
        const hopT = (tick.elapsed * 0.42) % 3.6;
        const arc = Math.sin((hopT / 3.6) * Math.PI);
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.18) * 2.5 + hopT * 0.35;
        node.position.z = anchor.z + Math.cos(tick.elapsed * 0.15) * 1.2;
        const wenyaoBase = LIFE_SEA_MOUNT_Y.wenyao != null ? LIFE_SEA_MOUNT_Y.wenyao : 0.12;
        const skyLift = arc * 2.4;
        node.position.y = LIFE_SEA_Y + wenyaoBase + skyLift + Math.sin(tick.elapsed * 0.6) * 0.08;
        node.rotation.x = -arc * 0.35 - 0.25;
        node.rotation.y = 0.35 + Math.sin(tick.elapsed * 0.22) * 0.12;
      } else if (kind === 'chiru') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.2) * 1.2;
        node.rotation.y = Math.sin(tick.elapsed * 0.15) * 0.3;
      } else if (kind === 'kitsune') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.08) * 4;
        node.position.z = anchor.z + tick.elapsed * 0.35;
        node.position.y = LIFE_SKY_BASE + 6 + Math.sin(tick.elapsed * 0.25) * 0.6;
      } else if (kind === 'chenghuang') {
        node.position.x = anchor.x + tick.elapsed * 0.4;
        node.position.z = anchor.z + Math.sin(tick.elapsed * 0.3) * 2;
      } else if (kind === 'bifang') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.18) * 6;
        node.position.z = anchor.z + Math.cos(tick.elapsed * 0.15) * 4;
      } else if (kind === 'qingniao') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.14) * 3.5;
        node.position.z = anchor.z + Math.cos(tick.elapsed * 0.12) * 2.5;
        node.position.y = LIFE_SKY_BASE + 6.5 + Math.sin(tick.elapsed * 0.35) * 0.45;
        node.rotation.y = tick.elapsed * 0.08;
      } else if (kind === 'kunpeng') {
        const vo = node.userData.viewOffset || { x: 0, z: 0 };
        const baseYaw = LIFE_KIND_YAW.kunpeng != null ? LIFE_KIND_YAW.kunpeng : 0;
        node.position.x = anchor.x + vo.x + Math.sin(tick.elapsed * 0.04) * 3;
        node.rotation.y = baseYaw + tick.elapsed * 0.05;
        node.rotation.x = Math.sin(tick.elapsed * 0.07) * 0.05;
      } else if (kind === 'phoenix') {
        node.position.y = LIFE_SKY_BASE + 7 + Math.sin(tick.elapsed * 0.35) * 0.5;
        node.rotation.y = tick.elapsed * 0.06;
      } else if (kind === 'wyvern') {
        node.position.x = anchor.x + Math.sin(tick.elapsed * 0.07) * 5;
        node.rotation.y = -0.4 + Math.sin(tick.elapsed * 0.05) * 0.2;
      }
    }
  }

  function dispose() {
    if (disposed) return;
    disposed = true;
    while (group.children.length > 0) {
      const child = group.children[group.children.length - 1];
      group.remove(child);
      child.traverse((part) => {
        if (part.geometry && part.geometry.dispose) part.geometry.dispose();
        if (part.material) {
          if (Array.isArray(part.material)) {
            part.material.forEach((m) => m.dispose && m.dispose());
          } else if (part.material.dispose) {
            part.material.dispose();
          }
        }
      });
    }
    for (let d = 0; d < disposables.length; d++) {
      const item = disposables[d];
      if (item && item.dispose) item.dispose();
    }
    disposables.length = 0;
    creatures.length = 0;
    for (let m = 0; m < mixers.length; m++) {
      const entry = mixers[m];
      if (entry.mixer && entry.mixer.stopAllAction) entry.mixer.stopAllAction();
    }
    mixers.length = 0;
  }

  applyVisibility(0);
  applyGlow();

  return {
    group,
    creatures,
    update,
    setMix,
    setLowQuality,
    setVisible,
    dispose,
    ready
  };
}