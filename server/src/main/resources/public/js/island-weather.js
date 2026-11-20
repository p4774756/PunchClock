// 群島天氣：API、視覺參數、Three.js 雨絲／雲層／濕地。純函式供 island-weather.test.mjs。
import { calendarViewForMs, resolveFestivalId, taipeiHour, FESTIVAL_QUERY_VALUES } from './island-world-data.js';
import { ISLANDS } from './island-layout.js';
import { G, P, HEX } from './island-kit.js';

export const WEATHER_KINDS = Object.freeze(['clear', 'cloudy', 'rain', 'storm', 'fog']);
export const WEATHER_FETCH_MS = 20 * 60 * 1000;
export const WEATHER_BLEND_SEC = 4.0;

export const RAIN_MAX_DESKTOP = 4000;
export const RAIN_MAX_MOBILE = 1200;

/** 不論鏡頭多遠，玩家與腳下這段距離一定在霧的清楚區內。 */
export const FOG_CLEAR_RADIUS = 12;

export const WAVE_MUL = Object.freeze({
  clear: 1,
  cloudy: 1,
  rain: 1.5,
  storm: 3.4,
  fog: 0.9
});

export const SWAY_MUL = Object.freeze({
  clear: 1,
  cloudy: 1.15,
  rain: 1.5,
  storm: 3.2,
  fog: 0.9
});

const SUN_MUL = Object.freeze({
  clear: 1,
  cloudy: 0.7,
  rain: 0.55,
  storm: 0.45,
  fog: 0.65
});

const FOG_PRESET = Object.freeze({
  clear: { near: 1, far: 1, wash: 0, span: 1 },
  cloudy: { near: 0.92, far: 0.88, wash: 0.12, span: 0.95 },
  rain: { near: 0.72, far: 0.62, wash: 0.22, span: 0.8 },
  storm: { near: 0.65, far: 0.52, wash: 0.28, span: 0.68 },
  // 霧的重點是「近清楚、遠很快糊掉」，所以淡出區間要短。
  fog: { near: 0.38, far: 0.42, wash: 0.45, span: 0.4 }
});

function normKind(kind) {
  const k = String(kind || 'clear').toLowerCase();
  return WEATHER_KINDS.includes(k) ? k : 'clear';
}

export function isMobileViewport(width) {
  return (Number(width) || 0) > 0 && width < 520;
}

export function rainStreakMaxForTier(tier, mobile) {
  const low = tier === 'shadowOff' || tier === 'shadowLow' || tier === 'dpr1';
  if (mobile || low) return RAIN_MAX_MOBILE;
  return RAIN_MAX_DESKTOP;
}

export function weatherVisualTargets(kind) {
  const k = normKind(kind);
  const fog = FOG_PRESET[k];
  return {
    kind: k,
    sunMul: SUN_MUL[k],
    fogNearMul: fog.near,
    fogFarMul: fog.far,
    fogWash: fog.wash,
    fogSpanMul: fog.span,
    waveMul: WAVE_MUL[k],
    swayMul: SWAY_MUL[k],
    rainActive: k === 'rain' || k === 'storm',
    rainStrength: k === 'rain' || k === 'storm' ? 1 : 0,
    cloudOpacity: k === 'clear' ? 0 : (k === 'cloudy' ? 0.7 : k === 'storm' ? 0.78 : k === 'fog' ? 0.32 : 0.58),
    wet: k === 'storm' ? 1 : (k === 'rain' ? 0.75 : 0),
    lightning: k === 'storm'
  };
}

