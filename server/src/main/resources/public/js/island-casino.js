// 網頁賭場大廳狀態與籌碼佔位（純邏輯，不依賴 Three.js）。
// 後續接真籌碼錢包時：傳入 chipWalletView({ enabled: true, balance: N }) 即可，不必改大廳版型。

export const CASINO_NAME = '莫卡賭場';
export const CASINO_GAMES = Object.freeze([
  Object.freeze({
    id: 'blackjack',
    title: '網頁 21 點',
    blurb: '下一階段開放。現在只能逛大廳。',
    status: 'soon',
    playable: false
  }),
  Object.freeze({
    id: 'slots',
    title: '老虎機',
    blurb: '即將推出，尚不可下注。',
    status: 'soon',
    playable: false
  })
]);

export function lobbyState(state, action) {
  const s = state || { open: false };
  switch (action) {
    case 'open': return { open: true };
    case 'close': return { open: false };
    default: return s;
  }
}

export function isLobbyOpen(state) {
  return !!(state && state.open);
}

/**
 * 籌碼顯示模型。Phase 2 固定為尚未啟用佔位；
 * 之後錢包啟用時傳 { enabled: true, balance: number, currency?: string }。
 */
export function chipWalletView(wallet) {
  if (wallet && wallet.enabled) {
    const balance = wallet.balance == null ? 0 : wallet.balance;
    return {
      enabled: true,
      balance: balance,
      label: wallet.currency || '籌碼',
      text: String(balance),
      note: ''
    };
  }
  return {
    enabled: false,
    balance: null,
    label: '虛擬籌碼',
    text: '尚未啟用',
    note: '佔位顯示，不是真實餘額，也不能下注。'
  };
}

export function playerLobbyLabel(auth) {
  if (auth && auth.authenticated) return '店長（訪客籌碼規則相同）';
  return '訪客';
}

export function canOpenCasinoLobby(ctx) {
  if (!ctx) return false;
  if (ctx.lobbyOpen) return false;
  return true;
}

/** 掛載大廳 DOM 事件；回傳 { open, close, isOpen, destroy }。 */
export function mountCasinoLobby(opts) {
  const document = (opts && opts.doc) || globalThis.document;
  const root = opts && opts.root;
  const onChange = opts && opts.onChange;
  let state = lobbyState(null, '');
  const chipsEl = root && root.querySelector('[data-casino-chips]');
  const playerEl = root && root.querySelector('[data-casino-player]');
  const backBtn = root && root.querySelector('[data-casino-back]');
  const gamesEl = root && root.querySelector('[data-casino-games]');

  function render() {
    if (!root) return;
    const open = isLobbyOpen(state);
    root.hidden = !open;
    root.setAttribute('aria-hidden', open ? 'false' : 'true');
    document.body.classList.toggle('casino-lobby-open', open);
    if (playerEl) {
      playerEl.textContent = playerLobbyLabel(opts && opts.auth);
    }
    if (chipsEl) {
      const chips = chipWalletView(opts && opts.wallet);
      chipsEl.textContent = chips.enabled ? (chips.label + ' ' + chips.text) : (chips.label + ' · ' + chips.text);
      chipsEl.setAttribute('data-enabled', chips.enabled ? 'true' : 'false');
      if (chips.note) chipsEl.setAttribute('title', chips.note);
      else chipsEl.removeAttribute('title');
    }
    if (gamesEl && !gamesEl.dataset.ready) {
      gamesEl.innerHTML = '';
      CASINO_GAMES.forEach((game) => {
        const card = document.createElement('article');
        card.className = 'casino-game-card';
        card.setAttribute('data-game', game.id);
        const title = document.createElement('h3');
        title.textContent = game.title;
        const blurb = document.createElement('p');
        blurb.textContent = game.blurb;
        const badge = document.createElement('span');
        badge.className = 'casino-game-badge';
        badge.textContent = '即將推出';
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'btn btn-ghost';
        action.textContent = '尚不可玩';
        action.disabled = true;
        action.setAttribute('aria-disabled', 'true');
        card.appendChild(badge);
        card.appendChild(title);
        card.appendChild(blurb);
        card.appendChild(action);
        gamesEl.appendChild(card);
      });
      gamesEl.dataset.ready = '1';
    }
  }

  function setOpen(next) {
    const want = !!next;
    if (want === isLobbyOpen(state)) {
      render();
      return state;
    }
    if (want && !canOpenCasinoLobby({ lobbyOpen: isLobbyOpen(state) })) return state;
    state = lobbyState(state, want ? 'open' : 'close');
    render();
    if (typeof onChange === 'function') onChange(state);
    return state;
  }

  function onBack(event) {
    if (event) event.preventDefault();
    setOpen(false);
  }

  if (backBtn) backBtn.addEventListener('click', onBack);

  render();

  return {
    open: () => setOpen(true),
    close: () => setOpen(false),
    isOpen: () => isLobbyOpen(state),
    destroy: () => {
      if (backBtn) backBtn.removeEventListener('click', onBack);
      document.body.classList.remove('casino-lobby-open');
    }
  };
}

/**
 * 島嶼上的大廳控制器：入口提示、清單「進入賭場」、返回按鈕。
 * hooks.beforeOpen()：開啟前的 UI 整理（鎖移動、關面板）。
 * hooks.onLobbyChange(state)：大廳每次開／關都只從這裡通知，不論來源是返回按鈕、Escape 或程式呼叫。
 */
export function createCasinoController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const win = (opts && opts.win) || globalThis.window;
  const hooks = (opts && opts.hooks) || {};
  const lobbyRoot = doc.getElementById('casinoLobby');
  const promptBtn = doc.getElementById('islandInteract');
  const lobby = mountCasinoLobby({
    doc: doc,
    root: lobbyRoot,
    auth: { authenticated: !!(win && win.PUNCHCLOCK_AUTHENTICATED) },
    wallet: null,
    onChange: (state) => {
      if (typeof hooks.onLobbyChange === 'function') hooks.onLobbyChange(state);
    }
  });

  function setPromptVisible(visible, label) {
    if (!promptBtn) return;
    const show = !!visible;
    promptBtn.hidden = !show;
    promptBtn.setAttribute('aria-hidden', show ? 'false' : 'true');
    if (label) promptBtn.textContent = label;
  }

  function hideList() {
    const list = doc.getElementById('islandList');
    const listBtn = doc.getElementById('islandListBtn');
    if (list) list.hidden = true;
    if (listBtn) listBtn.setAttribute('aria-expanded', 'false');
  }

  function openLobby() {
    if (lobby.isOpen()) return;
    hideList();
    if (typeof hooks.beforeOpen === 'function') hooks.beforeOpen();
    setPromptVisible(false);
    lobby.open();
    const back = lobbyRoot && lobbyRoot.querySelector('[data-casino-back]');
    if (back && typeof back.focus === 'function' && win && typeof win.requestAnimationFrame === 'function') {
      win.requestAnimationFrame(() => back.focus());
    }
  }

  function closeLobby() {
    lobby.close();
  }

  if (promptBtn) {
    promptBtn.addEventListener('click', (event) => {
      event.preventDefault();
      if (typeof hooks.onPrompt === 'function' && hooks.onPrompt() === true) return;
      openLobby();
    });
  }

  doc.querySelectorAll('[data-open-casino]').forEach((el) => {
    el.addEventListener('click', (event) => {
      event.preventDefault();
      openLobby();
    });
  });

  return {
    lobby: lobby,
    setPromptVisible: setPromptVisible,
    openLobby: openLobby,
    closeLobby: closeLobby,
    isOpen: () => lobby.isOpen()
  };
}
