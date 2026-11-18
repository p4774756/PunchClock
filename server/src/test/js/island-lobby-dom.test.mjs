// 賭場大廳 × 探索模式 DOM 事件整合測試：node --test server/src/test/js/island-lobby-dom.test.mjs
// 用最小假 DOM 驅動真正的 createCasinoController（按鈕 click）與 createExploreFlow，
// 並模擬 dashboard.js 的 switchDashTab → PunchClockIsland.focus／reset。
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const jsDir = join(here, '..', '..', 'main', 'resources', 'public', 'js');
async function load(name) {
  return loadPublicModule(name);
}
const X = await load('island-explore.js');
const C = await load('island-casino.js');
const B = await load('island-blackjack.js');
const islandSource = readFileSync(join(jsDir, 'island.js'), 'utf8');
const exploreInputSource = readFileSync(join(jsDir, 'island-explore-input.js'), 'utf8');

class FakeEvent {
  constructor(type, init) {
    this.type = type;
    this.key = init && init.key;
    this.target = null;
    this.defaultPrevented = false;
  }
  preventDefault() { this.defaultPrevented = true; }
}

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
    this.disabled = false;
    this.textContent = '';
    this.className = '';
    this.type = '';
    this.dataset = {};
    this.classList = new FakeClassList();
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (k === 'hidden') this.hidden = !!v;
      else this.setAttribute(k, v);
    });
  }
  get id() { return this.attrs.id || ''; }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; }
  hasAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k); }
  removeAttribute(k) { delete this.attrs[k]; }
  appendChild(child) { child.parent = this; this.children.push(child); return child; }
  set innerHTML(_) { this.children = []; }
  addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); }
  removeEventListener(type, fn) {
    this.listeners[type] = (this.listeners[type] || []).filter((f) => f !== fn);
  }
  dispatchEvent(event) {
    if (!event.target) event.target = this;
    for (let node = this; node; node = node.parent) {
      (node.listeners[event.type] || []).slice().forEach((fn) => fn.call(node, event));
    }
    return !event.defaultPrevented;
  }
  click() { this.dispatchEvent(new FakeEvent('click')); }
  focus() { this.ownerDocument.activeElement = this; }
  matches(sel) {
    if (sel.startsWith('#')) return this.id === sel.slice(1);
    const attr = /^\[([\w-]+)\]$/.exec(sel);
    if (attr) return this.hasAttribute(attr[1]);
    return this.tagName === sel.toUpperCase();
  }
  querySelectorAll(sel) {
    const out = [];
    const walk = (node) => node.children.forEach((c) => {
      if (c.matches(sel)) out.push(c);
      walk(c);
    });
    walk(this);
    return out;
  }
  querySelector(sel) { return this.querySelectorAll(sel)[0] || null; }
}

function makeDocument() {
  const doc = { activeElement: null };
  doc.createElement = (tag) => new FakeElement(doc, tag);
  doc.body = new FakeElement(doc, 'body');
  doc.getElementById = (id) => doc.body.querySelector('#' + id);
  doc.querySelectorAll = (sel) => doc.body.querySelectorAll(sel);
  doc.querySelector = (sel) => doc.body.querySelector(sel);
  const el = (tag, attrs, parent) => (parent || doc.body).appendChild(new FakeElement(doc, tag, attrs));

  const lobby = el('section', { id: 'casinoLobby', hidden: true, 'aria-hidden': 'true' });
  el('button', { 'data-casino-back': '' }, lobby);
  el('strong', { 'data-casino-player': '' }, lobby);
  el('strong', { 'data-casino-chips': '' }, lobby);
  el('small', { 'data-casino-chips-note': '' }, lobby);
  el('p', { 'data-casino-sub': '' }, lobby);
  el('div', { 'data-casino-games': '' }, lobby);
  el('div', { 'data-casino-blackjack': '', hidden: true }, lobby);
  el('button', { id: 'islandInteract', hidden: true });
  el('button', { id: 'islandListBtn', 'aria-expanded': 'false' });
  const list = el('aside', { id: 'islandList', hidden: true });
  el('button', { 'data-open-casino': '' }, list);
  el('section', { id: 'islandSheet', hidden: true });
  return doc;
}

function fakeChipStorage(balance) {
  const map = new Map();
  if (balance != null) {
    map.set('punchclock.casino.practiceChips', String(balance));
  }
  return {
    setItem: (k, v) => map.set(k, v),
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    removeItem: (k) => map.delete(k)
  };
}

