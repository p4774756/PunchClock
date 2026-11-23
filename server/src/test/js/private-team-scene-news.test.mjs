import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const sceneJs = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');

const {
  NEWS_TV_CENTER_Z,
  NEWS_TV_PLANE_W,
  PROGRESS_BOARD_CENTER_Z,
  PROGRESS_BOARD_PLANE_W,
  newsTickerScrollEnabled,
  newsTvGapFromProgressBoard
} = await loadPublicModule('private-team-scene.js');

test('新聞電視在進度板 +z 側留縫隙', () => {
  assert.ok(NEWS_TV_CENTER_Z > PROGRESS_BOARD_CENTER_Z);
  const gap = newsTvGapFromProgressBoard();
  assert.ok(gap > 0.04, 'gap=' + gap);
  const progEdge = PROGRESS_BOARD_CENTER_Z + PROGRESS_BOARD_PLANE_W / 2;
  const tvNear = NEWS_TV_CENTER_Z - NEWS_TV_PLANE_W / 2;
  assert.ok(tvNear > progEdge);
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

test('點擊電視會走 onOpenNews', () => {
  assert.match(sceneJs, /pickRole = 'news'/);
  assert.match(sceneJs, /onOpenNews/);
  assert.match(sceneJs, /target === 'news'/);
  assert.match(sceneJs, /newsTvScreen/);
});

test('Canvas 只在狀態變化時重畫', () => {
  assert.match(sceneJs, /if \(key === lastKey\) return/);
});