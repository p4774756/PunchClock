import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const D = await loadPublicModule('dev-machines.js');
const F = await loadPublicModule('dev-machines-format.js');

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('主島載入 dev-guest-ui，toast 樣式類名存在', () => {
  const indexHtml = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  assert.match(indexHtml, /href="\/css\/dev-guest-ui\.css"/);
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