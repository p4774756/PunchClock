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
const islandJs = readFileSync(join(publicDir, 'js/island.js'), 'utf8');

const Corner = await loadPublicModule('island-corner-ui.js');
const Chat = await loadPublicModule('island-chat.js');
const Explore = await loadPublicModule('island-explore.js');
const Interior = await loadPublicModule('island-interior.js');

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
    if (typeof event.preventDefault !== 'function') event.preventDefault = () => {};
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

  const hud = el('div', { class: 'island-hud' });
  el('span', { id: 'islandPeerCount', class: 'island-hud-peer-badge', hidden: true }, hud);
  el('strong', { id: 'islandHudPlayerName' }, hud);
  el('img', { id: 'islandHudPlayerThumb' }, hud);
  el('button', { id: 'islandDay', class: 'island-orb island-orb-day', 'aria-pressed': 'true' }, hud);
  el('button', { id: 'islandNight', class: 'island-orb island-orb-night', 'aria-pressed': 'false' }, hud);
  el('button', { id: 'islandAudio', class: 'island-orb island-orb-audio', 'aria-pressed': 'true' }, hud);
  const chatWrap = el('div', { class: 'island-chat-wrap island-hud-chat' }, hud);
  el('button', { id: 'islandChatBtn', class: 'island-orb island-orb-chat' }, chatWrap);
  el('div', { id: 'islandChatPanel', hidden: true }, chatWrap);
  el('button', { id: 'islandMoreBtn', class: 'island-orb island-orb-menu', 'aria-expanded': 'false' }, hud);
  el('button', { id: 'islandHudRoleBtn', 'aria-expanded': 'false' }, hud);
  el('button', { id: 'islandMapBtn', 'aria-expanded': 'false' }, hud);
  const exploreEntry = el('button', { id: 'islandExplore', class: 'island-hud-primary island-hud-explore-entry' }, hud);
  const exploreLabel = doc.createElement('span');
  exploreLabel.className = 'island-hud-primary-label';
  exploreEntry.appendChild(exploreLabel);

  const targets = el('div', { class: 'island-hud-menu-targets' }, hud);
  el('button', { id: 'islandHome' }, targets);
  el('button', { id: 'islandListBtn' }, targets);
  el('button', { id: 'islandNickBtn' }, targets);
  el('button', { id: 'islandAvatarBtn', 'aria-expanded': 'false' }, targets);
  const nickPop = el('div', { id: 'islandNickPop', class: 'island-nick', hidden: true }, hud);
  el('input', { id: 'islandNickInput' }, nickPop);
  el('button', { id: 'islandNickSave' }, nickPop);
  const avatarPop = el('div', { id: 'islandAvatarPop', class: 'island-avatar-pop', hidden: true }, hud);
  el('button', { class: 'island-avatar-choice', 'data-island-avatar': 'milu', 'aria-pressed': 'true' }, avatarPop);
  el('button', { class: 'island-avatar-choice', 'data-island-avatar': 'berry', 'aria-pressed': 'false' }, avatarPop);

  const more = el('section', { id: 'islandMoreSheet', hidden: true });
  el('button', { id: 'islandMoreScrim' }, more);
  const panel = el('div', { class: 'island-more-panel' }, more);
  el('button', { id: 'islandMoreClose' }, panel);
  const menu = el('div', { class: 'island-more-menu' }, panel);
  el('button', { class: 'island-more-item island-more-explore', 'data-island-more-target': 'islandExplore', hidden: true }, menu);
  ['islandHome', 'islandListBtn', 'islandNickBtn'].forEach((target) => {
    el('button', { class: 'island-more-item', 'data-island-more-target': target }, menu);
  });
  el('a', { class: 'island-more-item', 'data-guest-only': '', 'data-island-more-login': '', href: '/login' }, menu);
  el('a', { class: 'island-more-item', 'data-auth-only': '', 'data-island-more-logout': '', href: '/logout' }, menu);

  const map = el('section', { id: 'islandMapSheet', hidden: true });
  el('button', { id: 'islandMapScrim' }, map);
  const mapPanel = el('div', { class: 'island-map-panel' }, map);
  el('button', { id: 'islandMapClose' }, mapPanel);

  el('div', { id: 'islandLabels' });
  el('div', { id: 'islandJoystick', class: 'island-joystick' });
  el('input', { id: 'islandChatInput' });
  el('button', { id: 'islandChatSend' });
  el('div', { id: 'islandChatLog' });
  el('p', { id: 'islandChatHint', hidden: true });

  if (guest) doc.body.classList.add('is-guest');
  else doc.body.classList.add('is-authenticated');

  return doc;
}

function hasMenuTargetsAncestor(node) {
  for (let p = node && node.parent; p; p = p.parent) {
    if (typeof p.className === 'string' && p.className.split(/\s+/).includes('island-hud-menu-targets')) return true;
  }
  return false;
}

