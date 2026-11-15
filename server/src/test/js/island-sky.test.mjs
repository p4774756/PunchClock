// 日月星辰純邏輯：node --test server/src/test/js/island-sky.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const skyMod = await loadPublicModule('island-sky.js');
const seaMod = await loadPublicModule('island-sea.js');
const {
  smoothstep,
  starOpacity,
  sunOpacity,
  moonOpacity,
  sunY,
  moonY,
  nextMeteorDelay,
  meteorDuration,
  createMeteorScheduler,
  createIslandSky,
  starTwinkle,
  STAR_COUNT,
  METEOR_INTERVAL_MIN,
  METEOR_INTERVAL_MAX,
  METEOR_DURATION_MIN,
  METEOR_DURATION_MAX,
  MOON_REFLECTION_Y,
  MOON_ELEVATION,
  MOON_AZIMUTH
} = skyMod;

function mockCanvasElement() {
  const ctx = {
    createLinearGradient() {
      return { addColorStop() {} };
    },
    createRadialGradient() {
      return { addColorStop() {} };
    },
    fillRect() {},
    fillStyle: '',
    globalCompositeOperation: ''
  };
  return { width: 0, height: 0, getContext: () => ctx };
}

function mockThree() {
  return {
    SRGBColorSpace: 'srgb',
    AdditiveBlending: 2,
    FrontSide: 0,
    DoubleSide: 2,
    Group: class {
      constructor() {
        this.children = [];
        this.position = {
          x: 0, y: 0, z: 0,
          set(x, y, z) { this.x = x; this.y = y; this.z = z; }
        };
        this.visible = true;
      }
      add(c) { this.children.push(c); }
    },
    Vector3: class {
      constructor(x, y, z) { this.x = x; this.y = y; this.z = z; }
      set(x, y, z) { this.x = x; this.y = y; this.z = z; return this; }
      normalize() { return this; }
    },
    SpriteMaterial: class {
      constructor(opts) { Object.assign(this, opts); }
      dispose() {}
    },
    Sprite: class {
      constructor(mat) { this.material = mat; this.scale = { set() {} }; }
    },
    CanvasTexture: class {
      constructor() {}
      dispose() {}
    },
    BufferGeometry: class {
      constructor() { this.attributes = {}; }
      setAttribute(name, attr) { this.attributes[name] = attr; }
      getAttribute(name) { return this.attributes[name]; }
      dispose() {}
    },
    BufferAttribute: class {
      constructor(array, itemSize) { this.array = array; this.itemSize = itemSize; this.needsUpdate = false; }
    },
    ShaderMaterial: class {
      constructor(opts) { this.uniforms = opts.uniforms; }
      dispose() {}
    },
    Points: class {
      constructor(geo, mat) { this.geometry = geo; this.material = mat; this.visible = true; this.renderOrder = 0; }
    },
    LineBasicMaterial: class {
      constructor(opts) { Object.assign(this, opts); }
      dispose() {}
    },
    Line: class {
      constructor(geo, mat) { this.geometry = geo; this.material = mat; this.visible = true; this.renderOrder = 0; }
    },
    PlaneGeometry: class { dispose() {} },
    MeshBasicMaterial: class {
      constructor(opts) { Object.assign(this, opts); }
      dispose() {}
    },
    Mesh: class {
      constructor(geo, mat) {
        this.geometry = geo;
        this.material = mat;
        this.position = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.rotation = { x: 0, y: 0, z: 0, set(x, y, z) { this.x = x; this.y = y; this.z = z; } };
        this.visible = true;
        this.renderOrder = 0;
      }
    }
  };
}

