import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('首頁場景不再掛銅人展示', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /beginAtlasBronzeLoad\(\)/);
  assert.doesNotMatch(src, /atlasBronzeDisplay/);
  assert.doesNotMatch(src, /pickRole = 'atlas'/);
  assert.doesNotMatch(src, /BRONZE_GLB_URL/);
  assert.doesNotMatch(src, /coerceBronzeHumanMaterials/);
});

test('首頁場景不再掛牆上施工進度板', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.doesNotMatch(src, /progressTexture\(0\.62\)/);
  assert.doesNotMatch(src, /progMesh\.name = 'progressBoard'/);
  assert.doesNotMatch(src, /pickRole = 'progress'/);
  assert.doesNotMatch(src, /makeProgressBoardDrawer/);
  assert.doesNotMatch(src, /PROGRESS_BOARD_/);
});