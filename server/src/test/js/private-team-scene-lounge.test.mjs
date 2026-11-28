import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

/** 浴缸外緣半寬（private-team-scene.js TUB_HW），右緣世界 X ≈ 0.84 */
const TUB_RIGHT_EDGE_X = 0.84;

test('休憩區家具名稱與秘書沙發錨點', async () => {
  const {
    SECRETARY_SOFA_POSITION,
    SECRETARY_SHADOW_SOFA,
    SECRETARY_BUBBLE_SOFA,
    SECRETARY_STAGE_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_OFFSET,
    secretarySofaShadowLocal,
    SECRETARY_STAGE_SOFA_OFFSET,
    secretarySofaPlacementForOutfit,
    SOFA_HALF_WIDTH_X,
    SOFA_ARM_RADIUS,
    SOFA_WORLD_SCALE_X,
    vinylShouldSpin
  } = await loadPublicModule('private-team-scene-lounge.js');
  assert.deepEqual(SECRETARY_SOFA_POSITION, { x: 1.38, y: 0, z: -0.3 });
  assert.deepEqual(SECRETARY_SHADOW_SOFA, { x: -0.03, y: 0.072, z: 0.16 });
  assert.deepEqual(SECRETARY_BUBBLE_SOFA, { x: 1.5, z: 0.2 });
  const sofaLeftX = SECRETARY_SOFA_POSITION.x - (SOFA_HALF_WIDTH_X + SOFA_ARM_RADIUS) * SOFA_WORLD_SCALE_X;
  assert.ok(sofaLeftX > TUB_RIGHT_EDGE_X + 0.04, '沙發左緣應與浴缸右緣留有 X 向空隙');
  const defSofa = secretarySofaPlacementForOutfit('default');
  const stagePlaceForShadow = secretarySofaPlacementForOutfit('stage');
  assert.equal(defSofa.scale, SECRETARY_DEFAULT_SOFA_SCALE);
  assert.ok(defSofa.scale > 0 && defSofa.scale < 1, '預設服立繪已去背，需縮小坐上座墊');
  assert.equal(defSofa.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_DEFAULT_SOFA_OFFSET.x);
  assert.equal(defSofa.position.y, SECRETARY_SOFA_POSITION.y + SECRETARY_DEFAULT_SOFA_OFFSET.y);
  assert.equal(defSofa.position.z, SECRETARY_SOFA_POSITION.z + SECRETARY_DEFAULT_SOFA_OFFSET.z);
  const shadowLocal = secretarySofaShadowLocal(stagePlaceForShadow);
  assert.ok(Number.isFinite(shadowLocal.x) && Number.isFinite(shadowLocal.z));
  // 陰影的世界高度要貼著地毯，不能因立繪下移而沉進地板
  assert.ok(stagePlaceForShadow.position.y + shadowLocal.y * stagePlaceForShadow.scale > 0.04);
  assert.equal(defSofa.bubble.x, SECRETARY_BUBBLE_SOFA.x);
  const stageSofa = secretarySofaPlacementForOutfit('stage');
  assert.equal(stageSofa.scale, SECRETARY_STAGE_SOFA_SCALE);
  assert.ok(stageSofa.scale > 0 && stageSofa.scale < 1);
  assert.equal(stageSofa.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_STAGE_SOFA_OFFSET.x);
  assert.equal(stageSofa.position.y, SECRETARY_SOFA_POSITION.y + SECRETARY_STAGE_SOFA_OFFSET.y);
  assert.equal(stageSofa.position.z, SECRETARY_SOFA_POSITION.z + SECRETARY_STAGE_SOFA_OFFSET.z);
  assert.notDeepEqual(stageSofa.position, SECRETARY_SOFA_POSITION);
  assert.deepEqual(secretarySofaPlacementForOutfit('nope').position, defSofa.position);
  assert.equal(vinylShouldSpin('待命中', false), true);
  assert.equal(vinylShouldSpin('工作中', false), false);
  assert.equal(vinylShouldSpin('休息中', true), false);
  const src = sceneSrc;
  assert.match(src, /name = '焦糖棕皮沙發'/);
  assert.match(src, /name = 'walnutSideboard'/);
  assert.match(src, /name = 'vinylTurntable'/);
  assert.match(src, /platter\.name = 'vinylDisc'/);
  assert.match(src, /tickVinylSpin/);
  assert.match(src, /platter\.rotation\.y/);
  assert.match(src, /makeLeatherGrainTexture/);
  assert.match(src, /makeWalnutWoodTexture/);
  assert.match(src, /vinylRecordSleeve/);
  assert.match(src, /sofaRollArm/);
  assert.match(src, /sofaBackCushion/);
  assert.match(src, /sofaSeatCushion/);
  assert.match(src, /makeRugColorTexture/);
});