test('smoothstep 基本行為', () => {
  assert.equal(smoothstep(0, 1, 0), 0);
  assert.equal(smoothstep(0, 1, 1), 1);
  assert.equal(smoothstep(0, 1, 0.5), 0.5);
  assert.equal(smoothstep(0, 1, -1), 0);
  assert.equal(smoothstep(0, 1, 2), 1);
  assert.ok(smoothstep(0, 1, 0.25) > 0 && smoothstep(0, 1, 0.25) < 0.5);
  assert.ok(smoothstep(0, 1, 0.75) > 0.5 && smoothstep(0, 1, 0.75) < 1);
});

test('星星透明度隨 t 的曲線（t=0 為 0、t=1 為最大、中間單調遞增）', () => {
  assert.equal(starOpacity(0), 0);
  assert.equal(starOpacity(0.5), 0);
  assert.ok(starOpacity(0.6) > 0);
  assert.ok(starOpacity(0.8) > starOpacity(0.6));
  assert.equal(starOpacity(1), 1);

  const values = [0, 0.3, 0.5, 0.6, 0.7, 0.8, 0.9, 1].map(starOpacity);
  for (let i = 1; i < values.length; i++) {
    assert.ok(values[i] >= values[i - 1], `starOpacity 應單調遞增 at ${i}`);
  }
});

test('太陽／月亮可見度隨 t 互補', () => {
  assert.equal(sunOpacity(0), 1);
  assert.equal(sunOpacity(1), 0);
  assert.ok(sunOpacity(0.25) > sunOpacity(0.5));

  assert.equal(moonOpacity(0), 0);
  assert.ok(moonOpacity(0.5) > 0);
  assert.equal(moonOpacity(1), 1);

  for (let t = 0; t <= 1; t += 0.1) {
    const sun = sunOpacity(t);
    const moon = moonOpacity(t);
    assert.ok(sun >= 0 && sun <= 1);
    assert.ok(moon >= 0 && moon <= 1);
  }
});

test('sunY 隨 t 增加而下降', () => {
  const baseY = 100;
  assert.equal(sunY(0, baseY), baseY);
  assert.ok(sunY(0.5, baseY) < baseY);
  assert.ok(sunY(1, baseY) < sunY(0.5, baseY));
});

test('moonY 隨 t 從地平線升至基準高度', () => {
  const baseY = 80;
  const horizon = 5;
  assert.equal(moonY(0, baseY, horizon), horizon);
  assert.ok(moonY(0.5, baseY, horizon) > horizon);
  assert.ok(moonY(1, baseY, horizon) > moonY(0.5, baseY, horizon));
  assert.ok(Math.abs(moonY(1, baseY, horizon) - baseY) < 0.01);
});

test('流星間隔落在 8～20 秒', () => {
  for (let i = 0; i < 100; i++) {
    const delay = nextMeteorDelay();
    assert.ok(delay >= METEOR_INTERVAL_MIN && delay <= METEOR_INTERVAL_MAX,
      `delay ${delay} 應在 ${METEOR_INTERVAL_MIN}～${METEOR_INTERVAL_MAX}`);
  }
});

test('流星持續時間落在 0.6～1 秒', () => {
  for (let i = 0; i < 100; i++) {
    const dur = meteorDuration();
    assert.ok(dur >= METEOR_DURATION_MIN && dur <= METEOR_DURATION_MAX,
      `duration ${dur} 應在 ${METEOR_DURATION_MIN}～${METEOR_DURATION_MAX}`);
  }
});

test('流星排程：同時最多一顆', () => {
  let rngValue = 0;
  const rng = () => { rngValue = (rngValue + 0.1) % 1; return rngValue; };
  const scheduler = createMeteorScheduler({ reduced: false, rng: rng });

  let activeCount = 0;
  for (let elapsed = 0; elapsed < 100; elapsed += 0.016) {
    const state = scheduler.step(0.016, 1);
    if (state) activeCount++;
    const current = scheduler.current();
    if (state) {
      assert.ok(current === state, '正在進行中的流星應為同一物件');
    }
  }
  assert.ok(activeCount > 0, '應該有流星產生');
});

