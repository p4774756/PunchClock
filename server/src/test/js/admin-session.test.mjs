import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

function memoryStorage() {
  const data = new Map();
  return {
    getItem(key) {
      return data.has(key) ? data.get(key) : null;
    },
    setItem(key, value) {
      data.set(String(key), String(value));
    },
    removeItem(key) {
      data.delete(String(key));
    }
  };
}

function mockWin(overrides = {}) {
  const store = overrides.sessionStorage || memoryStorage();
  const calls = [];
  const win = {
    PUNCHCLOCK_CSRF: '',
    PUNCHCLOCK_AUTHENTICATED: true,
    PUNCHCLOCK_USERNAME: 'root',
    sessionStorage: store,
    location: {
      href: '/office',
      replace(url) {
        this.href = url;
        this.replaced = url;
      }
    },
    fetch(url, init) {
      calls.push({ url, init });
      const handler = win.fetchImpl || (() => Promise.resolve({ ok: true, status: 200, json: async () => ({}) }));
      return handler(url, init);
    },
    ...overrides,
    sessionStorage: store
  };
  win._calls = calls;
  return win;
}

const {
  CSRF_STORAGE_KEY,
  persistCsrf,
  csrfHeaderValue,
  hydrateCsrf,
  adminWriteHeaders,
  fetchCsrfFromSession,
  clearClientAuth,
  postLogout,
  wireLogoutLinks
} = await loadPublicModule('admin-session.js');

test('persistCsrf 寫入記憶體與 sessionStorage，hydrate 可還原', () => {
  const win = mockWin();
  persistCsrf(win, '  token-abc  ');
  assert.equal(win.PUNCHCLOCK_CSRF, 'token-abc');
  assert.equal(win.sessionStorage.getItem(CSRF_STORAGE_KEY), 'token-abc');
  assert.equal(csrfHeaderValue(win), 'token-abc');
  assert.deepEqual(adminWriteHeaders(win), { 'X-Csrf-Token': 'token-abc' });

  win.PUNCHCLOCK_CSRF = '';
  assert.equal(hydrateCsrf(win), 'token-abc');
  assert.equal(win.PUNCHCLOCK_CSRF, 'token-abc');
});

test('postLogout 缺 token 時先向 session 端點補拿再 POST', { concurrency: false }, async () => {
  const win = mockWin();
  win.fetchImpl = (url, init) => {
    if (url === '/api/auth/session') {
      return Promise.resolve({
        ok: true,
        status: 200,
        json: async () => ({ authenticated: true, username: 'root', csrfToken: 'from-session' })
      });
    }
    if (url === '/logout') {
      assert.equal(init.method, 'POST');
      assert.equal(init.headers['X-Csrf-Token'], 'from-session');
      assert.equal(init.headers.Accept, 'application/json');
      assert.equal(init.credentials, 'same-origin');
      return Promise.resolve({ ok: true, status: 200, json: async () => ({ success: true }) });
    }
    throw new Error('unexpected ' + url);
  };
  await postLogout(win);
  assert.equal(win._calls[0].url, '/api/auth/session');
  assert.equal(win._calls[1].url, '/logout');
  assert.equal(win.location.replaced, '/');
  assert.equal(win.PUNCHCLOCK_AUTHENTICATED, false);
  assert.equal(win.PUNCHCLOCK_CSRF, '');
  assert.equal(win.sessionStorage.getItem(CSRF_STORAGE_KEY), null);
});

test('postLogout 即使 403 也清掉前端狀態並回到首頁', { concurrency: false }, async () => {
  const win = mockWin({ PUNCHCLOCK_CSRF: 'stale-token' });
  persistCsrf(win, 'stale-token');
  const body = { classList: { removed: [], added: [], remove(c) { this.removed.push(c); }, add(c) { this.added.push(c); } } };
  win.document = { body };
  win.fetchImpl = (url) => {
    if (url === '/logout') {
      return Promise.resolve({ ok: false, status: 403, json: async () => ({ message: 'CSRF 驗證失敗' }) });
    }
    throw new Error('unexpected ' + url);
  };
  await postLogout(win);
  assert.equal(win._calls.length, 1);
  assert.equal(win._calls[0].url, '/logout');
  assert.equal(win.location.replaced, '/');
  assert.equal(win.PUNCHCLOCK_AUTHENTICATED, false);
  assert.equal(win.PUNCHCLOCK_USERNAME, '');
  assert.equal(win.PUNCHCLOCK_CSRF, '');
  assert.deepEqual(body.classList.removed, ['is-authenticated']);
  assert.deepEqual(body.classList.added, ['is-guest']);
});

test('postLogout 在 fetch 失敗時仍回到未登入畫面', { concurrency: false }, async () => {
  const win = mockWin();
  persistCsrf(win, 'token-abc');
  win.fetchImpl = () => Promise.reject(new Error('network'));
  await postLogout(win);
  assert.equal(win.location.replaced, '/');
  assert.equal(win.PUNCHCLOCK_AUTHENTICATED, false);
});

test('wireLogoutLinks 點 data-pt-logout 會 preventDefault 並登出', { concurrency: false }, async () => {
  const win = mockWin();
  persistCsrf(win, 'token-abc');
  const listeners = [];
  const button = {
    closest(sel) {
      return sel.includes('data-pt-logout') ? button : null;
    }
  };
  const doc = {
    addEventListener(type, fn, opts) {
      listeners.push({ type, fn, opts });
    }
  };
  win.fetchImpl = (url) => {
    if (url === '/logout') {
      return Promise.resolve({ ok: false, status: 401, json: async () => ({}) });
    }
    throw new Error('unexpected ' + url);
  };
  wireLogoutLinks(win, doc);
  assert.equal(listeners.length, 1);
  assert.equal(listeners[0].type, 'click');
  assert.equal(listeners[0].opts, true);
  let prevented = false;
  listeners[0].fn({
    target: button,
    preventDefault() { prevented = true; }
  });
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(prevented, true);
  assert.equal(win.location.replaced, '/');
});

test('fetchCsrfFromSession 未登入不寫 token', { concurrency: false }, async () => {
  const win = mockWin();
  win.fetchImpl = () => Promise.resolve({
    ok: true,
    status: 200,
    json: async () => ({ authenticated: false })
  });
  const token = await fetchCsrfFromSession(win);
  assert.equal(token, '');
  assert.equal(win.PUNCHCLOCK_CSRF, '');
});

test('clearClientAuth 清旗標與儲存', () => {
  const win = mockWin();
  persistCsrf(win, 'keep-me');
  const body = { classList: { remove() {}, add() {} } };
  win.document = { body };
  clearClientAuth(win);
  assert.equal(win.PUNCHCLOCK_AUTHENTICATED, false);
  assert.equal(csrfHeaderValue(win), '');
  assert.equal(win.sessionStorage.getItem(CSRF_STORAGE_KEY), null);
});
