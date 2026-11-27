/** 掌機虛擬籌碼（localStorage）。 */

export const POCKET_CHIPS_KEY_PREFIX = 'punchclock-pocket-chips:';
export const POCKET_SOUND_KEY_PREFIX = 'punchclock-pocket-sound:';
export const POCKET_DEFAULT_CHIPS = 100;

export function pocketUserLabel(win) {
  const w = win || globalThis;
  const u = w.PUNCHCLOCK_USERNAME;
  if (u != null && String(u).trim() !== '') return String(u).trim();
  return 'guest';
}

export function pocketChipsStorageKey(userLabel) {
  const label = userLabel == null ? 'guest' : String(userLabel).trim();
  return POCKET_CHIPS_KEY_PREFIX + (label === '' ? 'guest' : label);
}

export function pocketSoundStorageKey(userLabel) {
  const label = userLabel == null ? 'guest' : String(userLabel).trim();
  return POCKET_SOUND_KEY_PREFIX + (label === '' ? 'guest' : label);
}

export function readPocketChips(storage, key) {
  if (!storage || typeof storage.getItem !== 'function') return POCKET_DEFAULT_CHIPS;
  const k = key == null ? '' : String(key);
  if (!k) return POCKET_DEFAULT_CHIPS;
  try {
    const raw = storage.getItem(k);
    if (raw == null || raw === '') return POCKET_DEFAULT_CHIPS;
    const n = parseInt(raw, 10);
    if (!Number.isFinite(n) || n < 0) return POCKET_DEFAULT_CHIPS;
    return n;
  } catch (_e) {
    return POCKET_DEFAULT_CHIPS;
  }
}

export function writePocketChips(storage, key, value) {
  if (!storage || typeof storage.setItem !== 'function') return false;
  const k = key == null ? '' : String(key);
  if (!k) return false;
  const n = Math.max(0, Math.floor(Number(value)));
  if (!Number.isFinite(n)) return false;
  try {
    storage.setItem(k, String(n));
    return true;
  } catch (_e) {
    return false;
  }
}

/** 歸零時免費補幣；否則回傳原值。 */
export function pocketChipsAfterStartRefill(chips) {
  const n = Math.max(0, Math.floor(Number(chips)));
  if (n > 0) return n;
  return POCKET_DEFAULT_CHIPS;
}

export function readPocketSoundEnabled(storage, key) {
  if (!storage || typeof storage.getItem !== 'function') return false;
  const k = key == null ? '' : String(key);
  if (!k) return false;
  try {
    return storage.getItem(k) === '1';
  } catch (_e) {
    return false;
  }
}

export function writePocketSoundEnabled(storage, key, enabled) {
  if (!storage || typeof storage.setItem !== 'function') return false;
  const k = key == null ? '' : String(key);
  if (!k) return false;
  try {
    storage.setItem(k, enabled ? '1' : '0');
    return true;
  } catch (_e) {
    return false;
  }
}