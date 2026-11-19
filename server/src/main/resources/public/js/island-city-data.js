// 城市島純資料與紅綠燈邏輯（#144，無 Three.js／island-layout 依賴）。
export const CITY_ID = 'city';
export const CITY_CENTER = Object.freeze({ x: 0, z: -22 });

export const TRAFFIC_RED_SEC = 14;
export const TRAFFIC_YELLOW_SEC = 3;
export const TRAFFIC_GREEN_SEC = 12;

const TRAFFIC_CYCLE_SEC = TRAFFIC_RED_SEC + TRAFFIC_YELLOW_SEC + TRAFFIC_GREEN_SEC
  + TRAFFIC_GREEN_SEC + TRAFFIC_YELLOW_SEC;

export function trafficLightColor(elapsedSec) {
  const t = ((elapsedSec % TRAFFIC_CYCLE_SEC) + TRAFFIC_CYCLE_SEC) % TRAFFIC_CYCLE_SEC;
  if (t < TRAFFIC_RED_SEC) return 'red';
  if (t < TRAFFIC_RED_SEC + TRAFFIC_YELLOW_SEC) return 'yellow';
  if (t < TRAFFIC_RED_SEC + TRAFFIC_YELLOW_SEC + TRAFFIC_GREEN_SEC) return 'green';
  if (t < TRAFFIC_RED_SEC + TRAFFIC_YELLOW_SEC + TRAFFIC_GREEN_SEC + TRAFFIC_GREEN_SEC) return 'green';
  return 'yellow';
}

export function trafficLightMeshColors(elapsedSec) {
  const c = trafficLightColor(elapsedSec);
  return {
    red: c === 'red' ? 0xff3b30 : 0x3a2525,
    yellow: c === 'yellow' ? 0xffcc00 : 0x3a3520,
    green: c === 'green' ? 0x34c759 : 0x1e3324
  };
}

const BLOCK = 2.75;
const GRID = 4.85;

const CITY_LOCAL_RECTS = [
  { id: 'city-bld-arcade', minX: -GRID - BLOCK * 0.5, maxX: -GRID + BLOCK * 0.5, minZ: -BLOCK * 0.5, maxZ: BLOCK * 0.5 },
  { id: 'city-bld-tea', minX: GRID - BLOCK * 0.5, maxX: GRID + BLOCK * 0.5, minZ: -BLOCK * 0.5, maxZ: BLOCK * 0.5 },
  { id: 'city-bld-claw', minX: -BLOCK * 0.5, maxX: BLOCK * 0.5, minZ: -GRID - BLOCK * 0.5, maxZ: -GRID + BLOCK * 0.5 },
  { id: 'city-bld-plaza', minX: -1.15, maxX: 1.15, minZ: -1.05, maxZ: 1.05 },
  { id: 'city-bld-nw', minX: -GRID - BLOCK * 0.5, maxX: -GRID + BLOCK * 0.5, minZ: GRID - BLOCK * 0.5, maxZ: GRID + BLOCK * 0.5 },
  { id: 'city-bld-ne', minX: GRID - BLOCK * 0.5, maxX: GRID + BLOCK * 0.5, minZ: GRID - BLOCK * 0.5, maxZ: GRID + BLOCK * 0.5 },
  { id: 'city-bld-sw', minX: -GRID - BLOCK * 0.5, maxX: -GRID + BLOCK * 0.5, minZ: -GRID - BLOCK * 0.5, maxZ: -GRID + BLOCK * 0.5 },
  { id: 'city-bld-se', minX: GRID - BLOCK * 0.5, maxX: GRID + BLOCK * 0.5, minZ: -GRID - BLOCK * 0.5, maxZ: -GRID + BLOCK * 0.5 },
  { id: 'city-pier-rail-w', minX: -0.55, maxX: -0.34, minZ: 5.2, maxZ: 6.8 },
  { id: 'city-pier-rail-e', minX: 0.34, maxX: 0.55, minZ: 5.2, maxZ: 6.8 },
  { id: 'city-harbor-e', minX: 6.0, maxX: 8.45, minZ: -4.0, maxZ: 4.0 }
];

