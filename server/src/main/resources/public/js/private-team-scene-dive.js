/** 秘書跳水：純時間軸（可 node:test）。座標為世界座標，feetY 為腳底高度。 */

export const DIVE_GEOM_DEFAULTS = {
  edgeX: 0.98,
  edgeZ: 0.64,
  landX: 0.3,
  landZ: 0.42,
  floorY: 0.05,
  waterY: 0.028,
  /** 完全沒入水中時的腳底高度（頭頂也在水面下）。 */
  deepFeetY: -1.25,
  /** 浮出水面時的腳底高度（頭肩露出水面）。 */
  floatFeetY: -0.82,
  spriteH: 1.19
};

export const DIVE_MS = {
  walk: 1200,
  prep: 380,
  jump: 950,
  sink: 1300,
  float: 2600,
  rise: 1100,
  hop: 850,
  walkBack: 1200,
  reducedFloat: 2400
};

export function diveTimeline(fromLounge, reduced) {
  if (reduced) return [{ name: 'float', ms: DIVE_MS.reducedFloat }];
  const list = [];
  if (fromLounge) list.push({ name: 'walk', ms: DIVE_MS.walk });
  list.push({ name: 'prep', ms: DIVE_MS.prep });
  list.push({ name: 'jump', ms: DIVE_MS.jump });
  list.push({ name: 'sink', ms: DIVE_MS.sink });
  list.push({ name: 'float', ms: DIVE_MS.float });
  list.push({ name: 'rise', ms: DIVE_MS.rise });
  list.push({ name: 'hop', ms: DIVE_MS.hop });
  if (fromLounge) list.push({ name: 'walkBack', ms: DIVE_MS.walkBack });
  return list;
}

export function diveTotalMs(fromLounge, reduced) {
  return diveTimeline(fromLounge, reduced).reduce((sum, seg) => sum + seg.ms, 0);
}

/** 點「上岸」時直接跳到浮起階段。回傳該階段的起點毫秒；已在之後則回傳 null。 */
export function diveRiseStartMs(fromLounge, reduced) {
  const tl = diveTimeline(fromLounge, reduced);
  let t = 0;
  for (let i = 0; i < tl.length; i++) {
    if (tl[i].name === 'rise') return t;
    t += tl[i].ms;
  }
  return null;
}

export function diveSkipElapsedMs(elapsedMs, fromLounge, reduced) {
  const rise = diveRiseStartMs(fromLounge, reduced);
  if (rise == null) {
    const total = diveTotalMs(fromLounge, reduced);
    return Math.max(Number(elapsedMs) || 0, total - 600);
  }
  return Math.max(Number(elapsedMs) || 0, rise);
}

function clamp01(v) {
  return v < 0 ? 0 : v > 1 ? 1 : v;
}
function lerp(a, b, u) {
  return a + (b - a) * u;
}
function easeOutCubic(u) {
  return 1 - Math.pow(1 - u, 3);
}
function easeInOut(u) {
  return u < 0.5 ? 2 * u * u : 1 - Math.pow(-2 * u + 2, 2) / 2;
}

/**
 * @param {number} elapsedMs
 * @param {object} g geom：startX/startZ/fromLounge/reduced 與 DIVE_GEOM_DEFAULTS
 * @returns {{phase:string,x:number,z:number,feetY:number,rotZ:number,sprite:string,submerge:number,done:boolean}}
 */
export function secretaryDiveStateAt(elapsedMs, g) {
  const geom = Object.assign({}, DIVE_GEOM_DEFAULTS, g || {});
  const fromLounge = !!geom.fromLounge;
  const startX = Number.isFinite(geom.startX) ? geom.startX : geom.edgeX;
  const startZ = Number.isFinite(geom.startZ) ? geom.startZ : geom.edgeZ;
  const tl = diveTimeline(fromLounge, !!geom.reduced);
  let t = Number(elapsedMs);
  if (!Number.isFinite(t) || t < 0) t = 0;
  let name = 'done';
  let u = 1;
  for (let i = 0; i < tl.length; i++) {
    if (t < tl[i].ms) {
      name = tl[i].name;
      u = tl[i].ms > 0 ? t / tl[i].ms : 1;
      break;
    }
    t -= tl[i].ms;
  }

  const water = geom.waterY;
  const floor = geom.floorY;
  const deep = geom.deepFeetY;
  const afloat = geom.floatFeetY;
  let x = geom.edgeX;
  let z = geom.edgeZ;
  let feetY = floor;
  let rotZ = 0;
  let sprite = 'idle';

  if (name === 'walk') {
    const e = easeInOut(u);
    x = lerp(startX, geom.edgeX, e);
    z = lerp(startZ, geom.edgeZ, e);
    feetY = floor + Math.abs(Math.sin(u * Math.PI * 3)) * 0.05;
  } else if (name === 'prep') {
    sprite = 'wave';
    rotZ = -0.12 * Math.sin(u * Math.PI);
  } else if (name === 'jump') {
    sprite = 'yeah';
    x = lerp(geom.edgeX, geom.landX, u);
    z = lerp(geom.edgeZ, geom.landZ, u);
    feetY = lerp(floor, water, u) + 0.62 * 4 * u * (1 - u);
    rotZ = -0.5 * Math.sin(u * Math.PI);
  } else if (name === 'sink') {
    const e = easeOutCubic(u);
    x = lerp(geom.landX, geom.landX - 0.06, e);
    z = geom.landZ;
    feetY = lerp(water, deep, e);
    rotZ = 0.22 * Math.sin(u * Math.PI);
  } else if (name === 'float') {
    x = geom.landX - 0.06 + 0.04 * Math.sin(u * Math.PI * 2);
    z = geom.landZ;
    feetY = (geom.reduced ? afloat : deep) + 0.05 * Math.sin(u * Math.PI * 3);
    rotZ = 0.1 * Math.sin(u * Math.PI * 2);
  } else if (name === 'rise') {
    sprite = 'wave';
    const e = easeInOut(u);
    x = lerp(geom.landX - 0.06, geom.landX, e);
    z = geom.landZ;
    feetY = lerp(deep, afloat, e);
  } else if (name === 'hop') {
    sprite = 'yeah';
    x = lerp(geom.landX, geom.edgeX, u);
    z = lerp(geom.landZ, geom.edgeZ, u);
    feetY = lerp(afloat, floor, easeOutCubic(u)) + 0.7 * 4 * u * (1 - u);
    rotZ = 0.3 * Math.sin(u * Math.PI);
  } else if (name === 'walkBack') {
    const e = easeInOut(u);
    x = lerp(geom.edgeX, startX, e);
    z = lerp(geom.edgeZ, startZ, e);
    feetY = floor + Math.abs(Math.sin(u * Math.PI * 3)) * 0.05;
  } else {
    x = fromLounge ? startX : geom.edgeX;
    z = fromLounge ? startZ : geom.edgeZ;
  }
  if (geom.reduced && name === 'float') {
    x = geom.landX;
    z = geom.landZ;
    rotZ = 0;
  }

  const submerge = clamp01((water - feetY) / geom.spriteH);
  return { phase: name, x, z, feetY, rotZ, sprite, submerge, done: name === 'done' };
}

/** 水下時是否該冒氣泡（頭低於水面）。 */
export function diveIsUnderwater(state, g) {
  const spriteH = g && Number.isFinite(g.spriteH) ? g.spriteH : DIVE_GEOM_DEFAULTS.spriteH;
  const water = g && Number.isFinite(g.waterY) ? g.waterY : DIVE_GEOM_DEFAULTS.waterY;
  return !!state && state.feetY + spriteH < water;
}
