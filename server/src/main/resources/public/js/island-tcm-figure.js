import * as THREE from 'three';
import { PMREMGenerator } from 'three';
import { GLTFLoader } from 'three/addons/loaders/GLTFLoader.js';
import { G, nightHex } from './island-kit.js';
import { islandById } from './island-layout.js';
import {
  DATA_BODY_HEIGHT,
  FIGURE_TARGET_HEIGHT,
  buildCardTextModel,
  configureBronzeSurface,
  expandCurriculumInstances,
  fillAcupointCard,
  markerSurfacePosition,
  pickTodayCode,
  resolveTcmPickHit,
  hideEntranceBronzeMeshes,
  ENTRANCE_FIG_LOCAL
} from './island-tcm-figure-logic.js';

export { resolveTcmPickHit };

const BRONZE_POINTS_URL = '/island/tcm-bronze-points.json';
const BRONZE_SURFACE_URL = '/island/tcm-bronze-surface.json';
const BRONZE_FIG_URL = '/models/tcm-bronze-figure.glb';

let bronzeFigScene = null;
let bronzeFigPromise = null;
let bronzeEnvMap = null;
let bronzeEnvPmrem = null;
let bronzeEnvSourceTex = null;

function disposeBronzeEnvMap() {
  if (bronzeEnvMap) bronzeEnvMap.dispose();
  if (bronzeEnvSourceTex) bronzeEnvSourceTex.dispose();
  if (bronzeEnvPmrem) bronzeEnvPmrem.dispose();
  bronzeEnvMap = null;
  bronzeEnvPmrem = null;
  bronzeEnvSourceTex = null;
}

function ensureBronzeEnvMap(renderer) {
  if (bronzeEnvMap) return bronzeEnvMap;
  if (!renderer) return null;
  const w = 512;
  const h = 256;
  const canvas = document.createElement('canvas');
  canvas.width = w;
  canvas.height = h;
  const ctx2d = canvas.getContext('2d');
  const sky = ctx2d.createLinearGradient(0, 0, 0, h);
  sky.addColorStop(0, '#f8f2e8');
  sky.addColorStop(0.28, '#e8dfd0');
  sky.addColorStop(0.55, '#b8a080');
  sky.addColorStop(0.82, '#585048');
  sky.addColorStop(1, '#282420');
  ctx2d.fillStyle = sky;
  ctx2d.fillRect(0, 0, w, h);
  const sunSpot = ctx2d.createRadialGradient(w * 0.68, h * 0.26, 0, w * 0.68, h * 0.26, w * 0.4);
  sunSpot.addColorStop(0, 'rgba(255,215,150,0.92)');
  sunSpot.addColorStop(1, 'rgba(255,215,150,0)');
  ctx2d.fillStyle = sunSpot;
  ctx2d.fillRect(0, 0, w, h);
  bronzeEnvSourceTex = new THREE.CanvasTexture(canvas);
  bronzeEnvSourceTex.colorSpace = THREE.SRGBColorSpace;
  bronzeEnvSourceTex.mapping = THREE.EquirectangularReflectionMapping;
  bronzeEnvPmrem = new PMREMGenerator(renderer);
  bronzeEnvPmrem.compileEquirectangularShader();
  bronzeEnvMap = bronzeEnvPmrem.fromEquirectangular(bronzeEnvSourceTex).texture;
  bronzeEnvSourceTex.dispose();
  bronzeEnvSourceTex = null;
  bronzeEnvPmrem.dispose();
  bronzeEnvPmrem = null;
  return bronzeEnvMap;
}

function ensureBronzeFigureGlb() {
  if (bronzeFigScene) return Promise.resolve(bronzeFigScene);
  if (!bronzeFigPromise) {
    bronzeFigPromise = new Promise((resolve, reject) => {
      const loader = new GLTFLoader();
      loader.load(
        BRONZE_FIG_URL,
        (gltf) => {
          bronzeFigScene = gltf.scene;
          resolve(bronzeFigScene);
        },
        undefined,
        reject
      );
    });
  }
  return bronzeFigPromise;
}

