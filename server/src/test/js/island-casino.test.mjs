// 賭場大廳純邏輯：node --test server/src/test/js/island-casino.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const jsDir = join(here, '..', '..', 'main', 'resources', 'public', 'js');
const modulePath = join(jsDir, 'island-casino.js');
const islandSource = readFileSync(join(jsDir, 'island.js'), 'utf8');
const source = readFileSync(modulePath, 'utf8');
const C = await loadPublicModule('island-casino.js');

class FakeEvent {
  constructor(type) { this.type = type; this.defaultPrevented = false; }
  preventDefault() { this.defaultPrevented = true; }
}

class FakeClassList {
  constructor() { this.set = new Set(); }
  add(c) { this.set.add(c); }
  remove(c) { this.set.delete(c); }
  toggle(c, force) {
    const on = force === undefined ? !this.set.has(c) : !!force;
    if (on) this.set.add(c); else this.set.delete(c);
    return on;
  }
}

function wireDomTree(node) {
  node.querySelector = (sel) => {
    const wantAttr = /^\[([\w-]+)\]$/.exec(sel);
    const walk = (n) => {
      if (wantAttr && n.hasAttribute && n.hasAttribute(wantAttr[1])) return n;
      for (const c of n.children || []) {
        const hit = walk(c);
        if (hit) return hit;
      }
      return null;
    };
    return walk(node);
  };
  node.querySelectorAll = (sel) => {
    const wantAttr = /^\[([\w-]+)\]$/.exec(sel);
    const out = [];
    const walk = (n) => {
      if (wantAttr && n.hasAttribute && n.hasAttribute(wantAttr[1])) out.push(n);
      for (const c of n.children || []) walk(c);
    };
    walk(node);
    return out;
  };
}

class FakeElement {
  constructor(doc, tag, attrs) {
    this.ownerDocument = doc;
    this.tagName = tag.toUpperCase();
    this.listeners = {};
    this.children = [];
    this.hidden = false;
    this.textContent = '';
    this.classList = new FakeClassList();
    this.dataset = {};
    this.attrs = {};
    Object.entries(attrs || {}).forEach(([k, v]) => {
      if (k === 'id') this.id = v;
      if (k === 'hidden') this.hidden = !!v;
      this.attrs[k] = String(v);
    });
    wireDomTree(this);
  }
  appendChild(child) { this.children.push(child); return child; }
  hasAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k); }
  setAttribute(k, v) { this.attrs[k] = String(v); }
  getAttribute(k) { return Object.prototype.hasOwnProperty.call(this.attrs, k) ? this.attrs[k] : null; }
  addEventListener(type, fn) { (this.listeners[type] = this.listeners[type] || []).push(fn); }
  dispatchEvent(event) {
    for (const fn of (this.listeners[event.type] || [])) fn.call(this, event);
    return !event.defaultPrevented;
  }
  click() { this.dispatchEvent(new FakeEvent('click')); }
}

function makePromptDoc() {
  const doc = { body: null };
  doc.createElement = (tag) => new FakeElement(doc, tag);
  doc.body = new FakeElement(doc, 'body');
  doc.body.classList = new FakeClassList();
  const append = (tag, attrs, parent) => (parent || doc.body).appendChild(new FakeElement(doc, tag, attrs));
  const lobby = append('section', { id: 'casinoLobby', hidden: true, 'aria-hidden': 'true' });
  append('button', { 'data-casino-back': '' }, lobby);
  append('button', { id: 'islandInteract', hidden: true });
  append('button', { id: 'islandListBtn' });
  append('aside', { id: 'islandList', hidden: true });
  doc.getElementById = (id) => {
    const walk = (node) => {
      if (node.id === id) return node;
      for (const c of node.children || []) {
        const hit = walk(c);
        if (hit) return hit;
      }
      return null;
    };
    return walk(doc.body);
  };
  doc.querySelectorAll = (sel) => doc.body.querySelectorAll(sel);
  return doc;
}

test('lobbyState：開關大廳', () => {
  const init = C.lobbyState(null, '');
  assert.deepEqual(init, { open: false });
  assert.equal(C.isLobbyOpen(init), false);
  const open = C.lobbyState(init, 'open');
  assert.equal(C.isLobbyOpen(open), true);
  assert.deepEqual(C.lobbyState(open, 'close'), init);
});

test('chipWalletView：啟用練習籌碼時顯示餘額且註明不是真錢', () => {
  const placeholder = C.chipWalletView(null);
  assert.equal(placeholder.enabled, false);
  assert.equal(placeholder.balance, null);
  assert.match(placeholder.text, /尚未啟用/);
  assert.ok(placeholder.note);

  const live = C.chipWalletView({ enabled: true, balance: 500, currency: '練習籌碼' });
  assert.equal(live.enabled, true);
  assert.equal(live.balance, 500);
  assert.equal(live.text, '500');
  assert.match(live.note, /不是真錢/);
});

test('21 點可玩；老虎機仍即將推出', () => {
  const bj = C.CASINO_GAMES.find((g) => g.id === 'blackjack');
  const slots = C.CASINO_GAMES.find((g) => g.id === 'slots');
  assert.ok(bj);
  assert.ok(slots);
  assert.equal(bj.playable, true);
  assert.notEqual(bj.status, 'soon');
  assert.equal(slots.playable, false);
  assert.equal(slots.status, 'soon');
});

