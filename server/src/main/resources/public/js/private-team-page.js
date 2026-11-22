import { mountPrivateTeamScene } from './private-team-scene.js';
import {
  bubbleTextForStatus,
  fillStatusCard,
  tigerFromPublic
} from './private-team-office.js';

const DAY_KEY = 'punchclock-private-team-day';
const AUTH_ABBREV = '管';

const SUN_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="5" fill="none" stroke="#163a70" stroke-width="2"/><g stroke="#163a70" stroke-width="2" stroke-linecap="round"><line x1="12" y1="2" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="22"/><line x1="2" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="22" y2="12"/><line x1="4.2" y1="4.2" x2="6.4" y2="6.4"/><line x1="17.6" y1="17.6" x2="19.8" y2="19.8"/><line x1="4.2" y1="19.8" x2="6.4" y2="17.6"/><line x1="17.6" y1="6.4" x2="19.8" y2="4.2"/></g></svg>';

const PERSON_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="7" r="3.5" fill="none" stroke="#163a70" stroke-width="2"/><path d="M5 20c0-4 3-6 7-6s7 2 7 6" fill="none" stroke="#163a70" stroke-width="2" stroke-linecap="round"/></svg>';

const PERSON_SVG_GUEST =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="7" r="3.5" fill="none" stroke="#7a8a9c" stroke-width="2"/><path d="M5 20c0-4 3-6 7-6s7 2 7 6" fill="none" stroke="#7a8a9c" stroke-width="2" stroke-linecap="round"/></svg>';

const DL_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/><path d="M8 12l4 4 4-4" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><rect x="5" y="18" width="14" height="2" fill="#fff"/></svg>';

function identityHudMarkup(authed) {
  if (authed) {
    return (
      '<div class="pt-hud-id-wrap">' +
      '<button type="button" class="pt-hud-identity pt-hud-identity-auth" data-pt-identity-toggle aria-label="帳號選單" aria-expanded="false">' +
      '<span class="pt-hud-id-abbrev">' + AUTH_ABBREV + '</span>' +
      '<span class="pt-hud-id-dot" aria-hidden="true"></span>' +
      '</button>' +
      '<div class="pt-hud-id-menu" data-pt-identity-menu hidden role="menu">' +
      '<a href="/console" role="menuitem">主控台</a>' +
      '<a href="/logout" role="menuitem">登出</a>' +
      '</div></div>'
    );
  }
  return (
    '<a class="pt-hud-identity pt-hud-identity-guest" href="/login?next=%2F" aria-label="訪客登入">' +
    PERSON_SVG_GUEST +
    '<span>訪客</span></a>'
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
      '<div class="pt-hud-iconcol">' +
      '<button type="button" class="pt-hud-sqbtn" data-pt-task-btn aria-label="任務">' +
      PERSON_SVG +
      '</button></div>' +
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
      '<span class="pt-hud-dl-label">下載 App</span></a></div>';
    stage.appendChild(hud);
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

  const scene = mountPrivateTeamScene(canvas, {
    win,
    doc,
    onActivate: openStatusCard
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
    if (!cardOpen && (ev.key === 'Enter' || ev.key === ' ') && ev.target === canvas) {
      ev.preventDefault();
      openStatusCard();
    }
  }
  doc.addEventListener('keydown', onKeyDown);

  function applyTiger(tiger) {
    if (!tiger) return;
    tigerView = tiger;
    scene.setBubbleText(bubbleLabel());
    refreshCanvasA11y();
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
    destroy() {
      win.clearInterval(poll);
      doc.removeEventListener('keydown', onKeyDown);
      closeIdentityMenu();
      closeTaskHint();
      closeStatusCard();
      scene.destroy();
    }
  };
}