import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const F = await loadPublicModule('dev-machines-format.js');

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
  assert.equal(F.shouldShowDevStatusNavLink(), false);

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

test('buildRunRows 訪客無 href', () => {
  const doc = miniDoc();
  const now = 1_700_000_000_000;
  const ul = F.buildRunRows(doc, [{ issue: 235, round: 2, branch: 'ai/issue-235', startedAt: now - 60000, commit: 'abc1234567', issueTitle: '標題' }], now, { canLink: false });
  assert.equal(ul.className, 'dev-run-list');
  const flat = doc.nodes.map((n) => n.tagName + (n.href || '') + (n.textContent || '')).join('|');
  assert.ok(!flat.includes('github.com'));
  assert.ok(F.formatShortSha('abcdef123456') === 'abcdef1');
});

test('roadmap 不再顯示開發機狀態鈕', () => {
  const doc = miniDoc();
  const nav = doc.createElement('span');
  F.mergeRoadmapDevStatusLink(doc, nav, { authenticated: true });
  assert.equal(nav.hidden, true);
  assert.equal(nav.childNodes.length, 0);
  const src = doc.createElement('a');
  src.className = 'roadmap-source';
  F.applyGithubSourceLabel(doc, src, 'https://github.com/p4774756/PunchClock/milestones', false);
  assert.equal(src.textContent, '資料來源：GitHub Milestones');
  assert.equal(src.getAttribute('aria-disabled'), 'true');
  assert.ok(src.className.includes('dev-guest-ref'));
});

