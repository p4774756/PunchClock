import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const {
  DIVE_GEOM_DEFAULTS,
  diveIsUnderwater,
  diveSkipElapsedMs,
  diveTimeline,
  diveTotalMs,
  secretaryDiveStateAt
} = await loadPublicModule('private-team-scene-dive.js');

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const geom = Object.assign({}, DIVE_GEOM_DEFAULTS, { startX: 1.38, startZ: -0.3, fromLounge: true, reduced: false });

function phaseAt(ms, g = geom) {
  return secretaryDiveStateAt(ms, g).phase;
}

test('時間軸依序：走到板緣、跳、沉、浮、上岸、走回', () => {
  const names = diveTimeline(true, false).map((s) => s.name);
  assert.deepEqual(names, ['walk', 'prep', 'jump', 'sink', 'float', 'rise', 'hop', 'walkBack']);
  assert.deepEqual(diveTimeline(false, false).map((s) => s.name), ['prep', 'jump', 'sink', 'float', 'rise', 'hop']);
  assert.equal(phaseAt(0), 'walk');
  assert.equal(phaseAt(diveTotalMs(true, false) + 1), 'done');
  assert.equal(secretaryDiveStateAt(diveTotalMs(true, false) + 1, geom).done, true);
});

test('起點在沙發、終點回到沙發，腳底都在地板上', () => {
  const start = secretaryDiveStateAt(0, geom);
  assert.ok(Math.abs(start.x - geom.startX) < 1e-6 && Math.abs(start.z - geom.startZ) < 1e-6);
  const almostEnd = secretaryDiveStateAt(diveTotalMs(true, false) - 1, geom);
  assert.ok(Math.abs(almostEnd.x - geom.startX) < 0.02);
  assert.ok(Math.abs(almostEnd.feetY - geom.floorY) < 0.03);
});

test('跳躍是拋物線：中點最高、落點在水面', () => {
  let t = 0;
  for (const seg of diveTimeline(true, false)) {
    if (seg.name === 'jump') break;
    t += seg.ms;
  }
  const jumpMs = diveTimeline(true, false).find((s) => s.name === 'jump').ms;
  const mid = secretaryDiveStateAt(t + jumpMs / 2, geom);
  const early = secretaryDiveStateAt(t + jumpMs * 0.1, geom);
  const end = secretaryDiveStateAt(t + jumpMs - 1, geom);
  assert.ok(mid.feetY > early.feetY);
  assert.ok(mid.feetY > geom.floorY + 0.4);
  assert.ok(Math.abs(end.feetY - geom.waterY) < 0.05);
  assert.equal(mid.sprite, 'yeah');
});

test('下沉後完全沒入水中，氣泡條件成立；浮起時頭在水面上', () => {
  let t = 0;
  for (const seg of diveTimeline(true, false)) {
    if (seg.name === 'float') break;
    t += seg.ms;
  }
  const deep = secretaryDiveStateAt(t + 100, geom);
  assert.equal(deep.phase, 'float');
  assert.equal(diveIsUnderwater(deep, geom), true);
  assert.ok(deep.submerge > 0.9);
  const rise = diveTimeline(true, false).find((s) => s.name === 'float').ms + t;
  const surfaced = secretaryDiveStateAt(rise + 1099, geom);
  assert.equal(diveIsUnderwater(surfaced, geom), false);
});

test('上岸：略過到浮起階段，不會倒退', () => {
  const skipped = diveSkipElapsedMs(1000, true, false);
  assert.equal(phaseAt(skipped), 'rise');
  assert.equal(diveSkipElapsedMs(1e9, true, false), 1e9);
  const late = diveTotalMs(true, false) - 100;
  assert.equal(diveSkipElapsedMs(late, true, false), late);
});

test('減少動態：只留浮在水面的靜態停留，沒有拋物線', () => {
  const g = Object.assign({}, geom, { reduced: true });
  assert.deepEqual(diveTimeline(true, true).map((s) => s.name), ['float']);
  const s = secretaryDiveStateAt(500, g);
  assert.equal(s.phase, 'float');
  assert.equal(s.rotZ, 0);
  assert.equal(secretaryDiveStateAt(diveTotalMs(true, true) + 1, g).done, true);
});

test('場景接上跳水：按鈕、API、不用 emoji、不動 onBeforeCompile 為 null', () => {
  const page = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const scene = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  const fx = readFileSync(join(publicRoot, 'js/private-team-scene-dive-fx.js'), 'utf8');
  assert.match(page, /data-pt-dive-btn/);
  assert.match(page, />跳水<\/button>/);
  assert.match(page, /scene\.playSecretaryDive\(/);
  assert.match(scene, /playSecretaryDive/);
  assert.match(scene, /refreshSecretaryDisplay\(\) \{\s*if \(dancePlayback \|\| divePlayback\) return;/);
  assert.doesNotMatch(scene + fx, /onBeforeCompile\s*=\s*null/);
  assert.doesNotMatch(page + scene + fx, /[\u{1F300}-\u{1FAFF}]/u);
});
