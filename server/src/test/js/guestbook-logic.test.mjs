import test from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const G = await loadPublicModule('guestbook-logic.js');

test('暱稱與留言長度', () => {
  assert.equal(G.normalizeNickname('  小 明  '), '小 明');
  assert.equal(G.normalizeNickname(''), null);
  assert.equal(G.normalizeNickname('x'.repeat(21)), null);
  const xss = '<img onerror=1>';
  assert.equal(G.normalizeMessage(xss), xss);
});

test('日期格式', () => {
  const s = G.formatGuestDate(Date.UTC(2026, 10, 21, 4, 0));
  assert.match(s, /^11\/21$/);
});