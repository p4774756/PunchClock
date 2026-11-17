// 賭場大廳純邏輯：node --test server/src/test/js/island-casino.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const modulePath = join(here, '..', '..', 'main', 'resources', 'public', 'js', 'island-casino.js');
const source = readFileSync(modulePath, 'utf8');
const C = await loadPublicModule('island-casino.js');

test('lobbyState：開關大廳', () => {
  const init = C.lobbyState(null, '');
  assert.deepEqual(init, { open: false });
  assert.equal(C.isLobbyOpen(init), false);
  const open = C.lobbyState(init, 'open');
  assert.equal(C.isLobbyOpen(open), true);
  assert.deepEqual(C.lobbyState(open, 'close'), init);
});

test('chipWalletView：啟用練習籌碼時顯示餘額且註明不是真錢', () => {
  const placeholder = C.chipWalletView(null);
  assert.equal(placeholder.enabled, false);
  assert.equal(placeholder.balance, null);
  assert.match(placeholder.text, /尚未啟用/);
  assert.ok(placeholder.note);

  const live = C.chipWalletView({ enabled: true, balance: 500, currency: '練習籌碼' });
  assert.equal(live.enabled, true);
  assert.equal(live.balance, 500);
  assert.equal(live.text, '500');
  assert.match(live.note, /不是真錢/);
});

test('21 點可玩；老虎機仍即將推出', () => {
  const bj = C.CASINO_GAMES.find((g) => g.id === 'blackjack');
  const slots = C.CASINO_GAMES.find((g) => g.id === 'slots');
  assert.ok(bj);
  assert.ok(slots);
  assert.equal(bj.playable, true);
  assert.notEqual(bj.status, 'soon');
  assert.equal(slots.playable, false);
  assert.equal(slots.status, 'soon');
});

test('playerLobbyLabel：訪客與店長文案', () => {
  assert.equal(C.playerLobbyLabel(null), '訪客');
  assert.equal(C.playerLobbyLabel({ authenticated: false }), '訪客');
  assert.match(C.playerLobbyLabel({ authenticated: true }), /店長/);
});

test('island-casino 匯入 island-blackjack', () => {
  assert.match(source, /from '\.\/island-blackjack\.js'/);
  assert.equal(C.MIN_BET, 10);
  assert.equal(C.STARTING_BALANCE, 1000);
});