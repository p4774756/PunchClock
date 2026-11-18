import * as THREE from 'three';
import { G } from './island-kit.js';
import { islandById } from './island-layout.js';
import {
  DATA_BODY_HEIGHT,
  FIGURE_TARGET_HEIGHT,
  buildCardTextModel,
  expandCurriculumInstances,
  fillAcupointCard,
  markerSurfacePosition,
  pickTodayCode,
  resolveTcmPickHit
} from './island-tcm-figure-logic.js';

export { resolveTcmPickHit };

const BRONZE_POINTS_URL = '/island/tcm-bronze-points.json';
const LOAD_BUFFER = 1.65;
const UNLOAD_BUFFER = 3.2;
const LOAD_RETRY_MS = 30_000;
const GOLD = 0xd4a84b;
const GOLD_EMI = 0xffd27a;
const MARKER = 0xfff1c4;
const TODAY_MARK = 0xffb15a;
const BASE_BROWN = 0x4a2e1c;

let styleInjected = false;

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
    '@media (max-width:520px){#tcmAcupointCard{left:max(0.55rem,env(safe-area-inset-left,0px));',
    'right:max(0.55rem,env(safe-area-inset-right,0px));width:auto;max-width:none;',
    'top:calc(4.25rem + env(safe-area-inset-top,0px));',
    'bottom:auto;max-height:min(38vh,18rem)}}'
  ].join('');
  doc.head.appendChild(css);
}

function goldOpts(extra) {
  return {
    night: 0xb8922e,
    glow: GOLD_EMI,
    glowAmount: 0.22,
    shadow: true,
    ...(extra || {})
  };
}

