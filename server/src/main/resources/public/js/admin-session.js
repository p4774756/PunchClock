/** 站長 CSRF 與登出 POST。 */

export function csrfHeaderValue(win) {
  const w = win || globalThis;
  const token = w.PUNCHCLOCK_CSRF;
  return typeof token === 'string' && token ? token : '';
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

export function postLogout(win) {
  const w = win || globalThis;
  return adminFetch(w, '/logout', { method: 'POST' }).then((res) => {
    if (res.ok) {
      w.location.href = '/';
    }
    return res;
  });
}

export function wireLogoutLinks(win, doc) {
  const w = win || globalThis;
  const d = doc || w.document;
  if (!d || typeof d.addEventListener !== 'function') return;
  d.addEventListener('click', (ev) => {
    const target = ev.target;
    if (!target || typeof target.closest !== 'function') return;
    const link = target.closest('a[href="/logout"], [data-pt-logout]');
    if (!link || !w.PUNCHCLOCK_AUTHENTICATED) return;
    ev.preventDefault();
    postLogout(w);
  });
}