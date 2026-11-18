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

export const CITY_COLUMN_LOCAL = Object.freeze([
  { id: 'col-n1', x: -1.15, z: 0.5, r: 0.22 },
  { id: 'col-n2', x: 1.15, z: 0.5, r: 0.22 },
  { id: 'col-s1', x: -1.15, z: -0.5, r: 0.22 },
  { id: 'col-s2', x: 1.15, z: -0.5, r: 0.22 },
  { id: 'col-w1', x: -0.5, z: 1.15, r: 0.22 },
  { id: 'col-w2', x: -0.5, z: -1.15, r: 0.22 },
  { id: 'col-e1', x: 0.5, z: 1.15, r: 0.22 },
  { id: 'col-e2', x: 0.5, z: -1.15, r: 0.22 }
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

export const PINK_CAR_LOCAL = Object.freeze({ x: 2.35, z: 3.25, yaw: -0.35 });

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
    Object.freeze([w(-1.0, 4.2), w(-1.0, -0.2), w(1.0, -0.2), w(1.0, 4.2)]),
    Object.freeze([w(-3.5, 1.0), w(3.5, 1.0), w(3.5, -3.0), w(-3.5, -3.0)]),
    Object.freeze([w(0.5, 4.0), w(0.5, -4.5)]),
    Object.freeze([w(-3.0, 0), w(3.0, 0)]),
    Object.freeze([w(-2.5, 3.5), w(2.5, 3.5), w(2.5, -2.5), w(-2.5, -2.5)])
  ]);
}

export function cityMotoPaths() {
  const c = CITY_CENTER;
  const w = (lx, lz) => [c.x + lx, c.z + lz];
  return Object.freeze([
    Object.freeze([w(0.8, 4.0), w(0.8, -1.0), w(-0.8, -1.0), w(-0.8, 4.0)]),
    Object.freeze([w(-3.0, -2.5), w(3.0, -2.5)])
  ]);
}

export const CITY_PAVING_SPECS = Object.freeze([
  { x: 0, z: 0.5, w: 2.2, d: 9.5, rotY: 0 },
  { x: 0, z: 0.5, w: 9.5, d: 2.2, rotY: 0 },
  { x: -3.8, z: 0.5, w: 3.6, d: 4.8, rotY: 0 },
  { x: 3.8, z: 0.5, w: 3.6, d: 4.8, rotY: 0 },
  { x: 0, z: -4.3, w: 3.8, d: 2.8, rotY: 0 }
]);