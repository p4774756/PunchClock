// 群島清單（純資料，不依賴 Three.js／DOM）。
//
// 加一座島：在 ISLANDS 加 { id, name, x, z, r, visualR, theme }，
// 再在 BRIDGES 用 connectBridge 連到既有的島。r 是可行走半徑，visualR 是草地外觀半徑。
// 加一棟建築：在 BUILDINGS 加 { id, island, x, z, w, d, yaw, label, panel }。
// 碰撞框預設依寬深與 yaw 產生（門口那一側內縮 frontTrim）。主島舊建築已有 OBSTACLE_RECTS，設 collide: false。
// 要能互動就加 entry: { r, reach, label, action }，reach 是從建築中心沿門口方向（本地 +Z）往外的額外距離。
// 樹、碼頭這類不是建築的碰撞放在 OBSTACLE_RECTS／OBSTACLE_CIRCLES，順序請維持既有主島在前。

export const MAIN_ISLAND_ID = 'main';

const ISLAND_DEFS = [
  { id: 'main', name: '手搖店島', x: 0, z: 0, r: 8.05, visualR: 9.15, theme: 'shop' },
  // 博弈島在東南，橋不經過正東岸（既有測試把 +X 岸當海水）。
  { id: 'casino', name: '博弈島', x: 15.2, z: -7.4, r: 4.15, visualR: 5.3, theme: 'casino' },
  // 中醫島在正西，橋從樹與候餐座位之間的西岸接出去。
  { id: 'tcm', name: '中醫推拿島', x: -16.6, z: 1.15, r: 4.0, visualR: 5.15, theme: 'tcm' },
  // 電腦島在南邊，橋從碼頭接出去。
  { id: 'computer', name: '電腦島', x: 0.45, z: 17.1, r: 4.0, visualR: 5.15, theme: 'computer' }
];

export const ISLANDS = Object.freeze(ISLAND_DEFS.map((island) => Object.freeze(island)));

export function islandById(id) {
  for (let i = 0; i < ISLANDS.length; i++) {
    if (ISLANDS[i].id === id) return ISLANDS[i];
  }
  return null;
}

export const MAIN_ISLAND = islandById(MAIN_ISLAND_ID);

/** 全景鏡頭。reach 要罩住每座島的外緣。 */
export const PANORAMA = Object.freeze({
  look: Object.freeze([0.2, 1.2, 0.8]),
  radius: 108,
  theta: 0.62,
  phi: 1.04,
  fov: 25
});

export function panoramaReach(pose) {
  const view = pose || PANORAMA;
  const fov = ((view.fov || 25) * Math.PI) / 180;
  const dist = view.radius * 1.07;
  return dist * Math.tan(fov / 2) * 2.2;
}

export function islandInPanorama(island, pose) {
  const view = pose || PANORAMA;
  const dx = island.x - view.look[0];
  const dz = island.z - view.look[2];
  return Math.hypot(dx, dz) + island.visualR <= panoramaReach(view);
}

/** 清單跳到某一座島時的鏡頭。主島用原本的近景，衛星島從外側看向橋。 */
export function poseForIsland(id) {
  const island = islandById(id);
  if (!island) return null;
  if (island.id === MAIN_ISLAND_ID) {
    return { look: [0.15, 0.95, 1.35], radius: 28.4, theta: 0.48, phi: 0.93 };
  }
  return {
    look: [island.x, 1.25, island.z],
    radius: 18.5,
    theta: Math.atan2(island.x, island.z),
    phi: 0.98
  };
}

// 與 Three.js Object3D.rotation.y 相同：本地 +Z 轉到 (sin(yaw), cos(yaw))。
function rotateY(x, z, yaw) {
  const c = Math.cos(yaw || 0);
  const s = Math.sin(yaw || 0);
  return { x: x * c + z * s, z: -x * s + z * c };
}

/** 橋面中心線。兩端各伸進島內 overlap，接上內縮後的可行走區。 */
export function connectBridge(fromId, toId, halfWidth, overlap) {
  const from = islandById(fromId);
  const to = islandById(toId);
  const dx = to.x - from.x;
  const dz = to.z - from.z;
  const len = Math.hypot(dx, dz) || 1;
  const ux = dx / len;
  const uz = dz / len;
  const pad = overlap == null ? 1.15 : overlap;
  return Object.freeze({
    id: 'bridge-' + fromId + '-' + toId,
    from: fromId,
    to: toId,
    halfWidth: halfWidth == null ? 0.88 : halfWidth,
    ax: from.x + ux * (from.r - pad),
    az: from.z + uz * (from.r - pad),
    bx: to.x - ux * (to.r - pad),
    bz: to.z - uz * (to.r - pad)
  });
}