export function blendWeatherVisual(a, b, t) {
  const u = Math.max(0, Math.min(1, Number(t) || 0));
  const from = weatherVisualTargets(a);
  const to = weatherVisualTargets(b);
  const lerp = (x, y) => x + (y - x) * u;
  return {
    kind: u < 0.5 ? from.kind : to.kind,
    sunMul: lerp(from.sunMul, to.sunMul),
    fogNearMul: lerp(from.fogNearMul, to.fogNearMul),
    fogFarMul: lerp(from.fogFarMul, to.fogFarMul),
    fogWash: lerp(from.fogWash, to.fogWash),
    fogSpanMul: lerp(from.fogSpanMul, to.fogSpanMul),
    waveMul: lerp(from.waveMul, to.waveMul),
    swayMul: lerp(from.swayMul, to.swayMul),
    rainActive: from.rainActive || to.rainActive,
    rainStrength: lerp(from.rainStrength, to.rainStrength),
    cloudOpacity: lerp(from.cloudOpacity, to.cloudOpacity),
    wet: lerp(from.wet, to.wet),
    lightning: u < 0.5 ? from.lightning : to.lightning
  };
}

/**
 * 霧距離要跟著鏡頭到注視點的距離走：全景鏡頭離場景 100 公尺以上時，
 * 固定的 near/far 會把主島與玩家一起吃掉（#236 審核 e、j 項）。
 */
export function fogDistancesForCamera(baseNear, baseFar, nearMul, farMul, camDist, spanMul) {
  const rawNear = Math.max(10, baseNear * nearMul);
  const rawFar = Math.max(rawNear + 40, baseFar * farMul);
  const mul = Number.isFinite(Number(spanMul)) && Number(spanMul) > 0 ? Number(spanMul) : 1;
  const span = Math.max(30, (rawFar - rawNear) * mul);
  const dist = Number.isFinite(Number(camDist)) ? Math.max(0, Number(camDist)) : 0;
  const clear = Math.max(FOG_CLEAR_RADIUS, dist * 0.12);
  const near = Math.max(rawNear, dist + clear);
  return { near: near, far: near + span };
}

export function parseWorldWeatherQuery(search) {
  const qs = search instanceof URLSearchParams
    ? search
    : new URLSearchParams(String(search || ''));
  const weather = qs.get('weather');
  const temp = qs.get('temp');
  const festival = qs.get('festival');
  const news = qs.get('news');
  const festivalId = festival == null ? '' : String(festival).trim();
  return {
    weather: weather ? normKind(weather) : null,
    tempC: temp != null && temp !== '' && Number.isFinite(Number(temp)) ? Number(temp) : null,
    festival: FESTIVAL_QUERY_VALUES.includes(festivalId) ? festivalId : null,
    newsFlag: news === '0' ? false : news === '1' ? true : null
  };
}

export function mergeWorldPayload(apiBody, query, nowMs) {
  const q = query || {};
  const body = apiBody && typeof apiBody === 'object' ? apiBody : {};
  const w = body.weather && typeof body.weather === 'object' ? body.weather : {};
  const calendar = calendarViewForMs(nowMs);
  const queryFestival = FESTIVAL_QUERY_VALUES.includes(q.festival) ? q.festival : null;
  const festival = queryFestival || resolveFestivalId(nowMs, null);
  const news = Array.isArray(body.news) ? body.news.slice(0, 5) : [];
  const filteredNews = q.newsFlag === false ? [] : news;
  return {
    weather: {
      kind: q.weather || normKind(w.kind),
      tempC: q.tempC != null ? q.tempC : (Number(w.tempC) || 24),
      humidity: Number(w.humidity) || 60,
      windKmh: Number(w.windKmh) || 10,
      gustKmh: Number(w.gustKmh) || 12,
      isDay: w.isDay !== false,
      source: w.source || 'fallback',
      stale: !!w.stale
    },
    calendar: Object.assign({}, calendar, { festival: queryFestival || calendar.festival }),
    festival: festival,
    news: filteredNews,
    hour: taipeiHour(nowMs)
  };
}

export function defaultClearWorld(nowMs) {
  return mergeWorldPayload({ success: true, weather: { kind: 'clear', tempC: 24, isDay: true, source: 'fallback' }, news: [] }, {}, nowMs);
}

export function formatWeatherPerfLine(state) {
  if (!state) return '';
  return '天氣：' + (state.displayKind || 'clear') + ' · 雨絲 ' + (state.rainCount || 0);
}

