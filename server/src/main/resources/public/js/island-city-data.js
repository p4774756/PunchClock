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

const CITY_LOCAL_RECTS = [
  { id: 'city-bld-convenience', minX: -5.6, maxX: -2.0, minZ: -2.0, maxZ: 3.2 },
  { id: 'city-bld-tea', minX: 2.0, maxX: 5.6, minZ: -2.0, maxZ: 3.2 },
  { id: 'city-bld-claw', minX: -2.2, maxX: 2.2, minZ: -6.0, maxZ: -2.6 },
  { id: 'city-bld-nw', minX: -5.5, maxX: -3.0, minZ: 2.8, maxZ: 5.4 },
  { id: 'city-bld-ne', minX: 3.0, maxX: 5.5, minZ: 2.8, maxZ: 5.4 },
  { id: 'city-pier-rail-w', minX: -0.55, maxX: -0.34, minZ: 5.2, maxZ: 6.8 },
  { id: 'city-pier-rail-e', minX: 0.34, maxX: 0.55, minZ: 5.2, maxZ: 6.8 }
];

/** 騎樓柱（店前人行，不在路口車道中央）。 */
export const CITY_COLUMN_LOCAL = Object.freeze([
  { id: 'col-cv-1', x: -4.7, z: 3.05, r: 0.18 },
  { id: 'col-cv-2', x: -3.8, z: 3.05, r: 0.18 },
  { id: 'col-cv-3', x: -2.9, z: 3.05, r: 0.18 },
  { id: 'col-tea-1', x: 2.9, z: 3.05, r: 0.18 },
  { id: 'col-tea-2', x: 3.8, z: 3.05, r: 0.18 },
  { id: 'col-tea-3', x: 4.7, z: 3.05, r: 0.18 },
  { id: 'col-claw-1', x: -1.1, z: -4.65, r: 0.18 },
  { id: 'col-claw-2', x: 1.1, z: -4.65, r: 0.18 }
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

export const PINK_CAR_LOCAL = Object.freeze({ x: 0.9, z: -1.35, yaw: 0 });

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
    Object.freeze([w(-1.8, 3.0), w(1.8, 3.0), w(1.8, 1.55), w(-1.8, 1.55)]),
    Object.freeze([w(-0.95, 4.2), w(-0.95, -1.15)]),
    Object.freeze([w(0.95, 4.2), w(0.95, -1.15)]),
    Object.freeze([w(-0.95, -1.15), w(0.95, -1.15)]),
    Object.freeze([w(-1.8, -6.2), w(1.8, -6.2)]),
    Object.freeze([w(0, 4.6), w(0, 5.7)])
  ]);
}

export function cityMotoPaths() {
  const c = CITY_CENTER;
  const w = (lx, lz) => [c.x + lx, c.z + lz];
  return Object.freeze([
    Object.freeze([w(-0.95, 3.5), w(-0.95, -1.0), w(0.95, -1.0), w(0.95, 3.5)]),
    Object.freeze([w(-0.95, 1.55), w(0.95, 1.55)]),
    Object.freeze([w(-1.8, 3.0), w(1.8, 3.0)])
  ]);
}

export const CITY_PAVING_SPECS = Object.freeze([
  { x: 0, z: 0.5, w: 2.2, d: 9.5, rotY: 0 },
  { x: 0, z: 0.5, w: 9.5, d: 2.2, rotY: 0 },
  { x: -3.8, z: 0.5, w: 3.6, d: 4.8, rotY: 0 },
  { x: 3.8, z: 0.5, w: 3.6, d: 4.8, rotY: 0 },
  { x: 0, z: -4.3, w: 3.8, d: 2.8, rotY: 0 }
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