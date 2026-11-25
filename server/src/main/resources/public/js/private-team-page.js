import { wireHomeLoginModal } from './home-login-modal.js';
import { mountPrivateTeamScene } from './private-team-scene.js';
import {
  bubbleTextForStatus,
  completedTodayKeys,
  fetchSecretaryDevQueue,
  fillStatusCard,
  syncStatusBodyScroll,
  hasNewCompletedToday,
  secretarySpriteUrlForStatus,
  taskProgressFromTiger,
  tigerFromPublic
} from './private-team-office.js';

/** 登出選單貼在左上徽章下，並夾在 viewport 內（供單測與執行期） */
export function layoutAuthMenuInViewport(doc, win, menuEl, taskBtnEl, marginPx) {
  if (!menuEl || !taskBtnEl) return;
  const margin = Number.isFinite(marginPx) ? marginPx : 10;
  menuEl.style.transform = '';
  menuEl.style.left = '0';
  menuEl.style.right = 'auto';
  const badge = taskBtnEl.querySelector('.pt-a-frame');
  const fh = badge && badge.offsetHeight > 0 ? badge.offsetHeight : 58;
  menuEl.style.top = fh + 8 + 'px';
  if (menuEl.hidden) return;
  const vw = win.innerWidth || (doc.documentElement && doc.documentElement.clientWidth) || 1024;
  const anchor = badge || taskBtnEl;
  const ar = anchor.getBoundingClientRect();
  const mr = menuEl.getBoundingClientRect();
  let shift = 0;
  if (ar.left + mr.width > vw - margin) {
    shift = vw - margin - (ar.left + mr.width);
  }
  if (ar.left + shift < margin) {
    shift = margin - ar.left;
  }
  menuEl.style.transform = shift ? 'translateX(' + shift + 'px)' : '';
}

export function deployVersionLabel(win, opts) {
  const ver = win && typeof win.PUNCHCLOCK_SERVER_VERSION === 'string'
    ? win.PUNCHCLOCK_SERVER_VERSION.trim()
    : '';
  let bt = win && typeof win.PUNCHCLOCK_SERVER_BUILD_TIME === 'string'
    ? win.PUNCHCLOCK_SERVER_BUILD_TIME.trim()
    : '';
  if (!ver || !bt || ver.includes('{{') || bt.includes('{{')) {
    return '';
  }
  if (opts && opts.compact) {
    const short = /^(\d{4})-(\d{2}-\d{2})(\s+\d{2}:\d{2}(?:\s|$).*)/.exec(bt);
    if (short) bt = short[2] + short[3];
  }
  return '網站 v' + ver + ' · ' + bt;
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
  const narrow = (win.innerWidth || 1024) <= 520;
  const label = deployVersionLabel(win, { compact: narrow });
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

const DL_SVG =
  '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 4v11" stroke="#fff" stroke-width="2.5" stroke-linecap="round"/><path d="M8 12l4 4 4-4" fill="none" stroke="#fff" stroke-width="2.5" stroke-linecap="round" stroke-linejoin="round"/><rect x="5" y="18" width="14" height="2" fill="#fff"/></svg>';

const FRAME_CORNER_SVG =
  '<svg viewBox="0 0 22 22" fill="none" aria-hidden="true">' +
  '<path d="M3 19 V6 Q3 3 6 3 H19" stroke="#f3e3a6" stroke-width="2.4" stroke-linecap="square"/>' +
  '<path d="M6 19 V8 Q6 6 8 6 H19" stroke="#8a6a28" stroke-width="1.2"/>' +
  '<rect x="2" y="2" width="4.2" height="4.2" fill="#f3e3a6" stroke="#8a6a28" stroke-width="0.8"/></svg>';

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
    '<div class="pt-hud-auth-wrap pt-hud-id-wrap" data-pt-auth-wrap>' +
    '<button type="button" class="pt-hud-task" data-pt-task-btn aria-haspopup="true" aria-expanded="false" aria-label="訪客・點此登入">' +
    '<span class="pt-a-frame">' +
    '<span class="pt-a-frame-inner">' +
    '<img src="/assets/private-team/secretary-avatar.webp" alt="" aria-hidden="true" width="192" height="192" decoding="async">' +
    '</span>' +
    frameCornerSpans() +
    '</span>' +
    '<span class="pt-a-plate">' +
    '<span class="pt-a-name" data-pt-auth-label>訪客・點此登入</span>' +
    '<span class="pt-a-bar-row" data-pt-task-bar hidden>' +
    '<span class="pt-a-bar-label">今日完成</span>' +
    '<span class="pt-a-bar" aria-hidden="true"><i data-pt-task-bar-fill></i></span>' +
    '<span class="pt-a-bar-count" data-pt-task-bar-count></span>' +
    '</span></span></button>' +
    '<div class="pt-hud-id-menu" data-pt-auth-menu hidden role="menu" aria-label="帳號選單">' +
    '<a href="/logout" class="pt-hud-id-logout" role="menuitem">登出</a>' +
    '</div></div>'
  );
}

