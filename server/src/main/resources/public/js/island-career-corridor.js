import * as THREE from 'three';
import { G } from './island-kit.js';
import { islandById, buildingById } from './island-layout.js';
import { layInstancedPaving } from './island-decor-scene.js';
import {
  CAREER_TIMELINE_URL,
  buildCardTextModel,
  fillCareerCard,
  careerInteractZones,
  resolveCareerPickHit
} from './island-career-corridor-logic.js';
import { CAREER_EXHIBIT_LAYOUT, CAREER_ZONE_SIGNS } from './island-career-corridor-data.js';
import { careerCardEscapeShouldClose } from './island-career-corridor-logic.js';

export { resolveCareerPickHit };
export { careerCardEscapeShouldClose };

const LOAD_BUFFER = 1.4;
const UNLOAD_BUFFER = 2.8;

function buildPropMesh(kit, entry, group) {
  const { addBox, addMesh, geo, paint } = kit;
  const id = entry.id;
  const S = 1.65;
  if (id === 'thu-2007') {
    const boardMap = paint(192, 128, (g, w, h) => {
      g.fillStyle = '#1a2838';
      g.fillRect(0, 0, w, h);
      g.fillStyle = '#e8f4ea';
      g.font = '600 16px sans-serif';
      g.textAlign = 'left';
      g.fillText('資料結構', 12, 42);
      g.fillText('演算法', 12, 68);
    });
    addBox(group, 0.55 * S, 0.4 * S, 0.02, 0x1a2838, 0, 0.72 * S, -0.12);
    addMesh(group, new THREE.PlaneGeometry(0.48 * S, 0.3 * S), 0xffffff, 0, 0.74 * S, -0.108, {
      map: boardMap,
      side: THREE.DoubleSide,
      shadow: false
    });
    addBox(group, 0.22 * S, 0.03 * S, 0.16 * S, 0x5d4a38, 0.12, 0.46 * S, 0.05);
    addMesh(group, geo('box', [0.16 * S, 0.02 * S, 0.11 * S]), 0x8a9aaa, 0.14 * S, 0.49 * S, 0.05, { shadow: false });
  } else if (id === 'firmware-2013-2022') {
    const pcbMap = paint(96, 64, (g, w, h) => {
      g.fillStyle = '#1b5538';
      g.fillRect(0, 0, w, h);
      g.strokeStyle = '#c8e0a8';
      g.lineWidth = 1;
      for (let i = 8; i < w; i += 14) {
        g.beginPath();
        g.moveTo(i, 4);
        g.lineTo(i, h - 4);
        g.stroke();
      }
    });
    addMesh(group, new THREE.PlaneGeometry(0.28 * S, 0.2 * S), 0xffffff, -0.06 * S, 0.5 * S, 0.02, {
      map: pcbMap,
      side: THREE.DoubleSide,
      shadow: false
    });
    addMesh(group, geo('box', [0.08 * S, 0.02 * S, 0.08 * S]), 0x2a3848, 0.04 * S, 0.54 * S, 0.05, { shadow: false });
    addMesh(group, geo('cyl', [0.016 * S, 0.016 * S, 0.1 * S, 6, 0]), 0x4a6078, 0.12 * S, 0.58 * S, 0.04, { shadow: false });
    addMesh(group, geo('cyl', [0.02 * S, 0.02 * S, 0.08 * S, 5, 0]), 0x3cf0ff, 0.12 * S, 0.66 * S, 0.04, {
      glow: 0x3cf0ff,
      glowAmount: 0.55,
      shadow: false
    });
  } else if (id === 'software-2022-now') {
    const chartMap = paint(128, 80, (g, w, h) => {
      g.fillStyle = '#0e1824';
      g.fillRect(0, 0, w, h);
      g.strokeStyle = '#7ec8ea';
      g.lineWidth = 2;
      g.beginPath();
      g.moveTo(8, h - 12);
      g.lineTo(30, 52);
      g.lineTo(52, 58);
      g.lineTo(74, 28);
      g.lineTo(w - 8, 18);
      g.stroke();
    });
    addMesh(group, new THREE.PlaneGeometry(0.22 * S, 0.14 * S), 0xffffff, -0.14 * S, 0.58 * S, 0.02, {
      map: chartMap,
      side: THREE.DoubleSide,
      shadow: false
    });
    addMesh(group, geo('cyl', [0.09 * S, 0.09 * S, 0.02, 10, 0]), 0xf0d078, 0.1 * S, 0.46 * S, 0.04, {
      night: 0xd4a84b,
      glow: 0xffd27a,
      glowAmount: 0.6,
      shadow: false
    });
    addBox(group, 0.16 * S, 0.12 * S, 0.1 * S, 0x5b3a8c, 0.12 * S, 0.52 * S, 0);
    addBox(group, 0.12 * S, 0.05 * S, 0.02, 0xff6ad8, 0.12 * S, 0.6 * S, 0.06, { glow: 0xff4ec8, glowAmount: 0.65, shadow: false });
    addBox(group, 0.07 * S, 0.14 * S, 0.02, 0x1a2838, 0.2 * S, 0.48 * S, 0);
    addMesh(group, geo('box', [0.08 * S, 0.11 * S, 0.02]), 0x7ec8ea, 0.21 * S, 0.48 * S, 0, { shadow: false });
    addMesh(group, geo('cyl', [0.055 * S, 0.075 * S, 0.02, 5, 0]), 0x9fd9b4, 0.26 * S, 0.42 * S, 0.05, { shadow: false });
  } else if (id === 'punchclock') {
    addMesh(group, geo('sph', [0.1 * S, 8, 6]), 0xf06a2a, 0, 0.52 * S, 0, { glow: 0xffb15a, glowAmount: 0.45, shadow: false });
    addBox(group, 0.22 * S, 0.04 * S, 0.12 * S, 0x1f4d73, 0, 0.38 * S, 0);
  }
}

