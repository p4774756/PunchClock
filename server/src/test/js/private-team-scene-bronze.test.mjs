import assert from 'node:assert/strict';
import test from 'node:test';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';

const sceneSrc = readPrivateTeamSceneBundle();

test('首頁場景不再掛銅人展示', () => {
  const src = sceneSrc;
  assert.doesNotMatch(src, /beginAtlasBronzeLoad\(\)/);
  assert.doesNotMatch(src, /atlasBronzeDisplay/);
  assert.doesNotMatch(src, /pickRole = 'atlas'/);
  assert.doesNotMatch(src, /BRONZE_GLB_URL/);
  assert.doesNotMatch(src, /coerceBronzeHumanMaterials/);
});

test('首頁場景不再掛牆上施工進度板', () => {
  const src = sceneSrc;
  assert.doesNotMatch(src, /progressTexture\(0\.62\)/);
  assert.doesNotMatch(src, /progMesh\.name = 'progressBoard'/);
  assert.doesNotMatch(src, /pickRole = 'progress'/);
  assert.doesNotMatch(src, /makeProgressBoardDrawer/);
  assert.doesNotMatch(src, /PROGRESS_BOARD_/);
});