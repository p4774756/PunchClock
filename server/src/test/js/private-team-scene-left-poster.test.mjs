import assert from 'node:assert/strict';
import test from 'node:test';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const testDir = dirname(fileURLToPath(import.meta.url));
const publicRoot = join(testDir, '../../main/resources/public');
const sceneSrc = readPrivateTeamSceneBundle();
const posterDir = join(publicRoot, 'assets/private-team/posters');

const {
  PRIVATE_TEAM_LEFT_POSTER_IDX_KEY,
  LEFT_WALL_POSTER_URLS,
  parseLeftPosterIndex,
  nextLeftPosterIndex,
  readLeftPosterIndexFromStorage,
  formatLeftPosterIndexForStorage
} = await loadPublicModule('private-team-scene.js');

test('左牆海報索引：預設、輪播與 localStorage', () => {
  assert.equal(PRIVATE_TEAM_LEFT_POSTER_IDX_KEY, 'punchclock.office.leftPosterIdx');
  assert.equal(parseLeftPosterIndex(undefined), 0);
  assert.equal(parseLeftPosterIndex(''), 0);
  assert.equal(parseLeftPosterIndex('2'), 2);
  assert.equal(parseLeftPosterIndex('4'), 0);
  assert.equal(parseLeftPosterIndex('x'), 0);
  assert.equal(nextLeftPosterIndex(0), 1);
  assert.equal(nextLeftPosterIndex(3), 0);
  assert.equal(readLeftPosterIndexFromStorage(() => null), 0);
  assert.equal(readLeftPosterIndexFromStorage(() => '3'), 3);
  assert.equal(readLeftPosterIndexFromStorage(() => '9'), 0);
  assert.equal(formatLeftPosterIndexForStorage(2), '2');
  assert.equal(formatLeftPosterIndexForStorage('bad'), '0');
});

test('左牆海報靜態資源與場景掛載', () => {
  assert.equal(LEFT_WALL_POSTER_URLS.length, 4);
  for (let i = 0; i < LEFT_WALL_POSTER_URLS.length; i++) {
    const url = LEFT_WALL_POSTER_URLS[i];
    const rel = url.replace('/assets/', 'assets/');
    assert.ok(existsSync(join(publicRoot, rel)), url);
  }
  const src = sceneSrc;
  assert.match(src, /leftWallPoster/);
  assert.match(src, /pickRole = 'leftPoster'/);
  assert.match(src, /applyLeftPosterHighlight/);
  assert.match(src, /PRIVATE_TEAM_LEFT_POSTER_IDX_KEY/);
  assert.match(src, /clickables = \[windowSillHandheld\.group, bubble, leftPosterGroup\]/);
  assert.match(src, /target === 'leftPoster'/);
  assert.match(src, /ensurePosterTexture/);
  assert.match(src, /LEFT_POSTER_W = 1\.31/);
  assert.match(src, /brassPic/);
});