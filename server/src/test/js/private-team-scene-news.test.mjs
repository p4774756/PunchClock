import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const sceneJs = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');

const { newsTickerScrollEnabled } = await loadPublicModule('private-team-scene.js');

test('首頁場景不再掛新聞電視', () => {
  assert.doesNotMatch(sceneJs, /pickRole = 'news'/);
  assert.doesNotMatch(sceneJs, /newsTvScreen/);
  assert.doesNotMatch(sceneJs, /onOpenNews/);
  assert.doesNotMatch(sceneJs, /target === 'news'/);
});

test('reducedMotion 與粗指針／窄螢幕不滾動字幕', () => {
  const fakeWin = {
    innerWidth: 390,
    matchMedia(q) {
      return { matches: q.includes('coarse') };
    }
  };
  assert.equal(newsTickerScrollEnabled(true, fakeWin), false);
  assert.equal(newsTickerScrollEnabled(false, fakeWin), false);
  assert.equal(newsTickerScrollEnabled(false, { innerWidth: 1280, matchMedia: () => ({ matches: false }) }), true);
});

test('Canvas 只在狀態變化時重畫', () => {
  assert.match(sceneJs, /if \(key === lastKey\) return/);
});