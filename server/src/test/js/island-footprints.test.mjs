// 腳印軌跡純邏輯：node --test server/src/test/js/island-footprints.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const F = await loadPublicModule('island-footprints.js');

function walk(trail, opts) {
  const state = Object.assign({
    x: 0,
    z: 0,
    yaw: 0,
    moving: true,
    onSand: true,
    shoreDist: 2,
    reduced: false
  }, opts);
  return trail.step(state, opts.dt == null ? 1 / 60 : opts.dt);
}

test('走過 stride 才產生腳印，左右交替', () => {
  const trail = F.createFootprintTrail({ max: 10, stride: 0.34, life: 6, shoreLife: 2, halfWidth: 0.1 });
  walk(trail, { x: 0, z: 0 });
  assert.equal(trail.step({
    x: 0.1, z: 0, yaw: 0, moving: true, onSand: true, shoreDist: 2
  }, 1 / 60).length, 0, '未達 stride 不產生');

  let list = [];
  let x = 0.1;
  for (let i = 0; i < 20; i++) {
    x += 0.2;
    list = walk(trail, { x: x, z: 0, yaw: 0 });
  }
  assert.ok(list.length >= 2, '應留下多枚腳印');
  assert.equal(list[0].foot, -1);
  assert.equal(list[1].foot, 1);
  assert.notEqual(list[0].x, list[1].x, '左右腳水平偏移不同');
});

test('超過 max 循環覆蓋最舊', () => {
  const trail = F.createFootprintTrail({ max: 4, stride: 0.3, life: 60, shoreLife: 60 });
  let x = 0;
  walk(trail, { x: 0, z: 0 });
  for (let i = 0; i < 12; i++) {
    x += 0.3;
    walk(trail, { x: x, z: 0, yaw: 0 });
  }
  const alive = trail.prints.filter((p) => p.alive);
  assert.equal(alive.length, 4);
  assert.equal(trail.prints.length, 4);
});

test('life 淡出；shoreLife 更快消失', () => {
  const trail = F.createFootprintTrail({ max: 8, stride: 0.3, life: 4, shoreLife: 1 });
  walk(trail, { x: 0, z: 0, shoreDist: 2 });
  walk(trail, { x: 0.3, z: 0, shoreDist: 2 });
  assert.equal(trail.prints.filter((p) => p.alive).length, 1);
  const inland = trail.prints.find((p) => p.alive);
  assert.ok(inland);
  assert.equal(inland.life, 4);

  trail.clear();
  walk(trail, { x: 0, z: 0, shoreDist: 0.3 });
  walk(trail, { x: 0.3, z: 0, shoreDist: 0.3 });
  const shore = trail.prints.find((p) => p.alive);
  assert.ok(shore);
  assert.equal(shore.life, 1);

  trail.step({
    x: 0.3, z: 0, yaw: 0, moving: false, onSand: true, shoreDist: 0.3, reduced: false
  }, 0.6);
  assert.ok(shore.alive);
  assert.ok(shore.opacity < 1 && shore.opacity > 0.3);
  trail.step({
    x: 0.3, z: 0, yaw: 0, moving: false, onSand: true, shoreDist: 0.3, reduced: false
  }, 0.5);
  assert.equal(shore.alive, false);
});

test('不在沙上或沒移動時不產生', () => {
  const trail = F.createFootprintTrail({ max: 8, stride: 0.3, life: 6, shoreLife: 2 });
  walk(trail, { x: 0, z: 0 });
  for (let i = 1; i <= 10; i++) {
    walk(trail, { x: i * 0.4, z: 0, onSand: false });
  }
  assert.equal(trail.prints.filter((p) => p.alive).length, 0);

  trail.clear();
  walk(trail, { x: 0, z: 0, moving: false });
  for (let i = 1; i <= 10; i++) {
    walk(trail, { x: i * 0.4, z: 0, moving: false });
  }
  assert.equal(trail.prints.filter((p) => p.alive).length, 0);
});

test('reduced：到期前 opacity 維持 1，到期直接消失', () => {
  const trail = F.createFootprintTrail({ max: 4, stride: 0.3, life: 2, shoreLife: 2 });
  walk(trail, { x: 0, z: 0, reduced: true });
  walk(trail, { x: 0.3, z: 0, reduced: true });
  const p = trail.prints.find((item) => item.alive);
  assert.ok(p);
  trail.step({
    x: 0.3, z: 0, yaw: 0, moving: false, onSand: true, shoreDist: 2, reduced: true
  }, 1.0);
  assert.equal(p.alive, true);
  assert.equal(p.opacity, 1);
  trail.step({
    x: 0.3, z: 0, yaw: 0, moving: false, onSand: true, shoreDist: 2, reduced: true
  }, 1.1);
  assert.equal(p.alive, false);
  assert.equal(p.opacity, 0);
});

test('clear 清空全部', () => {
  const trail = F.createFootprintTrail({ max: 6, stride: 0.3, life: 6, shoreLife: 2 });
  walk(trail, { x: 0, z: 0 });
  for (let i = 1; i <= 5; i++) walk(trail, { x: i * 0.3, z: 0 });
  assert.ok(trail.prints.some((p) => p.alive));
  trail.clear();
  assert.equal(trail.prints.every((p) => !p.alive), true);
  assert.equal(trail.step({
    x: 1.5, z: 0, yaw: 0, moving: true, onSand: true, shoreDist: 2
  }, 0).length, 0);
});
