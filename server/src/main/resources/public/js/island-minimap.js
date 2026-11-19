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
const PIN_FONT_PX_DESKTOP = 16;
const PIN_FONT_PX_MOBILE = 13;
const PIN_MIN_TOUCH = 44;
const MAP_POINTER_JUMP_SLOP = 10;

const PIN_MAP_LABELS = Object.freeze({
  tcm: Object.freeze(['中醫', '推拿'])
});

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

/** 依視窗推估大地圖面板內留給 canvas 區塊的可用高度（與 CSS 排版一致）。 */
export function largeMapCanvasAvailHeight(viewportWidth, viewportHeight) {
  const vw = viewportWidth > 0 ? viewportWidth : 390;
  const vh = viewportHeight > 0 ? viewportHeight : 800;
  const desktop = vw >= 761;
  const panelCap = desktop ? Math.min(vh * 0.86, 780) : Math.min(vh * 0.88, 720);
  const headerH = desktop ? 46 : 44;
  const legendH = 28;
  const wrapPadY = 13;
  return Math.max(72, panelCap - headerH - legendH - wrapPadY);
}

/** 與 .island-map-canvas-wrap 左右 padding（0.85rem×2 @16px）一致，供無 DOM 推估。 */
export const MAP_WRAP_CONTENT_PAD_X = 27.2;

/** 依 wrap 內容區寬度（已扣 padding）與可用高度算出 canvas CSS 尺寸。 */
export function largeMapCanvasSizeFromWrapContent(contentWidth, availHeight) {
  const aspect = largeMapWorldAspect();
  const pad = LARGE_MAP_PAD;
  const boxW = contentWidth > 0 ? contentWidth : 1;
  let innerW = Math.max(1, Math.floor(boxW - pad * 2));
  let innerH = Math.max(1, Math.round(innerW * aspect));
  if (availHeight != null && innerH > availHeight) {
    innerH = Math.max(60, Math.floor(availHeight));
    innerW = Math.max(1, Math.round(innerH / aspect));
  }
  const cssW = innerW + pad * 2;
  const cssH = innerH + pad * 2;
  return Object.freeze({
    width: cssW,
    height: cssH,
    innerWidth: innerW,
    innerHeight: innerH,
    padding: pad,
    availHeight: availHeight,
    contentWidth: boxW
  });
}

/** 依視窗推估 wrap 內容寬（與面板 CSS 一致）再算 canvas 尺寸。 */
export function largeMapCanvasCssSize(viewportWidth, viewportHeight) {
  const vw = viewportWidth > 0 ? viewportWidth : 390;
  const desktop = vw >= 761;
  const panelW = desktop ? Math.min(44 * 16, vw * 0.92) : vw - 16;
  const contentW = Math.max(120, panelW - MAP_WRAP_CONTENT_PAD_X);
  const availH = largeMapCanvasAvailHeight(vw, viewportHeight);
  return largeMapCanvasSizeFromWrapContent(contentW, availH);
}

function pinTextWidthPx(name, fontPx) {
  let w = 0;
  const text = String(name || '');
  for (let i = 0; i < text.length; i++) {
    const ch = text.charCodeAt(i);
    w += ch > 0x2e80 ? fontPx : fontPx * 0.58;
  }
  return w + 14;
}

function pinDisplayForName(name, fontPx, maxTextW) {
  const text = String(name || '');
  const fullW = pinTextWidthPx(text, fontPx);
  const lineH = Math.round(fontPx * 1.25);
  const padV = 8;
  function pack(lines, visualW) {
    return Object.freeze({
      lines: Object.freeze(lines.slice()),
      visualWidth: visualW,
      visualHeight: lineH * lines.length + padV
    });
  }
  if (text.length >= 5 && fontPx <= PIN_FONT_PX_MOBILE) {
    const mid = text.length <= 5 ? 2 : Math.ceil(text.length / 2);
    const a = text.slice(0, mid);
    const b = text.slice(mid);
    const splitW = Math.max(pinTextWidthPx(a, fontPx), pinTextWidthPx(b, fontPx));
    if (!maxTextW || splitW <= maxTextW || splitW < fullW) {
      return pack([a, b], splitW);
    }
  }
  if (maxTextW == null || fullW <= maxTextW) {
    return pack([text], fullW);
  }
  if (text.length >= 4) {
    const mid = text.length <= 5 ? 2 : Math.ceil(text.length / 2);
    const a = text.slice(0, mid);
    const b = text.slice(mid);
    const splitW = Math.max(pinTextWidthPx(a, fontPx), pinTextWidthPx(b, fontPx));
    if (splitW <= maxTextW || maxTextW <= 0) {
      return pack([a, b], splitW);
    }
  }
  return pack([text], fullW);
}