function buildPhaseSigns(rootGroup, kit) {
  const { addBox, addMesh, paint } = kit;
  for (let i = 0; i < CAREER_ZONE_SIGNS.length; i++) {
    const sign = CAREER_ZONE_SIGNS[i];
    const map = paint(280, 88, (g, w, h) => {
      g.fillStyle = '#f3f6f9';
      g.fillRect(0, 0, w, h);
      g.fillStyle = '#1a2838';
      g.font = '700 22px sans-serif';
      g.textAlign = 'center';
      g.textBaseline = 'middle';
      g.fillText(sign.phase, w / 2, h * 0.38);
      g.font = '600 18px sans-serif';
      g.fillStyle = '#3a5068';
      g.fillText(sign.years, w / 2, h * 0.68);
    });
    const g = new THREE.Group();
    g.position.set(sign.x, G, sign.z);
    g.rotation.y = sign.yaw || 0;
    addBox(g, 0.52, 0.72, 0.06, 0x8a9aaa, 0, 0.36, 0, { shadow: false });
    addMesh(g, new THREE.PlaneGeometry(0.46, 0.22), 0xffffff, 0, 0.68, 0.035, {
      map,
      side: THREE.DoubleSide,
      shadow: false
    });
    rootGroup.add(g);
  }
}