test('場景在沙發姿勢依服裝套用相對偏移，且不寫死沙發錨點', () => {
  const src = sceneSrc;
  assert.match(src, /secretarySofaPlacementForOutfit\(secretaryOutfitId\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(sc, sc, sc\)/);
  assert.match(src, /\[px, py, pz, scale\]\.every\(Number\.isFinite\)/);
  assert.match(src, /secretaryRoot\.scale\.set\(SECRETARY_TUB_SCALE, SECRETARY_TUB_SCALE, SECRETARY_TUB_SCALE\)/);
  assert.doesNotMatch(src, /SECRETARY_STAGE_SOFA_POSITION/);
});

test('訪客／無效服裝的沙發擺位永遠是有限數字', async () => {
  const {
    secretarySofaPlacementForOutfit,
    SECRETARY_SOFA_POSITION,
    SECRETARY_DEFAULT_SOFA_SCALE,
    SECRETARY_DEFAULT_SOFA_OFFSET
  } = await loadPublicModule(
    'private-team-scene-lounge.js'
  );
  const samples = [undefined, null, '', 'default', 'stage', 'STAGE', 'foo', 0, {}, '  stage  '];
  for (const id of samples) {
    const place = secretarySofaPlacementForOutfit(id);
    assert.equal(typeof place.scale, 'number');
    assert.ok(Number.isFinite(place.scale) && place.scale > 0, String(id) + ' scale');
    for (const axis of ['x', 'y', 'z']) {
      assert.ok(Number.isFinite(place.position[axis]), String(id) + ' pos.' + axis);
    }
    assert.ok(Number.isFinite(place.bubble.x), String(id) + ' bubble.x');
    assert.ok(Number.isFinite(place.bubble.z), String(id) + ' bubble.z');
    if (place.bubble.y != null) {
      assert.ok(Number.isFinite(place.bubble.y), String(id) + ' bubble.y');
    }
  }
  const def = secretarySofaPlacementForOutfit('default');
  const visitor = secretarySofaPlacementForOutfit(undefined);
  assert.deepEqual(visitor, def);
  assert.equal(visitor.scale, SECRETARY_DEFAULT_SOFA_SCALE);
  assert.equal(visitor.position.x, SECRETARY_SOFA_POSITION.x + SECRETARY_DEFAULT_SOFA_OFFSET.x);
  assert.deepEqual(secretarySofaPlacementForOutfit(''), def);
  assert.deepEqual(secretarySofaPlacementForOutfit('foo'), def);
});

test('浴缸佔地常數與場景原始碼的 TUB_* 一致', async () => {
  const { LOUNGE_TUB_FOOTPRINT } = await loadPublicModule('private-team-scene-lounge.js');
  const num = (name) => {
    const m = sceneSrc.match(new RegExp('const ' + name + ' = (-?[0-9.]+);'));
    assert.ok(m, name);
    return Number(m[1]);
  };
  assert.equal(LOUNGE_TUB_FOOTPRINT.cx, num('TUB_CX'));
  assert.equal(LOUNGE_TUB_FOOTPRINT.cz, num('TUB_CZ'));
  // 外緣滾邊（+0.03）與地板收邊（+0.04／+0.038）
  assert.ok(Math.abs(LOUNGE_TUB_FOOTPRINT.hw - (num('TUB_HW') + 0.04)) < 1e-9);
  assert.ok(Math.abs(LOUNGE_TUB_FOOTPRINT.hd - (num('TUB_HD') + 0.04)) < 1e-9);
  assert.match(sceneSrc, /export const FLOOR_SIZE = 3\.6;/);
});

test('休憩區家具彼此不重疊、浴缸與沙發之間留有通道，家具都在地板內', async () => {
  const L = await loadPublicModule('private-team-scene-lounge.js');
  const f = L.loungeObstacleFootprints();
  const names = Object.keys(f);
  for (let i = 0; i < names.length; i++) {
    for (let j = i + 1; j < names.length; j++) {
      const gap = L.rectGap(f[names[i]], f[names[j]]);
      assert.ok(gap > 0, names[i] + ' 與 ' + names[j] + ' 重疊');
    }
    const r = f[names[i]];
    assert.ok(Math.abs(r.cx) + r.hw <= L.LOUNGE_FLOOR_HALF, names[i] + ' X 超出地板');
    assert.ok(Math.abs(r.cz) + r.hd <= L.LOUNGE_FLOOR_HALF, names[i] + ' Z 超出地板');
  }
  // 浴缸右緣與沙發左緣的通道至少一個肩寬的一半多一點
  assert.ok(L.rectGap(f.tub, f.sofa) >= 0.12, '浴缸與沙發太近：' + L.rectGap(f.tub, f.sofa));
  // 盆栽離浴缸與沙發都至少一個身體半徑，不再卡在兩者之間
  assert.ok(L.rectGap(f.plant, f.tub) >= L.SECRETARY_ROUTE_BODY_RADIUS);
  assert.ok(L.rectGap(f.plant, f.sofa) >= L.SECRETARY_ROUTE_BODY_RADIUS);
  assert.ok(L.rectGap(f.lamp, f.sofa) >= 0.05);
  // 地毯貼地：不能壓到浴缸，且要把整個沙發包進去
  const rug = { cx: L.LOUNGE_RUG.cx, cz: L.LOUNGE_RUG.cz, hw: L.LOUNGE_RUG.w / 2, hd: L.LOUNGE_RUG.d / 2 };
  assert.ok(L.rectGap(rug, f.tub) > 0, '地毯壓到浴缸');
  assert.ok(Math.abs(rug.cx) + rug.hw <= L.LOUNGE_FLOOR_HALF + 0.03, '地毯超出地板太多');
  assert.ok(rug.cx - rug.hw <= f.sofa.cx - f.sofa.hw && rug.cx + rug.hw >= f.sofa.cx + f.sofa.hw - 0.05);
});

test('秘書浴缸到沙發的路線點與整段線段都離家具有淨空', async () => {
  const L = await loadPublicModule('private-team-scene-lounge.js');
  const f = L.loungeObstacleFootprints();
  const need = L.SECRETARY_ROUTE_BODY_RADIUS + 0.06;
  const route = L.SECRETARY_LOUNGE_ROUTE;
  assert.ok(route.length >= 3);
  const check = (x, z, label) => {
    assert.ok(Math.abs(x) <= L.LOUNGE_FLOOR_HALF - L.SECRETARY_ROUTE_BODY_RADIUS, label + ' 超出地板 X');
    assert.ok(Math.abs(z) <= L.LOUNGE_FLOOR_HALF - L.SECRETARY_ROUTE_BODY_RADIUS, label + ' 超出地板 Z');
    for (const [name, rect] of Object.entries(f)) {
      const d = L.distancePointToRect(x, z, rect);
      assert.ok(d >= need, label + ' 離 ' + name + ' 只有 ' + d.toFixed(3) + '，需要 ' + need.toFixed(3));
    }
  };
  for (let i = 0; i < route.length; i++) {
    check(route[i].x, route[i].z, route[i].name);
    if (i === 0) continue;
    const a = route[i - 1];
    const b = route[i];
    for (let k = 1; k < 20; k++) {
      const t = k / 20;
      check(a.x + (b.x - a.x) * t, a.z + (b.z - a.z) * t, a.name + '→' + b.name + ' @' + t);
    }
  }
  // 起點在浴缸右側、終點在沙發前方
  assert.ok(route[0].x > f.tub.cx + f.tub.hw);
  const last = route[route.length - 1];
  assert.ok(last.z > f.sofa.cz + f.sofa.hd);
  assert.ok(last.x > f.sofa.cx - f.sofa.hw && last.x < f.sofa.cx + f.sofa.hw);
});

test('舊配置（盆栽卡在浴缸與沙發之間）確實不合格，測試有抓到', async () => {
  const L = await loadPublicModule('private-team-scene-lounge.js');
  const f = L.loungeObstacleFootprints();
  const oldPlant = { cx: 0.84, cz: 0.0, hw: L.LOUNGE_MONSTERA_RADIUS, hd: L.LOUNGE_MONSTERA_RADIUS };
  assert.equal(L.rectGap(oldPlant, f.tub), 0, '舊盆栽位置與浴缸重疊');
});


test('泡澡與沙發上的秘書比例相近（差距不超過 25%）', async () => {
  const lounge = await loadPublicModule('private-team-scene-lounge.js');
  const r1 = lounge.SECRETARY_DEFAULT_SOFA_SCALE / lounge.SECRETARY_TUB_SCALE;
  const r2 = lounge.SECRETARY_STAGE_SOFA_SCALE / lounge.SECRETARY_TUB_SCALE;
  assert.ok(r1 > 0.75 && r1 < 1.25, 'default sofa/tub ratio ' + r1);
  assert.ok(r2 > 0.75 && r2 < 1.25 + 0.0001 + 0.0, 'stage sofa/tub ratio ' + r2);
  assert.ok(lounge.SECRETARY_TUB_ROOT_Y > lounge.SECRETARY_TUB_POSITION.y);
});
