/** 小瑪莉：24 格外圈、押注結算與比大小（可單測的純邏輯）。 */

export const POCKET_MARY_RING_LEN = 24;
export const POCKET_MARY_SYMBOL_COUNT = 8;

export const POCKET_MARY_SYMBOLS = [
  { id: 0, short: '蘋', mult: 2 },
  { id: 1, short: '橘', mult: 3 },
  { id: 2, short: '木', mult: 5 },
  { id: 3, short: '鈴', mult: 8 },
  { id: 4, short: '瓜', mult: 10 },
  { id: 5, short: '星', mult: 15 },
  { id: 6, short: '77', mult: 20 },
  { id: 7, short: 'BR', mult: 30 }
];

/** 外圈 24 格：sym / lucky / again */
export const POCKET_MARY_RING = [
  { t: 'sym', id: 0 },
  { t: 'sym', id: 1 },
  { t: 'lucky' },
  { t: 'sym', id: 2 },
  { t: 'sym', id: 3 },
  { t: 'again' },
  { t: 'sym', id: 4 },
  { t: 'sym', id: 5 },
  { t: 'sym', id: 0 },
  { t: 'sym', id: 6 },
  { t: 'sym', id: 7 },
  { t: 'sym', id: 1 },
  { t: 'sym', id: 2 },
  { t: 'sym', id: 3 },
  { t: 'sym', id: 4 },
  { t: 'sym', id: 5 },
  { t: 'sym', id: 0 },
  { t: 'sym', id: 6 },
  { t: 'sym', id: 7 },
  { t: 'sym', id: 1 },
  { t: 'sym', id: 2 },
  { t: 'sym', id: 3 },
  { t: 'sym', id: 4 },
  { t: 'again' }
];

export const MARY_PHASE = {
  BET: 'bet',
  SPIN: 'spin',
  WIN: 'win',
  DOUBLE: 'double'
};

function emptyBets() {
  const b = [];
  for (let i = 0; i < POCKET_MARY_SYMBOL_COUNT; i++) b.push(0);
  return b;
}

export function createMaryState(credit) {
  const c = Math.max(0, Math.floor(Number(credit)));
  return {
    phase: MARY_PHASE.BET,
    credit: c,
    cursor: 0,
    bets: emptyBets(),
    lastBets: emptyBets(),
    pendingWin: 0,
    stopIndex: 0,
    litIndex: 0,
    spin: null,
    flash: 0,
    doublePick: '',
    doubleRoll: 0,
    status: '押注'
  };
}

export function marySymbolMult(symbolId) {
  const id = Math.floor(Number(symbolId));
  const row = POCKET_MARY_SYMBOLS[id];
  return row ? row.mult : 0;
}

export function maryTotalBet(state) {
  if (!state || !state.bets) return 0;
  let sum = 0;
  for (let i = 0; i < state.bets.length; i++) sum += Math.max(0, Math.floor(state.bets[i]));
  return sum;
}

export function maryRingCell(index) {
  const i = ((Math.floor(Number(index)) % POCKET_MARY_RING_LEN) + POCKET_MARY_RING_LEN) % POCKET_MARY_RING_LEN;
  return POCKET_MARY_RING[i];
}

export function maryComputeLineWin(bets, stopIndex) {
  const cell = maryRingCell(stopIndex);
  if (!cell || cell.t !== 'sym') return 0;
  const bet = Math.max(0, Math.floor(bets[cell.id] || 0));
  if (bet <= 0) return 0;
  return bet * marySymbolMult(cell.id);
}

export function maryLuckyBonus(totalBet) {
  const base = Math.max(0, Math.floor(totalBet));
  return base > 0 ? Math.max(5, Math.min(30, base)) : 5;
}

export function maryPlanSpinSteps(fromIndex, toIndex, minLaps) {
  const len = POCKET_MARY_RING_LEN;
  const from = ((fromIndex % len) + len) % len;
  const to = ((toIndex % len) + len) % len;
  const laps = Math.max(1, Math.floor(minLaps));
  let steps = laps * len + ((to - from + len) % len);
  if (steps < len) steps += len;
  return steps;
}

