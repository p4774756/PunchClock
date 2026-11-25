import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const Roadmap = await loadPublicModule('roadmap-panel.js');

function fakeDoc() {
  const classes = new Set();
  const keys = [];
  const doc = {
    body: {
      classList: {
        toggle(name, on) {
          if (on) classes.add(name);
          else classes.delete(name);
        },
        contains(name) { return classes.has(name); }
      }
    },
    getElementById() { return null; },
    querySelectorAll() { return []; },
    addEventListener(ev, fn) {
      if (ev === 'keydown') keys.push(fn);
    }
  };
  return { doc, classes, keys };
}

test('頂層 onChange 會接到開合，Esc 可關', () => {
  const { doc, classes, keys } = fakeDoc();
  const seen = [];
  const roadmap = Roadmap.createRoadmapController({
    doc,
    win: {},
    fetch: () => Promise.reject(new Error('skip')),
    onChange: ({ open }) => seen.push(open)
  });
  roadmap.open();
  assert.deepEqual(seen, [true]);
  assert.equal(classes.has('roadmap-open'), true);
  assert.equal(roadmap.isOpen(), true);
  keys[0]({ key: 'Escape', preventDefault() {} });
  assert.deepEqual(seen, [true, false]);
  assert.equal(classes.has('roadmap-open'), false);
  assert.equal(roadmap.isOpen(), false);
});

test('hooks.onChange 仍優先於頂層', () => {
  const { doc } = fakeDoc();
  const seen = [];
  const roadmap = Roadmap.createRoadmapController({
    doc,
    win: {},
    fetch: () => Promise.reject(new Error('skip')),
    onChange: () => seen.push('top'),
    hooks: { onChange: () => seen.push('hooks') }
  });
  roadmap.open();
  assert.deepEqual(seen, ['hooks']);
});

test('開啟時鎖住背景捲動，版本提示與無可見收合文案', () => {
  const css = readFileSync(join(publicRoot, 'css/roadmap.css'), 'utf8');
  assert.match(css, /body\.roadmap-open\s*\{[^}]*overflow:\s*hidden/);
  assert.match(css, /body\.roadmap-open\s*\{[^}]*overflow:\s*clip/);
  const dash = readFileSync(join(publicRoot, 'js/dashboard.js'), 'utf8');
  assert.match(dash, /class="app-version-hint"/);
  assert.match(dash, /aria-label="/);
  assert.match(dash, /deviceFoldAriaLabel/);
  assert.doesNotMatch(dash, /fold-hint/);
  assert.doesNotMatch(dash, /!isCollapsed && !ev\.target\.closest\('\.device-head'\)/);
  assert.doesNotMatch(dash, /replace\(\/\^\\?open=roadmap/);
});
