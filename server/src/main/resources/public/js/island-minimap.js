// 探索模式楓之谷風小／大地圖（純邏輯可 node:test；繪製走 2D canvas）。
import {
  ISLANDS,
  BRIDGES,
  BUILDINGS,
  BOAT_STOPS,
  SEA_NAV_BOUNDS,
  islandAt,
  onBridge,
  worldDoor
} from './island-layout.js';
import { pinkCarWorld } from './island-city-data.js';
import { npcSpawnPlan } from './island-people-data.js';
import { boatWorldPose } from './island-boat.js';
import { INTERIORS } from './island-interior.js';

export const INTERIOR_AREA_NAMES = Object.freeze({
  'shop-room': '神之島',
  'casino-room': '賭場',
  'tcm-room': '推拿館',
  'lab-room': '研發樓'
});

const DOOR_BUILDING_IDS = Object.freeze([
  'shop',
  'casino',
  'tcm-site',
  'computer-site',
  'graffiti-wall',
  'construction-board'
]);

const MARKER_COLORS = Object.freeze({
  land: '#8fcf78',
  landStroke: '#3a7a42',
  sea: '#9ed4f5',
  bridge: '#c4985a',
  door: '#e8b04a',
  boat: '#4a8fd4',
  car: '#ff8ac8',
  npc: '#7a9cc4',
  peer: '#b7d4ff',
  board: '#f0a050',
  player: '#163a70',
  study: '#d64545',
  exit: '#5a9e6e',
  block: '#c8d8e8',
  nightDim: 0.42
});

/** 包住六島 visualR 的外框；與 SEA_NAV_BOUNDS 取較大範圍。 */
export function worldMapBounds() {
  let minX = SEA_NAV_BOUNDS.minX;
  let maxX = SEA_NAV_BOUNDS.maxX;
  let minZ = SEA_NAV_BOUNDS.minZ;
  let maxZ = SEA_NAV_BOUNDS.maxZ;
  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const vr = isl.visualR != null ? isl.visualR : isl.r;
    minX = Math.min(minX, isl.x - vr);
    maxX = Math.max(maxX, isl.x + vr);
    minZ = Math.min(minZ, isl.z - vr);
    maxZ = Math.max(maxZ, isl.z + vr);
  }
  return Object.freeze({ minX: minX, maxX: maxX, minZ: minZ, maxZ: maxZ });
}

export function interiorMapBounds(room) {
  if (!room || !room.floor) {
    return Object.freeze({ minX: -8, maxX: 8, minZ: -2, maxZ: 12 });
  }
  const f = room.floor;
  return Object.freeze({ minX: f.minX, maxX: f.maxX, minZ: f.minZ, maxZ: f.maxZ });
}

/** 畫面上方為世界 −Z：z 越小（越負）越靠上。 */
export function worldToMap(x, z, bounds, width, height) {
  const b = bounds || worldMapBounds();
  const w = width > 0 ? width : 1;
  const h = height > 0 ? height : 1;
  const spanX = b.maxX - b.minX || 1;
  const spanZ = b.maxZ - b.minZ || 1;
  return Object.freeze({
    px: ((x - b.minX) / spanX) * w,
    py: ((z - b.minZ) / spanZ) * h
  });
}

export function mapToWorld(px, py, bounds, width, height) {
  const b = bounds || worldMapBounds();
  const w = width > 0 ? width : 1;
  const h = height > 0 ? height : 1;
  const spanX = b.maxX - b.minX || 1;
  const spanZ = b.maxZ - b.minZ || 1;
  return Object.freeze({
    x: b.minX + (px / w) * spanX,
    z: b.minZ + (py / h) * spanZ
  });
}

export function worldNorm(x, z, bounds) {
  const b = bounds || worldMapBounds();
  const pt = worldToMap(x, z, b, 1, 1);
  return Object.freeze({ u: pt.px, v: pt.py });
}

export function interiorToMap(x, z, room, width, height) {
  return worldToMap(x, z, interiorMapBounds(room), width, height);
}

