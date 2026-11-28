/** 心得動態牆資源：開啟覆蓋層時才載入。 */

import { ensureHomeRoadmapCss } from './home-panel-css-loader.js';

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

function loadStylesheet(doc, href, path) {
  return new Promise((resolve, reject) => {
    if (assetAlready('href', doc, path)) {
      resolve();
      return;
    }
    const link = doc.createElement('link');
    link.rel = 'stylesheet';
    link.href = href;
    link.setAttribute('data-journal-css', '1');
    link.onload = () => resolve();
    link.onerror = () => reject(new Error('css failed: ' + href));
    (doc.head || doc.documentElement).appendChild(link);
  });
}

export function ensureJournalAssets(win, doc) {
  if (loadPromise) return loadPromise;
  const q = versionQuery(win);
  // 先載共用彈窗 CSS（maple 視窗外框、標題、關閉鈕、btn），再載 journal.css 覆寫。
  loadPromise = ensureHomeRoadmapCss(win, doc)
    .then(() => loadStylesheet(doc, '/css/journal.css' + q, '/css/journal.css'))
    .catch((err) => {
      loadPromise = null;
      throw err;
    });
  return loadPromise;
}