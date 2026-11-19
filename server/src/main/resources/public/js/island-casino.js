// 網頁賭場大廳與單人 21 點（純邏輯模組 + DOM 掛載；Three.js 在 island.js）。
import {
  CHIP_STORAGE_KEY,
  MIN_BET,
  STARTING_BALANCE,
  cardLabel,
  createBlackjackRound,
  isRed,
  readChipBalance,
  writeChipBalance,
  placeBet,
  settleBet,
  canPlaceBet,
  needsPracticeChipRefill,
  handValue
} from './island-blackjack.js';

/**
 * 互動按鈕 DOM 更新：只在 visible／label 相對於 prev 有變時才回傳要寫的欄位（避免每幀重設 textContent）。
 * @param {{ visible?: boolean, label?: string }} prev
 * @param {{ visible?: boolean, label?: string }} next
 */
export function promptDomUpdate(prev, next) {
  const p = prev || { visible: false, label: '' };
  const n = next || { visible: false, label: '' };
  const show = !!n.visible;
  const wasShow = !!p.visible;
  const label = n.label != null ? String(n.label) : '';
  const prevLabel = p.label != null ? String(p.label) : '';
  const patch = {};
  if (show !== wasShow) {
    patch.hidden = !show;
    patch.ariaHidden = show ? 'false' : 'true';
  }
  if (label && label !== prevLabel) {
    patch.textContent = label;
    patch.ariaLabel = label;
  }
  return patch;
}

