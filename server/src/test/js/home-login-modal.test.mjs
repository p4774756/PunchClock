import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

test('首頁登入浮動視窗標記與 fetch JSON', () => {
  const html = readFileSync(join(publicRoot, 'index.html'), 'utf8');
  const js = readFileSync(join(publicRoot, 'js/home-login-modal.js'), 'utf8');
  assert.match(html, /id="loginBoard"/);
  assert.match(html, /roadmap-close-x/);
  assert.match(html, /data-login-form/);
  assert.match(html, /管理員登入/);
  assert.match(js, /Accept: 'application\/json'/);
  assert.match(js, /credentials: 'same-origin'/);
  assert.match(js, /POST/);
});