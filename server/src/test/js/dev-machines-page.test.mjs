import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const D = await loadPublicModule('dev-machines.js');
const F = await loadPublicModule('dev-machines-format.js');

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('主控台載入 dev-guest-ui，toast 樣式類名存在', () => {
  const consoleHtml = readFileSync(join(publicRoot, 'console.html'), 'utf8');
  assert.match(consoleHtml, /href="\/css\/dev-guest-ui\.css"/);
  const css = readFileSync(join(publicRoot, 'css/dev-guest-ui.css'), 'utf8');
  assert.match(css, /\.dev-login-toast/);
  assert.match(css, /z-index:\s*10000/);

  const doc = {
    body: { childNodes: [], appendChild(c) { this.childNodes.push(c); } },
    createElement() {
      return {
        className: '',
        classList: { _c: new Set(), add(x) { this._c.add(x); }, remove(x) { this._c.delete(x); } },
        childNodes: [],
        attributes: {},
        appendChild(c) { this.childNodes.push(c); return c; },
        setAttribute(k, v) { this.attributes[k] = v; },
        addEventListener() {}
      };
    },
    querySelector(sel) {
      if (sel === '[data-dev-login-toast]') {
        return doc.body.childNodes.find((n) => n.attributes && n.attributes['data-dev-login-toast']) || null;
      }
      return null;
    }
  };
  F.showLoginRequiredToast(doc, '/login');
  assert.equal(doc.body.childNodes.length, 1);
  const toast = doc.body.childNodes[0];
  assert.ok(String(toast.className).includes('dev-login-toast'));
  assert.ok(String(toast.className).includes('maple-window'));
});

test('管理員登入後隱藏頁尾管理員登入鈕', () => {
  const foot = { hidden: false, className: 'dev-machines-login-footer' };
  const doc = {
    querySelector(sel) {
      return sel === '.dev-machines-login-footer' ? foot : null;
    }
  };
  D.updateDevMachinesLoginFooter(doc, false);
  assert.equal(foot.hidden, false);
  D.updateDevMachinesLoginFooter(doc, true);
  assert.equal(foot.hidden, true);
});

test('頁尾管理員登入鈕 hidden 時覆寫 .dev-btn display', () => {
  const css = readFileSync(join(publicRoot, 'css/dev-machines.css'), 'utf8');
  assert.match(css, /\.dev-machines-login-footer\[hidden\]\s*\{[^}]*display:\s*none\s*!important/);
});

test('頁尾三顆按鈕同寬對齊（secondary 無額外 margin）', () => {
  const css = readFileSync(join(publicRoot, 'css/dev-machines.css'), 'utf8');
  const secondary = css.match(/\.dev-btn-secondary\s*\{([^}]+)\}/);
  assert.ok(secondary);
  assert.ok(!secondary[1].includes('margin-left'));
  assert.match(css, /\.dev-machines-footer \.dev-btn/);
});

test('訪客面板小標題為請先登入', () => {
  const js = readFileSync(join(publicRoot, 'js/dev-machines.js'), 'utf8');
  assert.match(js, /setText\(title,\s*'請先登入'\)/);
});