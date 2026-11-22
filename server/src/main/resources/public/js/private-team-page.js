import { mountPrivateTeamScene } from './private-team-scene.js';
import {
  bubbleTextForStatus,
  fillStatusCard,
  secretarySpriteUrlForStatus,
  taskProgressFromTiger,
  tigerFromPublic
} from './private-team-office.js';

const DAY_KEY = 'punchclock-private-team-day';

function deployVersionLabel(win) {
  const ver = win && typeof win.PUNCHCLOCK_SERVER_VERSION === 'string'
    ? win.PUNCHCLOCK_SERVER_VERSION.trim()
    : '';
  const bt = win && typeof win.PUNCHCLOCK_SERVER_BUILD_TIME === 'string'
    ? win.PUNCHCLOCK_SERVER_BUILD_TIME.trim()
    : '';
  if (!ver || !bt || ver.includes('{{') || bt.includes('{{')) {
    return '';
  }
  return 'v' + ver + ' · ' + bt;
}

function mountDeployVersion(hud, win, doc) {
  if (!hud) return;
  let el = hud.querySelector('[data-pt-deploy-ver]');
  if (!el) {
    el = doc.createElement('p');
    el.className = 'pt-hud-deploy-ver';
    el.setAttribute('data-pt-deploy-ver', '');
    hud.appendChild(el);
  }
  const label = deployVersionLabel(win);
  if (!label) {
    el.textContent = '';
    el.hidden = true;
    el.setAttribute('aria-hidden', 'true');
    return;
  }
  el.textContent = label;
  el.hidden = false;
  el.removeAttribute('aria-hidden');
}

const SUN_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="5" fill="none" stroke="#163a70" stroke-width="2"/><g stroke="#163a70" stroke-width="2" stroke-linecap="round"><line x1="12" y1="2" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="22"/><line x1="2" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="22" y2="12"/><line x1="4.2" y1="4.2" x2="6.4" y2="6.4"/><line x1="17.6" y1="17.6" x2="19.8" y2="19.8"/><line x1="4.2" y1="19.8" x2="6.4" y2="17.6"/><line x1="17.6" y1="6.4" x2="19.8" y2="4.2"/></g></svg>';

const PERSON_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="7" r="3.5" fill="none" stroke="#163a70" stroke-width="2"/><path d="M5 20c0-4 3-6 7-6s7 2 7 6" fill="none" stroke="#163a70" stroke-width="2" stroke-linecap="round"/></svg>';

const DL_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/><path d="M8 12l4 4 4-4" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><rect x="5" y="18" width="14" height="2" fill="#fff"/></svg>';

const FRAME_CORNER_SVG =
  '<svg viewBox="0 0 22 22" fill="none" aria-hidden="true">' +
  '<path d="M3 19 V6 Q3 3 6 3 H19" stroke="#f3e3a6" stroke-width="2.4" stroke-linecap="square"/>' +
  '<path d="M6 19 V8 Q6 6 8 6 H19" stroke="#8a6a28" stroke-width="1.2"/>' +
  '<rect x="2" y="2" width="4.2" height="4.2" fill="#f3e3a6" stroke="#8a6a28" stroke-width="0.8"/></svg>';

const TASK_BADGE_SVG =
  '<svg viewBox="0 0 42 42" aria-hidden="true">' +
  '<polygon points="21,2 39,12 39,30 21,40 3,30 3,12" fill="#d4b35c" stroke="#8a6a28" stroke-width="1.6"/>' +
  '<polygon points="21,7 35,15 35,27 21,35 7,27 7,15" fill="#f3e3a6"/></svg>';

function frameCornerSpans() {
  return (
    '<span class="pt-a-corner pt-a-corner-tl" aria-hidden="true">' + FRAME_CORNER_SVG + '</span>' +
    '<span class="pt-a-corner pt-a-corner-tr" aria-hidden="true">' + FRAME_CORNER_SVG + '</span>' +
    '<span class="pt-a-corner pt-a-corner-bl" aria-hidden="true">' + FRAME_CORNER_SVG + '</span>' +
    '<span class="pt-a-corner pt-a-corner-br" aria-hidden="true">' + FRAME_CORNER_SVG + '</span>'
  );
}

function taskHudMarkup() {
  return (
    '<button type="button" class="pt-hud-task" data-pt-task-btn aria-label="任務">' +
    '<span class="pt-a-frame">' +
    '<span class="pt-a-frame-inner">' +
    '<img src="/assets/private-team/secretary-avatar.webp" alt="" aria-hidden="true" width="192" height="192" decoding="async">' +
    '</span>' +
    frameCornerSpans() +
    '<span class="pt-a-badge" aria-hidden="true">' + TASK_BADGE_SVG + '<span>任</span></span>' +
    '</span>' +
    '<span class="pt-a-plate">' +
    '<span class="pt-a-name">私人秘書</span>' +
    '<span class="pt-a-bar-row" data-pt-task-bar hidden>' +
    '<span class="pt-a-bar-label">任務</span>' +
    '<span class="pt-a-bar" aria-hidden="true"><i data-pt-task-bar-fill></i></span>' +
    '<span class="pt-a-bar-count" data-pt-task-bar-count></span>' +
    '</span></span></button>'
  );
}