/** 跑燈時序：加速 → 巡航 → 減速（回傳目前亮起格與是否結束）。 */
export function marySpinTick(spin, elapsedMs) {
  if (!spin || spin.done) return { lit: spin ? spin.stopIndex : 0, done: true };
  const t = Math.max(0, Number(elapsedMs));
  const total = Math.max(1, spin.durationMs);
  const steps = Math.max(1, spin.stepCount);
  let progress;
  if (t <= total * 0.2) {
    const p = t / (total * 0.2);
    progress = p * p * steps * 0.25;
  } else if (t <= total * 0.65) {
    const p = (t - total * 0.2) / (total * 0.45);
    progress = steps * 0.25 + p * steps * 0.45;
  } else {
    const p = (t - total * 0.65) / (total * 0.35);
    const eased = 1 - (1 - p) * (1 - p);
    progress = steps * 0.7 + eased * steps * 0.3;
  }
  const step = Math.min(steps, Math.floor(progress));
  const lit = (spin.fromIndex + step) % POCKET_MARY_RING_LEN;
  const done = t >= total;
  return { lit, done };
}

export function maryDoubleRollValue(rng) {
  const fn = rng && typeof rng === 'function' ? rng : Math.random;
  return 1 + Math.floor(fn() * 13);
}

/** pick: 'small' | 'big'；7 莊吃。 */
export function maryDoubleOutcome(roll, pick) {
  const r = Math.floor(Number(roll));
  const p = pick === 'big' ? 'big' : 'small';
  if (r === 7) return { win: false, reason: 'seven' };
  if (r >= 1 && r <= 6) return { win: p === 'small', reason: 'small' };
  if (r >= 8 && r <= 13) return { win: p === 'big', reason: 'big' };
  return { win: false, reason: 'invalid' };
}

function cloneBets(bets) {
  return (bets || []).map((n) => Math.max(0, Math.floor(Number(n))));
}

function copyState(state) {
  return {
    phase: state.phase,
    credit: state.credit,
    cursor: state.cursor,
    bets: cloneBets(state.bets),
    lastBets: cloneBets(state.lastBets),
    pendingWin: state.pendingWin,
    stopIndex: state.stopIndex,
    litIndex: state.litIndex,
    spin: state.spin ? { ...state.spin } : null,
    flash: state.flash,
    doublePick: state.doublePick,
    doubleRoll: state.doubleRoll,
    status: state.status
  };
}

export function maryBeginSpin(state, credit, stopIndex, nowMs) {
  const total = maryTotalBet(state);
  if (!state || state.phase !== MARY_PHASE.BET || total <= 0) {
    return { state: copyState(state), credit, ok: false };
  }
  const c = Math.max(0, Math.floor(Number(credit)));
  if (c < total) return { state: copyState(state), credit: c, ok: false };
  const next = copyState(state);
  next.lastBets = cloneBets(next.bets);
  next.credit = c - total;
  next.phase = MARY_PHASE.SPIN;
  next.pendingWin = 0;
  next.flash = 0;
  const stop = ((Math.floor(stopIndex) % POCKET_MARY_RING_LEN) + POCKET_MARY_RING_LEN) % POCKET_MARY_RING_LEN;
  const steps = maryPlanSpinSteps(next.litIndex, stop, 2);
  next.spin = {
    fromIndex: next.litIndex,
    stopIndex: stop,
    stepCount: steps,
    durationMs: 1800 + steps * 42,
    startedAt: Number(nowMs) || 0,
    done: false
  };
  next.status = '跑燈';
  return { state: next, credit: next.credit, ok: true };
}

export function maryAdvanceSpin(state, nowMs) {
  if (!state || state.phase !== MARY_PHASE.SPIN || !state.spin) {
    return { state: copyState(state), settled: false };
  }
  const elapsed = Math.max(0, Number(nowMs) - state.spin.startedAt);
  const tick = marySpinTick(state.spin, elapsed);
  const next = copyState(state);
  next.litIndex = tick.lit;
  if (!tick.done) return { state: next, settled: false };
  next.spin.done = true;
  next.stopIndex = next.spin.stopIndex;
  next.litIndex = next.stopIndex;
  return marySettleStop(next, nowMs);
}

function marySettleStop(state, nowMs) {
  const next = copyState(state);
  const cell = maryRingCell(next.stopIndex);
  const totalBet = maryTotalBet(next);
  if (cell.t === 'again') {
    next.status = '再轉';
    next.phase = MARY_PHASE.SPIN;
    const stop = next.stopIndex;
    const steps = maryPlanSpinSteps(next.litIndex, (stop + 5) % POCKET_MARY_RING_LEN, 1);
    next.spin = {
      fromIndex: next.litIndex,
      stopIndex: (stop + 5) % POCKET_MARY_RING_LEN,
      stepCount: steps,
      durationMs: 1200 + steps * 38,
      startedAt: Number(nowMs) || 0,
      done: false
    };
    return { state: next, settled: false, again: true };
  }
  let win = 0;
  if (cell.t === 'sym') {
    win = maryComputeLineWin(next.bets, next.stopIndex);
  } else if (cell.t === 'lucky') {
    win = maryLuckyBonus(totalBet);
  }
  next.pendingWin = win;
  next.phase = win > 0 ? MARY_PHASE.WIN : MARY_PHASE.BET;
  next.spin = null;
  next.flash = win > 0 ? 1 : 0;
  next.status = win > 0 ? '中獎' : '未中';
  if (win <= 0) next.bets = emptyBets();
  return { state: next, settled: true, win };
}

