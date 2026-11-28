import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync, statSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { readPrivateTeamSceneBundle } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

function fileSize(rel) {
  return statSync(join(publicRoot, rel)).size;
}

function sumSizes(rels) {
  let total = 0;
  for (let i = 0; i < rels.length; i++) total += fileSize(rels[i]);
  return total;
}

const CRITICAL_HTML_JS_CSS = [
  'index.html',
  'css/lab-office.css',
  'css/dev-guest-ui.css',
  'css/private-team.css',
  'js/device-fold.js',
  'js/device-head-badge.js',
  'js/office-page.js'
];

const DEFERRED_FIRST_PAINT = [
  'css/roadmap.css',
  'css/login.css',
  'css/download.css',
  'js/vendor/three.module.min.js'
];

const ALL_WINDOW_WEBP = [
  'assets/private-team/window/taipei-window-day.webp',
  'assets/private-team/window/taipei-window-sunset.webp',
  'assets/private-team/window/taipei-window-night.webp'
];

test('index 首屏不含非關鍵 CSS 的 blocking link', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const head = html.slice(0, html.indexOf('</head>'));
  assert.doesNotMatch(head, /href="[^"]*roadmap\.css/);
  assert.doesNotMatch(head, /href="[^"]*login\.css/);
  assert.doesNotMatch(head, /href="[^"]*download\.css/);
  assert.doesNotMatch(head, /type="importmap"/);
});

test('彈窗 CSS 改由 home-panel-css-loader 延後載入', () => {
  const loader = readFileSync(join(publicRoot, 'js/home-panel-css-loader.js'), 'utf8');
  assert.match(loader, /ensureHomeRoadmapCss/);
  assert.match(loader, /ensureHomeLoginCss/);
  assert.match(loader, /ensureHomeDownloadCss/);
  assert.match(readFileSync(join(publicRoot, 'js/home-login-modal.js'), 'utf8'), /ensureHomeLoginCss/);
  assert.match(readFileSync(join(publicRoot, 'js/home-download-panel.js'), 'utf8'), /ensureHomeDownloadCss/);
  assert.match(readFileSync(join(publicRoot, 'js/home-tasks-panel.js'), 'utf8'), /ensureHomeRoadmapCss/);
});

test('3D 場景與 Three 延後載入，不擋首屏 HUD', () => {
  const page = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.doesNotMatch(page, /from '\.\/private-team-scene\.js'/);
  assert.match(page, /import\('\.\/private-team-scene\.js'\)/);
  assert.match(page, /requestIdleCallback|setTimeout/);
  const sceneBundle = readPrivateTeamSceneBundle();
  assert.match(sceneBundle, /ensureWindowVistaTexture/);
  assert.doesNotMatch(sceneBundle, /vistaPeriodKeys/);
});

test('窗景 webp 僅在切換時期才透過 ensure 發請求', () => {
  const sceneBundle = readPrivateTeamSceneBundle();
  assert.match(sceneBundle, /ensureWindowVistaTexture\(texLoader, vistaTex, windowVistaPeriod\)/);
  assert.match(sceneBundle, /ensureWindowVistaTexture\(texLoader, vistaTex, period\)/);
});

test('首頁關鍵路徑傳輸量低於瘦身前基準（檔案加總）', () => {
  const critical = sumSizes(CRITICAL_HTML_JS_CSS);
  const deferredBlocking = sumSizes(DEFERRED_FIRST_PAINT);
  const oneWindow = fileSize('assets/private-team/window/taipei-window-day.webp');
  const threeWindows = sumSizes(ALL_WINDOW_WEBP);
  const windowSavings = threeWindows - oneWindow;
  const afterFirstPaint = critical + oneWindow;
  const beforeApprox = critical + deferredBlocking + threeWindows;
  assert.ok(windowSavings > 200_000, '應少載至少兩張窗景 webp');
  assert.ok(deferredBlocking > 600_000, 'Three 仍應存在但延後');
  assert.ok(afterFirstPaint < beforeApprox - 650_000, '關鍵路徑應明顯小於含 Three+三窗景+非關鍵 CSS');
});