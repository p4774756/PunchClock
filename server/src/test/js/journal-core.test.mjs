import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  ANONYMOUS,
  containsForbiddenLink,
  escapeHtmlText,
  normalizeNickname
} from '../../main/resources/public/js/journal-core.js';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('暱稱空白時用預設稱謂', () => {
  assert.equal(normalizeNickname(''), ANONYMOUS);
  assert.equal(normalizeNickname('  '), ANONYMOUS);
});

test('拒絕留言中的連結', () => {
  assert.equal(containsForbiddenLink('看 www.evil.test'), true);
  assert.equal(containsForbiddenLink('純文字'), false);
});

test('跳脫 HTML 字元', () => {
  assert.equal(escapeHtmlText('<b>"\''), '&lt;b&gt;&quot;\'');
});

test('首屏不載入心得牆樣式與模組', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const officeJs = readFileSync(join(publicRoot, 'js/office-page.js'), 'utf8');
  assert.doesNotMatch(html, /journal\.css/);
  assert.doesNotMatch(html, /journal-panel\.js/);
  assert.match(html, /id="journalBoard"/);
  assert.match(officeJs, /home-journal-panel/);
  assert.match(officeJs, /applyJournalOpenParam/);
  assert.doesNotMatch(officeJs, /from '\.\/journal-panel\.js'/);
});