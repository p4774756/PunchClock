/** 窗台 Game Boy 彈窗、按鍵與籌碼。 */

import {
  pocketChipsAfterStartRefill,
  pocketChipsStorageKey,
  pocketSoundStorageKey,
  pocketUserLabel,
  readPocketChips,
  readPocketSoundEnabled,
  writePocketChips,
  writePocketSoundEnabled
} from './private-team-pocket-chips.js';
import { POCKET_LCD_H, POCKET_LCD_W, drawPocketIdleLcd, makePocketLcdCanvas } from './private-team-pocket-lcd.js';

export const POCKET_BTN_KEYS = ['up', 'down', 'left', 'right', 'a', 'b', 'start', 'select'];

const KEY_MAP = {
  ArrowUp: 'up',
  ArrowDown: 'down',
  ArrowLeft: 'left',
  ArrowRight: 'right',
  KeyZ: 'a',
  KeyX: 'b',
  Enter: 'start',
  ShiftLeft: 'select',
  ShiftRight: 'select'
};

export function pocketButtonFromKeyboard(ev) {
  if (!ev || ev.code == null) return '';
  return KEY_MAP[ev.code] || '';
}

export function wireGameBoyModal(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const onOpenChange = typeof (opts && opts.onOpenChange) === 'function' ? opts.onOpenChange : null;
  const officeModal = opts && opts.officeModal;

  const board = doc.getElementById('pocketBoard');
  if (!board) {
    return {
      open() {},
      close() {},
      isOpen() {
        return false;
      }
    };
  }

  const lcdCanvas = board.querySelector('[data-pocket-lcd]');
  const chipReadout = board.querySelector('[data-pocket-chips]');
  const soundBtn = board.querySelector('[data-pocket-sound]');
  const closeBtn = board.querySelector('[data-pocket-close]');
  const refillBtn = board.querySelector('[data-pocket-refill]');

  let open = false;
  let lastFocus = null;
  let chips = 0;
  let soundOn = false;
  let pressedKeys = {};
  let animFrame = 0;
  let animTimer = 0;
  let chipsKey = '';
  let soundKey = '';

  function syncStorageKeys() {
    const label = pocketUserLabel(win);
    chipsKey = pocketChipsStorageKey(label);
    soundKey = pocketSoundStorageKey(label);
  }

  function loadChips() {
    syncStorageKeys();
    chips = readPocketChips(win.localStorage, chipsKey);
  }

  function saveChips() {
    writePocketChips(win.localStorage, chipsKey, chips);
  }

  function loadSound() {
    syncStorageKeys();
    soundOn = readPocketSoundEnabled(win.localStorage, soundKey);
    paintSoundBtn();
  }

  function paintSoundBtn() {
    if (!soundBtn) return;
    soundBtn.setAttribute('aria-pressed', soundOn ? 'true' : 'false');
    soundBtn.textContent = soundOn ? '音效開' : '音效關';
  }

  function paintChipReadout() {
    if (chipReadout) chipReadout.textContent = '籌碼 ' + chips;
    if (refillBtn) refillBtn.hidden = chips > 0;
  }

  function paintLcd() {
    if (!lcdCanvas) return;
    const ctx = lcdCanvas.getContext('2d');
    if (!ctx) return;
    drawPocketIdleLcd(ctx, { chips, pressedKeys, frame: animFrame });
  }

  function setPressed(name, on) {
    const key = name == null ? '' : String(name);
    if (!key) return;
    if (on) pressedKeys[key] = true;
    else delete pressedKeys[key];
    paintLcd();
    setBtnPressedClass(key, on);
  }

  function setBtnPressedClass(key, on) {
    const btn = board.querySelector('[data-pocket-btn="' + key + '"]');
    if (btn) btn.classList.toggle('is-pressed', !!on);
  }

  function maybeBeep(btnName) {
    if (!soundOn) return;
    try {
      const Ctx = win.AudioContext || win.webkitAudioContext;
      if (!Ctx) return;
      const ac = new Ctx();
      const osc = ac.createOscillator();
      const gain = ac.createGain();
      osc.frequency.value = btnName === 'start' ? 520 : 440;
      gain.gain.value = 0.04;
      osc.connect(gain);
      gain.connect(ac.destination);
      osc.start();
      osc.stop(ac.currentTime + 0.05);
      osc.onended = () => ac.close();
    } catch (_e) {
      /* noop */
    }
  }

  function activateButton(name) {
    setPressed(name, true);
    maybeBeep(name);
    if (name === 'start') {
      const next = pocketChipsAfterStartRefill(chips);
      if (next !== chips) {
        chips = next;
        saveChips();
        paintChipReadout();
      }
    }
    win.clearTimeout(animTimer);
    animTimer = win.setTimeout(() => setPressed(name, false), 120);
    paintLcd();
  }

  function tickAnim() {
    animFrame += 1;
    if (open) paintLcd();
  }

  let tickId = win.setInterval(tickAnim, 400);

  function setOpen(next, meta) {
    const want = !!next;
    if (want === open) return;
    open = want;
    board.hidden = !open;
    board.setAttribute('aria-hidden', open ? 'false' : 'true');
    if (open) {
      loadChips();
      loadSound();
      paintChipReadout();
      paintLcd();
      if (officeModal) officeModal.notifyOpened('pocket', meta && meta.trigger);
      lastFocus = doc.activeElement;
      const focusTarget = closeBtn || lcdCanvas;
      if (focusTarget && focusTarget.focus) focusTarget.focus({ preventScroll: true });
    } else {
      pressedKeys = {};
      POCKET_BTN_KEYS.forEach((k) => setBtnPressedClass(k, false));
      if (!meta || !meta.silent) {
        if (officeModal) officeModal.notifyClosed('pocket');
      }
      if (lastFocus && lastFocus.focus) {
        try {
          lastFocus.focus({ preventScroll: true });
        } catch (_e) {
          /* noop */
        }
      }
      lastFocus = null;
    }
    if (onOpenChange) onOpenChange(open);
  }

  function onRefillClick(ev) {
    ev.preventDefault();
    chips = pocketChipsAfterStartRefill(0);
    saveChips();
    paintChipReadout();
    paintLcd();
  }

  function onSoundClick(ev) {
    ev.preventDefault();
    soundOn = !soundOn;
    writePocketSoundEnabled(win.localStorage, soundKey, soundOn);
    paintSoundBtn();
  }

  board.querySelectorAll('[data-pocket-btn]').forEach((btn) => {
    const name = btn.getAttribute('data-pocket-btn');
    btn.addEventListener('pointerdown', (ev) => {
      ev.preventDefault();
      activateButton(name);
    });
    btn.addEventListener('click', (ev) => ev.preventDefault());
  });

  if (refillBtn) refillBtn.addEventListener('click', onRefillClick);
  if (soundBtn) soundBtn.addEventListener('click', onSoundClick);

  if (closeBtn) {
    closeBtn.addEventListener('click', () => setOpen(false));
  }

  board.addEventListener('click', (ev) => {
    if (ev.target === board) setOpen(false);
  });

  const shell = board.querySelector('.pt-pocket-shell');
  if (shell) {
    shell.addEventListener('click', (ev) => ev.stopPropagation());
  }

  function onKeyDown(ev) {
    if (!open) return;
    const btn = pocketButtonFromKeyboard(ev);
    if (!btn) return;
    ev.preventDefault();
    activateButton(btn);
    if (btn === 'start' && ev.repeat) return;
  }

  function onKeyUp(ev) {
    if (!open) return;
    const btn = pocketButtonFromKeyboard(ev);
    if (btn) setPressed(btn, false);
  }

  doc.addEventListener('keydown', onKeyDown);
  doc.addEventListener('keyup', onKeyUp);

  function destroy() {
    win.clearInterval(tickId);
    doc.removeEventListener('keydown', onKeyDown);
    doc.removeEventListener('keyup', onKeyUp);
  }

  return {
    open(trigger) {
      setOpen(true, { trigger });
    },
    close(meta) {
      setOpen(false, meta);
    },
    isOpen() {
      return open;
    },
    destroy
  };
}

export function ensurePocketLcdCanvasInBoard(doc) {
  const board = doc.getElementById('pocketBoard');
  if (!board) return null;
  let c = board.querySelector('[data-pocket-lcd]');
  if (c) return c;
  c = makePocketLcdCanvas(doc);
  if (!c) return null;
  c.setAttribute('data-pocket-lcd', '');
  c.width = POCKET_LCD_W;
  c.height = POCKET_LCD_H;
  const wrap = board.querySelector('[data-pocket-lcd-wrap]');
  if (wrap) wrap.appendChild(c);
  return c;
}