/** 首頁彈窗用 CSS：不在 index 首屏 blocking，開啟對應面板時才載入。 */

const cache = {};

function versionQuery(win) {
  const ver = win && typeof win.PUNCHCLOCK_SERVER_VERSION === 'string'
    ? win.PUNCHCLOCK_SERVER_VERSION.trim()
    : '';
  const built = win && typeof win.PUNCHCLOCK_SERVER_BUILD_TIME === 'string'
    ? win.PUNCHCLOCK_SERVER_BUILD_TIME.trim()
    : '';
  const parts = [];
  if (ver && !ver.includes('{{')) parts.push(ver);
  if (built && !built.includes('{{') && built.indexOf('開發') < 0) {
    parts.push(built.replace(/[^0-9A-Za-z]+/g, ''));
  }
  if (!parts.length) return '';
  return '?v=' + encodeURIComponent(parts.join('.'));
}

function assetAlready(doc, path) {
  const nodes = doc.querySelectorAll('link[rel="stylesheet"]');
  for (let i = 0; i < nodes.length; i++) {
    const raw = nodes[i].getAttribute('href') || '';
    if (raw === path || raw.startsWith(path + '?')) return true;
  }
  return false;
}

function loadStylesheet(doc, path, href, marker) {
  return new Promise((resolve, reject) => {
    if (assetAlready(doc, path)) {
      resolve();
      return;
    }
    const link = doc.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    if (marker) link.setAttribute(marker, '1');
    link.onload = () => resolve();
    link.onerror = () => reject(new Error('css failed: ' + href));
    (doc.head || doc.documentElement).appendChild(link);
  });
}

function ensureOne(win, doc, key, path, marker) {
  if (cache[key]) return cache[key];
  const q = versionQuery(win);
  cache[key] = loadStylesheet(doc, path, path + q, marker);
  return cache[key];
}

export function ensureHomeRoadmapCss(win, doc) {
  return ensureOne(win, doc, 'roadmap', '/css/roadmap.css', 'data-home-roadmap-css');
}

export function ensureHomeLoginCss(win, doc) {
  return ensureHomeRoadmapCss(win, doc).then(() =>
    ensureOne(win, doc, 'login', '/css/login.css', 'data-home-login-css')
  );
}

export function ensureHomeDownloadCss(win, doc) {
  return ensureHomeRoadmapCss(win, doc).then(() =>
    ensureOne(win, doc, 'download', '/css/download.css', 'data-home-download-css')
  );
}
