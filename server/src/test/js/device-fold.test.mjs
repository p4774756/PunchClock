import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('device-fold.js');

test('Lucide 風格箭頭與 aria 收合文案', () => {
  const collapsed = M.deviceChevronMarkup(false);
  const expanded = M.deviceChevronMarkup(true);
  assert.match(collapsed, /device-chevron/);
  assert.match(collapsed, /m9 18 6-6-6-6/);
  assert.match(expanded, /m6 9 6 6 6-6/);
  assert.equal(M.deviceFoldLabel(false), '展開');
  assert.equal(M.deviceFoldLabel(true), '收合');
  assert.equal(M.deviceFoldAriaLabel(false, 'AA123'), '展開 AA123');
  assert.equal(M.deviceFoldAriaLabel(true, 'AA123'), '收合 AA123');
});

test('鍵盤只接受 Enter 與 Space', () => {
  assert.equal(M.shouldToggleDeviceFold('Enter'), true);
  assert.equal(M.shouldToggleDeviceFold(' '), true);
  assert.equal(M.shouldToggleDeviceFold('Escape'), false);
  assert.equal(M.shouldToggleDeviceFold('a'), false);
});