export function bridgeAt(x, z, radius) {
  const inset = radius || 0.12;
  for (let i = 0; i < BRIDGES.length; i++) {
    if (onBridge(x, z, BRIDGES[i], inset)) return BRIDGES[i];
  }
  return null;
}

export function resolveAreaName(opts) {
  const options = opts || {};
  if (options.interiorRoom) {
    const id = options.interiorRoom.id;
    return INTERIOR_AREA_NAMES[id] || options.interiorRoom.buildingId || '室內';
  }
  const x = options.x;
  const z = options.z;
  if (!Number.isFinite(x) || !Number.isFinite(z)) return '海域';
  const bridge = bridgeAt(x, z, 0.18);
  if (bridge) return '橋上';
  const isl = islandAt(x, z, 0.15);
  if (isl) return isl.name;
  return '海域';
}

export function collectDoorMarkers() {
  const out = [];
  for (let i = 0; i < BUILDINGS.length; i++) {
    const b = BUILDINGS[i];
    if (DOOR_BUILDING_IDS.indexOf(b.id) < 0 || !b.entry) continue;
    const door = b.door || worldDoor(b);
    if (!door) continue;
    out.push(Object.freeze({
      kind: b.id === 'construction-board' ? 'board' : 'door',
      id: b.id,
      label: b.label,
      x: door.x,
      z: door.z
    }));
  }
  return Object.freeze(out);
}

export function collectBoatMarkers() {
  const out = [];
  const keys = Object.keys(BOAT_STOPS);
  for (let i = 0; i < keys.length; i++) {
    const stop = BOAT_STOPS[keys[i]];
    if (stop.berth) {
      out.push(Object.freeze({
        kind: 'berth',
        id: keys[i] + '-berth',
        x: stop.berth.x,
        z: stop.berth.z
      }));
    }
    if (stop.board) {
      out.push(Object.freeze({
        kind: 'dock',
        id: keys[i] + '-board',
        x: stop.board.x,
        z: stop.board.z
      }));
    }
  }
  return Object.freeze(out);
}

function npcPointsFromPlan(plan) {
  const out = [];
  (plan.idles || []).forEach((item, idx) => {
    out.push(Object.freeze({ kind: 'npc', id: 'idle-' + idx, x: item.x, z: item.z }));
  });
  (plan.extras || []).forEach((item, idx) => {
    const pt = item.path && item.path.length ? item.path[0] : [item.x, item.z];
    out.push(Object.freeze({ kind: 'npc', id: 'extra-' + idx, x: pt[0], z: pt[1] }));
  });
  (plan.walkers || []).forEach((item, idx) => {
    if (!item.path || !item.path.length) return;
    const pt = item.path[0];
    out.push(Object.freeze({ kind: 'npc', id: 'walk-' + idx, x: pt[0], z: pt[1] }));
  });
  return out;
}

export function collectNpcMarkers(opts) {
  const plan = npcSpawnPlan(opts || {});
  return Object.freeze(npcPointsFromPlan(plan));
}

export function collectCarMarker(vehicleState) {
  if (vehicleState && vehicleState.phase === 'driving') {
    return Object.freeze({
      kind: 'car',
      x: vehicleState.carX,
      z: vehicleState.carZ,
      yaw: vehicleState.carYaw
    });
  }
  const car = pinkCarWorld();
  return Object.freeze({ kind: 'car', x: car.x, z: car.z, yaw: car.yaw });
}

export function collectBoatPoseMarker(boatState) {
  if (!boatState) return null;
  const pose = boatWorldPose(boatState);
  return Object.freeze({ kind: 'boat-live', x: pose.x, z: pose.z, yaw: pose.yaw });
}

export function collectOutdoorMarkers(opts) {
  const options = opts || {};
  const markers = collectDoorMarkers().slice();
  const boats = collectBoatMarkers();
  for (let i = 0; i < boats.length; i++) markers.push(boats[i]);
  markers.push(collectCarMarker(options.vehicleState));
  const live = collectBoatPoseMarker(options.boatState);
  if (live) markers.push(live);
  const npcs = collectNpcMarkers(options);
  for (let i = 0; i < npcs.length; i++) markers.push(npcs[i]);
  const peers = options.peers || [];
  for (let p = 0; p < peers.length; p++) {
    markers.push(Object.freeze({
      kind: 'peer',
      x: peers[p].x,
      z: peers[p].z,
      yaw: peers[p].yaw
    }));
  }
  return Object.freeze(markers);
}