/** 依 island.js 的接線方式組裝；camera 記錄鏡頭動作，keys 代表按住中的移動鍵。 */
function setup(opts) {
  const doc = makeDocument();
  const win = { PUNCHCLOCK_AUTHENTICATED: false, requestAnimationFrame: (fn) => fn() };
  const sheet = doc.getElementById('islandSheet');
  const camera = [];
  const lobbyEvents = [];
  const player = { keys: {} };
  let casino = null;

  const flow = X.createExploreFlow({
    clearMoveInput: () => { player.keys = {}; },
    followPlayer: () => camera.push('follow'),
    showPose: (id) => camera.push('pose:' + id),
    showHome: () => camera.push('home'),
    sheetOpen: () => !sheet.hidden,
    lobbyOpen: () => !!(casino && casino.isOpen()),
    closeSheet: () => switchDashTab('')
  });

  // dashboard.js：先改 sheet，再呼叫 PunchClockIsland.focus／reset
  function switchDashTab(id) {
    sheet.hidden = !id;
    if (id) flow.focusPanel(id);
    else flow.resetPanel();
  }

  casino = C.createCasinoController({
    doc: doc,
    win: win,
    storage: opts && opts.storage,
    hooks: {
      beforeOpen: () => flow.beforeLobbyOpen(),
      onLobbyChange: (state) => {
        lobbyEvents.push(state.open ? 'open' : 'close');
        flow.lobbyChanged(state.open);
      }
    }
  });

  // island.js 的 Escape 處理順序：大廳 → 管理面板 → 退出探索
  function pressEscape() {
    if (casino.isTableOpen && casino.isTableOpen()) {
      casino.closeTable();
      return;
    }
    if (casino.isOpen()) { casino.closeLobby(); return; }
    if (!sheet.hidden) switchDashTab('');
  }

  return {
    doc, sheet, camera, lobbyEvents, player, flow, casino, switchDashTab, pressEscape,
    lobbyEl: doc.getElementById('casinoLobby'),
    backBtn: doc.querySelector('[data-casino-back]'),
    promptBtn: doc.getElementById('islandInteract'),
    listEntry: doc.querySelector('[data-open-casino]'),
    enterExplore() { flow.dispatch('enter'); camera.length = 0; }
  };
}

