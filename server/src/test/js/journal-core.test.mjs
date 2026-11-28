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

test('首屏不載入留言板樣式與模組', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const officeJs = readFileSync(join(publicRoot, 'js/office-page.js'), 'utf8');
  assert.doesNotMatch(html, /journal\.css/);
  assert.doesNotMatch(html, /journal-panel\.js/);
  assert.match(html, /id="journalBoard"/);
  assert.match(officeJs, /home-journal-panel/);
  assert.match(officeJs, /applyJournalOpenParam/);
  assert.doesNotMatch(officeJs, /from '\.\/journal-panel\.js'/);
});
test('可見文案為留言板且短文上限 280', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const page = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  const room = readFileSync(join(publicRoot, 'js/private-team-scene-room.js'), 'utf8');
  const core = readFileSync(join(publicRoot, 'js/journal-core.js'), 'utf8');
  const panel = readFileSync(join(publicRoot, 'js/journal-panel.js'), 'utf8');
  assert.match(html, /aria-label="留言板"/);
  assert.match(html, /<h2>留言板<\/h2>/);
  assert.match(html, /placeholder="寫點什麼…"/);
  assert.match(html, /maxlength="280"/);
  assert.doesNotMatch(html, /心得動態牆|心得牆|<h2>心得<\/h2>|寫下心得/);
  assert.match(page, />留言板<\/a>/);
  assert.doesNotMatch(page, /心得牆/);
  assert.match(room, /fillText\('留言板'/);
  assert.doesNotMatch(room, /fillText\('心得'|fillText\('動態牆'/);
  assert.match(core, /BODY_MAX = 280/);
  assert.match(panel, /尚無留言/);
});
