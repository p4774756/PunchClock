import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const D = await loadPublicModule('donate-link.js');

function miniDoc() {
  const nodes = [];
  function el(tag) {
    const n = {
      tagName: tag.toUpperCase(),
      className: '',
      childNodes: [],
      attributes: {},
      textContent: '',
      parentNode: null,
      body: null,
      setAttribute(k, v) {
        this.attributes[k] = String(v);
      },
      getAttribute(k) {
        return this.attributes[k];
      },
      removeAttribute(k) {
        delete this.attributes[k];
      },
      addEventListener() {},
      appendChild(c) {
        this.childNodes.push(c);
        c.parentNode = this;
        nodes.push(c);
        return c;
      },
      removeChild(c) {
        const i = this.childNodes.indexOf(c);
        if (i >= 0) this.childNodes.splice(i, 1);
        return c;
      },
      querySelector() {
        return null;
      },
      querySelectorAll() {
        return [];
      }
    };
    return n;
  }
  const body = el('body');
  body.body = body;
  function liveNodes(root) {
    const out = [];
    const walk = (node) => {
      out.push(node);
      node.childNodes.forEach(walk);
    };
    walk(root);
    return out;
  }
  const doc = {
    body,
    createElement: el,
    querySelector: (sel) => liveNodes(body).find((n) => n.attributes && n.attributes['data-donate-toast']) || null,
    querySelectorAll: (sel) => {
      if (sel !== '[data-donate-toast]') return [];
      return liveNodes(body).filter((n) => n.attributes && n.attributes['data-donate-toast']);
    },
    nodes
  };
  return doc;
}

test('isHttpsDonateUrl rejects non-https and unsafe chars', () => {
  assert.equal(D.isHttpsDonateUrl('http://ko-fi.com/x'), false);
  assert.equal(D.isHttpsDonateUrl('https://ko-fi.com/ok'), true);
  assert.equal(D.isHttpsDonateUrl('https://x.com/a b'), false);
  assert.equal(D.isHttpsDonateUrl('https://x.com/"x"'), false);
});

test('isDonateConfigured accepts real host only', () => {
  assert.equal(D.isDonateConfigured('https://example.com/donate', true), false);
  assert.equal(D.isDonateConfigured('https://ko-fi.com/test', true), true);
});

test('configured anchor gets target and rel', () => {
  const doc = miniDoc();
  const a = doc.createElement('a');
  D.applyDonateAnchor(a, 'https://ko-fi.com/test', true);
  assert.equal(a.getAttribute('href'), 'https://ko-fi.com/test');
  assert.equal(a.getAttribute('target'), '_blank');
  assert.ok((a.getAttribute('rel') || '').includes('noopener'));
});

test('placeholder click shows single toast', () => {
  const doc = miniDoc();
  const ev = { prevented: false, preventDefault() { this.prevented = true; }, stopPropagation() {} };
  const blocked = D.handleDonateClick(ev, doc, 'https://example.com/donate', false);
  assert.equal(blocked, true);
  assert.equal(ev.prevented, true);
  assert.equal(doc.querySelectorAll('[data-donate-toast]').length, 1);
  const toast = doc.querySelector('[data-donate-toast]');
  assert.ok(toast);
  assert.ok(
    toast.childNodes.some((n) => (n.textContent || '').includes(D.DONATE_PREPARE_MSG))
  );
  D.showDonateToast(doc);
  assert.equal(doc.querySelectorAll('[data-donate-toast]').length, 1);
  assert.equal(D.DONATE_TOAST_MS, 3000);
});

test('donate toast uses placement class and top slot for dev', () => {
  const doc = miniDoc();
  const islandToast = D.showDonateToast(doc, 'island');
  assert.ok(String(islandToast.className).includes('donate-toast--island'));
  D.dismissDonateToast(doc);
  const devToast = D.showDonateToast(doc, 'dev');
  assert.ok(String(devToast.className).includes('donate-toast--dev'));
});