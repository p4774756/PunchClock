// 群島天氣：API、視覺參數、Three.js 雨滴／雲層。純函式供 island-weather.test.mjs。
import { calendarViewForMs, resolveFestivalId, taipeiHour } from './island-world-data.js';

export const WEATHER_KINDS = Object.freeze(['clear', 'cloudy', 'rain', 'storm', 'fog']);
export const WEATHER_FETCH_MS = 20 * 60 * 1000;
export const WEATHER_BLEND_SEC = 4.0;

export const RAIN_MAX_DESKTOP = 4000;
export const RAIN_MAX_MOBILE = 1200;

export const WAVE_MUL = Object.freeze({
  clear: 1,
  cloudy: 1,
  rain: 1.15,
  storm: 2.0,
  fog: 1
});

export const SWAY_MUL = Object.freeze({
  clear: 1,
  cloudy: 1,
  rain: 1.1,
  storm: 1.75,
  fog: 1
});

const SUN_MUL = Object.freeze({
  clear: 1,
  cloudy: 0.7,
  rain: 0.55,
  storm: 0.45,
  fog: 0.65
});

const FOG_PRESET = Object.freeze({
  clear: { near: 1, far: 1, wash: 0 },
  cloudy: { near: 0.92, far: 0.88, wash: 0.12 },
  rain: { near: 0.72, far: 0.62, wash: 0.22 },
  storm: { near: 0.65, far: 0.52, wash: 0.28 },
  fog: { near: 0.38, far: 0.42, wash: 0.45 }
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
    waveMul: WAVE_MUL[k],
    swayMul: SWAY_MUL[k],
    rainActive: k === 'rain' || k === 'storm',
    cloudOpacity: k === 'clear' ? 0 : (k === 'cloudy' ? 0.55 : k === 'storm' ? 0.72 : 0.35),
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
    waveMul: lerp(from.waveMul, to.waveMul),
    swayMul: lerp(from.swayMul, to.swayMul),
    rainActive: u < 0.5 ? from.rainActive : to.rainActive,
    cloudOpacity: lerp(from.cloudOpacity, to.cloudOpacity),
    lightning: u < 0.5 ? from.lightning : to.lightning
  };
}

export function parseWorldWeatherQuery(search) {
  const qs = search instanceof URLSearchParams
    ? search
    : new URLSearchParams(String(search || ''));
  const weather = qs.get('weather');
  const temp = qs.get('temp');
  const festival = qs.get('festival');
  const news = qs.get('news');
  return {
    weather: weather ? normKind(weather) : null,
    tempC: temp != null && temp !== '' && Number.isFinite(Number(temp)) ? Number(temp) : null,
    festival: festival != null && festival !== '' ? String(festival).trim() : null,
    newsFlag: news === '0' ? false : news === '1' ? true : null
  };
}