function circleHitsBox(cx, cy, radius, box) {
  const closestX = Math.max(box.left, Math.min(cx, box.right));
  const closestY = Math.max(box.top, Math.min(cy, box.bottom));
  const dx = cx - closestX;
  const dy = cy - closestY;
  return dx * dx + dy * dy <= radius * radius;
}

export function touchBoxOverlapsCircle(box, cx, cy, radius) {
  return circleHitsBox(cx, cy, radius, box);
}

function pinBoxFromCenter(x, y, w, h) {
  return Object.freeze({
    left: x - w * 0.5,
    top: y - h * 0.5,
    right: x + w * 0.5,
    bottom: y + h * 0.5,
    width: w,
    height: h,
    centerX: x,
    centerY: y
  });
}

export function pointInIslandEllipse(px, py, cx, cy, rx, ry, shrink) {
  const s = shrink == null ? 1 : shrink;
  const erx = rx * s;
  const ery = ry * s;
  if (erx <= 0 || ery <= 0) return false;
  const dx = (px - cx) / erx;
  const dy = (py - cy) / ery;
  return dx * dx + dy * dy <= 1;
}

/** 矩形（名牌點擊區）是否侵入另一座島的橢圓。 */
export function pinTouchBoxHitsForeignIsland(box, ownId, islands, shrink) {
  const pts = [
    [box.left, box.top],
    [box.right, box.top],
    [box.left, box.bottom],
    [box.right, box.bottom],
    [box.centerX != null ? box.centerX : (box.left + box.right) * 0.5,
      box.centerY != null ? box.centerY : (box.top + box.bottom) * 0.5]
  ];
  for (let i = 0; i < islands.length; i++) {
    const isl = islands[i];
    if (isl.id === ownId) continue;
    for (let p = 0; p < pts.length; p++) {
      if (pointInIslandEllipse(pts[p][0], pts[p][1], isl.centerX, isl.centerY, isl.rx, isl.ry, shrink)) {
        return true;
      }
    }
  }
  return false;
}

function boxesOverlap(a, b) {
  return !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom);
}

function boxInsideBounds(box, maxW, maxH, margin) {
  const m = margin == null ? 2 : margin;
  return box.left >= m && box.top >= m && box.right <= maxW - m && box.bottom <= maxH - m;
}

/** 世界座標玩家在大地圖 canvas 上的位置（含邊框 pad）。 */
export function playerMapPointFromWorld(player, cssWidth, cssHeight, padding) {
  if (!player || !Number.isFinite(player.x) || !Number.isFinite(player.z)) return null;
  const w = cssWidth > 0 ? cssWidth : 1;
  const h = cssHeight > 0 ? cssHeight : 1;
  const pad = padding == null ? LARGE_MAP_PAD : padding;
  const frame = mapInnerFrame(w, h, pad);
  const bounds = worldMapBounds();
  const c = worldToMap(player.x, player.z, bounds, frame.innerW, frame.innerH);
  return Object.freeze({ x: frame.pad + c.px, y: frame.pad + c.py });
}

