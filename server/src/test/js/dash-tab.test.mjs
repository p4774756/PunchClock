import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

await loadPublicModule('dash-tab.js');
const { resolveInitialDashTab, isRemovedDashTab } = globalThis.PunchclockDashTab;

test('沒有已存分頁時預設開任務', () => {
  assert.equal(resolveInitialDashTab(null, ''), 'devices');
  assert.equal(resolveInitialDashTab(undefined, undefined), 'devices');
  assert.equal(resolveInitialDashTab('', ''), 'devices');
  assert.equal(resolveInitialDashTab('   ', '#'), 'devices');
});

test('簡報牆或伺服器的 session 或 hash 改開任務', () => {
  assert.equal(resolveInitialDashTab('photos', ''), 'devices');
  assert.equal(resolveInitialDashTab('panel-photos', ''), 'devices');
  assert.equal(resolveInitialDashTab('', '#photos'), 'devices');
  assert.equal(resolveInitialDashTab('', '#panel-photos'), 'devices');
  assert.equal(resolveInitialDashTab('system', ''), 'devices');
  assert.equal(resolveInitialDashTab('', '#system'), 'devices');
  assert.equal(resolveInitialDashTab('', '#panel-system'), 'devices');
  assert.equal(resolveInitialDashTab('devices', '#system'), 'devices');
  assert.equal(isRemovedDashTab('#photos'), true);
  assert.equal(isRemovedDashTab('#system'), true);
  assert.equal(isRemovedDashTab('system'), true);
});

test('hash 仍會還原任務分頁', () => {
  assert.equal(resolveInitialDashTab('', '#devices'), 'devices');
  assert.equal(resolveInitialDashTab('', '#panel-devices'), 'devices');
  assert.equal(resolveInitialDashTab('devices', '#'), 'devices');
});

test('未知分頁沒有可用值時預設開任務', () => {
  assert.equal(resolveInitialDashTab('nope', ''), 'devices');
  assert.equal(resolveInitialDashTab('', '#nope'), 'devices');
  assert.equal(resolveInitialDashTab('nope', '#ghost'), 'devices');
  assert.equal(isRemovedDashTab(''), false);
});