function nightColorFromDay(hex, opts) {
  const bronze = opts && opts.bronze;
  const mapped = nightHex(hex);
  if (mapped !== null && !bronze) return new THREE.Color(mapped);
  const c = new THREE.Color(hex);
  const hsl = { h: 0, s: 0, l: 0 };
  c.getHSL(hsl);
  const out = new THREE.Color();
  if (bronze) {
    out.setHSL(hsl.h, Math.min(1, hsl.s * 0.82), 0.28 + hsl.l * 0.4);
  } else {
    out.setHSL(hsl.h, Math.min(1, hsl.s * 0.78), 0.12 + hsl.l * 0.42);
  }
  return out;
}

const BRONZE_SRGB = 0xcda862;
const MERIDIAN_SRGB = 0x4a3214;
const BRONZE_PED_SRGB = 0xab8748;

function bronzeMaterialFromGltf(oldMat, kit, opts) {
  const o = opts || {};
  const isBronze = !!o.bronze;
  const isFabric = !!o.fabric;
  const matName = (oldMat.name || '').toLowerCase();
  const darkPed = matName.indexOf('pedestal') >= 0 || matName === 'bronze_dark';
  const day = oldMat.color ? oldMat.color.clone() : new THREE.Color(
    isFabric ? 0xffffff : darkPed ? BRONZE_PED_SRGB : BRONZE_SRGB
  );
  if (o.meridian) {
    if (oldMat.color) day.copy(oldMat.color);
    else day.setHex(MERIDIAN_SRGB);
  } else if (isBronze && !isFabric) {
    const hasMap = !!(oldMat.map);
    if (hasMap) {
      day.setRGB(1, 1, 1);
    } else {
      day.setHex(darkPed ? BRONZE_PED_SRGB : BRONZE_SRGB);
    }
  }
  const night = nightColorFromDay(day.getHex(), { bronze: isBronze && !isFabric });
  const emissive = new THREE.Color(0x000000);
  const emissiveI = 0;
  let metalness = oldMat.metalness != null ? oldMat.metalness : (isBronze && !isFabric ? 0.97 : 0.04);
  let roughness = oldMat.roughness != null ? oldMat.roughness : (isFabric ? 0.62 : isBronze ? 0.26 : 0.82);
  if (isBronze && !isFabric) {
    metalness = Math.min(1.0, Math.max(0.95, metalness));
    roughness = Math.min(0.3, Math.max(0.22, roughness));
  } else if (isFabric) {
    metalness = 0;
    roughness = Math.max(0.55, Math.min(0.68, roughness));
  }
  const std = new THREE.MeshStandardMaterial({
    name: oldMat.name || '',
    color: day.clone(),
    metalness: metalness,
    roughness: roughness,
    emissive: emissive,
    emissiveIntensity: emissiveI,
    flatShading: false
  });
  if (oldMat.map) std.map = oldMat.map;
  if (oldMat.normalMap) {
    std.normalMap = oldMat.normalMap;
    std.normalScale = new THREE.Vector2(0.85, 0.85);
  }
  if (oldMat.roughnessMap) std.roughnessMap = oldMat.roughnessMap;
  if (oldMat.metalnessMap) std.metalnessMap = oldMat.metalnessMap;
  std.userData.day = day.clone();
  std.userData.night = night;
  std.userData.bronze = isBronze && !isFabric;
  std.userData.fabric = isFabric;
  std.userData.dayE = 0;
  std.userData.nightE = isBronze && !isFabric ? 0.018 : isFabric ? 0 : 0.006;
  std.userData.dayEnvI = isBronze && !isFabric ? 1.52 : isFabric ? 0 : 0.18;
  std.userData.nightEnvI = isBronze && !isFabric ? 0.82 : isFabric ? 0 : 0.12;
  std.envMapIntensity = std.userData.dayEnvI;
  kit.addMaterial(std);
  return std;
}

