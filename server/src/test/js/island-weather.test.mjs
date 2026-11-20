// node --test server/src/test/js/island-weather.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const W = await loadPublicModule('island-weather.js');

/** 最小 Three.js 替身：沒有 document 時天氣模組只會用到這些型別。 */
function fakeThree() {
  class Color {
    constructor(hex) { this.hex = hex == null ? 0xffffff : hex; }
    clone() { return new Color(this.hex); }
    copy(other) { this.hex = other.hex; return this; }
    lerp() { return this; }
    setHex(hex) { this.hex = hex; return this; }
    multiplyScalar() { return this; }
  }
  class Matrix4 {
    constructor() { this.elements = new Array(16).fill(0); this.elements[0] = 1; this.elements[5] = 1; this.elements[10] = 1; this.elements[15] = 1; }
    makeRotationFromEuler() { return this; }
  }
  return {
    Group: class { constructor() { this.children = []; this.visible = true; } add(c) { this.children.push(c); } },
    Color: Color,
    Matrix4: Matrix4,
    Euler: class { constructor() { this.x = 0; this.y = 0; this.z = 0; } set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; } },
    PlaneGeometry: class { dispose() {} },
    InstancedBufferGeometry: class { copy() { return this; } dispose() {} },
    MeshBasicMaterial: class {
      constructor(o) { Object.assign(this, o); this.color = new Color(o && o.color); }
      dispose() {}
    },
    InstancedMesh: class {
      constructor(geometry, material, count) {
        this.geometry = geometry;
        this.material = material;
        this.count = count;
        this.visible = true;
        this.instanceMatrix = { array: new Float32Array(count * 16), setUsage() {}, needsUpdate: false };
      }
    },
    DynamicDrawUsage: 1,
    DoubleSide: 2,
    ClampToEdgeWrapping: 3,
    RepeatWrapping: 4,
    EquirectangularReflectionMapping: 5
  };
}

function makeController(overrides) {
  const o = overrides || {};
  const ticks = [];
  const sun = { intensity: 2 };
  const scene = { add() {}, remove() {} };
  const loop = { register: (fn) => ticks.push(fn) };
  const controller = W.createIslandWeather(Object.assign({
    THREE: fakeThree(),
    scene: scene,
    sun: sun,
    loop: loop,
    baseSunIntensity: 2,
    getNightMix: () => 0,
    getQualityTier: () => 'high',
    getViewportWidth: () => 1280,
    fetchNow: () => Promise.resolve({ weather: { kind: 'clear' }, news: [] })
  }, o));
  const tick = (dt, t) => ticks.forEach((fn) => fn(dt, t));
  return {
    controller: controller,
    sun: sun,
    tick: tick,
    settle: () => {
      for (let i = 0; i < 20; i++) tick(0.5, i * 0.5);
    }
  };
}

test('weatherVisualTargets 對應', () => {
  const rain = W.weatherVisualTargets('rain');
  assert.ok(rain.fogNearMul < 1);
  assert.ok(rain.sunMul < 1);
  assert.equal(rain.waveMul, W.WAVE_MUL.rain);
  assert.ok(rain.rainActive);
  assert.ok(rain.wet > 0);
  const storm = W.weatherVisualTargets('storm');
  assert.ok(storm.waveMul >= 3, 'storm 浪高倍率要明顯大於 rain');
  assert.ok(storm.swayMul >= 3, 'storm 樹搖倍率要明顯大於 rain');
  assert.equal(W.weatherVisualTargets('clear').wet, 0);
});

test('雨絲上限與畫質 tier', () => {
  assert.equal(W.rainStreakMaxForTier('high', false), 4000);
  assert.equal(W.rainStreakMaxForTier('shadowOff', false), 1200);
  assert.equal(W.rainStreakMaxForTier('high', true), 1200);
});

test('?weather= 覆寫解析', () => {
  const q = W.parseWorldWeatherQuery('?weather=storm&temp=32&news=0');
  assert.equal(q.weather, 'storm');
  assert.equal(q.tempC, 32);
  assert.equal(q.newsFlag, false);
});

test('?festival= 只接受合法 id，亂填不會留在 payload', () => {
  assert.equal(W.parseWorldWeatherQuery('?festival=mid-autumn').festival, 'mid-autumn');
  assert.equal(W.parseWorldWeatherQuery('?festival=<img src=x>').festival, null);
  const merged = W.mergeWorldPayload({}, W.parseWorldWeatherQuery('?festival=nope'), Date.parse('2026-06-01T04:00:00Z'));
  assert.equal(merged.festival, null);
  assert.equal(merged.calendar.festival, null);
  const ok = W.mergeWorldPayload({}, W.parseWorldWeatherQuery('?festival=christmas'), Date.parse('2026-06-01T04:00:00Z'));
  assert.equal(ok.festival, 'christmas');
  assert.equal(ok.calendar.festival, 'christmas');
});

test('reduced 時 blend 可直接到目標', () => {
  const mid = W.blendWeatherVisual('clear', 'storm', 0.5);
  assert.ok(mid.waveMul > 1 && mid.waveMul < W.WAVE_MUL.storm);
});

test('mergeWorldPayload 覆寫優先', () => {
  const merged = W.mergeWorldPayload(
    { weather: { kind: 'clear', tempC: 20 }, news: [{ title: 'A' }] },
    { weather: 'fog', tempC: 18 },
    Date.now()
  );
  assert.equal(merged.weather.kind, 'fog');
  assert.equal(merged.weather.tempC, 18);
});