export function maryCollectWin(state, credit) {
  const next = copyState(state);
  const win = Math.max(0, Math.floor(next.pendingWin));
  let c = Math.max(0, Math.floor(Number(credit)));
  if (win > 0) c += win;
  next.pendingWin = 0;
  next.phase = MARY_PHASE.BET;
  next.flash = 0;
  next.doublePick = '';
  next.doubleRoll = 0;
  next.bets = emptyBets();
  next.status = '押注';
  next.credit = c;
  return { state: next, credit: c };
}

export function maryEnterDouble(state) {
  if (!state || state.phase !== MARY_PHASE.WIN || state.pendingWin <= 0) {
    return copyState(state);
  }
  const next = copyState(state);
  next.phase = MARY_PHASE.DOUBLE;
  next.doublePick = '';
  next.doubleRoll = 0;
  next.status = '比大小';
  return next;
}

export function maryApplyDouble(state, pick, roll) {
  const next = copyState(state);
  if (next.phase !== MARY_PHASE.DOUBLE || next.pendingWin <= 0) return { state: next, credit: next.credit };
  const outcome = maryDoubleOutcome(roll, pick);
  next.doublePick = pick;
  next.doubleRoll = roll;
  if (outcome.win) {
    next.pendingWin *= 2;
    next.phase = MARY_PHASE.WIN;
    next.status = '猜中';
    next.flash = 2;
  } else {
    next.pendingWin = 0;
    next.phase = MARY_PHASE.BET;
    next.bets = emptyBets();
    next.status = roll === 7 ? '莊7' : '猜錯';
    next.flash = 0;
  }
  return { state: next, credit: next.credit };
}

export function maryApplyBetButton(state, credit, btn) {
  const name = btn == null ? '' : String(btn);
  let next = copyState(state);
  let c = Math.max(0, Math.floor(Number(credit)));

  if (next.phase === MARY_PHASE.SPIN) {
    return { state: next, credit: c };
  }

  if (next.phase === MARY_PHASE.WIN) {
    if (name === 'a') {
      const out = maryCollectWin(next, c);
      return { state: out.state, credit: out.credit };
    }
    if (name === 'b') return { state: maryEnterDouble(next), credit: c };
    return { state: next, credit: c };
  }

  if (next.phase === MARY_PHASE.DOUBLE) {
    if (name === 'b') {
      const out = maryCollectWin(next, c);
      return { state: out.state, credit: out.credit };
    }
    if (name === 'left' || name === 'right') {
      next.doublePick = name === 'left' ? 'small' : 'big';
      next.status = name === 'left' ? '押小' : '押大';
      return { state: next, credit: c };
    }
    if (name === 'a' && next.doublePick) {
      const roll = maryDoubleRollValue();
      return maryApplyDouble(next, next.doublePick, roll);
    }
    return { state: next, credit: c };
  }

  if (name === 'left') {
    next.cursor = (next.cursor - 1 + POCKET_MARY_SYMBOL_COUNT) % POCKET_MARY_SYMBOL_COUNT;
    next.status = '選注';
    return { state: next, credit: c };
  }
  if (name === 'right') {
    next.cursor = (next.cursor + 1) % POCKET_MARY_SYMBOL_COUNT;
    next.status = '選注';
    return { state: next, credit: c };
  }
  if (name === 'a') {
    const cur = next.cursor;
    const total = maryTotalBet(next);
    if (c > total) {
      next.bets[cur] = Math.max(0, Math.floor(next.bets[cur])) + 1;
      next.status = '加注';
    }
    return { state: next, credit: c };
  }
  if (name === 'b') {
    const cur = next.cursor;
    if (next.bets[cur] > 0) {
      next.bets[cur] -= 1;
      next.status = '減注';
    } else {
      next.bets = emptyBets();
      next.status = '清除';
    }
    return { state: next, credit: c };
  }
  if (name === 'select') {
    if (maryTotalBet(next) > 0) {
      next.bets = emptyBets();
      next.status = '清押';
    } else {
      next.bets = cloneBets(next.lastBets);
      next.status = '重押';
    }
    return { state: next, credit: c };
  }
  return { state: next, credit: c };
}

export function maryPreviewState() {
  const st = createMaryState(100);
  st.bets[0] = 2;
  st.bets[3] = 1;
  return st;
}