function popWouldBeVisible(node) {
  return node && !node.hidden && !hasMenuTargetsAncestor(node);
}

test('index 使用 Concept A HUD：玩家卡、右上圓鈕、選單與地圖', () => {
  assert.doesNotMatch(indexHtml, /id="islandChatFab"/);
  assert.doesNotMatch(indexHtml, />蜜鹿</);
  assert.match(indexHtml, /id="islandHudPlayer"/);
  assert.match(indexHtml, /id="islandMoreBtn"/);
  assert.match(indexHtml, /aria-label="選單"/);
  assert.match(indexHtml, /id="islandMapSheet"/);
  assert.match(indexHtml, /id="islandHudRoleBtn"/);
  assert.match(indexHtml, /island-hud-player-name[^>]*>milu</);
});

test('index：角色／暱稱彈層不在隱藏選單靶容器內', () => {
  const start = indexHtml.indexOf('class="island-hud-menu-targets"');
  assert.ok(start >= 0, '有 island-hud-menu-targets');
  const end = indexHtml.indexOf('</div>', start);
  const targetsHtml = indexHtml.slice(start, end);
  assert.doesNotMatch(targetsHtml, /id="islandAvatarPop"/);
  assert.doesNotMatch(targetsHtml, /id="islandNickPop"/);
  assert.match(indexHtml, /id="islandAvatarPop"/);
  assert.match(indexHtml, /id="islandNickPop"/);
});

test('syncAvatarUi：milu 時顯示 milu 而不是蜜鹿', () => {
  const doc = makeMobileDom(true);
  const ui = Corner.createCornerUi({
    document: doc,
    storage: null,
    getPresence: () => null,
    isExploring: () => false,
    isWelcomed: () => false
  });
  ui.syncAvatarUi();
  assert.equal(doc.getElementById('islandHudPlayerName').textContent, 'milu');
  assert.equal(doc.getElementById('islandAvatarBtn').textContent, 'milu');
});

test('360／390 右上 HUD 在 viewport 內且不與搖桿相交', () => {
  for (const width of [360, 390]) {
    const height = 844;
    const joy = Corner.joystickRectMobile(width, height);
    const bounds = Corner.hudTopRightRect(width, height, false);
    assert.ok(Corner.dockFitsViewport(bounds, width), width + 'px 右上 HUD 在畫面內');
    assert.ok(bounds.top >= 0, width + 'px 上緣不為負');
    assert.ok(!Corner.rectsIntersect(bounds, joy), width + 'px 不與搖桿重疊');
    assert.ok(bounds.orbSize >= 44, width + 'px 觸控目標至少 44px');
  }
});

