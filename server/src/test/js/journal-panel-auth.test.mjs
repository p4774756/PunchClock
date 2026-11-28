import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const { mountJournalPanel } = await loadPublicModule('journal-panel.js');

test('訪客掛載後再登入，重新開啟會出現刪除鈕', async () => {
  const { doc, root, win, whoEl, nickEl, listEl } = fakeJournalDom();
  win.PUNCHCLOCK_AUTHENTICATED = false;
  const api = mountJournalPanel({ doc, win, root });
  await api.onOpen();

  assert.equal(whoEl.hidden, true);
  assert.equal(nickEl.hidden, false);
  assert.equal(findClass(listEl, 'journal-post-delete'), null);
  assert.ok(findClass(listEl, 'journal-author'));
  assert.equal(findClass(listEl, 'journal-author').textContent, '站長');

  win.PUNCHCLOCK_AUTHENTICATED = true;
  await api.onOpen();

  assert.equal(whoEl.hidden, false);
  assert.equal(nickEl.hidden, true);
  const del = findClass(listEl, 'journal-post-delete');
  assert.ok(del);
  assert.equal(del.textContent, '刪除');
  assert.equal(del.getAttribute('aria-label'), '刪除這則留言');
});

test('登入事件會讓已掛載的留言板補上刪除鈕', async () => {
  const { doc, root, win, whoEl, nickEl, listEl } = fakeJournalDom();
  win.PUNCHCLOCK_AUTHENTICATED = false;
  const api = mountJournalPanel({ doc, win, root });
  await api.onOpen();
  assert.equal(findClass(listEl, 'journal-post-delete'), null);

  win.PUNCHCLOCK_AUTHENTICATED = true;
  win.dispatchEvent('punchclock:auth-changed');
  let del = null;
  for (let i = 0; i < 15; i++) {
    del = findClass(listEl, 'journal-post-delete');
    if (del) break;
    await tick();
  }

  assert.equal(whoEl.hidden, false);
  assert.equal(nickEl.hidden, true);
  assert.ok(findClass(listEl, 'journal-post-delete'));
});

function tick() {
  return new Promise((resolve) => setImmediate(resolve));
}

function findClass(node, className) {
  if (!node) return null;
  const names = String(node.className || '').split(/\s+/);
  if (names.includes(className)) return node;
  for (let i = 0; i < (node.children || []).length; i++) {
    const hit = findClass(node.children[i], className);
    if (hit) return hit;
  }
  return null;
}

function fakeJournalDom() {
  function create(tag) {
    const listeners = Object.create(null);
    const attrs = Object.create(null);
    const node = {
      tagName: String(tag).toLowerCase(),
      className: '',
      hidden: false,
      disabled: false,
      type: '',
      name: '',
      value: '',
      innerHTML: '',
      title: '',
      textContent: '',
      parentNode: null,
      children: [],
      attrs,
      ownerDocument: null,
      classList: {
        toggle() {}
      },
      setAttribute(key, value) {
        attrs[key] = value == null ? '' : String(value);
      },
      getAttribute(key) {
        return Object.prototype.hasOwnProperty.call(attrs, key) ? attrs[key] : null;
      },
      addEventListener(type, fn) {
        (listeners[type] || (listeners[type] = [])).push(fn);
      },
      removeEventListener() {},
      dispatchEvent(type, ev) {
        (listeners[type] || []).forEach((fn) => fn(ev || {}));
      },
      blur() {},
      click() {
        (listeners.click || []).forEach((fn) => fn({}));
      },
      appendChild(child) {
        if (child.parentNode && typeof child.parentNode.removeChild === 'function') {
          child.parentNode.removeChild(child);
        }
        child.parentNode = node;
        node.children.push(child);
        return child;
      },
      insertBefore(child, ref) {
        if (child.parentNode && typeof child.parentNode.removeChild === 'function') {
          child.parentNode.removeChild(child);
        }
        child.parentNode = node;
        const idx = node.children.indexOf(ref);
        if (idx >= 0) node.children.splice(idx, 0, child);
        else node.children.push(child);
        return child;
      },
      removeChild(child) {
        const idx = node.children.indexOf(child);
        if (idx >= 0) node.children.splice(idx, 1);
        child.parentNode = null;
        return child;
      },
      querySelector(sel) {
        return query(node, sel, false);
      },
      querySelectorAll(sel) {
        const found = [];
        query(node, sel, true, found);
        return found;
      }
    };
    Object.defineProperty(node, 'firstChild', {
      get() {
        return node.children[0] || null;
      }
    });
    return node;
  }

  function match(node, sel) {
    if (!sel) return false;
    if (sel.startsWith('[') && sel.endsWith(']')) {
      const body = sel.slice(1, -1);
      const eq = body.indexOf('=');
      if (eq < 0) {
        return Object.prototype.hasOwnProperty.call(node.attrs, body);
      }
      const key = body.slice(0, eq);
      let val = body.slice(eq + 1);
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      return node.getAttribute(key) === val;
    }
    if (sel.startsWith('.')) {
      return String(node.className || '').split(/\s+/).includes(sel.slice(1));
    }
    return node.tagName === sel.toLowerCase();
  }

  function query(node, sel, all, found) {
    for (let i = 0; i < (node.children || []).length; i++) {
      const child = node.children[i];
      if (match(child, sel)) {
        if (!all) return child;
        found.push(child);
      }
      const nested = query(child, sel, all, found);
      if (!all && nested) return nested;
    }
    return all ? found : null;
  }

  const doc = { createElement: create };
  const root = create('section');
  const compose = create('form');
  compose.setAttribute('data-journal-compose', '');
  const whoEl = create('p');
  whoEl.setAttribute('data-journal-who', '');
  whoEl.hidden = true;
  whoEl.textContent = '站長';
  const nickEl = create('input');
  nickEl.setAttribute('data-journal-nickname', '');
  nickEl.ownerDocument = doc;
  const bodyEl = create('textarea');
  bodyEl.setAttribute('data-journal-body', '');
  const countEl = create('span');
  countEl.setAttribute('data-journal-count', '');
  const statusEl = create('p');
  statusEl.setAttribute('data-journal-status', '');
  const listEl = create('div');
  listEl.setAttribute('data-journal-list', '');
  const moreEl = create('button');
  moreEl.setAttribute('data-journal-more', '');
  moreEl.hidden = true;
  compose.appendChild(whoEl);
  compose.appendChild(nickEl);
  compose.appendChild(bodyEl);
  compose.appendChild(countEl);
  root.appendChild(compose);
  root.appendChild(statusEl);
  root.appendChild(listEl);
  root.appendChild(moreEl);

  const winListeners = Object.create(null);
  const store = Object.create(null);
  const win = {
    PUNCHCLOCK_AUTHENTICATED: false,
    localStorage: {
      getItem(k) { return Object.prototype.hasOwnProperty.call(store, k) ? store[k] : null; },
      setItem(k, v) { store[k] = String(v); }
    },
    addEventListener(type, fn) {
      (winListeners[type] || (winListeners[type] = [])).push(fn);
    },
    dispatchEvent(type) {
      const ev = typeof type === 'string' ? { type } : type;
      const name = ev.type || type;
      (winListeners[name] || []).forEach((fn) => fn(ev));
      return true;
    },
    fetch() {
      return Promise.resolve({
        ok: true,
        json: async () => ({
          items: [{
            id: 'abcd1234ef',
            body: '站長測試',
            authorName: '站長',
            owner: true,
            createdAt: 1,
            tags: []
          }],
          nextBefore: null
        })
      });
    }
  };

  return { doc, root, win, whoEl, nickEl, listEl };
}
