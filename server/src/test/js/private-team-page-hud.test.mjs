import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const { taskProgressFromTiger } = await loadPublicModule('private-team-office.js');
const {
  deployVersionLabel,
  showGuestMonitorHintToast,
  dismissGuestMonitorHintToast,
  GUEST_MONITOR_HINT_TEXT,
  GUEST_MONITOR_HINT_MS,
  GUEST_MONITOR_HINT_TOAST_CLASS,
  GUEST_MONITOR_HINT_PULSE_CLASS,
  syncPrivateTeamPageViewport,
  bindPrivateTeamPageViewport
} = await loadPublicModule('private-team-page.js');

test('私人女秘書 HUD 標記與身分文案', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /class="pt-hud-task"[^>]*data-pt-task-btn/);
  assert.match(js, /data-pt-secretary-avatar/);
  assert.match(js, /secretary-avatar\.webp/);
  assert.match(js, /alt="" aria-hidden="true"/);
  assert.match(js, /data-pt-auth-label/);
  assert.match(js, /訪客・點此登入/);
  assert.match(js, /data-pt-task-bar/);
  assert.match(js, /今日完成/);
  assert.doesNotMatch(js, /pt-a-badge/);
  assert.doesNotMatch(js, /data-pt-identity-toggle/);
  assert.doesNotMatch(js, /pt-hud-tr/);
  assert.match(js, /taskProgressFromTiger/);
  assert.doesNotMatch(js, /先登入才能看任務/);
  assert.doesNotMatch(js, /data-pt-task-hint/);
  assert.match(js, /data-pt-auth-menu/);
  assert.match(js, /pt-hud-id-logout/);
  assert.match(js, /win\.PUNCHCLOCK_USERNAME/);
  assert.match(js, /target === 'monitor'/);
  assert.match(js, /showGuestMonitorHintToast/);
  assert.match(js, /請先從左上角登入/);
  assert.match(js, /if \(target === 'monitor'\)[\s\S]*showGuestMonitorHintToast/);
  assert.doesNotMatch(js, /if \(target === 'monitor'\)[\s\S]*pendingOpenTasksAfterLogin = true/);
  assert.doesNotMatch(js, /pt-hud-identity/);
  assert.doesNotMatch(js, /AUTH_ABBREV/);
  assert.match(js, /data-pt-open-download/);
  assert.match(js, /aria-label="下載 App"/);
  assert.match(js, /title="下載 App"/);
  assert.doesNotMatch(js, /pt-hud-dl-label/);
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.doesNotMatch(css, /pt-hud-dl-label/);
  assert.doesNotMatch(js, /href="\/download"/);
  assert.doesNotMatch(js, /data-pt-daynight/);
  assert.doesNotMatch(js, /punchclock-private-team-day/);
});

function miniDocForGuestHint() {
  const nodes = [];
  function el(tag) {
    const n = {
      tagName: String(tag).toUpperCase(),
      className: '',
      childNodes: [],
      children: [],
      attributes: {},
      textContent: '',
      parentNode: null,
      classList: {
        add(c) {
          const parts = n.className.split(/\s+/).filter(Boolean);
          if (!parts.includes(c)) parts.push(c);
          n.className = parts.join(' ');
        },
        remove(c) {
          n.className = n.className.split(/\s+/).filter((x) => x && x !== c).join(' ');
        },
        contains(c) {
          return n.className.split(/\s+/).includes(c);
        }
      },
      removeChild(c) {
        const i = this.childNodes.indexOf(c);
        if (i >= 0) this.childNodes.splice(i, 1);
        const j = this.children.indexOf(c);
        if (j >= 0) this.children.splice(j, 1);
        c.parentNode = null;
        c.parent = null;
        return c;
      },
      appendChild(c) {
        this.childNodes.push(c);
        this.children.push(c);
        c.parentNode = this;
        c.parent = this;
        return c;
      },
      addEventListener() {},
      setAttribute(k, v) {
        this.attributes[k] = v;
      },
      getAttribute(k) {
        return this.attributes[k];
      }
    };
    nodes.push(n);
    return n;
  }
  const body = el('body');
  const badge = el('button');
  badge.className = 'pt-hud-task';
  badge.setAttribute('data-pt-task-btn', '');
  return {
    createElement: el,
    body,
    badge,
    nodes,
    addEventListener() {},
    removeEventListener() {},
    querySelector(sel) {
      if (sel === '[data-pt-guest-monitor-hint]') {
        return nodes.find((n) => n.attributes['data-pt-guest-monitor-hint']) || null;
      }
      if (sel === '[data-pt-task-btn]') {
        return nodes.find((n) => Object.prototype.hasOwnProperty.call(n.attributes, 'data-pt-task-btn')) || null;
      }
      return null;
    }
  };
}

