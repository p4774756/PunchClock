// 日月星辰測試：node --test server/src/test/js/island-sky.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const skyMod = await loadPublicModule('island-sky.js');
const {
  smoothstep,
  sunOpacity,
  moonOpacity,
  starsOpacity,
  nextMeteorDelay,
  scheduleMeteor,
  stepMeteor,
  createMeteorState,
  moonPosition,
  sunDirection,
  generateStars
} = skyMod;

test('smoothstep 在邊界正確過渡', () => {
  assert.equal(smoothstep(0, 1, 0), 0);
  assert.equal(smoothstep(0, 1, 1), 1);
  assert.equal(smoothstep(0, 1, 0.5), 0.5);
  assert.equal(smoothstep(0, 1, -1), 0);
  assert.equal(smoothstep(0, 1, 2), 1);
});

test('星星透明度隨 t 遞增（t=0 為 0、t=1 最大）', () => {
  assert.equal(starsOpacity(0), 0);
  assert.equal(starsOpacity(0.5), 0);
  assert.ok(starsOpacity(0.75) > 0);
  assert.equal(starsOpacity(1), 1);
  for (let i = 0; i < 10; i++) {
    const t1 = i / 10;
    const t2 = (i + 1) / 10;
    assert.ok(starsOpacity(t2) >= starsOpacity(t1), `starsOpacity(${t2}) >= starsOpacity(${t1})`);
  }
});

test('太陽／月亮可見度隨 t 互補', () => {
  assert.equal(sunOpacity(0), 1);
  assert.equal(sunOpacity(1), 0);
  assert.ok(moonOpacity(0) < 0.01);
  assert.equal(moonOpacity(1), 1);
  for (let i = 0; i <= 10; i++) {
    const t = i / 10;
    const sunVis = sunOpacity(t) > 0.5;
    const moonVis = moonOpacity(t) > 0.5;
    assert.ok(!(sunVis && moonVis), `t=${t} 太陽月亮不能同時清晰可見`);
  }
});

test('流星間隔落在 8～20 秒', () => {
  const rng = () => 0;
  assert.equal(nextMeteorDelay(rng), 8);
  const rng1 = () => 1;
  assert.equal(nextMeteorDelay(rng1), 20);
  const rngMid = () => 0.5;
  assert.equal(nextMeteorDelay(rngMid), 14);
});

test('流星排程：同時最多一顆', () => {
  let rngVal = 0;
  const rng = () => rngVal;
  let state = createMeteorState();
  state = scheduleMeteor(state, 0, false, rng);
  assert.ok(state.pending);
  assert.ok(!state.active);
  assert.equal(state.nextAt, 8);
  state = scheduleMeteor(state, 8, false, rng);
  assert.ok(!state.pending);
  assert.ok(state.active);
  const state2 = scheduleMeteor(state, 8.1, false, rng);
  assert.ok(state2.active);
  assert.ok(!state2.pending);
});

test('流星排程：reduced 時不產生', () => {
  const rng = () => 0;
  let state = createMeteorState();
  state = scheduleMeteor(state, 0, true, rng);
  assert.ok(!state.pending);
  assert.ok(!state.active);
  state = scheduleMeteor(state, 100, true, rng);
  assert.ok(!state.pending);
  assert.ok(!state.active);
});

test('stepMeteor 正確更新進度並結束', () => {
  let state = createMeteorState();
  state = Object.assign({}, state, { active: true, startTime: 0, duration: 1 });
  state = stepMeteor(state, 0.5);
  assert.equal(state.progress, 0.5);
  assert.ok(state.active);
  state = stepMeteor(state, 1.2);
  assert.ok(!state.active);
  assert.equal(state.progress, 1);
});

test('moonPosition 回傳合理的座標', () => {
  const pos = moonPosition();
  assert.ok(typeof pos.x === 'number');
  assert.ok(typeof pos.y === 'number');
  assert.ok(typeof pos.z === 'number');
  assert.ok(pos.y > 50, '月亮應該在地平線上方');
});

test('sunDirection 回傳單位向量', () => {
  const dir = sunDirection();
  const len = Math.hypot(dir.x, dir.y, dir.z);
  assert.ok(Math.abs(len - 1) < 0.0001);
});

