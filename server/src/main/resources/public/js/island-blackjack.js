// 單人對莊家 21 點純邏輯（對齊 shared BlackjackRules.java）。不依賴 DOM／Three.js。
// 練習籌碼 localStorage 鍵名：punchclock.casino.practiceChips

export const DECK_SIZE = 52;
export const CHIP_STORAGE_KEY = 'punchclock.casino.practiceChips';
export const STARTING_BALANCE = 1000;
export const MIN_BET = 10;

export function rank(index) {
  const r = index % 13;
  if (r === 0) return 'A';
  if (r <= 9) return String(r + 1);
  if (r === 10) return 'J';
  if (r === 11) return 'Q';
  return 'K';
}

export function suitSymbol(index) {
  switch (Math.floor(index / 13)) {
    case 0: return '♠';
    case 1: return '♥';
    case 2: return '♦';
    default: return '♣';
  }
}

export function cardLabel(index) {
  return rank(index) + suitSymbol(index);
}

export function isRed(index) {
  const suit = Math.floor(index / 13);
  return suit === 1 || suit === 2;
}

function baseValue(index) {
  const r = index % 13;
  if (r === 0) return 11;
  if (r >= 10) return 10;
  return r + 1;
}

/** 手牌點數；A 可當 1 或 11，取不爆的最大值。 */
export function handValue(cards) {
  if (!cards || cards.length === 0) return 0;
  let total = 0;
  let aces = 0;
  for (const index of cards) {
    total += baseValue(index);
    if (index % 13 === 0) aces++;
  }
  while (total > 21 && aces > 0) {
    total -= 10;
    aces--;
  }
  return total;
}

export function isBust(cards) {
  return handValue(cards) > 21;
}

/** 兩張牌且點數 21 → 自然 21 點。 */
export function isNatural21(cards) {
  return !!cards && cards.length === 2 && handValue(cards) === 21;
}

/** 莊家未達 17 要牌；17（含軟 17）停牌。 */
export function dealerShouldHit(cards) {
  return handValue(cards) < 17;
}

export function shuffledDeck(rng) {
  const random = typeof rng === 'function' ? rng : Math.random;
  const deck = [];
  for (let i = 0; i < DECK_SIZE; i++) deck.push(i);
  for (let i = DECK_SIZE - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    const t = deck[i];
    deck[i] = deck[j];
    deck[j] = t;
  }
  return deck;
}

/**
 * 比大小：回傳 winner = player | dealer | push，以及 reason 供文案。
 * 爆牌的一方輸；雙方都爆平手。自然 21 勝出對方非自然 21。
 */
export function compareHands(player, dealer) {
  const pBust = isBust(player);
  const dBust = isBust(dealer);
  if (pBust && dBust) return { winner: 'push', reason: 'both_bust' };
  if (pBust) return { winner: 'dealer', reason: 'player_bust' };
  if (dBust) return { winner: 'player', reason: 'dealer_bust' };
  const pNat = isNatural21(player);
  const dNat = isNatural21(dealer);
  if (pNat && dNat) return { winner: 'push', reason: 'both_natural' };
  if (pNat) return { winner: 'player', reason: 'player_natural' };
  if (dNat) return { winner: 'dealer', reason: 'dealer_natural' };
  const pv = handValue(player);
  const dv = handValue(dealer);
  if (pv > dv) return { winner: 'player', reason: 'higher' };
  if (pv < dv) return { winner: 'dealer', reason: 'lower' };
  return { winner: 'push', reason: 'tie' };
}

/** 結算籌碼：勝出 1:1（贏得 bet、輸掉 bet、平手不變）。 */
export function payoutForBet(bet, winner) {
  if (winner === 'player') return bet;
  if (winner === 'dealer') return -bet;
  return 0;
}

