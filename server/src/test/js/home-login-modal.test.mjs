import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { bundleSource } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

function ruleBlocks(css) {
  const clean = css.replace(/\/\*[\s\S]*?\*\//g, '');
  const blocks = [];
  const re = /([^{}]+)\{([^{}]*)\}/g;
  let match;
  while ((match = re.exec(clean))) {
    blocks.push({ sel: match[1].trim(), body: match[2] });
  }
  return blocks;
}

function textNode(data) {
  return {
    nodeType: 3,
    data: String(data),
    parent: null,
    children: [],
    get textContent() { return this.data; },
    set textContent(value) { this.data = String(value); }
  };
}

function createDocument() {
  function queryAll(node, sel) {
    const out = [];
    const kids = node.children || [];
    for (let i = 0; i < kids.length; i++) {
      const child = kids[i];
      if (!child || child.nodeType !== 1) continue;
      if (child.matches(sel)) out.push(child);
      out.push(...queryAll(child, sel));
    }
    return out;
  }

  function matches(el, sel) {
    let rest = String(sel || '').trim();
    if (!rest || /\s/.test(rest)) return false;
    while (rest) {
      if (rest[0] === '.') {
        const part = rest.match(/^\.([A-Za-z0-9_-]+)/);
        if (!part || !el.classList.contains(part[1])) return false;
        rest = rest.slice(part[0].length);
      } else if (rest[0] === '#') {
        const part = rest.match(/^#([A-Za-z0-9_-]+)/);
        if (!part || el.id !== part[1]) return false;
        rest = rest.slice(part[0].length);
      } else if (rest[0] === '[') {
        const part = rest.match(/^\[([^\]=\s]+)(?:="([^"]*)")?\]/);
        if (!part) return false;
        if (part[2] == null) {
          if (!el.hasAttribute(part[1])) return false;
        } else if (el.getAttribute(part[1]) !== part[2]) return false;
        rest = rest.slice(part[0].length);
      } else {
        const part = rest.match(/^([A-Za-z][\w-]*)/);
        if (!part || el.tag !== part[1].toLowerCase()) return false;
        rest = rest.slice(part[0].length);
      }
    }
    return true;
  }

  function parseAttrs(node, raw) {
    const re = /([^\s=/]+)(?:\s*=\s*(?:"([^"]*)"|'([^']*)'|(\S+)))?/g;
    let part;
    while ((part = re.exec(raw))) {
      const name = part[1];
      if (name === '/') continue;
      const value = part[2] ?? part[3] ?? part[4] ?? '';
      node.setAttribute(name, value);
    }
  }

  function parseInto(parent, html) {
    const re = /<!--[\s\S]*?-->|<(\/?)([A-Za-z][\w:-]*)([^>]*)>|([^<]+)/g;
    const voidTags = new Set(['img', 'br', 'hr', 'input', 'meta', 'link', 'area', 'base', 'col', 'embed', 'source', 'track', 'wbr']);
    const stack = [parent];
    let part;
    while ((part = re.exec(html))) {
      if (part[0].startsWith('<!--')) continue;
      if (part[4] != null) {
        if (part[4]) stack[stack.length - 1].appendChild(textNode(part[4]));
        continue;
      }
      const closing = part[1] === '/';
      const tag = part[2].toLowerCase();
      if (closing) {
        for (let i = stack.length - 1; i > 0; i--) {
          if (stack[i].tag === tag) {
            stack.length = i;
            break;
          }
        }
        continue;
      }
      const node = createElement(tag);
      parseAttrs(node, part[3] || '');
      stack[stack.length - 1].appendChild(node);
      const selfClose = /\/\s*$/.test(part[3] || '') || voidTags.has(tag);
      if (!selfClose) stack.push(node);
    }
  }

  function createElement(tag) {
    const node = {
      nodeType: 1,
      tag: String(tag || 'div').toLowerCase(),
      id: '',
      className: '',
      hidden: false,
      disabled: false,
      value: '',
      type: '',
      style: {},
      children: [],
      parent: null,
      attrs: new Map(),
      _on: {}
    };
    node.classList = {
      contains(name) {
        return node.className.split(/\s+/).filter(Boolean).includes(name);
      },
      add(...names) {
        const set = new Set(node.className.split(/\s+/).filter(Boolean));
        names.forEach((name) => set.add(name));
        node.className = [...set].join(' ');
      },
      remove(...names) {
        const drop = new Set(names);
        node.className = node.className.split(/\s+/).filter((name) => name && !drop.has(name)).join(' ');
      },
      toggle(name, force) {
        const has = node.classList.contains(name);
        const next = force == null ? !has : !!force;
        if (next) node.classList.add(name);
        else node.classList.remove(name);
        return next;
      }
    };
    node.matches = (sel) => matches(node, sel);
    node.setAttribute = (name, value) => {
      node.attrs.set(name, String(value));
      if (name === 'class') node.className = String(value);
      if (name === 'id') node.id = String(value);
      if (name === 'hidden') node.hidden = true;
      if (name === 'type') node.type = String(value);
    };
    node.getAttribute = (name) => (node.attrs.has(name) ? node.attrs.get(name) : null);
    node.hasAttribute = (name) => node.attrs.has(name);
    node.removeAttribute = (name) => {
      node.attrs.delete(name);
      if (name === 'class') node.className = '';
      if (name === 'id') node.id = '';
    };
    node.appendChild = (child) => {
      child.parent = node;
      node.children.push(child);
      return child;
    };
    node.insertBefore = (child, ref) => {
      child.parent = node;
      const index = ref ? node.children.indexOf(ref) : -1;
      if (index < 0) node.children.push(child);
      else node.children.splice(index, 0, child);
      return child;
    };
    node.contains = (other) => {
      let current = other;
      while (current) {
        if (current === node) return true;
        current = current.parent;
      }
      return false;
    };
    node.closest = (sel) => {
      let current = node;
      while (current) {
        if (current.nodeType === 1 && matches(current, sel)) return current;
        current = current.parent;
      }
      return null;
    };
    node.addEventListener = (type, fn) => {
      (node._on[type] || (node._on[type] = [])).push(fn);
    };
    node.removeEventListener = (type, fn) => {
      const list = node._on[type] || [];
      const index = list.indexOf(fn);
      if (index >= 0) list.splice(index, 1);
    };
    node.dispatchEvent = (ev) => {
      const event = {
        type: ev.type,
        key: ev.key,
        target: node,
        defaultPrevented: false,
        preventDefault() { event.defaultPrevented = true; },
        stopPropagation() { event._stop = true; }
      };
      let current = node;
      while (current) {
        const list = (current._on && current._on[event.type]) || [];
        for (let i = 0; i < list.length; i++) list[i](event);
        if (event._stop) break;
        current = current.parent;
      }
    };
    node.click = () => node.dispatchEvent({ type: 'click' });
    node.focus = () => { doc.activeElement = node; };
    node.querySelector = (sel) => queryAll(node, sel)[0] || null;
    node.querySelectorAll = (sel) => queryAll(node, sel);
    Object.defineProperty(node, 'firstChild', {
      get() { return node.children[0] || null; }
    });
    Object.defineProperty(node, 'textContent', {
      get() {
        return node.children.map((child) => child.textContent || '').join('');
      },
      set(value) {
        node.children = [];
        if (value) node.appendChild(textNode(value));
      }
    });
    Object.defineProperty(node, 'innerHTML', {
      get() { return ''; },
      set(html) {
        node.children = [];
        parseInto(node, String(html || ''));
      }
    });
    if (node.tag === 'form') {
      node.reset = () => {
        queryAll(node, 'input').forEach((input) => { input.value = ''; });
      };
    }
    return node;
  }

  function findId(node, id) {
    if (node && node.nodeType === 1 && node.id === id) return node;
    const kids = (node && node.children) || [];
    for (let i = 0; i < kids.length; i++) {
      const found = findId(kids[i], id);
      if (found) return found;
    }
    return null;
  }

  const doc = {
    activeElement: null,
    _on: {},
    createElement,
    createTextNode: textNode
  };
  doc.body = createElement('body');
  doc.body.parent = doc;
  doc.addEventListener = (type, fn) => {
    (doc._on[type] || (doc._on[type] = [])).push(fn);
  };
  doc.removeEventListener = (type, fn) => {
    const list = doc._on[type] || [];
    const index = list.indexOf(fn);
    if (index >= 0) list.splice(index, 1);
  };
  doc.getElementById = (id) => findId(doc.body, id);
  doc.querySelector = (sel) => queryAll(doc.body, sel)[0] || null;
  doc.querySelectorAll = (sel) => queryAll(doc.body, sel);
  return doc;
}

const LOGIN_BOARD_HTML =
  '<div class="lab-office-stage" data-private-team-stage></div>' +
  '<section id="loginBoard" class="roadmap-board pt-login-board" hidden aria-hidden="true">' +
  '<div class="roadmap-panel maple-window login-sheet pt-login-panel">' +
  '<button type="button" data-login-close>x</button>' +
  '<div data-login-alert></div>' +
  '<form data-login-form>' +
  '<input type="password" data-login-password>' +
  '<button type="button" data-login-toggle-pwd>顯示</button>' +
  '<button type="submit" data-login-submit>登入</button>' +
  '</form></div></section>';

function flush() {
  return new Promise((resolve) => setTimeout(resolve, 0));
}

test('首頁登入浮動視窗標記與 fetch JSON', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const js = readFileSync(join(publicRoot, 'js/home-login-modal.js'), 'utf8');
  assert.match(html, /id="loginBoard"/);
  assert.match(html, /roadmap-close-x/);
  assert.match(html, /data-login-form/);
  assert.match(html, /id="home-login-title">登入</);
  assert.match(html, /login-lead">請先登入</);
  assert.doesNotMatch(html, /管理員登入/);
  assert.doesNotMatch(html, /管理員請先登入/);
  assert.match(js, /Accept: 'application\/json'/);
  assert.match(js, /credentials: 'same-origin'/);
  assert.match(js, /POST/);
  assert.doesNotMatch(js, /innerHTML/);
  assert.doesNotMatch(js, /剩餘嘗試次數：4/);
});

test('登入彈窗錯誤用 textContent，解析失敗不寫死剩餘 4 次', { concurrency: false }, async () => {
  const { wireHomeLoginModal } = await import(
    'data:text/javascript;base64,' + Buffer.from(readFileSync(join(publicRoot, 'js/home-login-modal.js'), 'utf8')).toString('base64')
  );
  const doc = createDocument();
  doc.body.innerHTML = LOGIN_BOARD_HTML;
  const intervals = [];
  let mode = 'xss';
  const modal = wireHomeLoginModal({
    doc,
    win: {
      setInterval(fn) {
        intervals.push(fn);
        return intervals.length;
      },
      clearInterval(id) {
        if (id) intervals[id - 1] = null;
      }
    },
    fetch() {
      if (mode === 'badjson') {
        return Promise.resolve({
          ok: false,
          status: 500,
          json: () => Promise.reject(new Error('not json'))
        });
      }
      if (mode === 'lock') {
        return Promise.resolve({
          ok: false,
          status: 403,
          json: () => Promise.resolve({
            success: false,
            locked: true,
            remainingSec: 90,
            message: '<b id="xss-proof">XSS</b>'
          })
        });
      }
      return Promise.resolve({
        ok: false,
        status: 401,
        json: () => Promise.resolve({
          success: false,
          locked: false,
          message: '<b id="xss-proof">XSS</b>'
        })
      });
    }
  });
  const board = doc.getElementById('loginBoard');
  modal.open();
  board.querySelector('[data-login-password]').value = 'x';
  board.querySelector('[data-login-form]').dispatchEvent({ type: 'submit' });
  await flush();
  const alert = board.querySelector('.error-alert');
  assert.ok(alert);
  assert.equal(alert.textContent, '<b id="xss-proof">XSS</b>');
  assert.equal(board.querySelector('#xss-proof'), null);
  assert.equal(alert.querySelector('b'), null);

  mode = 'badjson';
  modal.close();
  modal.open();
  board.querySelector('[data-login-form]').dispatchEvent({ type: 'submit' });
  await flush();
  const unknown = board.querySelector('[data-login-alert]').textContent;
  assert.match(unknown, /登入失敗/);
  assert.doesNotMatch(unknown, /剩餘嘗試次數：4/);
  assert.doesNotMatch(unknown, /4 次/);

  mode = 'lock';
  modal.close();
  modal.open();
  board.querySelector('[data-login-form]').dispatchEvent({ type: 'submit' });
  await flush();
  const countdown = board.querySelector('[data-login-countdown]');
  assert.equal(countdown.textContent, '1 分 30 秒');
  assert.equal(board.querySelector('#xss-proof'), null);
  assert.match(board.querySelector('[data-login-submit]').textContent, /冷卻鎖定中/);
  intervals[intervals.length - 1]();
  assert.equal(countdown.textContent, '1 分 29 秒');
  modal.destroy();
});

test('左頭像提示的登入打開彈窗，重畫膠囊不加第二次 listener', { concurrency: false }, async () => {
  const raw = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const withoutScene = raw.replace(
    /import\s+\{\s*mountPrivateTeamScene\s*\}\s+from\s+['"]\.\/private-team-scene\.js['"];\s*/,
    ''
  );
  assert.equal(withoutScene.includes('private-team-scene'), false);
  const source = ('function mountPrivateTeamScene(canvas,opts){globalThis.__ptOnActivate=opts&&opts.onActivate;return {setSceneInputEnabled(){},setBubbleText(){},setSecretarySprite(){},noteTigerStatus(){},destroy(){}};}\n' +
    withoutScene).replace(/from\s+(['"])\.\/([^'"]+)\1/g, (all, quote, dep) => {
    const bundled = bundleSource(dep);
    const url = 'data:text/javascript;base64,' + Buffer.from(bundled).toString('base64');
    return 'from ' + quote + url + quote;
  });
  const { mountPrivateTeamPage } = await import(
    'data:text/javascript;base64,' + Buffer.from(source).toString('base64')
  );
  const doc = createDocument();
  doc.body.innerHTML = LOGIN_BOARD_HTML;
  const loc = { href: '', pathname: '/', search: '', hash: '' };
  loc.assign = (url) => { loc.href = String(url); };
  const win = {
    PUNCHCLOCK_AUTHENTICATED: false,
    setInterval(fn, ms) { return setInterval(fn, ms); },
    clearInterval(id) { clearInterval(id); },
    location: loc
  };
  const page = mountPrivateTeamPage({
    doc,
    win,
    fetch(url) {
      if (String(url).includes('/login')) {
        return Promise.resolve({
          ok: true,
          status: 200,
          json: () => Promise.resolve({ success: true })
        });
      }
      return Promise.resolve({ ok: false, status: 404, json: () => Promise.resolve(null) });
    }
  });
  await flush();
  const hint = doc.querySelector('.pt-hud-task-hint-login');
  const task = doc.querySelector('[data-pt-task-btn]');
  const guest = doc.querySelector('.pt-hud-id-plate');
  const board = doc.getElementById('loginBoard');
  const stage = doc.querySelector('[data-private-team-stage]');
  const activate = globalThis.__ptOnActivate;
  assert.equal(typeof activate, 'function');
  activate('atlas');
  assert.equal(board.hidden, false);
  assert.equal(loc.href, '');
  board.querySelector('[data-login-close]').click();
  assert.ok(hint);
  assert.equal(hint._on.click.length, 1);
  task.click();
  assert.equal(doc.querySelector('[data-pt-task-hint]').hidden, false);
  const hintClose = doc.querySelector('[data-pt-task-hint-close]');
  assert.ok(hintClose);
  hintClose.click();
  assert.equal(doc.querySelector('[data-pt-task-hint]').hidden, true);
  task.click();
  assert.equal(doc.querySelector('[data-pt-task-hint]').hidden, false);
  hint.click();
  assert.equal(board.hidden, false);
  assert.equal(doc.querySelector('[data-pt-task-hint]').hidden, true);
  assert.equal(stage.classList.contains('pt-login-open'), true);
  assert.equal(loc.href, '');
  doc.querySelector('[data-login-close]').click();
  assert.equal(board.hidden, true);
  guest.click();
  assert.equal(board.hidden, false);
  board.querySelector('[data-login-password]').value = 'secret';
  board.querySelector('[data-login-form]').dispatchEvent({ type: 'submit' });
  await flush();
  assert.equal(board.hidden, true);
  assert.equal(doc.querySelector('.pt-a-id-role').textContent, '已登入');
  assert.equal(hint._on.click.length, 1);
  hint.click();
  assert.equal(board.hidden, false);
  assert.equal(hint._on.click.length, 1);
  delete globalThis.__ptOnActivate;
  delete globalThis.__ptAtlasNp;
  page.destroy();
});

test('登入成功後依 pending 進研習室', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(js, /pendingOpenAtlasAfterLogin/);
  assert.match(js, /if \(pendingOpenAtlasAfterLogin\)[\s\S]*openAtlasRoom\(\)/);
});

test('登入彈窗不藏場景，窄螢幕不跟施工進度貼頂', () => {
  const pageCss = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  const loginCss = readFileSync(join(publicRoot, 'css/login.css'), 'utf8');
  const pageBlocks = ruleBlocks(pageCss);
  const loginOpen = pageBlocks.filter((block) => block.sel.includes('pt-login-open'));
  assert.ok(loginOpen.length >= 1);
  assert.ok(loginOpen.some((block) => block.sel.includes('.private-team-canvas')));
  for (const block of loginOpen) {
    if (block.sel.includes('.pt-hud-download') || block.sel.includes('.pt-hud-deploy-ver')) {
      assert.match(block.body, /visibility\s*:\s*hidden/);
      assert.match(block.body, /pointer-events\s*:\s*none/);
      continue;
    }
    assert.doesNotMatch(block.body, /visibility\s*:\s*hidden/);
    assert.match(block.body, /pointer-events\s*:\s*none/);
  }
  const roadmapOpen = pageBlocks.filter((block) => block.sel.includes('pt-roadmap-open'));
  assert.ok(roadmapOpen.some((block) => /visibility\s*:\s*hidden/.test(block.body)));
  const loginBlocks = ruleBlocks(loginCss);
  const board = loginBlocks.find((block) => block.sel.includes('roadmap-board') && block.sel.includes('pt-login-board'));
  const panel = loginBlocks.find((block) => block.sel.includes('pt-login-board') && block.sel.includes('maple-window'));
  assert.ok(board);
  assert.match(board.body, /align-items\s*:\s*center/);
  assert.match(board.body, /padding\s*:\s*0\.75rem/);
  assert.ok(panel);
  assert.match(panel.sel, /roadmap-panel/);
  assert.match(panel.body, /align-self\s*:\s*center/);
  assert.match(panel.body, /height\s*:\s*auto/);
  assert.match(panel.body, /border-radius\s*:\s*6px/);
});