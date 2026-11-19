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

const LARGE_MAP_PAD = 10;
const PIN_FONT_PX_DESKTOP = 14;
const PIN_FONT_PX_MOBILE = 13;
const PIN_MIN_TOUCH = 44;

/** 大地圖標記：不含 NPC／門口／施工牌／停放車；碼頭每站一個。 */
export function collectLargeMapMarkers(opts) {
  const options = opts || {};
  const markers = [];
  const keys = Object.keys(BOAT_STOPS);
  for (let i = 0; i < keys.length; i++) {
    const stop = BOAT_STOPS[keys[i]];
    let x = null;
    let z = null;
    if (stop.berth && stop.board) {
      x = (stop.berth.x + stop.board.x) * 0.5;
      z = (stop.berth.z + stop.board.z) * 0.5;
    } else if (stop.berth) {
      x = stop.berth.x;
      z = stop.berth.z;
    } else if (stop.board) {
      x = stop.board.x;
      z = stop.board.z;
    }
    if (x != null && z != null) {
      markers.push(Object.freeze({ kind: 'berth', id: keys[i] + '-dock', x: x, z: z }));
    }
  }
  const live = collectBoatPoseMarker(options.boatState);
  if (live) markers.push(live);
  if (options.vehicleState && options.vehicleState.phase === 'driving') {
    markers.push(collectCarMarker(options.vehicleState));
  }
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

export function mapInnerFrame(width, height, padding) {
  const pad = padding == null ? 6 : padding;
  const w = width > 0 ? width : 1;
  const h = height > 0 ? height : 1;
  return Object.freeze({
    pad: pad,
    innerW: Math.max(1, w - pad * 2),
    innerH: Math.max(1, h - pad * 2)
  });
}

export function largeMapWorldAspect() {
  const b = worldMapBounds();
  const spanX = b.maxX - b.minX || 1;
  const spanZ = b.maxZ - b.minZ || 1;
  return spanZ / spanX;
}

/** 依面板 CSS 推估大地圖 canvas 邏輯尺寸（與 #islandMapCanvasWrap 排版一致）。 */
export function largeMapCanvasCssSize(viewportWidth) {
  const vw = viewportWidth > 0 ? viewportWidth : 390;
  const desktop = vw >= 761;
  const panelW = desktop ? Math.min(44 * 16, vw * 0.92) : vw - 16;
  const wrapPadX = 27;
  const innerW = Math.max(120, panelW - wrapPadX);
  const aspect = largeMapWorldAspect();
  const pad = LARGE_MAP_PAD;
  const innerH = innerW * aspect;
  const height = innerH + pad * 2;
  const width = innerW + pad * 2;
  return Object.freeze({
    width: width,
    height: height,
    innerWidth: innerW,
    innerHeight: innerH,
    padding: pad
  });
}

function pinTextWidthPx(name, fontPx) {
  let w = 0;
  const text = String(name || '');
  for (let i = 0; i < text.length; i++) {
    const ch = text.charCodeAt(i);
    w += ch > 0x2e80 ? fontPx : fontPx * 0.58;
  }
  return w + 18;
}

function pinBoxFromAnchor(x, y, w, h) {
  return Object.freeze({
    left: x - w * 0.5,
    top: y - h,
    right: x + w * 0.5,
    bottom: y,
    width: w,
    height: h
  });
}

function boxesOverlap(a, b) {
  return !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom);
}

function boxInsideBounds(box, maxW, maxH, margin) {
  const m = margin == null ? 2 : margin;
  return box.left >= m && box.top >= m && box.right <= maxW - m && box.bottom <= maxH - m;
}

