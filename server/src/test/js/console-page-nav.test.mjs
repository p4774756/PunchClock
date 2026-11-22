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
  const css = readFileSync(join(publicRoot, 'css/console.css'), 'utf8');
  assert.match(css, /\.console-auth a\[href="\/logout"\]\s*\{[^}]*display:\s*none\s*!important/);
});

test('首頁 HUD 楓之谷角色框與身分名牌', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.doesNotMatch(js, /data-pt-menu-toggle/);
  assert.doesNotMatch(js, /data-pt-open-roadmap/);
  assert.doesNotMatch(js, /施工進度/);
  assert.match(js, /data-pt-prog-hit/);
  assert.match(js, /onOpenProgress/);
  assert.match(js, /setRoadmapOpen/);
  assert.match(js, /openRoadmapFromBoard/);
  assert.match(js, /data-pt-task-btn/);
  assert.match(js, /secretary-avatar\.webp/);
  assert.match(js, /私人秘書/);
  assert.match(js, /data-pt-task-bar/);
  assert.match(js, /data-pt-identity-toggle/);
  assert.match(js, /pt-hud-id-plate/);
  assert.match(js, /管理員/);
  assert.match(js, /線上/);
  assert.match(js, /訪客/);
  assert.match(js, /\/login\?next=%2F/);
  assert.match(js, /\/login\?next=%2Fconsole/);
  assert.doesNotMatch(js, /開發機狀態/);
  assert.doesNotMatch(js, /pt-hud-card/);
  assert.doesNotMatch(js, /pt-hud-id-abbrev/);
  assert.doesNotMatch(js, /\/api\/dev-status/);
  assert.match(js, /\/api\/office\/public/);
  assert.match(css, /\.pt-hud-task/);
  assert.match(css, /\.pt-hud-id-plate/);
  assert.match(css, /\.pt-roadmap-open/);
  assert.match(css, /\.pt-prog-hit/);
});

test('牆上進度看板與施工進度彈窗遮罩', () => {
  const sceneJs = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  const officeJs = readFileSync(join(publicRoot, 'js/office-page.js'), 'utf8');
  assert.match(sceneJs, /progressBoard/);
  assert.match(sceneJs, /onOpenProgress/);
  assert.match(sceneJs, /setSceneInputEnabled/);
  assert.doesNotMatch(sceneJs, /pickSecretary/);
  assert.match(officeJs, /onChange/);
  assert.match(officeJs, /setRoadmapOpen/);
});