/**
 * 單一 in-flight 的 /api/world/now 取用；TTL 到期才重抓，頁面隱藏時完全不抓。
 * 純邏輯，測試可注入 fetchNow／isHidden／now。
 */
export function createWorldRefresher(opts) {
  const o = opts || {};
  const fetchNow = typeof o.fetchNow === 'function' ? o.fetchNow : () => Promise.resolve({});
  const isHidden = typeof o.isHidden === 'function' ? o.isHidden : () => false;
  const now = typeof o.now === 'function' ? o.now : () => Date.now();
  const onBody = typeof o.onBody === 'function' ? o.onBody : () => null;
  const ttlMs = Number(o.ttlMs) > 0 ? Number(o.ttlMs) : WEATHER_FETCH_MS;

  let inFlight = null;
  let lastFetchAt = 0;
  let fetchCount = 0;

  function refresh() {
    if (inFlight) return inFlight;
    fetchCount += 1;
    const at = now();
    const settle = (body) => {
      lastFetchAt = at;
      inFlight = null;
      return onBody(body, at);
    };
    let started;
    try {
      started = Promise.resolve(fetchNow());
    } catch (err) {
      started = Promise.reject(err);
    }
    inFlight = started.then((body) => settle(body), () => settle(null));
    return inFlight;
  }

  function tick() {
    if (inFlight) return inFlight;
    if (isHidden()) return null;
    if (lastFetchAt > 0 && now() - lastFetchAt < ttlMs) return null;
    return refresh();
  }

  return {
    refresh: refresh,
    tick: tick,
    isRefreshing: () => inFlight != null,
    isStale: () => lastFetchAt === 0 || now() - lastFetchAt >= ttlMs,
    fetchCount: () => fetchCount,
    lastFetchAt: () => lastFetchAt
  };
}

/**
 * 可無縫平鋪的柔邊雲層貼圖：多尺度徑向團塊，邊界全是漸層，
 * 留下真正的空隙讓島嶼透出來，不是一整片白。
 */
function makeTilingCloudTexture(THREE, seedBase, density) {
  const size = 512;
  const canvas = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  if (!canvas) return null;
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, size, size);
  let seed = seedBase;
  const rnd = () => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed / 0x7fffffff;
  };
  function blob(x, y, r, alpha) {
    // 四個方向各補一次，貼圖左右上下才接得起來。
    for (let dx = -1; dx <= 1; dx++) {
      for (let dy = -1; dy <= 1; dy++) {
        const cx = x + dx * size;
        const cy = y + dy * size;
        if (cx < -r || cx > size + r || cy < -r || cy > size + r) continue;
        const g = ctx.createRadialGradient(cx, cy, 0, cx, cy, r);
        g.addColorStop(0, 'rgba(255,255,255,' + alpha.toFixed(3) + ')');
        g.addColorStop(0.45, 'rgba(255,255,255,' + (alpha * 0.45).toFixed(3) + ')');
        g.addColorStop(1, 'rgba(255,255,255,0)');
        ctx.fillStyle = g;
        ctx.beginPath();
        ctx.arc(cx, cy, r, 0, Math.PI * 2);
        ctx.fill();
      }
    }
  }
  // 大團雲底 + 中團 + 小團，疊出有層次的雲塊。
  const banks = Math.round(5 * density);
  for (let i = 0; i < banks; i++) {
    const bx = rnd() * size;
    const by = rnd() * size;
    blob(bx, by, 86 + rnd() * 64, 0.42);
    for (let j = 0; j < 7; j++) {
      blob(bx + (rnd() - 0.5) * 190, by + (rnd() - 0.5) * 150, 34 + rnd() * 56, 0.42);
    }
    for (let j = 0; j < 9; j++) {
      blob(bx + (rnd() - 0.5) * 230, by + (rnd() - 0.5) * 190, 14 + rnd() * 26, 0.3);
    }
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  return tex;
}