test('playerLobbyLabel：訪客與店長文案', () => {
  assert.equal(C.playerLobbyLabel(null), '訪客');
  assert.equal(C.playerLobbyLabel({ authenticated: false }), '訪客');
  assert.match(C.playerLobbyLabel({ authenticated: true }), /店長/);
});

test('island-casino 匯入 island-blackjack', () => {
  assert.match(source, /from '\.\/island-blackjack\.js'/);
  assert.equal(C.MIN_BET, 10);
  assert.equal(C.STARTING_BALANCE, 1000);
});

test('promptDomUpdate：相同 visible＋label 不寫入', () => {
  const prev = { visible: true, label: '搭船到沙灘島' };
  assert.deepEqual(C.promptDomUpdate(prev, prev), {});
  assert.deepEqual(C.promptDomUpdate(prev, { visible: true, label: '搭船到沙灘島' }), {});
});

test('promptDomUpdate：label 改變只寫文字與 aria-label', () => {
  const prev = { visible: true, label: '搭船到沙灘島' };
  const patch = C.promptDomUpdate(prev, { visible: true, label: '搭船回神之島' });
  assert.deepEqual(patch, {
    textContent: '搭船回神之島',
    ariaLabel: '搭船回神之島'
  });
});

test('promptDomUpdate：visible 改變只寫 hidden 與 aria-hidden', () => {
  const prev = { visible: false, label: '進入賭場' };
  assert.deepEqual(C.promptDomUpdate(prev, { visible: true, label: '進入賭場' }), {
    hidden: false,
    ariaHidden: 'false'
  });
  assert.deepEqual(C.promptDomUpdate({ visible: true, label: '進入賭場' }, { visible: false, label: '進入賭場' }), {
    hidden: true,
    ariaHidden: 'true'
  });
});

test('setPromptVisible：連續相同呼叫不重寫 textContent', () => {
  let promptState = { visible: false, label: '進入賭場' };
  let textWrites = 0;
  const label = '搭船到沙灘島';
  for (let i = 0; i < 60; i += 1) {
    const next = { visible: true, label };
    const patch = C.promptDomUpdate(promptState, next);
    if ('textContent' in patch) textWrites += 1;
    if (Object.keys(patch).length > 0) promptState = next;
  }
  assert.equal(textWrites, 1, 'textContent 只應寫入一次');
});

test('island-casino：#islandInteract 不再於 onPrompt 失敗時一律 openLobby', () => {
  const block = /if \(promptBtn\) \{[\s\S]*?promptBtn\.addEventListener\('click'/;
  const handler = block.exec(source);
  assert.ok(handler, '應有 promptBtn click 處理');
  const clickBlock = source.slice(handler.index, handler.index + 320);
  assert.doesNotMatch(clickBlock, /openLobby\(\)/);
});

test('island.js：onPrompt 在 hitInteractZone 前先處理下車', () => {
  assert.match(
    islandSource,
    /onPrompt: \(\) => \{[\s\S]*?isSteering\(\)[\s\S]*?isDriving\(\)[\s\S]*?hitInteractZone/
  );
});

test('開車且無互動區：點「下車」會下車、不開練習大廳', () => {
  const doc = makePromptDoc();
  let disembarked = false;
  const onPrompt = () => {
    disembarked = true;
    return true;
  };
  const casino = C.createCasinoController({
    doc,
    win: { PUNCHCLOCK_AUTHENTICATED: false, localStorage: { getItem: () => null, setItem: () => {} } },
    hooks: { onPrompt }
  });
  casino.setPromptVisible(true, '下車');
  doc.getElementById('islandInteract').click();
  assert.equal(disembarked, true);
  assert.equal(casino.isOpen(), false);
});

test('未知或無互動區（非開車）：點提示不開任何 overlay', () => {
  const doc = makePromptDoc();
  let called = false;
  const casino = C.createCasinoController({
    doc,
    win: { PUNCHCLOCK_AUTHENTICATED: false, localStorage: { getItem: () => null, setItem: () => {} } },
    hooks: { onPrompt: () => { called = true; return false; } }
  });
  casino.setPromptVisible(true, '互動');
  doc.getElementById('islandInteract').click();
  assert.equal(called, true);
  assert.equal(casino.isOpen(), false);
});

test('賭場門口 enterInterior：onPrompt 回 true 不開練習大廳', () => {
  const doc = makePromptDoc();
  let entered = null;
  const onPrompt = () => {
    const zone = { id: 'casino', action: 'enterInterior' };
    if (zone.action === 'enterInterior') {
      entered = zone.id;
      return true;
    }
    return false;
  };
  const casino = C.createCasinoController({
    doc,
    win: { PUNCHCLOCK_AUTHENTICATED: false, localStorage: { getItem: () => null, setItem: () => {} } },
    hooks: { onPrompt }
  });
  casino.setPromptVisible(true, '進入賭場');
  doc.getElementById('islandInteract').click();
  assert.equal(entered, 'casino');
  assert.equal(casino.isOpen(), false);
});