export const CASINO_NAME = '神之島賭場';
export const CASINO_GAMES = Object.freeze([
  Object.freeze({
    id: 'blackjack',
    title: '網頁 21 點',
    blurb: '單人對莊家，練習籌碼僅本機。',
    status: 'open',
    playable: true
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
 * 籌碼顯示模型。啟用時傳 { enabled: true, balance: number, currency?: string }。
 * 練習籌碼餘額見 island-blackjack.js CHIP_STORAGE_KEY（localStorage）。
 */
export function chipWalletView(wallet) {
  if (wallet && wallet.enabled) {
    const balance = wallet.balance == null ? 0 : wallet.balance;
    return {
      enabled: true,
      balance: balance,
      label: wallet.currency || '練習籌碼',
      text: String(balance),
      note: wallet.note || '練習籌碼，僅本機，不是真錢。'
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

function formatCards(cards, hideSecond) {
  if (!cards || !cards.length) return '（無）';
  return cards.map((c, i) => {
    if (hideSecond && i === 1) return '蓋牌';
    const red = isRed(c) ? ' casino-card-red' : '';
    return '<span class="casino-card' + red + '">' + cardLabel(c) + '</span>';
  }).join(' ');
}

/** 掛載大廳 DOM 事件；回傳 { open, close, isOpen, isTableOpen, closeTable, destroy }。 */
export function mountCasinoLobby(opts) {
  const document = (opts && opts.doc) || globalThis.document;
  const root = opts && opts.root;
  const onChange = opts && opts.onChange;
  const storage = (opts && opts.storage) || (globalThis.localStorage);
  let state = lobbyState(null, '');
  let tableOpen = false;
  let blackjackFromInterior = false;
  let balance = readChipBalance(storage);
  const TABLE_BACK_LOBBY = '返回大廳';
  const TABLE_BACK_INTERIOR = '返回賭廳';
  const chipsEl = root && root.querySelector('[data-casino-chips]');
  const chipsNoteEl = root && root.querySelector('[data-casino-chips-note]');
  const playerEl = root && root.querySelector('[data-casino-player]');
  const backBtn = root && root.querySelector('[data-casino-back]');
  const gamesEl = root && root.querySelector('[data-casino-games]');
  const tableEl = root && root.querySelector('[data-casino-blackjack]');
  const subEl = root && root.querySelector('[data-casino-sub]');
  let round = createBlackjackRound();
  let betInput = null;
  let tableBackBtn = null;
  let btnHit = null;
  let btnStand = null;
  let btnDeal = null;
  let btnAgain = null;
  let btnRefill = null;
  let insufficientEl = null;
  let resultEl = null;
  let playerHandEl = null;
  let dealerHandEl = null;
  let playerValEl = null;
  let dealerValEl = null;
  let betRow = null;

  function walletModel() {
    return { enabled: true, balance, currency: '練習籌碼' };
  }

  function persistBalance(next) {
    balance = Math.max(0, Math.floor(next));
    writeChipBalance(storage, balance);
  }

  function refreshChips() {
    if (playerEl) {
      playerEl.textContent = playerLobbyLabel(opts && opts.auth);
    }
    if (chipsEl) {
      const chips = chipWalletView(walletModel());
      chipsEl.textContent = chips.label + ' ' + chips.text;
      chipsEl.setAttribute('data-enabled', 'true');
      if (chips.note) chipsEl.setAttribute('title', chips.note);
    }
    if (chipsNoteEl) {
      chipsNoteEl.textContent = '練習籌碼，僅本機，不是真錢';
    }
  }

  function setTableVisible(open) {
    tableOpen = !!open;
    if (gamesEl) gamesEl.hidden = tableOpen;
    if (tableEl) tableEl.hidden = !tableOpen;
    if (subEl) {
      subEl.textContent = tableOpen
        ? '21 點牌桌 · 勝出賠率 1:1'
        : '練習大廳 · 虛擬籌碼僅本機';
    }
    document.body.classList.toggle('casino-blackjack-open', tableOpen);
    if (open && tableEl) {
      if (root && typeof root.scrollTop === 'number') root.scrollTop = 0;
      if (typeof tableEl.scrollIntoView === 'function') {
        tableEl.scrollIntoView({ block: 'start', behavior: 'auto' });
      }
    }
  }

  function renderRoundUI() {
    const st = round.getState();
    const hideHole = st.holeHidden && st.phase !== 'done';
    if (playerHandEl) {
      playerHandEl.innerHTML = formatCards(st.player, false);
    }
    if (dealerHandEl) {
      dealerHandEl.innerHTML = formatCards(st.dealer, hideHole);
    }
    if (playerValEl) playerValEl.textContent = '點數 ' + st.playerValue;
    if (dealerValEl) {
      dealerValEl.textContent = hideHole ? '莊家明牌 ' + st.dealerValue : '點數 ' + handValue(st.dealer);
    }
    if (resultEl) {
      resultEl.textContent = st.outcome || '';
      resultEl.hidden = !st.outcome;
    }
    const betting = st.phase === 'betting';
    const playing = st.phase === 'player';
    const done = st.phase === 'done';
    if (betInput) betInput.disabled = !betting;
    if (btnDeal) {
      btnDeal.hidden = !betting;
      btnDeal.disabled = false;
    }
    if (btnHit) {
      btnHit.hidden = !playing;
      btnHit.disabled = !playing;
    }
    if (btnStand) {
      btnStand.hidden = !playing;
      btnStand.disabled = !playing;
    }
    if (btnAgain) {
      btnAgain.hidden = !done;
      btnAgain.disabled = !done;
    }
    const broke = needsPracticeChipRefill(balance);
    if (insufficientEl) insufficientEl.hidden = !(broke && betting);
    if (btnRefill) btnRefill.hidden = !(broke && betting);
    if (betRow) betRow.hidden = broke && betting;
    if (btnDeal && betting) {
      btnDeal.disabled = broke;
    }
  }

  function applyRoundPayout(st) {
    if (!st.compare) return;
    const next = settleBet(balance, st.bet, st.compare.winner);
    persistBalance(next);
    refreshChips();
  }

  function beginBet() {
    const raw = betInput ? parseInt(betInput.value, 10) : MIN_BET;
    const amount = Number.isFinite(raw) ? raw : MIN_BET;
    if (!canPlaceBet(balance, amount)) {
      if (resultEl) {
        resultEl.hidden = false;
        resultEl.textContent = '籌碼不足或下注低於 ' + MIN_BET;
      }
      return;
    }
    const afterBet = placeBet(balance, amount);
    if (afterBet == null) return;
    persistBalance(afterBet);
    refreshChips();
    round.start(amount);
    const st = round.getState();
    if (st.phase === 'done') applyRoundPayout(st);
    renderRoundUI();
  }

  function onRefillChips() {
    if (!needsPracticeChipRefill(balance)) return;
    persistBalance(STARTING_BALANCE);
    refreshChips();
    if (resultEl) resultEl.hidden = true;
    renderRoundUI();
  }

  function onHit() {
    round.hit();
    const st = round.getState();
    if (st.phase === 'done') applyRoundPayout(st);
    renderRoundUI();
  }

  function onStand() {
    round.stand();
    const st = round.getState();
    applyRoundPayout(st);
    renderRoundUI();
  }

  function onAgain() {
    round.reset();
    if (resultEl) resultEl.hidden = true;
    renderRoundUI();
  }

  function openBlackjack(fromInterior) {
    if (fromInterior) {
      blackjackFromInterior = true;
      if (tableBackBtn) tableBackBtn.textContent = TABLE_BACK_INTERIOR;
      if (!isLobbyOpen(state)) setOpen(true);
    }
    setTableVisible(true);
    round.reset();
    if (betInput) betInput.value = String(Math.min(100, balance));
    if (resultEl) resultEl.hidden = true;
    renderRoundUI();
    if (tableBackBtn && typeof tableBackBtn.focus === 'function') tableBackBtn.focus();
  }

  function closeTable() {
    if (!tableOpen) return;
    const fromInterior = blackjackFromInterior;
    setTableVisible(false);
    round.reset();
    if (fromInterior) {
      blackjackFromInterior = false;
      if (tableBackBtn) tableBackBtn.textContent = TABLE_BACK_LOBBY;
      if (isLobbyOpen(state)) {
        state = lobbyState(state, 'close');
        render();
        if (typeof onChange === 'function') onChange(state);
      }
    }
  }

  function buildBlackjackTable() {
    if (!tableEl || tableEl.dataset.ready) return;
    tableEl.innerHTML = '';
    const head = document.createElement('header');
    head.className = 'casino-bj-head';
    const title = document.createElement('h2');
    title.textContent = '網頁 21 點';
    tableBackBtn = document.createElement('button');
    tableBackBtn.type = 'button';
    tableBackBtn.className = 'btn btn-ghost';
    tableBackBtn.textContent = TABLE_BACK_LOBBY;
    tableBackBtn.setAttribute('data-casino-table-back', '');
    head.appendChild(title);
    head.appendChild(tableBackBtn);

    const table = document.createElement('div');
    table.className = 'casino-bj-table';

    const dealerBlock = document.createElement('div');
    dealerBlock.className = 'casino-bj-hand';
    dealerBlock.innerHTML = '<span class="casino-bj-label">莊家</span>';
    dealerHandEl = document.createElement('div');
    dealerHandEl.className = 'casino-bj-cards';
    dealerValEl = document.createElement('p');
    dealerValEl.className = 'casino-bj-value';
    dealerBlock.appendChild(dealerHandEl);
    dealerBlock.appendChild(dealerValEl);

    const playerBlock = document.createElement('div');
    playerBlock.className = 'casino-bj-hand';
    playerBlock.innerHTML = '<span class="casino-bj-label">你的牌</span>';
    playerHandEl = document.createElement('div');
    playerHandEl.className = 'casino-bj-cards';
    playerValEl = document.createElement('p');
    playerValEl.className = 'casino-bj-value';
    playerBlock.appendChild(playerHandEl);
    playerBlock.appendChild(playerValEl);

    resultEl = document.createElement('p');
    resultEl.className = 'casino-bj-result';
    resultEl.hidden = true;

    insufficientEl = document.createElement('p');
    insufficientEl.className = 'casino-bj-insufficient';
    insufficientEl.textContent = '練習籌碼不足';
    insufficientEl.hidden = true;

    btnRefill = document.createElement('button');
    btnRefill.type = 'button';
    btnRefill.className = 'btn casino-bj-refill';
    btnRefill.textContent = '重新領取 ' + STARTING_BALANCE + ' 練習籌碼';
    btnRefill.hidden = true;

    betRow = document.createElement('div');
    betRow.className = 'casino-bj-bet';
    const betLabel = document.createElement('label');
    betLabel.textContent = '下注（最少 ' + MIN_BET + '）';
    betInput = document.createElement('input');
    betInput.type = 'number';
    betInput.className = 'casino-bj-bet-input';
    betInput.min = String(MIN_BET);
    betInput.step = '10';
    betInput.inputMode = 'numeric';
    betInput.value = String(Math.min(100, STARTING_BALANCE));
    betLabel.appendChild(betInput);
    betRow.appendChild(betLabel);

    const actions = document.createElement('div');
    actions.className = 'casino-bj-actions';
    btnDeal = document.createElement('button');
    btnDeal.type = 'button';
    btnDeal.className = 'btn';
    btnDeal.textContent = '下注並發牌';
    btnHit = document.createElement('button');
    btnHit.type = 'button';
    btnHit.className = 'btn';
    btnHit.textContent = '要牌';
    btnStand = document.createElement('button');
    btnStand.type = 'button';
    btnStand.className = 'btn btn-ghost';
    btnStand.textContent = '停牌';
    btnAgain = document.createElement('button');
    btnAgain.type = 'button';
    btnAgain.className = 'btn';
    btnAgain.textContent = '再來一局';
    actions.appendChild(btnDeal);
    actions.appendChild(btnHit);
    actions.appendChild(btnStand);
    actions.appendChild(btnAgain);

    table.appendChild(dealerBlock);
    table.appendChild(playerBlock);
    table.appendChild(resultEl);
    table.appendChild(insufficientEl);
    table.appendChild(btnRefill);
    table.appendChild(betRow);
    table.appendChild(actions);

    tableEl.appendChild(head);
    tableEl.appendChild(table);
    tableEl.dataset.ready = '1';

    tableBackBtn.addEventListener('click', (e) => {
      e.preventDefault();
      closeTable();
    });
    btnDeal.addEventListener('click', (e) => {
      e.preventDefault();
      beginBet();
    });
    btnHit.addEventListener('click', (e) => {
      e.preventDefault();
      onHit();
    });
    btnStand.addEventListener('click', (e) => {
      e.preventDefault();
      onStand();
    });
    btnAgain.addEventListener('click', (e) => {
      e.preventDefault();
      onAgain();
    });
    btnRefill.addEventListener('click', (e) => {
      e.preventDefault();
      onRefillChips();
    });
  }

  function render() {
    if (!root) return;
    const open = isLobbyOpen(state);
    root.hidden = !open;
    root.setAttribute('aria-hidden', open ? 'false' : 'true');
    document.body.classList.toggle('casino-lobby-open', open);
    refreshChips();
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
        if (!game.playable) {
          const badge = document.createElement('span');
          badge.className = 'casino-game-badge';
          badge.textContent = '即將推出';
          card.appendChild(badge);
        }
        card.appendChild(title);
        card.appendChild(blurb);
        const action = document.createElement('button');
        action.type = 'button';
        action.className = 'btn btn-ghost';
        if (game.playable) {
          action.textContent = '開始遊玩';
          action.disabled = false;
          action.removeAttribute('aria-disabled');
          action.classList.add('casino-game-play');
          action.addEventListener('click', (e) => {
            e.preventDefault();
            if (game.id === 'blackjack') openBlackjack();
          });
        } else {
          action.textContent = '尚不可玩';
          action.disabled = true;
          action.setAttribute('aria-disabled', 'true');
        }
        card.appendChild(action);
        gamesEl.appendChild(card);
      });
      gamesEl.dataset.ready = '1';
    }
    buildBlackjackTable();
    if (!open) closeTable();
    renderRoundUI();
  }

  function setOpen(next) {
    const want = !!next;
    if (want === isLobbyOpen(state)) {
      render();
      return state;
    }
    if (want && !canOpenCasinoLobby({ lobbyOpen: isLobbyOpen(state) })) return state;
    if (!want) closeTable();
    state = lobbyState(state, want ? 'open' : 'close');
    render();
    if (typeof onChange === 'function') onChange(state);
    return state;
  }

  function onBack(event) {
    if (event) event.preventDefault();
    if (tableOpen) {
      closeTable();
      return;
    }
    setOpen(false);
  }

  if (backBtn) backBtn.addEventListener('click', onBack);

  render();

  return {
    open: () => setOpen(true),
    close: () => setOpen(false),
    isOpen: () => isLobbyOpen(state),
    isTableOpen: () => tableOpen,
    closeTable: () => closeTable(),
    openBlackjack: (fromInterior) => openBlackjack(!!fromInterior),
    getBalance: () => balance,
    destroy: () => {
      if (backBtn) backBtn.removeEventListener('click', onBack);
      document.body.classList.remove('casino-lobby-open');
      document.body.classList.remove('casino-blackjack-open');
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
    storage: (opts && opts.storage) || (win && win.localStorage),
    auth: { authenticated: !!(win && win.PUNCHCLOCK_AUTHENTICATED) },
    onChange: (state) => {
      if (typeof hooks.onLobbyChange === 'function') hooks.onLobbyChange(state);
    }
  });

  let promptState = {
    visible: promptBtn ? !promptBtn.hidden : false,
    label: promptBtn ? String(promptBtn.textContent || '') : ''
  };

  function setPromptVisible(visible, label) {
    if (!promptBtn) return;
    const next = {
      visible: !!visible,
      label: label ? String(label) : promptState.label
    };
    const patch = promptDomUpdate(promptState, next);
    if (Object.keys(patch).length === 0) return;
    if ('hidden' in patch) promptBtn.hidden = patch.hidden;
    if ('ariaHidden' in patch) promptBtn.setAttribute('aria-hidden', patch.ariaHidden);
    if ('textContent' in patch) promptBtn.textContent = patch.textContent;
    if ('ariaLabel' in patch) promptBtn.setAttribute('aria-label', patch.ariaLabel);
    promptState = next;
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
    if (lobby.isTableOpen()) lobby.closeTable();
    lobby.close();
  }

  function closeTable() {
    lobby.closeTable();
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
      if (typeof hooks.onListCasino === 'function' && hooks.onListCasino() === true) return;
      openLobby();
    });
  });

  return {
    lobby: lobby,
    setPromptVisible: setPromptVisible,
    openLobby: openLobby,
    closeLobby: closeLobby,
    closeTable: closeTable,
    openBlackjackFromInterior: () => {
      hideList();
      if (typeof hooks.beforeOpen === 'function') hooks.beforeOpen();
      setPromptVisible(false);
      lobby.openBlackjack(true);
    },
    isTableOpen: () => lobby.isTableOpen(),
    isOpen: () => lobby.isOpen()
  };
}

export { CHIP_STORAGE_KEY, STARTING_BALANCE, MIN_BET };