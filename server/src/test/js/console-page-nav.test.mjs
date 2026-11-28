import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';

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

test('首頁 HUD 楓之谷角色框', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.doesNotMatch(js, /data-pt-menu-toggle/);
  assert.doesNotMatch(js, /data-pt-open-roadmap/);
  assert.doesNotMatch(js, /role="menuitem">施工進度/);
  assert.doesNotMatch(js, />施工進度</);
  assert.doesNotMatch(js, /進度看板，開啟施工進度/);
  assert.doesNotMatch(js, /data-pt-prog-hit/);
  assert.doesNotMatch(js, /onProgressBoardRect/);
  assert.doesNotMatch(js, /onOpenProgress/);
  assert.doesNotMatch(js, /setRoadmapOpen/);
  assert.match(js, /setTasksOpen/);
  assert.doesNotMatch(js, /openRoadmapFromBoard/);
  assert.match(js, /openTasks/);
  assert.doesNotMatch(js, /location\.href = '\/console'/);
  assert.match(js, /data-pt-task-btn/);
  assert.match(js, /secretary-avatar\.webp/);
  assert.match(js, /私人女秘書/);
  assert.match(js, /data-pt-task-bar/);
  assert.doesNotMatch(js, /pt-hud-id-plate/);
  assert.doesNotMatch(js, /data-pt-identity-toggle/);
  assert.match(js, /data-pt-open-login/);
  assert.match(js, /wireHomeLoginModal/);
  assert.doesNotMatch(js, /role="menuitem">主控台/);
  assert.doesNotMatch(js, /\/login\?next=%2F/);
  assert.doesNotMatch(js, /開發機狀態/);
  assert.doesNotMatch(js, /pt-hud-card/);
  assert.doesNotMatch(js, /pt-hud-id-abbrev/);
  assert.doesNotMatch(js, /\/api\/dev-status/);
  assert.match(js, /\/api\/office\/public/);
  assert.match(css, /\.pt-hud-task/);
  assert.match(css, /\.pt-roadmap-open/);
  assert.doesNotMatch(css, /\.pt-prog-hit/);
});

test('首頁不再掛施工進度與新聞彈窗', () => {
  const sceneJs = readPrivateTeamSceneBundle();
  const officeJs = readFileSync(join(publicRoot, 'js/office-page.js'), 'utf8');
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.doesNotMatch(sceneJs, /progMesh\.name = 'progressBoard'/);
  assert.doesNotMatch(sceneJs, /onOpenProgress/);
  assert.doesNotMatch(sceneJs, /progressBoardScreenRect/);
  assert.match(sceneJs, /setSceneInputEnabled/);
  assert.doesNotMatch(sceneJs, /pickSecretary/);
  assert.doesNotMatch(officeJs, /createRoadmapController/);
  assert.doesNotMatch(officeJs, /createNewsController/);
  assert.doesNotMatch(officeJs, /applyRoadmapOpenParam/);
  assert.match(officeJs, /onChange/);
  assert.match(officeJs, /createHomeTasksController/);
  assert.match(officeJs, /applyTasksOpenParam/);
  assert.match(officeJs, /applyDownloadOpenParam/);
  assert.match(officeJs, /createOfficeModalManager/);
  assert.doesNotMatch(html, /id="roadmapBoard"/);
  assert.doesNotMatch(html, /id="newsBoard"/);
});

test('首頁無經絡研習室後備連結', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.doesNotMatch(html, /href="\/atlas\/tcm-3d\/" tabindex="0">/);
  assert.match(html, /href="\/login\?next=\/"[^>]*tabindex="-1"/);
  assert.match(html, /href="\/logout"[^>]*tabindex="-1"/);
});

test('首頁裝置浮動視窗結構', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.match(html, /id="tasksBoard"/);
  assert.match(html, /aria-label="裝置"/);
  assert.match(html, /<h2>裝置<\/h2>/);
  assert.match(html, /roadmap-close-x/);
  assert.match(html, /data-tasks-close/);
  assert.match(html, /id="panel-devices"/);
  assert.match(html, /data-label="裝置"/);
  assert.match(html, /PUNCHCLOCK_USERNAME/);
  assert.match(html, /id="clientContainer"/);
  // #765：dashboard.css/js 改為開啟裝置面板時才由 home-dashboard-loader 延後載入。
  assert.doesNotMatch(html, /dashboard\.css/);
  assert.doesNotMatch(html, /dashboard\.js/);
  const panel = readFileSync(join(publicRoot, 'js/home-tasks-panel.js'), 'utf8');
  assert.match(panel, /home-dashboard-loader/);
});