import { cleanNick, loadAvatar, loadNick, normalizeAvatar, saveAvatar } from './island-presence.js';

export const MOBILE_MAX_WIDTH = 760;
const REM = 16;
export const ORB_SIZE = 2.85 * REM;
export const ORB_GAP = 0.4 * REM;
const HUD_MARGIN = 0.55 * REM;

const AUDIO_ON_SVG =
  '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round" d="M5 10v4h3l4.5 3V7L8 10H5z"/><path fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" d="M17 9.5v5"/></svg>';
const AUDIO_OFF_SVG =
  '<svg viewBox="0 0 24 24" width="22" height="22"><path fill="none" stroke="currentColor" stroke-width="2.2" stroke-linejoin="round" d="M5 10v4h3l4.5 3V7L8 10H5z"/><path fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" d="M16 10l4 4M20 10l-4 4"/></svg>';

/** 供 Node 測試估算手機搖桿與 HUD 是否重疊（對齊 island.css）。 */
export function joystickRectMobile(viewportWidth, viewportHeight) {
  const narrow = viewportWidth <= MOBILE_MAX_WIDTH;
  const left = (narrow ? 0.7 : 1) * REM;
  const width = (narrow ? 8 : 7.5) * REM;
  const height = width;
  const bottomInset = (narrow ? 0.85 : 1.1) * REM;
  const top = viewportHeight - bottomInset - height;
  return { left, right: left + width, top, bottom: viewportHeight - bottomInset, width, height };
}

function hudMargin(viewportWidth) {
  return viewportWidth <= MOBILE_MAX_WIDTH ? 0.55 * REM : 0.85 * REM;
}

function orbRowCount(showAudio) {
  return showAudio ? 4 : 3;
}

export function hudTopRightRect(viewportWidth, viewportHeight, showAudio) {
  const margin = hudMargin(viewportWidth);
  const orbs = orbRowCount(showAudio);
  const dayNightBlock = ORB_SIZE;
  const audioW = showAudio ? ORB_SIZE + ORB_GAP : 0;
  const chatMenuW = ORB_SIZE * 2 + ORB_GAP;
  const totalW = dayNightBlock + audioW + chatMenuW;
  const right = viewportWidth - margin;
  const left = right - totalW;
  const top = (viewportWidth <= MOBILE_MAX_WIDTH ? 4.55 : 3.6) * REM;
  const bottom = top + ORB_SIZE + 0.9 * REM;
  return { left, right, top, bottom, width: totalW, height: bottom - top, orbSize: ORB_SIZE };
}

const EXPLORE_EXIT_MIN_W = 5.5 * REM;
const INTERACT_BTN_W = 7.5625 * REM;
const ROADMAP_BTN_W = 6.25 * REM;
const STEER_BTN_W = 5.5 * REM;
const HUD_CHIP = 2.85 * REM;

/** 探索中右下角「退出探索」主按鈕（對齊 island.css）。 */
export function exploreExitButtonRect(viewportWidth, viewportHeight) {
  const narrow = viewportWidth <= MOBILE_MAX_WIDTH;
  const margin = hudMargin(viewportWidth);
  const height = 3.75 * REM;
  const width = narrow ? EXPLORE_EXIT_MIN_W : 5.85 * REM;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const right = viewportWidth - margin;
  const left = right - width;
  let bottom;
  let top;
  if (narrow) {
    const layout = exploreHudActionItems(viewportWidth, viewportHeight, {
      withInteract: true,
      withSteer: true
    });
    const rowGap = 0.45 * REM;
    const clusterTop = layout.items.length
      ? Math.min(...layout.items.map((i) => i.top))
      : layout.actions.top;
    bottom = clusterTop - rowGap;
    top = bottom - height;
  } else {
    const bottomInset = 1.1 * REM;
    bottom = viewportHeight - bottomInset;
    top = bottom - height;
  }
  return {
    left,
    right,
    top,
    bottom,
    width,
    height,
    joy,
    tr: hudTopRightRect(viewportWidth, viewportHeight, true),
    actions: hudActionsRect(viewportWidth, viewportHeight, { exploring: true })
  };
}

