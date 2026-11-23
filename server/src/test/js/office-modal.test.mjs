import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const officeModal = await loadPublicModule('office-modal.js');
const homeDownload = await loadPublicModule('home-download-panel.js');
const devFmt = await loadPublicModule('dev-machines-format.js');

function miniDoc() {
  const nodes = [];
  const body = {
    classList: { classes: new Set(), add(c) { this.classes.add(c); }, remove(c) { this.classes.delete(c); }, toggle(c, on) { on ? this.classes.add(c) : this.classes.delete(c); } },
    style: {},
    dataset: {}
  };
  function el(id, hidden) {
    return {
      id,
      hidden: !!hidden,
      hiddenAttr: hidden,
      attributes: new Map([['aria-hidden', hidden ? 'true' : 'true']]),
      children: [],
      style: {},
      classList: { list: new Set(), add(c) { this.list.add(c); }, remove(c) { this.list.remove(c); } },
      setAttribute(k, v) { this.attributes.set(k, v); },
      getAttribute(k) { return this.attributes.get(k); },
      removeAttribute(k) { this.attributes.delete(k); },
      hasAttribute(k) { return this.attributes.has(k); },
      querySelector() { return null; },
      querySelectorAll() { return []; },
      addEventListener() {},
      focus() {},
      contains() { return false; }
    };
  }
  const downloadBoard = el('downloadBoard', true);
  Object.defineProperty(downloadBoard, 'hidden', {
    get() { return this.hiddenAttr; },
    set(v) { this.hiddenAttr = v; }
  });
  const doc = {
    body,
    activeElement: null,
    getElementById(id) {
      if (id === 'downloadBoard') return downloadBoard;
      return null;
    },
    addEventListener() {},
    removeEventListener() {}
  };
  return { doc, downloadBoard };
}

test('HUD 下載按鈕不連到 /download 整頁', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /data-pt-open-download/);
  assert.doesNotMatch(js, /href="\/download"/);
});

test('open=download 深連結', () => {
  assert.equal(devFmt.shouldOpenDownloadFromSearch('?open=download'), true);
  let opened = false;
  const win = {
    location: { href: 'http://localhost/?open=download', search: '?open=download', pathname: '/', hash: '' },
    history: { replaceState: (_a, _b, url) => { win._url = url; } }
  };
  assert.equal(devFmt.applyDownloadOpenParam(win, () => { opened = true; }), true);
  assert.equal(opened, true);
  assert.equal(win._url, '/');
});

test('office modal 一次只開一個', () => {
  let aOpen = false;
  let bOpen = false;
  const mgr = officeModal.createOfficeModalManager({
    doc: { body: { classList: { add() {}, remove() {} }, style: {}, dataset: {} }, addEventListener() {}, removeEventListener() {} },
    win: { addEventListener() {}, removeEventListener() {}, location: { pathname: '/', search: '', hash: '' }, history: { pushState() {}, replaceState() {} } },
    onPauseScene() {}
  });
  mgr.register('download', {
    root: { querySelectorAll: () => [] },
    close() { aOpen = false; }
  });
  mgr.register('login', {
    root: { querySelectorAll: () => [] },
    close() { bOpen = false; }
  });
  aOpen = true;
  mgr.notifyOpened('download');
  assert.equal(mgr.activeId(), 'download');
  mgr.notifyOpened('login');
  assert.equal(aOpen, false);
});

test('下載彈窗可關閉且不設 location', () => {
  const { doc, downloadBoard } = miniDoc();
  downloadBoard.querySelector = (sel) => {
    if (sel === '.home-download-panel') return { addEventListener() {} };
    if (sel === '[data-download-close]') return { addEventListener() {}, focus() {} };
    if (sel === '[data-download-actions]') return { querySelector: () => null };
    return null;
  };
  let assigned = null;
  const win = {
    fetch: async () => ({ ok: false }),
    location: { assign: (url) => { assigned = url; } }
  };
  const download = homeDownload.createHomeDownloadController({ doc, win });
  download.open({ trigger: { focus() {} } });
  assert.equal(downloadBoard.hiddenAttr, false);
  download.close();
  assert.equal(downloadBoard.hiddenAttr, true);
  assert.equal(assigned, null);
});

test('download.css 下載區塊使用 border-box', () => {
  const css = readFileSync(join(publicRoot, 'css/download.css'), 'utf8');
  assert.match(css, /box-sizing:\s*border-box/);
});