test('generateStars 產生指定數量的星星資料', () => {
  let callCount = 0;
  const rng = () => {
    callCount++;
    return (callCount % 100) / 100;
  };
  const stars = generateStars(100, rng);
  assert.equal(stars.length, 100);
  for (const star of stars) {
    assert.ok(typeof star.x === 'number');
    assert.ok(typeof star.y === 'number');
    assert.ok(typeof star.z === 'number');
    assert.ok(star.y > 0, '星星應該在上半球');
    assert.ok(star.size >= 0.8 && star.size <= 2.4);
    assert.ok(star.phase >= 0 && star.phase < Math.PI * 2);
    assert.ok(star.twinkleSpeed >= 1.5 && star.twinkleSpeed <= 3.5);
  }
});

function createMockTHREE() {
  const disposables = [];
  function Sprite(mat) {
    this.scale = { set: () => {} };
    this.position = { x: 0, y: 0, z: 0, set: (x, y, z) => { this.x = x; this.y = y; this.z = z; }, copy: () => {} };
  }
  function SpriteMaterial(opts) {
    this.opacity = opts.opacity;
    this.dispose = () => disposables.push(this);
  }
  function BufferGeometry() {
    this.attributes = {};
    this.setAttribute = (name, attr) => { this.attributes[name] = attr; };
    this.dispose = () => disposables.push(this);
  }
  function BufferAttribute(arr, size) {
    this.array = arr;
    this.needsUpdate = false;
  }
  function PointsMaterial(opts) {
    this.opacity = opts.opacity;
    this.dispose = () => disposables.push(this);
  }
  function Points(geo, mat) {
    this.visible = true;
  }
  function LineBasicMaterial(opts) {
    this.opacity = opts.opacity;
    this.dispose = () => disposables.push(this);
  }
  function Line(geo, mat) {
    this.visible = true;
  }
  function PlaneGeometry(w, h) {
    this.dispose = () => disposables.push(this);
  }
  function MeshBasicMaterial(opts) {
    this.opacity = opts.opacity;
    this.dispose = () => disposables.push(this);
  }
  function Mesh(geo, mat) {
    this.position = { set: () => {} };
    this.rotation = { x: 0, z: 0 };
  }
  function Group() {
    this.children = [];
    this.add = (obj) => this.children.push(obj);
    this.renderOrder = 0;
    this.visible = true;
    this.parent = null;
  }
  return {
    Sprite,
    SpriteMaterial,
    BufferGeometry,
    BufferAttribute,
    PointsMaterial,
    Points,
    LineBasicMaterial,
    Line,
    PlaneGeometry,
    MeshBasicMaterial,
    Mesh,
    Group,
    AdditiveBlending: 2,
    DoubleSide: 2,
    disposables
  };
}

test('setVisible(false) 後 group 隱藏', () => {
  const THREE = createMockTHREE();
  const { createIslandSky } = skyMod;
  const sky = createIslandSky(THREE, { reduced: false });
  assert.ok(sky.group.visible);
  sky.setVisible(false);
  assert.ok(!sky.group.visible);
  sky.setVisible(true);
  assert.ok(sky.group.visible);
});

test('dispose 會釋放幾何與材質', () => {
  const THREE = createMockTHREE();
  const { createIslandSky } = skyMod;
  const sky = createIslandSky(THREE, { reduced: false });
  sky.dispose();
  assert.ok(THREE.disposables.length >= 8, '應該至少釋放 8 個物件（材質+幾何）');
});

test('update 後太陽月亮透明度符合 t', () => {
  const THREE = createMockTHREE();
  const { createIslandSky } = skyMod;
  const sky = createIslandSky(THREE, { reduced: true });
  sky.update(0, 0, null);
  assert.equal(sky.getSunOpacity(), 1);
  assert.ok(sky.getMoonOpacity() < 0.01);
  sky.update(1, 1, null);
  assert.equal(sky.getSunOpacity(), 0);
  assert.equal(sky.getMoonOpacity(), 1);
});

test('reduced 時流星狀態保持不活躍', () => {
  const THREE = createMockTHREE();
  const { createIslandSky } = skyMod;
  const sky = createIslandSky(THREE, { reduced: true });
  for (let i = 0; i < 100; i++) {
    sky.update(1, i, null);
    const state = sky.getMeteorState();
    assert.ok(!state.active, '減少動態時不應有活躍流星');
    assert.ok(!state.pending, '減少動態時不應排程流星');
  }
});
