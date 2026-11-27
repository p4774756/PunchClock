import assert from 'node:assert/strict';
import test from 'node:test';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneJs = readPrivateTeamSceneBundle();

test('首頁場景不再掛新聞電視', () => {
  assert.doesNotMatch(sceneJs, /pickRole = 'news'/);
  assert.doesNotMatch(sceneJs, /newsTvScreen/);
  assert.doesNotMatch(sceneJs, /onOpenNews/);
  assert.doesNotMatch(sceneJs, /target === 'news'/);
  assert.doesNotMatch(sceneJs, /makeNewsTvDrawer/);
  assert.doesNotMatch(sceneJs, /NEWS_TV_/);
});