import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { bundleSource } from './load-public-module.mjs';

const seaSource = bundleSource('island-sea.js');
const seaSourceNoThree = seaSource
  .replace(/import \* as THREE from 'three';\n?/, '')
  .replace(/import { ISLANDS } from '[^']+';\n?/, '')
  .replace(/export function createIslandSea[\s\S]*$/, '');
const seaMod = await import('data:text/javascript;base64,' + Buffer.from(seaSourceNoThree).toString('base64'));
const { waveHeight, foamRadius, foamCycle, foamCycleReduced } = seaMod;

const { loadPublicModule } = await import('./load-public-module.mjs');
const { ISLANDS } = await loadPublicModule('island-layout.js');

describe('island-sea wave height', () => {
  test('waveHeight returns deterministic value for given position and time', () => {
    const h1 = waveHeight(5, 10, 2.5);
    const h2 = waveHeight(5, 10, 2.5);
    assert.equal(h1, h2, 'same inputs should produce same output');
  });

  test('waveHeight varies with position', () => {
    const h1 = waveHeight(0, 0, 1);
    const h2 = waveHeight(10, 10, 1);
    assert.notEqual(h1, h2, 'different positions should produce different heights');
  });

  test('waveHeight varies with time', () => {
    const h1 = waveHeight(5, 5, 0);
    const h2 = waveHeight(5, 5, 5);
    assert.notEqual(h1, h2, 'different times should produce different heights');
  });

  test('waveHeight amplitude is capped', () => {
    const cap = 0.038;
    for (let x = -50; x <= 50; x += 10) {
      for (let z = -50; z <= 50; z += 10) {
        for (let t = 0; t < 20; t += 1) {
          const h = waveHeight(x, z, t);
          assert.ok(h >= -cap && h <= cap, `height ${h} at (${x}, ${z}, ${t}) should be within cap`);
        }
      }
    }
  });

  test('waveHeight respects custom amplitude cap', () => {
    const customCap = 0.02;
    for (let x = -30; x <= 30; x += 15) {
      for (let z = -30; z <= 30; z += 15) {
        for (let t = 0; t < 10; t += 2) {
          const h = waveHeight(x, z, t, { cap: customCap });
          assert.ok(h >= -customCap && h <= customCap, `height ${h} should be within custom cap ${customCap}`);
        }
      }
    }
  });

  test('waveHeight returns 0 when reduced is true', () => {
    for (let x = -20; x <= 20; x += 10) {
      for (let z = -20; z <= 20; z += 10) {
        for (let t = 0; t < 10; t += 2) {
          const h = waveHeight(x, z, t, { reduced: true });
          assert.equal(h, 0, `reduced mode should return 0, got ${h}`);
        }
      }
    }
  });
});

describe('island-sea foam', () => {
  test('foamRadius derives from visualR', () => {
    const visualR = 5.3;
    const r = foamRadius(visualR);
    assert.ok(r > visualR, 'foam radius should be larger than visualR');
    assert.equal(r, visualR + 0.95 + 0.35, 'foam radius formula: visualR + 0.95 + 0.35');
  });

  test('foamRadius for each island in ISLANDS', () => {
    ISLANDS.forEach((island) => {
      const r = foamRadius(island.visualR);
      assert.ok(r > island.visualR + 0.9, `island ${island.id} foam radius should be >visualR+0.9`);
    });
  });

  test('foamCycle scale and opacity vary within period', () => {
    const period = 5;
    const phase = 0;
    const samples = [];
    for (let t = 0; t < period; t += 0.5) {
      samples.push(foamCycle(t, phase, period));
    }
    const scales = samples.map(s => s.scale);
    const opacities = samples.map(s => s.opacity);
    assert.ok(Math.max(...scales) > Math.min(...scales), 'scale should vary');
    assert.ok(Math.max(...opacities) > Math.min(...opacities), 'opacity should vary');
  });

  test('foamCycle repeats with same period', () => {
    const period = 5;
    const phase = 1.2;
    const c1 = foamCycle(2.5, phase, period);
    const c2 = foamCycle(2.5 + period, phase, period);
    assert.ok(Math.abs(c1.scale - c2.scale) < 0.001, 'scale should repeat');
    assert.ok(Math.abs(c1.opacity - c2.opacity) < 0.001, 'opacity should repeat');
  });

  test('foamCycle different phases produce different values at same time', () => {
    const period = 5;
    const c1 = foamCycle(3, 0, period);
    const c2 = foamCycle(3, 2, period);
    const sameScale = Math.abs(c1.scale - c2.scale) < 0.001;
    const sameOpacity = Math.abs(c1.opacity - c2.opacity) < 0.001;
    assert.ok(!sameScale || !sameOpacity, 'different phases should not sync');
  });

  test('foamCycleReduced returns fixed values', () => {
    const c1 = foamCycleReduced();
    const c2 = foamCycleReduced();
    assert.deepEqual(c1, c2, 'reduced cycle should be constant');
    assert.equal(c1.scale, 0.85, 'reduced scale should be 0.85');
    assert.equal(c1.opacity, 0.45, 'reduced opacity should be 0.45');
  });
});

describe('island-sea coverage', () => {
  test('all ISLANDS have valid visualR for foam', () => {
    assert.ok(ISLANDS.length >= 4, 'should have at least 4 islands');
    ISLANDS.forEach((island) => {
      assert.ok(island.visualR > 0, `island ${island.id} should have positive visualR`);
      const r = foamRadius(island.visualR);
      assert.ok(r > 0, `island ${island.id} foam radius should be positive`);
    });
  });
});
