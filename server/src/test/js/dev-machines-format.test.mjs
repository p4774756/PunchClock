import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const F = await loadPublicModule('dev-machines-format.js');
const D = await loadPublicModule('dev-machines.js');

function miniDoc() {
  const nodes = [];
  function el(tag) {
    const n = {
      tagName: tag,
      className: '',
      classList: {
        _c: new Set(),
        add(x) {
          this._c.add(x);
          n.className = [...this._c].join(' ');
        },
        remove(x) {
          this._c.delete(x);
          n.className = [...this._c].join(' ');
        }
      },
      childNodes: [],
      attributes: {},
      style: {},
      textContent: '',
      hidden: false,
      href: '',
      firstChild: null,
      parentNode: null,
      appendChild(c) {
        this.childNodes.push(c);
        c.parentNode = this;
        if (!this.firstChild) this.firstChild = c;
        return c;
      },
      removeChild(c) {
        const i = this.childNodes.indexOf(c);
        if (i >= 0) this.childNodes.splice(i, 1);
        return c;
      },
      replaceChildren() {
        this.childNodes = [];
        this.firstChild = null;
      },
      querySelector(sel) {
        if (sel === '[data-dev-login-toast]') {
          return nodes.find((n) => n.attributes['data-dev-login-toast']) || null;
        }
        return null;
      },
      setAttribute(k, v) { this.attributes[k] = v; },
      getAttribute(k) { return this.attributes[k]; },
      removeAttribute(k) { delete this.attributes[k]; },
      addEventListener() {}
    };
    nodes.push(n);
    return n;
  }
  const body = el('body');
  return {
    createElement: el,
    body,
    querySelector(sel) {
      if (sel === '[data-dev-login-toast]') {
        return nodes.find((n) => n.attributes['data-dev-login-toast']) || null;
      }
      return null;
    },
    get nodes() { return nodes; }
  };
}

test('最後回報與已跑時間格式化', () => {
  const now = 10 * 60 * 60 * 1000;
  assert.equal(F.formatLastSeenAgo(null, now), '尚無回報');
  assert.equal(F.formatLastSeenAgo(now - 30 * 1000, now), '剛剛');
  assert.equal(F.formatRunDuration(now - 45 * 60 * 1000, now), '已跑 45 分鐘');
});

test('工作狀態：runs／閒置／Cursor／舊單線', () => {
  assert.equal(F.formatMachineWork({ online: true, runs: [{ issue: 1 }, { issue: 2 }] }).text, '2 條進行中');
  assert.equal(F.formatMachineWork({ online: true, agentRunning: false, issue: null }).text, '閒置');
  assert.equal(F.formatMachineWork({ online: true, agentRunning: true, issue: null }).text, 'Cursor 執行中（未標記工作）');
  const legacy = F.formatMachineWork({ online: true, issue: 172, agentRunning: false }, null);
  assert.equal(legacy.text, '等待 review・#172');
  assert.equal(F.formatRoundLabel(0), '初版');
  assert.equal(F.formatRoundLabel(3), '第 3 輪');
});

test('研發樓單行 runs 上限', () => {
  const lines = F.formatLabDevMachineLines({
    success: true,
    machines: [
      { label: '小貓', online: true, runs: [{ issue: 235 }, { issue: 237 }, { issue: 239 }, { issue: 241 }] }
    ]
  });
  assert.ok(lines[0].includes('跑 4 條（#235 #237 #239 …）'));
  const legacy = F.formatLabDevMachineLines({
    success: true,
    machines: [{ label: '小貓', online: true, issue: 126, agentRunning: true }]
  });
  assert.equal(legacy[0], '小貓 在線・跑 #126');
});