test('open=roadmap 參數解析與深連結', () => {
  assert.equal(F.parseRoadmapOpenParam(''), '');
  assert.equal(F.parseRoadmapOpenParam('?open=roadmap'), 'roadmap');
  assert.equal(F.shouldOpenRoadmapFromSearch('?open=roadmap&x=1'), true);
  assert.equal(F.shouldOpenRoadmapFromSearch('?open=nope'), false);
  let opened = false;
  const win = {
    location: { href: 'http://localhost/?open=roadmap', search: '?open=roadmap', pathname: '/', hash: '' },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(F.applyRoadmapOpenParam(win, () => { opened = true; }), true);
  assert.equal(opened, true);
  assert.equal(win._url, '/');
});

test('open=tasks 參數解析與深連結', () => {
  assert.equal(F.shouldOpenTasksFromSearch('?open=tasks'), true);
  assert.equal(F.shouldOpenTasksFromSearch('?open=roadmap'), false);
  let opened = false;
  const win = {
    location: { href: 'http://localhost/?open=tasks', search: '?open=tasks', pathname: '/', hash: '' },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(F.applyTasksOpenParam(win, () => { opened = true; }), true);
  assert.equal(opened, true);
  assert.equal(win._url, '/');
});

test('open=download 參數解析與深連結', () => {
  assert.equal(F.shouldOpenDownloadFromSearch('?open=download'), true);
  let opened = false;
  const win = {
    location: { href: 'http://localhost/?open=download', search: '?open=download', pathname: '/', hash: '' },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(F.applyDownloadOpenParam(win, () => { opened = true; }), true);
  assert.equal(opened, true);
  assert.equal(win._url, '/');
});

test('open=atlas-login 參數解析與深連結', () => {
  assert.equal(F.shouldOpenAtlasLoginFromSearch('?open=atlas-login'), true);
  assert.equal(F.shouldOpenAtlasLoginFromSearch('?open=tasks'), false);
  let login = false;
  let atlas = false;
  const win = {
    location: {
      href: 'http://localhost/?open=atlas-login',
      search: '?open=atlas-login',
      pathname: '/',
      hash: ''
    },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(
    F.applyAtlasLoginOpenParam(win, {
      isAuthed: () => false,
      openLoginForAtlas: () => { login = true; },
      openAtlas: () => { atlas = true; }
    }),
    true
  );
  assert.equal(login, true);
  assert.equal(atlas, false);
  assert.equal(win._url, '/');
  atlas = false;
  win.location.search = '?open=atlas-login';
  assert.equal(
    F.applyAtlasLoginOpenParam(win, {
      isAuthed: () => true,
      openLoginForAtlas: () => { login = true; },
      openAtlas: () => { atlas = true; }
    }),
    true
  );
  assert.equal(atlas, true);
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
    removeEventListener(ev, fn, cap) {
      if (ev !== 'keydown') return;
      const capture = !!cap;
      const i = keyHandlers.findIndex((h) => h.fn === fn && h.cap === capture);
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
  assert.equal(keyHandlers.length, 0);
});

function docWithKeydownCapture() {
  const keyHandlers = [];
  const doc = {
    body: {
      childNodes: [],
      appendChild(c) { this.childNodes.push(c); c.parentNode = this; },
      removeChild(c) {
        const i = this.childNodes.indexOf(c);
        if (i >= 0) this.childNodes.splice(i, 1);
        c.parentNode = null;
      }
    },
    addEventListener(ev, fn, cap) {
      if (ev === 'keydown') keyHandlers.push({ fn, cap: !!cap });
    },
    removeEventListener(ev, fn, cap) {
      if (ev !== 'keydown') return;
      const capture = !!cap;
      const i = keyHandlers.findIndex((h) => h.fn === fn && h.cap === capture);
      if (i >= 0) keyHandlers.splice(i, 1);
    },
    createElement(tag) {
      const node = {
        tagName: tag,
        type: tag === 'button' ? 'button' : undefined,
        className: '',
        classList: { _c: new Set(), add(x) { this._c.add(x); } },
        childNodes: [],
        attributes: {},
        textContent: '',
        href: '',
        parentNode: null,
        appendChild(c) { this.childNodes.push(c); c.parentNode = this; return c; },
        setAttribute(k, v) { this.attributes[k] = v; },
        addEventListener(ev, fn) {
          if (!node._ev) node._ev = {};
          node._ev[ev] = fn;
        }
      };
      return node;
    },
    querySelector(sel) {
      if (sel === '[data-dev-login-toast]') {
        return doc.body.childNodes.find((n) => n.attributes && n.attributes['data-dev-login-toast']) || null;
      }
      return null;
    }
  };
  const fireEscape = () => {
    const h = keyHandlers[0];
    const ev = {
      key: 'Escape',
      stopPropagation() { ev.stopped = true; },
      preventDefault() { ev.prevented = true; }
    };
    if (h) h.fn(ev);
    return ev;
  };
  const toastWrap = () => doc.body.childNodes.find((n) => n.attributes && n.attributes['data-dev-login-toast']);
  const closeBtn = () => {
    const w = toastWrap();
    return w && w.childNodes.find((c) => c.tagName === 'button');
  };
  return { doc, keyHandlers, fireEscape, toastWrap, closeBtn };
}

test('toast 關閉後 Esc 監聽移除且不攔截後續 Esc', () => {
  const { doc, keyHandlers, fireEscape } = docWithKeydownCapture();
  F.showLoginRequiredToast(doc, '/login');
  assert.equal(keyHandlers.length, 1);
  const first = fireEscape();
  assert.equal(first.stopped, true);
  assert.equal(keyHandlers.length, 0);
  const second = fireEscape();
  assert.notEqual(second.stopped, true);
});

test('toast × 關閉與逾時關閉後無 keydown 監聽', async () => {
  const { doc, keyHandlers, closeBtn } = docWithKeydownCapture();
  F.showLoginRequiredToast(doc, '/login');
  assert.equal(keyHandlers.length, 1);
  closeBtn()._ev.click({});
  assert.equal(keyHandlers.length, 0);
  assert.equal(F.isLoginRequiredToastOpen(doc), false);

  F.showLoginRequiredToast(doc, '/login');
  assert.equal(keyHandlers.length, 1);
  await new Promise((r) => setTimeout(r, 4100));
  assert.equal(keyHandlers.length, 0);
  assert.equal(F.isLoginRequiredToastOpen(doc), false);
});

test('連續開兩次 toast 關閉後 keydown 監聽為 0', () => {
  const { doc, keyHandlers, fireEscape } = docWithKeydownCapture();
  F.showLoginRequiredToast(doc, '/login');
  F.showLoginRequiredToast(doc, '/login');
  assert.equal(keyHandlers.length, 1);
  fireEscape();
  assert.equal(keyHandlers.length, 0);
  const second = fireEscape();
  assert.notEqual(second.stopped, true);
});

test('今日合併篩選接受 API 合併與已合併', () => {
  const now = Date.UTC(2026, 10, 19, 8, 6, 0);
  const merged = F.filterMergedToday(
    [{ action: '合併', at: Date.UTC(2026, 10, 19, 8, 6, 0), ref: '#205' }],
    now
  );
  assert.equal(merged.length, 1);
});

test('施工進度搜尋：i349／p373／中文／找不到', () => {
  const item349 = { text: '移除 App 21 點', issue: 349, pr: null, done: false };
  const item339 = { text: '某單', issue: 339, pr: 373, done: true };
  const q349 = F.parseRoadmapSearchQuery('i349');
  const q373 = F.parseRoadmapSearchQuery('p373');
  const qText = F.parseRoadmapSearchQuery('移除 App');
  const qMiss = F.parseRoadmapSearchQuery('zzz999');
  assert.equal(F.roadmapItemMatchesSearch(q349, item349), true);
  assert.equal(F.roadmapItemMatchesSearch(q349, item339), false);
  assert.equal(F.roadmapItemMatchesSearch(q373, item339), true);
  assert.equal(F.roadmapItemMatchesSearch(q373, item349), false);
  assert.equal(F.roadmapItemMatchesSearch(qText, item349), true);
  assert.equal(F.roadmapItemMatchesSearch(qMiss, item349), false);
});