export function collectInteriorMarkers(room) {
  if (!room) return Object.freeze([]);
  const out = [];
  if (room.exit) {
    out.push(Object.freeze({
      kind: 'exit',
      label: room.exit.label,
      x: room.exit.x,
      z: room.exit.z
    }));
  }
  const blocks = room.blocks || [];
  for (let i = 0; i < blocks.length; i++) {
    const b = blocks[i];
    out.push(Object.freeze({
      kind: 'block',
      id: b.id,
      x: (b.minX + b.maxX) * 0.5,
      z: (b.minZ + b.maxZ) * 0.5,
      w: b.maxX - b.minX,
      d: b.maxZ - b.minZ
    }));
  }
  const interacts = room.interacts || [];
  for (let j = 0; j < interacts.length; j++) {
    const iz = interacts[j];
    out.push(Object.freeze({
      kind: iz.id === 'tcm-study' ? 'study' : 'interact',
      id: iz.id,
      label: iz.label,
      x: iz.x,
      z: iz.z
    }));
  }
  return Object.freeze(out);
}

function applyNight(ctx, mix) {
  const t = mix == null ? 0 : Math.max(0, Math.min(1, mix));
  if (t <= 0.001) return;
  ctx.fillStyle = 'rgba(10, 18, 42, ' + (MARKER_COLORS.nightDim * t).toFixed(3) + ')';
  ctx.fillRect(0, 0, ctx.canvas.width, ctx.canvas.height);
}

function drawArrow(ctx, x, y, yaw, color, scale) {
  const s = scale || 1;
  const len = 7 * s;
  const head = 4 * s;
  const vx = Math.sin(yaw || 0);
  const vz = Math.cos(yaw || 0);
  const ex = x + vx * len;
  const ey = y + vz * len;
  ctx.strokeStyle = color;
  ctx.fillStyle = color;
  ctx.lineWidth = Math.max(1.2, 1.4 * s);
  ctx.beginPath();
  ctx.moveTo(x, y);
  ctx.lineTo(ex, ey);
  ctx.stroke();
  const nx = -vz;
  const ny = vx;
  ctx.beginPath();
  ctx.moveTo(ex, ey);
  ctx.lineTo(ex - vx * head + nx * head * 0.55, ey - vz * head + ny * head * 0.55);
  ctx.lineTo(ex - vx * head - nx * head * 0.55, ey - vz * head - ny * head * 0.55);
  ctx.closePath();
  ctx.fill();
}

