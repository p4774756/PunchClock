/** 首頁任務儀表板：登入後開啟裝置窗時才載入（訪客首屏不下載）。 */

let loadPromise = null;

function versionQuery(win) {
  const ver = win && typeof win.PUNCHCLOCK_SERVER_VERSION === 'string'
    ? win.PUNCHCLOCK_SERVER_VERSION.trim()
    : '';
  if (!ver || ver.includes('{{')) return '';
  return '?v=' + encodeURIComponent(ver);
}

function assetAlready(attr, doc, path) {
  const nodes = doc.querySelectorAll('[' + attr + ']');
  for (let i = 0; i < nodes.length; i++) {
    const raw = nodes[i].getAttribute(attr) || '';
    if (raw === path || raw.startsWith(path + '?')) return true;
  }
  return false;
}

function loadStylesheet(doc, href) {
  return new Promise((resolve, reject) => {
    if (assetAlready('href', doc, '/css/dashboard.css')) {
      resolve();
      return;
    }
    const link = doc.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.setAttribute('data-home-dashboard-css', '1');
    link.onload = () => resolve();
    link.onerror = () => reject(new Error('css failed: ' + href));
    (doc.head || doc.documentElement).appendChild(link);
  });
}

function loadScript(doc, src, path) {
  return new Promise((resolve, reject) => {
    if (assetAlready('src', doc, path)) {
      resolve();
      return;
    }
    const el = doc.createElement('script');
    el.src = src;
    el.onload = () => resolve();
    el.onerror = () => reject(new Error('script failed: ' + src));
    (doc.body || doc.documentElement).appendChild(el);
  });
}

export function ensureHomeDashboardAssets(win, doc) {
  if (loadPromise) return loadPromise;
  const q = versionQuery(win);
  loadPromise = loadStylesheet(doc, '/css/dashboard.css' + q)
    .then(() => loadScript(doc, '/js/dash-tab.js' + q, '/js/dash-tab.js'))
    .then(() => loadScript(doc, '/js/dashboard.js' + q, '/js/dashboard.js'));
  return loadPromise;
}