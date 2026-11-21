import { filterMergedToday } from './dev-machines-format.js';
import { mountPrivateTeamScene } from './private-team-scene.js';

export const SECRETARY_AVATAR_URL = '/assets/private-team/secretary-avatar.webp';

const DAY_KEY = 'punchclock-private-team-day';

const SUN_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="12" r="5" fill="none" stroke="#163a70" stroke-width="2"/><g stroke="#163a70" stroke-width="2" stroke-linecap="round"><line x1="12" y1="2" x2="12" y2="5"/><line x1="12" y1="19" x2="12" y2="22"/><line x1="2" y1="12" x2="5" y2="12"/><line x1="19" y1="12" x2="22" y2="12"/><line x1="4.2" y1="4.2" x2="6.4" y2="6.4"/><line x1="17.6" y1="17.6" x2="19.8" y2="19.8"/><line x1="4.2" y1="19.8" x2="6.4" y2="17.6"/><line x1="17.6" y1="6.4" x2="19.8" y2="4.2"/></g></svg>';

const MENU_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><line x1="4" y1="7" x2="20" y2="7" stroke="#163a70" stroke-width="2.5" stroke-linecap="round"/><line x1="4" y1="12" x2="20" y2="12" stroke="#163a70" stroke-width="2.5" stroke-linecap="round"/><line x1="4" y1="17" x2="20" y2="17" stroke="#163a70" stroke-width="2.5" stroke-linecap="round"/></svg>';

const PERSON_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><circle cx="12" cy="7" r="3.5" fill="none" stroke="#163a70" stroke-width="2"/><path d="M5 20c0-4 3-6 7-6s7 2 7 6" fill="none" stroke="#163a70" stroke-width="2" stroke-linecap="round"/></svg>';

const DL_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/><path d="M8 12l4 4 4-4" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><rect x="5" y="18" width="14" height="2" fill="#fff"/></svg>';

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

function setMergedLabel(el, n) {
  if (!el) return;
  const val = Number.isFinite(n) && n >= 0 ? String(n) : '—';
  el.textContent = '工作中・今天完成 ' + val + ' 件';
}

export function mountPrivateTeamPage(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const hooks = (opts && opts.hooks) || {};
  const stage = doc.querySelector('[data-private-team-stage]');
  if (!stage) return { destroy() {} };

  let canvas = stage.querySelector('[data-private-team-canvas]');
  if (!canvas) {
    canvas = doc.createElement('canvas');
    canvas.className = 'private-team-canvas';
    canvas.setAttribute('data-private-team-canvas', '');
    canvas.setAttribute('aria-hidden', 'true');
    stage.insertBefore(canvas, stage.firstChild);
  }

  let hud = stage.querySelector('.private-team-hud');
  if (!hud) {
    hud = doc.createElement('div');
    hud.className = 'private-team-hud';
    hud.setAttribute('aria-label', '操作介面');
    const authed = !!(win.PUNCHCLOCK_AUTHENTICATED);
    hud.innerHTML =
      '<div class="pt-hud-card maple-window">' +
      '<img class="pt-hud-avatar" src="' + SECRETARY_AVATAR_URL + '" alt="" aria-hidden="true" decoding="async">' +
      '<div class="pt-hud-card-body">' +
      '<p class="pt-hud-name">私人秘書</p>' +
      '<p class="pt-hud-line" data-pt-merged>工作中・今天完成 — 件</p>' +
      '<span class="pt-hud-badge">線上</span>' +
      '</div></div>' +
      '<div class="pt-hud-tr">' +
      '<div class="pt-hud-iconcol"><button type="button" class="pt-hud-sqbtn is-day" data-pt-daynight aria-label="日夜切換">' +
      SUN_SVG +
      '</button><span class="pt-hud-sqbtn-label">日夜</span></div>' +
      '<div class="pt-hud-iconcol pt-hud-menu-wrap">' +
      '<button type="button" class="pt-hud-sqbtn" data-pt-menu-toggle aria-label="選單" aria-expanded="false">' +
      MENU_SVG +
      '</button><span class="pt-hud-sqbtn-label">選單</span>' +
      '<div class="pt-hud-menu" data-pt-menu hidden role="menu">' +
      '<button type="button" role="menuitem" data-pt-open-roadmap>施工進度</button>' +
      '<a href="/dev-status" role="menuitem">開發機狀態</a>' +
      (authed
        ? '<a href="/console" role="menuitem">主控台</a><a href="/logout" role="menuitem">登出</a>'
        : '<a href="/login?next=%2F" role="menuitem">登入</a>') +
      '</div></div></div>' +
      '<div class="pt-hud-br">' +
      '<a class="pt-hud-atlas" href="/atlas/tcm-3d/">' + PERSON_SVG + '<span>經絡研習室</span></a>' +
      '<a class="pt-hud-download" href="/download" aria-label="下載 App">' + DL_SVG +
      '<span class="pt-hud-dl-label">下載 App</span></a></div>';
    stage.appendChild(hud);
  }

  const mergedEl = hud.querySelector('[data-pt-merged]');
  const dayBtn = hud.querySelector('[data-pt-daynight]');
  const menuBtn = hud.querySelector('[data-pt-menu-toggle]');
  const menuPanel = hud.querySelector('[data-pt-menu]');
  const roadmapBtn = hud.querySelector('[data-pt-open-roadmap]');

  const scene = mountPrivateTeamScene(canvas, { win, doc });
  let dayMode = readDayPref(win);
  scene.setDayMode(dayMode);
  if (dayBtn) {
    dayBtn.classList.toggle('is-day', dayMode);
    dayBtn.classList.toggle('is-night', !dayMode);
  }

  function closeMenu() {
    if (!menuPanel || !menuBtn) return;
    menuPanel.hidden = true;
    menuBtn.setAttribute('aria-expanded', 'false');
  }

  function toggleMenu() {
    if (!menuPanel || !menuBtn) return;
    const open = menuPanel.hidden;
    menuPanel.hidden = !open;
    menuBtn.setAttribute('aria-expanded', open ? 'true' : 'false');
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

  if (menuBtn) {
    menuBtn.addEventListener('click', (ev) => {
      ev.stopPropagation();
      toggleMenu();
    });
  }

  doc.addEventListener('click', (ev) => {
    if (!menuPanel || menuPanel.hidden) return;
    if (menuPanel.contains(ev.target) || (menuBtn && menuBtn.contains(ev.target))) return;
    closeMenu();
  });

  if (roadmapBtn) {
    roadmapBtn.addEventListener('click', () => {
      closeMenu();
      if (typeof hooks.openRoadmap === 'function') hooks.openRoadmap();
    });
  }

  function refreshMerged() {
    if (!fetchFn) return;
    fetchFn('/api/dev-status')
      .then((r) => (r.ok ? r.json() : null))
      .catch(() => null)
      .then((body) => {
        if (!body || !Array.isArray(body.timeline)) {
          setMergedLabel(mergedEl, null);
          return;
        }
        const list = filterMergedToday(body.timeline, Date.now());
        setMergedLabel(mergedEl, list.length);
      });
  }
  refreshMerged();
  const poll = win.setInterval(refreshMerged, 60_000);

  return {
    destroy() {
      win.clearInterval(poll);
      closeMenu();
      scene.destroy();
    }
  };
}