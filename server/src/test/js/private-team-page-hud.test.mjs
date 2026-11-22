import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const { taskProgressFromTiger } = await loadPublicModule('private-team-office.js');

test('私人秘書 HUD 標記與身分文案', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /class="pt-hud-task"[^>]*data-pt-task-btn/);
  assert.match(js, /<img src="\/assets\/private-team\/secretary-avatar\.webp"/);
  assert.match(js, /alt="" aria-hidden="true"/);
  assert.match(js, /<span class="pt-a-name">私人秘書<\/span>/);
  assert.match(js, /data-pt-task-bar/);
  assert.match(js, /taskProgressFromTiger/);
  assert.match(js, /pt-hud-id-plate/);
  assert.match(js, /data-pt-identity-toggle[^>]*aria-label="帳號"/);
  assert.match(js, /pt-hud-id-plate is-guest[^>]*aria-label="帳號"/);
  assert.match(js, /管理員/);
  assert.match(js, /線上/);
  assert.match(js, /訪客/);
  assert.match(js, /登入/);
  assert.doesNotMatch(js, /pt-hud-identity/);
  assert.doesNotMatch(js, /AUTH_ABBREV/);
  assert.doesNotMatch(js, /data-pt-daynight/);
  assert.doesNotMatch(js, /punchclock-private-team-day/);
});

test('任務細條僅在有公開計數時顯示', () => {
  assert.equal(taskProgressFromTiger({ active: [], completedToday: [] }), null);
  const onlyActive = taskProgressFromTiger({ active: [{ issue: 1 }], completedToday: [] });
  assert.deepEqual(onlyActive, { completed: 0, total: 1, fraction: 0 });
});