function drawOutdoorScene(ctx, frame, opts) {
  const width = frame.width;
  const height = frame.height;
  const pad = frame.padding || 6;
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;
  const bounds = worldMapBounds();

  ctx.fillStyle = MARKER_COLORS.sea;
  ctx.fillRect(pad, pad, innerW, innerH);

  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const r = isl.visualR != null ? isl.visualR : isl.r;
    const c = worldToMap(isl.x, isl.z, bounds, innerW, innerH);
    const rx = (r / (bounds.maxX - bounds.minX)) * innerW;
    const ry = (r / (bounds.maxZ - bounds.minZ)) * innerH;
    ctx.fillStyle = MARKER_COLORS.land;
    ctx.strokeStyle = MARKER_COLORS.landStroke;
    ctx.lineWidth = 1.2;
    ctx.beginPath();
    ctx.ellipse(pad + c.px, pad + c.py, Math.max(rx, 4), Math.max(ry, 4), 0, 0, Math.PI * 2);
    ctx.fill();
    ctx.stroke();
  }

  ctx.strokeStyle = MARKER_COLORS.bridge;
  ctx.lineWidth = Math.max(2, frame.scale * 1.8);
  ctx.lineCap = 'round';
  for (let bi = 0; bi < BRIDGES.length; bi++) {
    const br = BRIDGES[bi];
    const a = worldToMap(br.ax, br.az, bounds, innerW, innerH);
    const b = worldToMap(br.bx, br.bz, bounds, innerW, innerH);
    ctx.beginPath();
    ctx.moveTo(pad + a.px, pad + a.py);
    ctx.lineTo(pad + b.px, pad + b.py);
    ctx.stroke();
  }

  const markers = collectOutdoorMarkers(opts);
  for (let m = 0; m < markers.length; m++) {
    const mk = markers[m];
    const p = worldToMap(mk.x, mk.z, bounds, innerW, innerH);
    const px = pad + p.px;
    const py = pad + p.py;
    if (mk.kind === 'door' || mk.kind === 'board') {
      ctx.fillStyle = mk.kind === 'board' ? MARKER_COLORS.board : MARKER_COLORS.door;
      ctx.fillRect(px - 2.5, py - 2.5, 5, 5);
    } else if (mk.kind === 'berth' || mk.kind === 'dock' || mk.kind === 'boat-live') {
      ctx.fillStyle = MARKER_COLORS.boat;
      ctx.beginPath();
      ctx.moveTo(px, py - 3);
      ctx.lineTo(px + 3.5, py + 2.5);
      ctx.lineTo(px - 3.5, py + 2.5);
      ctx.closePath();
      ctx.fill();
      if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, '#fff', frame.scale * 0.65);
    } else if (mk.kind === 'car') {
      ctx.fillStyle = MARKER_COLORS.car;
      ctx.fillRect(px - 3, py - 2, 6, 4);
      if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, '#fff', frame.scale * 0.55);
    } else if (mk.kind === 'npc') {
      ctx.fillStyle = MARKER_COLORS.npc;
      ctx.beginPath();
      ctx.arc(px, py, 2.2, 0, Math.PI * 2);
      ctx.fill();
    } else if (mk.kind === 'peer') {
      ctx.fillStyle = MARKER_COLORS.peer;
      ctx.beginPath();
      ctx.arc(px, py, 2.5, 0, Math.PI * 2);
      ctx.fill();
      if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, MARKER_COLORS.player, frame.scale * 0.5);
    }
  }

  const player = opts.player;
  if (player && Number.isFinite(player.x) && Number.isFinite(player.z)) {
    const p = worldToMap(player.x, player.z, bounds, innerW, innerH);
    const px = pad + p.px;
    const py = pad + p.py;
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(px, py, 3.6, 0, Math.PI * 2);
    ctx.fill();
    drawArrow(ctx, px, py, player.yaw, MARKER_COLORS.player, frame.scale);
  }
}

