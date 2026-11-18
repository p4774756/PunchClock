import { cleanNick, loadAvatar, loadNick, normalizeAvatar, saveAvatar } from './island-presence.js';

export const MOBILE_MAX_WIDTH = 760;
const REM = 16;

/** 供 Node 測試估算手機 dock 是否落在可視範圍、是否與搖桿重疊（對齊 island.css @760px）。 */
export function joystickRectMobile(viewportWidth, viewportHeight) {
  const left = 0.7 * REM;
  const width = 8 * REM;
  const height = 8 * REM;
  const bottomInset = 0.85 * REM;
  const top = viewportHeight - bottomInset - height;
  return { left, right: left + width, top, bottom: viewportHeight - bottomInset, width, height };
}

function dockToolWidth(label, narrow) {
  const minW = (narrow ? 2.45 : 2.8) * REM;
  const pad = (narrow ? 0.45 : 0.6) * REM * 2;
  const font = (narrow ? 0.7 : 0.75) * REM;
  const approxChar = font * 0.92;
  const textW = Array.from(String(label)).length * approxChar;
  return Math.max(minW, pad + textW);
}

export function computeMobileDockBounds(viewportWidth, viewportHeight, exploring, peerLabel, showPeer) {
  const narrow = viewportWidth <= 400;
  const margin = 0.55 * REM;
  const gap = (narrow ? 0.25 : 0.35) * REM;
  const toolH = 2.25 * REM;
  const labels = exploring
    ? (showPeer ? ['退出', '聊天', peerLabel || '島上 1 人', '⋯'] : ['退出', '聊天', '⋯'])
  : ['探索', '聊天', '⋯'];
  let totalW = 0;
  for (let i = 0; i < labels.length; i++) {
    totalW += dockToolWidth(labels[i], narrow);
    if (i < labels.length - 1) totalW += gap;
  }
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const dockReserve = joy.right + 0.55 * REM;
  const maxW = viewportWidth - margin - dockReserve;
  if (totalW > maxW && showPeer && exploring) {
    const shrink = dockToolWidth(peerLabel || '島上 1 人', true) - dockToolWidth(peerLabel || '島上 1 人', narrow);
    if (shrink > 0) totalW -= shrink;
  }
  const right = viewportWidth - margin;
  const left = right - totalW;
  const bottom = viewportHeight - 0.7 * REM;
  return { left, right, top: bottom - toolH, bottom, width: totalW, height: toolH };
}

export function rectsIntersect(a, b) {
  return !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom);
}

export function dockFitsViewport(bounds, viewportWidth) {
  return bounds.left >= 0 && bounds.right <= viewportWidth + 0.5;
}

/** 探索中手機聊天面板預期邊界（對齊 island.css @760px body.is-exploring .island-chat）。 */
/** 手機角色選單預期邊界（對齊 island.css @760px body.island-avatar-open .island-avatar-pop）。 */
export function avatarPopRectMobile(viewportWidth, viewportHeight, exploring) {
  const popH = 7.15 * REM;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const margin = 0.55 * REM;
  const toolH = 2.25 * REM;
  const gap = 0.4 * REM;
  const bottomInset = 0.7 * REM;
  const left = exploring
    ? joy.right + 0.55 * REM
    : Math.max(margin, 0);
  const right = viewportWidth - margin;
  const bottom = viewportHeight - bottomInset - toolH - gap;
  const top = bottom - popH;
  return { left, right, top, bottom, width: right - left, height: popH, joy };
}

export function chatPanelRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem) {
  const hRem = panelHeightRem > 0 ? panelHeightRem : 12;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const dockBottom = (0.7 + 2.25 + 0.4) * REM;
  const joyClear = 0.85 * REM + 8 * REM + 0.35 * REM;
  const bottomInset = Math.max(dockBottom, joyClear);
  const left = 0.7 * REM + 8 * REM + 0.55 * REM;
  const right = viewportWidth - 0.55 * REM;
  const bottom = viewportHeight - bottomInset;
  const top = bottom - hRem * REM;
  return { left, right, top, bottom, width: right - left, height: hRem * REM, joy };
}

