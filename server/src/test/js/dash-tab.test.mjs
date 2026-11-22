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

test('簡報牆的 session 或 hash 改開任務', () => {
  assert.equal(resolveInitialDashTab('photos', ''), 'devices');
  assert.equal(resolveInitialDashTab('panel-photos', ''), 'devices');
  assert.equal(resolveInitialDashTab('', '#photos'), 'devices');
  assert.equal(resolveInitialDashTab('', '#panel-photos'), 'devices');
  assert.equal(resolveInitialDashTab('system', '#photos'), 'devices');
  assert.equal(resolveInitialDashTab('system', '#panel-photos'), 'devices');
  assert.equal(isRemovedDashTab('#photos'), true);
  assert.equal(isRemovedDashTab('#panel-photos'), true);
  assert.equal(isRemovedDashTab('photos'), true);
});

test('仍有效的伺服器分頁會從 session 或 hash 還原', () => {
  assert.equal(resolveInitialDashTab('system', ''), 'system');
  assert.equal(resolveInitialDashTab('system', '#'), 'system');
  assert.equal(resolveInitialDashTab('', '#system'), 'system');
  assert.equal(resolveInitialDashTab('', '#panel-system'), 'system');
  assert.equal(resolveInitialDashTab('devices', '#system'), 'system');
  assert.equal(resolveInitialDashTab('photos', '#system'), 'system');
  assert.equal(resolveInitialDashTab('photos', '#panel-system'), 'system');
});

test('未知分頁沒有可用值時預設開任務，合法 session 仍優先於未知 hash', () => {
  assert.equal(resolveInitialDashTab('nope', ''), 'devices');
  assert.equal(resolveInitialDashTab('', '#nope'), 'devices');
  assert.equal(resolveInitialDashTab('nope', '#ghost'), 'devices');
  assert.equal(resolveInitialDashTab('system', '#nope'), 'system');
  assert.equal(isRemovedDashTab('#system'), false);
  assert.equal(isRemovedDashTab(''), false);
});
