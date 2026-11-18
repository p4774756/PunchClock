import * as THREE from 'three';
import { G } from './island-kit.js';
import { islandById } from './island-layout.js';
import {
  DATA_BODY_HEIGHT,
  FIGURE_TARGET_HEIGHT,
  buildCardTextModel,
  expandCurriculumInstances,
  fillAcupointCard,
  pickTodayCode,
  resolveTcmPickHit
} from './island-tcm-figure-logic.js';

export { resolveTcmPickHit };

const BRONZE_POINTS_URL = '/island/tcm-bronze-points.json';
const LOAD_BUFFER = 1.65;
const UNLOAD_BUFFER = 3.2;
const GOLD = 0xd4a84b;
const GOLD_EMI = 0xffd27a;
const MARKER = 0xfff1c4;
const TODAY_MARK = 0xffb15a;

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

export function buildBronzeFigure(ctx) {
  const kit = ctx.kit;
  const scale = ctx.scale != null ? ctx.scale : 1;
  const interactive = ctx.interactive !== false;
  const { addMesh, addBox, geo, mat } = kit;
  const group = new THREE.Group();
  group.scale.setScalar(scale * (FIGURE_TARGET_HEIGHT / DATA_BODY_HEIGHT));
  const goldMat = (extra) => mat(GOLD, { glow: GOLD_EMI, glowAmount: 0.22, ...extra });
  const addPart = (w, h, d, x, y, z, rx, rz) => {
    const m = addBox(group, w, h, d, GOLD, x, y, z, {
      night: 0xb8922e,
      glow: GOLD_EMI,
      glowAmount: 0.18,
      shadow: true
    });
    if (rx) m.rotation.x = rx;
    if (rz) m.rotation.z = rz;
    return m;
  };

  addBox(group, 0.5, 0.06, 0.34, 0, 0.03, 0.01, 0x6b3a2a);
  addPart(0.2, 0.09, 0.26, 0, 0.11, 0);
  addPart(0.17, 0.14, 0.2, -0.1, 0.2, 0.02);
  addPart(0.17, 0.14, 0.2, 0.1, 0.2, 0.02);
  addPart(0.14, 0.42, 0.17, -0.1, 0.48, -0.01);
  addPart(0.14, 0.42, 0.17, 0.1, 0.48, -0.01);
  addPart(0.1, 0.08, 0.1, -0.1, 0.08, 0.04);
  addPart(0.1, 0.08, 0.1, 0.1, 0.08, 0.04);
  addPart(0.28, 0.34, 0.15, 0, 0.82, 0);
  addPart(0.12, 0.08, 0.1, 0, 1.02, 0.02);
  addMesh(group, geo('sph', [0.115, 7, 6]), GOLD, 0, 1.14, 0, {
    night: 0xb8922e,
    glow: GOLD_EMI,
    glowAmount: 0.2
  });
  addPart(0.09, 0.36, 0.09, -0.22, 0.86, 0, 0, 0.22);
  addPart(0.09, 0.36, 0.09, 0.22, 0.86, 0, 0, -0.22);
  addPart(0.07, 0.28, 0.07, -0.34, 0.66, 0.02, 0.06, 0.12);
  addPart(0.07, 0.28, 0.07, 0.34, 0.66, 0.02, 0.06, -0.12);
  addPart(0.05, 0.05, 0.12, -0.38, 0.48, 0.04);
  addPart(0.05, 0.05, 0.12, 0.38, 0.48, 0.04);

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
  const doc = ctx.document || document;

  const tcmIsland = islandById('tcm');
  const anchor = ctx.anchor || { x: -16.8, z: -1.05 };

  let loaded = false;
  let loading = false;
  let disposed = false;
  let rootGroup = null;
  let pointMesh = null;
  let pickMesh = null;
  let instanceRows = [];
  let recordsByCode = {};
  let todayCode = null;
  let cardEl = null;
  let cardBody = null;
  let cardVisible = false;
  let modulePromise = null;

  window.__tcmFigure = {
    get loaded() { return loaded; },
    get figures() { return rootGroup ? 1 : 0; },
    get todayCode() { return todayCode; },
    get instanceCount() { return instanceRows.length; }
  };

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
    const baseR = reduced ? 0.016 : 0.018;
    const todayR = reduced ? 0.022 : 0.026;
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

  function disposeFigure() {
    if (rootGroup) {
      scene.remove(rootGroup);
      rootGroup.traverse((obj) => {
        if (obj.geometry) obj.geometry.dispose();
        if (obj.material) {
          if (Array.isArray(obj.material)) obj.material.forEach((m) => m.dispose());
          else obj.material.dispose();
        }
      });
      rootGroup = null;
    }
    if (pointMesh) {
      const idx = pickables.indexOf(pickMesh || pointMesh);
      if (idx >= 0) pickables.splice(idx, 1);
      pointMesh = null;
      pickMesh = null;
    }
    instanceRows = [];
    recordsByCode = {};
    loaded = false;
    todayCode = null;
    hideCard();
  }

  function buildPointMeshes(parent, rows) {
    const count = rows.length;
    const markerGeo = new THREE.SphereGeometry(0.018, 6, 5);
    const markerMat = kit.mat(MARKER, {
      glow: TODAY_MARK,
      glowAmount: 0.55,
      shadow: false
    });
    pointMesh = new THREE.InstancedMesh(markerGeo, markerMat, count);
    pointMesh.castShadow = false;
    pointMesh.receiveShadow = false;

    const pickScale = window.matchMedia('(max-width: 520px)').matches ? 1.45 : 1.2;
    const pickGeo = new THREE.SphereGeometry(0.018 * pickScale, 5, 4);
    const pickMat = new THREE.MeshBasicMaterial({ visible: false });
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

  async function loadFigure() {
    if (loaded || loading || disposed) return;
    loading = true;
    try {
      const res = await fetch(BRONZE_POINTS_URL);
      if (!res.ok) throw new Error('fetch ' + BRONZE_POINTS_URL);
      const records = await res.json();
      recordsByCode = {};
      for (let i = 0; i < records.length; i++) recordsByCode[records[i].code] = records[i];
      instanceRows = expandCurriculumInstances(records);
      todayCode = pickTodayCode(records, getNow());

      rootGroup = new THREE.Group();
      rootGroup.position.set(anchor.x, G, anchor.z);
      const body = buildBronzeFigure({ kit: kit, scale: 1, interactive: true });
      rootGroup.add(body);
      buildPointMeshes(body, instanceRows);
      scene.add(rootGroup);
      refreshTodayHighlight();
      loaded = true;
      if (shouldLoad()) showCardForInstance(findTodayInstanceKey(), { today: true });
    } catch (err) {
      console.warn('[Island] 銅人載入失敗', err);
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
    if (shouldLoad() && !loaded && !loading) {
      loadFigure();
      return;
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

export function replaceEntranceBronzeStatue(model, kit) {
  const hidden = [];
  const box = new THREE.Box3();
  model.traverse((obj) => {
    if (!obj.isMesh) return;
    const matName = obj.material && obj.material.name;
    if (matName !== 'bronze' && matName !== 'bronze_dark' && matName !== 'gold') return;
    const b = new THREE.Box3().setFromObject(obj);
    const c = b.getCenter(new THREE.Vector3());
    if (c.x < -0.3 && c.x > -0.9 && c.y > 0.15 && c.y < 1.3 && Math.abs(c.z) < 0.35) {
      obj.visible = false;
      hidden.push(obj);
      box.union(b);
    }
  });
  if (!hidden.length) return null;
  const center = box.getCenter(new THREE.Vector3());
  const fig = buildBronzeFigure({ kit: kit, scale: 0.52, interactive: false });
  fig.position.set(center.x, 0, center.z);
  const hint = new THREE.Mesh(
    new THREE.BoxGeometry(0.32, 1.05, 0.28),
    new THREE.MeshBasicMaterial({ visible: false })
  );
  hint.position.copy(fig.position);
  hint.position.y += 0.52;
  hint.userData.tcmEntranceHint = true;
  model.add(fig);
  model.add(hint);
  return { figure: fig, hint: hint };
}