export function createCornerUi(opts) {
  const document = opts.document;
  const storage = opts.storage;
  const loadNickFn = opts.loadNick || loadNick;
  const cleanNickFn = opts.cleanNick || cleanNick;
  const getPresence = opts.getPresence;
  const getPeerCount = opts.getPeerCount;
  const isExploring = opts.isExploring;
  const isWelcomed = opts.isWelcomed;
  const getAudioMuted = opts.getAudioMuted;
  const onAvatarChange = opts.onAvatarChange;

  const avatarBtn = document.getElementById('islandAvatarBtn');
  const avatarPop = document.getElementById('islandAvatarPop');
  const avatarChoices = avatarPop
    ? avatarPop.querySelectorAll('[data-island-avatar]')
    : [];

  const nickBtn = document.getElementById('islandNickBtn');
  const nickPop = document.getElementById('islandNickPop');
  const nickInput = document.getElementById('islandNickInput');
  const nickSave = document.getElementById('islandNickSave');

  const moreBtn = document.getElementById('islandMoreBtn');
  const moreSheet = document.getElementById('islandMoreSheet');
  const moreClose = document.getElementById('islandMoreClose');
  const moreScrim = document.getElementById('islandMoreScrim');
  const moreAudioStatus = document.getElementById('islandMoreAudioStatus');
  const moreAudioRow = moreSheet
    ? moreSheet.querySelector('[data-island-more-target="islandAudio"]')
    : null;

  if (nickInput) {
    const storedNick = loadNickFn(storage);
    if (storedNick) nickInput.value = storedNick;
  }

  let selectedAvatar = loadAvatar(storage);

  function syncAvatarUi() {
    const current = normalizeAvatar(selectedAvatar);
    if (avatarChoices) {
      avatarChoices.forEach((btn) => {
        const id = btn.getAttribute('data-island-avatar');
        const on = normalizeAvatar(id) === current;
        btn.classList.toggle('is-selected', on);
        btn.setAttribute('aria-pressed', on ? 'true' : 'false');
      });
    }
    if (avatarBtn) {
      avatarBtn.textContent = current === 'berry' ? '小莓' : '蜜鹿';
      avatarBtn.setAttribute(
        'aria-label',
        '探索角色：' + (current === 'berry' ? '小莓' : '蜜鹿（預設）')
      );
    }
  }

  function setAvatarOpen(open) {
    if (!avatarPop || !avatarBtn) return;
    const on = !!open;
    avatarPop.hidden = !on;
    avatarBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    document.body.classList.toggle('island-avatar-open', on);
    if (on) {
      syncAvatarUi();
      setNickOpen(false);
    }
  }

  function pickAvatar(id) {
    const next = saveAvatar(storage, id);
    if (next === selectedAvatar) {
      setAvatarOpen(false);
      return;
    }
    selectedAvatar = next;
    syncAvatarUi();
    setAvatarOpen(false);
    if (typeof onAvatarChange === 'function') onAvatarChange(next);
  }

  syncAvatarUi();

  if (avatarBtn && avatarPop) {
    avatarBtn.addEventListener('click', () => setAvatarOpen(avatarPop.hidden));
  }
  if (avatarChoices) {
    avatarChoices.forEach((btn) => {
      btn.addEventListener('click', () => {
        const id = btn.getAttribute('data-island-avatar');
        if (id) pickAvatar(id);
      });
    });
  }

  function setNickOpen(open) {
    if (!nickPop || !nickBtn) return;
    nickPop.hidden = !open;
    nickBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
    if (open) {
      setAvatarOpen(false);
      if (nickInput) nickInput.focus();
    }
  }

  function saveNick() {
    const islandPresence = getPresence();
    if (!nickInput || !islandPresence) return;
    const cleaned = cleanNickFn(nickInput.value);
    if (!cleaned) return;
    nickInput.value = cleaned;
    islandPresence.rename(cleaned);
    setNickOpen(false);
  }

  if (nickBtn && nickPop) {
    nickBtn.addEventListener('click', () => setNickOpen(nickPop.hidden));
  }
  if (nickSave) nickSave.addEventListener('click', saveNick);
  if (nickInput) {
    nickInput.addEventListener('keydown', (event) => {
      if (event.key !== 'Enter' && event.key !== 'Escape') return;
      event.preventDefault();
      event.stopPropagation();
      if (event.key === 'Enter') saveNick();
      else setNickOpen(false);
    });
  }

  function syncPeerCount() {
    const el = document.getElementById('islandPeerCount');
    if (!el) return;
    const show = !!(isExploring() && isWelcomed());
    el.hidden = !show;
    if (!show) return;
    const count = typeof getPeerCount === 'function' ? getPeerCount() : 0;
    el.textContent = '島上 ' + count + ' 人';
  }

  function syncMoreAudioLabel() {
    if (!moreAudioStatus) return;
    const audioBtn = document.getElementById('islandAudio');
    if (moreAudioRow) {
      moreAudioRow.hidden = !audioBtn || audioBtn.hidden;
    }
    let muted = false;
    if (typeof getAudioMuted === 'function') muted = !!getAudioMuted();
    else if (audioBtn) muted = audioBtn.getAttribute('aria-pressed') === 'false';
    moreAudioStatus.textContent = muted ? '已靜音' : '開啟';
  }

  function setMoreOpen(open) {
    if (!moreSheet || !moreBtn) return;
    const on = !!open;
    moreSheet.hidden = !on;
    moreBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    document.body.classList.toggle('island-more-open', on);
    if (on) {
      setAvatarOpen(false);
      syncMoreAudioLabel();
    }
  }

  function closeMore() {
    setMoreOpen(false);
  }

  function runMoreTarget(id) {
    if (!id) return;
    const el = document.getElementById(id);
    if (!el) return;
    if (id === 'islandNickBtn') {
      setMoreOpen(false);
      setNickOpen(true);
      return;
    }
    if (id === 'islandAvatarBtn') {
      setMoreOpen(false);
      setAvatarOpen(true);
      return;
    }
    closeMore();
    el.click();
  }

  if (moreBtn) {
    moreBtn.addEventListener('click', () => setMoreOpen(moreSheet && moreSheet.hidden));
  }
  if (moreClose) moreClose.addEventListener('click', () => closeMore());
  if (moreScrim) moreScrim.addEventListener('click', () => closeMore());
  if (moreSheet) {
    moreSheet.querySelectorAll('[data-island-more-target]').forEach((row) => {
      row.addEventListener('click', (event) => {
        const id = row.getAttribute('data-island-more-target');
        if (!id) return;
        event.preventDefault();
        runMoreTarget(id);
      });
    });
    moreSheet.querySelectorAll('[data-island-more-login], [data-island-more-logout]').forEach((link) => {
      link.addEventListener('click', () => closeMore());
    });
  }

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    if (moreSheet && !moreSheet.hidden) {
      event.preventDefault();
      closeMore();
    }
  });

  return {
    syncPeerCount: syncPeerCount,
    setNickOpen: setNickOpen,
    saveNick: saveNick,
    setMoreOpen: setMoreOpen,
    closeMore: closeMore,
    syncMoreAudioLabel: syncMoreAudioLabel,
    setAvatarOpen: setAvatarOpen,
    syncAvatarUi: syncAvatarUi,
    currentAvatar: () => normalizeAvatar(selectedAvatar)
  };
}