function spanBridge(fromId, toId, ax, az, bx, bz, halfWidth) {
  return Object.freeze({
    id: 'bridge-' + fromId + '-' + toId,
    from: fromId,
    to: toId,
    halfWidth: halfWidth == null ? 0.88 : halfWidth,
    ax: ax,
    az: az,
    bx: bx,
    bz: bz
  });
}

export const BRIDGES = Object.freeze([
  // 東南岸 → 博弈島。避開正東 (8, 0) 的海水測試點，也避開東南角的樹。
  spanBridge('main', 'casino', 7.15, -2.15, 12.55, -5.55, 0.88),
  // 西岸 → 中醫島。
  spanBridge('main', 'tcm', -7.05, 0.9, -13.85, 1.05, 0.88),
  connectBridge('main', 'computer', 0.88, 1.15)
]);

export function bridgeById(id) {
  for (let i = 0; i < BRIDGES.length; i++) {
    if (BRIDGES[i].id === id || BRIDGES[i].to === id) return BRIDGES[i];
  }
  return null;
}

/** 點是否在橋面內。radius 會把整個人的圓內縮，避免半身在海上。 */
export function onBridge(x, z, bridge, radius) {
  const dx = bridge.bx - bridge.ax;
  const dz = bridge.bz - bridge.az;
  const len = Math.hypot(dx, dz);
  if (!(len > 0)) return false;
  const t = ((x - bridge.ax) * dx + (z - bridge.az) * dz) / (len * len);
  const along = t * len;
  const inset = radius || 0;
  if (along < inset || along > len - inset) return false;
  const px = bridge.ax + dx * t;
  const pz = bridge.az + dz * t;
  return Math.hypot(x - px, z - pz) <= bridge.halfWidth - inset;
}

export const WALK_PADS = Object.freeze([
  Object.freeze({ id: 'dock', minX: -1.05, maxX: 0.85, minZ: 6.9, maxZ: 8.95 }),
  Object.freeze({ id: 'pier', minX: 6.2, maxX: 9.55, minZ: 1.15, maxZ: 2.75 })
]);

// 主島既有障礙，順序與改清單前的 BLOCK_RECTS（不含已搬走的賭場）一致。
export const OBSTACLE_RECTS = Object.freeze([
  Object.freeze({ id: 'shop', minX: -2.45, maxX: 2.45, minZ: -2.2, maxZ: 0.95 }),
  Object.freeze({ id: 'counter', minX: -1.35, maxX: 1.35, minZ: 1.0, maxZ: 1.9 }),
  Object.freeze({ id: 'machine', minX: -4.85, maxX: -3.2, minZ: -0.85, maxZ: 0.55 }),
  Object.freeze({ id: 'photos', minX: 3.05, maxX: 5.25, minZ: -0.35, maxZ: 0.7 }),
  Object.freeze({ id: 'seat-west', minX: -5.35, maxX: -4.35, minZ: 3.05, maxZ: 4.05 }),
  Object.freeze({ id: 'seat-east', minX: 4.05, maxX: 5.05, minZ: 3.0, maxZ: 4.0 }),
  Object.freeze({ id: 'stand', minX: 1.55, maxX: 2.75, minZ: 4.55, maxZ: 5.2 }),
  Object.freeze({ id: 'photo-wall', minX: -6.55, maxX: 6.55, minZ: -6.85, maxZ: -6.25 })
]);