/** 六島名牌版面：置於各自圓內、不重疊、點擊區不壓到別島／玩家標記。 */
export function islandPinLayout(cssWidth, cssHeight, opts) {
  const options = opts || {};
  const w = cssWidth > 0 ? cssWidth : 1;
  const h = cssHeight > 0 ? cssHeight : 1;
  const padding = options.padding == null ? LARGE_MAP_PAD : options.padding;
  const fontPx = w < 420 ? PIN_FONT_PX_MOBILE : PIN_FONT_PX_DESKTOP;
  const playerObs = options.playerObstacle;
  const playerR = playerObs && playerObs.radius > 0 ? playerObs.radius : 0;
  const bounds = worldMapBounds();
  const frame = mapInnerFrame(w, h, padding);
  const spanX = bounds.maxX - bounds.minX || 1;
  const spanZ = bounds.maxZ - bounds.minZ || 1;
  const islandGeo = [];
  for (let gi = 0; gi < ISLANDS.length; gi++) {
    const isl = ISLANDS[gi];
    const vr = isl.visualR != null ? isl.visualR : isl.r;
    const c = worldToMap(isl.x, isl.z, bounds, frame.innerW, frame.innerH);
    islandGeo.push({
      id: isl.id,
      name: isl.name,
      centerX: frame.pad + c.px,
      centerY: frame.pad + c.py,
      rx: (vr / spanX) * frame.innerW,
      ry: (vr / spanZ) * frame.innerH
    });
  }
  const placedTouch = [];
  const out = [];

  for (let i = 0; i < ISLANDS.length; i++) {
    const isl = ISLANDS[i];
    const geo = islandGeo[i];
    const centerX = geo.centerX;
    const centerY = geo.centerY;
    const rx = geo.rx;
    const ry = geo.ry;
    const labelOverride = PIN_MAP_LABELS[isl.id];
    const labelText = labelOverride ? labelOverride.join('') : isl.name;
    const maxTextW = Math.max(
      PIN_MIN_TOUCH * 0.82,
      Math.min(rx * 1.55, rx * 2 + ry * 0.35, rx * 1.85)
    );
    const display = labelOverride
      ? Object.freeze({
        lines: labelOverride,
        visualWidth: Math.max(
          pinTextWidthPx(labelOverride[0], fontPx),
          pinTextWidthPx(labelOverride[1], fontPx)
        ),
        visualHeight: Math.round(fontPx * 1.25) * 2 + 8
      })
      : pinDisplayForName(labelText, fontPx, maxTextW);
    const visualW = display.visualWidth;
    const visualH = display.visualHeight;
    const touchW = Math.max(PIN_MIN_TOUCH, visualW);
    const touchH = Math.max(PIN_MIN_TOUCH, visualH);

    const offsets = [];
    if (playerObs && playerR > 0 && options.playerIslandId === isl.id) {
      const pdx = playerObs.x - centerX;
      const pdy = playerObs.y - centerY;
      const pd = Math.hypot(pdx, pdy) || 1;
      offsets.push({
        dx: (-pdx / pd) * rx * 0.52,
        dy: (-pdy / pd) * ry * 0.52
      });
      offsets.push({
        dx: (-pdx / pd) * rx * 0.34,
        dy: (-pdy / pd) * ry * 0.34
      });
      offsets.push({ dx: -rx * 0.55, dy: -ry * 0.42 });
      offsets.push({ dx: -rx * 0.42, dy: -ry * 0.55 });
      offsets.push({ dx: -rx * 0.35, dy: -ry * 0.35 });
    }
    offsets.push(
      { dx: 0, dy: 0 },
      { dx: 0, dy: -ry * 0.28 },
      { dx: 0, dy: ry * 0.22 },
      { dx: -rx * 0.26, dy: 0 },
      { dx: rx * 0.26, dy: 0 },
      { dx: -rx * 0.2, dy: -ry * 0.2 },
      { dx: rx * 0.2, dy: -ry * 0.2 },
      { dx: -rx * 0.18, dy: ry * 0.18 },
      { dx: rx * 0.18, dy: ry * 0.18 },
      { dx: 0, dy: -ry * 0.12 },
      { dx: -rx * 0.12, dy: -ry * 0.32 },
      { dx: rx * 0.12, dy: ry * 0.3 }
    );

    function placementOk(touchBox, anchorX, anchorY) {
      if (!pointInIslandEllipse(anchorX, anchorY, centerX, centerY, rx, ry, 0.88)) return false;
      if (!boxInsideBounds(touchBox, w, h, 2)) return false;
      if (pinTouchBoxHitsForeignIsland(touchBox, isl.id, islandGeo, 0.9)) return false;
      if (playerObs && playerR > 0) {
        const visualBox = pinBoxFromCenter(anchorX, anchorY, visualW, visualH);
        if (circleHitsBox(playerObs.x, playerObs.y, playerR, visualBox)) return false;
      }
      for (let p = 0; p < placedTouch.length; p++) {
        if (boxesOverlap(touchBox, placedTouch[p])) return false;
      }
      return true;
    }

    let chosen = null;
    for (let o = 0; o < offsets.length; o++) {
      const anchorX = centerX + offsets[o].dx;
      const anchorY = centerY + offsets[o].dy;
      const touchBox = pinBoxFromCenter(anchorX, anchorY, touchW, touchH);
      if (placementOk(touchBox, anchorX, anchorY)) {
        chosen = { anchorX: anchorX, anchorY: anchorY, touchBox: touchBox };
        break;
      }
    }
    if (!chosen && playerObs && playerR > 0) {
      let best = null;
      let bestDist = -1;
      for (let gy = -0.72; gy <= 0.72; gy += 0.09) {
        for (let gx = -0.72; gx <= 0.72; gx += 0.09) {
          const anchorX = centerX + rx * gx;
          const anchorY = centerY + ry * gy;
          const touchBox = pinBoxFromCenter(anchorX, anchorY, touchW, touchH);
          if (!pointInIslandEllipse(anchorX, anchorY, centerX, centerY, rx, ry, 0.86)) continue;
          if (!boxInsideBounds(touchBox, w, h, 2)) continue;
          if (pinTouchBoxHitsForeignIsland(touchBox, isl.id, islandGeo, 0.9)) continue;
          let clash = false;
          for (let p = 0; p < placedTouch.length; p++) {
            if (boxesOverlap(touchBox, placedTouch[p])) {
              clash = true;
              break;
            }
          }
          if (clash) continue;
          const visualBox = pinBoxFromCenter(anchorX, anchorY, visualW, visualH);
          if (circleHitsBox(playerObs.x, playerObs.y, playerR, visualBox)) continue;
          const dist = Math.hypot(anchorX - playerObs.x, anchorY - playerObs.y);
          if (dist > bestDist) {
            bestDist = dist;
            best = { anchorX: anchorX, anchorY: anchorY, touchBox: touchBox };
          }
        }
      }
      if (best) chosen = best;
    }
    if (!chosen) {
      const anchorX = centerX;
      const anchorY = centerY;
      const touchBox = pinBoxFromCenter(anchorX, anchorY, touchW, touchH);
      chosen = { anchorX: anchorX, anchorY: anchorY, touchBox: touchBox };
    }
    placedTouch.push(chosen.touchBox);
    const visualBox = pinBoxFromCenter(chosen.anchorX, chosen.anchorY, visualW, visualH);
    out.push(Object.freeze({
      id: isl.id,
      name: isl.name,
      lines: display.lines,
      anchorX: chosen.anchorX,
      anchorY: chosen.anchorY,
      centerX: centerX,
      centerY: centerY,
      rx: rx,
      ry: ry,
      fontPx: fontPx,
      visualWidth: visualW,
      visualHeight: visualH,
      touchWidth: touchW,
      touchHeight: touchH,
      box: visualBox,
      touchBox: chosen.touchBox
    }));
  }
  return Object.freeze(out);
}

