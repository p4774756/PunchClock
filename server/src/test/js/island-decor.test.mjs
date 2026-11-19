import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const decor = await loadPublicModule('island-decor.js');
const kit = await loadPublicModule('island-kit.js');

const { G } = kit;

const {
  MAX_LAMP_POINT_LIGHTS,
  MAIN_PAVING_SPECS,
  MAIN_ISLAND_GRASS_DECOR_CENTER_Y,
  MAIN_ISLAND_GRASS_DECOR_HEIGHT,
  MAIN_PAVING_GROUP_Y,
  MAIN_PAVING_BRICK_HEIGHT,
  SHOP_GLB_FRONT_Z,
  SHOP_EAVES_BULB_Y,
  SHOP_EAVES_BULB_Z,
  SHOP_EAVES_SIGN_Z,
  pavingTileLayout,
  decorLampHaloRadius,
  decorLampHaloOpacity,
  stringLightBulbLayout,
  stringLightGlowScale,
  stringLightEmissiveAtMix,
  flowerBedStemLayout,
  exploreSkyColorHex,
  moonSeaReflectionOpacity,
  mainIslandFoamAlpha,
  mainIslandFoamBaseOpacity,
  countPointLightsInRoot
} = decor;

const { lampGroundHaloRadius, lampHaloOpacity } = kit;

describe('island-decor paving', () => {
  test('grass decor layer sits below paving bricks', () => {
    const grassTop = MAIN_ISLAND_GRASS_DECOR_CENTER_Y + MAIN_ISLAND_GRASS_DECOR_HEIGHT / 2;
    const pavingTop = MAIN_PAVING_GROUP_Y + MAIN_PAVING_BRICK_HEIGHT / 2;
    assert.ok(grassTop < pavingTop - 0.02);
    assert.equal(MAIN_ISLAND_GRASS_DECOR_CENTER_Y, G + 0.013);
  });

  test('pavingTileLayout is deterministic', () => {
    const a = pavingTileLayout(4, 3);
    const b = pavingTileLayout(4, 3);
    assert.deepEqual(a, b);
    assert.ok(a.length >= 4);
  });

  test('MAIN_PAVING_SPECS keeps legacy walkway count', () => {
    assert.equal(MAIN_PAVING_SPECS.length, 8);
  });
});

describe('island-decor lamps', () => {
  test('decorLampHaloRadius matches kit baseline scale', () => {
    const p = 12;
    assert.ok(decorLampHaloRadius(p) > lampGroundHaloRadius(p));
    assert.ok(Math.abs(decorLampHaloRadius(p) / lampGroundHaloRadius(p) - 1.045) < 1e-9);
  });

  test('decorLampHaloOpacity compatible with lampHaloOpacity at day', () => {
    assert.equal(decorLampHaloOpacity(10, 0), 0);
    assert.equal(decorLampHaloOpacity(10, 0), lampHaloOpacity(10, 0));
  });

  test('decorLampHaloOpacity slightly brighter at night', () => {
    const n = decorLampHaloOpacity(12, 1);
    const base = lampHaloOpacity(12, 1);
    assert.ok(n > base);
    assert.ok(n <= 0.96);
  });

  test('MAX_LAMP_POINT_LIGHTS stays 4', () => {
    assert.equal(MAX_LAMP_POINT_LIGHTS, 4);
  });
});

describe('island-decor string lights', () => {
  test('shop eaves JS decor retired; constants kept for layout reference', () => {
    assert.ok(SHOP_EAVES_BULB_Y < 1.85);
    assert.ok(SHOP_EAVES_BULB_Z > SHOP_GLB_FRONT_Z);
    assert.ok(SHOP_EAVES_SIGN_Z >= SHOP_GLB_FRONT_Z);
    const sceneJs = readFileSync(
      join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/js/island-buildings.js'),
      'utf8'
    );
    assert.ok(!sceneJs.includes('attachShopEavesDecor'));
  });

  test('stringLightBulbLayout spans walkway', () => {
    const bulbs = stringLightBulbLayout(4, 9);
    assert.equal(bulbs.length, 9);
    assert.ok(Math.abs(bulbs[0].x + 2) < 1e-9);
    assert.ok(Math.abs(bulbs[8].x - 2) < 1e-9);
  });

  test('reduced motion uses static glow', () => {
    assert.equal(stringLightGlowScale(true, 10, 0), 1);
    assert.equal(stringLightGlowScale(true, 99, 5), 1);
    const a = stringLightGlowScale(false, 1.2, 0.3);
    const b = stringLightGlowScale(false, 1.2, 0.3);
    assert.equal(a, b);
    assert.ok(a >= 0.88 && a <= 1.06);
  });

  test('stringLightEmissiveAtMix static when reduced', () => {
    const day = stringLightEmissiveAtMix(0, true, 5, 0);
    const night = stringLightEmissiveAtMix(1, true, 5, 0);
    assert.ok(night > day);
    assert.equal(stringLightEmissiveAtMix(1, true, 50, 2), stringLightEmissiveAtMix(1, true, 99, 2));
  });
});

describe('island-decor flowers and sky', () => {
  test('flowerBedStemLayout deterministic', () => {
    assert.deepEqual(flowerBedStemLayout(1, 0.8, 5, 3), flowerBedStemLayout(1, 0.8, 5, 3));
  });

  test('exploreSkyColorHex deepens toward night', () => {
    const day = exploreSkyColorHex(0);
    const night = exploreSkyColorHex(1);
    assert.ok(night < day);
    assert.equal(exploreSkyColorHex(0), 0xd8f7f8);
  });

  test('moonSeaReflectionOpacity off by day', () => {
    assert.equal(moonSeaReflectionOpacity(0, false), 0);
    assert.equal(moonSeaReflectionOpacity(0.2, false), 0);
    assert.ok(moonSeaReflectionOpacity(1, false) > 0.75);
    assert.equal(moonSeaReflectionOpacity(1, true), moonSeaReflectionOpacity(1, false));
  });

  test('main island foam', () => {
    assert.equal(mainIslandFoamBaseOpacity(), 0.42);
    assert.equal(mainIslandFoamAlpha(0.5, 0, false), 0.5);
    assert.equal(mainIslandFoamAlpha(0.5, 1, true), mainIslandFoamBaseOpacity());
  });
});

describe('island-decor point light budget', () => {
  test('countPointLightsInRoot respects cap in mock scene', () => {
    function makeLight() {
      return { isPointLight: true, children: [] };
    }
    const root = {
      children: [makeLight(), makeLight(), makeLight(), makeLight(), makeLight()]
    };
    assert.equal(countPointLightsInRoot(root), 5);
    assert.ok(countPointLightsInRoot(root) > MAX_LAMP_POINT_LIGHTS);
  });
});