test('右下操作叢與搖桿、離開室內不重疊（390×844）', () => {
  const width = 390;
  const height = 844;
  const joy = Corner.joystickRectMobile(width, height);
  const actions = Corner.hudActionsRect(width, height);
  const leave = Corner.leaveInteriorRectMobile(width, height);
  assert.ok(!Corner.rectsIntersect(actions, joy), '操作叢不與搖桿重疊');
  assert.ok(!Corner.rectsIntersect(leave, joy), '離開室內不與搖桿重疊');
  assert.ok(actions.primary.minTouch >= 44, '主情境鈕觸控至少 44px');
  assert.ok(actions.smallOrb.minTouch >= 44, '小圓鈕觸控至少 44px');
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

test('選單打開後訪客看得到登入，已登入看得到登出與全景／清單／暱稱', () => {
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

test('手機 CSS 有 HUD 圓鈕、搖桿安全區與地圖層級', () => {
  assert.match(islandCss, /\.island-orb/);
  assert.match(islandCss, /safe-area-inset-right/);
  assert.match(islandCss, /safe-area-inset-bottom/);
  assert.doesNotMatch(islandCss, /island-chat-fab/);
});

test('探索時聊天面板邊界不與搖桿相交（360／390）', () => {
  for (const width of [360, 390]) {
    const height = width === 360 ? 780 : 844;
    const panel = Corner.chatPanelRectMobileExplore(width, height, 12);
    assert.ok(panel.left >= panel.joy.right - 0.5, width + 'px 面板左緣避開搖桿');
    assert.ok(!Corner.rectsIntersect(panel, panel.joy), width + 'px 面板與搖桿不重疊');
    assert.ok(panel.left >= 0 && panel.right <= width + 0.5, width + 'px 面板在 viewport 內');
  }
});

test('「選單」打開時 body 有 island-more-open，CSS 隱藏搖桿與 HUD', () => {
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
    /body\.island-more-open \.island-hud[\s\S]*visibility:\s*hidden/
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
  assert.ok(popWouldBeVisible(doc.getElementById('islandAvatarPop')), '角色選單不在 display:none 祖先下');
  assert.match(
    islandCss,
    /body\.island-avatar-open \.island-joystick[\s\S]*pointer-events:\s*none/
  );
  ui.setAvatarOpen(false);
  assert.equal(doc.body.classList.contains('island-avatar-open'), false);
});

test('右下角色鈕與選單暱稱可開啟彈層且可見', () => {
  const doc = makeMobileDom(true);
  const ui = Corner.createCornerUi({
    document: doc,
    storage: null,
    getPresence: () => null,
    isExploring: () => true,
    isWelcomed: () => false
  });
  doc.getElementById('islandHudRoleBtn').click();
  assert.ok(popWouldBeVisible(doc.getElementById('islandAvatarPop')), '角色鈕開啟選單可見');
  ui.setAvatarOpen(false);
  ui.setMoreOpen(true);
  doc.querySelector('[data-island-more-target="islandNickBtn"]').click();
  assert.ok(popWouldBeVisible(doc.getElementById('islandNickPop')), '選單暱稱開啟輸入可見');
  assert.equal(doc.body.classList.contains('island-nick-open'), true);
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

test('選小莓寫入 localStorage 並更新玩家卡', () => {
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
  assert.equal(doc.getElementById('islandHudPlayerName').textContent, '小莓');
  const miluBtn = doc.querySelector('[data-island-avatar="milu"]');
  ui.setAvatarOpen(true);
  miluBtn.click();
  assert.equal(storage.data['punchclock.island.avatar'], 'milu');
  assert.equal(doc.getElementById('islandHudPlayerName').textContent, 'milu');
});

test('選單面板含 safe-area 底內距且層級高於搖桿', () => {
  assert.match(islandCss, /\.island-more-panel[\s\S]*safe-area-inset-bottom/);
  const sheetZ = /\.island-more-sheet\s*\{[^}]*z-index:\s*(\d+)/m.exec(islandCss);
  const joyZ = /\.island-joystick\s*\{[^}]*z-index:\s*(\d+)/m.exec(islandCss);
  assert.ok(sheetZ && joyZ, '可解析 z-index');
  assert.ok(Number(sheetZ[1]) > Number(joyZ[1]), '選單在搖桿之上');
  assert.match(islandCss, /body\.is-exploring \.island-hud-tr \.island-chat/);
});

test('探索中不再隱藏 #islandExplore（Concept A 退出探索）', () => {
  assert.doesNotMatch(
    islandCss,
    /body\.is-exploring \.island-hud-explore-entry\s*\{[^}]*display:\s*none/
  );
  assert.doesNotMatch(islandJs, /btn\.hidden = exploring/);
  assert.match(islandJs, /退出探索/);
});

test('syncExploreHudLabels：探索中顯示「退出探索」', () => {
  const doc = makeMobileDom(true);
  const exploreBtn = doc.getElementById('islandExplore');
  const label = exploreBtn.querySelector('.island-hud-primary-label');
  const ui = Corner.createCornerUi({
    document: doc,
    storage: null,
    getPresence: () => null,
    isExploring: () => true,
    isWelcomed: () => false
  });
  ui.syncExploreHudLabels();
  assert.equal(exploreBtn.getAttribute('aria-label'), '退出探索模式');
  const visibleLabel = label && label.textContent ? label.textContent : exploreBtn.textContent;
  assert.equal(visibleLabel, '退出探索');
  const moreRow = doc.querySelector('.island-more-explore');
  assert.equal(moreRow.textContent, '退出探索');
  assert.equal(moreRow.hidden, false);
});

test('探索按鈕切換（與 island.js click 同路徑）會離開探索', () => {
  const flow = Explore.createExploreFlow({});
  flow.dispatch('enter');
  assert.equal(flow.mode.exploring, true);
  const clickExploreToggle = () => {
    const next = !flow.mode.exploring;
    flow.dispatch(next ? 'enter' : 'exit');
  };
  clickExploreToggle();
  assert.equal(flow.mode.exploring, false);
});

test('戶外探索提示含 Esc 退出探索', () => {
  assert.ok(Interior.exploreHintText(false, false).includes('Esc 退出探索'));
  assert.match(indexHtml, /Esc 退出探索/);
});

test('1280×720 與 390×844「退出探索」不與搖桿、右上 HUD 重疊', () => {
  for (const [w, h] of [[1280, 720], [390, 844]]) {
    const btn = Corner.exploreExitButtonRect(w, h);
    assert.ok(!Corner.rectsIntersect(btn, btn.joy), w + ' 退出探索與搖桿不重疊');
    assert.ok(!Corner.rectsIntersect(btn, btn.tr), w + ' 退出探索與右上圓鈕不重疊');
    assert.ok(btn.left >= btn.joy.right - 0.5, w + ' 退出探索在搖桿右側');
    assert.ok(btn.left >= 0 && btn.right <= w + 0.5, w + ' 退出探索在 viewport 內');
  }
});