function buildOutdoorEasterEggs(scene, kit) {
  const { addBox, addMesh, geo, paint } = kit;
  const island = islandById('computer');
  if (!island) return;
  const group = new THREE.Group();
  group.position.set(island.x, G, island.z);

  const bench = new THREE.Group();
  bench.position.set(-1.55, 0, -2.65);
  bench.rotation.y = 0.08;
  addBox(bench, 0.55, 0.06, 0.22, 0x2a3848, 0, 0.34, 0);
  const keys = ['W', 'A', 'S', 'D'];
  keys.forEach((_, i) => {
    addBox(bench, 0.1, 0.04, 0.1, 0xf3f6f9, -0.18 + i * 0.12, 0.4, 0);
  });
  group.add(bench);

  const duck = new THREE.Group();
  duck.position.set(-0.35, 0, -2.85);
  addMesh(duck, geo('sph', [0.08, 7, 6]), 0xf6d35a, 0, 0.12, 0, { shadow: false });
  addMesh(duck, geo('sph', [0.05, 6, 5]), 0xf6d35a, 0.07, 0.15, 0.04, { shadow: false });
  addMesh(duck, geo('cone', [0.04, 0.08, 4]), 0xf06a2a, 0.11, 0.14, 0.06, { rot: [0, 0, -0.4], shadow: false });
  group.add(duck);

  const helloMap = paint(320, 80, (g, w, h) => {
    g.fillStyle = '#051018';
    g.fillRect(0, 0, w, h);
    g.font = '700 42px monospace';
    g.textAlign = 'center';
    g.textBaseline = 'middle';
    g.strokeStyle = '#3cf0ff';
    g.lineWidth = 3;
    g.strokeText('Hello World', w / 2, h / 2);
    g.fillStyle = '#7ec8ea';
    g.fillText('Hello World', w / 2, h / 2);
  });
  const neon = new THREE.Group();
  neon.position.set(0.85, 0, -3.05);
  neon.rotation.y = 0.12;
  addBox(neon, 0.72, 0.22, 0.05, 0x24384c, 0, 0.62, 0);
  addMesh(neon, new THREE.PlaneGeometry(0.68, 0.18), 0xffffff, 0, 0.62, 0.028, {
    map: helloMap,
    glow: 0x3cf0ff,
    glowAmount: 0.95,
    side: THREE.DoubleSide,
    shadow: false
  });
  group.add(neon);

  const board = new THREE.Group();
  board.position.set(-1.05, 0, -2.35);
  addBox(board, 0.45, 0.32, 0.03, 0xf3f6f9, 0, 0.54, 0);
  for (let i = 0; i < 8; i++) {
    const c = i % 3 === 0 ? 0xfff3a8 : i % 3 === 1 ? 0xb8e8ff : 0xffc4d8;
    addBox(board, 0.06, 0.06, 0.01, c, -0.14 + (i % 4) * 0.09, 0.52 + Math.floor(i / 4) * 0.08, 0.018, { shadow: false });
  }
  group.add(board);

  scene.add(group);
}

let styleInjected = false;

function ensureCardStyle(doc) {
  if (styleInjected) return;
  styleInjected = true;
  const css = doc.createElement('style');
  css.textContent = [
    '#careerExhibitCard{position:fixed;z-index:8;max-width:min(22rem,calc(100vw - 1.5rem));',
    'top:calc(4.6rem + env(safe-area-inset-top,0px));right:max(0.75rem,env(safe-area-inset-right,0px));',
    'left:auto;max-height:min(52vh,24rem);overflow:auto;padding:0.85rem 0.95rem 0.75rem;',
    'border-radius:14px;background:rgba(255,250,246,0.97);color:#1a2630;',
    'box-shadow:0 12px 28px rgba(33,29,29,0.16);border:1px solid rgba(36,56,76,0.14);',
    'font-size:0.92rem;line-height:1.45}',
    '#careerExhibitCard[hidden]{display:none!important}',
    '#careerExhibitCard h2{margin:0 0 0.35rem;font-size:1.05rem}',
    '#careerExhibitCard .career-card-meta{margin:0 0 0.55rem;color:#3a5068;font-size:0.88rem}',
    '#careerExhibitCard .career-card-summary{margin:0.35rem 0}',
    '#careerExhibitCard .career-card-prop{margin:0.35rem 0 0;color:#5a7088;font-size:0.85rem}',
    '#careerExhibitCardClose{position:absolute;top:0.45rem;right:0.45rem;width:2rem;height:2rem;',
    'border:0;border-radius:999px;background:rgba(36,56,76,0.08);color:#1a2630;font-size:1.1rem;cursor:pointer}',
    '#careerExhibitCard .career-enter-lab{margin-top:0.65rem;display:inline-block;padding:0.4rem 0.75rem;',
    'border-radius:999px;border:0;background:#1f4d73;color:#fff;font-size:0.88rem;cursor:pointer}',
    '#careerExhibitCard .career-enter-lab[hidden]{display:none!important}',
    '@media (max-width:520px){#careerExhibitCard{left:max(0.55rem,env(safe-area-inset-left,0px));',
    'right:max(0.55rem,env(safe-area-inset-right,0px));width:auto;max-width:none;top:auto;',
    'bottom:calc(0.85rem + 8rem + 0.4rem + env(safe-area-inset-bottom,0px));',
    'max-height:min(30vh,14rem);-webkit-overflow-scrolling:touch}',
    'body.is-exploring #careerExhibitCard{bottom:max(',
    'calc(0.85rem + 8rem + 0.4rem + env(safe-area-inset-bottom,0px)),',
    'calc(0.7rem + 2.25rem + 0.45rem + env(safe-area-inset-bottom,0px)))}}'
  ].join('');
  doc.head.appendChild(css);
}

