// node --test server/src/test/js/island-people.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', '..', 'main', 'resources', 'public');
const glbPath = join(publicDir, 'models', 'npc-women.glb');
const islandJs = readFileSync(join(publicDir, 'js', 'island.js'), 'utf8');

const D = await loadPublicModule('island-people-data.js');
const L = await loadPublicModule('island-layout.js');
const peopleJs = readFileSync(join(publicDir, 'js', 'island-people.js'), 'utf8');
const C = await loadPublicModule('island-city-data.js');

test('七種造型清單且不含旗袍', () => {
  assert.deepEqual(D.NPC_STYLE_IDS, [
    'summer', 'student', 'tech', 'idol', 'swimsuit', 'sport', 'fairy'
  ]);
  assert.equal(D.NPC_STYLE_IDS.includes('qipao'), false);
  assert.equal(D.NPC_STYLE_IDS.includes('cheongsam'), false);
  assert.equal(D.NPC_STYLE_IDS.length, 7);
});

test('預算常數與 GLB 統計', () => {
  const st = statSync(glbPath);
  assert.ok(st.size <= D.NPC_BUDGET.MAX_GLB_BYTES);
  assert.ok(D.NPC_GLB_STATS.bytes <= D.NPC_BUDGET.MAX_GLB_BYTES);
  assert.ok(D.NPC_GLB_STATS.trisTotalUnique <= D.NPC_BUDGET.MAX_UNIQUE_TRIS);
  for (const style of D.NPC_STYLE_IDS) {
    assert.ok(D.NPC_GLB_STATS.trisPerStyle[style] <= D.NPC_BUDGET.MAX_TRIS_PER_STYLE);
  }
  assert.ok(D.NPC_GLB_STATS.drawCallsEst <= D.NPC_BUDGET.MAX_DRAW_CALLS);
});

test('行人數量上限', () => {
  const full = D.npcSpawnPlan({});
  assert.ok(D.countNpcInstances(full) <= D.NPC_BUDGET.MAX_INSTANCES);
  const estTris = D.countNpcInstances(full) * D.NPC_BUDGET.MAX_TRIS_PER_STYLE;
  assert.ok(estTris <= D.NPC_BUDGET.MAX_INSTANCE_TRIS);
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

test('dispose 介面存在', () => {
  assert.match(peopleJs, /npcDisposeAll/);
  assert.match(peopleJs, /mixer\.stopAllAction/);
  assert.match(peopleJs, /geometry\.dispose/);
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

test('重建指令字串', () => {
  assert.match(D.NPC_GLB_STATS.rebuildCmd, /build_npc_women\.py/);
  assert.match(D.NPC_GLB_STATS.rebuildCmd, /npc-women\.glb/);
});