export const OBSTACLE_CIRCLES = Object.freeze([
  Object.freeze({ id: 'tree-0', x: -6.25, z: -5.45, r: 0.85 }),
  Object.freeze({ id: 'tree-1', x: -4.5, z: -6.25, r: 0.75 }),
  Object.freeze({ id: 'tree-2', x: 5.7, z: -5.65, r: 0.85 }),
  Object.freeze({ id: 'tree-3', x: 6.45, z: -3.15, r: 0.7 }),
  Object.freeze({ id: 'tree-4', x: -6.55, z: -1.35, r: 0.8 }),
  Object.freeze({ id: 'tree-5', x: -6.35, z: 4.55, r: 0.7 }),
  Object.freeze({ id: 'tree-6', x: 6.25, z: 5.15, r: 0.65 }),
  Object.freeze({ id: 'tree-7', x: 1.7, z: -6.35, r: 0.55 }),
  Object.freeze({ id: 'tree-8', x: -1.7, z: -6.45, r: 0.55 }),
  Object.freeze({ id: 'tree-9', x: -5.7, z: 5.15, r: 0.45 }),
  Object.freeze({ id: 'bin-0', x: 2.55, z: 4.35, r: 0.32 }),
  Object.freeze({ id: 'bin-1', x: -2.7, z: -2.55, r: 0.32 }),
  Object.freeze({ id: 'crate', x: -1.5, z: -2.5, r: 0.4 })
]);

function themeCircle(id, islandId, dx, dz, r) {
  const island = islandById(islandId);
  return Object.freeze({ id: id, x: island.x + dx, z: island.z + dz, r: r });
}

export const THEME_CIRCLES = Object.freeze([
  themeCircle('palm-n', 'casino', 1.15, 3.2, 0.36),
  themeCircle('palm-s', 'casino', 1.0, -3.25, 0.34),
  themeCircle('palm-e', 'casino', 3.4, 0.3, 0.34),
  themeCircle('bamboo', 'tcm', 0.35, 2.55, 0.42),
  themeCircle('dish', 'computer', 2.45, -0.55, 0.4)
]);

export function footprintBlock(building) {
  const hx = building.w / 2;
  const hz = building.d / 2;
  const trim = building.frontTrim == null ? 0.2 : building.frontTrim;
  const yaw = building.yaw || 0;
  const corners = [
    [-hx, -hz],
    [hx, -hz],
    [hx, hz - trim],
    [-hx, hz - trim]
  ];
  let minX = Infinity;
  let maxX = -Infinity;
  let minZ = Infinity;
  let maxZ = -Infinity;
  for (let i = 0; i < corners.length; i++) {
    const p = rotateY(corners[i][0], corners[i][1], yaw);
    if (p.x < minX) minX = p.x;
    if (p.x > maxX) maxX = p.x;
    if (p.z < minZ) minZ = p.z;
    if (p.z > maxZ) maxZ = p.z;
  }
  return Object.freeze({
    minX: building.x + minX,
    maxX: building.x + maxX,
    minZ: building.z + minZ,
    maxZ: building.z + maxZ
  });
}

export function worldDoor(building) {
  const reach = building.d / 2 + (building.entry && building.entry.reach != null ? building.entry.reach : 0.75);
  const p = rotateY(0, reach, building.yaw || 0);
  return Object.freeze({ x: building.x + p.x, z: building.z + p.z });
}

const BUILDING_DEFS = [
  // 主島碰撞沿用 OBSTACLE_RECTS，這裡只記錄外觀位置，不再推一份框。
  { id: 'shop', island: 'main', x: 0, z: -0.55, w: 4.75, d: 3.2, yaw: 0, label: '莫卡手搖', panel: null, collide: false },
  { id: 'devices', island: 'main', x: 0, z: 1.42, w: 2.55, d: 0.76, yaw: 0, label: '點單', panel: 'devices', collide: false },
  { id: 'system', island: 'main', x: -4.05, z: -0.15, w: 1.45, d: 1.15, yaw: 0.42, label: '製茶機', panel: 'system', collide: false },
  { id: 'photos', island: 'main', x: 4.15, z: 0.2, w: 2.15, d: 0.7, yaw: 0.62, label: '飲品牆', panel: 'photos', collide: false },
  // 主島南側空地：全景鏡頭在 +Z，牆面朝南才看得到畫。離三座橋頭、候餐座位和既有步行路線都有一段距離。
  {
    id: 'graffiti-wall', island: 'main', x: -4.2, z: 4.55, w: 2.6, d: 0.34, yaw: 0,
    label: '塗鴉牆', panel: null, frontTrim: 0.12,
    entry: { r: 1.7, reach: 0.55, label: '在牆上塗鴉', action: 'openGraffiti' }
  },
  {
    id: 'casino', island: 'casino', x: 16.35, z: -7.4, w: 2.4, d: 1.9, yaw: -Math.PI / 2,
    label: '賭場', panel: null, frontTrim: 0.22,
    entry: { r: 1.2, reach: 0.78, label: '進入賭場', action: 'enterCasino' }
  },
  {
    id: 'tcm-site', island: 'tcm', x: -17.7, z: 1.15, w: 2.2, d: 1.65, yaw: Math.PI / 2,
    label: '施工中', panel: null, frontTrim: 0.18
  },
  {
    id: 'computer-site', island: 'computer', x: 0.45, z: 18.05, w: 2.2, d: 1.65, yaw: Math.PI,
    label: '施工中', panel: null, frontTrim: 0.18
  }
];

