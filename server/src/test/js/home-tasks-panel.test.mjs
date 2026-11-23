import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const homeTasks = await loadPublicModule('home-tasks-panel.js');

function miniDoc() {
  let openState = true;
  const body = {
    classList: { add() {}, remove() {}, toggle() {} }
  };
  const panel = {
    addEventListener() {}
  };
  const closeBtn = {
    addEventListener() {},
    closest(sel) {
      return sel === '[data-tasks-close]' ? this : null;
    }
  };
  const header = { contains() { return true; } };
  const tasksBoard = {
    id: 'tasksBoard',
    hidden: false,
    hiddenAttr: false,
    attributes: new Map(),
    addEventListener(ev, fn) {
      if (ev === 'click') this._click = fn;
    },
    setAttribute(k, v) {
      this.attributes.set(k, v);
    },
    querySelector(sel) {
      if (sel === '.home-tasks-panel') return panel;
      return null;
    },
    _click: null
  };
  Object.defineProperty(tasksBoard, 'hidden', {
    get() { return this.hiddenAttr; },
    set(v) { this.hiddenAttr = v; }
  });
  let notifyClosed = 0;
  const officeModal = {
    notifyOpened() {},
    notifyClosed() { notifyClosed += 1; }
  };
  const panelClicks = [];
  panel.addEventListener = (ev, fn) => {
    if (ev === 'click') panel._click = fn;
  };
  const doc = {
    body,
    getElementById(id) {
      return id === 'tasksBoard' ? tasksBoard : null;
    },
    addEventListener() {},
    removeEventListener() {}
  };
  return { doc, tasksBoard, panel, closeBtn, officeModal, panelClicks, getNotifyClosed: () => notifyClosed };
}

test('任務窗：尚未觸發時實際觸發不複製設定時間', () => {
  const dash = readFileSync(join(publicRoot, 'js/dashboard.js'), 'utf8');
  assert.match(dash, /function formatActualTriggerTime\(t\)/);
  assert.doesNotMatch(dash, /actualTimeStr = \(t\.actualTime \|\| t\.targetTime/);
  assert.match(dash, /if \(!t \|\| !t\.actualTime\) return '—'/);
});

test('任務窗：展開後仍可點 × 關閉', () => {
  const { doc, panel, closeBtn, officeModal, getNotifyClosed } = miniDoc();
  const ctrl = homeTasks.createHomeTasksController({ doc, officeModal });
  ctrl.open();
  assert.equal(ctrl.isOpen(), true);
  panel._click({ target: closeBtn, preventDefault() {} });
  assert.equal(ctrl.isOpen(), false);
  assert.equal(getNotifyClosed(), 1);
});

test('任務窗：Esc 會解鎖 office modal', () => {
  const keys = [];
  const { doc, officeModal, getNotifyClosed } = miniDoc();
  doc.addEventListener = (ev, fn) => {
    if (ev === 'keydown') keys.push(fn);
  };
  const ctrl = homeTasks.createHomeTasksController({ doc, officeModal });
  ctrl.open();
  keys[0]({ key: 'Escape', preventDefault() {} });
  assert.equal(ctrl.isOpen(), false);
  assert.equal(getNotifyClosed(), 1);
});

test('首頁 closeTasks 不再靜默關閉', () => {
  const js = readFileSync(join(publicRoot, 'js/office-page.js'), 'utf8');
  assert.match(js, /closeTasks:\s*\(\)\s*=>\s*tasks\.close\(\{\s*fromManager:\s*true\s*\}\)/);
  assert.doesNotMatch(js, /closeTasks:.*silent:\s*true/);
});

test('任務面板標題列固定，內容區捲動', () => {
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  assert.match(css, /\.home-tasks-panel\s*\{[^}]*overflow:\s*hidden/);
  assert.match(css, /\.home-tasks-panel \.roadmap-win-head\s*\{[^}]*position:\s*sticky/);
});