function drawInteriorScene(ctx, frame, opts) {
  const room = opts.interiorRoom;
  if (!room) return;
  const width = frame.width;
  const height = frame.height;
  const pad = frame.padding || 6;
  const innerW = width - pad * 2;
  const innerH = height - pad * 2;
  const bounds = interiorMapBounds(room);

  ctx.fillStyle = '#edf4fc';
  ctx.fillRect(pad, pad, innerW, innerH);
  ctx.strokeStyle = '#8aa8c8';
  ctx.lineWidth = 1.4;
  const f = room.floor;
  const tl = interiorToMap(f.minX, f.minZ, room, innerW, innerH);
  const br = interiorToMap(f.maxX, f.maxZ, room, innerW, innerH);
  ctx.strokeRect(pad + tl.px, pad + tl.py, br.px - tl.px, br.py - tl.py);

  const markers = collectInteriorMarkers(room);
  for (let i = 0; i < markers.length; i++) {
    const mk = markers[i];
    const p = interiorToMap(mk.x, mk.z, room, innerW, innerH);
    const px = pad + p.px;
    const py = pad + p.py;
    if (mk.kind === 'block') {
      const w = (mk.w / (bounds.maxX - bounds.minX)) * innerW;
      const h = (mk.d / (bounds.maxZ - bounds.minZ)) * innerH;
      ctx.fillStyle = MARKER_COLORS.block;
      ctx.fillRect(px - w * 0.5, py - h * 0.5, w, h);
    } else if (mk.kind === 'exit') {
      ctx.fillStyle = MARKER_COLORS.exit;
      ctx.fillRect(px - 4, py - 3, 8, 6);
    } else if (mk.kind === 'study') {
      ctx.fillStyle = MARKER_COLORS.study;
      ctx.strokeStyle = '#8b2020';
      ctx.lineWidth = 1.2;
      ctx.beginPath();
      ctx.arc(px, py, 5.5, 0, Math.PI * 2);
      ctx.fill();
      ctx.stroke();
      if (frame.showLabels) {
        ctx.fillStyle = '#6b1010';
        ctx.font = '600 ' + Math.round(8 * frame.scale) + 'px "Noto Sans TC", "Noto Sans CJK TC", sans-serif';
        ctx.textAlign = 'center';
        ctx.fillText('經絡研習室', px, py - 8);
      }
    } else if (mk.kind === 'interact') {
      ctx.fillStyle = MARKER_COLORS.door;
      ctx.fillRect(px - 2.5, py - 2.5, 5, 5);
    }
  }

  const player = opts.player;
  if (player && Number.isFinite(player.x) && Number.isFinite(player.z)) {
    const p = interiorToMap(player.x, player.z, room, innerW, innerH);
    const px = pad + p.px;
    const py = pad + p.py;
    ctx.fillStyle = '#fff';
    ctx.beginPath();
    ctx.arc(px, py, 3.6, 0, Math.PI * 2);
    ctx.fill();
    drawArrow(ctx, px, py, player.yaw, MARKER_COLORS.player, frame.scale);
  }
}

/** 繪製一幀小／大地圖。 */
export function paintMinimap(ctx, opts) {
  if (!ctx || !ctx.canvas) return;
  const options = opts || {};
  const scale = options.large ? 1.35 : 1;
  const width = ctx.canvas.width;
  const height = ctx.canvas.height;
  const frame = {
    width: width,
    height: height,
    padding: options.padding == null ? 6 : options.padding,
    scale: scale,
    showLabels: !!options.large
  };
  ctx.clearRect(0, 0, width, height);
  if (options.interiorRoom) drawInteriorScene(ctx, frame, options);
  else drawOutdoorScene(ctx, frame, options);
  applyNight(ctx, options.nightMix);
}

export function shouldOpenMapWithKey(ctx) {
  if (!ctx || !ctx.exploring) return false;
  if (ctx.typing) return false;
  if (ctx.altKey || ctx.ctrlKey || ctx.metaKey) return false;
  const key = ctx.key;
  if (key !== 'm' && key !== 'M') return false;
  if (ctx.mapOpen) return false;
  if (ctx.moreOpen) return false;
  if (ctx.nickOpen) return false;
  if (ctx.chatOpen) return false;
  if (ctx.avatarOpen) return false;
  if (ctx.sheetOpen) return false;
  if (ctx.roadmapOpen) return false;
  if (ctx.casinoOpen) return false;
  if (ctx.graffitiOpen) return false;
  return true;
}

export function minimapMode(opts) {
  const options = opts || {};
  if (options.interiorRoom) {
    return Object.freeze({ type: 'interior', room: options.interiorRoom });
  }
  return Object.freeze({ type: 'outdoor' });
}

/** 供測試：室內 id → INTERIORS 資料。 */
export function interiorByRoomId(id) {
  for (let i = 0; i < INTERIORS.length; i++) {
    if (INTERIORS[i].id === id) return INTERIORS[i];
  }
  return null;
}