/** 六島名牌版面：圓圈上緣、不重疊、在邊界內。 */
export function islandPinLayout(cssWidth, cssHeight, opts) {
  const options = opts || {};
  const w = cssWidth > 0 ? cssWidth : 1;
  const h = cssHeight > 0 ? cssHeight : 1;
  const padding = options.padding == null ? LARGE_MAP_PAD : options.padding;
  const fontPx = w < 420 ? PIN_FONT_PX_MOBILE : PIN_FONT_PX_DESKTOP;
  const bounds = worldMapBounds();
  const frame = mapInnerFrame(w, h, padding);
  const spanX = bounds.maxX - bounds.minX || 1;
  const spanZ = bounds.maxZ - bounds.minZ || 1;
  const placed = [];
  const out = [];

  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const vr = isl.visualR != null ? isl.visualR : isl.r;
    const c = worldToMap(isl.x, isl.z, bounds, frame.innerW, frame.innerH);
    const rx = (vr / spanX) * frame.innerW;
    const ry = (vr / spanZ) * frame.innerH;
    const centerX = frame.pad + c.px;
    const centerY = frame.pad + c.py;
    const pinW = Math.max(PIN_MIN_TOUCH, pinTextWidthPx(isl.name, fontPx));
    const pinH = Math.max(PIN_MIN_TOUCH, fontPx + 14);

    const offsets = [
      { dx: 0, dy: -ry - 6 },
      { dx: -rx * 0.35, dy: -ry * 0.55 },
      { dx: rx * 0.35, dy: -ry * 0.55 },
      { dx: 0, dy: -ry - pinH * 0.15 },
      { dx: -rx * 0.55, dy: -ry * 0.25 },
      { dx: rx * 0.55, dy: -ry * 0.25 }
    ];

    let chosen = null;
    for (let o = 0; o < offsets.length; o++) {
      const anchorX = centerX + offsets[o].dx;
      const anchorY = centerY + offsets[o].dy;
      const box = pinBoxFromAnchor(anchorX, anchorY, pinW, pinH);
      if (!boxInsideBounds(box, w, h, 2)) continue;
      let clash = false;
      for (let p = 0; p < placed.length; p++) {
        if (boxesOverlap(box, placed[p])) {
          clash = true;
          break;
        }
      }
      if (!clash) {
        chosen = { anchorX: anchorX, anchorY: anchorY, box: box };
        break;
      }
    }
    if (!chosen) {
      const anchorX = centerX;
      const anchorY = centerY - ry - 6;
      chosen = {
        anchorX: anchorX,
        anchorY: anchorY,
        box: pinBoxFromAnchor(anchorX, anchorY, pinW, pinH)
      };
    }
    placed.push(chosen.box);
    out.push(Object.freeze({
      id: isl.id,
      name: isl.name,
      anchorX: chosen.anchorX,
      anchorY: chosen.anchorY,
      centerX: centerX,
      centerY: centerY,
      rx: rx,
      ry: ry,
      fontPx: fontPx,
      pinWidth: pinW,
      pinHeight: pinH,
      box: chosen.box
    }));
  }
  return Object.freeze(out);
}

/** 大地圖 canvas 座標命中哪座島；海面／橋上回傳 null。 */
export function islandAtMapPoint(px, py, cssWidth, cssHeight, slop) {
  if (!Number.isFinite(px) || !Number.isFinite(py)) return null;
  const w = cssWidth > 0 ? cssWidth : 1;
  const h = cssHeight > 0 ? cssHeight : 1;
  const frame = mapInnerFrame(w, h, LARGE_MAP_PAD);
  const localX = px - frame.pad;
  const localY = py - frame.pad;
  if (localX < 0 || localY < 0 || localX > frame.innerW || localY > frame.innerH) return null;

  const bounds = worldMapBounds();
  const spanX = bounds.maxX - bounds.minX || 1;
  const spanZ = bounds.maxZ - bounds.minZ || 1;
  const slopPx = slop == null ? 12 : slop;
  let hit = null;
  let hitDist = Infinity;

  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const vr = isl.visualR != null ? isl.visualR : isl.r;
    const c = worldToMap(isl.x, isl.z, bounds, frame.innerW, frame.innerH);
    const rx = Math.max(4, (vr / spanX) * frame.innerW) + slopPx;
    const ry = Math.max(4, (vr / spanZ) * frame.innerH) + slopPx;
    const dx = (localX - c.px) / rx;
    const dy = (localY - c.py) / ry;
    const dist = dx * dx + dy * dy;
    if (dist <= 1 && dist < hitDist) {
      hit = isl.id;
      hitDist = dist;
    }
  }
  return hit;
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

