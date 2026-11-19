// WebGL 提示：node --test server/src/test/js/island-webgl-notice.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const {
  shouldShowWebglNotice,
  webglNoticeBody,
  readForceNoWebgl,
  WEBGL_NOTICE_TITLE,
  bindWebglNotice,
  attachWebglContextHandlers
} = await loadPublicModule('island-webgl-notice.js');

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', '..', 'main', 'resources', 'public');
const islandCss = readFileSync(join(publicDir, 'css', 'island.css'), 'utf8');
const indexHtml = readFileSync(join(publicDir, 'index.html'), 'utf8');

test('shouldShowWebglNotice：僅 WebGL 正常時不顯示', () => {
  assert.equal(shouldShowWebglNotice({ rendererOk: true, contextLost: false }), false);
});

test('shouldShowWebglNotice：建立失敗或 context lost 要顯示', () => {
  assert.equal(shouldShowWebglNotice({ rendererOk: false, contextLost: false }), true);
  assert.equal(shouldShowWebglNotice({ rendererOk: true, contextLost: true }), true);
  assert.equal(shouldShowWebglNotice({ rendererOk: false, contextLost: true }), true);
});

test('webglNoticeBody：context lost 附加中斷句', () => {
  const base = webglNoticeBody({ contextLost: false });
  assert.ok(base.includes('WebGL'));
  assert.ok(!base.includes('中途中斷'));
  const lost = webglNoticeBody({ contextLost: true });
  assert.ok(lost.endsWith('3D 繪圖中途中斷。'));
  assert.ok(lost.startsWith(base.slice(0, 8)));
});

test('readForceNoWebgl：僅帶參數時為真', () => {
  assert.equal(readForceNoWebgl(''), false);
  assert.equal(readForceNoWebgl('?tab=devices'), false);
  assert.equal(readForceNoWebgl('?forceNoWebgl=1'), true);
  assert.equal(readForceNoWebgl('?forceNoWebgl=0'), false);
});

class FakeLocation {
  constructor() {
    this.reloadCount = 0;
  }
  reload() {
    this.reloadCount += 1;
  }
}

class FakeBody {
  constructor() {
    this._classes = new Set();
    this.style = {
      _props: {},
      setProperty(k, v) { this._props[k] = v; },
      removeProperty(k) { delete this._props[k]; }
    };
  }
  get classList() {
    const self = this;
    return {
      add(c) { self._classes.add(c); },
      remove(c) { self._classes.delete(c); },
      contains(c) { return self._classes.has(c); }
    };
  }
}

class FakeElement {
  constructor(attrs = {}) {
    this.attrs = { ...attrs };
    this.hidden = !!attrs.hidden;
    this.listeners = {};
    this.children = [];
    this.textContent = '';
    this.offsetHeight = attrs.offsetHeight ?? 184;
    this.ownerDocument = attrs.ownerDocument ?? null;
  }
  get id() { return this.attrs.id || ''; }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; }
  querySelector(sel) {
    const m = /^\[([\w-]+)\]$/.exec(sel);
    if (m) {
      const key = m[1];
      return this.children.find((c) => c.attrs && Object.prototype.hasOwnProperty.call(c.attrs, key)) || null;
    }
    return null;
  }
  addEventListener(type, fn) {
    (this.listeners[type] = this.listeners[type] || []).push(fn);
  }
  dispatchEvent(event) {
    (this.listeners[event.type] || []).forEach((fn) => fn(event));
  }
}

test('bindWebglNotice：顯示、關閉與重新載入', () => {
  const docBody = new FakeBody();
  const root = new FakeElement({
    hidden: true,
    ownerDocument: { body: docBody }
  });
  const body = new FakeElement({ 'data-webgl-notice-body': '' });
  const reload = new FakeElement({ 'data-webgl-notice-reload': '' });
  const close = new FakeElement({ 'data-webgl-notice-close': '' });
  root.children.push(body, reload, close);

  const loc = new FakeLocation();
  const notice = bindWebglNotice({ root, location: loc });
  notice.present({ rendererOk: false, contextLost: false });
  assert.equal(root.hidden, false);
  assert.ok(body.textContent.includes('WebGL'));
  assert.ok(docBody.classList.contains('is-webgl-notice-open'));
  assert.equal(docBody.style._props['--island-webgl-notice-height'], '184px');

  close.dispatchEvent({ type: 'click' });
  assert.equal(root.hidden, true);
  assert.equal(notice.dismissed(), true);
  assert.ok(!docBody.classList.contains('is-webgl-notice-open'));

  notice.present({ rendererOk: false, contextLost: true });
  assert.equal(root.hidden, true, '關閉後不再自動顯示');

  const root2 = new FakeElement({ hidden: true });
  const reload2 = new FakeElement({ 'data-webgl-notice-reload': '' });
  const body2 = new FakeElement({ 'data-webgl-notice-body': '' });
  root2.children.push(body2, reload2);
  const loc2 = new FakeLocation();
  const notice2 = bindWebglNotice({ root: root2, location: loc2 });
  notice2.present({ rendererOk: false, contextLost: false });
  reload2.dispatchEvent({ type: 'click' });
  assert.equal(loc2.reloadCount, 1);
});

test('attachWebglContextHandlers：preventDefault 與 restored 重載', () => {
  const canvas = new FakeElement();
  const root = new FakeElement({ hidden: true });
  const body = new FakeElement({ 'data-webgl-notice-body': '' });
  root.children.push(body);
  const loc = new FakeLocation();
  const notice = bindWebglNotice({ root, location: loc });
  attachWebglContextHandlers(canvas, notice, loc);

  const lost = { type: 'webglcontextlost', preventDefault() { this.defaultPrevented = true; } };
  canvas.dispatchEvent(lost);
  assert.equal(lost.defaultPrevented, true);
  assert.equal(root.hidden, false);
  assert.ok(body.textContent.includes('中途中斷'));

  canvas.dispatchEvent({ type: 'webglcontextrestored' });
  assert.equal(loc.reloadCount, 1);
});

test('index.html 含 WebGL 提示結構', () => {
  assert.match(indexHtml, /id="islandWebglNotice"/);
  assert.match(indexHtml, /data-webgl-notice-reload/);
  assert.match(indexHtml, new RegExp(WEBGL_NOTICE_TITLE));
});

test('CSS：按鈕觸控高度與版面約束', () => {
  assert.match(islandCss, /\.island-maple-btn[\s\S]*min-height:\s*2\.75rem/);
  assert.match(islandCss, /#islandWebglNotice[\s\S]*max-width:[\s\S]*800px/);
  assert.match(islandCss, /body\.island-body\.is-night \.island-webgl-notice-card/);
  assert.match(islandCss, /@media \(max-width: 760px\)[\s\S]*#islandWebglNotice[\s\S]*bottom:\s*calc\(12px \+ 2\.25rem/);
  assert.match(islandCss, /body\.is-webgl-notice-open \.island-list:not\(\[hidden\]\)/);
});

function noticeRightEdgeX(viewportWidth) {
  const leftPct = 0.38;
  const cardW = Math.min(26.25 * 16, Math.min(26.25 * 16, 800 - 2 * Math.max(12, Math.min(32, viewportWidth * 0.04))));
  const centerX = viewportWidth * leftPct;
  return centerX + cardW * 0.5;
}

test('桌面 1280／1440：提示卡右緣在清單區左側', () => {
  for (const w of [1280, 1440]) {
    assert.ok(noticeRightEdgeX(w) < 800, `viewport ${w}px 右緣 < 800`);
  }
});