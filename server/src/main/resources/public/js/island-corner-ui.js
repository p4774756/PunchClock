import { cleanNick, loadAvatar, loadNick, normalizeAvatar, saveAvatar, NICK_KEY } from './island-presence.js';
import { loadSelfNameEnabled, saveSelfNameEnabled } from './island-self-label.js';

export const MOBILE_MAX_WIDTH = 760;
const REM = 16;
export const ORB_SIZE = 2.85 * REM;
export const ORB_GAP = 0.4 * REM;
export const ORB_TR_GAP_MOBILE = 0.28 * REM;
export const HUD_PLAYER_TR_GAP = 0.375 * REM;
const HUD_MARGIN = 0.55 * REM;
const HUD_PLAYER_MAX_W_MOBILE = 10.5 * REM;

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
  void viewportHeight;
  const margin = hudMargin(viewportWidth);
  const narrow = viewportWidth <= MOBILE_MAX_WIDTH;
  const trGap = narrow ? ORB_TR_GAP_MOBILE : ORB_GAP;
  const orbs = orbRowCount(showAudio);
  const totalW = orbs * ORB_SIZE + Math.max(0, orbs - 1) * trGap;
  const right = viewportWidth - margin;
  const left = right - totalW;
  const top = (narrow ? 0.55 : 3.6) * REM;
  const bottom = top + ORB_SIZE + 1.05 * REM;
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

/** 探索中桌面清單：錨在右上 HUD 列下方（與選單面板對齊）。 */
export function listPanelRectDesktop(viewportWidth, viewportHeight, showAudio) {
  const tr = hudTopRightRect(viewportWidth, viewportHeight, showAudio);
  const margin = hudMargin(viewportWidth);
  const gap = 8;
  const width = Math.min(280, viewportWidth - 2 * REM);
  const right = viewportWidth - margin;
  const left = right - width;
  const top = tr.bottom + gap;
  const maxBody = Math.min(28 * REM, viewportHeight - top - REM);
  return {
    left,
    right,
    top,
    bottom: top + maxBody,
    width,
    height: maxBody,
    closeTop: top + 4,
    closeRight: right - 4,
    tr
  };
}

const EXPLORE_FLOAT_BOTTOM_REM =
  0.85 + 3.75 + 0.45 + 3.75 + 0.45 + 3.75 + 0.35;

export function exploreFloatBottomPx(viewportHeight) {
  return viewportHeight - EXPLORE_FLOAT_BOTTOM_REM * REM;
}

/** 手機：右上圓鈕列寬度（對齊 island.css @760px flex gap）。 */
export function hudTopRightWidthMobile(showAudio) {
  const orbs = orbRowCount(showAudio);
  return orbs * ORB_SIZE + Math.max(0, orbs - 1) * ORB_TR_GAP_MOBILE;
}

/** 手機探索：左上玩家 HUD（含卡、間距、人數晶片；對齊 island.css @760px）。 */
export function hudPlayerRectMobile(viewportWidth, viewportHeight, showAudio) {
  void viewportHeight;
  const margin = hudMargin(viewportWidth);
  const audio = showAudio !== false;
  const trWidth = hudTopRightWidthMobile(audio);
  const top = Math.max(margin, 0);
  const left = margin;
  const width = Math.min(
    HUD_PLAYER_MAX_W_MOBILE,
    viewportWidth - trWidth - 2 * margin - HUD_PLAYER_TR_GAP
  );
  const cardH = 5.35 * REM;
  const gap = 0.35 * REM;
  const badgeH = 1.35 * REM;
  const boxH = cardH + gap + badgeH;
  return { left, right: left + width, top, bottom: top + boxH, width, height: boxH, cardH };
}

/** 桌面探索：左上玩家 HUD（對齊 island.css）。 */
export function hudPlayerRectDesktop(viewportWidth, viewportHeight) {
  void viewportHeight;
  const top = Math.max(0.85 * REM, 0);
  const left = 0.85 * REM;
  const width = Math.min(14 * REM, viewportWidth - 11 * REM);
  const boxH = 5.85 * REM;
  return { left, right: left + width, top, bottom: top + boxH, width, height: boxH };
}

/** 桌面探索：底部置中操作提示（對齊 .island-explore-hint）。 */
export function exploreHintRectDesktop(viewportWidth, viewportHeight, approxHeightRem) {
  const hRem = approxHeightRem > 0 ? approxHeightRem : 2.5;
  const height = hRem * REM;
  const bottomInset = 1.15 * REM;
  const bottom = viewportHeight - bottomInset;
  const top = bottom - height;
  const width = Math.min(32 * REM, viewportWidth - 2 * REM);
  const left = (viewportWidth - width) * 0.5;
  return {
    left,
    right: left + width,
    top,
    bottom,
    width,
    height,
    tr: hudTopRightRect(viewportWidth, viewportHeight, true),
    player: hudPlayerRectDesktop(viewportWidth, viewportHeight),
    actions: hudActionsRect(viewportWidth, viewportHeight, { exploring: true }),
    joy: joystickRectMobile(viewportWidth, viewportHeight)
  };
}