function applyBronzeEnvToRoot(root, envMap) {
  if (!envMap) return;
  root.traverse((obj) => {
    if (!obj.isMesh || !obj.material) return;
    const mats = Array.isArray(obj.material) ? obj.material : [obj.material];
    for (let i = 0; i < mats.length; i++) {
      const m = mats[i];
      if (
        m
        && m.isMeshStandardMaterial
        && (m.userData.bronze || (!m.userData.fabric && m.metalness >= 0.5))
      ) {
        m.envMap = envMap;
        if (m.userData.dayEnvI == null) {
          m.envMapIntensity = 1.35;
        }
        m.needsUpdate = true;
      }
    }
  });
}

function applyBronzeFigureMaterials(root, kit) {
  root.traverse((obj) => {
    if (!obj.isMesh) return;
    const oldMat = obj.material;
    const matName = (oldMat && oldMat.name) || '';
    const isMeridian =
      matName.indexOf('meridian') >= 0 || obj.name.indexOf('meridian') >= 0;
    const fabric =
      matName === 'sash_cloth' ||
      matName === 'bronze_sash' ||
      obj.name.indexOf('sash') >= 0;
    const isPlaque =
      matName === 'plaque' || obj.name.indexOf('plaque') >= 0;
    const isBronze = !fabric && !isMeridian && !isPlaque;
    if (isMeridian) {
      obj.material = bronzeMaterialFromGltf(
        oldMat && oldMat.isMeshStandardMaterial
          ? oldMat
          : {
              name: matName || 'meridian_dark',
              color: new THREE.Color(MERIDIAN_SRGB),
              metalness: 0.9,
              roughness: 0.45,
              emissive: new THREE.Color(0x000000),
              emissiveIntensity: 0
            },
        kit,
        { bronze: true, fabric: false, meridian: true }
      );
    } else if (isPlaque && oldMat && oldMat.isMeshStandardMaterial) {
      obj.material = bronzeMaterialFromGltf(oldMat, kit, { bronze: false, fabric: false });
      if (obj.material.metalness > 0.22) obj.material.metalness = 0.18;
      if (obj.material.roughness < 0.42) obj.material.roughness = 0.5;
      obj.material.envMapIntensity = 0.35;
    } else if (oldMat && oldMat.isMeshStandardMaterial) {
      obj.material = bronzeMaterialFromGltf(oldMat, kit, { bronze: isBronze, fabric: fabric });
    } else if (fabric) {
      obj.material = bronzeMaterialFromGltf(
        {
          name: matName || 'sash_cloth',
          color: new THREE.Color(0xddd0c8),
          metalness: 0,
          roughness: 0.62,
          emissive: new THREE.Color(0x000000),
          emissiveIntensity: 0
        },
        kit,
        { bronze: false, fabric: true }
      );
    } else {
      const darkPedestal = matName === 'bronze_dark' || obj.name.indexOf('pedestal') >= 0;
      obj.material = bronzeMaterialFromGltf(
        {
          name: matName || 'bronze',
          color: new THREE.Color(darkPedestal ? BRONZE_PED_SRGB : BRONZE_SRGB),
          metalness: darkPedestal ? 0.96 : 0.97,
          roughness: darkPedestal ? 0.28 : 0.26,
          emissive: new THREE.Color(0x000000),
          emissiveIntensity: 0
        },
        kit,
        { bronze: true, fabric: false }
      );
    }
    if (oldMat && typeof oldMat.dispose === 'function' && oldMat !== obj.material) oldMat.dispose();
    obj.castShadow = true;
    obj.receiveShadow = true;
  });
}
/** 模型正面 +Z；yaw 0 時面朝庭院入口（與 PR #214 一致）。 */
const COURTYARD_FIG_YAW = 0;
const LOAD_BUFFER = 1.65;
const UNLOAD_BUFFER = 3.2;
const LOAD_RETRY_MS = 30_000;
const MARKER = 0x8a6a30;
const MARKER_VIS_R = 0.0045;
const TODAY_MARK = 0xffb15a;
const MARKER_MERIDIAN_DEDUP_M = 0.03;

