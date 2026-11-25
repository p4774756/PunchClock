import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const { taskProgressFromTiger } = await loadPublicModule('private-team-office.js');
const { deployVersionLabel } = await loadPublicModule('private-team-page.js');

test('私人女秘書 HUD 標記與身分文案', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /class="pt-hud-task"[^>]*data-pt-task-btn/);
  assert.match(js, /<img src="\/assets\/private-team\/secretary-avatar\.webp"/);
  assert.match(js, /alt="" aria-hidden="true"/);
  assert.match(js, /<span class="pt-a-name">私人女秘書<\/span>/);
  assert.match(js, /data-pt-task-bar/);
  assert.match(js, /今日完成/);
  assert.doesNotMatch(js, /pt-a-badge/);
  assert.doesNotMatch(js, /pt-hud-id-plate/);
  assert.doesNotMatch(js, /data-pt-identity-toggle/);
  assert.doesNotMatch(js, /pt-hud-tr/);
  assert.match(js, /taskProgressFromTiger/);
  assert.match(js, /data-pt-task-hint-close/);
  assert.match(js, /pt-hud-task-hint-close/);
  assert.match(js, /先登入才能看任務/);
  assert.match(js, /data-pt-open-login/);
  assert.match(js, /登入/);
  assert.doesNotMatch(js, /pt-hud-identity/);
  assert.doesNotMatch(js, /AUTH_ABBREV/);
  assert.match(js, /data-pt-open-download/);
  assert.doesNotMatch(js, /href="\/download"/);
  assert.doesNotMatch(js, /data-pt-daynight/);
  assert.doesNotMatch(js, /punchclock-private-team-day/);
});

test('任務細條僅在有公開計數時顯示', () => {
  assert.equal(taskProgressFromTiger({ active: [], completedToday: [] }), null);
  const onlyActive = taskProgressFromTiger({ active: [{ issue: 1 }], completedToday: [] });
  assert.deepEqual(onlyActive, { completed: 0, total: 1, fraction: 0 });
});

test('左下部署版本列僅標明網站與時間', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.match(js, /網站 v/);
  assert.doesNotMatch(js, /App v/);
  assert.match(html, /PUNCHCLOCK_CLIENT_VERSION/);
  assert.match(css, /\.pt-hud-deploy-ver[\s\S]*font-size:\s*0\.72rem/);
  assert.match(css, /\.pt-hud-deploy-ver[\s\S]*rgba\(248,\s*251,\s*255,\s*0\.78\)/);
  assert.match(css, /\.pt-hud-deploy-ver[\s\S]*white-space:\s*nowrap/);
  assert.match(
    css,
    /@media \(max-width:\s*520px\)[\s\S]*\.pt-hud-deploy-ver[\s\S]*white-space:\s*normal/
  );
  const win = {
    PUNCHCLOCK_SERVER_VERSION: '4.0.0',
    PUNCHCLOCK_SERVER_BUILD_TIME: '2026-11-23 20:16',
    PUNCHCLOCK_CLIENT_VERSION: '4.0.2'
  };
  assert.equal(deployVersionLabel(win), '網站 v4.0.0 · 2026-11-23 20:16');
  assert.equal(deployVersionLabel(win, { compact: true }), '網站 v4.0.0 · 11-23 20:16');
  assert.equal(
    deployVersionLabel({ ...win, PUNCHCLOCK_CLIENT_VERSION: '' }),
    '網站 v4.0.0 · 2026-11-23 20:16'
  );
  assert.equal(
    deployVersionLabel({ ...win, PUNCHCLOCK_CLIENT_VERSION: '' }, { compact: true }),
    '網站 v4.0.0 · 11-23 20:16'
  );
});