/**
 * @param {{ exploring?: boolean, withInteract?: boolean, withSteer?: boolean, interactWidth?: number }} [opts]
 */
export function hudActionsRect(viewportWidth, viewportHeight, opts) {
  const exploring = !!(opts && opts.exploring);
  const withInteract = !!(opts && opts.withInteract);
  const withSteer = !!(opts && opts.withSteer);
  const interactWidth = (opts && opts.interactWidth) || INTERACT_BTN_W;
  const margin = hudMargin(viewportWidth);
  const narrow = viewportWidth <= MOBILE_MAX_WIDTH;
  const smallOrb = HUD_CHIP;
  const primaryW = 3.75 * REM;
  const primaryH = 3.75 * REM;
  const steerW = STEER_BTN_W;
  const gap = 0.45 * REM;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const bottomInset = narrow ? 0.85 * REM : 1.1 * REM;
  const bottomEdge = viewportHeight - bottomInset;
  const top = bottomEdge - primaryH;
  const right = viewportWidth - margin;
  let rowW = smallOrb * 2 + gap + ROADMAP_BTN_W;
  if (!(exploring && narrow)) rowW += gap + primaryW;
  if (withInteract) rowW += gap + interactWidth;
  if (withSteer) rowW += gap + steerW;
  let left;
  if (narrow && exploring) {
    left = joy.right + 0.55 * REM;
  } else {
    const legacyRow = smallOrb * 2 + gap + ROADMAP_BTN_W + gap + primaryW;
    left = right - legacyRow - (withSteer ? steerW + gap : 0);
  }
  const width = Math.max(0, right - left);
  return {
    left,
    right,
    top,
    bottom: bottomEdge,
    width,
    height: primaryH,
    rowW,
    primary: { width: primaryW, height: primaryH, minTouch: Math.min(primaryW, primaryH) },
    smallOrb: { width: smallOrb, height: smallOrb + 0.75 * REM, minTouch: smallOrb },
    joy
  };
}

/** 探索中下排地圖／角色／情境／開船各鈕（DOM 順序、flex-end 換行；不含「退出探索」）。 */
export function exploreHudActionItems(viewportWidth, viewportHeight, opts) {
  const withInteract = !!(opts && opts.withInteract);
  const withSteer = !!(opts && opts.withSteer);
  const interactWidth = (opts && opts.interactWidth) || INTERACT_BTN_W;
  const actions = hudActionsRect(viewportWidth, viewportHeight, {
    exploring: true,
    withInteract,
    withSteer,
    interactWidth
  });
  const gap = 0.45 * REM;
  const smallOrb = HUD_CHIP;
  const primaryH = 3.75 * REM;
  const specById = {
    map: { id: 'map', w: smallOrb, h: smallOrb + 0.15 * REM },
    role: { id: 'role', w: smallOrb, h: smallOrb + 0.15 * REM },
    roadmap: { id: 'roadmap', w: ROADMAP_BTN_W, h: primaryH },
    interact: { id: 'interact', w: interactWidth, h: primaryH },
    steer: { id: 'steer', w: STEER_BTN_W, h: primaryH }
  };
  const sourceOrder = [
    specById.map,
    specById.role,
    specById.roadmap,
    ...(withInteract ? [specById.interact] : []),
    ...(withSteer ? [specById.steer] : [])
  ];
  const rows = [];
  let row = [];
  let rowW = 0;
  for (const spec of sourceOrder) {
    const need = row.length ? gap + spec.w : spec.w;
    if (row.length && rowW + need > actions.width + 0.5) {
      rows.push(row);
      row = [spec];
      rowW = spec.w;
    } else {
      rowW += need;
      row.push(spec);
    }
  }
  if (row.length) rows.push(row);
  const items = [];
  let rowBottom = actions.bottom;
  for (let r = rows.length - 1; r >= 0; r--) {
    const line = rows[r];
    const rowH = Math.max(...line.map((s) => s.h));
    let cursor = actions.right;
    for (let i = line.length - 1; i >= 0; i--) {
      const spec = line[i];
      cursor -= spec.w;
      items.push({
        id: spec.id,
        left: cursor,
        right: cursor + spec.w,
        top: rowBottom - spec.h,
        bottom: rowBottom,
        width: spec.w,
        height: spec.h
      });
      cursor -= gap;
    }
    rowBottom -= rowH + gap;
  }
  return { actions, items, joy: actions.joy, rows };
}