/** 室內 → 所屬群島 id（依 buildingId）。 */
export function islandIdForInteriorRoom(room) {
  if (!room || !room.buildingId) return null;
  for (let i = 0; i < BUILDINGS.length; i++) {
    const b = BUILDINGS[i];
    if (b.id === room.buildingId) return b.island || null;
  }
  return null;
}

/** 室內切換群島地圖時，玩家標記畫在建築門口。 */
export function outdoorPlayerPoseFromInterior(room) {
  if (!room) return null;
  if (room.outside && Number.isFinite(room.outside.x) && Number.isFinite(room.outside.z)) {
    return Object.freeze({
      x: room.outside.x,
      z: room.outside.z,
      yaw: room.outside.yaw == null ? 0 : room.outside.yaw
    });
  }
  for (let i = 0; i < BUILDINGS.length; i++) {
    const b = BUILDINGS[i];
    if (b.id !== room.buildingId) continue;
    const door = b.door || worldDoor(b);
    if (door && Number.isFinite(door.x) && Number.isFinite(door.z)) {
      return Object.freeze({ x: door.x, z: door.z, yaw: b.yaw == null ? 0 : b.yaw });
    }
    break;
  }
  return null;
}

/** pointerdown→pointerup 同一島且位移小於 slop 才跳島。 */
export function shouldJumpIslandOnPointerUp(down, up, slop) {
  if (!down || !up || !down.islandId) return null;
  if (down.islandId !== up.islandId) return null;
  const limit = slop == null ? MAP_POINTER_JUMP_SLOP : slop;
  const dx = up.x - down.x;
  const dy = up.y - down.y;
  if (dx * dx + dy * dy > limit * limit) return null;
  return down.islandId;
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

function drawAnchorIcon(ctx, px, py, scale) {
  const s = scale || 1;
  const navy = '#163a70';
  ctx.strokeStyle = navy;
  ctx.fillStyle = MARKER_COLORS.boat;
  ctx.lineWidth = Math.max(1.15, 1.3 * s);
  ctx.lineCap = 'round';
  ctx.lineJoin = 'round';
  const ringY = py - 3.8 * s;
  ctx.beginPath();
  ctx.arc(px, ringY, 2.4 * s, 0, Math.PI * 2);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px, ringY + 2.5 * s);
  ctx.lineTo(px, py + 1.5 * s);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px - 3.2 * s, py - 0.4 * s);
  ctx.lineTo(px + 3.2 * s, py - 0.4 * s);
  ctx.stroke();
  const flukeY = py + 2.2 * s;
  ctx.beginPath();
  ctx.moveTo(px, py + 1.2 * s);
  ctx.bezierCurveTo(px - 5.5 * s, py + 4.5 * s, px - 5.5 * s, flukeY + 2 * s, px - 5 * s, flukeY + 2.5 * s);
  ctx.stroke();
  ctx.beginPath();
  ctx.moveTo(px, py + 1.2 * s);
  ctx.bezierCurveTo(px + 5.5 * s, py + 4.5 * s, px + 5.5 * s, flukeY + 2 * s, px + 5 * s, flukeY + 2.5 * s);
  ctx.stroke();
  ctx.fillStyle = navy;
  ctx.beginPath();
  ctx.arc(px - 5 * s, flukeY + 2.5 * s, 0.85 * s, 0, Math.PI * 2);
  ctx.arc(px + 5 * s, flukeY + 2.5 * s, 0.85 * s, 0, Math.PI * 2);
  ctx.fill();
}