let styleInjected = false;

function markerDedupHideSet(rows, recordsByCode) {
  const byKey = new Map();
  for (let i = 0; i < rows.length; i++) {
    const row = rows[i];
    const rec = recordsByCode[row.code];
    const mer = (rec && rec.meridian) || '';
    const side = row.instanceKey.endsWith(':-1') ? -1 : 1;
    const key = mer + '|' + side;
    if (!byKey.has(key)) byKey.set(key, []);
    byKey.get(key).push(row);
  }
  const hide = new Set();
  for (const group of byKey.values()) {
    const kept = [];
    for (let g = 0; g < group.length; g++) {
      const row = group[g];
      const p = row.position;
      let dup = false;
      for (let k = 0; k < kept.length; k++) {
        const q = kept[k].position;
        const d = Math.hypot(p[0] - q[0], p[1] - q[1], p[2] - q[2]);
        if (d < MARKER_MERIDIAN_DEDUP_M) {
          dup = true;
          hide.add(row.instanceKey);
          break;
        }
      }
      if (!dup) kept.push(row);
    }
  }
  return hide;
}

function ensureCardStyle(doc) {
  if (styleInjected) return;
  styleInjected = true;
  const css = doc.createElement('style');
  css.textContent = [
    '#tcmAcupointCard{position:fixed;z-index:8;max-width:min(22rem,calc(100vw - 1.5rem));',
    'top:calc(4.6rem + env(safe-area-inset-top,0px));right:max(0.75rem,env(safe-area-inset-right,0px));',
    'left:auto;max-height:min(52vh,24rem);overflow:auto;padding:0.85rem 0.95rem 0.75rem;',
    'border-radius:14px;background:rgba(255,250,246,0.97);color:#211d1d;',
    'box-shadow:0 12px 28px rgba(33,29,29,0.16);border:1px solid rgba(107,58,42,0.12);',
    'font-size:0.92rem;line-height:1.45}',
    '#tcmAcupointCard[hidden]{display:none!important}',
    '#tcmAcupointCard h2{margin:0 0 0.35rem;font-size:1.05rem}',
    '#tcmAcupointCard .tcm-card-sub{margin:0 0 0.55rem;color:#5a4d42;font-size:0.88rem}',
    '#tcmAcupointCard .tcm-card-date{margin:0 0 0.5rem;font-size:0.85rem;color:#6b3a2a}',
    '#tcmAcupointCard p{margin:0.35rem 0}',
    '#tcmAcupointCard .tcm-card-disclaimer{margin-top:0.65rem;font-size:0.78rem;color:#7a6e64}',
    '#tcmAcupointCardClose{position:absolute;top:0.45rem;right:0.45rem;width:2rem;height:2rem;',
    'border:0;border-radius:999px;background:rgba(107,58,42,0.08);color:#3a2620;font-size:1.1rem;cursor:pointer}',
  '#tcmAcupointCard .tcm-card-more{margin-top:0.45rem;font-size:0.88rem}',
  '#tcmAcupointCard .tcm-card-more summary{cursor:pointer;color:#6b3a2a;font-weight:600}',
  '#tcmAcupointCard .tcm-card-indications-line{margin:0.35rem 0 0;line-height:1.4}',
    '@media (max-width:520px){#tcmAcupointCard{left:max(0.55rem,env(safe-area-inset-left,0px));',
    'right:max(0.55rem,env(safe-area-inset-right,0px));width:auto;max-width:none;top:auto;',
    'bottom:calc(0.85rem + 8rem + 0.4rem + env(safe-area-inset-bottom,0px));',
    'max-height:min(30vh,14rem);-webkit-overflow-scrolling:touch}',
    'body.is-exploring #tcmAcupointCard{bottom:max(',
    'calc(0.85rem + 8rem + 0.4rem + env(safe-area-inset-bottom,0px)),',
    'calc(0.7rem + 2.25rem + 0.45rem + env(safe-area-inset-bottom,0px)))}}'
  ].join('');
  doc.head.appendChild(css);
}

