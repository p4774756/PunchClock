// 主島手機操作列：node --test server/src/test/js/island-mobile-chrome.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const publicDir = join(here, '..', '..', 'main', 'resources', 'public');
const indexHtml = readFileSync(join(publicDir, 'index.html'), 'utf8');
const islandCss = readFileSync(join(publicDir, 'css', 'island.css'), 'utf8');

const Corner = await loadPublicModule('island-corner-ui.js');
const Chat = await loadPublicModule('island-chat.js');

class FakeClassList {
  constructor() { this.set = new Set(); }
  add(c) { this.set.add(c); }
  remove(c) { this.set.delete(c); }
  contains(c) { return this.set.has(c); }
  toggle(c, force) {
    const on = force === undefined ? !this.set.has(c) : !!force;
    if (on) this.set.add(c); else this.set.delete(c);
    return on;
  }
}

class FakeElement {
  constructor(doc, tag, attrs) {
    this.ownerDocument = doc;
    this.tagName = tag.toUpperCase();
    this.attrs = {};
    this.children = [];
    this.parent = null;
    this.listeners = {};
    this.hidden = false;
    this.textContent = '';
    this.className = '';
    this.classList = new FakeClassList();
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (k === 'hidden') this.hidden = !!v;
      else if (k === 'class') this.className = String(v);
      else this.setAttribute(k, v);
    });
  }
  get id() { return this.attrs.id || ''; }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; }
  hasAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k); }
  appendChild(child) { child.parent = this; this.children.push(child); return child; }
  addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); }
  click() { this.dispatchEvent({ type: 'click', target: this }); }
  dispatchEvent(event) {
    if (!event.target) event.target = this;
    (this.listeners[event.type] || []).slice().forEach((fn) => fn.call(this, event));
  }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
  querySelectorAll(sel) {
    const out = [];
    const walk = (node) => {
      node.children.forEach((c) => {
        if (c.matches(sel)) out.push(c);
        walk(c);
      });
    };
    walk(this);
    return out;
  }
  matches(sel) {
    if (sel.startsWith('#')) return this.id === sel.slice(1);
    const attrEq = /^\[([\w-]+)="([^"]+)"\]$/.exec(sel);
    if (attrEq) return this.getAttribute(attrEq[1]) === attrEq[2];
    const attrBare = /^\[([\w-]+)\]$/.exec(sel);
    if (attrBare) return this.hasAttribute(attrBare[1]);
    if (sel.startsWith('.')) {
      const cls = sel.slice(1);
      return this.className.split(/\s+/).includes(cls);
    }
    return this.tagName === sel.toUpperCase();
  }
  closest(sel) {
    for (let node = this; node; node = node.parent) {
      if (node.matches && node.matches(sel)) return node;
    }
    return null;
  }
  focus() { this.ownerDocument.activeElement = this; }
}