test('mergeWorldPayload 帶出當天或最近剛過的節氣', () => {
  const merged = W.mergeWorldPayload({}, {}, Date.parse('2026-11-20T04:00:00Z'));
  assert.equal(merged.calendar.date, '2026-11-20');
  assert.equal(merged.calendar.solarTerm, null);
  assert.equal(merged.calendar.recentSolarTerm, '立冬');
  assert.equal(merged.calendar.solarTermIsToday, false);
});

test('霧距離跟著鏡頭距離走：全景時玩家與主島仍在清楚區', () => {
  const closeUp = W.fogDistancesForCamera(150, 320, 0.38, 0.42, 14);
  assert.ok(closeUp.near >= 14 + W.FOG_CLEAR_RADIUS);
  assert.ok(closeUp.far > closeUp.near);

  // 全景鏡頭離注視點 115 公尺，固定 near=57 會把整座島吃光。
  const panorama = W.fogDistancesForCamera(150, 320, 0.38, 0.42, 115);
  assert.ok(panorama.near >= 115 + W.FOG_CLEAR_RADIUS, 'near=' + panorama.near);
  assert.ok(panorama.far - panorama.near > 40, '遠景仍要有淡出區間');

  // clear 天氣不應該因為這個補償而改變原本的霧。
  const clear = W.fogDistancesForCamera(150, 320, 1, 1, 14);
  assert.equal(clear.near, 150);
  assert.equal(clear.far, 320);
});

test('refresh 單一 in-flight：TTL 到期前只打一次 /api/world/now', async () => {
  let now = 1_000_000;
  let hidden = false;
  let pending = null;
  let calls = 0;
  const refresher = W.createWorldRefresher({
    fetchNow: () => {
      calls += 1;
      return new Promise((resolve) => { pending = resolve; });
    },
    isHidden: () => hidden,
    now: () => now,
    onBody: (body) => body
  });

  refresher.refresh();
  refresher.refresh();
  for (let i = 0; i < 120; i++) refresher.tick();
  assert.equal(calls, 1, '請求未完成前不得重複發送');
  assert.equal(refresher.isRefreshing(), true);

  pending({ weather: { kind: 'rain' } });
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(refresher.isRefreshing(), false);

  for (let i = 0; i < 120; i++) refresher.tick();
  assert.equal(calls, 1, 'TTL 內不重抓');

  now += W.WEATHER_FETCH_MS;
  refresher.tick();
  assert.equal(calls, 2);
  pending({});
  await new Promise((r) => setTimeout(r, 0));

  hidden = true;
  now += W.WEATHER_FETCH_MS * 3;
  for (let i = 0; i < 120; i++) assert.equal(refresher.tick(), null, '頁面隱藏時不抓也不建立 Promise');
  assert.equal(calls, 2);

  hidden = false;
  refresher.tick();
  assert.equal(calls, 3, '回到前景只補抓一次');
  refresher.tick();
  assert.equal(calls, 3);
});

test('refresh 失敗仍會解除 in-flight 並走 fallback', async () => {
  let calls = 0;
  const seen = [];
  const refresher = W.createWorldRefresher({
    fetchNow: () => {
      calls += 1;
      return Promise.reject(new Error('offline'));
    },
    onBody: (body) => { seen.push(body); return body; }
  });
  await refresher.refresh();
  assert.equal(calls, 1);
  assert.deepEqual(seen, [null]);
  assert.equal(refresher.isRefreshing(), false);
});

test('reduced：天氣切換不過場、不打雷', () => {
  const h = makeController({ reduced: true });
  h.controller.setTargetKind('storm');
  h.tick(0.016, 0);
  const v = h.controller.getVisual();
  assert.equal(v.kind, 'storm');
  assert.equal(v.waveMul, W.WAVE_MUL.storm, 'reduced 應直接到目標，不留在中間值');

  const first = h.sun.intensity;
  for (let i = 0; i < 2000; i++) h.tick(0.016, i * 0.05);
  assert.equal(h.controller.getVisual().kind, 'storm');
  assert.equal(h.sun.intensity, first, 'reduced 不得有閃電造成的亮度跳動');
});

test('非 reduced：天氣切換會淡入淡出', () => {
  const h = makeController({ reduced: false });
  h.controller.setTargetKind('storm');
  h.tick(0.5, 0);
  const mid = h.controller.getVisual();
  assert.ok(mid.waveMul > 1 && mid.waveMul < W.WAVE_MUL.storm, 'waveMul=' + mid.waveMul);
  for (let i = 0; i < Math.ceil(W.WEATHER_BLEND_SEC / 0.5) + 2; i++) h.tick(0.5, i);
  assert.equal(h.controller.getVisual().waveMul, W.WAVE_MUL.storm);
});

test('室內：雨絲數與 perf 行歸零', () => {
  let indoor = false;
  const h = makeController({ getIsIndoor: () => indoor });
  h.controller.setTargetKind('rain');
  h.settle();
  assert.ok(h.controller.getRainCount() > 0);
  assert.match(h.controller.formatPerfLine(), /雨絲 \d+/);

  indoor = true;
  h.tick(0.5, 1);
  assert.equal(h.controller.getRainCount(), 0);
  assert.equal(h.controller.formatPerfLine(), '天氣：rain · 雨絲 0');
});

test('雨絲是單一 draw call 的 InstancedMesh，數量受 tier 限制', () => {
  const h = makeController({ getQualityTier: () => 'shadowOff' });
  h.controller.setTargetKind('storm');
  h.settle();
  assert.equal(h.controller.getRainCount(), W.RAIN_MAX_MOBILE);
});