export function outcomeText(compare) {
  if (!compare) return '';
  const { winner, reason } = compare;
  if (reason === 'player_bust') return '爆牌 · 你輸了';
  if (reason === 'dealer_bust') return '莊家爆牌 · 你贏了';
  if (reason === 'both_bust') return '雙方爆牌 · 平手';
  if (reason === 'player_natural') return '自然 21 點 · 你贏了';
  if (reason === 'dealer_natural') return '莊家自然 21 點 · 你輸了';
  if (reason === 'both_natural') return '雙方自然 21 點 · 平手';
  if (winner === 'player') return '你贏了';
  if (winner === 'dealer') return '你輸了';
  return '平手';
}

export function canPlaceBet(balance, amount) {
  const n = Number(amount);
  if (!Number.isFinite(n)) return false;
  return n >= MIN_BET && n <= balance;
}

export function readChipBalance(storage) {
  if (!storage || typeof storage.getItem !== 'function') return STARTING_BALANCE;
  const raw = storage.getItem(CHIP_STORAGE_KEY);
  if (raw == null || raw === '') return STARTING_BALANCE;
  const n = Number(raw);
  if (!Number.isFinite(n) || n < 0) return STARTING_BALANCE;
  return Math.floor(n);
}

export function writeChipBalance(storage, balance) {
  if (!storage || typeof storage.setItem !== 'function') return false;
  const n = Math.max(0, Math.floor(balance));
  storage.setItem(CHIP_STORAGE_KEY, String(n));
  return true;
}

export function applyPayout(balance, bet, winner) {
  return balance + payoutForBet(bet, winner);
}

/** 莊家依規則補牌至停牌；回傳新莊家手牌。 */
export function dealerPlay(dealer, deck, drawIndex) {
  const hand = dealer.slice();
  let idx = drawIndex;
  while (dealerShouldHit(hand)) {
    if (!deck[idx]) break;
    hand.push(deck[idx]);
    idx++;
  }
  return { hand, drawIndex: idx };
}

/**
 * 可測牌局狀態：betting → player → dealer → done
 */
export function createBlackjackRound(opts) {
  const rng = opts && opts.rng;
  let phase = 'betting';
  let deck = [];
  let drawIndex = 0;
  let player = [];
  let dealer = [];
  let bet = 0;
  let compare = null;
  let holeHidden = true;

  function dealFromDeck() {
    const card = deck[drawIndex];
    drawIndex++;
    return card;
  }

  function start(betAmount) {
    bet = Math.floor(betAmount);
    deck = shuffledDeck(rng);
    drawIndex = 0;
    player = [dealFromDeck(), dealFromDeck()];
    dealer = [dealFromDeck(), dealFromDeck()];
    compare = null;
    holeHidden = true;
    phase = 'player';
    if (isBust(player)) {
      compare = compareHands(player, dealer);
      phase = 'done';
      holeHidden = false;
    }
    return getState();
  }

  function hit() {
    if (phase !== 'player') return getState();
    player.push(dealFromDeck());
    if (isBust(player)) {
      compare = compareHands(player, dealer);
      phase = 'done';
      holeHidden = false;
    }
    return getState();
  }

  function stand() {
    if (phase !== 'player') return getState();
    holeHidden = false;
    const played = dealerPlay(dealer, deck, drawIndex);
    dealer = played.hand;
    drawIndex = played.drawIndex;
    compare = compareHands(player, dealer);
    phase = 'done';
    return getState();
  }

  function getState() {
    return {
      phase,
      bet,
      player: player.slice(),
      dealer: dealer.slice(),
      holeHidden,
      playerValue: handValue(player),
      dealerValue: holeHidden && dealer.length > 0
        ? handValue([dealer[0]])
        : handValue(dealer),
      compare,
      outcome: compare ? outcomeText(compare) : ''
    };
  }

  function reset() {
    phase = 'betting';
    deck = [];
    drawIndex = 0;
    player = [];
    dealer = [];
    bet = 0;
    compare = null;
    holeHidden = true;
    return getState();
  }

  return { start, hit, stand, reset, getState };
}