/** 楓之谷風方向指標（亮黃填色、深藍描邊），供大地圖與圖例。 */
export function drawMapleDirectionPointer(ctx, x, y, yaw, sizePx) {
  const len = sizePx > 0 ? sizePx : 22;
  const half = len * 0.38;
  const tip = len * 0.52;
  ctx.save();
  ctx.translate(x, y);
  ctx.rotate(yaw || 0);
  ctx.fillStyle = '#ffd93a';
  ctx.strokeStyle = '#163a70';
  ctx.lineWidth = Math.max(1.6, len * 0.08);
  ctx.lineJoin = 'round';
  ctx.beginPath();
  ctx.moveTo(0, -tip);
  ctx.lineTo(half, half * 0.45);
  ctx.lineTo(0, half * 0.15);
  ctx.lineTo(-half, half * 0.45);
  ctx.closePath();
  ctx.fill();
  ctx.stroke();
  ctx.restore();
}

export function largeMapPlayerMarkerRadius(scale, mobile) {
  const s = scale || 1;
  const base = mobile ? 10 : 11;
  return base * s + 4;
}

/** 名牌排版避開玩家圖示時用的圓半徑（略小於完整箭頭外框）。 */
export function largeMapPlayerLabelAvoidRadius(scale, mobile) {
  const s = scale || 1;
  return (mobile ? 9 : 10) * s + 2;
}

/** 玩家圖示繪製點：若壓到目前島名牌則沿遠離名牌方向微移。 */
export function largeMapPlayerDrawPoint(mapX, mapY, pinLayout, gapPx) {
  if (!pinLayout || !Number.isFinite(mapX) || !Number.isFinite(mapY)) {
    return Object.freeze({ x: mapX, y: mapY });
  }
  const gap = gapPx == null ? 16 : gapPx;
  const box = pinLayout.box;
  const r = largeMapPlayerLabelAvoidRadius(1, pinLayout.fontPx <= PIN_FONT_PX_MOBILE);
  if (!box || !circleHitsBox(mapX, mapY, r, box)) {
    return Object.freeze({ x: mapX, y: mapY });
  }
  const cx = (box.left + box.right) * 0.5;
  const cy = (box.top + box.bottom) * 0.5;
  let dx = mapX - cx;
  let dy = mapY - cy;
  const len = Math.hypot(dx, dy);
  if (len < 0.5) {
    dx = 0;
    dy = -1;
  } else {
    dx /= len;
    dy /= len;
  }
  let x = mapX;
  let y = mapY;
  for (let step = 0; step < 5; step++) {
    if (!circleHitsBox(x, y, r, box)) break;
    x += dx * gap;
    y += dy * gap;
  }
  return Object.freeze({ x: x, y: y });
}