export function leaveInteriorRectMobile(viewportWidth, viewportHeight) {
  const margin = hudMargin(viewportWidth);
  const width = 5.5 * REM;
  const height = 2.75 * REM;
  const actions = hudActionsRect(viewportWidth, viewportHeight);
  const right = viewportWidth - margin;
  const left = right - width;
  const bottom = actions.top - 0.45 * REM;
  const top = bottom - height;
  return { left, right, top, bottom, width, height };
}

/** @deprecated 舊 dock 估算；改為右上 HUD 寬度檢查。 */
export function computeMobileDockBounds(viewportWidth, viewportHeight, exploring, peerLabel, showPeer) {
  void exploring;
  void peerLabel;
  void showPeer;
  return hudTopRightRect(viewportWidth, viewportHeight, false);
}

export function rectsIntersect(a, b) {
  return !(a.right <= b.left || a.left >= b.right || a.bottom <= b.top || a.top >= b.bottom);
}

export function dockFitsViewport(bounds, viewportWidth) {
  return bounds.left >= 0 && bounds.right <= viewportWidth + 0.5;
}

export function avatarPopRectMobile(viewportWidth, viewportHeight, exploring) {
  const popH = 7.15 * REM;
  const actions = hudActionsRect(viewportWidth, viewportHeight);
  const margin = hudMargin(viewportWidth);
  const left = exploring
    ? joystickRectMobile(viewportWidth, viewportHeight).right + 0.55 * REM
    : Math.max(margin, 0);
  const right = viewportWidth - margin;
  const bottom = actions.top - 0.4 * REM;
  const top = bottom - popH;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  return { left, right, top, bottom, width: right - left, height: popH, joy };
}