/** 騎樓柱（店前人行，不在路口車道中央）。 */
export const CITY_COLUMN_LOCAL = Object.freeze([
  { id: 'col-arc-1', x: -GRID - 0.85, z: 1.25, r: 0.18 },
  { id: 'col-arc-2', x: -GRID + 0.15, z: 1.25, r: 0.18 },
  { id: 'col-tea-1', x: GRID - 0.15, z: 1.25, r: 0.18 },
  { id: 'col-tea-2', x: GRID + 0.85, z: 1.25, r: 0.18 },
  { id: 'col-claw-1', x: -1.1, z: -GRID - 0.55, r: 0.18 },
  { id: 'col-claw-2', x: 1.1, z: -GRID - 0.55, r: 0.18 },
  { id: 'col-plaza-1', x: -1.0, z: 1.05, r: 0.16 },
  { id: 'col-plaza-2', x: 1.0, z: 1.05, r: 0.16 }
]);

export function cityObstacleRects() {
  const c = CITY_CENTER;
  return CITY_LOCAL_RECTS.map((r) => Object.freeze({
    id: r.id,
    minX: c.x + r.minX,
    maxX: c.x + r.maxX,
    minZ: c.z + r.minZ,
    maxZ: c.z + r.maxZ
  }));
}

export function cityObstacleCircles() {
  const c = CITY_CENTER;
  return CITY_COLUMN_LOCAL.map((item) => Object.freeze({
    id: item.id,
    x: c.x + item.x,
    z: c.z + item.z,
    r: item.r
  }));
}

export const PINK_CAR_LOCAL = Object.freeze({ x: 2.5, z: 0.35, yaw: 0.65 });
/** 路人／機車取樣點須與停車位保持的最小距離（含車身與人身寬度）。 */
export const PINK_CAR_PATH_CLEAR_R = 1.55;

export function pinkCarWorld() {
  const c = CITY_CENTER;
  return Object.freeze({
    x: c.x + PINK_CAR_LOCAL.x,
    z: c.z + PINK_CAR_LOCAL.z,
    yaw: PINK_CAR_LOCAL.yaw
  });
}

export function vehicleZones() {
  const car = pinkCarWorld();
  return Object.freeze([
    Object.freeze({
      id: 'city-pink-car',
      x: car.x,
      z: car.z,
      r: 1.25,
      label: '上車',
      action: 'enterVehicle'
    })
  ]);
}

export function cityWalkerPaths() {
  const c = CITY_CENTER;
  const w = (lx, lz) => [c.x + lx, c.z + lz];
  return Object.freeze([
    Object.freeze([w(-2.2, 3.8), w(1.05, 3.8), w(1.05, 2.45), w(-2.2, 2.45)]),
    Object.freeze([w(-3.15, 3.8), w(-3.15, 2.45)]),
    Object.freeze([w(-3.15, -3.1), w(-3.15, -1.25)]),
    Object.freeze([w(-2.2, -3.2), w(-1.45, -3.2)]),
    Object.freeze([w(1.45, -3.2), w(2.2, -3.2)]),
    Object.freeze([w(-1.2, 3.65), w(-1.2, 6.0)])
  ]);
}

export function cityMotoPaths() {
  const c = CITY_CENTER;
  const w = (lx, lz) => [c.x + lx, c.z + lz];
  return Object.freeze([
    Object.freeze([w(-2.85, 3.4), w(-2.85, 2.25), w(-2.1, 2.25), w(2.1, 2.25)]),
    Object.freeze([w(-2.1, 3.5), w(2.1, 3.5)]),
    Object.freeze([w(-2.15, -3.15), w(-1.42, -3.15)])
  ]);
}

export const CITY_PAVING_SPECS = Object.freeze([
  { x: 0, z: 0, w: 1.85, d: 11.5, rotY: 0 },
  { x: 0, z: 0, w: 11.5, d: 1.85, rotY: 0 },
  { x: -GRID, z: 0, w: 1.85, d: 11.5, rotY: 0 },
  { x: GRID, z: 0, w: 1.85, d: 11.5, rotY: 0 },
  { x: 0, z: -GRID, w: 11.5, d: 1.85, rotY: 0 },
  { x: 0, z: GRID, w: 11.5, d: 1.85, rotY: 0 }
]);

/** 供測試：沿路徑分段取樣世界座標。 */
export function sampleCityPaths(paths, stepsPerSegment) {
  const steps = stepsPerSegment == null ? 10 : stepsPerSegment;
  const out = [];
  for (let p = 0; p < paths.length; p++) {
    const path = paths[p];
    for (let i = 0; i < path.length - 1; i++) {
      const ax = path[i][0];
      const az = path[i][1];
      const bx = path[i + 1][0];
      const bz = path[i + 1][1];
      for (let s = 0; s <= steps; s++) {
        const t = s / steps;
        out.push([ax + (bx - ax) * t, az + (bz - az) * t]);
      }
    }
  }
  return out;
}