export function drawLargeMapPlayerMarker(ctx, x, y, yaw, scale, nightMix, mobile) {
  const s = scale || 1;
  const size = (mobile ? 20 : 24) * s;
  const t = nightMix == null ? 0 : Math.max(0, Math.min(1, nightMix));
  ctx.save();
  if (t > 0.02) {
    ctx.globalAlpha = 1 - t * 0.18;
  }
  drawMapleDirectionPointer(ctx, x, y, yaw, size);
  ctx.restore();
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
    const pressed = frame.largeMap && frame.pressedIslandId === isl.id;
    const hovered = frame.largeMap && frame.hoverIslandId === isl.id;
  const active = pressed || hovered;
    ctx.fillStyle = pressed ? '#7bc96a' : (hovered ? '#a4e088' : MARKER_COLORS.land);
    ctx.strokeStyle = pressed ? '#1f4a28' : (hovered ? '#2d6b38' : MARKER_COLORS.landStroke);
    ctx.lineWidth = active ? 2.4 : 1.2;
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
        drawAnchorIcon(ctx, px, py, frame.scale * 0.95);
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
  const playerOnOverlay = frame.largeMap && frame.playerOnOverlay;
  if (!playerOnOverlay && player && Number.isFinite(player.x) && Number.isFinite(player.z)) {
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

/** 大地圖玩家疊加層（高於名牌 DOM）。 */
export function paintLargeMapPlayerOverlay(ctx, opts) {
  const options = opts || {};
  const player = options.player;
  if (!player || !Number.isFinite(player.x) || !Number.isFinite(player.z)) return;
  const w = options.logicalWidth > 0 ? options.logicalWidth : 1;
  const h = options.logicalHeight > 0 ? options.logicalHeight : 1;
  const pad = options.padding == null ? LARGE_MAP_PAD : options.padding;
  const frame = mapInnerFrame(w, h, pad);
  const bounds = worldMapBounds();
  const dpr = options.devicePixelRatio == null ? 1 : options.devicePixelRatio;
  ctx.save();
  if (dpr !== 1) ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  else ctx.setTransform(1, 0, 0, 1, 0, 0);
  ctx.clearRect(0, 0, w, h);
  const p = worldToMap(player.x, player.z, bounds, frame.innerW, frame.innerH);
  const mobile = options.logicalWidth > 0 && options.logicalWidth < 420;
  const baseX = pad + p.px;
  const baseY = pad + p.py;
  const drawPt = largeMapPlayerDrawPoint(baseX, baseY, options.avoidPinLayout, mobile ? 14 : 16);
  drawLargeMapPlayerMarker(
    ctx,
    drawPt.x,
    drawPt.y,
    player.yaw,
    options.scale || 1,
    options.nightMix,
    mobile
  );
  ctx.restore();
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
    hoverIslandId: options.hoverIslandId || null,
    pressedIslandId: options.pressedIslandId || null,
    playerOnOverlay: !!options.playerOnOverlay
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

/** 大地圖面板標題（室內顯示房名，群島／強制戶外顯示群島地圖）。 */
export function resolveLargeMapSheetTitle(interiorRoom, forceOutdoor) {
  if (interiorRoom && !forceOutdoor) {
    return resolveAreaName({ interiorRoom: interiorRoom });
  }
  return '群島地圖';
}

/** 大地圖模式切換鈕：顯示將切換到的模式。 */
export function resolveLargeMapModeToggleLabel(forceOutdoor) {
  return forceOutdoor ? '室內平面圖' : '群島地圖';
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
  const mapTitleEl = document.getElementById('islandMapTitle');
  const bodyEl = document.getElementById('islandMinimapBody');
  const hudCanvas = document.getElementById('islandMinimapCanvas');
  const mapCanvas = document.getElementById('islandMapCanvas');
  const mapPlayerCanvas = document.getElementById('islandMapPlayerCanvas');
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
  let pressedIslandId = null;
  let pointerDown = null;
  let mapLogicalSize = { width: 480, height: 360 };
  let currentIslandPinLayout = null;
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

  function mapPlayerCtx() {
    return mapPlayerCanvas && mapPlayerCanvas.getContext ? mapPlayerCanvas.getContext('2d') : null;
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
    const label = resolveLargeMapModeToggleLabel(showOutdoor);
    mapModeToggle.textContent = label;
    mapModeToggle.setAttribute('aria-label', '切換到' + label);
  }

  function syncMapSheetTitle(state) {
    if (!mapTitleEl) return;
    const interiorRoom = state && state.interiorRoom;
    const showOutdoor = mapOverlayMode === 'force-outdoor';
    mapTitleEl.textContent = resolveLargeMapSheetTitle(interiorRoom, showOutdoor);
  }

  function outdoorPaintPlayer(state) {
    if (!state) return null;
    if (state.interiorRoom) {
      if (mapOverlayMode !== 'force-outdoor') return null;
      return outdoorPlayerPoseFromInterior(state.interiorRoom) || state.worldPlayer;
    }
    return state.player;
  }

  function outdoorCurrentIslandId(state) {
    if (!state) return null;
    if (state.interiorRoom) {
      if (mapOverlayMode === 'force-outdoor') return islandIdForInteriorRoom(state.interiorRoom);
      return null;
    }
    const player = outdoorPaintPlayer(state);
    if (player && Number.isFinite(player.x) && Number.isFinite(player.z)) {
      const here = islandAt(player.x, player.z, 0.12);
      return here ? here.id : null;
    }
    return null;
  }

  function syncMapPins(state) {
    if (!mapPins || !mapCanvas) return;
    const interiorRoom = resolveLargeMapInterior(state);
    const outdoor = !interiorRoom;
    mapPins.hidden = !outdoor;
    mapPins.setAttribute('aria-hidden', outdoor ? 'false' : 'true');
    if (!outdoor) return;
    const currentId = outdoorCurrentIslandId(state);
    const outdoorPlayer = outdoorPaintPlayer(state);
    const playerPt = outdoorPlayer
      ? playerMapPointFromWorld(
        outdoorPlayer,
        mapLogicalSize.width,
        mapLogicalSize.height,
        LARGE_MAP_PAD
      )
      : null;
    const mobile = mapLogicalSize.width < 420;
    const obstacle = playerPt
      ? {
        x: playerPt.x,
        y: playerPt.y,
        radius: largeMapPlayerLabelAvoidRadius(1, mobile)
      }
      : null;
    const layouts = islandPinLayout(mapLogicalSize.width, mapLogicalSize.height, {
      playerObstacle: obstacle,
      playerIslandId: currentId
    });
    currentIslandPinLayout = currentId ? layouts.find((row) => row.id === currentId) : null;
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
      btn.style.fontSize = row.fontPx + 'px';
      btn.classList.toggle('is-current', id === currentId);
      btn.classList.toggle('is-multiline', row.lines && row.lines.length > 1);
      const label = btn.querySelector('.island-map-pin-label');
      const lines = row.lines || [row.name];
      const text = lines.join('\n');
      if (label) {
        if (label.textContent !== text) label.textContent = text;
      } else {
        btn.textContent = '';
        const span = document.createElement('span');
        span.className = 'island-map-pin-label';
        span.textContent = text;
        btn.appendChild(span);
      }
    }
    if (mapCanvas) {
      mapCanvas.classList.toggle('island-map-canvas-hit', outdoor);
    }
  }

  function mapWrapAvailableHeight() {
    if (!mapWrap) return null;
    const panel = mapWrap.closest ? mapWrap.closest('.island-map-panel') : null;
    if (panel && panel.clientHeight > 0) {
      const bar = panel.querySelector('.island-map-bar');
      const legendEl = panel.querySelector('.island-map-legend');
      const barH = bar ? bar.offsetHeight : 46;
      const legH = legendEl ? legendEl.offsetHeight : 28;
      let padY = 13;
      if (win && typeof win.getComputedStyle === 'function') {
        const cs = win.getComputedStyle(mapWrap);
        padY = (parseFloat(cs.paddingTop) || 0) + (parseFloat(cs.paddingBottom) || 0);
      }
      return Math.max(72, panel.clientHeight - barH - legH - padY);
    }
    if (win && typeof win.innerHeight === 'number') {
      const vw = win.innerWidth || 390;
      return largeMapCanvasAvailHeight(vw, win.innerHeight);
    }
    return null;
  }

  function mapWrapContentWidth() {
    if (!mapWrap) return 1;
    let contentW = Math.max(1, Math.floor(mapWrap.clientWidth || mapWrap.getBoundingClientRect().width));
    if (win && typeof win.getComputedStyle === 'function') {
      const cs = win.getComputedStyle(mapWrap);
      const pl = parseFloat(cs.paddingLeft) || 0;
      const pr = parseFloat(cs.paddingRight) || 0;
      contentW = Math.max(1, Math.floor(contentW - pl - pr));
    }
    return contentW;
  }

  function syncLargeMapCanvasSize() {
    if (!mapCanvas || !mapWrap) return;
    const contentW = mapWrapContentWidth();
    const availH = mapWrapAvailableHeight();
    const sized = largeMapCanvasSizeFromWrapContent(contentW, availH);
    const pad = sized.padding;
    const innerW = sized.innerWidth;
    const innerH = sized.innerHeight;
    const cssW = sized.width;
    const cssH = sized.height;
    const dpr = win && win.devicePixelRatio
      ? Math.min(2, Math.max(1, win.devicePixelRatio))
      : 1;
    mapLogicalSize = { width: cssW, height: cssH, dpr: dpr, innerW: innerW, innerH: innerH };
    mapCanvas.style.width = cssW + 'px';
    mapCanvas.style.height = cssH + 'px';
    mapCanvas.style.marginLeft = 'auto';
    mapCanvas.style.marginRight = 'auto';
    mapCanvas.width = Math.round(cssW * dpr);
    mapCanvas.height = Math.round(cssH * dpr);
    if (mapPlayerCanvas) {
      mapPlayerCanvas.style.width = cssW + 'px';
      mapPlayerCanvas.style.height = cssH + 'px';
      mapPlayerCanvas.width = Math.round(cssW * dpr);
      mapPlayerCanvas.height = Math.round(cssH * dpr);
    }
    syncMapOverlayGeometry(cssW, cssH);
  }

  function syncMapOverlayGeometry(cssW, cssH) {
    if (!mapWrap || !mapCanvas) return;
    const left = mapCanvas.offsetLeft;
    const top = mapCanvas.offsetTop;
    const geom = cssW + 'px';
    const geoh = cssH + 'px';
    if (mapPins) {
      mapPins.style.left = left + 'px';
      mapPins.style.top = top + 'px';
      mapPins.style.width = geom;
      mapPins.style.height = geoh;
    }
    if (mapPlayerCanvas) {
      mapPlayerCanvas.style.left = left + 'px';
      mapPlayerCanvas.style.top = top + 'px';
      mapPlayerCanvas.style.width = geom;
      mapPlayerCanvas.style.height = geoh;
    }
  }

  function resetMapOverlay() {
    mapOverlayMode = 'default';
    hoverIslandId = null;
  }

  function paintTargets() {
    const getState = opts.getPaintState;
    if (typeof getState !== 'function') return;
    const state = getState();
    if (titleEl) {
      titleEl.textContent = resolveAreaName({
        interiorRoom: resolveLargeMapInterior(state) || state.interiorRoom,
        x: state.player ? state.player.x : 0,
        z: state.player ? state.player.z : 0
      });
    }
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
      const outdoorLarge = !interiorRoom;
      syncMapSheetTitle(state);
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
        hoverIslandId: hoverIslandId,
        pressedIslandId: pressedIslandId,
        playerOnOverlay: outdoorLarge
      }));
      const pctx = mapPlayerCtx();
      if (pctx && outdoorLarge && outdoorPlayer) {
        paintLargeMapPlayerOverlay(pctx, {
          player: outdoorPlayer,
          logicalWidth: mapLogicalSize.width,
          logicalHeight: mapLogicalSize.height,
          devicePixelRatio: mapLogicalSize.dpr || 1,
          padding: LARGE_MAP_PAD,
          scale: 1,
          nightMix: paintOpts.nightMix,
          avoidPinLayout: currentIslandPinLayout
        });
      } else if (pctx) {
        const dpr = mapLogicalSize.dpr || 1;
        pctx.save();
        if (dpr !== 1) pctx.setTransform(dpr, 0, 0, dpr, 0, 0);
        else pctx.setTransform(1, 0, 0, 1, 0, 0);
        pctx.clearRect(0, 0, mapLogicalSize.width, mapLogicalSize.height);
        pctx.restore();
      }
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

  let pinPointerDown = null;
  let pinJumpHandledByPointer = false;

  if (mapPins && typeof opts.onJumpToIsland === 'function') {
    function pinIslandFromEvent(event) {
      const btn = event.target && event.target.closest
        ? event.target.closest('.island-map-pin')
        : null;
      if (!btn) return null;
      return btn.getAttribute('data-island');
    }
    mapPins.addEventListener('pointerdown', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) return;
      const islandId = pinIslandFromEvent(event);
      if (!islandId) return;
      pinPointerDown = {
        islandId: islandId,
        x: event.clientX,
        y: event.clientY,
        pointerId: event.pointerId
      };
      if (typeof event.target.setPointerCapture === 'function') {
        try { event.target.setPointerCapture(event.pointerId); } catch (_e) { /* ignore */ }
      }
    });
    mapPins.addEventListener('pointerup', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) {
        pinPointerDown = null;
        return;
      }
      const islandId = pinIslandFromEvent(event);
      const jumpId = shouldJumpIslandOnPointerUp(
        pinPointerDown,
        islandId ? { islandId: islandId, x: event.clientX, y: event.clientY } : null,
        MAP_POINTER_JUMP_SLOP
      );
      pinPointerDown = null;
      if (jumpId) {
        event.preventDefault();
        event.stopPropagation();
        pinJumpHandledByPointer = true;
        opts.onJumpToIsland(jumpId);
      }
    });
    mapPins.addEventListener('pointercancel', () => { pinPointerDown = null; });
    mapPins.addEventListener('click', (event) => {
      if (pinJumpHandledByPointer) {
        pinJumpHandledByPointer = false;
        event.preventDefault();
        event.stopPropagation();
        return;
      }
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) return;
      const islandId = pinIslandFromEvent(event);
      if (islandId) {
        event.preventDefault();
        event.stopPropagation();
        opts.onJumpToIsland(islandId);
      }
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
    function clearPointerPress() {
      pointerDown = null;
      if (pressedIslandId) {
        pressedIslandId = null;
        lastPaint = 0;
        paintTargets();
      }
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
        event.stopPropagation();
        pointerDown = { islandId: hit, x: pt.x, y: pt.y };
        pressedIslandId = hit;
        if (typeof mapCanvas.setPointerCapture === 'function') {
          try { mapCanvas.setPointerCapture(event.pointerId); } catch (_e) { /* ignore */ }
        }
        lastPaint = 0;
        paintTargets();
      }
    });
    mapCanvas.addEventListener('pointerup', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) {
        clearPointerPress();
        return;
      }
      const getState = opts.getPaintState;
      const state = typeof getState === 'function' ? getState() : null;
      if (resolveLargeMapInterior(state)) {
        clearPointerPress();
        return;
      }
      const pt = canvasPointFromEvent(event);
      const upHit = pt
        ? islandAtMapPoint(pt.x, pt.y, mapLogicalSize.width, mapLogicalSize.height, 12)
        : null;
      const jumpId = shouldJumpIslandOnPointerUp(
        pointerDown,
        pt && upHit ? { islandId: upHit, x: pt.x, y: pt.y } : null,
        MAP_POINTER_JUMP_SLOP
      );
      pointerDown = null;
      pressedIslandId = null;
      lastPaint = 0;
      if (jumpId) {
        event.preventDefault();
        event.stopPropagation();
        opts.onJumpToIsland(jumpId);
      }
      paintTargets();
    });
    mapCanvas.addEventListener('click', (event) => {
      const mapSheet = document.getElementById('islandMapSheet');
      if (!mapSheet || mapSheet.hidden) return;
      const getState = opts.getPaintState;
      const state = typeof getState === 'function' ? getState() : null;
      if (resolveLargeMapInterior(state)) return;
      if (pointerDown) return;
      const pt = canvasPointFromEvent(event);
      if (!pt) return;
      const hit = islandAtMapPoint(pt.x, pt.y, mapLogicalSize.width, mapLogicalSize.height, 12);
      if (hit) {
        event.preventDefault();
        event.stopPropagation();
        opts.onJumpToIsland(hit);
      }
    });
    mapCanvas.addEventListener('pointercancel', clearPointerPress);
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
      if (pointerDown) clearPointerPress();
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