function identityHudMarkup(authed) {
  const idBody =
    '<span class="pt-a-id-gem" aria-hidden="true"></span>' +
    '<span class="pt-a-id-text">' +
    '<span class="pt-a-id-role">' + (authed ? '管理員' : '訪客') + '</span>' +
    '<span class="pt-a-id-sub">' + (authed ? '線上' : '登入') + '</span>' +
    '</span>';
  if (authed) {
    return (
      '<div class="pt-hud-id-wrap">' +
      '<button type="button" class="pt-hud-id-plate" data-pt-identity-toggle aria-label="帳號" aria-expanded="false">' +
      idBody +
      '</button>' +
      '<div class="pt-hud-id-menu" data-pt-identity-menu hidden role="menu">' +
      '<a href="/console" role="menuitem">主控台</a>' +
      '<a href="/logout" role="menuitem">登出</a>' +
      '</div></div>'
    );
  }
  return (
    '<a class="pt-hud-id-plate is-guest" href="/login?next=%2F" aria-label="帳號">' +
    idBody +
    '</a>'
  );
}

function readDayPref(win) {
  try {
    const v = win.localStorage.getItem(DAY_KEY);
    if (v === 'night') return false;
    if (v === 'day') return true;
  } catch (_e) { /* ignore */ }
  return true;
}

function writeDayPref(win, day) {
  try {
    win.localStorage.setItem(DAY_KEY, day ? 'day' : 'night');
  } catch (_e) { /* ignore */ }
}

