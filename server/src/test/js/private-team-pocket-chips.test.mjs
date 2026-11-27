import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  POCKET_CHIPS_KEY_PREFIX,
  POCKET_DEFAULT_CHIPS,
  pocketChipsAfterStartRefill,
  pocketChipsStorageKey,
  readPocketChips,
  writePocketChips
} = await loadPublicModule('private-team-pocket-chips.js');

test('籌碼 storage key 含使用者或 guest', () => {
  assert.equal(pocketChipsStorageKey('alice'), POCKET_CHIPS_KEY_PREFIX + 'alice');
  assert.equal(pocketChipsStorageKey(''), POCKET_CHIPS_KEY_PREFIX + 'guest');
  assert.equal(pocketChipsStorageKey(null), POCKET_CHIPS_KEY_PREFIX + 'guest');
});

test('讀寫籌碼與預設值', () => {
  const store = new Map();
  const storage = {
    getItem(k) {
      return store.has(k) ? store.get(k) : null;
    },
    setItem(k, v) {
      store.set(k, String(v));
    }
  };
  const key = pocketChipsStorageKey('bob');
  assert.equal(readPocketChips(storage, key), POCKET_DEFAULT_CHIPS);
  writePocketChips(storage, key, 42);
  assert.equal(readPocketChips(storage, key), 42);
  writePocketChips(storage, key, -5);
  assert.equal(readPocketChips(storage, key), 0);
});

test('歸零時 Start 補幣回預設', () => {
  assert.equal(pocketChipsAfterStartRefill(50), 50);
  assert.equal(pocketChipsAfterStartRefill(0), POCKET_DEFAULT_CHIPS);
  assert.equal(pocketChipsAfterStartRefill(-1), POCKET_DEFAULT_CHIPS);
});