function makeMobileDom(guest) {
  const doc = { activeElement: null, listeners: {} };
  doc.createElement = (tag) => new FakeElement(doc, tag);
  doc.body = new FakeElement(doc, 'body');
  doc.body.classList = new FakeClassList();
  doc.getElementById = (id) => doc.body.querySelector('#' + id);
  doc.querySelector = (sel) => doc.body.querySelector(sel);
  doc.querySelectorAll = (sel) => doc.body.querySelectorAll(sel);
  doc.addEventListener = (type, fn) => {
    (doc.listeners[type] = doc.listeners[type] || []).push(fn);
  };
  const el = (tag, attrs, parent) => (parent || doc.body).appendChild(new FakeElement(doc, tag, attrs));

  const corner = el('div', { class: 'island-corner' });
  const avatarWrap = el('div', { class: 'island-avatar-wrap island-corner-dock' }, corner);
  el('button', { id: 'islandAvatarBtn', class: 'island-tool', 'aria-expanded': 'false' }, avatarWrap);
  const avatarPop = el('div', { id: 'islandAvatarPop', class: 'island-avatar-pop', hidden: true }, avatarWrap);
  el('button', { class: 'island-avatar-choice', 'data-island-avatar': 'milu', 'aria-pressed': 'true' }, avatarPop);
  el('button', { class: 'island-avatar-choice', 'data-island-avatar': 'berry', 'aria-pressed': 'false' }, avatarPop);
  el('button', { id: 'islandExplore', class: 'island-tool island-corner-dock' }, corner);
  el('button', { id: 'islandHome', class: 'island-tool island-corner-overflow' }, corner);
  el('button', { id: 'islandListBtn', class: 'island-tool island-corner-overflow' }, corner);
  el('button', { id: 'islandAudio', class: 'island-tool island-corner-overflow', 'aria-pressed': 'true' }, corner);
  const nickWrap = el('div', { class: 'island-nick-wrap island-corner-overflow' }, corner);
  el('button', { id: 'islandNickBtn' }, nickWrap);
  el('div', { id: 'islandNickPop', hidden: true }, nickWrap);
  const chatWrap = el('div', { class: 'island-chat-wrap island-corner-dock' }, corner);
  el('button', { id: 'islandChatBtn' }, chatWrap);
  el('div', { id: 'islandChatPanel', hidden: true }, chatWrap);
  el('span', { id: 'islandPeerCount', class: 'island-peer-count island-corner-dock', hidden: true }, corner);
  el('a', { href: '/login', class: 'island-tool island-corner-overflow', 'data-guest-only': '' }, corner);
  el('a', { href: '/logout', class: 'island-tool island-corner-overflow', 'data-auth-only': '' }, corner);
  el('button', { id: 'islandMoreBtn', class: 'island-corner-more-btn island-corner-dock' }, corner);

  const more = el('section', { id: 'islandMoreSheet', hidden: true });
  el('button', { id: 'islandMoreScrim' }, more);
  const panel = el('div', { class: 'island-more-panel' }, more);
  el('button', { id: 'islandMoreClose' }, panel);
  const menu = el('div', { class: 'island-more-menu' }, panel);
  ['islandHome', 'islandListBtn', 'islandNickBtn', 'islandAudio'].forEach((target) => {
    const row = el('button', { class: 'island-more-item', 'data-island-more-target': target }, menu);
    if (target === 'islandAudio') {
      el('small', { id: 'islandMoreAudioStatus' }, row);
    }
  });
  el('a', { class: 'island-more-item', 'data-guest-only': '', 'data-island-more-login': '', href: '/login' }, menu);
  el('a', { class: 'island-more-item', 'data-auth-only': '', 'data-island-more-logout': '', href: '/logout' }, menu);

  el('motion', { id: 'islandLabels' });
  el('div', { id: 'islandJoystick', class: 'island-joystick' });
  el('input', { id: 'islandChatInput' });
  el('button', { id: 'islandChatSend' });
  el('div', { id: 'islandChatLog' });
  el('p', { id: 'islandChatHint', hidden: true });

  if (guest) doc.body.classList.add('is-guest');
  else doc.body.classList.add('is-authenticated');

  return doc;
}

test('index 不再含 #islandChatFab，有手機「更多」選單', () => {
  assert.doesNotMatch(indexHtml, /id="islandChatFab"/);
  assert.match(indexHtml, /id="islandMoreBtn"/);
  assert.match(indexHtml, /id="islandMoreSheet"/);
  assert.match(indexHtml, /aria-label="更多"/);
});

test('360／390 探索 dock 不溢出 viewport 且不與搖桿相交', () => {
  for (const width of [360, 390]) {
    const height = 844;
    const joy = Corner.joystickRectMobile(width, height);
    const bounds = Corner.computeMobileDockBounds(width, height, true, '島上 3 人', true);
    assert.ok(Corner.dockFitsViewport(bounds, width), width + 'px 探索列在畫面內');
    assert.equal(bounds.left >= 0, true, width + 'px 左緣不為負');
    assert.ok(!Corner.rectsIntersect(bounds, joy), width + 'px 不與搖桿重疊');
  }
});

test('非探索 dock 較短、不含人數徽章時仍放得下', () => {
  const bounds = Corner.computeMobileDockBounds(360, 780, false, '', false);
  assert.ok(Corner.dockFitsViewport(bounds, 360));
});