/** 手機探索：提示與常見 HUD 邊界（供 node:test 驗證不重疊）。 */
export function exploreHintHudTargetsMobile(viewportWidth, viewportHeight, approxHeightRem) {
  const hint = exploreHintRectMobile(viewportWidth, viewportHeight, approxHeightRem);
  const player = hudPlayerRectMobile(viewportWidth, viewportHeight);
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const actions = hudActionsRect(viewportWidth, viewportHeight, {
    exploring: true,
    withInteract: true,
    withSteer: true
  });
  const layout = exploreHudActionItems(viewportWidth, viewportHeight, {
    withInteract: true,
    withSteer: true
  });
  const interact = layout.items.find((item) => item.id === 'islandInteract') || actions;
  return { hint, player, joy, actions, interact, tr: hint.tr };
}

/** 手機探索：清單面板（對齊 island.css @760px）。 */
export function listPanelRectMobileExplore(viewportWidth, viewportHeight) {
  const player = hudPlayerRectMobile(viewportWidth, viewportHeight);
  const margin = hudMargin(viewportWidth);
  const top = player.bottom + 0.5 * REM;
  const width = Math.min(280, viewportWidth - 1.1 * REM);
  const right = viewportWidth - margin;
  const left = right - width;
  const titleBarH = 2 * REM;
  const maxBody = Math.min(
    0.52 * viewportHeight,
    viewportHeight - top - Math.max(5.5 * REM, 3.75 * REM + 0.85 * REM)
  );
  return {
    left,
    right,
    top,
    bottom: top + titleBarH,
    width,
    height: titleBarH,
    titleBar: { left, right, top, bottom: top + titleBarH, width, height: titleBarH },
    closeTop: top + 4,
    closeRight: right - 4,
    maxBody,
    player
  };
}

export function nickPopRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem) {
  return popRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem > 0 ? panelHeightRem : 9);
}

export function chatPanelRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem) {
  return popRectMobileExplore(viewportWidth, viewportHeight, panelHeightRem > 0 ? panelHeightRem : 12);
}

function popRectMobileExplore(viewportWidth, viewportHeight, hRem) {
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  const exit = exploreExitButtonRect(viewportWidth, viewportHeight);
  const margin = hudMargin(viewportWidth);
  const gap = 0.35 * REM;
  const left = joy.right + 0.55 * REM;
  const right = viewportWidth - margin;
  const bottom = exploreFloatBottomPx(viewportHeight);
  const maxH = Math.max(6 * REM, bottom - gap - joy.top - gap);
  const height = Math.min(hRem * REM, maxH);
  const top = bottom - height;
  return { left, right, top, bottom, width: right - left, height, joy, exit };
}

/** 桌面探索：暱稱窗（對齊 body.is-exploring.island-nick-open .island-nick）。 */
export function nickPopRectDesktopExplore(viewportWidth, viewportHeight) {
  const margin = 0.85 * REM;
  const width = Math.min(18 * REM, viewportWidth - 1.6 * REM);
  const right = viewportWidth - margin;
  const left = right - width;
  const height = 7.5 * REM;
  const bottomInset = 1.1 * REM + 3.75 * REM + 0.65 * REM;
  const panelBottom = viewportHeight - bottomInset;
  const top = panelBottom - height;
  const actions = hudActionsRect(viewportWidth, viewportHeight, { exploring: true });
  return { left, right, top, bottom: panelBottom, width, height, actions };
}