function drawBerthIcon(ctx, px, py, scale) {
  const s = scale || 1;
  ctx.fillStyle = MARKER_COLORS.boat;
  ctx.strokeStyle = '#1f3a66';
  ctx.lineWidth = Math.max(1, 1.2 * s);
  ctx.beginPath();
  ctx.arc(px, py + 1.5 * s, 4.5 * s, Math.PI, 0);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px, py - 4 * s);
  ctx.lineTo(px + 0.5 * s, py + 1 * s);
  ctx.lineTo(px - 0.5 * s, py + 1 * s);
  ctx.closePath();
  ctx.fill();
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
    const hovered = frame.largeMap && frame.hoverIslandId === isl.id;
    ctx.fillStyle = hovered ? '#a4e088' : MARKER_COLORS.land;
    ctx.strokeStyle = hovered ? '#2d6b38' : MARKER_COLORS.landStroke;
    ctx.lineWidth = hovered ? 2.4 : 1.2;
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

  const markers = frame.largeMap ? collectLargeMapMarkers(opts) : collectOutdoorMarkers(opts);
  for (let m = 0; m < markers.length; m++) {
    const mk = markers[m];
    const p = worldToMap(mk.x, mk.z, bounds, innerW, innerH);
    const px = pad + p.px;
    const py = pad + p.py;
    if (frame.largeMap) {
      if (mk.kind === 'berth') {
        drawBerthIcon(ctx, px, py, frame.scale * 1.1);
      } else if (mk.kind === 'boat-live') {
        ctx.fillStyle = MARKER_COLORS.boat;
        ctx.beginPath();
        ctx.moveTo(px, py - 4);
        ctx.lineTo(px + 4.5, py + 3);
        ctx.lineTo(px - 4.5, py + 3);
        ctx.closePath();
        ctx.fill();
        if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, '#fff', frame.scale * 0.75);
      } else if (mk.kind === 'car') {
        ctx.fillStyle = MARKER_COLORS.car;
        ctx.fillRect(px - 4, py - 2.5, 8, 5);
        if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, '#fff', frame.scale * 0.65);
      } else if (mk.kind === 'peer') {
        ctx.fillStyle = MARKER_COLORS.peer;
        ctx.beginPath();
        ctx.arc(px, py, 3.6, 0, Math.PI * 2);
        ctx.fill();
        if (mk.yaw != null) drawArrow(ctx, px, py, mk.yaw, MARKER_COLORS.player, frame.scale * 0.55);
      }
      continue;
    }
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
    const arrowScale = frame.largeMap ? frame.scale * 1.85 : frame.scale;
    const coreR = frame.largeMap ? 5.5 : 3.6;
    if (frame.largeMap) {
      ctx.strokeStyle = 'rgba(31, 58, 102, 0.55)';
      ctx.lineWidth = 2.2;
      ctx.beginPath();
      ctx.arc(px, py, coreR + 4.5, 0, Math.PI * 2);
      ctx.stroke();
    }
    ctx.fillStyle = '#fff';
    ctx.strokeStyle = frame.largeMap ? '#1f3a66' : '#fff';
    ctx.lineWidth = frame.largeMap ? 1.8 : 0;
    ctx.beginPath();
    ctx.arc(px, py, coreR, 0, Math.PI * 2);
    ctx.fill();
    if (frame.largeMap) ctx.stroke();
    drawArrow(ctx, px, py, player.yaw, MARKER_COLORS.player, arrowScale);
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
  const dpr = options.devicePixelRatio == null ? 1 : options.devicePixelRatio;
  const logicalW = options.logicalWidth > 0 ? options.logicalWidth : ctx.canvas.width / dpr;
  const logicalH = options.logicalHeight > 0 ? options.logicalHeight : ctx.canvas.height / dpr;
  const scale = options.large ? 1.35 : 1;
  const frame = {
    width: logicalW,
    height: logicalH,
    padding: options.padding == null ? (options.large ? LARGE_MAP_PAD : 6) : options.padding,
    scale: scale,
    showLabels: !!options.large,
    largeMap: !!options.large,
    hoverIslandId: options.hoverIslandId || null
  };
  ctx.save();
  if (dpr !== 1) ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  else ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, logicalW, logicalH);
  if (options.interiorRoom) drawInteriorScene(ctx, frame, options);
  else drawOutdoorScene(ctx, frame, options);
  const nightMix = options.nightMix;
  if (options.large && nightMix > 0) {
    const t = Math.max(0, Math.min(1, nightMix));
    ctx.fillStyle = 'rgba(10, 18, 42, ' + (MARKER_COLORS.nightDim * 0.72 * t).toFixed(3) + ')';
    ctx.fillRect(0, 0, logicalW, logicalH);
  } else {
    applyNight(ctx, nightMix);
  }
  ctx.restore();
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

