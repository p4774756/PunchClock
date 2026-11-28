/** 站長 CSRF 與登出 POST。 */

export const CSRF_STORAGE_KEY = 'punchclock.csrf';

function storage(win) {
  try {
    return win && win.sessionStorage ? win.sessionStorage : null;
  } catch (_e) {
    return null;
  }
}

export function persistCsrf(win, token) {
  const w = win || globalThis;
  const value = typeof token === 'string' ? token.trim() : '';
  if (!value) return '';
  w.PUNCHCLOCK_CSRF = value;
  const store = storage(w);
  if (store && typeof store.setItem === 'function') {
    try {
      store.setItem(CSRF_STORAGE_KEY, value);
    } catch (_e) { /* 私密模式等 */ }
  }
  return value;
}

export function clearCsrf(win) {
  const w = win || globalThis;
  w.PUNCHCLOCK_CSRF = '';
  const store = storage(w);
  if (store && typeof store.removeItem === 'function') {
    try {
      store.removeItem(CSRF_STORAGE_KEY);
    } catch (_e) { /* ignore */ }
  }
}

export function csrfHeaderValue(win) {
  const w = win || globalThis;
  const mem = w.PUNCHCLOCK_CSRF;
  if (typeof mem === 'string' && mem.trim()) {
    return mem.trim();
  }
  const store = storage(w);
  if (store && typeof store.getItem === 'function') {
    try {
      const saved = store.getItem(CSRF_STORAGE_KEY);
      if (typeof saved === 'string' && saved.trim()) {
        w.PUNCHCLOCK_CSRF = saved.trim();
        return saved.trim();
      }
    } catch (_e) { /* ignore */ }
  }
  return '';
}

export function hydrateCsrf(win) {
  const w = win || globalThis;
  const mem = typeof w.PUNCHCLOCK_CSRF === 'string' ? w.PUNCHCLOCK_CSRF.trim() : '';
  if (mem) {
    return persistCsrf(w, mem);
  }
  return csrfHeaderValue(w);
}

export function adminWriteHeaders(win) {
  const value = csrfHeaderValue(win);
  return value ? { 'X-Csrf-Token': value } : {};
}

export function adminFetch(win, input, init) {
  const w = win || globalThis;
  const fetchFn = w.fetch ? w.fetch.bind(w) : null;
  if (!fetchFn) {
    return Promise.reject(new Error('fetch unavailable'));
  }
  const base = init ? { ...init } : {};
  const headers = { ...(base.headers || {}), ...adminWriteHeaders(w) };
  return fetchFn(input, { ...base, headers, credentials: 'same-origin' });
}

export function fetchCsrfFromSession(win) {
  const w = win || globalThis;
  const fetchFn = w.fetch ? w.fetch.bind(w) : null;
  if (!fetchFn) {
    return Promise.resolve('');
  }
  return fetchFn('/api/auth/session', {
    method: 'GET',
    credentials: 'same-origin',
    headers: { Accept: 'application/json' }
  })
    .then((res) => (res.ok ? res.json() : null))
    .catch(() => null)
    .then((data) => {
      const token = data && typeof data.csrfToken === 'string' ? data.csrfToken.trim() : '';
      if (token) {
        persistCsrf(w, token);
        if (data.authenticated) {
          w.PUNCHCLOCK_AUTHENTICATED = true;
          if (typeof data.username === 'string' && data.username && !w.PUNCHCLOCK_USERNAME) {
            w.PUNCHCLOCK_USERNAME = data.username;
          }
        }
        return token;
      }
      return '';
    });
}

export function clearClientAuth(win, doc) {
  const w = win || globalThis;
  const d = doc || w.document;
  w.PUNCHCLOCK_AUTHENTICATED = false;
  w.PUNCHCLOCK_USERNAME = '';
  clearCsrf(w);
  if (d && d.body && d.body.classList) {
    d.body.classList.remove('is-authenticated');
    d.body.classList.add('is-guest');
  }
}

export function goLoggedOut(win) {
  const w = win || globalThis;
  if (!w.location) return;
  if (typeof w.location.replace === 'function') {
    w.location.replace('/');
    return;
  }
  if (typeof w.location.assign === 'function') {
    w.location.assign('/');
    return;
  }
  w.location.href = '/';
}

function ensureCsrf(win) {
  const existing = csrfHeaderValue(win);
  if (existing) {
    return Promise.resolve(existing);
  }
  return fetchCsrfFromSession(win);
}

export function postLogout(win) {
  const w = win || globalThis;
  const finish = () => {
    clearClientAuth(w);
    goLoggedOut(w);
  };
  return ensureCsrf(w)
    .then((token) => {
      const headers = { Accept: 'application/json' };
      if (token) {
        headers['X-Csrf-Token'] = token;
      }
      return adminFetch(w, '/logout', { method: 'POST', headers }).catch(() => null);
    })
    .catch(() => null)
    .then(() => {
      finish();
    });
}

function eventElement(target) {
  if (target && typeof target.closest === 'function') {
    return target;
  }
  if (target && target.parentElement && typeof target.parentElement.closest === 'function') {
    return target.parentElement;
  }
  return null;
}

export function wireLogoutLinks(win, doc) {
  const w = win || globalThis;
  const d = doc || w.document;
  hydrateCsrf(w);
  if (!d || typeof d.addEventListener !== 'function') return;
  d.addEventListener('click', (ev) => {
    const el = eventElement(ev && ev.target);
    if (!el) return;
    const link = el.closest('a[href="/logout"], [data-pt-logout]');
    if (!link) return;
    if (typeof ev.preventDefault === 'function') ev.preventDefault();
    postLogout(w);
  }, true);
}