test('流星排程：reduced 時不產生', () => {
  const scheduler = createMeteorScheduler({ reduced: true });

  for (let elapsed = 0; elapsed < 60; elapsed += 0.016) {
    const state = scheduler.step(0.016, 1);
    assert.equal(state, null, 'reduced 時不應產生流星');
  }
});

test('流星排程：t < 0.7 時不產生', () => {
  const scheduler = createMeteorScheduler({ reduced: false });

  for (let elapsed = 0; elapsed < 30; elapsed += 0.016) {
    const state = scheduler.step(0.016, 0.5);
    assert.equal(state, null, 't < 0.7 時不應產生流星');
  }
});

test('流星排程：正確的時間間隔', () => {
  let callCount = 0;
  const rng = () => {
    callCount++;
    return 0;
  };
  const scheduler = createMeteorScheduler({ reduced: false, rng: rng });

  let lastMeteorEnd = 0;
  let meteorStarts = [];
  let elapsed = 0;
  let prevState = null;

  for (elapsed = 0; elapsed < 100; elapsed += 0.016) {
    const state = scheduler.step(0.016, 1);
    if (state && !prevState) {
      if (meteorStarts.length > 0) {
        const gap = elapsed - lastMeteorEnd;
        assert.ok(gap >= METEOR_INTERVAL_MIN * 0.9,
          `間隔 ${gap} 應 >= ${METEOR_INTERVAL_MIN}`);
      }
      meteorStarts.push(elapsed);
    }
    if (!state && prevState) {
      lastMeteorEnd = elapsed;
    }
    prevState = state;
  }
});

test('starTwinkle 回傳 0～1 範圍', () => {
  for (let phase = 0; phase < Math.PI * 2; phase += 0.5) {
    for (let elapsed = 0; elapsed < 10; elapsed += 0.5) {
      const twinkle = starTwinkle(phase, elapsed);
      assert.ok(twinkle >= 0 && twinkle <= 1,
        `twinkle ${twinkle} 應在 0～1 範圍`);
    }
  }
});

test('常數匯出正確', () => {
  assert.equal(STAR_COUNT, 600);
  assert.equal(METEOR_INTERVAL_MIN, 8);
  assert.equal(METEOR_INTERVAL_MAX, 20);
  assert.equal(METEOR_DURATION_MIN, 0.6);
  assert.equal(METEOR_DURATION_MAX, 1.0);
  assert.equal(MOON_REFLECTION_Y, -0.69);
});

test('月光倒影 Y 高於浪花、低於沙地', () => {
  const foamY = seaMod.foamY();
  const sandTop = -0.65;
  assert.ok(MOON_REFLECTION_Y > foamY, '月光倒影應高於浪花泡沫');
  assert.ok(MOON_REFLECTION_Y < sandTop, '月光倒影應低於沙地');
});

test('月亮仰角在 15～30° 範圍（探索模式抬頭可見）', () => {
  const elevDeg = MOON_ELEVATION * 180 / Math.PI;
  assert.ok(elevDeg >= 15 && elevDeg <= 30,
    `月亮仰角 ${elevDeg}° 應在 15～30° 範圍`);
});

test('月亮方位角已匯出', () => {
  assert.ok(typeof MOON_AZIMUTH === 'number');
});

test('createIslandSky 可建立且夜景 update 不會 moonY 遮蔽錯誤', () => {
  const prevDoc = globalThis.document;
  globalThis.document = { createElement: (tag) => (tag === 'canvas' ? mockCanvasElement() : {}) };
  try {
    const sky = createIslandSky(mockThree(), { reduced: true, sunPos: { x: -9.5, y: 20.5, z: 11 } });
    assert.ok(sky.group);
    sky.update(1, 1, null);
    sky.tick(0.016, 1);
    sky.setVisible(false);
    sky.dispose();
  } finally {
    globalThis.document = prevDoc;
  }
});