test('訪客與管理員連結／toast', () => {
  assert.equal(F.shouldFetchDevMachines(false), false);
  assert.equal(F.shouldFetchDevMachines(true), true);
  assert.equal(F.shouldShowDevStatusNavLink(false), true);

  const doc = miniDoc();
  const guest = F.createGithubLink(doc, { text: '#235', href: 'https://github.com/p4774756/PunchClock/issues/235', canLink: false });
  assert.equal(guest.tagName, 'span');
  assert.equal(guest.getAttribute('aria-disabled'), 'true');

  const admin = F.createGithubLink(doc, { text: '#235', href: 'https://github.com/p4774756/PunchClock/issues/235', canLink: true });
  assert.equal(admin.tagName, 'a');
  assert.ok(admin.href.includes('issues/235'));

  F.showLoginRequiredToast(doc, '/login');
  F.showLoginRequiredToast(doc, '/login');
  const toasts = doc.body.childNodes.filter((n) => n.attributes && n.attributes['data-dev-login-toast']);
  assert.equal(toasts.length, 1);
  assert.ok(toasts[0].className.includes('dev-login-toast'));
  assert.ok(toasts[0].className.includes('maple-window'));
  assert.ok(toasts[0].childNodes.some((c) => (c.textContent || '').includes('登入後才能查看連結')));
  assert.ok(toasts[0].childNodes.some((c) => c.href === '/login'));

  let clicked = 0;
  guest.addEventListener = (ev, fn) => {
    if (ev === 'click') clicked += 1;
  };
  guest.childNodes.forEach(() => {});
  F.createGithubLink(doc, { text: '#1', href: 'https://github.com/x/issues/1', canLink: false });
});

test('buildRunRows 與 renderCard 訪客無 href', () => {
  const doc = miniDoc();
  const now = 1_700_000_000_000;
  const ul = F.buildRunRows(doc, [{ issue: 235, round: 2, branch: 'ai/issue-235', startedAt: now - 60000, commit: 'abc1234567', issueTitle: '標題' }], now, { canLink: false });
  assert.equal(ul.className, 'dev-run-list');
  const flat = doc.nodes.map((n) => n.tagName + (n.href || '') + (n.textContent || '')).join('|');
  assert.ok(!flat.includes('github.com'));
  assert.ok(F.formatShortSha('abcdef123456') === 'abcdef1');

  const cardGuest = D.renderCard(doc, {
    label: '小貓',
    online: true,
    runs: [{ issue: 235, round: 0, branch: 'ai/issue-235', startedAt: now }],
    agentRunning: true
  }, now, { viewer: 'guest' });
  const guestNodes = doc.nodes.map((n) => n.tagName + (n.href || '')).join('|');
  assert.ok(!guestNodes.includes('github.com'));

  const cardAdmin = D.renderCard(doc, {
    label: '小貓',
    online: true,
    runs: [{ issue: 235, round: 0, branch: 'ai/issue-235', startedAt: now }],
    agentRunning: true
  }, now, { viewer: 'admin' });
  assert.ok(doc.nodes.some((n) => n.href && n.href.includes('github.com')));
});

function navWithCapture(doc) {
  const nav = doc.createElement('span');
  const listeners = new Map();
  const origCreate = doc.createElement.bind(doc);
  doc.createElement = (tag) => {
  const node = origCreate(tag);
    if (tag === 'a') {
      node.addEventListener = (ev, fn) => {
        if (!listeners.has(node)) listeners.set(node, {});
        listeners.get(node)[ev] = fn;
      };
    }
    return node;
  };
  return { nav, listeners, restore: () => { doc.createElement = origCreate; } };
}

test('roadmap 開發機狀態鈕與資料來源', () => {
  const doc = miniDoc();
  const nav = doc.createElement('span');
  F.mergeRoadmapDevStatusLink(doc, nav, { authenticated: true });
  assert.equal(nav.hidden, false);
  assert.equal(nav.childNodes[0].href, '/dev-status');
  const src = doc.createElement('a');
  src.className = 'roadmap-source';
  F.applyGithubSourceLabel(doc, src, 'https://github.com/p4774756/PunchClock/issues/36', false);
  assert.equal(src.textContent, '資料來源：GitHub #36');
  assert.equal(src.getAttribute('aria-disabled'), 'true');
  assert.ok(src.className.includes('dev-guest-ref'));
});

test('訪客點路線圖開發機狀態鈕跳 toast 不導頁', () => {
  const doc = miniDoc();
  const { nav, listeners, restore } = navWithCapture(doc);
  F.mergeRoadmapDevStatusLink(doc, nav, { authenticated: false });
  const link = nav.childNodes[0];
  assert.equal(link.href, '/dev-status');
  let prevented = false;
  const clickFn = listeners.get(link)?.click;
  assert.equal(typeof clickFn, 'function');
  clickFn({ preventDefault: () => { prevented = true; } });
  assert.equal(prevented, true);
  const toasts = doc.body.childNodes.filter((n) => n.attributes && n.attributes['data-dev-login-toast']);
  assert.equal(toasts.length, 1);
  assert.ok(toasts[0].childNodes.some((c) => (c.textContent || '') === '開發機狀態需登入後查看'));
  restore();
});