/** 雨絲貼圖：兩端收細的垂直亮帶，遠看才像雨不像棍子。 */
function makeRainStreakTexture(THREE) {
  const canvas = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  if (!canvas) return null;
  canvas.width = 8;
  canvas.height = 64;
  const ctx = canvas.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, 0, 64);
  g.addColorStop(0, 'rgba(255,255,255,0)');
  g.addColorStop(0.3, 'rgba(255,255,255,0.75)');
  g.addColorStop(0.75, 'rgba(255,255,255,1)');
  g.addColorStop(1, 'rgba(255,255,255,0)');
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, 8, 64);
  // 橫向也收一點，邊緣不要是硬的方條。
  ctx.globalCompositeOperation = 'destination-in';
  const side = ctx.createLinearGradient(0, 0, 8, 0);
  side.addColorStop(0, 'rgba(0,0,0,0)');
  side.addColorStop(0.5, 'rgba(0,0,0,1)');
  side.addColorStop(1, 'rgba(0,0,0,0)');
  ctx.fillStyle = side;
  ctx.fillRect(0, 0, 8, 64);
  ctx.globalCompositeOperation = 'source-over';
  return new THREE.CanvasTexture(canvas);
}

/** 濕地倒影用的天空貼圖（天空漸層＋雲條）；直接當平面 map 用，不走 PBR 環境光。 */
function makeSkyEnvTexture(THREE) {
  const w = 256;
  const h = 256;
  const canvas = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  if (!canvas) return null;
  canvas.width = w;
  canvas.height = h;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#b9d6ef';
  ctx.fillRect(0, 0, w, h);
  // 倒影裡的雲條：亮暗交錯，水窪才不會是一塊死板的藍。
  let seed = 4472761;
  const rnd = () => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed / 0x7fffffff;
  };
  for (let i = 0; i < 22; i++) {
    const cx = rnd() * w;
    const cy = rnd() * h;
    const rx = 22 + rnd() * 52;
    const ry = rx * (0.18 + rnd() * 0.3);
    const bright = rnd() < 0.6;
    const band = ctx.createRadialGradient(cx, cy, 0, cx, cy, rx);
    band.addColorStop(0, bright ? 'rgba(255,255,255,0.95)' : 'rgba(104,128,156,0.8)');
    band.addColorStop(1, bright ? 'rgba(255,255,255,0)' : 'rgba(104,128,156,0)');
    ctx.fillStyle = band;
    ctx.save();
    ctx.translate(cx, cy);
    ctx.scale(1, ry / rx);
    ctx.beginPath();
    ctx.arc(0, 0, rx, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = THREE.RepeatWrapping;
  tex.wrapT = THREE.RepeatWrapping;
  if (THREE.SRGBColorSpace) tex.colorSpace = THREE.SRGBColorSpace;
  return tex;
}

/** 水窪遮罩：決定濕地反光出現在哪些斑塊。 */
function makePuddleMaskTexture(THREE) {
  const size = 256;
  const canvas = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  if (!canvas) return null;
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  ctx.fillStyle = '#0f0f0f';
  ctx.fillRect(0, 0, size, size);
  let seed = 773311;
  const rnd = () => {
    seed = (seed * 1103515245 + 12345) & 0x7fffffff;
    return seed / 0x7fffffff;
  };
  for (let i = 0; i < 26; i++) {
    const x = rnd() * size;
    const y = rnd() * size;
    const r = 16 + rnd() * 40;
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, 'rgba(255,255,255,1)');
    g.addColorStop(0.55, 'rgba(255,255,255,0.95)');
    g.addColorStop(0.8, 'rgba(255,255,255,0.5)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.save();
    ctx.translate(x, y);
    ctx.scale(1, 0.62 + rnd() * 0.5);
    ctx.beginPath();
    ctx.arc(0, 0, r, 0, Math.PI * 2);
    ctx.fill();
    ctx.restore();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  return tex;
}

const RAIN_NEAR_SPAN = 18;
const RAIN_FAR_SPAN = 86;
const RAIN_TOP = 14;

function createRainSystem(THREE, maxCount) {
  const count = Math.max(0, maxCount | 0);
  const geo = new THREE.InstancedBufferGeometry();
  const base = new THREE.PlaneGeometry(0.075, 1);
  geo.copy(base);
  base.dispose();
  const tex = makeRainStreakTexture(THREE);
  const mesh = new THREE.InstancedMesh(
    geo,
    new THREE.MeshBasicMaterial({
      map: tex,
      color: 0xd6e4f7,
      transparent: true,
      opacity: 0.5,
      depthWrite: false,
      fog: true,
      side: THREE.DoubleSide
    }),
    count
  );
  mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
  mesh.frustumCulled = false;
  mesh.visible = false;
  mesh.name = 'weatherRain';
  mesh.renderOrder = 6;
  const ox = new Float32Array(count);
  const oy = new Float32Array(count);
  const oz = new Float32Array(count);
  const len = new Float32Array(count);
  const spd = new Float32Array(count);
  return {
    mesh: mesh,
    tex: tex,
    ox: ox,
    oy: oy,
    oz: oz,
    len: len,
    spd: spd,
    active: 0,
    seeded: 0,
    rot: new THREE.Matrix4(),
    euler: new THREE.Euler(0, 0, 0, 'YXZ')
  };
}

function seedRainDrop(rain, i, resetY) {
  // 近景帶（偶數）讓鏡頭前就看得到雨絲，遠景帶撐滿視野。
  const near = (i & 1) === 0;
  const span = near ? RAIN_NEAR_SPAN : RAIN_FAR_SPAN;
  rain.ox[i] = (Math.random() - 0.5) * span;
  rain.oz[i] = (Math.random() - 0.5) * span;
  rain.len[i] = near ? 0.55 + Math.random() * 0.5 : 1.4 + Math.random() * 1.4;
  rain.spd[i] = 0.85 + Math.random() * 0.45;
  rain.oy[i] = resetY
    ? RAIN_TOP + Math.random() * 8
    : G + Math.random() * RAIN_TOP;
}

export function createIslandWeather(opts) {
  const o = opts || {};
  const THREE = o.THREE;
  const reduced = !!o.reduced;
  const scene = o.scene;
  const sun = o.sun;
  const loop = o.loop;
  const getNightMix = typeof o.getNightMix === 'function' ? o.getNightMix : () => 0;
  const getIsIndoor = typeof o.getIsIndoor === 'function' ? o.getIsIndoor : () => false;
  const getQualityTier = typeof o.getQualityTier === 'function' ? o.getQualityTier : () => 'high';
  const getViewportWidth = typeof o.getViewportWidth === 'function' ? o.getViewportWidth : () => 1280;
  const getCamera = typeof o.getCamera === 'function' ? o.getCamera : () => null;
  const getFocus = typeof o.getFocus === 'function' ? o.getFocus : () => null;
  const onWaveMul = typeof o.onWaveMul === 'function' ? o.onWaveMul : null;
  const onSwayMul = typeof o.onSwayMul === 'function' ? o.onSwayMul : null;
  const onFogBase = typeof o.onFogBase === 'function' ? o.onFogBase : null;
  const setGroundWet = typeof o.setGroundWet === 'function' ? o.setGroundWet : null;
  const fetchNow = typeof o.fetchNow === 'function'
    ? o.fetchNow
    : (typeof fetch !== 'undefined'
      ? () => fetch('/api/world/now').then((r) => r.json())
      : () => Promise.resolve({}));

  const qs = typeof window !== 'undefined' ? new URLSearchParams(window.location.search) : new URLSearchParams();
  const queryOverride = parseWorldWeatherQuery(qs);

  let worldState = defaultClearWorld(Date.now());
  let target = weatherVisualTargets('clear');
  let visual = weatherVisualTargets('clear');
  let blendT = 1;
  let fromKind = 'clear';
  let toKind = 'clear';
  let rainMax = rainStreakMaxForTier(getQualityTier(), isMobileViewport(getViewportWidth()));
  let displayKind = 'clear';
  let rainCount = 0;
  let lightningT = 0;
  const focus = { x: 0, z: 0 };

  // ---- 雲層：繞著注視點的柔邊 billboard，分層、不同深度與速度 ----
  const cloudGroup = new THREE.Group();
  cloudGroup.name = 'weatherClouds';
  const cloudPlanes = [];
  // 鏡頭主要是俯視群島，所以雲做成三層飄在海面上方的柔邊雲層（各一次 draw call），
  // 從上面看是空拍雲帶，從島上抬頭看是陰天雲頂；貼圖留空隙讓島嶼透出來。
  const CLOUD_SHEET_SIZE = 620;
  const CLOUD_SHEETS = [
    { y: 12.5, repeat: 13, speed: 0.0042, alpha: 0.45, seed: 20260236, density: 1.0 },
    { y: 19, repeat: 9, speed: 0.0026, alpha: 0.62, seed: 7781133, density: 0.9 },
    { y: 28, repeat: 6, speed: 0.0015, alpha: 0.55, seed: 991277, density: 0.8 }
  ];
  const cloudDay = new THREE.Color(0xf6f9fc);
  const cloudNight = new THREE.Color(0x4a5f8e);
  const cloudTextures = [];
  CLOUD_SHEETS.forEach((sheet, i) => {
    const tex = makeTilingCloudTexture(THREE, sheet.seed, sheet.density);
    if (!tex) return;
    tex.repeat.set(sheet.repeat, sheet.repeat);
    cloudTextures.push(tex);
    const mat = new THREE.MeshBasicMaterial({
      map: tex,
      color: cloudDay.clone(),
      transparent: true,
      opacity: 0,
      depthWrite: false,
      // 空拍視角下雲層離鏡頭比島還遠，吃霧的話整片雲會被霧色抹平只剩灰濛濛。
      fog: false,
      side: THREE.DoubleSide
    });
    const geom = new THREE.PlaneGeometry(CLOUD_SHEET_SIZE, CLOUD_SHEET_SIZE);
    geom.rotateX(-Math.PI / 2);
    const plane = new THREE.Mesh(geom, mat);
    plane.frustumCulled = false;
    plane.renderOrder = 3 + i * 0.1;
    plane.userData.sheet = sheet;
    plane.userData.tex = tex;
    cloudGroup.add(plane);
    cloudPlanes.push(plane);
  });
  if (cloudPlanes.length) scene.add(cloudGroup);

  // ---- 濕地：有真實環境反射的薄層＋水窪遮罩 ----
  const wetGroup = new THREE.Group();
  wetGroup.name = 'weatherWet';
  wetGroup.visible = false;
  const wetDay = new THREE.Color(0xffffff);
  const wetNight = new THREE.Color(0x6f80a8);
  const envTex = makeSkyEnvTexture(THREE);
  const puddleTex = makePuddleMaskTexture(THREE);
  let wetMat = null;
  if (envTex && puddleTex) {
    envTex.repeat.set(1.1, 1.1);
    puddleTex.repeat.set(1.6, 1.6);
    wetMat = new THREE.MeshBasicMaterial({
      map: envTex,
      alphaMap: puddleTex,
      color: 0xffffff,
      transparent: true,
      opacity: 0,
      depthWrite: false,
      fog: true,
      polygonOffset: true,
      polygonOffsetFactor: -2,
      polygonOffsetUnits: -2
    });
    ISLANDS.forEach((island) => {
      const geom = new THREE.CircleGeometry(Math.max(1, island.visualR - 0.5), 6, HEX);
      geom.rotateX(-Math.PI / 2);
      const mesh = new THREE.Mesh(geom, wetMat);
      mesh.position.set(island.x, P + 0.035, island.z);
      mesh.renderOrder = 1;
      mesh.frustumCulled = true;
      wetGroup.add(mesh);
    });
    scene.add(wetGroup);
  }

  const rain = createRainSystem(THREE, rainMax);
  scene.add(rain.mesh);

  function setTargetKind(kind) {
    const k = normKind(kind);
    if (k === toKind && blendT >= 1) return;
    fromKind = displayKind;
    toKind = k;
    blendT = reduced ? 1 : 0;
    target = weatherVisualTargets(k);
    displayKind = k;
    worldState.weather.kind = k;
  }

  function updateFocus() {
    const f = getFocus();
    if (f && Number.isFinite(f.x) && Number.isFinite(f.z)) {
      focus.x = f.x;
      focus.z = f.z;
    }
  }

  function ensureRainSeeded(useCount) {
    for (let i = rain.seeded; i < useCount; i++) {
      seedRainDrop(rain, i, false);
    }
    if (useCount > rain.seeded) rain.seeded = useCount;
  }

  function writeRainMatrices(useCount, camYaw, slant) {
    rain.euler.set(0, camYaw, slant);
    rain.rot.makeRotationFromEuler(rain.euler);
    const e = rain.rot.elements;
    const arr = rain.mesh.instanceMatrix.array;
    for (let i = 0; i < useCount; i++) {
      const o = i * 16;
      const sy = rain.len[i];
      arr[o] = e[0];
      arr[o + 1] = e[1];
      arr[o + 2] = e[2];
      arr[o + 3] = 0;
      arr[o + 4] = e[4] * sy;
      arr[o + 5] = e[5] * sy;
      arr[o + 6] = e[6] * sy;
      arr[o + 7] = 0;
      arr[o + 8] = e[8];
      arr[o + 9] = e[9];
      arr[o + 10] = e[10];
      arr[o + 11] = 0;
      arr[o + 12] = focus.x + rain.ox[i];
      arr[o + 13] = rain.oy[i];
      arr[o + 14] = focus.z + rain.oz[i];
      arr[o + 15] = 1;
    }
    rain.mesh.count = useCount;
    rain.mesh.instanceMatrix.needsUpdate = true;
  }

  function applyVisual(v, nightMix) {
    visual = v;
    const indoor = getIsIndoor();
    if (sun) {
      const base = o.baseSunIntensity != null ? o.baseSunIntensity : sun.intensity;
      sun.intensity = base * v.sunMul * (0.85 + nightMix * 0.15);
    }
    if (onFogBase) {
      onFogBase(v.fogNearMul, v.fogFarMul, v.fogWash, nightMix, v.fogSpanMul);
    }
    if (onWaveMul) onWaveMul(v.waveMul);
    if (onSwayMul) onSwayMul(v.swayMul);
    if (setGroundWet) setGroundWet(v.wet, nightMix);

    if (wetMat) {
      const show = v.wet > 0.02 && !indoor;
      wetGroup.visible = show;
      if (show) {
        wetMat.opacity = Math.min(0.9, 0.42 + v.wet * 0.4);
        wetMat.color.copy(wetDay).lerp(wetNight, nightMix);
      }
    }

    const cloudVisible = v.cloudOpacity > 0.02 && !indoor;
    cloudGroup.visible = cloudVisible;
    if (cloudVisible) {
      cloudPlanes.forEach((p) => {
        p.material.opacity = v.cloudOpacity * p.userData.sheet.alpha;
        p.material.color.copy(cloudDay).lerp(cloudNight, nightMix);
        if (v.kind === 'storm') p.material.color.multiplyScalar(0.62);
        else if (v.kind === 'rain') p.material.color.multiplyScalar(0.78);
      });
    }

    const strength = v.rainStrength == null ? (v.rainActive ? 1 : 0) : v.rainStrength;
    const showRain = v.rainActive && strength > 0.02 && !indoor;
    rain.mesh.visible = showRain;
    if (showRain) {
      const mobile = isMobileViewport(getViewportWidth());
      rainMax = rainStreakMaxForTier(getQualityTier(), mobile);
      const useCount = Math.min(rain.ox.length, rainMax);
      rain.active = useCount;
      rainCount = useCount;
      ensureRainSeeded(useCount);
      const nightBoost = nightMix * 0.12;
      rain.mesh.material.opacity = Math.min(0.95, (v.kind === 'storm' ? 0.9 : 0.8) + nightBoost) * strength;
      rain.mesh.material.color.setHex(nightMix > 0.45 ? 0xf2f7ff : 0xffffff);
    } else {
      rain.active = 0;
      rainCount = 0;
    }
  }

  const refresher = createWorldRefresher({
    fetchNow: fetchNow,
    isHidden: () => typeof document !== 'undefined' && document.hidden,
    onBody: (body, at) => {
      worldState = mergeWorldPayload(
        body || { weather: { kind: 'clear', source: 'fallback' }, news: [] },
        queryOverride,
        at
      );
      setTargetKind(worldState.weather.kind);
      return worldState;
    }
  });

  refresher.refresh();

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && refresher.isStale()) refresher.refresh();
    });
  }

  if (queryOverride.weather) setTargetKind(queryOverride.weather);

  loop.register(function weatherTick(dt, t) {
    const nightMix = getNightMix();
    if (!reduced && blendT < 1) {
      blendT = Math.min(1, blendT + dt / WEATHER_BLEND_SEC);
    }
    const blended = blendT >= 1
      ? weatherVisualTargets(toKind)
      : blendWeatherVisual(fromKind, toKind, blendT);
    updateFocus();
    applyVisual(blended, nightMix);

    if (!reduced && blended.lightning && !getIsIndoor()) {
      lightningT += dt;
      if (Math.sin(t * 0.37) > 0.96 && lightningT > 4.5) {
        lightningT = 0;
        if (sun) sun.intensity *= 2.4;
        setTimeout(() => {
          if (sun) sun.intensity /= 2.4;
        }, 90);
      }
    }

    const camera = getCamera();
    if (rain.mesh.visible && rain.active > 0) {
      const useCount = rain.active;
      const storm = blended.kind === 'storm';
      const fall = storm ? 38 : 25;
      const wind = storm ? 7.5 : 3.0;
      for (let i = 0; i < useCount; i++) {
        rain.oy[i] -= dt * fall * rain.spd[i];
        rain.ox[i] += dt * wind;
        if (rain.oy[i] < G) seedRainDrop(rain, i, true);
      }
      let camYaw = 0;
      if (camera) {
        camYaw = Math.atan2(camera.position.x - focus.x, camera.position.z - focus.z);
      }
      writeRainMatrices(useCount, camYaw, storm ? 0.52 : 0.26);
    }

    if (cloudGroup.visible) {
      const drift = reduced ? 0 : t;
      cloudPlanes.forEach((p) => {
        const sheet = p.userData.sheet;
        const k = sheet.repeat / CLOUD_SHEET_SIZE;
        p.position.set(focus.x, sheet.y, focus.z);
        // 雲層跟著注視點搬，但貼圖位移反向補償，雲團在世界座標上才不會黏著鏡頭。
        p.userData.tex.offset.set(
          focus.x * k + drift * sheet.speed,
          -focus.z * k + drift * sheet.speed * 0.4
        );
      });
    }

    if (wetGroup.visible && envTex && !reduced) {
      // 倒影慢慢飄，水面才像在動。
      envTex.offset.set(t * 0.004, t * 0.0026);
    }

    refresher.tick();
  });

  return {
    getWorldState: () => worldState,
    getVisual: () => visual,
    getDisplayKind: () => displayKind,
    getRainCount: () => (getIsIndoor() ? 0 : rainCount),
    formatPerfLine: () => formatWeatherPerfLine({
      displayKind: displayKind,
      rainCount: getIsIndoor() ? 0 : rainCount
    }),
    refresh: refresher.refresh,
    isRefreshing: refresher.isRefreshing,
    setTargetKind: setTargetKind,
    dispose: () => {
      scene.remove(cloudGroup);
      scene.remove(wetGroup);
      scene.remove(rain.mesh);
      rain.mesh.geometry.dispose();
      rain.mesh.material.dispose();
      if (rain.tex) rain.tex.dispose();
      cloudPlanes.forEach((p) => {
        p.geometry.dispose();
        p.material.dispose();
      });
      cloudTextures.forEach((tex) => tex.dispose());
      wetGroup.children.forEach((m) => m.geometry.dispose());
      if (wetMat) wetMat.dispose();
      if (envTex) envTex.dispose();
      if (puddleTex) puddleTex.dispose();
    }
  };
}