test('island.js 的接線與本測試一致', () => {
  assert.match(islandSource, /beforeOpen: \(\) => \{[\s\S]*?flow\.beforeLobbyOpen\(\)/);
  assert.match(islandSource, /onLobbyChange: \(state\) => flow\.lobbyChanged\(state\.open\)/);
  assert.match(islandSource, /onPrompt: \(\) => \{[\s\S]*?openGraffiti\(\)/);
  assert.match(
    exploreInputSource,
    /ctx\.roadmap\.close\(\);[\s\S]*ctx\.graffiti\.close\(\);[\s\S]*isTypingTarget\(event\.target\)[\s\S]*ctx\.casino && ctx\.casino\.isOpen\(\)/
  );
  assert.match(islandSource, /createExploreKeys\(/);
  assert.match(islandSource, /onPrompt: \(\) => \{[\s\S]*?openRoadmap\(\)/);
  assert.match(islandSource, /function updatePlayer\(dt\) \{[\s\S]*?if \(isSailing\(\)\)/);
  assert.match(islandSource, /createExploreJoystick\([\s\S]*canMove: \(\) => canExploreMove\(\)/);
  assert.match(islandSource, /function reset\(\) \{\s*flow\.resetPanel\(\);/);
  assert.match(islandSource, /flow\.focusPanel\(id\)/);
  assert.doesNotMatch(islandSource, /afterClose/, '關閉流程只能走 onLobbyChange');
});

test('探索中點入口提示開大廳：不可移動；點「返回島嶼」恢復角色與鏡頭', () => {
  const t = setup();
  t.enterExplore();
  t.player.keys.KeyW = true;
  t.casino.setPromptVisible(true, '進入賭場');

  t.promptBtn.click();
  assert.equal(t.casino.isOpen(), true);
  assert.equal(t.lobbyEl.hidden, false);
  assert.ok(t.doc.body.classList.contains('casino-lobby-open'));
  assert.equal(t.promptBtn.hidden, true);
  assert.equal(t.doc.activeElement, t.backBtn, '焦點移到返回按鈕');
  assert.deepEqual(t.player.keys, {}, '開大廳時清除按住的移動鍵');
  assert.equal(t.flow.canMove(), false);
  assert.equal(X.playerVisible(t.flow.mode), true);

  t.backBtn.click();
  assert.equal(t.casino.isOpen(), false);
  assert.equal(t.lobbyEl.hidden, true);
  assert.ok(!t.doc.body.classList.contains('casino-lobby-open'));
  assert.equal(t.flow.canMove(), true, '返回島嶼後恢復移動');
  assert.deepEqual(t.camera, ['follow'], '鏡頭回到角色身上');
  assert.deepEqual(t.lobbyEvents, ['open', 'close']);
});

test('管理面板開啟時從清單進入大廳：面板 reset 不會解除大廳鎖定', () => {
  const t = setup();
  t.enterExplore();
  t.switchDashTab('devices');
  assert.equal(t.sheet.hidden, false);
  assert.equal(t.flow.mode.paused, true);
  t.camera.length = 0;

  t.doc.getElementById('islandList').hidden = false;
  t.listEntry.click();
  assert.equal(t.sheet.hidden, true, '進大廳時關閉管理面板');
  assert.equal(t.doc.getElementById('islandList').hidden, true);
  assert.equal(t.casino.isOpen(), true);
  assert.equal(t.flow.mode.paused, false);
  assert.equal(t.flow.mode.lobby, true);
  assert.equal(t.flow.canMove(), false, '大廳下仍不可移動');
  assert.deepEqual(t.camera, [], '面板 reset 不把鏡頭拉回角色');

  t.pressEscape();
  assert.equal(t.casino.isOpen(), false);
  assert.equal(t.flow.canMove(), true, 'Escape 關大廳後恢復');
  assert.deepEqual(t.camera, ['follow']);
});

test('開大廳後外部 reset／focus 呼叫不會解除鎖定；程式關閉走同一流程', () => {
  const t = setup();
  t.enterExplore();
  t.casino.openLobby();
  assert.equal(t.flow.canMove(), false);

  t.flow.resetPanel();
  assert.equal(t.flow.canMove(), false, 'reset 後仍鎖定');
  t.switchDashTab('system');
  t.switchDashTab('');
  assert.equal(t.flow.canMove(), false, 'focus → reset 後仍鎖定');
  assert.ok(!t.camera.includes('follow'));

  t.casino.closeLobby();
  assert.equal(t.flow.canMove(), true);
  assert.equal(t.camera[t.camera.length - 1], 'follow');
  assert.deepEqual(t.lobbyEvents, ['open', 'close']);
});

test('大廳上又開管理面板：關大廳後維持暫停，關面板才恢復', () => {
  const t = setup();
  t.enterExplore();
  t.casino.openLobby();
  t.switchDashTab('photos');
  t.backBtn.click();
  assert.equal(t.casino.isOpen(), false);
  assert.equal(t.flow.canMove(), false, '面板還開著');
  assert.ok(!t.camera.includes('follow'));

  t.pressEscape();
  assert.equal(t.sheet.hidden, true);
  assert.equal(t.flow.canMove(), true);
  assert.equal(t.camera[t.camera.length - 1], 'follow');
});

test('全景進大廳：返回後仍是全景、不顯示角色', () => {
  const t = setup();
  t.listEntry.click();
  assert.equal(t.casino.isOpen(), true);
  t.backBtn.click();
  assert.equal(t.casino.isOpen(), false);
  assert.equal(t.flow.mode.exploring, false);
  assert.equal(X.playerVisible(t.flow.mode), false);
  assert.equal(t.flow.canMove(), false);
  assert.ok(!t.camera.includes('follow'), '不切到角色鏡頭');
});

function queryClass(root, className) {
  const walk = (node) => {
    for (const child of node.children) {
      const parts = (child.className || '').split(/\s+/).filter(Boolean);
      if (parts.includes(className)) return child;
      const found = walk(child);
      if (found) return found;
    }
    return null;
  };
  return walk(root);
}

function openBlackjackTable(t) {
  t.casino.openLobby();
  const games = t.lobbyEl.querySelector('[data-casino-games]');
  const card = games.children[0];
  const playBtn = card.children[card.children.length - 1];
  playBtn.click();
  return t.lobbyEl.querySelector('[data-casino-blackjack]');
}

test('21 點：補領按鈕僅下注階段且餘額不足時顯示', () => {
  const tOk = setup({ storage: fakeChipStorage(1000) });
  const tableOk = openBlackjackTable(tOk);
  const refillOk = queryClass(tableOk, 'casino-bj-refill');
  const betRowOk = queryClass(tableOk, 'casino-bj-bet');
  const actionsOk = queryClass(tableOk, 'casino-bj-actions');
  const dealOk = actionsOk && actionsOk.children[0];
  assert.ok(refillOk);
  assert.equal(refillOk.hidden, true, '餘額 1000 時補領按鈕 hidden');
  assert.equal(betRowOk.hidden, false);
  assert.equal(dealOk.disabled, false);
  assert.equal(B.needsPracticeChipRefill(1000), false);
  refillOk.click();
  assert.equal(tOk.casino.lobby.getBalance(), 1000, '餘額足夠時點補領不生效');

  const tBroke = setup({ storage: fakeChipStorage(5) });
  const tableLow = openBlackjackTable(tBroke);
  const refillLow = queryClass(tableLow, 'casino-bj-refill');
  const betRowLow = queryClass(tableLow, 'casino-bj-bet');
  const insufficient = queryClass(tableLow, 'casino-bj-insufficient');
  const actionsLow = queryClass(tableLow, 'casino-bj-actions');
  const dealLow = actionsLow && actionsLow.children[0];
  assert.equal(refillLow.hidden, false, '餘額 5 時補領按鈕可見');
  assert.equal(betRowLow.hidden, true);
  assert.equal(insufficient.hidden, false);
  assert.equal(dealLow.disabled, true);
  assert.equal(B.needsPracticeChipRefill(5), true);
  refillLow.click();
  assert.equal(tBroke.casino.lobby.getBalance(), B.STARTING_BALANCE);
});

test('21 點牌桌：可玩按鈕啟用；Escape 先關牌桌再關大廳', () => {
  const t = setup();
  t.enterExplore();
  t.casino.openLobby();
  const games = t.lobbyEl.querySelector('[data-casino-games]');
  const card = games.children[0];
  assert.equal(card.getAttribute('data-game'), 'blackjack');
  const playBtn = card.children[card.children.length - 1];
  assert.equal(playBtn.disabled, false);
  assert.equal(playBtn.textContent, '開始遊玩');
  playBtn.click();
  assert.equal(t.casino.isTableOpen(), true);
  const table = t.lobbyEl.querySelector('[data-casino-blackjack]');
  assert.equal(table.hidden, false);
  assert.equal(games.hidden, true, '開桌後隱藏遊戲列表');

  t.casino.closeTable();
  assert.equal(games.hidden, false, '返回大廳後顯示遊戲列表');
  assert.equal(table.hidden, true);

  playBtn.click();
  assert.equal(games.hidden, true);

  t.pressEscape();
  assert.equal(t.casino.isTableOpen(), false);
  assert.equal(t.casino.isOpen(), true);
  t.pressEscape();
  assert.equal(t.casino.isOpen(), false);
});

test('三種關閉方式都只觸發一次關閉流程；重複開啟不重複觸發', () => {
  for (const close of ['back', 'escape', 'api']) {
    const t = setup();
    t.enterExplore();
    t.promptBtn.click();
    t.casino.openLobby();
    t.listEntry.click();
    if (close === 'back') t.backBtn.click();
    if (close === 'escape') t.pressEscape();
    if (close === 'api') t.casino.closeLobby();
    t.casino.closeLobby();
    assert.deepEqual(t.lobbyEvents, ['open', 'close'], close);
    assert.deepEqual(t.camera, ['follow'], close);
    assert.equal(t.flow.canMove(), true, close);
  }
});

test('室內開牌桌：返回遊樂場與返回島嶼都只關 overlay 不殘留大廳', () => {
  const t = setup();
  t.enterExplore();
  t.casino.lobby.openBlackjack(true);
  assert.equal(t.casino.isTableOpen(), true);
  assert.equal(t.casino.isOpen(), true);
  const tableBack = t.lobbyEl.querySelector('[data-casino-table-back]');
  assert.ok(tableBack);
  assert.equal(tableBack.textContent, '返回遊樂場');
  tableBack.click();
  assert.equal(t.casino.isTableOpen(), false);
  assert.equal(t.casino.isOpen(), false);
  assert.equal(t.lobbyEl.hidden, true);

  t.casino.lobby.openBlackjack(true);
  assert.equal(t.casino.isTableOpen(), true);
  t.backBtn.click();
  assert.equal(t.casino.isTableOpen(), false);
  assert.equal(t.casino.isOpen(), false);
  assert.deepEqual(t.lobbyEvents, ['open', 'close', 'open', 'close']);
  assert.equal(t.flow.canMove(), true);
});