test('管理員點路線圖開發機狀態鈕可導頁', () => {
  const doc = miniDoc();
  const { nav, listeners, restore } = navWithCapture(doc);
  F.mergeRoadmapDevStatusLink(doc, nav, { authenticated: true });
  const link = nav.childNodes[0];
  assert.equal(listeners.get(link)?.click, undefined);
  restore();
});

test('open=roadmap 參數解析與深連結', () => {
  assert.equal(F.parseIslandOpenParam(''), '');
  assert.equal(F.parseIslandOpenParam('?open=roadmap'), 'roadmap');
  assert.equal(F.shouldOpenRoadmapFromSearch('?open=roadmap&x=1'), true);
  assert.equal(F.shouldOpenRoadmapFromSearch('?open=casino'), false);
  let opened = false;
  const win = {
    location: { href: 'http://localhost/?open=roadmap', search: '?open=roadmap', pathname: '/', hash: '' },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(F.applyIslandOpenRoadmapParam(win, () => { opened = true; }), true);
  assert.equal(opened, true);
  assert.equal(win._url, '/');
});

test('effectivePhaseStatus：0/5 pending、3/5 progress、5/5 done、current 0/5 active', () => {
  const phase = (n, done, total, extra) => Object.assign({ phase: n, done: false, itemsDone: done, itemsTotal: total }, extra);
  assert.equal(F.effectivePhaseStatus(phase(6, 0, 5), 7), 'pending');
  assert.equal(F.effectivePhaseStatus(phase(6, 3, 5), 7), 'progress');
  assert.equal(F.effectivePhaseStatus(phase(6, 5, 5), 7), 'done');
  assert.equal(F.effectivePhaseStatus(phase(7, 0, 5), 7), 'active');
  assert.equal(F.effectivePhaseStatus({ phase: 5, done: true, itemsDone: 2, itemsTotal: 5 }, null), 'done');
});

test('toast Esc 用 capture 關閉並 stopPropagation', () => {
  const keyHandlers = [];
  const doc = {
    body: {
      childNodes: [],
      appendChild(c) { this.childNodes.push(c); },
      removeChild(c) {
        const i = this.childNodes.indexOf(c);
        if (i >= 0) this.childNodes.splice(i, 1);
      }
    },
    addEventListener(ev, fn, cap) {
      if (ev === 'keydown') keyHandlers.push({ fn, cap: !!cap });
    },
    removeEventListener(ev, fn) {
      if (ev !== 'keydown') return;
      const i = keyHandlers.findIndex((h) => h.fn === fn);
      if (i >= 0) keyHandlers.splice(i, 1);
    },
    createElement() {
      return {
        className: '',
        classList: { _c: new Set(), add(x) { this._c.add(x); }, remove(x) { this._c.delete(x); } },
        childNodes: [],
        attributes: {},
        parentNode: doc.body,
        appendChild(c) { this.childNodes.push(c); return c; },
        setAttribute(k, v) { this.attributes[k] = v; },
        addEventListener() {},
        removeChild(c) {
          const i = this.childNodes.indexOf(c);
          if (i >= 0) this.childNodes.splice(i, 1);
        }
      };
    },
    querySelector(sel) {
      if (sel === '[data-dev-login-toast]') {
        return doc.body.childNodes.find((n) => n.attributes && n.attributes['data-dev-login-toast']) || null;
      }
      return null;
    }
  };
  F.showLoginRequiredToast(doc, '/login');
  assert.equal(F.isLoginRequiredToastOpen(doc), true);
  assert.ok(keyHandlers.some((h) => h.cap));
  const ev = {
    key: 'Escape',
    stopPropagation() { this.stopped = true; },
    preventDefault() { this.prevented = true; }
  };
  const cap = keyHandlers.find((h) => h.cap);
  cap.fn(ev);
  assert.equal(ev.stopped, true);
  assert.equal(F.isLoginRequiredToastOpen(doc), false);
});

test('今日合併篩選接受 API 合併與已合併', () => {
  const now = Date.UTC(2026, 10, 19, 8, 6, 0);
  const merged = F.filterMergedToday(
    [{ action: '合併', at: Date.UTC(2026, 10, 19, 8, 6, 0), ref: '#205' }],
    now
  );
  assert.equal(merged.length, 1);
});