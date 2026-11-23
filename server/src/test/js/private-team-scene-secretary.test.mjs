import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const {
  SECRETARY_SPRITE_URL_TYPING,
  SECRETARY_SPRITE_URL_TYPING_B,
  SECRETARY_SPRITE_URL_WAVE,
  SECRETARY_SPRITE_URL_YEAH,
  secretaryTypingDisplayUrl
} = await loadPublicModule('private-team-scene.js');

const {
  completedTodayKeys,
  hasNewCompletedToday
} = await loadPublicModule('private-team-office.js');

test('secretaryTypingDisplayUrl 工作中 B 幀與減少動態', () => {
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, true, false),
    SECRETARY_SPRITE_URL_TYPING_B
  );
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, true, true),
    SECRETARY_SPRITE_URL_TYPING
  );
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, false, false),
    SECRETARY_SPRITE_URL_TYPING
  );
});

test('今日完成差量鍵', () => {
  const list = [{ issue: 2, at: 'z' }, { pr: 9 }];
  assert.deepEqual(completedTodayKeys(list), ['issue:2', 'pr:9']);
  assert.equal(hasNewCompletedToday(null, list), false);
  assert.equal(hasNewCompletedToday(['issue:2', 'pr:9'], list), false);
  assert.equal(hasNewCompletedToday(['issue:1'], list), true);
});

test('場景含揮手與比耶素材鍵', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.match(src, /SECRETARY_SPRITE_URL_WAVE/);
  assert.match(src, /playSecretaryWave/);
  assert.match(src, /playSecretaryYeah/);
  assert.match(src, new RegExp(SECRETARY_SPRITE_URL_WAVE.replace(/\//g, '\\/')));
  assert.match(src, new RegExp(SECRETARY_SPRITE_URL_YEAH.replace(/\//g, '\\/')));
});