test('chat 無 FAB，探索切換不會露出第二顆聊天', () => {
  const doc = makeMobileDom(true);
  const chat = Chat.createIslandChat({ document: doc });
  assert.equal(doc.getElementById('islandChatFab'), null);
  chat.setExploring(true);
  assert.equal(doc.querySelectorAll('#islandChatBtn').length, 1);
});

test('syncPeerCount：探索且 welcomed 才顯示人數', () => {
  const doc = makeMobileDom(true);
  let exploring = false;
  let welcomed = false;
  const ui = Corner.createCornerUi({
    document: doc,
    storage: null,
    getPresence: () => null,
    getPeerCount: () => 4,
    isExploring: () => exploring,
    isWelcomed: () => welcomed
  });
  ui.syncPeerCount();
  assert.equal(doc.getElementById('islandPeerCount').hidden, true);
  exploring = true;
  ui.syncPeerCount();
  assert.equal(doc.getElementById('islandPeerCount').hidden, true);
  welcomed = true;
  ui.syncPeerCount();
  assert.equal(doc.getElementById('islandPeerCount').hidden, false);
  assert.match(doc.getElementById('islandPeerCount').textContent, /島上 4 人/);
});

test('「更多」打開後訪客看得到登入，已登入看得到登出', () => {
  const guestDoc = makeMobileDom(true);
  const guestUi = Corner.createCornerUi({
    document: guestDoc,
    storage: null,
    getPresence: () => null,
    isExploring: () => false,
    isWelcomed: () => false
  });
  guestUi.setMoreOpen(true);
  const guestMenu = guestDoc.getElementById('islandMoreSheet');
  assert.equal(guestMenu.hidden, false);
  assert.ok(guestDoc.querySelector('[data-island-more-login]'));
  assert.ok(guestDoc.querySelector('[data-island-more-target="islandHome"]'));
  assert.ok(guestDoc.querySelector('[data-island-more-target="islandListBtn"]'));
  assert.ok(guestDoc.querySelector('[data-island-more-target="islandNickBtn"]'));

  const authDoc = makeMobileDom(false);
  Corner.createCornerUi({
    document: authDoc,
    storage: null,
    getPresence: () => null,
    isExploring: () => false,
    isWelcomed: () => false
  });
  assert.ok(authDoc.querySelector('[data-island-more-logout]'));
});

test('手機 CSS 隱藏 overflow 列、保留 dock 與安全區', () => {
  assert.match(islandCss, /\.island-corner-overflow/);
  assert.match(islandCss, /safe-area-inset-right/);
  assert.match(islandCss, /safe-area-inset-bottom/);
  assert.doesNotMatch(islandCss, /island-chat-fab/);
});

test('探索時聊天面板邊界不與搖桿相交（360／390）', () => {
  for (const width of [360, 390]) {
    const height = width === 360 ? 780 : 844;
    const panel = Corner.chatPanelRectMobileExplore(width, height, 12);
    assert.ok(panel.left >= panel.joy.right - 0.5, width + 'px 面板左緣避開搖桿');
    assert.ok(panel.bottom <= panel.joy.top + 0.5, width + 'px 面板底在搖桿上緣之上');
    assert.ok(!Corner.rectsIntersect(panel, panel.joy), width + 'px 面板與搖桿不重疊');
    assert.ok(panel.left >= 0 && panel.right <= width + 0.5, width + 'px 面板在 viewport 內');
  }
});

test('「更多」打開時 body 有 island-more-open，CSS 隱藏搖桿與 dock', () => {
  const doc = makeMobileDom(true);
  const ui = Corner.createCornerUi({
    document: doc,
    storage: null,
    getPresence: () => null,
    isExploring: () => true,
    isWelcomed: () => false
  });
  ui.setMoreOpen(true);
  assert.equal(doc.body.classList.contains('island-more-open'), true);
  assert.match(
    islandCss,
    /body\.island-more-open \.island-corner[\s\S]*visibility:\s*hidden/
  );
  assert.match(
    islandCss,
    /body\.island-more-open \.island-joystick[\s\S]*visibility:\s*hidden/
  );
  ui.setMoreOpen(false);
  assert.equal(doc.body.classList.contains('island-more-open'), false);
});