test('訪客螢幕提示 toast', () => {
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.match(css, /\.pt-guest-hint-toast/);
  assert.match(css, /background:\s*linear-gradient\([^)]*#163a70[^)]*#102548\)/);
  assert.match(css, /\.pt-guest-hint-toast[^{]*\{[^}]*border:\s*2px solid #c9a64a/);
  assert.match(css, /\.pt-guest-hint-toast[^{]*\{[^}]*color:\s*#f8fbff/);
  assert.match(css, /\.pt-guest-hint-toast[^{]*\{[^}]*top:\s*max\(/);
  assert.match(css, /\.pt-guest-hint-toast[^{]*\{[^}]*bottom:\s*auto/);
  assert.doesNotMatch(
    css.slice(css.indexOf('.pt-guest-hint-toast')),
    /var\(--maple-(?:white|ink|navy)\)(?!,)/
  );

  const timers = [];
  let nextId = 1;
  const win = {
    setTimeout(fn, ms) {
      const id = nextId++;
      timers.push({ id, fn, ms, cleared: false });
      return id;
    },
    clearTimeout(id) {
      const t = timers.find((x) => x.id === id);
      if (t) t.cleared = true;
    }
  };

  const doc = miniDocForGuestHint();
  const wrap = showGuestMonitorHintToast(doc, win);
  assert.ok(wrap);
  assert.equal(wrap.attributes['data-pt-guest-monitor-hint'], '1');
  assert.equal(wrap.attributes.role, 'status');
  assert.equal(wrap.attributes['aria-live'], 'polite');
  assert.ok((wrap.className || '').includes(GUEST_MONITOR_HINT_TOAST_CLASS));
  const msg = wrap.childNodes.find((c) => (c.className || '').includes('dev-login-toast-msg'));
  assert.equal(msg.textContent, GUEST_MONITOR_HINT_TEXT);
  assert.equal(timers.length, 1);
  assert.equal(timers[0].ms, GUEST_MONITOR_HINT_MS);
  assert.equal(GUEST_MONITOR_HINT_MS, 4000);
  assert.ok(doc.badge.classList.contains(GUEST_MONITOR_HINT_PULSE_CLASS));

  const firstFn = timers[0].fn;
  showGuestMonitorHintToast(doc, win);
  assert.equal(timers[0].cleared, true);
  assert.equal(doc.body.childNodes.length, 1);
  firstFn();
  assert.equal(doc.body.childNodes.length, 1);
  assert.ok(doc.badge.classList.contains(GUEST_MONITOR_HINT_PULSE_CLASS));

  dismissGuestMonitorHintToast(doc);
  assert.equal(doc.body.childNodes.length, 0);
  assert.equal(timers[1].cleared, true);
  assert.equal(doc.badge.classList.contains(GUEST_MONITOR_HINT_PULSE_CLASS), false);
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
  assert.match(html, /viewport-fit=cover/);
  assert.match(js, /syncPrivateTeamPageViewport/);
  assert.match(js, /bindPrivateTeamPageViewport/);
  assert.match(css, /\.private-team-page\.lab-office-root\.is-page[\s\S]*justify-content:\s*flex-start/);
  assert.match(css, /--pt-vv-height/);
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

test('visualViewport 同步設定首頁高度與頂部偏移', () => {
  const root = { style: { setProperty() {}, removeProperty() {} } };
  const calls = [];
  root.style.setProperty = (k, v) => calls.push([k, v]);
  const win = {
    innerHeight: 800,
    visualViewport: { height: 700, offsetTop: 44, addEventListener() {}, removeEventListener() {} },
    addEventListener() {},
    removeEventListener() {}
  };
  syncPrivateTeamPageViewport({ getElementById() {} }, win, root);
  assert.ok(calls.some(([k, v]) => k === '--pt-vv-height' && v === '700px'));
  assert.ok(calls.some(([k, v]) => k === '--pt-vv-offset-top' && v === '44px'));
  calls.length = 0;
  const unbind = bindPrivateTeamPageViewport({ getElementById() {} }, win, root);
  assert.ok(calls.length >= 2);
  unbind();
});