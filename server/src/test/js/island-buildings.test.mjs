import { describe, it } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { BUILDING_GLOW, glowForMaterial } from '../../main/resources/public/js/island-buildings-data.js';
import { BUILDINGS, buildingById } from '../../main/resources/public/js/island-layout.js';
import { MAX_LAMP_POINT_LIGHTS } from '../../main/resources/public/js/island-kit.js';

const buildingsJs = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/js/island-buildings.js'),
  'utf8'
);

describe('casino Vegas sign', () => {
  it('招牌 canvas 使用繁中賭場，不含簡體赌场', () => {
    assert.ok(buildingsJs.includes('神之島賭場'));
    assert.ok(!buildingsJs.includes('赌场'));
    assert.ok(!buildingsJs.includes("'CASINO'"));
  });

  it('招牌貼在 GLB sign_face，JS 替身裝飾已移除', () => {
    assert.ok(!buildingsJs.includes('attachCasinoVegasDecor'));
    assert.ok(!buildingsJs.includes('signGroup'));
    assert.ok(buildingsJs.includes('casinoSignMap'));
    assert.ok(buildingsJs.includes("signMaps[buildingId]"));
    assert.match(buildingsJs, /fillText\('777'/);
  });

  it('casino layout v2 放大與博弈島半徑', () => {
    const c = buildingById('casino');
    assert.equal(c.x, 17.0);
    assert.equal(c.z, -7.35);
    assert.equal(c.w, 4.2);
    assert.equal(c.d, 3.4);
    assert.equal(c.yaw, -Math.PI / 2);
    assert.equal(c.entry.reach, 0.85);
    assert.equal(c.entry.action, 'enterInterior');
  });

  it('MAX_LAMP_POINT_LIGHTS 仍為 4', () => {
    assert.equal(MAX_LAMP_POINT_LIGHTS, 4);
  });
});

describe('BUILDING_GLOW', () => {
  it('shop has windows_glow and lantern_glow', () => {
    assert.ok(BUILDING_GLOW.shop);
    assert.deepStrictEqual(BUILDING_GLOW.shop.windows_glow, { color: 0xffb15a, amount: 0.95 });
    assert.deepStrictEqual(BUILDING_GLOW.shop.lantern_glow, { color: 0xff7a3a, amount: 1.1 });
  });

  it('casino has windows_glow, lantern_glow, and neon_glow', () => {
    assert.ok(BUILDING_GLOW.casino);
    assert.deepStrictEqual(BUILDING_GLOW.casino.windows_glow, { color: 0xffc56a, amount: 1.05 });
    assert.deepStrictEqual(BUILDING_GLOW.casino.lantern_glow, { color: 0xffc56a, amount: 1.2 });
    assert.deepStrictEqual(BUILDING_GLOW.casino.neon_glow, { color: 0xff6ad8, amount: 1.15 });
  });

  it('tcm-site has windows_glow and lantern_glow', () => {
    assert.ok(BUILDING_GLOW['tcm-site']);
    assert.deepStrictEqual(BUILDING_GLOW['tcm-site'].windows_glow, { color: 0xffb15a, amount: 0.9 });
    assert.deepStrictEqual(BUILDING_GLOW['tcm-site'].lantern_glow, { color: 0xff7a3a, amount: 1.15 });
  });

  it('computer-site has windows_glow, lantern_glow, and neon_glow with cyan', () => {
    assert.ok(BUILDING_GLOW['computer-site']);
    assert.deepStrictEqual(BUILDING_GLOW['computer-site'].windows_glow, { color: 0x7ec8ea, amount: 1.15 });
    assert.deepStrictEqual(BUILDING_GLOW['computer-site'].lantern_glow, { color: 0x7ec8ea, amount: 0.95 });
    assert.deepStrictEqual(BUILDING_GLOW['computer-site'].neon_glow, { color: 0x3cf0ff, amount: 1.35 });
  });
});

describe('glowForMaterial', () => {
  it('returns glow info for known building/material pairs', () => {
    assert.deepStrictEqual(glowForMaterial('shop', 'windows_glow'), { color: 0xffb15a, amount: 0.95 });
    assert.deepStrictEqual(glowForMaterial('casino', 'neon_glow'), { color: 0xff6ad8, amount: 1.15 });
    assert.deepStrictEqual(glowForMaterial('computer-site', 'neon_glow'), { color: 0x3cf0ff, amount: 1.35 });
  });

  it('returns null for unknown building or material', () => {
    assert.strictEqual(glowForMaterial('unknown', 'windows_glow'), null);
    assert.strictEqual(glowForMaterial('shop', 'unknown_material'), null);
  });
});

describe('BUILDINGS door consistency', () => {
  it('shop door is at -X (west wall)', () => {
    const shop = buildingById('shop');
    assert.ok(shop);
    assert.ok(shop.entry);
    assert.ok(shop.entry.at);
    assert.ok(shop.entry.at.x < 0, 'shop door x should be negative (west wall)');
  });

  it('casino, tcm-site, computer-site have door at +Z (front)', () => {
    ['casino', 'tcm-site', 'computer-site'].forEach((id) => {
      const b = buildingById(id);
      assert.ok(b, `${id} should exist`);
      assert.ok(b.entry, `${id} should have entry`);
      assert.ok(!b.entry.at, `${id} should NOT have entry.at (door at +Z via reach)`);
    });
  });
});

describe('computer-site GLB exterior', () => {
  it('研發樓 JS 外觀遮醜已移除，招牌仍走 labSignMap', () => {
    assert.ok(!buildingsJs.includes('attachComputerLabExterior'));
    assert.ok(!buildingsJs.includes('attachComputerSiteExterior'));
    assert.ok(!buildingsJs.includes('whitenComputerSiteMeshes'));
    assert.ok(buildingsJs.includes('labSignMap'));
    assert.ok(buildingsJs.includes("buildingId === 'computer-site'"));
    assert.ok(buildingsJs.includes("obj.name === 'windmill'"));
  });

  it('computer-site layout 與門口維持職涯走廊可走', () => {
    const c = buildingById('computer-site');
    assert.equal(c.x, 0.45);
    assert.equal(c.z, 18.35);
    assert.equal(c.w, 3.5);
    assert.equal(c.d, 2.8);
    assert.equal(c.yaw, Math.PI);
    assert.equal(c.entry.reach, 0.78);
    assert.ok(Math.abs(c.door.x - 0.45) < 1e-6);
    assert.ok(Math.abs(c.door.z - 16.17) < 0.01);
  });
});

describe('BUILDINGS footprint dimensions', () => {
  const expected = {
    shop: { w: 4.75, d: 3.2 },
    casino: { w: 4.2, d: 3.4 },
    'tcm-site': { w: 2.3, d: 1.8 },
    'computer-site': { w: 3.5, d: 2.8 }
  };

  Object.entries(expected).forEach(([id, dims]) => {
    it(`${id} footprint is w=${dims.w}, d=${dims.d}`, () => {
      const b = buildingById(id);
      assert.ok(b);
      assert.strictEqual(b.w, dims.w);
      assert.strictEqual(b.d, dims.d);
    });
  });
});