const MINIMAP_EXPAND_SVG =
  '<svg class="island-minimap-expand-icon" viewBox="0 0 24 24" width="14" height="14" aria-hidden="true">'
  + '<path fill="currentColor" d="M4 4h7v2H6v3H4V4zm13 0v5h-2V6h-3V4h5zM4 15h2v3h3v2H4v-5zm16 0v5h-5v-2h3v-3h2z"/></svg>';

/** 探索中同步左上玩家卡地點標題（與小地圖標題同源）。 */
export function syncPlayerAreaTitle(doc, state, cache) {
  if (!doc) return;
  const textEl = doc.getElementById('islandHudPlayerTitleText');
  const markEl = doc.querySelector('.island-hud-player-title-mark');
  if (!textEl && !markEl) return;
  const st = state || {};
  const name = resolveAreaName({
    interiorRoom: st.interiorRoom,
    x: st.player ? st.player.x : 0,
    z: st.player ? st.player.z : 0
  });
  if (cache && cache.name === name) return;
  if (cache) cache.name = name;
  const mark = name ? String(name).charAt(0) : '';
  if (textEl && textEl.textContent !== name) textEl.textContent = name;
  if (markEl && markEl.textContent !== mark) markEl.textContent = mark;
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
  const mapWrap = document.getElementById('islandMapCanvasWrap');
  const mapPins = document.getElementById('islandMapPins');
  const mapModeToggle = document.getElementById('islandMapModeToggle');
  const collapseBtn = document.getElementById('islandMinimapCollapse');
  const expandBtn = document.getElementById('islandMinimapExpand');
  const mobileMq = win && typeof win.matchMedia === 'function'
    ? win.matchMedia('(max-width: 760px)')
    : { matches: false };

  let collapsed = mobileMq.matches;
  let lastPaint = 0;
  const intervalMs = 100;
  const playerTitleCache = { name: null };
  let expandIconReady = false;
  let mapOverlayMode = 'default';
  let hoverIslandId = null;
  let mapLogicalSize = { width: 480, height: 360 };
  let mapResizeObserver = null;
  let mapWindowResizeListener = null;

  function isMobileViewport() {
    return mobileMq.matches;
  }

  function hudCtx() {
    return hudCanvas && hudCanvas.getContext ? hudCanvas.getContext('2d') : null;
  }

  function mapCtx() {
    return mapCanvas && mapCanvas.getContext ? mapCanvas.getContext('2d') : null;
  }

  function ensureExpandIcon() {
    if (!expandBtn || expandIconReady) return;
    if (expandBtn.querySelector('.island-minimap-expand-icon')) {
      expandIconReady = true;
      return;
    }
    expandBtn.textContent = '';
    expandBtn.insertAdjacentHTML('beforeend', MINIMAP_EXPAND_SVG);
    expandIconReady = true;
  }

  function syncCollapsedUi() {
    if (!root) return;
    root.classList.toggle('island-minimap-collapsed', collapsed);
    ensureExpandIcon();
    if (collapseBtn) {
      collapseBtn.setAttribute('aria-expanded', collapsed ? 'false' : 'true');
      collapseBtn.textContent = collapsed ? '▾' : '−';
      collapseBtn.setAttribute('aria-label', collapsed ? '展開小地圖' : '縮小小地圖');
    }
  }

  function syncMinimapVisibility(exploring) {
    if (!root) return;
    if (!exploring || isMobileViewport()) {
      root.hidden = true;
      return;
    }
    root.hidden = false;
  }

  function setCollapsed(on) {
    collapsed = !!on;
    syncCollapsedUi();
  }

  function resolveLargeMapInterior(state) {
    if (!state || !state.interiorRoom) return null;
    if (mapOverlayMode === 'force-outdoor') return null;
    return state.interiorRoom;
  }

  function syncMapModeToggle(state) {
    if (!mapModeToggle) return;
    const inRoom = !!(state && state.interiorRoom);
    if (!inRoom) {
      mapModeToggle.hidden = true;
      return;
    }
    mapModeToggle.hidden = false;
  const showOutdoor = mapOverlayMode === 'force-outdoor';
    mapModeToggle.textContent = showOutdoor ? '室內平面圖' : '群島地圖';
    mapModeToggle.setAttribute(
      'aria-label',
      showOutdoor ? '切換到室內平面圖' : '切換到群島地圖'
    );
  }

  function outdoorPaintPlayer(state) {
    if (!state) return null;
    if (state.interiorRoom) {
      if (mapOverlayMode === 'force-outdoor' && state.worldPlayer) return state.worldPlayer;
      return null;
    }
    return state.player;
  }

  function syncMapPins(state) {
    if (!mapPins || !mapCanvas) return;
    const interiorRoom = resolveLargeMapInterior(state);
    const outdoor = !interiorRoom;
    mapPins.hidden = !outdoor;
    mapPins.setAttribute('aria-hidden', outdoor ? 'false' : 'true');
    if (!outdoor) return;
    const layouts = islandPinLayout(mapLogicalSize.width, mapLogicalSize.height);
    const player = outdoorPaintPlayer(state);
    let currentId = null;
    if (player && Number.isFinite(player.x) && Number.isFinite(player.z)) {
      const here = islandAt(player.x, player.z, 0.12);
      currentId = here ? here.id : null;
    }
    const buttons = mapPins.querySelectorAll('.island-map-pin');
    for (let i = 0; i < buttons.length; i++) {
      const btn = buttons[i];
      const id = btn.getAttribute('data-island');
      const row = layouts.find((item) => item.id === id);
      if (!row) continue;
      const pctX = (row.anchorX / mapLogicalSize.width) * 100;
      const pctY = (row.anchorY / mapLogicalSize.height) * 100;
      btn.style.left = pctX + '%';
      btn.style.top = pctY + '%';
      btn.classList.toggle('is-current', id === currentId);
    }
    if (mapCanvas) {
      mapCanvas.classList.toggle('island-map-canvas-hit', outdoor);
    }
  }

  function syncLargeMapCanvasSize() {
    if (!mapCanvas || !mapWrap) return;
    const cssW = Math.max(1, Math.floor(mapWrap.clientWidth || mapWrap.getBoundingClientRect().width));
    const aspect = largeMapWorldAspect();
    const pad = LARGE_MAP_PAD;
    const innerW = Math.max(1, cssW - pad * 2);
    const innerH = Math.max(1, Math.round(innerW * aspect));
    const cssH = innerH + pad * 2;
    const dpr = win && win.devicePixelRatio
      ? Math.min(2, Math.max(1, win.devicePixelRatio))
      : 1;
    mapLogicalSize = { width: cssW, height: cssH, dpr: dpr, innerW: innerW, innerH: innerH };
    mapCanvas.style.height = cssH + 'px';
    mapCanvas.width = Math.round(cssW * dpr);
    mapCanvas.height = Math.round(cssH * dpr);
  }

  function resetMapOverlay() {
    mapOverlayMode = 'default';
    hoverIslandId = null;
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
    syncPlayerAreaTitle(document, state, playerTitleCache);
    const hctx = hudCtx();
    if (hctx && !collapsed && root && !root.hidden && !isMobileViewport()) paintMinimap(hctx, paintOpts);
    const mapSheet = document.getElementById('islandMapSheet');
    const mctx = mapCtx();
    if (mctx && mapSheet && !mapSheet.hidden) {
      syncLargeMapCanvasSize();
      const interiorRoom = resolveLargeMapInterior(state);
      const outdoorPlayer = outdoorPaintPlayer(state);
      syncMapModeToggle(state);
      syncMapPins(state);
      paintMinimap(mctx, Object.assign({}, paintOpts, {
        large: true,
        padding: LARGE_MAP_PAD,
        interiorRoom: interiorRoom,
        player: outdoorPlayer || paintOpts.player,
        logicalWidth: mapLogicalSize.width,
        logicalHeight: mapLogicalSize.height,
        devicePixelRatio: mapLogicalSize.dpr || 1,
        hoverIslandId: hoverIslandId
      }));
    }
  }

  function tick(now) {
    const exploring = opts.isExploring && opts.isExploring();
    syncMinimapVisibility(exploring);
    if (!exploring) return;
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
  if (mapModeToggle) {
    mapModeToggle.addEventListener('click', (event) => {
      event.stopPropagation();
      if (mapOverlayMode === 'force-outdoor') mapOverlayMode = 'default';
      else mapOverlayMode = 'force-outdoor';
      lastPaint = 0;
      paintTargets();
    });
  }

  if (mapCanvas && typeof opts.onJumpToIsland === 'function') {
    function canvasPointFromEvent(event) {
      const rect = mapCanvas.getBoundingClientRect();
      if (!rect.width || !rect.height) return null;
      const x = ((event.clientX - rect.left) / rect.width) * mapLogicalSize.width;
      const y = ((event.clientY - rect.top) / rect.height) * mapLogicalSize.height;
      return { x: x, y: y };
    }
    mapCanvas.addEventListener('pointerdown', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) return;
      const getState = opts.getPaintState;
      const state = typeof getState === 'function' ? getState() : null;
      if (resolveLargeMapInterior(state)) return;
      const pt = canvasPointFromEvent(event);
      if (!pt) return;
      const hit = islandAtMapPoint(pt.x, pt.y, mapLogicalSize.width, mapLogicalSize.height, 12);
      if (hit) {
        event.preventDefault();
        opts.onJumpToIsland(hit);
      }
    });
    mapCanvas.addEventListener('pointermove', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) return;
      const getState = opts.getPaintState;
      const state = typeof getState === 'function' ? getState() : null;
      if (resolveLargeMapInterior(state)) {
        if (hoverIslandId) {
          hoverIslandId = null;
          lastPaint = 0;
          paintTargets();
        }
        return;
      }
      const pt = canvasPointFromEvent(event);
      if (!pt) return;
      const hit = islandAtMapPoint(pt.x, pt.y, mapLogicalSize.width, mapLogicalSize.height, 12);
      if (hit !== hoverIslandId) {
        hoverIslandId = hit;
        lastPaint = 0;
        paintTargets();
      }
    });
    mapCanvas.addEventListener('pointerleave', () => {
      if (!hoverIslandId) return;
      hoverIslandId = null;
      lastPaint = 0;
      paintTargets();
    });
  }

  if (mapWrap && typeof ResizeObserver !== 'undefined') {
    mapResizeObserver = new ResizeObserver(() => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (mapSheet && !mapSheet.hidden) {
        lastPaint = 0;
        paintTargets();
      }
    });
    mapResizeObserver.observe(mapWrap);
  } else if (win && typeof win.addEventListener === 'function') {
    mapWindowResizeListener = () => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (mapSheet && !mapSheet.hidden) {
        lastPaint = 0;
        paintTargets();
      }
    };
    win.addEventListener('resize', mapWindowResizeListener);
    win.addEventListener('orientationchange', mapWindowResizeListener);
  }

  {
    const mapSheet = document.getElementById('islandMapSheet');
    if (mapSheet && typeof MutationObserver !== 'undefined') {
      const obs = new MutationObserver(() => {
        if (!mapSheet.hidden) {
          lastPaint = 0;
          paintTargets();
        } else {
          resetMapOverlay();
        }
        if (typeof opts.onMapOpenChange === 'function') {
          opts.onMapOpenChange(!mapSheet.hidden);
        }
      });
      obs.observe(mapSheet, { attributes: true, attributeFilter: ['hidden'] });
    }
  }

  function onMedia() {
    if (isMobileViewport()) {
      syncMinimapVisibility(opts.isExploring && opts.isExploring());
      if (!collapsed) setCollapsed(true);
      return;
    }
    collapsed = false;
    syncCollapsedUi();
    syncMinimapVisibility(opts.isExploring && opts.isExploring());
  }
  if (mobileMq.addEventListener) mobileMq.addEventListener('change', onMedia);
  else if (mobileMq.addListener) mobileMq.addListener(onMedia);
  syncCollapsedUi();

  return {
    tick: tick,
    setCollapsed: setCollapsed,
    forceRepaint: function () { lastPaint = 0; paintTargets(); },
    resetMapOverlay: resetMapOverlay,
    destroy: function () {
      if (mapResizeObserver && mapWrap) mapResizeObserver.unobserve(mapWrap);
      if (mapWindowResizeListener && win) {
        win.removeEventListener('resize', mapWindowResizeListener);
        win.removeEventListener('orientationchange', mapWindowResizeListener);
      }
      if (mobileMq.removeEventListener) mobileMq.removeEventListener('change', onMedia);
      else if (mobileMq.removeListener) mobileMq.removeListener(onMedia);
    }
  };
}