export function mergeWorldPayload(apiBody, query, nowMs) {
  const q = query || {};
  const body = apiBody && typeof apiBody === 'object' ? apiBody : {};
  const w = body.weather && typeof body.weather === 'object' ? body.weather : {};
  const calendar = calendarViewForMs(nowMs);
  const festival = resolveFestivalId(nowMs, q.festival);
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
    calendar: Object.assign({}, calendar, { festival: q.festival ? resolveFestivalId(nowMs, q.festival) : calendar.festival }),
    festival: q.festival || festival,
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

function makeSoftCloudTexture(THREE) {
  const size = 256;
  const canvas = typeof document !== 'undefined' ? document.createElement('canvas') : null;
  if (!canvas) return null;
  canvas.width = size;
  canvas.height = size;
  const ctx = canvas.getContext('2d');
  ctx.clearRect(0, 0, size, size);
  for (let i = 0; i < 14; i++) {
    const x = Math.random() * size;
    const y = Math.random() * size * 0.55;
    const r = 40 + Math.random() * 70;
    const g = ctx.createRadialGradient(x, y, 0, x, y, r);
    g.addColorStop(0, 'rgba(255,255,255,0.55)');
    g.addColorStop(0.45, 'rgba(255,255,255,0.18)');
    g.addColorStop(1, 'rgba(255,255,255,0)');
    ctx.fillStyle = g;
    ctx.beginPath();
    ctx.arc(x, y, r, 0, Math.PI * 2);
    ctx.fill();
  }
  const tex = new THREE.CanvasTexture(canvas);
  tex.wrapS = tex.wrapT = THREE.RepeatWrapping;
  return tex;
}

function createRainSystem(THREE, maxCount) {
  const geo = new THREE.InstancedBufferGeometry();
  const base = new THREE.PlaneGeometry(0.018, 1.8);
  geo.copy(base);
  base.dispose();
  const count = Math.max(0, maxCount | 0);
  const mesh = new THREE.InstancedMesh(
    geo,
    new THREE.MeshBasicMaterial({
      color: 0xc8d8ee,
      transparent: true,
      opacity: 0.35,
      depthWrite: false,
      side: THREE.DoubleSide
    }),
    count
  );
  mesh.instanceMatrix.setUsage(THREE.DynamicDrawUsage);
  mesh.frustumCulled = false;
  mesh.visible = false;
  const dummy = new THREE.Object3D();
  const seeds = new Float32Array(count);
  for (let i = 0; i < count; i++) seeds[i] = Math.random();
  return { mesh: mesh, dummy: dummy, seeds: seeds, active: 0, slant: 0.22 };
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
  let lastFetch = 0;
  let rainMax = rainStreakMaxForTier(getQualityTier(), isMobileViewport(getViewportWidth()));
  let displayKind = 'clear';
  let rainCount = 0;
  let lightningT = 0;

  const cloudGroup = new THREE.Group();
  cloudGroup.name = 'weatherClouds';
  const cloudTex = makeSoftCloudTexture(THREE);
  const cloudPlanes = [];
  if (cloudTex) {
    for (let i = 0; i < 9; i++) {
      const mat = new THREE.MeshBasicMaterial({
        map: cloudTex,
        transparent: true,
        opacity: 0,
        depthWrite: false,
        side: THREE.DoubleSide
      });
      const plane = new THREE.Mesh(new THREE.PlaneGeometry(38 + i * 2.5, 16 + (i % 3) * 2), mat);
      plane.position.set((i - 4) * 14, 42 + (i % 2) * 4, -30 - (i % 4) * 12);
      plane.rotation.x = -0.15;
      plane.userData.drift = 0.012 + i * 0.0025;
      plane.userData.baseX = plane.position.x;
      cloudGroup.add(plane);
      cloudPlanes.push(plane);
    }
    scene.add(cloudGroup);
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

  function applyVisual(v, nightMix) {
    visual = v;
    if (sun) {
      const base = o.baseSunIntensity != null ? o.baseSunIntensity : sun.intensity;
      sun.intensity = base * v.sunMul * (0.85 + nightMix * 0.15);
    }
    if (onFogBase) {
      onFogBase(v.fogNearMul, v.fogFarMul, v.fogWash, nightMix);
    }
    if (onWaveMul) onWaveMul(v.waveMul);
    if (onSwayMul) onSwayMul(v.swayMul);
    const wet = v.rainActive ? Math.min(1, 0.55 + (v.kind === 'storm' ? 0.35 : 0.2)) : 0;
    if (setGroundWet) setGroundWet(wet, nightMix);

    cloudPlanes.forEach((p) => {
      p.material.opacity = v.cloudOpacity * (0.75 + nightMix * 0.25);
      p.visible = v.cloudOpacity > 0.02;
    });

    const indoor = getIsIndoor();
  const showRain = v.rainActive && !indoor;
    rain.mesh.visible = showRain;
    if (showRain) {
      const mobile = isMobileViewport(getViewportWidth());
      rainMax = rainStreakMaxForTier(getQualityTier(), mobile);
      const useCount = Math.min(rain.seeds.length, rainMax);
      rain.active = useCount;
      rain.slant = v.kind === 'storm' ? 0.55 : 0.28;
      rainCount = useCount;
      const span = 72;
      const dummy = rain.dummy;
      for (let i = 0; i < useCount; i++) {
        const s = rain.seeds[i];
        const x = (s * 97.3 % 1) * span - span / 2;
        const z = ((s * 173.7) % 1) * span - span / 2;
        const y = 8 + ((s * 51.2) % 1) * 28;
        dummy.position.set(x, y, z);
        dummy.rotation.z = rain.slant;
        dummy.scale.set(1, v.kind === 'storm' ? 1.35 : 1, 1);
        dummy.updateMatrix();
        rain.mesh.setMatrixAt(i, dummy.matrix);
      }
      rain.mesh.count = useCount;
      rain.mesh.instanceMatrix.needsUpdate = true;
      const nightBoost = 0.25 + nightMix * 0.45;
      rain.mesh.material.opacity = (v.kind === 'storm' ? 0.42 : 0.34) * (0.55 + nightBoost);
      rain.mesh.material.color.setHex(nightMix > 0.45 ? 0xb8ccef : 0xc8d8ee);
    } else {
      rainCount = 0;
    }
  }

  function refreshWorld() {
    if (typeof document !== 'undefined' && document.hidden) return Promise.resolve(worldState);
    const now = Date.now();
    return fetchNow().then((body) => {
      worldState = mergeWorldPayload(body, queryOverride, now);
      setTargetKind(worldState.weather.kind);
      lastFetch = now;
      return worldState;
    }).catch(() => {
      worldState = mergeWorldPayload({ weather: { kind: 'clear', source: 'fallback' }, news: [] }, queryOverride, now);
      setTargetKind(worldState.weather.kind);
      lastFetch = now;
      return worldState;
    });
  }

  refreshWorld();

  if (typeof document !== 'undefined') {
    document.addEventListener('visibilitychange', () => {
      if (!document.hidden && Date.now() - lastFetch > WEATHER_FETCH_MS) refreshWorld();
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
    applyVisual(blended, nightMix);

    if (!reduced && blended.lightning && !getIsIndoor()) {
      lightningT += dt;
      if (Math.sin(t * 0.37) > 0.96 && lightningT > 4.5) {
        lightningT = 0;
        if (sun) sun.intensity *= 2.4;
        setTimeout(() => {
          if (sun) sun.intensity /= 2.4;
        }, reduced ? 0 : 90);
      }
    }

    if (!getIsIndoor() && rain.mesh.visible) {
      const dummy = rain.dummy;
      const useCount = rain.active;
      const fall = blended.kind === 'storm' ? 34 : 22;
      for (let i = 0; i < useCount; i++) {
        rain.mesh.getMatrixAt(i, dummy.matrix);
        dummy.matrix.decompose(dummy.position, dummy.quaternion, dummy.scale);
        dummy.position.y -= dt * fall;
        dummy.position.x += dt * (blended.kind === 'storm' ? 6 : 2.5);
        if (dummy.position.y < 0.2) {
          const s = rain.seeds[i];
          dummy.position.y = 26 + (s % 1) * 10;
          dummy.position.x = ((s * 97.3) % 1) * 72 - 36;
          dummy.position.z = ((s * 173.7) % 1) * 72 - 36;
        }
        dummy.updateMatrix();
        rain.mesh.setMatrixAt(i, dummy.matrix);
      }
      rain.mesh.instanceMatrix.needsUpdate = true;
    }

    cloudPlanes.forEach((p, i) => {
      if (!p.visible) return;
      p.position.x = p.userData.baseX + Math.sin(t * p.userData.drift + i) * 2.8;
    });

    if (Date.now() - lastFetch > WEATHER_FETCH_MS) refreshWorld();
  });

  return {
    getWorldState: () => worldState,
    getVisual: () => visual,
    getDisplayKind: () => displayKind,
    getRainCount: () => rainCount,
    formatPerfLine: () => formatWeatherPerfLine({ displayKind: displayKind, rainCount: rainCount }),
    refresh: refreshWorld,
    setTargetKind: setTargetKind,
    dispose: () => {
      scene.remove(cloudGroup);
      scene.remove(rain.mesh);
      rain.mesh.geometry.dispose();
      rain.mesh.material.dispose();
      cloudPlanes.forEach((p) => {
        p.geometry.dispose();
        p.material.dispose();
      });
      if (cloudTex) cloudTex.dispose();
    }
  };
}