export function mountPrivateTeamPage(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis;
  const fetchFn = (opts && opts.fetch) || (win.fetch ? win.fetch.bind(win) : null);
  const stage = doc.querySelector('[data-private-team-stage]');
  if (!stage) return { destroy() {} };

  let sessionAuthed = !!(win.PUNCHCLOCK_AUTHENTICATED);
  const pageHooks = (opts && opts.hooks) || {};
  const officeModal = opts && opts.officeModal;
  const openTasks = typeof pageHooks.openTasks === 'function' ? pageHooks.openTasks : null;
  const closeTasks = typeof pageHooks.closeTasks === 'function' ? pageHooks.closeTasks : null;
  const openDownload =
    typeof pageHooks.openDownload === 'function' ? pageHooks.openDownload : null;
  const closeDownload =
    typeof pageHooks.closeDownload === 'function' ? pageHooks.closeDownload : null;

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
      '</div>' +
      '<div class="pt-hud-br">' +
      '<button type="button" class="pt-hud-download" data-pt-open-download aria-label="下載 App">' + DL_SVG +
      '<span class="pt-hud-dl-label">下載 App</span></button></div>' +
      '<p class="pt-hud-deploy-ver" data-pt-deploy-ver hidden aria-hidden="true"></p>';
    stage.appendChild(hud);
  }
  mountDeployVersion(hud, win, doc);

  let cardBackdrop = stage.querySelector('[data-pt-status-card]');
  let cardBody = null;
  let cardScroll = null;
  let closeBtn = null;
  if (!cardBackdrop) {
    cardBackdrop = doc.createElement('div');
    cardBackdrop.className = 'pt-status-backdrop';
    cardBackdrop.setAttribute('data-pt-status-card', '');
    cardBackdrop.hidden = true;
    cardBackdrop.innerHTML =
      '<div class="pt-status-card maple-window" role="dialog" aria-modal="true" aria-labelledby="pt-status-title" tabindex="-1">' +
      '<button type="button" class="pt-status-close" data-pt-status-close aria-label="關閉工作狀態">×</button>' +
      '<h2 id="pt-status-title" class="pt-status-title">私人女秘書・工作狀態</h2>' +
      '<div class="pt-status-scroll" data-pt-status-scroll>' +
      '<div class="pt-status-body" data-pt-status-body></div>' +
      '</div>' +
      '</div>';
    stage.appendChild(cardBackdrop);
  }
  const cardDialog = cardBackdrop.querySelector('.pt-status-card');
  cardScroll = cardBackdrop.querySelector('[data-pt-status-scroll]');
  cardBody = cardBackdrop.querySelector('[data-pt-status-body]');
  closeBtn = cardBackdrop.querySelector('[data-pt-status-close]');

  function refreshStatusBodyScroll() {
    syncStatusBodyScroll(cardScroll, cardBody);
  }

  if (cardBody && cardBody.getAttribute('data-pt-scroll-bound') !== '1') {
    cardBody.setAttribute('data-pt-scroll-bound', '1');
    cardBody.addEventListener('scroll', refreshStatusBodyScroll, { passive: true });
  }

  const taskBtn = hud.querySelector('[data-pt-task-btn]');
  const authWrap = hud.querySelector('[data-pt-auth-wrap]');
  const authLabel = hud.querySelector('[data-pt-auth-label]');
  const authMenu = hud.querySelector('[data-pt-auth-menu]');
  const taskBarRow = hud.querySelector('[data-pt-task-bar]');
  const taskBarFill = hud.querySelector('[data-pt-task-bar-fill]');
  const taskBarCount = hud.querySelector('[data-pt-task-bar-count]');

  let tigerView = { status: '待命中', active: [], completedToday: [] };
  let seenCompletedKeys = null;
  let pendingCompletedFlash = false;
  let devQueue = [];
  const statusUi = { doneExpanded: false, queueExpanded: false };
  let cardOpen = false;
  let authMenuOpen = false;
  let loginOpen = false;
  let pendingOpenTasksAfterLogin = false;
  let lastFocus = null;

  function authDisplayName() {
    const raw =
      typeof win.PUNCHCLOCK_USERNAME === 'string' ? win.PUNCHCLOCK_USERNAME.trim() : '';
    if (sessionAuthed) return raw || 'root';
    return '';
  }

  function paintAuthHud() {
    if (!authLabel || !taskBtn) return;
    if (sessionAuthed) {
      const name = authDisplayName();
      authLabel.textContent = name;
      taskBtn.setAttribute('aria-label', name + '，開啟登出選單');
    } else {
      authLabel.textContent = '訪客・點此登入';
      taskBtn.setAttribute('aria-label', '訪客・點此登入');
    }
    taskBtn.setAttribute('aria-expanded', authMenuOpen ? 'true' : 'false');
  }

  function syncAuthMenuLayout() {
    layoutAuthMenuInViewport(doc, win, authMenu, taskBtn);
  }

  function closeAuthMenu() {
    if (!authMenu || !authMenuOpen) return;
    authMenuOpen = false;
    authMenu.hidden = true;
    if (authMenu) authMenu.style.transform = '';
    paintAuthHud();
  }

  function toggleAuthMenu() {
    if (!authMenu || !sessionAuthed) return;
    authMenuOpen = !authMenuOpen;
    authMenu.hidden = !authMenuOpen;
    paintAuthHud();
    if (authMenuOpen) {
      if (typeof win.requestAnimationFrame === 'function') {
        win.requestAnimationFrame(() => syncAuthMenuLayout());
      } else {
        syncAuthMenuLayout();
      }
    } else if (authMenu) {
      authMenu.style.transform = '';
    }
  }

  function bindOpenLoginHandlers(root) {
    if (!root || typeof root.querySelectorAll !== 'function') return;
    const nodes = root.querySelectorAll('[data-pt-open-login]');
    for (let i = 0; i < nodes.length; i++) {
      const node = nodes[i];
      if (node.getAttribute('data-pt-login-bound') === '1') continue;
      node.setAttribute('data-pt-login-bound', '1');
      node.addEventListener('click', (ev) => {
        ev.preventDefault();
        ev.stopPropagation();
        closeAuthMenu();
        closeStatusCard();
        loginModal.open(node);
      });
    }
  }

  function applySessionAuthed() {
    sessionAuthed = true;
    win.PUNCHCLOCK_AUTHENTICATED = true;
    if (!win.PUNCHCLOCK_USERNAME) win.PUNCHCLOCK_USERNAME = 'root';
    if (doc.body) {
      doc.body.classList.remove('is-guest');
      doc.body.classList.add('is-authenticated');
    }
    closeAuthMenu();
    paintAuthHud();
    if (pendingOpenTasksAfterLogin) {
      pendingOpenTasksAfterLogin = false;
      if (openTasks) openTasks();
    }
  }

  let loginModal = {
    open() {},
    close() {},
    destroy() {},
    isOpen() {
      return false;
    }
  };

  function bubbleLabel() {
    return bubbleTextForStatus(tigerView.status);
  }

  function refreshCanvasA11y() {
    const label = bubbleLabel();
    canvas.setAttribute(
      'aria-label',
      '私人女秘書場景，目前' + label + '。點選秘書或泡泡，或按 Enter 查看工作狀態。'
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

  function statusCardUiOpts() {
    return {
      canLinkGithub: sessionAuthed,
      doneExpanded: statusUi.doneExpanded,
      queueExpanded: statusUi.queueExpanded,
      onToggleDone: () => {
        statusUi.doneExpanded = !statusUi.doneExpanded;
        paintStatusCard();
      },
      onToggleQueue: () => {
        statusUi.queueExpanded = !statusUi.queueExpanded;
        paintStatusCard();
      }
    };
  }

  function paintStatusCard() {
    const ui = Object.assign({}, statusCardUiOpts(), {
      flashCompleted: pendingCompletedFlash
    });
    pendingCompletedFlash = false;
    fillStatusCard(doc, cardBody, { tiger: tigerView, queue: devQueue }, ui);
    if (typeof requestAnimationFrame === 'function') {
      requestAnimationFrame(refreshStatusBodyScroll);
    } else {
      refreshStatusBodyScroll();
    }
  }

  function openStatusCard() {
    if (cardOpen) return;
    lastFocus = doc.activeElement;
    paintStatusCard();
    cardOpen = true;
    cardBackdrop.hidden = false;
    if (closeBtn) closeBtn.focus({ preventScroll: true });
  }

  function onSceneActivate(target) {
    if (target === 'monitor') {
      if (!sessionAuthed) {
        pendingOpenTasksAfterLogin = true;
        loginModal.open(canvas);
        return;
      }
      if (openTasks) openTasks();
      return;
    }
    openStatusCard();
  }

  let tasksOpen = false;
  let downloadOpen = false;

  function syncSceneOverlay() {
    const sceneBlocked = tasksOpen || downloadOpen;
    stage.classList.toggle('pt-roadmap-open', sceneBlocked);
    scene.setSceneInputEnabled(!sceneBlocked && !loginOpen);
  }

  function setTasksOpen(open) {
    tasksOpen = !!open;
    syncSceneOverlay();
    if (tasksOpen) {
      closeStatusCard();
      closeAuthMenu();
      loginModal.close({ fromManager: true, silent: true });
      if (closeDownload) closeDownload();
    }
  }

  function setDownloadOpen(open) {
    downloadOpen = !!open;
    syncSceneOverlay();
    if (downloadOpen) {
      closeStatusCard();
      closeAuthMenu();
      loginModal.close({ fromManager: true, silent: true });
      if (tasksOpen && closeTasks) closeTasks();
    }
  }

  const scene = mountPrivateTeamScene(canvas, {
    win,
    doc,
    onActivate: onSceneActivate
  });

  scene.setBubbleText(bubbleLabel());
  refreshCanvasA11y();

  loginModal = wireHomeLoginModal({
    doc,
    win,
    fetch: fetchFn,
    officeModal,
    onAuthed: applySessionAuthed,
    onOpenChange: (open) => {
      loginOpen = !!open;
      stage.classList.toggle('pt-login-open', loginOpen);
      scene.setSceneInputEnabled(!loginOpen && !tasksOpen && !downloadOpen);
      if (loginOpen) {
        closeStatusCard();
        closeAuthMenu();
      }
    }
  });

  bindOpenLoginHandlers(hud);

  if (taskBtn) {
    taskBtn.addEventListener('click', (ev) => {
      ev.stopPropagation();
      if (sessionAuthed) {
        closeStatusCard();
        toggleAuthMenu();
        return;
      }
      closeAuthMenu();
      loginModal.open(taskBtn);
    });
  }

  const downloadBtn = hud.querySelector('[data-pt-open-download]');
  if (downloadBtn && openDownload) {
    downloadBtn.addEventListener('click', (ev) => {
      ev.preventDefault();
      ev.stopPropagation();
      closeStatusCard();
      closeAuthMenu();
      openDownload(downloadBtn);
    });
  }

  doc.addEventListener('click', (ev) => {
    if (authMenu && authMenuOpen) {
      if (!authWrap || (!authWrap.contains(ev.target) && !(taskBtn && taskBtn.contains(ev.target)))) {
        closeAuthMenu();
      }
    }
  });

  paintAuthHud();

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
      if (tasksOpen) {
        ev.preventDefault();
        if (closeTasks) closeTasks();
        return;
      }
      if (loginOpen) {
        ev.preventDefault();
        loginModal.close();
        return;
      }
      if (cardOpen) {
        ev.preventDefault();
        closeStatusCard();
        return;
      }
      if (authMenuOpen) {
        ev.preventDefault();
        closeAuthMenu();
        return;
      }
    }
    if (
      !tasksOpen &&
      !downloadOpen &&
      !cardOpen &&
      (ev.key === 'Enter' || ev.key === ' ') &&
      ev.target === canvas
    ) {
      ev.preventDefault();
      scene.playSecretaryWave();
      openStatusCard();
    }
  }
  doc.addEventListener('keydown', onKeyDown);

  function applyTaskBar(tiger) {
    if (!taskBarRow) return;
    const prog = taskProgressFromTiger(tiger);
    if (!prog) {
      taskBarRow.hidden = true;
      taskBarRow.removeAttribute('aria-label');
      if (taskBarFill) taskBarFill.style.width = '0%';
      if (taskBarCount) taskBarCount.textContent = '';
      return;
    }
    taskBarRow.hidden = false;
    taskBarRow.setAttribute(
      'aria-label',
      '今日完成 ' + prog.completed + '／' + prog.total + '（不含排隊）'
    );
    if (taskBarFill) {
      taskBarFill.style.width = Math.round(prog.fraction * 100) + '%';
    }
    if (taskBarCount) {
      taskBarCount.textContent = prog.completed + '/' + prog.total;
    }
  }

  function applyTiger(tiger) {
    if (!tiger) return;
    const now = typeof performance !== 'undefined' && performance.now ? performance.now() : Date.now();
    if (seenCompletedKeys != null && hasNewCompletedToday(seenCompletedKeys, tiger.completedToday)) {
      scene.playSecretaryDance(now);
      pendingCompletedFlash = true;
    }
    seenCompletedKeys = completedTodayKeys(tiger.completedToday);
    tigerView = tiger;
    scene.noteTigerStatus(tiger.status, now);
    scene.setSecretarySprite(secretarySpriteUrlForStatus(tigerView.status));
    scene.setBubbleText(bubbleLabel());
    refreshCanvasA11y();
    applyTaskBar(tiger);
    if (cardOpen) paintStatusCard();
  }

  function refreshDevQueue() {
    if (!fetchFn) return;
    fetchSecretaryDevQueue(fetchFn).then((rows) => {
      devQueue = rows;
      if (cardOpen) paintStatusCard();
    });
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
  refreshDevQueue();
  const poll = win.setInterval(() => {
    refreshOfficePublic();
    refreshDevQueue();
  }, 60_000);

  return {
    setTasksOpen,
    setDownloadOpen,
    getLoginModal() {
      return loginModal;
    },
    setSceneAnimationPaused(paused) {
      scene.setSceneAnimationPaused(paused);
    },
    destroy() {
      win.clearInterval(poll);
      doc.removeEventListener('keydown', onKeyDown);
      loginModal.destroy();
      closeAuthMenu();
      closeStatusCard();
      setTasksOpen(false);
      if (closeTasks) closeTasks();
      scene.destroy();
    }
  };
}