// node --test server/src/test/js/island-people.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', '..', 'main', 'resources', 'public');
const petRoot = join(publicDir, 'pet', 'npc-women');
const islandJs = readFileSync(join(publicDir, 'js', 'island.js'), 'utf8');
const peopleJs = readFileSync(join(publicDir, 'js', 'island-people.js'), 'utf8');

const D = await loadPublicModule('island-people-data.js');
const L = await loadPublicModule('island-layout.js');
const C = await loadPublicModule('island-city-data.js');
const M = await loadPublicModule('island-milu.js');

test('七種造型清單且不含旗袍', () => {
  assert.deepEqual(D.NPC_STYLE_IDS, [
    'summer', 'student', 'tech', 'idol', 'swimsuit', 'sport', 'fairy'
  ]);
  assert.equal(D.NPC_STYLE_IDS.includes('qipao'), false);
  assert.equal(D.NPC_STYLE_IDS.length, 7);
});

test('各造型看板幀檔與 baseUrl', () => {
  for (const style of D.NPC_STYLE_IDS) {
    const anim = D.npcWomenAnim(style);
    assert.equal(anim.baseUrl, D.NPC_PET_BASE + style + '/');
    assert.equal(anim.footV, M.MILU_ANIM.footV);
    assert.equal(anim.fps.walk, 8);
    assert.equal(anim.fps.idle, 5);
    const dir = join(petRoot, style);
    assert.ok(existsSync(dir), style);
    for (const rel of ['idle/breath_01.png', 'walk/left_01.png', 'walk/front_01.png']) {
      assert.ok(existsSync(join(dir, rel)), style + ' ' + rel);
    }
    if (style === 'student' || style === 'idol' || style === 'sport') {
      assert.ok(existsSync(join(dir, 'sit', 'sit_01.png')));
      assert.ok(existsSync(join(dir, 'sit', 'sit_02.png')));
      assert.equal(anim.clips.idle.sit.length, 2);
    }
  }
});

test('行人數量上限', () => {
  const full = D.npcSpawnPlan({});
  assert.ok(D.countNpcInstances(full) <= D.NPC_BUDGET.MAX_INSTANCES);
});

test('walker／idle 路徑可走', () => {
  const strictWalk = (x, z, h) => L.walkable(x, z, h) && !L.blocked(x, z, h);
  const looseWalk = (x, z, h) => L.walkable(x, z, h);
  const plan = D.npcSpawnPlan({});
  const paths = D.allNpcPathsForTests(plan);
  for (const path of paths) {
    const onMainRing = path.length === D.MAIN_RING_WALK.length
      && path[0][0] === D.MAIN_RING_WALK[0][0];
    const fn = onMainRing ? looseWalk : strictWalk;
    const bad = D.pathWalkableSamples(path, fn);
    assert.deepEqual(bad, [], '不可走點：' + JSON.stringify(path));
  }
  const cityPaths = C.cityWalkerPaths();
  for (const path of cityPaths) {
    const bad = D.pathWalkableSamples(path, strictWalk);
    assert.deepEqual(bad, [], '城市路徑');
  }
  const main = L.islandById('main');
  for (const def of plan.idles) {
    const onMain =
      L.inIsland(def.x, def.z, main, 0.22) || L.walkable(def.x, def.z, 0.29);
    assert.ok(onMain, '主島閒置 ' + def.style);
  }
});

test('reduced 計畫縮減且不增例', () => {
  const r = D.npcSpawnPlan({ reduced: true });
  const f = D.npcSpawnPlan({});
  assert.ok(D.countNpcInstances(r) < D.countNpcInstances(f));
  assert.ok(r.cityCount < f.cityCount);
});

test('#189 三處生成點座標', () => {
  const idles = D.mainIdleDefs();
  const sport = idles.find((d) => d.style === 'sport' && d.seated);
  assert.ok(sport);
  assert.ok(Math.abs(sport.x - (-5.15)) < 0.01);
  assert.ok(Math.abs(sport.z - 5.05) < 0.01);
  assert.ok(Math.abs(sport.rotY - Math.PI * 0.85) < 0.01);

  const extras = D.islandExtraDefs();
  const idolCasino = extras.find((d) => d.style === 'idol' && d.island === 'casino' && !d.walker);
  assert.ok(idolCasino);
  assert.ok(Math.abs(idolCasino.x - 15.25) < 0.01);
  assert.ok(Math.abs(idolCasino.z - (-5.45)) < 0.01);
  assert.ok(Math.abs(idolCasino.rotY - 2.35) < 0.01);

  const tech = extras.find((d) => d.style === 'tech');
  assert.ok(tech);
  assert.ok(Math.abs(tech.x - 1.05) < 0.01);
  assert.ok(Math.abs(tech.z - 15.25) < 0.01);
  assert.ok(Math.abs(tech.rotY - (-0.35)) < 0.01);
});

test('主島三個坐下座位仍在', () => {
  const seated = D.mainIdleDefs().filter((d) => d.seated);
  assert.equal(seated.length, 3);
  const styles = seated.map((d) => d.style).sort();
  assert.deepEqual(styles, ['idol', 'sport', 'student']);
});

test('dispose 與看板運動介面', () => {
  assert.match(peopleJs, /npcDisposeAll/);
  assert.match(peopleJs, /registerNpcBillboardMotion/);
  assert.match(peopleJs, /createMiluView/);
  assert.doesNotMatch(peopleJs, /SkeletonUtils/);
  assert.doesNotMatch(peopleJs, /npc-women\.glb/);
  assert.match(islandJs, /registerBillboardMotion/);
});

test('milu 靜態點由閒置物件推導', () => {
  assert.match(islandJs, /people\.miluStaticPoints/);
  assert.doesNotMatch(islandJs, /const MILU_NPCS\s*=/);
  const idles = [{ obj: { position: { x: 0.1, z: 0.2 } } }];
  const pts = D.miluPointsFromIdles(idles);
  assert.deepEqual(pts, [{ x: 0.1, z: 0.2 }]);
});

test('makePlayerAvatar 仍使用 makePerson', () => {
  assert.match(islandJs, /function makePlayerAvatar/);
  assert.match(islandJs, /makePerson\(/);
});

test('城市機車騎乘幀獨立於七造型預算', () => {
  assert.equal(D.NPC_STYLE_IDS.includes('scooter'), false);
  assert.equal(D.NPC_STYLE_IDS.length, 7);
  const anim = D.cityScooterAnim();
  assert.equal(anim.baseUrl, D.NPC_PET_BASE + 'scooter/');
  assert.equal(anim.footV, M.MILU_ANIM.footV);
  for (const dir of ['front', 'back', 'left', 'right']) {
    assert.ok(anim.clips.walk[dir].length >= 2 && anim.clips.walk[dir].length <= 3, dir);
    for (const rel of anim.clips.walk[dir]) {
      assert.ok(existsSync(join(petRoot, 'scooter', rel)), 'scooter ' + rel);
    }
  }
  const paths = D.cityScooterFramePaths();
  assert.ok(paths.length >= 12);
});

test('機車騎士朝向依螢幕軸', () => {
  const anim = D.cityScooterAnim();
  const sticky = false;
  assert.equal(M.resolveFacing('front', 0, 1, anim, sticky), 'front');
  assert.equal(M.resolveFacing('front', 0, -1, anim, sticky), 'back');
  assert.equal(M.resolveFacing('front', 1, 0, anim, sticky), 'right');
  assert.equal(M.resolveFacing('front', -1, 0, anim, sticky), 'left');
});