export function mountPrivateTeamPage(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const stage = doc.querySelector('[data-private-team-stage]');
  if (!stage) return { destroy() {} };

  const authed = !!(win.PUNCHCLOCK_AUTHENTICATED);
  const pageHooks = (opts && opts.hooks) || {};
  const openRoadmap =
    typeof pageHooks.openRoadmap === 'function' ? pageHooks.openRoadmap : null;

  let canvas = stage.querySelector('[data-private-team-canvas]');
  if (!canvas) {
    canvas = doc.createElement('canvas');
    canvas.className = 'private-team-canvas';
    canvas.setAttribute('data-private-team-canvas', '');
    canvas.setAttribute('tabindex', '0');
    stage.insertBefore(canvas, stage.firstChild);
  } else {
    canvas.removeAttribute('aria-hidden');
    canvas.setAttribute('tabindex', '0');
  }

  let hud = stage.querySelector('.private-team-hud');
  if (!hud) {
    hud = doc.createElement('div');
    hud.className = 'private-team-hud';
    hud.setAttribute('aria-label', '操作介面');
    hud.innerHTML =
      '<div class="pt-hud-tl">' +
      taskHudMarkup() +
      '<div class="pt-hud-task-hint" data-pt-task-hint hidden role="dialog" aria-label="登入提示">' +
      '<p class="pt-hud-task-hint-text">先登入才能看任務</p>' +
      '<a class="pt-hud-task-hint-login" href="/login?next=%2Fconsole">登入</a>' +
      '</div></div>' +
      '<div class="pt-hud-tr">' +
      '<div class="pt-hud-iconcol"><button type="button" class="pt-hud-sqbtn is-day" data-pt-daynight aria-label="日夜切換">' +
      SUN_SVG +
      '</button><span class="pt-hud-sqbtn-label">日夜</span></div>' +
      identityHudMarkup(authed) +
      '</div>' +
      '<div class="pt-hud-br">' +
      '<a class="pt-hud-atlas" href="/atlas/tcm-3d/">' + PERSON_SVG + '<span>經絡研習室</span></a>' +
      '<a class="pt-hud-download" href="/download" aria-label="下載 App">' + DL_SVG +
      '<span class="pt-hud-dl-label">下載 App</span></a></div>' +
      '<p class="pt-hud-deploy-ver" data-pt-deploy-ver hidden aria-hidden="true"></p>';
    stage.appendChild(hud);
  }
  mountDeployVersion(hud, win, doc);

  let progHit = stage.querySelector('[data-pt-prog-hit]');
  if (!progHit) {
    progHit = doc.createElement('button');
    progHit.type = 'button';
    progHit.className = 'pt-prog-hit';
    progHit.setAttribute('data-pt-prog-hit', '');
    progHit.setAttribute('aria-label', '進度看板，開啟施工進度');
    progHit.textContent = '進度';
    stage.appendChild(progHit);
  }

  let cardBackdrop = stage.querySelector('[data-pt-status-card]');
  let cardBody = null;
  let closeBtn = null;
  if (!cardBackdrop) {
    cardBackdrop = doc.createElement('div');
    cardBackdrop.className = 'pt-status-backdrop';
    cardBackdrop.setAttribute('data-pt-status-card', '');
    cardBackdrop.hidden = true;
    cardBackdrop.innerHTML =
      '<div class="pt-status-card maple-window" role="dialog" aria-modal="true" aria-labelledby="pt-status-title" tabindex="-1">' +
      '<button type="button" class="pt-status-close" data-pt-status-close aria-label="關閉工作狀態">×</button>' +
      '<h2 id="pt-status-title" class="pt-status-title">猛虎・工作狀態</h2>' +
      '<div class="pt-status-body" data-pt-status-body></div>' +
      '</div>';
    stage.appendChild(cardBackdrop);
  }
  const cardDialog = cardBackdrop.querySelector('.pt-status-card');
  cardBody = cardBackdrop.querySelector('[data-pt-status-body]');
  closeBtn = cardBackdrop.querySelector('[data-pt-status-close]');

  const dayBtn = hud.querySelector('[data-pt-daynight]');
  const taskBtn = hud.querySelector('[data-pt-task-btn]');
  const taskHint = hud.querySelector('[data-pt-task-hint]');
  const identityBtn = hud.querySelector('[data-pt-identity-toggle]');
  const identityMenu = hud.querySelector('[data-pt-identity-menu]');
  const taskBarRow = hud.querySelector('[data-pt-task-bar]');
  const taskBarFill = hud.querySelector('[data-pt-task-bar-fill]');
  const taskBarCount = hud.querySelector('[data-pt-task-bar-count]');

  let tigerView = { status: '待命中', active: [], completedToday: [] };
  let cardOpen = false;
  let taskHintOpen = false;
  let identityMenuOpen = false;
  let lastFocus = null;

  function bubbleLabel() {
    return bubbleTextForStatus(tigerView.status);
  }

  function refreshCanvasA11y() {
    const label = bubbleLabel();
    canvas.setAttribute(
      'aria-label',
      '私人秘書場景，目前' + label + '。點選秘書或泡泡，或按 Enter 查看工作狀態。'
    );
  }

  function closeStatusCard() {
    if (!cardOpen) return;
    cardOpen = false;
    cardBackdrop.hidden = true;
    if (lastFocus && typeof lastFocus.focus === 'function') {
      try {
        lastFocus.focus({ preventScroll: true });
      } catch (_e) { /* ignore */ }
    }
    lastFocus = null;
  }

  function openStatusCard() {
    if (cardOpen) return;
    lastFocus = doc.activeElement;
    fillStatusCard(doc, cardBody, tigerView);
    cardOpen = true;
    cardBackdrop.hidden = false;
    if (closeBtn) closeBtn.focus({ preventScroll: true });
  }

  function openRoadmapFromBoard() {
    if (!openRoadmap) return;
    closeStatusCard();
    closeIdentityMenu();
    closeTaskHint();
    openRoadmap();
  }

  let roadmapOpen = false;

  function placeProgHit(rect) {
    if (!progHit || !rect) return;
    const left = Number(rect.left);
    const top = Number(rect.top);
    const width = Number(rect.width);
    const height = Number(rect.height);
    if (![left, top, width, height].every(Number.isFinite) || width < 1 || height < 1) return;
    progHit.style.left = left.toFixed(1) + 'px';
    progHit.style.top = top.toFixed(1) + 'px';
    progHit.style.width = width.toFixed(1) + 'px';
    progHit.style.height = height.toFixed(1) + 'px';
  }

  function setRoadmapOpen(open) {
    roadmapOpen = !!open;
    stage.classList.toggle('pt-roadmap-open', roadmapOpen);
    scene.setSceneInputEnabled(!roadmapOpen);
    if (roadmapOpen) {
      closeStatusCard();
      closeIdentityMenu();
      closeTaskHint();
    }
  }

  const scene = mountPrivateTeamScene(canvas, {
    win,
    doc,
    onActivate: openStatusCard,
    onOpenProgress: openRoadmapFromBoard,
    onProgressBoardRect: placeProgHit
  });

  let dayMode = readDayPref(win);
  scene.setDayMode(dayMode);
  scene.setBubbleText(bubbleLabel());
  refreshCanvasA11y();
  if (dayBtn) {
    dayBtn.classList.toggle('is-day', dayMode);
    dayBtn.classList.toggle('is-night', !dayMode);
  }

  function closeTaskHint() {
    if (!taskHint || !taskHintOpen) return;
    taskHintOpen = false;
    taskHint.hidden = true;
  }

  function openTaskHint() {
    if (!taskHint || taskHintOpen) return;
    closeIdentityMenu();
    taskHintOpen = true;
    taskHint.hidden = false;
  }

  function closeIdentityMenu() {
    if (!identityMenu || !identityBtn) return;
    if (!identityMenuOpen) return;
    identityMenuOpen = false;
    identityMenu.hidden = true;
    identityBtn.setAttribute('aria-expanded', 'false');
  }

  function toggleIdentityMenu() {
    if (!identityMenu || !identityBtn) return;
    closeTaskHint();
    const open = identityMenu.hidden;
    identityMenu.hidden = !open;
    identityMenuOpen = open;
    identityBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
  }

  if (dayBtn) {
    dayBtn.addEventListener('click', () => {
      dayMode = !dayMode;
      scene.setDayMode(dayMode);
      dayBtn.classList.toggle('is-day', dayMode);
      dayBtn.classList.toggle('is-night', !dayMode);
      writeDayPref(win, dayMode);
    });
  }

  if (taskBtn) {
    taskBtn.addEventListener('click', (ev) => {
      ev.stopPropagation();
      if (authed) {
        closeTaskHint();
        win.location.href = '/console';
        return;
      }
      if (taskHintOpen) {
        closeTaskHint();
      } else {
        openTaskHint();
      }
    });
  }

  if (identityBtn) {
    identityBtn.addEventListener('click', (ev) => {
      ev.stopPropagation();
      toggleIdentityMenu();
    });
  }

  if (progHit) {
    progHit.addEventListener('click', (ev) => {
      ev.stopPropagation();
      openRoadmapFromBoard();
    });
  }

  doc.addEventListener('click', (ev) => {
    if (identityMenu && identityMenuOpen) {
      if (!identityMenu.contains(ev.target) && !identityBtn.contains(ev.target)) {
        closeIdentityMenu();
      }
    }
    if (taskHint && taskHintOpen) {
      if (!taskHint.contains(ev.target) && !(taskBtn && taskBtn.contains(ev.target))) {
        closeTaskHint();
      }
    }
  });

  if (closeBtn) {
    closeBtn.addEventListener('click', () => closeStatusCard());
  }

  cardBackdrop.addEventListener('click', (ev) => {
    if (ev.target === cardBackdrop) closeStatusCard();
  });

  if (cardDialog) {
    cardDialog.addEventListener('click', (ev) => ev.stopPropagation());
  }

  function onKeyDown(ev) {
    if (ev.key === 'Escape') {
      if (cardOpen) {
        ev.preventDefault();
        closeStatusCard();
        return;
      }
      if (identityMenuOpen) {
        ev.preventDefault();
        closeIdentityMenu();
        return;
      }
      if (taskHintOpen) {
        ev.preventDefault();
        closeTaskHint();
        return;
      }
    }
    if (
      !roadmapOpen &&
      !cardOpen &&
      (ev.key === 'Enter' || ev.key === ' ') &&
      ev.target === canvas
    ) {
      ev.preventDefault();
      openStatusCard();
    }
  }
  doc.addEventListener('keydown', onKeyDown);

  function applyTaskBar(tiger) {
    if (!taskBarRow) return;
    const prog = taskProgressFromTiger(tiger);
    if (!prog) {
      taskBarRow.hidden = true;
      if (taskBarFill) taskBarFill.style.width = '0%';
      if (taskBarCount) taskBarCount.textContent = '';
      return;
    }
    taskBarRow.hidden = false;
    if (taskBarFill) {
      taskBarFill.style.width = Math.round(prog.fraction * 100) + '%';
    }
    if (taskBarCount) {
      taskBarCount.textContent = prog.completed + '/' + prog.total;
    }
  }

  function applyTiger(tiger) {
    if (!tiger) return;
    tigerView = tiger;
    scene.setSecretarySprite(secretarySpriteUrlForStatus(tigerView.status));
    scene.setBubbleText(bubbleLabel());
    refreshCanvasA11y();
    applyTaskBar(tiger);
    if (cardOpen) fillStatusCard(doc, cardBody, tigerView);
  }

  function refreshOfficePublic() {
    if (!fetchFn) return;
    fetchFn('/api/office/public')
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null)
      .then((body) => {
        const tiger = tigerFromPublic(body);
        applyTiger(tiger || { status: '待命中', active: [], completedToday: [] });
      });
  }

  refreshOfficePublic();
  const poll = win.setInterval(refreshOfficePublic, 60_000);

  return {
    setRoadmapOpen,
    destroy() {
      win.clearInterval(poll);
      doc.removeEventListener('keydown', onKeyDown);
      closeIdentityMenu();
      closeTaskHint();
      closeStatusCard();
      setRoadmapOpen(false);
      scene.destroy();
    }
  };
}