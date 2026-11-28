/** 窗台 Game Boy 彈窗、按鍵與籌碼。 */

import { ensureHomeRoadmapCss } from './home-panel-css-loader.js';
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
import { POCKET_LCD_H, POCKET_LCD_W, drawPocketMaryLcd, makePocketLcdCanvas } from './private-team-pocket-lcd.js';
import {
  MARY_PHASE,
  createMaryState,
  maryAdvanceSpin,
  maryApplyBetButton,
  maryBeginSpin,
  maryCollectWin,
  maryTotalBet
} from './private-team-pocket-mary.js';

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

/** 量測未縮放機身尺寸，並依 92vw／92vh 上限算出等比 --pt-pocket-scale。 */
export function applyPocketShellFit(board, win = globalThis) {
  if (!board) return;
  const fit = board.querySelector('.pt-pocket-fit');
  const shell = board.querySelector('.pt-pocket-shell');
  if (!fit || !shell) return;
  fit.classList.add('pt-pocket-fit--measure');
  const w = shell.offsetWidth;
  const h = shell.offsetHeight;
  fit.classList.remove('pt-pocket-fit--measure');
  if (w < 1 || h < 1) return;
  board.style.setProperty('--pt-pocket-design-w', w + 'px');
  board.style.setProperty('--pt-pocket-design-h', h + 'px');
  const vw = win.innerWidth || 0;
  const vh = win.innerHeight || 0;
  const scale = Math.min((0.92 * vw) / w, (0.92 * vh) / h);
  board.style.setProperty('--pt-pocket-scale', String(scale));
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
  let mary = createMaryState(0);
  let spinRaf = 0;
  let spinClock = () => Date.now();

  function syncStorageKeys() {
    const label = pocketUserLabel(win);
    chipsKey = pocketChipsStorageKey(label);
    soundKey = pocketSoundStorageKey(label);
  }

  function loadChips() {
    syncStorageKeys();
    chips = readPocketChips(win.localStorage, chipsKey);
    mary = createMaryState(chips);
    mary.credit = chips;
  }

  function saveChips() {
    const c = Math.max(0, Math.floor(mary.credit));
    chips = c;
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
    const c = Math.max(0, Math.floor(mary.credit));
    if (chipReadout) chipReadout.textContent = '籌碼 ' + c;
    if (refillBtn) refillBtn.hidden = c > 0;
  }

  function paintLcd() {
    if (!lcdCanvas) return;
    const ctx = lcdCanvas.getContext('2d');
    if (!ctx) return;
    mary.credit = Math.max(0, Math.floor(mary.credit));
    drawPocketMaryLcd(ctx, mary, animFrame);
  }

  function setPressed(name, on) {
    const key = name == null ? '' : String(name);
    if (!key) return;
    if (on) pressedKeys[key] = true;
    else delete pressedKeys[key];
    setBtnPressedClass(key, on);
  }

  function setBtnPressedClass(key, on) {
    const btn = board.querySelector('[data-pocket-btn="' + key + '"]');
    if (btn) btn.classList.toggle('is-pressed', !!on);
  }

  function maybeBeep(kind) {
    if (!soundOn) return;
    try {
      const Ctx = win.AudioContext || win.webkitAudioContext;
      if (!Ctx) return;
      const ac = new Ctx();
      const osc = ac.createOscillator();
      const gain = ac.createGain();
      const freq = kind === 'win' ? 620 : kind === 'spin' ? 280 : 440;
      osc.frequency.value = freq;
      gain.gain.value = 0.04;
      osc.connect(gain);
      gain.connect(ac.destination);
      const dur = kind === 'spin' ? 0.03 : 0.05;
      osc.start();
      osc.stop(ac.currentTime + dur);
      osc.onended = () => ac.close();
    } catch (_e) {
      /* noop */
    }
  }

  function stopSpinLoop() {
    if (spinRaf) {
      win.cancelAnimationFrame(spinRaf);
      spinRaf = 0;
    }
  }

  function spinLoop() {
    if (!open || mary.phase !== MARY_PHASE.SPIN) {
      stopSpinLoop();
      return;
    }
    const now = spinClock();
    const out = maryAdvanceSpin(mary, now);
    mary = out.state;
    if (out.settled) {
      stopSpinLoop();
      maybeBeep(mary.pendingWin > 0 ? 'win' : 'spin');
      paintChipReadout();
      paintLcd();
      return;
    }
    if (out.again) {
      maybeBeep('spin');
    }
    paintLcd();
    spinRaf = win.requestAnimationFrame(spinLoop);
  }

  function beginStartSpin() {
    const stop = Math.floor(Math.random() * 24);
    const out = maryBeginSpin(mary, mary.credit, stop, spinClock());
    if (!out.ok) return false;
    mary = out.state;
    saveChips();
    paintChipReadout();
    maybeBeep('spin');
    stopSpinLoop();
    spinRaf = win.requestAnimationFrame(spinLoop);
    paintLcd();
    return true;
  }

  function activateButton(name) {
    setPressed(name, true);
    const key = name == null ? '' : String(name);

    if (key === 'start') {
      if (mary.phase === MARY_PHASE.BET && maryTotalBet(mary) > 0) {
        if (beginStartSpin()) {
          win.clearTimeout(animTimer);
          animTimer = win.setTimeout(() => setPressed(name, false), 120);
          return;
        }
      }
      if (mary.credit <= 0 && maryTotalBet(mary) <= 0) {
        const next = pocketChipsAfterStartRefill(0);
        mary.credit = next;
        chips = next;
        saveChips();
        paintChipReadout();
        maybeBeep('spin');
      }
    } else {
      const out = maryApplyBetButton(mary, mary.credit, key);
      mary = out.state;
      if (out.credit !== mary.credit) {
        mary.credit = out.credit;
        saveChips();
        paintChipReadout();
      }
      if (key === 'a' && mary.phase === MARY_PHASE.BET) maybeBeep('spin');
      else maybeBeep('spin');
    }

    win.clearTimeout(animTimer);
    animTimer = win.setTimeout(() => setPressed(name, false), 120);
    paintLcd();
  }

  function tickAnim() {
    animFrame += 1;
    if (open) paintLcd();
  }

  let tickId = win.setInterval(tickAnim, 200);

  function setOpen(next, meta) {
    const want = !!next;
    if (want === open) return;
    open = want;
    board.hidden = !open;
    board.setAttribute('aria-hidden', open ? 'false' : 'true');
    if (open) {
      ensureHomeRoadmapCss(win, doc).catch(() => {});
      loadChips();
      loadSound();
      paintChipReadout();
      paintLcd();
      applyPocketShellFit(board, win);
      if (typeof win.requestAnimationFrame === 'function') {
        win.requestAnimationFrame(() => applyPocketShellFit(board, win));
      }
      if (officeModal) officeModal.notifyOpened('pocket', meta && meta.trigger);
      lastFocus = doc.activeElement;
      const focusTarget = closeBtn || lcdCanvas;
      if (focusTarget && focusTarget.focus) focusTarget.focus({ preventScroll: true });
    } else {
      stopSpinLoop();
      if (mary.phase === MARY_PHASE.WIN && mary.pendingWin > 0) {
        const collected = maryCollectWin(mary, mary.credit);
        mary = collected.state;
        saveChips();
      }
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
    mary.credit = pocketChipsAfterStartRefill(0);
    chips = mary.credit;
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

  const fit = board.querySelector('.pt-pocket-fit');
  if (fit) {
    fit.addEventListener('click', (ev) => ev.stopPropagation());
  }

  function onPocketResize() {
    if (open) applyPocketShellFit(board, win);
  }
  win.addEventListener('resize', onPocketResize);

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
    stopSpinLoop();
    win.clearInterval(tickId);
    win.removeEventListener('resize', onPocketResize);
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