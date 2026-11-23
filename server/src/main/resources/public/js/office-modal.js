/** 辦公室場景浮窗共用：互斥、卷軸鎖定、返回鍵、焦點圈。 */
import {
  buildOfficeOpenUrl,
  officeOpenFromPopstateHref,
  stripOfficeOpenSearch
} from './office-nav-history.js';
import { parseRoadmapOpenParam } from './dev-machines-format.js';

const FOCUSABLE =
  'a[href],area[href],button:not([disabled]),input:not([disabled]):not([type="hidden"]),' +
  'select:not([disabled]),textarea:not([disabled]),[tabindex]:not([tabindex="-1"])';

function focusableIn(root) {
  if (!root || !root.querySelectorAll) return [];
  return Array.from(root.querySelectorAll(FOCUSABLE)).filter((el) => {
    if (el.hasAttribute('hidden')) return false;
    if (el.getAttribute('aria-hidden') === 'true') return false;
    return true;
  });
}

export function createOfficeModalManager(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const onPauseScene = typeof (opts && opts.onPauseScene) === 'function' ? opts.onPauseScene : null;
  const closers = new Map();
  const roots = new Map();
  let activeId = '';
  let lastTrigger = null;
  let historyDepth = 0;
  let suppressHistory = false;

  function lockBody(want) {
    const body = doc && doc.body;
    if (!body) return;
    if (want) {
      const y = win && typeof win.scrollY === 'number' ? win.scrollY : 0;
      if (body.dataset) body.dataset.officeModalScrollY = String(y);
      body.style.top = (-y) + 'px';
      body.classList.add('roadmap-open', 'office-modal-open');
      return;
    }
    const y = body.dataset ? Number(body.dataset.officeModalScrollY || 0) : 0;
    body.style.top = '';
    if (body.dataset) delete body.dataset.officeModalScrollY;
    body.classList.remove('roadmap-open', 'office-modal-open');
    if (win && typeof win.scrollTo === 'function') {
      win.scrollTo(0, Number.isFinite(y) ? y : 0);
    }
  }

  function restoreFocus() {
    if (lastTrigger && typeof lastTrigger.focus === 'function') {
      try {
        lastTrigger.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
    lastTrigger = null;
  }

  function syncPause() {
    if (onPauseScene) onPauseScene(!!activeId);
  }

  function syncHistoryOnOpen(id) {
    if (!win.location || !win.history || suppressHistory) return;
    try {
      const path = win.location.pathname || '/';
      const search = win.location.search || '';
      const openNow = parseRoadmapOpenParam(search);
      if (openNow === id) return;
      const hash = win.location.hash || '';
      win.history.pushState({ punchclockOfficeModal: id }, '', buildOfficeOpenUrl(path, id) + hash);
      historyDepth += 1;
    } catch (_e) { /* ignore */ }
  }

  function syncHistoryOnClose() {
    if (!win.location || !win.history || suppressHistory) return;
    try {
      const path = win.location.pathname || '/';
      const clean = stripOfficeOpenSearch(win.location.search || '');
      const hash = win.location.hash || '';
      const next = path + clean + hash;
      const now = path + (win.location.search || '') + hash;
      if (now !== next) win.history.replaceState(null, '', next);
      historyDepth = Math.max(0, historyDepth - 1);
    } catch (_e) { /* ignore */ }
  }

  function closeId(id, optsClose) {
    const closeFn = closers.get(id);
    if (typeof closeFn === 'function') closeFn(optsClose);
  }

  function closeOthers(keepId) {
    closers.forEach((_fn, id) => {
      if (id !== keepId) closeId(id, { fromManager: true, silent: true });
    });
  }

  function onKeyDown(ev) {
    if (!activeId || !ev) return;
    if (ev.key === 'Tab') {
      const root = roots.get(activeId);
      if (!root) return;
      const items = focusableIn(root);
      if (!items.length) return;
      const first = items[0];
      const last = items[items.length - 1];
      const active = doc.activeElement;
      if (ev.shiftKey) {
        if (active === first || !root.contains(active)) {
          ev.preventDefault();
          last.focus({ preventScroll: true });
        }
      } else if (active === last) {
        ev.preventDefault();
        first.focus({ preventScroll: true });
      }
    }
  }

  if (doc && typeof doc.addEventListener === 'function') {
    doc.addEventListener('keydown', onKeyDown);
  }

  function register(id, api) {
    if (!id || !api) return;
    closers.set(id, () => {
      if (typeof api.close === 'function') api.close({ fromManager: true });
    });
    if (api.root) roots.set(id, api.root);
  }

  function notifyOpened(id, triggerEl) {
    if (id === activeId) return;
    closeOthers(id);
    activeId = id;
    if (triggerEl) lastTrigger = triggerEl;
    else if (!lastTrigger) lastTrigger = doc.activeElement;
    lockBody(true);
    syncPause();
    syncHistoryOnOpen(id);
  }

  function notifyClosed(id) {
    if (activeId && id !== activeId) return;
    activeId = '';
    lockBody(false);
    syncPause();
    syncHistoryOnClose();
    restoreFocus();
  }

  function openExclusive(id, opener) {
    lastTrigger = doc.activeElement;
    closeOthers(id);
    suppressHistory = false;
    if (typeof opener === 'function') opener();
  }

  function handlePopstate() {
    const token = officeOpenFromPopstateHref(win.location && win.location.href);
    if (!token) {
      if (activeId) {
        suppressHistory = true;
        closeId(activeId, { fromHistory: true });
        activeId = '';
        lockBody(false);
        syncPause();
        restoreFocus();
        suppressHistory = false;
      }
      historyDepth = 0;
      return;
    }
    if (token === activeId) return;
    const openFn = (opts && opts.openByToken) || null;
    if (typeof openFn === 'function') {
      suppressHistory = true;
      openFn(token);
      suppressHistory = false;
    }
  }

  if (win && typeof win.addEventListener === 'function') {
    win.addEventListener('popstate', handlePopstate);
  }

  return {
    register,
    notifyOpened,
    notifyClosed,
    openExclusive,
    isActive(id) {
      return activeId === id;
    },
    activeId() {
      return activeId;
    },
    destroy() {
      if (doc && typeof doc.removeEventListener === 'function') {
        doc.removeEventListener('keydown', onKeyDown);
      }
      if (win && typeof win.removeEventListener === 'function') {
        win.removeEventListener('popstate', handlePopstate);
      }
      closers.forEach((_fn, id) => closeId(id, { fromManager: true }));
      activeId = '';
      lockBody(false);
      syncPause();
    }
  };
}