export function buildBronzeFigure(ctx) {
  const kit = ctx.kit;
  const scale = ctx.scale != null ? ctx.scale : 1;
  const interactive = ctx.interactive !== false;
  const { addMesh, addBox, geo, mat } = kit;
  const group = new THREE.Group();
  group.scale.setScalar(scale * (FIGURE_TARGET_HEIGHT / DATA_BODY_HEIGHT));

  const goldPart = (geometry, x, y, z, rot) => {
    const m = addMesh(group, geometry, GOLD, x, y, z, goldOpts());
    if (rot) {
      if (rot.x) m.rotation.x = rot.x;
      if (rot.y) m.rotation.y = rot.y;
      if (rot.z) m.rotation.z = rot.z;
    }
    return m;
  };
  const limb = (rt, rb, h, x, y, z, rot) => goldPart(geo('cyl', [rt, rb, h, 7, 0]), x, y + h / 2, z, rot);

  addMesh(
    group,
    geo('cyl', [0.2, 0.22, 0.06, 8, 0]),
    BASE_BROWN,
    0,
    0.03,
    0,
    { night: 0x2a1a10, glow: 0x000000, glowAmount: 0, shadow: true }
  );
  addMesh(
    group,
    geo('cyl', [0.12, 0.14, 0.05, 8, 0]),
    BASE_BROWN,
    0,
    0.085,
    0,
    { night: 0x2a1a10, glow: 0x000000, glowAmount: 0, shadow: true }
  );

  [-1, 1].forEach((side) => {
    const sx = side * 0.09;
    addBox(group, 0.07, 0.04, 0.14, 0x5a3a24, sx, 0.06, 0.02, goldOpts({ glowAmount: 0.08 }));
    limb(0.05, 0.055, 0.34, sx, 0.08, 0, { z: side * 0.04 });
    limb(0.058, 0.062, 0.44, sx, 0.42, -0.01, { z: side * 0.03 });
    const shoulderX = side * 0.12;
    const shoulderY = 1.44;
    const elbowX = side * 0.21;
    const elbowY = 1.1;
    const wristX = side * 0.29;
    const wristY = 0.9;
    goldPart(geo('cyl', [0.045, 0.052, 0.38, 7, 0]), (shoulderX + elbowX) / 2, (shoulderY + elbowY) / 2, 0.03, {
      z: side * 0.55 + 0.1,
      x: 0.12
    });
    goldPart(geo('cyl', [0.038, 0.042, 0.34, 7, 0]), (elbowX + wristX) / 2, (elbowY + wristY) / 2, 0.045, {
      z: side * 0.35 + 0.18,
      x: 0.35
    });
    addBox(group, 0.05, 0.035, 0.08, GOLD, wristX, 0.82, 0.05, goldOpts({ glowAmount: 0.18 }));
  });

  addBox(group, 0.22, 0.12, 0.14, GOLD, 0, 0.92, 0, goldOpts());
  addBox(group, 0.2, 0.28, 0.15, GOLD, 0, 1.1, 0.01, goldOpts());
  addBox(group, 0.24, 0.2, 0.16, GOLD, 0, 1.34, -0.01, goldOpts());
  limb(0.05, 0.055, 0.08, 0, 1.46, 0);
  goldPart(geo('sph', [0.115, 8, 7]), 0, 1.61, 0);

  if (!interactive) {
    group.userData.tcmDecor = true;
  }
  return group;
}

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
    fillAcupointCard(cardBody, model, doc);
    cardEl.hidden = false;
    cardVisible = true;
  }

  function refreshTodayHighlight() {
    const code = pickTodayCode(Object.values(recordsByCode), getNow());
    todayCode = code;
    if (!pointMesh || !instanceRows.length) return;
    const dummy = new THREE.Object3D();
    const baseR = reduced ? 0.024 : 0.026;
    const todayR = reduced ? 0.028 : 0.032;
    for (let i = 0; i < instanceRows.length; i++) {
      const row = instanceRows[i];
      const isToday = row.code === code;
      const r = isToday ? todayR : baseR;
      const pos = row.position;
      dummy.position.set(pos[0], pos[1], pos[2]);
      dummy.scale.set(r / baseR, r / baseR, r / baseR);
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
    const markerGeo = new THREE.SphereGeometry(0.026, 7, 5);
    const markerMat = kit.mat(MARKER, {
      glow: TODAY_MARK,
      glowAmount: 0.55,
      shadow: false
    });
    ownedGeometries.push(markerGeo);
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
      dummy.scale.set(1, 1, 1);
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

  function tryEntranceBronze() {
    if (entrancePlaced || !getTcmSiteModel) return;
    const model = getTcmSiteModel();
    if (!model) return;
    const result = replaceEntranceBronzeStatue(model, kit, pickables);
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
      const res = await fetch(BRONZE_POINTS_URL);
      if (!res.ok) throw new Error('fetch ' + BRONZE_POINTS_URL);
      const records = await res.json();
      recordsByCode = {};
      for (let i = 0; i < records.length; i++) recordsByCode[records[i].code] = records[i];
      instanceRows = expandRowsWithSurface(records);
      todayCode = pickTodayCode(records, getNow());

      rootGroup = new THREE.Group();
      rootGroup.position.set(anchor.x, G, anchor.z);
      const body = buildBronzeFigure({ kit: kit, scale: 1, interactive: true });
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

const ENTRANCE_FIG_SCALE = 0.46;
const ENTRANCE_FIG_LOCAL = { x: -0.58, y: 0, z: 1.85 };

export function replaceEntranceBronzeStatue(model, kit, pickables) {
  model.updateMatrixWorld(true);
  const hidden = [];
  const world = new THREE.Vector3();
  const local = new THREE.Vector3();
  const bronzeMats = ['bronze', 'bronze_dark', 'gold'];

  model.traverse((obj) => {
    if (!obj.isMesh) return;
    const matName = obj.material && obj.material.name;
    if (bronzeMats.indexOf(matName) < 0) return;
    obj.getWorldPosition(world);
    model.worldToLocal(local.copy(world));
    if (
      local.x > -0.88 && local.x < -0.32 &&
      local.y > 0.08 && local.y < 1.22 &&
      local.z > 0.55 && local.z < 2.15
    ) {
      obj.visible = false;
      hidden.push(obj);
    }
  });

  const fig = buildBronzeFigure({ kit: kit, scale: ENTRANCE_FIG_SCALE, interactive: false });
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

  if (!hidden.length) {
    console.warn('[Island] 館門舊銅像未命中，仍放置新銅人');
  }

  return { figure: fig, hint: hint, hiddenCount: hidden.length };
}