export function createCareerCorridorController(ctx) {
  const scene = ctx.scene;
  const kit = ctx.kit;
  const pickables = ctx.pickables;
  const reduced = !!ctx.reduced;
  const getPlayer = ctx.getPlayer;
  const enterInterior = ctx.enterInterior;
  const doc = ctx.document || document;
  const computerIsland = islandById('computer');
  const building = buildingById('computer-site');

  let loaded = false;
  let loading = false;
  let disposed = false;
  let rootGroup = null;
  let entriesById = {};
  let cardEl = null;
  let cardBody = null;
  let cardVisible = false;
  let enterLabBtn = null;

  function distToComputer() {
    const p = getPlayer();
    if (!p || !computerIsland) return Infinity;
    return Math.hypot(p.x - computerIsland.x, p.z - computerIsland.z);
  }

  function shouldLoad() {
    return distToComputer() < computerIsland.visualR + LOAD_BUFFER;
  }

  function shouldUnload() {
    return distToComputer() > computerIsland.visualR + UNLOAD_BUFFER;
  }

  function ensureCard() {
    ensureCardStyle(doc);
    if (cardEl) return;
    cardEl = doc.createElement('aside');
    cardEl.id = 'careerExhibitCard';
    cardEl.hidden = true;
    cardEl.setAttribute('aria-live', 'polite');
    const close = doc.createElement('button');
    close.id = 'careerExhibitCardClose';
    close.type = 'button';
    close.setAttribute('aria-label', '關閉經歷說明');
    close.textContent = '×';
    close.addEventListener('click', () => hideCard());
    cardBody = doc.createElement('div');
    cardBody.className = 'career-card-body';
    enterLabBtn = doc.createElement('button');
    enterLabBtn.type = 'button';
    enterLabBtn.className = 'career-enter-lab';
    enterLabBtn.textContent = '進入研發樓';
    enterLabBtn.hidden = true;
    enterLabBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      hideCard();
      if (typeof enterInterior === 'function') enterInterior('computer-site');
    });
    cardEl.appendChild(close);
    cardEl.appendChild(cardBody);
    cardEl.appendChild(enterLabBtn);
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
    if (!careerCardEscapeShouldClose(cardVisible, ev.key)) return;
    hideCard();
    ev.preventDefault();
    ev.stopPropagation();
  }

  function hideCard() {
    if (!cardEl) return;
    cardVisible = false;
    cardEl.hidden = true;
    if (enterLabBtn) enterLabBtn.hidden = true;
  }

  function showCard(entryId) {
    const entry = entriesById[entryId];
    if (!entry) return;
    ensureCard();
    const model = buildCardTextModel(entry);
    fillCareerCard(cardBody, model, doc);
    if (enterLabBtn) enterLabBtn.hidden = !model.isPunchClock;
    cardEl.hidden = false;
    cardVisible = true;
  }

  function disposeScene() {
    if (rootGroup && rootGroup.parent) rootGroup.parent.remove(rootGroup);
    rootGroup = null;
    loaded = false;
    entriesById = {};
  }

  async function loadCorridor() {
    if (loaded || loading || disposed) return;
    loading = true;
    try {
      const res = await fetch(CAREER_TIMELINE_URL);
      if (!res.ok) throw new Error('fetch career');
      const data = await res.json();
      const entries = Array.isArray(data.entries) ? data.entries : [];
      entriesById = {};
      for (let i = 0; i < entries.length; i++) entriesById[entries[i].id] = entries[i];

      rootGroup = new THREE.Group();
      const doorZ = building.door ? building.door.z : 16.17;
      layInstancedPaving(scene, kit, [
        { x: building.x - 0.05, z: doorZ - 0.45, w: 0.65, d: 1.55, rotY: 0.02 },
        { x: building.x - 1.25, z: 16.28, w: 1.55, d: 0.82, rotY: 0.05 },
        { x: building.x - 1.15, z: 15.68, w: 1.35, d: 0.58, rotY: -0.03 },
        { x: building.x + 1.28, z: 16.25, w: 1.55, d: 0.78, rotY: -0.04 },
        { x: building.x + 0.92, z: 15.58, w: 1.05, d: 0.52, rotY: 0.06 }
      ]);

      for (let j = 0; j < CAREER_EXHIBIT_LAYOUT.length; j++) {
        const row = CAREER_EXHIBIT_LAYOUT[j];
        const entry = entriesById[row.id];
        if (!entry) continue;
        const pedestal = new THREE.Group();
        pedestal.position.set(row.x, G, row.z);
        kit.addBox(pedestal, 0.48, 0.09, 0.36, 0xe8eef2, 0, 0.045, 0, { shadow: false });
        const prop = new THREE.Group();
        prop.position.y = 0.08;
        buildPropMesh(kit, entry, prop);
        pedestal.add(prop);

        const pick = new THREE.Mesh(
          new THREE.BoxGeometry(0.42, 0.72, 0.42),
          new THREE.MeshBasicMaterial({ visible: false })
        );
        pick.position.set(0, 0.36, 0);
        pick.userData.careerExhibitId = row.id;
        pedestal.add(pick);
        if (pickables) pickables.push(pick);
        rootGroup.add(pedestal);
      }

      buildPhaseSigns(rootGroup, kit);
      buildOutdoorEasterEggs(scene, kit);
      scene.add(rootGroup);
      loaded = true;
    } catch (err) {
      console.warn('[Island] 資工人生紀念館載入失敗', err);
      disposeScene();
    } finally {
      loading = false;
    }
  }

  function tick(dt) {
    if (disposed) return;
    if (shouldUnload() && loaded) {
      disposeScene();
      return;
    }
    if (shouldLoad() && !loaded && !loading) loadCorridor();
  }

  function handleZoneClick(zone) {
    if (!zone || typeof zone !== 'object') return false;
    if (zone.type === 'career-exhibit') {
      showCard(zone.entryId);
      return true;
    }
    if (zone.action === 'openCareerCard' && zone.entryId) {
      showCard(zone.entryId);
      return true;
    }
    return false;
  }

  function destroy() {
    disposed = true;
    disposeScene();
    if (cardEl && cardEl.parentNode) cardEl.parentNode.removeChild(cardEl);
    cardEl = null;
    doc.removeEventListener('pointerdown', onDocPointer, true);
    doc.removeEventListener('keydown', onDocKey, true);
  }

  return {
    tick,
    handleZoneClick,
    destroy,
    isCardOpen: () => cardVisible,
    getInteractZones: () => (loaded ? careerInteractZones() : []),
    showCard
  };
}