/** 手機探索：操作提示（對齊 .island-explore-hint @760px）。 */
export function exploreHintRectMobile(viewportWidth, viewportHeight, approxHeightRem) {
  const hRem = approxHeightRem > 0 ? approxHeightRem : 2.5;
  const margin = hudMargin(viewportWidth);
  const top = 0.55 * REM + 2.85 * REM + 1.05 * REM + 0.35 * REM;
  const right = viewportWidth - margin;
  const width = Math.min(11 * REM, viewportWidth - 8 * REM);
  const left = right - width;
  const height = hRem * REM;
  const tr = hudTopRightRect(viewportWidth, viewportHeight, true);
  const player = hudPlayerRectMobile(viewportWidth, viewportHeight);
  const joy = joystickRectMobile(viewportWidth, viewportHeight);
  return { left, right, top, bottom: top + height, width, height, tr, player, joy };
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
  const onNickSaved = opts.onNickSaved;
  const onSelfNameToggle = opts.onSelfNameToggle;
  const closeList = typeof opts.closeList === 'function' ? opts.closeList : null;

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
  const selfNameToggle = document.getElementById('islandSelfNameToggle');
  const selfNameToggleState = selfNameToggle
    ? selfNameToggle.querySelector('.island-more-self-name-state')
    : null;
  const moreClose = document.getElementById('islandMoreClose');
  const moreScrim = document.getElementById('islandMoreScrim');
  const nickClose = document.getElementById('islandNickClose');
  const avatarClose = document.getElementById('islandAvatarClose');

  let morePositionListener = null;

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
      if (closeList) closeList();
      else {
        const listEl = document.getElementById('islandList');
        const listToggle = document.getElementById('islandListBtn');
        if (listEl) listEl.hidden = true;
        if (listToggle) listToggle.setAttribute('aria-expanded', 'false');
      }
      if (nickInput) nickInput.focus();
    }
  }

  let selfNameEnabled = loadSelfNameEnabled(storage);

  function syncSelfNameToggleUi() {
    if (!selfNameToggle) return;
    selfNameToggle.setAttribute('aria-checked', selfNameEnabled ? 'true' : 'false');
    if (selfNameToggleState) {
      selfNameToggleState.textContent = selfNameEnabled ? '開' : '關';
    }
  }

  function setSelfNameEnabled(on) {
    selfNameEnabled = !!on;
    saveSelfNameEnabled(storage, selfNameEnabled);
    syncSelfNameToggleUi();
    if (typeof onSelfNameToggle === 'function') onSelfNameToggle(selfNameEnabled);
  }

  syncSelfNameToggleUi();

  if (selfNameToggle) {
    selfNameToggle.addEventListener('click', () => {
      setSelfNameEnabled(!selfNameEnabled);
    });
  }

  function saveNick() {
    const islandPresence = getPresence();
    if (!nickInput) return;
    const cleaned = cleanNickFn(nickInput.value);
    if (!cleaned) {
      if (storage && typeof storage.removeItem === 'function') {
        try {
          storage.removeItem(NICK_KEY);
        } catch (err) {
          // 略過
        }
      }
      nickInput.value = '';
      if (islandPresence && typeof islandPresence.rename === 'function') {
        islandPresence.rename('');
      }
      if (typeof onNickSaved === 'function') onNickSaved('');
      setNickOpen(false);
      return;
    }
    if (!islandPresence) return;
    nickInput.value = cleaned;
    islandPresence.rename(cleaned);
    if (typeof onNickSaved === 'function') onNickSaved(cleaned);
    setNickOpen(false);
  }

  if (nickBtn && nickPop) {
    nickBtn.addEventListener('click', () => setNickOpen(nickPop.hidden));
  }
  if (nickSave) nickSave.addEventListener('click', saveNick);
  if (nickClose) nickClose.addEventListener('click', () => setNickOpen(false));
  if (avatarClose) avatarClose.addEventListener('click', () => setAvatarOpen(false));
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

  function positionMorePanel() {
    const panel = moreSheet ? moreSheet.querySelector('.island-more-panel') : null;
    if (!panel || !moreBtn || (moreSheet && moreSheet.hidden)) return;
    if (!panel.style || typeof panel.style.setProperty !== 'function') return;
    if (typeof moreBtn.getBoundingClientRect !== 'function') return;
    const mm = typeof globalThis.matchMedia === 'function'
      ? globalThis.matchMedia('(min-width: 761px)')
      : null;
    if (!mm || !mm.matches) {
      panel.style.removeProperty('--island-more-top');
      panel.style.removeProperty('--island-more-right');
      return;
    }
    const rect = moreBtn.getBoundingClientRect();
    const vw = globalThis.innerWidth || 1280;
    panel.style.setProperty('--island-more-top', `${rect.bottom + 8}px`);
    panel.style.setProperty('--island-more-right', `${Math.max(0, vw - rect.right)}px`);
  }

  function bindMorePosition(on) {
    const g = globalThis;
    if (!g || typeof g.addEventListener !== 'function') return;
    if (on && !morePositionListener) {
      morePositionListener = () => positionMorePanel();
      g.addEventListener('resize', morePositionListener);
      g.addEventListener('scroll', morePositionListener, true);
    } else if (!on && morePositionListener) {
      g.removeEventListener('resize', morePositionListener);
      g.removeEventListener('scroll', morePositionListener, true);
      morePositionListener = null;
    }
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
      positionMorePanel();
      bindMorePosition(true);
    } else {
      bindMorePosition(false);
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

  function stopEscape(event) {
    event.preventDefault();
    event.stopPropagation();
  }

  document.addEventListener('keydown', (event) => {
    if (event.key !== 'Escape') return;
    if (mapSheet && !mapSheet.hidden) {
      stopEscape(event);
      closeMap();
      return;
    }
    if (moreSheet && !moreSheet.hidden) {
      stopEscape(event);
      closeMore();
      return;
    }
    const listEl = document.getElementById('islandList');
    if (listEl && !listEl.hidden) {
      stopEscape(event);
      if (closeList) closeList();
      else {
        listEl.hidden = true;
        const listToggle = document.getElementById('islandListBtn');
        if (listToggle) listToggle.setAttribute('aria-expanded', 'false');
      }
      return;
    }
    if (nickPop && !nickPop.hidden) {
      stopEscape(event);
      setNickOpen(false);
      return;
    }
    if (avatarPop && !avatarPop.hidden) {
      stopEscape(event);
      setAvatarOpen(false);
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
    currentAvatar: () => normalizeAvatar(selectedAvatar),
    isSelfNameEnabled: () => selfNameEnabled,
    setSelfNameEnabled: setSelfNameEnabled
  };
}