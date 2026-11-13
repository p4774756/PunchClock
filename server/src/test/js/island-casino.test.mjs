// 賭場大廳純邏輯：node --test server/src/test/js/island-casino.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

const here = dirname(fileURLToPath(import.meta.url));
const modulePath = join(here, '..', '..', 'main', 'resources', 'public', 'js', 'island-casino.js');
const source = readFileSync(modulePath, 'utf8');
const C = await import('data:text/javascript;base64,' + Buffer.from(source).toString('base64'));

test('lobbyState：開關大廳', () => {
  const init = C.lobbyState(null, '');
  assert.deepEqual(init, { open: false });
  assert.equal(C.isLobbyOpen(init), false);
  const open = C.lobbyState(init, 'open');
  assert.equal(C.isLobbyOpen(open), true);
  assert.deepEqual(C.lobbyState(open, 'close'), init);
});

test('chipWalletView：預設為尚未啟用佔位，啟用後才顯示餘額', () => {
  const placeholder = C.chipWalletView(null);
  assert.equal(placeholder.enabled, false);
  assert.equal(placeholder.balance, null);
  assert.match(placeholder.text, /尚未啟用/);
  assert.ok(placeholder.note);

  const live = C.chipWalletView({ enabled: true, balance: 500, currency: '籌碼' });
  assert.equal(live.enabled, true);
  assert.equal(live.balance, 500);
  assert.equal(live.text, '500');
  assert.equal(live.label, '籌碼');
});

test('遊戲卡片皆為即將推出且不可玩', () => {
  assert.ok(C.CASINO_GAMES.length >= 2);
  for (const game of C.CASINO_GAMES) {
    assert.equal(game.playable, false);
    assert.equal(game.status, 'soon');
  }
  assert.ok(C.CASINO_GAMES.some((g) => g.id === 'blackjack'));
  assert.ok(C.CASINO_GAMES.some((g) => g.id === 'slots'));
});

test('playerLobbyLabel：訪客與店長文案', () => {
  assert.equal(C.playerLobbyLabel(null), '訪客');
  assert.equal(C.playerLobbyLabel({ authenticated: false }), '訪客');
  assert.match(C.playerLobbyLabel({ authenticated: true }), /店長/);
});
