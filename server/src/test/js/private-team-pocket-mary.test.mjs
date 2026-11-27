import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const {
  MARY_PHASE,
  createMaryState,
  maryApplyBetButton,
  maryApplyDouble,
  maryBeginSpin,
  maryAdvanceSpin,
  maryCollectWin,
  maryComputeLineWin,
  maryDoubleOutcome,
  maryEnterDouble,
  marySymbolMult,
  maryTotalBet
} = await loadPublicModule('private-team-pocket-mary.js');

test('圖示倍率', () => {
  assert.equal(marySymbolMult(0), 2);
  assert.equal(marySymbolMult(7), 30);
  assert.equal(maryComputeLineWin([5, 0, 0, 0, 0, 0, 0, 0], 0), 10);
});

test('加注與總注', () => {
  let st = createMaryState(50);
  let out = maryApplyBetButton(st, 50, 'a');
  st = out.state;
  assert.equal(maryTotalBet(st), 1);
  out = maryApplyBetButton(st, 50, 'a');
  st = out.state;
  assert.equal(st.bets[0], 2);
  out = maryApplyBetButton(st, 50, 'b');
  st = out.state;
  assert.equal(st.bets[0], 1);
});

test('開始跑燈扣款', () => {
  let st = createMaryState(20);
  st.bets[0] = 5;
  const begin = maryBeginSpin(st, 20, 0, 1000);
  assert.equal(begin.ok, true);
  assert.equal(begin.credit, 15);
  assert.equal(begin.state.phase, MARY_PHASE.SPIN);
});

test('籌碼不足不能開轉', () => {
  let st = createMaryState(3);
  st.bets[0] = 5;
  const begin = maryBeginSpin(st, 3, 0, 0);
  assert.equal(begin.ok, false);
});

test('停燈結算中獎', () => {
  let st = createMaryState(30);
  st.bets[0] = 4;
  const begin = maryBeginSpin(st, 30, 0, 1000);
  st = begin.state;
  const endAt = 1000 + begin.state.spin.durationMs + 50;
  const adv = maryAdvanceSpin(st, endAt);
  assert.equal(adv.settled, true);
  assert.equal(adv.win, 8);
  assert.equal(adv.state.phase, MARY_PHASE.WIN);
  assert.equal(adv.state.pendingWin, 8);
});

test('收分入袋', () => {
  let st = createMaryState(10);
  st.phase = MARY_PHASE.WIN;
  st.pendingWin = 12;
  const out = maryCollectWin(st, 10);
  assert.equal(out.credit, 22);
  assert.equal(out.state.phase, MARY_PHASE.BET);
  assert.equal(out.state.pendingWin, 0);
});

test('比大小狀態機', () => {
  let st = createMaryState(0);
  st.phase = MARY_PHASE.WIN;
  st.pendingWin = 16;
  st = maryEnterDouble(st);
  assert.equal(st.phase, MARY_PHASE.DOUBLE);
  st = maryApplyBetButton(st, 0, 'left').state;
  assert.equal(st.doublePick, 'small');
  let out = maryApplyDouble(st, 'small', 3);
  assert.equal(out.state.pendingWin, 32);
  assert.equal(out.state.phase, MARY_PHASE.WIN);
  out = maryApplyDouble(maryEnterDouble(out.state), 'big', 7);
  assert.equal(out.state.pendingWin, 0);
  assert.equal(out.state.status, '莊7');
});

test('比大小 7 與大小區間', () => {
  assert.equal(maryDoubleOutcome(7, 'small').win, false);
  assert.equal(maryDoubleOutcome(4, 'small').win, true);
  assert.equal(maryDoubleOutcome(4, 'big').win, false);
  assert.equal(maryDoubleOutcome(10, 'big').win, true);
});

test('Select 清除與重押', () => {
  let st = createMaryState(40);
  st.bets[1] = 2;
  st.lastBets[1] = 3;
  let out = maryApplyBetButton(st, 40, 'select');
  assert.equal(maryTotalBet(out.state), 0);
  out = maryApplyBetButton(out.state, 40, 'select');
  assert.equal(out.state.bets[1], 3);
});

test('掌機模組接上小瑪莉', () => {
  const js = readFileSync(join(publicRoot, 'js/private-team-pocket.js'), 'utf8');
  assert.match(js, /private-team-pocket-mary\.js/);
  assert.match(js, /maryBeginSpin/);
});