export function createMinimapHud(opts) {
  const document = opts.document;
  if (!document) return { tick: function () {}, destroy: function () {} };

  const win = opts.window || null;
  const root = document.getElementById('islandMinimap');
  const titleEl = document.getElementById('islandMinimapTitle');
  const bodyEl = document.getElementById('islandMinimapBody');
  const hudCanvas = document.getElementById('islandMinimapCanvas');
  const mapCanvas = document.getElementById('islandMapCanvas');
  const collapseBtn = document.getElementById('islandMinimapCollapse');
  const expandBtn = document.getElementById('islandMinimapExpand');
  const mobileMq = win && typeof win.matchMedia === 'function'
    ? win.matchMedia('(max-width: 760px)')
    : { matches: false };

  let collapsed = mobileMq.matches;
  let lastPaint = 0;
  const intervalMs = 100;

  function hudCtx() {
    return hudCanvas && hudCanvas.getContext ? hudCanvas.getContext('2d') : null;
  }

  function mapCtx() {
    return mapCanvas && mapCanvas.getContext ? mapCanvas.getContext('2d') : null;
  }

  function syncCollapsedUi() {
    if (!root) return;
    root.classList.toggle('island-minimap-collapsed', collapsed);
    if (collapseBtn) {
      collapseBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      collapseBtn.textContent = collapsed ? '+' : '−';
      collapseBtn.setAttribute('aria-label', collapsed ? '展開小地圖' : '縮小小地圖');
    }
  }

  function setCollapsed(on) {
    collapsed = !!on;
    syncCollapsedUi();
  }

  function paintTargets() {
    const getState = opts.getPaintState;
    if (typeof getState !== 'function') return;
    const state = getState();
  if (titleEl) titleEl.textContent = resolveAreaName({
      interiorRoom: state.interiorRoom,
      x: state.player ? state.player.x : 0,
      z: state.player ? state.player.z : 0
    });
    const paintOpts = {
      player: state.player,
      interiorRoom: state.interiorRoom,
      vehicleState: state.vehicleState,
      boatState: state.boatState,
      peers: state.peers,
      nightMix: state.nightMix,
      large: false
    };
    const hctx = hudCtx();
    if (hctx && !collapsed && root && !root.hidden) paintMinimap(hctx, paintOpts);
    const mapSheet = document.getElementById('islandMapSheet');
    const mctx = mapCtx();
    if (mctx && mapSheet && !mapSheet.hidden) {
      paintMinimap(mctx, Object.assign({}, paintOpts, { large: true, padding: 10 }));
    }
  }

  function tick(now) {
    if (!opts.isExploring || !opts.isExploring()) {
      if (root) root.hidden = true;
      return;
    }
    if (root) root.hidden = false;
    if (!now) now = typeof performance !== 'undefined' ? performance.now() : Date.now();
    if (now - lastPaint < intervalMs) return;
    lastPaint = now;
    paintTargets();
  }

  if (collapseBtn) {
    collapseBtn.addEventListener('click', (event) => {
      event.stopPropagation();
      setCollapsed(!collapsed);
    });
  }
  if (expandBtn && typeof opts.openLargeMap === 'function') {
    expandBtn.addEventListener('click', (event) => {
      event.stopPropagation();
      opts.openLargeMap();
    });
  }
  if (hudCanvas && typeof opts.openLargeMap === 'function') {
    hudCanvas.addEventListener('click', () => opts.openLargeMap());
  }
  if (typeof opts.onMapOpenChange === 'function') {
    const mapSheet = document.getElementById('islandMapSheet');
    if (mapSheet && typeof MutationObserver !== 'undefined') {
      const obs = new MutationObserver(() => {
        if (!mapSheet.hidden) {
          lastPaint = 0;
          paintTargets();
        }
        opts.onMapOpenChange(!mapSheet.hidden);
      });
      obs.observe(mapSheet, { attributes: true, attributeFilter: ['hidden'] });
    }
  }

  function onMedia() {
    if (mobileMq.matches && !collapsed) setCollapsed(true);
  }
  if (mobileMq.addEventListener) mobileMq.addEventListener('change', onMedia);
  else if (mobileMq.addListener) mobileMq.addListener(onMedia);
  syncCollapsedUi();

  return {
    tick: tick,
    setCollapsed: setCollapsed,
    forceRepaint: function () { lastPaint = 0; paintTargets(); },
    destroy: function () {
      if (mobileMq.removeEventListener) mobileMq.removeEventListener('change', onMedia);
      else if (mobileMq.removeListener) mobileMq.removeListener(onMedia);
    }
  };
}