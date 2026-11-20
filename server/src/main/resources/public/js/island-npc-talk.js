// 探索模式路人對話：楓之谷風視窗與打字機；可測邏輯盡量純函式。
import {
  pickNpcLinesWithWorld,
  npcTalkShouldClose,
  NPC_TALK_CLOSE_DIST
} from './island-people-data.js';

export const TYPEWRITER_CPS = 37;
export const TYPEWRITER_CPS_MIN = 30;
export const TYPEWRITER_CPS_MAX = 45;

export function typewriterCps(reduced) {
  if (reduced) return TYPEWRITER_CPS_MAX * 10;
  return TYPEWRITER_CPS;
}

export function npcTalkPromptLabel(name) {
  return '和 ' + String(name || '路人') + ' 聊天';
}

/** 推進打字機；回傳 { text, done } */
export function advanceTypewriter(state, dt) {
  const line = state.line || '';
  const shown = state.shown != null ? state.shown : 0;
  const cps = typewriterCps(state.reduced);
  const next = Math.min(line.length, shown + Math.max(1, Math.floor(dt * cps)));
  return { text: line.slice(0, next), shown: next, done: next >= line.length };
}

/**
 * 使用者點擊／Space／Enter 時：若在打字先補完；否則換句或關閉。
 * @returns {'typing-done'|'next-line'|'close'|'idle'}
 */
export function talkDialogAdvance(state) {
  if (!state || !state.open) return 'idle';
  if (state.shown < (state.line || '').length) {
    return 'typing-done';
  }
  const nextIdx = (state.lineIndex || 0) + 1;
  if (nextIdx >= (state.lines || []).length) return 'close';
  return 'next-line';
}

export function createTalkSession(npc, rng, reduced, lastOpening, world) {
  const picked = pickNpcLinesWithWorld(npc.style, npc.islandId, lastOpening, rng, world);
  return {
    talkKey: npc.talkKey,
    name: npc.name,
    style: npc.style,
    islandId: npc.islandId,
    nx: npc.x,
    nz: npc.z,
    seated: !!npc.seated,
    lines: picked.lines,
    openingKey: picked.openingKey,
    lineIndex: 0,
    line: picked.lines[0] || '',
    shown: 0,
    reduced: !!reduced,
    open: true,
    greetT0: 0
  };
}

export function createIslandNpcTalk(options) {
  const opts = options || {};
  const doc = opts.document || (typeof document !== 'undefined' ? document : null);
  const win = opts.win || (typeof window !== 'undefined' ? window : null);
  const reduced = !!opts.reduced;
  const hooks = opts.hooks || {};
  let session = null;
  let lastOpeningByKey = Object.create(null);
  let typeAcc = 0;

  const root = doc ? doc.getElementById('islandNpcTalk') : null;
  const titleEl = doc ? doc.getElementById('islandNpcTalkTitle') : null;
  const bodyEl = doc ? doc.getElementById('islandNpcTalkBody') : null;
  const hintEl = doc ? doc.getElementById('islandNpcTalkHint') : null;
  const closeBtn = doc ? doc.getElementById('islandNpcTalkClose') : null;

  function paint() {
    if (!root || !session) return;
    if (titleEl) titleEl.textContent = session.name || '';
    const full = session.reduced ? session.line : session.line.slice(0, session.shown);
    if (bodyEl) bodyEl.textContent = full;
    const more = session.lineIndex < session.lines.length - 1
      || session.shown < session.line.length;
    if (hintEl) {
      hintEl.textContent = session.shown < session.line.length ? '▼' : (more ? '下一句 ▼' : '再按關閉');
      hintEl.hidden = false;
    }
  }

  function setVisible(on) {
    if (!root) return;
    root.hidden = !on;
    if (doc && doc.body) doc.body.classList.toggle('island-npc-talk-open', !!on);
  }

  function close() {
    if (!session) {
      setVisible(false);
      return;
    }
    const key = session.talkKey;
    if (typeof hooks.onClose === 'function') hooks.onClose(session);
    session = null;
    typeAcc = 0;
    setVisible(false);
    if (typeof hooks.onEnded === 'function') hooks.onEnded(key);
  }

  function beginLine() {
    if (!session) return;
    const idx = session.lineIndex;
    session.line = session.lines[idx] || '';
    session.shown = session.reduced ? session.line.length : 0;
    paint();
  }

  function open(npc) {
    if (!npc || !root) return false;
    if (session) close();
    const last = lastOpeningByKey[npc.talkKey];
    const rng = typeof opts.rng === 'function' ? opts.rng : Math.random;
    const world = typeof opts.getWorld === 'function' ? opts.getWorld() : null;
    session = createTalkSession(npc, rng, reduced, last, world);
    session.greetT0 = win && win.performance ? win.performance.now() : 0;
    lastOpeningByKey[npc.talkKey] = session.openingKey;
    setVisible(true);
    beginLine();
    if (typeof hooks.onOpen === 'function') hooks.onOpen(session);
    return true;
  }

  function advance() {
    if (!session) return false;
    const action = talkDialogAdvance(session);
    if (action === 'typing-done') {
      session.shown = session.line.length;
      paint();
      return true;
    }
    if (action === 'next-line') {
      session.lineIndex += 1;
      beginLine();
      return true;
    }
    if (action === 'close') {
      close();
      return true;
    }
    return false;
  }

  function tick(dt, player) {
    if (!session || !player) return;
    if (npcTalkShouldClose(player.x, player.z, session.nx, session.nz)) {
      close();
      return;
    }
    if (session.reduced) return;
    if (session.shown >= session.line.length) return;
    const step = advanceTypewriter(session, dt);
    session.shown = step.shown;
    paint();
  }

  function isOpen() {
    return !!(session && session.open);
  }

  function getSession() {
    return session;
  }

  function interactPromptLabel() {
    if (!session) return '';
    if (session.shown < session.line.length) return '下一句';
    if (session.lineIndex < session.lines.length - 1) return '下一句';
    return '';
  }

  function onPointerActivate(event) {
    if (!session || !root) return;
    event.preventDefault();
    advance();
  }

  if (root) {
    root.addEventListener('click', onPointerActivate);
    root.addEventListener('pointerup', (event) => {
      if (event.pointerType === 'mouse' && event.button !== 0) return;
      onPointerActivate(event);
    });
  }
  if (closeBtn) {
    closeBtn.addEventListener('click', (event) => {
      event.preventDefault();
      event.stopPropagation();
      close();
    });
  }

  return {
    open: open,
    close: close,
    advance: advance,
    tick: tick,
    isOpen: isOpen,
    getSession: getSession,
    interactPromptLabel: interactPromptLabel,
    destroy: () => {
      close();
      if (root) root.removeEventListener('click', onPointerActivate);
    }
  };
}

export { NPC_TALK_CLOSE_DIST };