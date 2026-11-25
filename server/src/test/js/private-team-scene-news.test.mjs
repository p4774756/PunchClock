import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const sceneJs = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');

test('首頁場景不再掛新聞電視', () => {
  assert.doesNotMatch(sceneJs, /pickRole = 'news'/);
  assert.doesNotMatch(sceneJs, /newsTvScreen/);
  assert.doesNotMatch(sceneJs, /onOpenNews/);
  assert.doesNotMatch(sceneJs, /target === 'news'/);
  assert.doesNotMatch(sceneJs, /makeNewsTvDrawer/);
  assert.doesNotMatch(sceneJs, /NEWS_TV_/);
});