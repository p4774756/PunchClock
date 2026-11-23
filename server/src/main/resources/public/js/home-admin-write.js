/** 後台 Cookie 寫入：帶 CSRF（同源由瀏覽器送 Origin）。 */

export function setCsrfToken(win, token) {
  if (win) {
    win.PUNCHCLOCK_CSRF_TOKEN = token == null ? '' : String(token);
  }
}

export function adminWriteFetch(win, url, options) {
  const fetchFn = (win && win.fetch) || (typeof fetch === 'function' ? fetch : null);
  if (!fetchFn) {
    return Promise.reject(new Error('fetch unavailable'));
  }
  const opts = Object.assign({}, options || {});
  opts.credentials = 'same-origin';
  const headers = Object.assign({}, opts.headers || {});
  const token = win && win.PUNCHCLOCK_CSRF_TOKEN;
  if (token) {
    headers['X-Csrf-Token'] = token;
  }
  opts.headers = headers;
  return fetchFn(url, opts);
}