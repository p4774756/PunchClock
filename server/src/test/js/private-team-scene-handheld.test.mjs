import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const scenePath = join(publicRoot, 'js/private-team-scene.js');

test('窗台馬克杯右側立放 Game Boy，可點擊 handheld', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /addWindowSillHandheld/);
  assert.match(src, /windowSillHandheld/);
  assert.match(src, /pickRole = 'handheld'|userData\.pickRole = 'handheld'/);
  assert.match(src, /windowSillMug/);
  assert.match(src, /mugX \+ 0\.11/);
  assert.match(src, /windowSillHandheldPick/);
  assert.match(src, /PointLight/);
  assert.match(src, /NearestFilter/);
  assert.match(
    src,
    /clickables\.push\(monitors\[mi\]\.group\)|clickables = \[(?:bubble, windowSillHandheld\.group|windowSillHandheld\.group, bubble)\]/
  );
  assert.match(src, /pickRoleFromHits/);
});

test('掌機 hover 與啟動回呼', () => {
  const src = readFileSync(scenePath, 'utf8');
  assert.match(src, /applyHandheldHighlight/);
  assert.match(src, /target === 'handheld'/);
});