function decorateBuilding(raw) {
  const derive = raw.collide !== false && !raw.block;
  const block = raw.block ? Object.freeze(raw.block) : footprintBlock(raw);
  const door = raw.entry ? worldDoor(raw) : null;
  return Object.freeze({
    id: raw.id,
    island: raw.island,
    x: raw.x,
    z: raw.z,
    w: raw.w,
    d: raw.d,
    yaw: raw.yaw || 0,
    label: raw.label || '',
    panel: raw.panel || null,
    frontTrim: raw.frontTrim == null ? 0.2 : raw.frontTrim,
    entry: raw.entry ? Object.freeze(raw.entry) : null,
    deriveBlock: derive,
    block: block,
    door: door
  });
}

export const BUILDINGS = Object.freeze(BUILDING_DEFS.map(decorateBuilding));

export function buildingById(id) {
  for (let i = 0; i < BUILDINGS.length; i++) {
    if (BUILDINGS[i].id === id) return BUILDINGS[i];
  }
  return null;
}

const BLOCK_RECT_LIST = OBSTACLE_RECTS.slice();
for (let bi = 0; bi < BUILDINGS.length; bi++) {
  if (BUILDINGS[bi].deriveBlock) BLOCK_RECT_LIST.push(BUILDINGS[bi].block);
}

export function blockRects() {
  return BLOCK_RECT_LIST;
}

export function interactZones() {
  const zones = [];
  for (let i = 0; i < BUILDINGS.length; i++) {
    const b = BUILDINGS[i];
    if (!b.entry || !b.door) continue;
    zones.push(Object.freeze({
      id: b.id,
      x: b.door.x,
      z: b.door.z,
      r: b.entry.r,
      label: b.entry.label,
      action: b.entry.action
    }));
  }
  return Object.freeze(zones);
}

export function inIsland(x, z, island, radius) {
  const dx = x - island.x;
  const dz = z - island.z;
  const rr = island.r - (radius || 0);
  return rr > 0 && dx * dx + dz * dz <= rr * rr;
}

export function inPad(x, z, pad, radius) {
  const inset = radius || 0;
  return x >= pad.minX + inset && x <= pad.maxX - inset
    && z >= pad.minZ + inset && z <= pad.maxZ - inset;
}

/** 島、碼頭、橋的聯集。每塊各自內縮，角色圓不會伸出該塊陸地。 */
export function walkable(x, z, radius) {
  const inset = radius || 0;
  for (let i = 0; i < ISLANDS.length; i++) {
    if (inIsland(x, z, ISLANDS[i], inset)) return true;
  }
  for (let i = 0; i < WALK_PADS.length; i++) {
    if (inPad(x, z, WALK_PADS[i], inset)) return true;
  }
  for (let i = 0; i < BRIDGES.length; i++) {
    if (onBridge(x, z, BRIDGES[i], inset)) return true;
  }
  return false;
}

function circleHits(x, z, circle, radius) {
  const dx = x - circle.x;
  const dz = z - circle.z;
  const rr = circle.r + (radius || 0);
  return dx * dx + dz * dz < rr * rr;
}

function rectHits(x, z, rect, radius) {
  const dx = x - Math.max(rect.minX, Math.min(x, rect.maxX));
  const dz = z - Math.max(rect.minZ, Math.min(z, rect.maxZ));
  const inset = radius || 0;
  return dx * dx + dz * dz < inset * inset;
}

export function blocked(x, z, radius) {
  const rects = blockRects();
  for (let i = 0; i < rects.length; i++) {
    if (rectHits(x, z, rects[i], radius)) return true;
  }
  for (let i = 0; i < OBSTACLE_CIRCLES.length; i++) {
    if (circleHits(x, z, OBSTACLE_CIRCLES[i], radius)) return true;
  }
  for (let i = 0; i < THEME_CIRCLES.length; i++) {
    if (circleHits(x, z, THEME_CIRCLES[i], radius)) return true;
  }
  return false;
}
