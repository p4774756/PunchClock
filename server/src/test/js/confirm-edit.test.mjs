import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  formatCounter,
  isDirty,
  mountConfirmEdit,
  readStored,
  writeStored,
  JOURNAL_NICK_KEY
} = await loadPublicModule('confirm-edit.js');

test('字數以 code point 計算，沒有上限時不加字數', () => {
  assert.equal(formatCounter('', 20), '0/20');
  assert.equal(formatCounter('忙碌中請留言', 20), '6/20');
  assert.equal(formatCounter('今天先這樣了喔', 20), '7/20');
  assert.equal(formatCounter('🙂🙂', 12), '2/12');
  assert.equal(formatCounter('hello', 0), '');
  assert.equal(isDirty('a', 'a'), false);
  assert.equal(isDirty('a', 'b'), true);
});

test('確認會記下新值，取消會還原；Enter／Esc 同樣有效', () => {
  const { document, parent, input } = fakeDom('原本');
  const store = memoryStore();
  const api = mountConfirmEdit(input, {
    max: 12,
    initial: '原本',
    onConfirm: (value) => writeStored(store, JOURNAL_NICK_KEY, value)
  });
  const root = api.root;
  assert.equal(qs(root, 'confirm-edit-count').textContent, '2/12');
  assert.equal(qs(root, 'confirm-edit-ok').disabled, true);
  input.value = '新暱稱';
  input.dispatchEvent('input');
  assert.equal(qs(root, 'confirm-edit-count').textContent, '3/12');
  assert.equal(qs(root, 'confirm-edit-ok').disabled, false);
  input.dispatchEvent('keydown', { key: 'Escape', preventDefault() {}, stopPropagation() {} });
  assert.equal(input.value, '原本');
  input.value = '新暱稱';
  input.dispatchEvent('input');
  input.dispatchEvent('keydown', { key: 'Enter', preventDefault() {}, stopPropagation() {} });
  assert.equal(api.getCommitted(), '新暱稱');
  assert.equal(readStored(store, JOURNAL_NICK_KEY), '新暱稱');
  input.value = '再改';
  input.dispatchEvent('input');
  qs(root, 'confirm-edit-cancel').click();
  assert.equal(input.value, '新暱稱');
  assert.equal(parent.children[0], root);
});

function memoryStore() {
  const data = Object.create(null);
  return {
    getItem(k) { return Object.prototype.hasOwnProperty.call(data, k) ? data[k] : null; },
    setItem(k, v) { data[k] = String(v); }
  };
}

function qs(node, className) {
  if (!node) return null;
  if (node.className === className) return node;
  for (let i = 0; i < (node.children || []).length; i++) {
    const hit = qs(node.children[i], className);
    if (hit) return hit;
  }
  return null;
}

function fakeDom(value) {
  function create(tag) {
    const listeners = Object.create(null);
    const node = {
      tagName: String(tag).toLowerCase(),
      className: '',
      hidden: false,
      disabled: false,
      innerHTML: '',
      title: '',
      textContent: '',
      parentNode: null,
      children: [],
      classList: { toggle() {} },
      setAttribute() {},
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
      }
    };
    return node;
  }
  const document = { createElement: create };
  const parent = create('form');
  const input = create('input');
  input.value = value;
  input.ownerDocument = document;
  parent.appendChild(input);
  return { document, parent, input };
}