export function buildBronzeFigure(ctx) {
  const kit = ctx.kit;
  const scale = ctx.scale != null ? ctx.scale : 1;
  const interactive = ctx.interactive !== false;
  const renderer = ctx.renderer;
  if (!bronzeFigScene) {
    throw new Error('bronze GLB not loaded; call ensureBronzeFigureGlb() first');
  }
  const group = bronzeFigScene.clone(true);
  group.scale.setScalar(scale * (FIGURE_TARGET_HEIGHT / DATA_BODY_HEIGHT));
  applyBronzeFigureMaterials(group, kit);
  const envMap = ensureBronzeEnvMap(renderer);
  applyBronzeEnvToRoot(group, envMap);
  if (!interactive) {
    group.userData.tcmDecor = true;
  }
  return group;
}

export { disposeBronzeEnvMap };

export { ensureBronzeFigureGlb };

export function createTcmFigureController(ctx) {
  const scene = ctx.scene;
  const kit = ctx.kit;
  const pickables = ctx.pickables;
  const reduced = !!ctx.reduced;
  const getPlayer = ctx.getPlayer;
  const getNow = ctx.getNow || (() => Date.now());
  const onZoneClick = ctx.onZoneClick;
  const islandUiOpen = ctx.islandUiOpen;
  const getTcmSiteModel = ctx.getTcmSiteModel;
  const doc = ctx.document || document;

  const tcmIsland = islandById('tcm');
  const anchor = ctx.anchor || { x: -16.8, z: -1.05 };

  let loaded = false;
  let loading = false;
  let loadFailedAt = 0;
  let disposed = false;
  let rootGroup = null;
  let pointMesh = null;
  let pickMesh = null;
  let ownedMaterials = [];
  let ownedGeometries = [];
  let instanceRows = [];
  let recordsByCode = {};
  let todayCode = null;
  let cardEl = null;
  let cardBody = null;
  let cardVisible = false;
  let entrancePlaced = false;

  const figState = {
    get loaded() { return loaded; },
    get figures() { return rootGroup ? 1 : 0; },
    get todayCode() { return todayCode; },
    get instanceCount() { return instanceRows.length; },
    entrance: { hidden: 0, placed: false }
  };
  window.__tcmFigure = figState;

  function distToTcm() {
    const p = getPlayer();
    if (!p || !tcmIsland) return Infinity;
    return Math.hypot(p.x - tcmIsland.x, p.z - tcmIsland.z);
  }

  function shouldLoad() {
    if (!tcmIsland) return false;
    return distToTcm() < tcmIsland.visualR + LOAD_BUFFER;
  }

  function shouldUnload() {
    if (!tcmIsland) return false;
    return distToTcm() > tcmIsland.visualR + UNLOAD_BUFFER;
  }

  function ensureCard() {
    ensureCardStyle(doc);
    if (cardEl) return;
    cardEl = doc.createElement('aside');
    cardEl.id = 'tcmAcupointCard';
    cardEl.hidden = true;
    cardEl.setAttribute('aria-live', 'polite');
    const close = doc.createElement('button');
    close.id = 'tcmAcupointCardClose';
    close.type = 'button';
    close.setAttribute('aria-label', '關閉穴位說明');
    close.textContent = '×';
    close.addEventListener('click', () => hideCard());
    cardBody = doc.createElement('div');
    cardBody.className = 'tcm-card-body';
    cardEl.appendChild(close);
    cardEl.appendChild(cardBody);
    doc.body.appendChild(cardEl);
    doc.addEventListener('pointerdown', onDocPointer, true);
    doc.addEventListener('keydown', onDocKey, true);
  }

  function onDocPointer(ev) {
    if (!cardVisible || !cardEl) return;
    if (cardEl.contains(ev.target)) return;
    const stage = doc.getElementById('islandStage');
    if (stage && (stage === ev.target || stage.contains(ev.target))) return;
    hideCard();
  }

  function onDocKey(ev) {
    if (!cardVisible || ev.key !== 'Escape') return;
    if (islandUiOpen && islandUiOpen()) return;
    hideCard();
    ev.preventDefault();
    ev.stopPropagation();
  }

  function hideCard() {
    if (!cardEl) return;
    cardVisible = false;
    cardEl.hidden = true;
  }

  function showCardForInstance(instanceKey, opts) {
    ensureCard();
    const row = instanceRows.find((r) => r.instanceKey === instanceKey);
    if (!row) return;
    const record = recordsByCode[row.code] || row;
    const today = opts && opts.today;
    const model = buildCardTextModel(record, { today: today, nowMs: getNow() });
    const mobile = typeof window !== 'undefined' && window.matchMedia('(max-width: 520px)').matches;
    fillAcupointCard(cardBody, model, doc, { compact: !!(today && mobile) });
    cardEl.hidden = false;
    cardVisible = true;
  }

  function refreshTodayHighlight() {
    const code = pickTodayCode(Object.values(recordsByCode), getNow());
    todayCode = code;
    if (!pointMesh || !instanceRows.length) return;
    const dummy = new THREE.Object3D();
    const baseR = MARKER_VIS_R;
    const todayR = reduced ? 0.007 : 0.008;
    const hideSet = markerDedupHideSet(instanceRows, recordsByCode);
    for (let i = 0; i < instanceRows.length; i++) {
      const row = instanceRows[i];
      const isToday = row.code === code;
      const r = isToday ? todayR : baseR;
      const pos = row.position;
      dummy.position.set(pos[0], pos[1], pos[2]);
      const hidden = hideSet.has(row.instanceKey) && !isToday;
      const scaleMul = hidden ? 0 : r / baseR;
      dummy.scale.set(scaleMul, scaleMul, scaleMul);
      dummy.updateMatrix();
      pointMesh.setMatrixAt(i, dummy.matrix);
      const col = new THREE.Color(isToday ? TODAY_MARK : MARKER);
      pointMesh.setColorAt(i, col);
    }
    pointMesh.instanceMatrix.needsUpdate = true;
    if (pointMesh.instanceColor) pointMesh.instanceColor.needsUpdate = true;
  }

  function disposeOwned() {
    for (let i = 0; i < ownedGeometries.length; i++) ownedGeometries[i].dispose();
    for (let i = 0; i < ownedMaterials.length; i++) ownedMaterials[i].dispose();
    ownedGeometries = [];
    ownedMaterials = [];
  }

  function disposeFigure() {
    if (rootGroup) {
      scene.remove(rootGroup);
      rootGroup = null;
    }
    if (pointMesh) {
      const idx = pickables.indexOf(pickMesh || pointMesh);
      if (idx >= 0) pickables.splice(idx, 1);
      pointMesh = null;
      pickMesh = null;
    }
    disposeOwned();
    instanceRows = [];
    recordsByCode = {};
    loaded = false;
    todayCode = null;
    loadFailedAt = 0;
    hideCard();
  }

  function buildPointMeshes(parent, rows) {
    const count = rows.length;
    const envMap = ensureBronzeEnvMap(ctx.renderer);
    const hideSet = markerDedupHideSet(rows, recordsByCode);
    const markerGeo = new THREE.SphereGeometry(MARKER_VIS_R, 8, 6);
    const markerMat = new THREE.MeshStandardMaterial({
      color: MARKER,
      metalness: 0.9,
      roughness: 0.4,
      envMap: envMap || null,
      envMapIntensity: envMap ? 1.0 : 0
    });
    ownedGeometries.push(markerGeo);
    ownedMaterials.push(markerMat);
    pointMesh = new THREE.InstancedMesh(markerGeo, markerMat, count);
    pointMesh.castShadow = false;
    pointMesh.receiveShadow = false;

    const mobile = typeof window !== 'undefined' && window.matchMedia('(max-width: 520px)').matches;
    const pickRadius = mobile ? 0.06 : 0.045;
    const pickGeo = new THREE.SphereGeometry(pickRadius, 5, 4);
    const pickMat = new THREE.MeshBasicMaterial({ visible: false });
    ownedGeometries.push(pickGeo);
    ownedMaterials.push(pickMat);
    pickMesh = new THREE.InstancedMesh(pickGeo, pickMat, count);
    pickMesh.userData.tcmPointPick = { instanceKeys: rows.map((r) => r.instanceKey) };

    const dummy = new THREE.Object3D();
    for (let i = 0; i < count; i++) {
      const row = rows[i];
      const pos = row.position;
      dummy.position.set(pos[0], pos[1], pos[2]);
      dummy.rotation.set(0, 0, 0);
      const hidden = hideSet.has(row.instanceKey) && row.code !== todayCode;
      dummy.scale.set(hidden ? 0 : 1, hidden ? 0 : 1, hidden ? 0 : 1);
      dummy.updateMatrix();
      pointMesh.setMatrixAt(i, dummy.matrix);
      pickMesh.setMatrixAt(i, dummy.matrix);
    }
    pointMesh.instanceMatrix.needsUpdate = true;
    pickMesh.instanceMatrix.needsUpdate = true;
    parent.add(pointMesh);
    parent.add(pickMesh);
    pickables.push(pickMesh);
  }

  function expandRowsWithSurface(records) {
    const expanded = expandCurriculumInstances(records);
    for (let i = 0; i < expanded.length; i++) {
      const row = expanded[i];
      const rec = recordsByCode[row.code];
      const side = row.instanceKey.endsWith(':-1') ? -1 : row.instanceKey.endsWith(':1') ? 1 : 1;
      const surf = markerSurfacePosition(rec || row, side);
      row.position = surf;
    }
    return expanded;
  }

  async function tryEntranceBronze() {
    if (entrancePlaced || !getTcmSiteModel) return;
    const model = getTcmSiteModel();
    if (!model) return;
    try {
      await ensureBronzeFigureGlb();
    } catch (err) {
      console.warn('[Island] 館門銅人 GLB 載入失敗', err);
      return;
    }
    const result = replaceEntranceBronzeStatue(model, kit, pickables, { renderer: ctx.renderer });
    entrancePlaced = !!result;
    figState.entrance = {
      hidden: result ? result.hiddenCount : 0,
      placed: !!result
    };
  }

  async function loadFigure() {
    if (loaded || loading || disposed) return;
    if (loadFailedAt && getNow() - loadFailedAt < LOAD_RETRY_MS) return;
    loading = true;
    try {
      await ensureBronzeFigureGlb();
      const [pointsRes, surfaceRes] = await Promise.all([
        fetch(BRONZE_POINTS_URL),
        fetch(BRONZE_SURFACE_URL)
      ]);
      if (!pointsRes.ok) throw new Error('fetch ' + BRONZE_POINTS_URL);
      if (!surfaceRes.ok) throw new Error('fetch ' + BRONZE_SURFACE_URL);
      const records = await pointsRes.json();
      configureBronzeSurface(await surfaceRes.json());
      recordsByCode = {};
      for (let i = 0; i < records.length; i++) recordsByCode[records[i].code] = records[i];
      instanceRows = expandRowsWithSurface(records);
      todayCode = pickTodayCode(records, getNow());

      rootGroup = new THREE.Group();
      rootGroup.position.set(anchor.x, G, anchor.z);
      const body = buildBronzeFigure({ kit: kit, renderer: ctx.renderer, scale: 1, interactive: true });
      body.rotation.y = COURTYARD_FIG_YAW;
      rootGroup.add(body);
      buildPointMeshes(body, instanceRows);
      scene.add(rootGroup);
      refreshTodayHighlight();
      loaded = true;
      loadFailedAt = 0;
      tryEntranceBronze();
      if (shouldLoad()) showCardForInstance(findTodayInstanceKey(), { today: true });
    } catch (err) {
      console.warn('[Island] 銅人載入失敗', err);
      loadFailedAt = getNow();
      disposeFigure();
    } finally {
      loading = false;
    }
  }

  function findTodayInstanceKey() {
    if (!todayCode) return null;
    const row = instanceRows.find((r) => r.code === todayCode);
    return row ? row.instanceKey : null;
  }

  function handleZoneClick(zone) {
    if (!zone || typeof zone !== 'object') return false;
    if (zone.type === 'tcm-point') {
      const row = instanceRows.find((r) => r.instanceKey === zone.instanceKey);
      showCardForInstance(zone.instanceKey, { today: !!(row && row.code === todayCode) });
      return true;
    }
    if (zone.type === 'tcm-entrance-hint') {
      const key = findTodayInstanceKey();
      if (key) showCardForInstance(key, { today: true });
      else {
        ensureCard();
        while (cardBody.firstChild) cardBody.removeChild(cardBody.firstChild);
        const p = doc.createElement('p');
        p.textContent = '庭院有穴位銅人可點';
        cardBody.appendChild(p);
        cardEl.hidden = false;
        cardVisible = true;
      }
      return true;
    }
    return false;
  }

  function tick() {
    if (disposed) return;
    if (shouldUnload() && loaded) {
      disposeFigure();
      return;
    }
    if (shouldLoad()) {
      if (!loaded && !loading) loadFigure();
      else if (!entrancePlaced) tryEntranceBronze();
    }
    if (loaded) {
      const nextCode = pickTodayCode(Object.values(recordsByCode), getNow());
      if (nextCode !== todayCode) {
        todayCode = nextCode;
        refreshTodayHighlight();
      }
    }
  }

  function destroy() {
    disposed = true;
    disposeFigure();
    disposeBronzeEnvMap();
    if (cardEl && cardEl.parentNode) cardEl.parentNode.removeChild(cardEl);
    cardEl = null;
    doc.removeEventListener('pointerdown', onDocPointer, true);
    doc.removeEventListener('keydown', onDocKey, true);
  }

  return {
    tick: tick,
    handleZoneClick: handleZoneClick,
    destroy: destroy,
    showTodayCard: () => {
      const key = findTodayInstanceKey();
      if (key) showCardForInstance(key, { today: true });
    }
  };
}

