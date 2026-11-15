import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const {
  waveHeight,
  waveAmplitudeMax,
  foamRadius,
  foamPhase,
  foamCycle
} = await loadPublicModule('island-sea.js');

describe('island-sea wave', () => {
  test('waveHeight returns deterministic value for given position and time', () => {
    const h1 = waveHeight(5, 3, 1.0, false);
    const h2 = waveHeight(5, 3, 1.0, false);
    assert.strictEqual(h1, h2, 'same inputs should return same output');
  });

  test('waveHeight never exceeds amplitude max', () => {
    const max = waveAmplitudeMax();
    for (let x = -50; x <= 50; x += 5) {
      for (let z = -50; z <= 50; z += 5) {
        for (let t = 0; t < 20; t += 0.5) {
          const h = waveHeight(x, z, t, false);
          assert.ok(Math.abs(h) <= max, `|waveHeight(${x},${z},${t})| = ${Math.abs(h)} exceeds max ${max}`);
        }
      }
    }
  });

  test('waveHeight returns 0 when reduced', () => {
    const h = waveHeight(10, 15, 2.5, true);
    assert.strictEqual(h, 0, 'reduced motion should yield 0');
  });

  test('waveHeight varies with time', () => {
    const h1 = waveHeight(0, 0, 0, false);
    const h2 = waveHeight(0, 0, 2.0, false);
    assert.notStrictEqual(h1, h2, 'different times should yield different heights');
  });
});

describe('island-sea foam', () => {
  test('foamRadius depends on visualR', () => {
    const r1 = foamRadius(5);
    const r2 = foamRadius(9);
    assert.ok(r2 > r1, 'larger island should have larger foam radius');
  });

  test('foamPhase returns different values for different indices', () => {
    const p0 = foamPhase(0);
    const p1 = foamPhase(1);
    const p2 = foamPhase(2);
    assert.notStrictEqual(p0, p1, 'index 0 and 1 should differ');
    assert.notStrictEqual(p1, p2, 'index 1 and 2 should differ');
  });

  test('foamCycle scale and alpha cycle within range', () => {
    for (let t = 0; t < 10; t += 0.25) {
      const cycle = foamCycle(t, 0, false);
      assert.ok(cycle.scale >= 1.0 && cycle.scale <= 1.2, `scale ${cycle.scale} out of range at t=${t}`);
      assert.ok(cycle.alpha >= 0.2 && cycle.alpha <= 0.6, `alpha ${cycle.alpha} out of range at t=${t}`);
    }
  });

  test('foamCycle returns fixed values when reduced', () => {
    const c1 = foamCycle(0, 0, true);
    const c2 = foamCycle(5, 0, true);
    assert.strictEqual(c1.scale, c2.scale, 'reduced should give constant scale');
    assert.strictEqual(c1.alpha, c2.alpha, 'reduced should give constant alpha');
    assert.strictEqual(c1.scale, 1.0, 'reduced scale should be 1.0');
    assert.strictEqual(c1.alpha, 0.38, 'reduced alpha should be 0.38');
  });

  test('different phase offsets produce different cycles at same time', () => {
    const c0 = foamCycle(2.0, 0, false);
    const c1 = foamCycle(2.0, 1.5, false);
    assert.ok(c0.alpha !== c1.alpha || c0.scale !== c1.scale, 'different phases should differ');
  });

  test('every ISLANDS entry gets a foam ring (foamRadius is positive)', async () => {
    const { ISLANDS } = await loadPublicModule('island-layout.js');
    for (const island of ISLANDS) {
      const r = foamRadius(island.visualR);
      assert.ok(r > island.visualR, `foam radius ${r} should exceed visualR ${island.visualR} for ${island.id}`);
    }
  });
});
