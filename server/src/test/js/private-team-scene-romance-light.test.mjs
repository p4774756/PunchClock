import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const scenePath = join(publicRoot, 'js/private-team-scene.js');
const { privateTeamCandleFlickerMultiplier } = await loadPublicModule('private-team-scene.js');

test('紫藍月光、窗框影子、水光、燭火與暖色洗牆', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /0x8898f8/);
  assert.match(src, /makeBathtubCausticsTexture/);
  assert.match(src, /bathtubCaustics/);
  assert.match(src, /bathtubWaterCaustics/);
  assert.match(src, /windowSillCandle/);
  assert.match(src, /bathtubEdgeCandle/);
  assert.match(src, /tubBackWarmWash/);
  assert.doesNotMatch(src, /ceilingWarmWash/);
  assert.match(src, /windowMoonlightWall/);
  assert.match(src, /rgba\(16,22,52/);
  assert.doesNotMatch(src, /shadowMap\.enabled\s*=\s*true/);
});

test('蠟燭閃爍在 reducedMotion 時趨於穩定', () => {
  const steady = privateTeamCandleFlickerMultiplier(12.4, true, false);
  const active = privateTeamCandleFlickerMultiplier(12.4, false, false);
  assert.equal(steady, 0.7);
  assert.ok(active > steady);
  const low = privateTeamCandleFlickerMultiplier(5.1, false, true);
  assert.ok(low >= 0.79 && low <= 0.92);
});

test('caustics 位移在 reducedMotion 區塊外、且窄螢較慢', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /causticsTex\.offset/);
  assert.match(src, /reducedMotion[\s\S]*causticsMat\.opacity = 0\.28/);
  assert.match(src, /coarsePointer \|\| narrowWin \? 0\.012 : 0\.028/);
  assert.match(src, /privateTeamCandleFlickerMultiplier/);
});