const ENTRANCE_FIG_SCALE = 0.78;

export function replaceEntranceBronzeStatue(model, kit, pickables, opts) {
  model.updateMatrixWorld(true);
  const hiddenCount = hideEntranceBronzeMeshes(model);
  const renderer = opts && opts.renderer;

  const fig = buildBronzeFigure({ kit: kit, renderer: renderer, scale: ENTRANCE_FIG_SCALE, interactive: false });
  fig.position.set(ENTRANCE_FIG_LOCAL.x, ENTRANCE_FIG_LOCAL.y, ENTRANCE_FIG_LOCAL.z);
  fig.rotation.y = 0.22;

  const hintGeo = new THREE.BoxGeometry(0.32, 1.05, 0.28);
  const hintMat = new THREE.MeshBasicMaterial({ visible: false });
  const hint = new THREE.Mesh(hintGeo, hintMat);
  hint.position.set(ENTRANCE_FIG_LOCAL.x, ENTRANCE_FIG_SCALE * 0.95, ENTRANCE_FIG_LOCAL.z);
  hint.userData.tcmEntranceHint = true;
  model.add(fig);
  model.add(hint);
  if (pickables) pickables.push(hint);

  if (!hiddenCount) {
    console.warn('[Island] 館門舊銅像未命中，仍放置新銅人');
  }

  return { figure: fig, hint: hint, hiddenCount: hiddenCount };
}