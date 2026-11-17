// 單人 21 點純邏輯：node --test server/src/test/js/island-blackjack.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const B = await loadPublicModule('island-blackjack.js');

test('handValue／isBust 對齊 Java：A+10=21、A+A+9=21、A+9+9=19、三張 K 爆牌', () => {
  const aceSpades = 0;
  const tenHearts = 22;
  assert.equal(B.handValue([aceSpades, tenHearts]), 21);

  const aceHearts = 13;
  const nineDiamonds = 34;
  assert.equal(B.handValue([aceSpades, aceHearts, nineDiamonds]), 21);

  const nineHearts = 21;
  assert.equal(B.handValue([aceSpades, nineDiamonds, nineHearts]), 19);

  const kingSpades = 12;
  const kingHearts = 25;
  const kingDiamonds = 38;
  assert.equal(B.handValue([kingSpades, kingHearts, kingDiamonds]), 30);
  assert.equal(B.isBust([kingSpades, kingHearts, kingDiamonds]), true);
});

test('莊家 16 要牌、17 與軟 17 停牌', () => {
  const cards16 = [7, 20];
  assert.equal(B.handValue(cards16), 16);
  assert.equal(B.dealerShouldHit(cards16), true);

  const soft17 = [0, 5];
  assert.equal(B.handValue(soft17), 17);
  assert.equal(B.dealerShouldHit(soft17), false);

  const hard17 = [6, 9];
  assert.equal(B.handValue(hard17), 17);
  assert.equal(B.dealerShouldHit(hard17), false);
});

test('結算：玩家爆、莊家爆、點數高低、雙方自然 21、一方自然 21', () => {
  const bust = [12, 25, 38];
  const ok = [9, 10];
  assert.deepEqual(B.compareHands(bust, ok), { winner: 'dealer', reason: 'player_bust' });
  assert.deepEqual(B.compareHands(ok, bust), { winner: 'player', reason: 'dealer_bust' });
  assert.deepEqual(B.compareHands(bust, bust), { winner: 'push', reason: 'both_bust' });

  const low = [5, 6];
  const high = [9, 10];
  assert.deepEqual(B.compareHands(low, high), { winner: 'dealer', reason: 'lower' });
  assert.deepEqual(B.compareHands(high, low), { winner: 'player', reason: 'higher' });

  const ten = 22;
  const natPlayer = [0, ten];
  const natDealer = [13, 25];
  assert.equal(B.isNatural21(natPlayer), true);
  assert.equal(B.isNatural21(natDealer), true);
  assert.deepEqual(B.compareHands(natPlayer, natDealer), { winner: 'push', reason: 'both_natural' });

  const notNat21 = [0, 5, 6];
  assert.deepEqual(B.compareHands(natPlayer, notNat21), { winner: 'player', reason: 'player_natural' });
  assert.deepEqual(B.compareHands(notNat21, natPlayer), { winner: 'dealer', reason: 'dealer_natural' });
});

test('籌碼：起始額、贏加輸減、不能下超過餘額、可序列化', () => {
  const mem = new Map();
  const storage = {
    getItem: (k) => (mem.has(k) ? mem.get(k) : null),
    setItem: (k, v) => { mem.set(k, v); }
  };
  assert.equal(B.readChipBalance(storage), B.STARTING_BALANCE);
  B.writeChipBalance(storage, 880);
  assert.equal(B.readChipBalance(storage), 880);

  assert.equal(B.canPlaceBet(100, 50), true);
  assert.equal(B.canPlaceBet(100, 100), true);
  assert.equal(B.canPlaceBet(100, 101), false);
  assert.equal(B.canPlaceBet(5, 10), false);

  assert.equal(B.placeBet(1000, 50), 950);
  assert.equal(B.placeBet(5, 10), null);
  assert.equal(B.settleBet(950, 50, 'player'), 1050);
  assert.equal(B.settleBet(950, 50, 'dealer'), 950);
  assert.equal(B.settleBet(950, 50, 'push'), 1000);

  let mid = B.placeBet(100, 20);
  assert.equal(mid, 80);
  assert.equal(B.settleBet(mid, 20, 'dealer'), 80, '中途放棄不退注');

  assert.equal(B.needsPracticeChipRefill(9), true);
  assert.equal(B.needsPracticeChipRefill(10), false);
  B.writeChipBalance(storage, B.STARTING_BALANCE);
  assert.equal(B.readChipBalance(storage), B.STARTING_BALANCE);
  assert.equal(B.CHIP_STORAGE_KEY, 'punchclock.casino.practiceChips');
});

function paddedDeck(indices) {
  const deck = indices.slice();
  for (let i = 0; deck.length < B.DECK_SIZE; i++) deck.push(i % 13 + Math.floor(i / 13) * 13);
  return deck;
}

test('自然 21：發牌後立即結算，莊家不補牌', () => {
  const ten = 22;
  const roundPlayerNat = B.createBlackjackRound({ deck: paddedDeck([0, ten, 5, 6]) });
  roundPlayerNat.start(10);
  let st = roundPlayerNat.getState();
  assert.equal(st.phase, 'done');
  assert.equal(st.compare.winner, 'player');
  assert.equal(st.holeHidden, false);

  const roundDealerNat = B.createBlackjackRound({ deck: paddedDeck([5, 6, 0, ten]) });
  roundDealerNat.start(10);
  st = roundDealerNat.getState();
  assert.equal(st.phase, 'done');
  assert.equal(st.compare.winner, 'dealer');

  const roundBothNat = B.createBlackjackRound({ deck: paddedDeck([0, ten, 13, 25]) });
  roundBothNat.start(10);
  st = roundBothNat.getState();
  assert.equal(st.phase, 'done');
  assert.equal(st.compare.winner, 'push');
  assert.equal(st.compare.reason, 'both_natural');
});

test('dealerPlay：未達 17 持續要牌', () => {
  const deck = [5, 6, 7, 8, 9, 10, 11, 12];
  const dealer = [5, 6];
  const out = B.dealerPlay(dealer, deck, 0);
  assert.ok(B.handValue(out.hand) >= 17);
  assert.equal(B.dealerShouldHit(out.hand), false);
});