export function chatPanelRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem) {
  const hRem = panelHeightRem > 0 ? panelHeightRem : 12;
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const actions = hudActionsRect(viewportWidth, viewportHeight);
  const margin = hudMargin(viewportWidth);
  const bottomInset = Math.max(actions.bottom - actions.top + 0.5 * REM, joy.bottom - joy.top);
  const left = joy.right + 0.55 * REM;
  const right = viewportWidth - margin;
  const bottom = actions.top - 0.35 * REM;
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
  const roleBtn = document.getElementById('islandHudRoleBtn');
  const playerName = document.getElementById('islandHudPlayerName');
  const playerThumb = document.getElementById('islandHudPlayerThumb');
  const explorePrimaryLabel = document.querySelector('#islandExplore .island-hud-primary-label');
  const moreExploreRow = document.querySelector('.island-more-explore');
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

  const mapBtn = document.getElementById('islandMapBtn');
  const mapSheet = document.getElementById('islandMapSheet');
  const mapClose = document.getElementById('islandMapClose');
  const mapScrim = document.getElementById('islandMapScrim');

  if (nickInput) {
    const storedNick = loadNickFn(storage);
    if (storedNick) nickInput.value = storedNick;
  }

  let selectedAvatar = loadAvatar(storage);

  function avatarDisplayName(id) {
  return normalizeAvatar(id) === 'berry' ? '小莓' : 'milu';
  }

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
    const name = avatarDisplayName(current);
    if (avatarBtn) {
      avatarBtn.textContent = name;
      avatarBtn.setAttribute('aria-label', '探索角色：' + (current === 'berry' ? '小莓' : 'milu（預設）'));
    }
    if (playerName) playerName.textContent = name;
    if (playerThumb) {
      const thumb = avatarPop
        ? avatarPop.querySelector('[data-island-avatar="' + current + '"] .island-avatar-thumb')
        : null;
      if (thumb && thumb.getAttribute('src')) playerThumb.src = thumb.getAttribute('src');
    }
    if (roleBtn) {
      roleBtn.setAttribute('aria-label', '角色：' + name);
    }
  }

  function setAvatarOpen(open) {
    if (!avatarPop) return;
    const on = !!open;
    avatarPop.hidden = !on;
    if (avatarBtn) avatarBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    if (roleBtn) roleBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    document.body.classList.toggle('island-avatar-open', on);
    if (on) {
      syncAvatarUi();
      setNickOpen(false);
      setMapOpen(false);
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

  if (roleBtn) {
    roleBtn.addEventListener('click', () => setAvatarOpen(avatarPop && avatarPop.hidden));
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
    document.body.classList.toggle('island-nick-open', open);
    if (open) {
      setAvatarOpen(false);
      setMapOpen(false);
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

  function syncExploreHudLabels() {
    const exploring = isExploring();
    const exploreBtn = document.getElementById('islandExplore');
    if (exploreBtn) {
      exploreBtn.setAttribute('aria-pressed', exploring ? 'true' : 'false');
      exploreBtn.setAttribute('aria-label', exploring ? '退出探索模式' : '進入探索模式');
      const label = exploring ? '退出探索' : '探索';
      if (explorePrimaryLabel) explorePrimaryLabel.textContent = label;
      else exploreBtn.textContent = label;
    }
    if (moreExploreRow) {
      moreExploreRow.hidden = !exploring;
      moreExploreRow.textContent = '退出探索';
    }
  }

  function syncMoreAudioLabel() {
    const audioBtn = document.getElementById('islandAudio');
    if (!audioBtn) return;
    const iconEl = audioBtn.querySelector('.island-tool-audio-icon');
    let muted = false;
    if (typeof getAudioMuted === 'function') muted = !!getAudioMuted();
    else muted = audioBtn.getAttribute('aria-pressed') === 'false';
    if (iconEl) iconEl.innerHTML = muted ? AUDIO_OFF_SVG : AUDIO_ON_SVG;
  }

  function setMoreOpen(open) {
    if (!moreSheet || !moreBtn) return;
    const on = !!open;
    moreSheet.hidden = !on;
    moreBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    document.body.classList.toggle('island-more-open', on);
    if (on) {
      setAvatarOpen(false);
      setMapOpen(false);
      syncMoreAudioLabel();
      syncExploreHudLabels();
    }
  }

  function closeMore() {
    setMoreOpen(false);
  }

  function setMapOpen(open) {
    if (!mapSheet || !mapBtn) return;
    const on = !!open;
    mapSheet.hidden = !on;
    mapBtn.setAttribute('aria-expanded', on ? 'true' : 'false');
    document.body.classList.toggle('island-map-open', on);
    if (on) {
      setAvatarOpen(false);
      setNickOpen(false);
      closeMore();
    }
  }

  function closeMap() {
    setMapOpen(false);
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
  if (id === 'islandAvatarBtn' || id === 'islandHudRoleBtn') {
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

  if (mapBtn) mapBtn.addEventListener('click', () => setMapOpen(mapSheet && mapSheet.hidden));
  if (mapClose) mapClose.addEventListener('click', () => closeMap());
  if (mapScrim) mapScrim.addEventListener('click', () => closeMap());

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    if (mapSheet && !mapSheet.hidden) {
      event.preventDefault();
      closeMap();
      return;
    }
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
    setMapOpen: setMapOpen,
    closeMap: closeMap,
    syncMoreAudioLabel: syncMoreAudioLabel,
    setAvatarOpen: setAvatarOpen,
    syncAvatarUi: syncAvatarUi,
    syncExploreHudLabels: syncExploreHudLabels,
    currentAvatar: () => normalizeAvatar(selectedAvatar)
  };
}