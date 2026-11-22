import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('主控台頂欄為任務、施工進度、辦公室', () => {
  const html = readFileSync(join(publicRoot, 'console.html'), 'utf8');
  assert.match(html, /data-tab="devices"/);
  assert.match(html, /data-open-roadmap/);
  assert.match(html, /href="\/">辦公室<\/a>/);
  assert.doesNotMatch(html, /data-tab="system"/);
  assert.doesNotMatch(html, /id="panel-system"/);
  assert.doesNotMatch(html, /href="\/dev-status"/);
  assert.doesNotMatch(html, /開發機狀態/);
});

test('首頁 HUD 右上身分膠囊與左上任務頭像', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.doesNotMatch(js, /data-pt-menu-toggle/);
  assert.doesNotMatch(js, /data-pt-open-roadmap/);
  assert.match(js, /data-pt-task-btn/);
  assert.match(js, /data-pt-identity-toggle|pt-hud-identity-guest/);
  assert.match(js, /\/login\?next=%2F/);
  assert.match(js, /\/login\?next=%2Fconsole/);
  assert.doesNotMatch(js, /開發機狀態/);
  assert.doesNotMatch(js, /pt-hud-card/);
  assert.doesNotMatch(js, /\/api\/dev-status/);
  assert.match(js, /\/api\/office\/public/);
});