test('角色選單打開時 body 有 island-avatar-open，CSS 停用搖桿', () => {
  const doc = makeMobileDom(true);
  const storage = { data: {}, getItem(k) { return this.data[k] ?? null; }, setItem(k, v) { this.data[k] = v; } };
  const ui = Corner.createCornerUi({
    document: doc,
    storage,
    getPresence: () => null,
    isExploring: () => true,
    isWelcomed: () => false
  });
  ui.setAvatarOpen(true);
  assert.equal(doc.body.classList.contains('island-avatar-open'), true);
  assert.equal(doc.getElementById('islandAvatarPop').hidden, false);
  assert.match(
    islandCss,
    /body\.island-avatar-open \.island-joystick[\s\S]*pointer-events:\s*none/
  );
  ui.setAvatarOpen(false);
  assert.equal(doc.body.classList.contains('island-avatar-open'), false);
});

test('手機角色選單邊界在 viewport 內且探索時不與搖桿相交（360／390）', () => {
  for (const width of [360, 390]) {
    const height = width === 360 ? 780 : 844;
    for (const exploring of [false, true]) {
      const panel = Corner.avatarPopRectMobile(width, height, exploring);
      assert.ok(panel.left >= -0.5, width + 'px 選單左緣在畫面內');
      assert.ok(panel.right <= width + 0.5, width + 'px 選單右緣在畫面內');
      assert.ok(panel.width > 6 * 16, width + 'px 選單足夠顯示縮圖與文字');
      if (exploring) {
        assert.ok(!Corner.rectsIntersect(panel, panel.joy), width + 'px 探索中選單不與搖桿重疊');
      }
    }
  }
});

test('手機 CSS 角色選單 fixed 錨在 dock 上、探索時避開搖桿', () => {
  assert.match(islandCss, /body\.island-avatar-open \.island-avatar-pop[\s\S]*position:\s*fixed/);
  assert.match(islandCss, /body\.is-exploring\.island-avatar-open \.island-avatar-pop[\s\S]*0\.7rem \+ 8rem/);
});

test('選小莓寫入 localStorage 並更新按鈕', () => {
  const doc = makeMobileDom(true);
  const storage = { data: {}, getItem(k) { return this.data[k] ?? null; }, setItem(k, v) { this.data[k] = v; } };
  let changed = null;
  const ui = Corner.createCornerUi({
    document: doc,
    storage,
    getPresence: () => null,
    isExploring: () => false,
    isWelcomed: () => false,
    onAvatarChange: (id) => { changed = id; }
  });
  const berryBtn = doc.querySelector('[data-island-avatar="berry"]');
  berryBtn.click();
  assert.equal(storage.data['punchclock.island.avatar'], 'berry');
  assert.equal(changed, 'berry');
  assert.equal(doc.getElementById('islandAvatarBtn').textContent, '小莓');
  const miluBtn = doc.querySelector('[data-island-avatar="milu"]');
  ui.setAvatarOpen(true);
  miluBtn.click();
  assert.equal(storage.data['punchclock.island.avatar'], 'milu');
  assert.equal(doc.getElementById('islandAvatarBtn').textContent, '蜜鹿');
});

test('「更多」選單面板含 safe-area 底內距且層級高於搖桿', () => {
  assert.match(islandCss, /\.island-more-panel[\s\S]*safe-area-inset-bottom/);
  const sheetZ = /\.island-more-sheet\s*\{[^}]*z-index:\s*(\d+)/m.exec(islandCss);
  const joyZ = /\.island-joystick\s*\{[^}]*z-index:\s*(\d+)/m.exec(islandCss);
  assert.ok(sheetZ && joyZ, '可解析 z-index');
  assert.ok(Number(sheetZ[1]) > Number(joyZ[1]), '更多選單在搖桿之上');
  assert.match(islandCss, /body\.is-exploring \.island-chat-wrap\s*>\s*\.island-chat/);
});
