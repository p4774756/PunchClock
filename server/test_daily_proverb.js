const assert = require('assert');
const {
  getDailyProverb,
  dateKeyInTaipei,
  hashDay,
  PROVERBS
} = require('./lib/dailyProverb');

console.log('🔍 [本機自動測試] 正在檢驗每日六人行台詞模組...');

assert.ok(PROVERBS.length >= 30, '台詞數量應至少 30 則');

const a = getDailyProverb(new Date('2026-08-26T04:00:00+08:00'));
const b = getDailyProverb(new Date('2026-08-26T23:59:00+08:00'));
assert.strictEqual(a.date, '2026-08-26');
assert.strictEqual(a.en, b.en, '同一台北日期應回傳同一則台詞');
assert.strictEqual(a.zh, b.zh);
assert.ok(typeof a.en === 'string' && a.en.length > 0);
assert.ok(typeof a.zh === 'string' && a.zh.length > 0);
assert.ok(typeof a.context === 'string' && a.context.length > 0, '每則台詞應含情境說明');
assert.match(a.context, /^S\d+E\d+ · /, '情境應含 S#E# 標記');

for (const item of PROVERBS) {
  assert.ok(item.context && /^S\d+E\d+ · /.test(item.context), '台詞庫每則需有 context');
}

const next = getDailyProverb(new Date('2026-08-27T12:00:00+08:00'));
assert.strictEqual(next.date, '2026-08-27');

const utcNearMidnight = getDailyProverb(new Date('2026-08-25T16:30:00Z')); // 台北已是 8/26 00:30
assert.strictEqual(utcNearMidnight.date, '2026-08-26', '應以 Asia/Taipei 日界為準');

assert.strictEqual(dateKeyInTaipei(new Date('2026-01-02T00:00:00+08:00')), '2026-01-02');
assert.strictEqual(typeof hashDay('2026-08-26'), 'number');
assert.ok(hashDay('2026-08-26') >= 0);

console.log(`✅ 今日範例 (${a.date}): ${a.en}`);
console.log(`   情境: ${a.context}`);
console.log(`✅ 台詞庫共 ${PROVERBS.length} 則，同日穩定、跨日可換`);
console.log('🎉 [100% SUCCESS] dailyProverb 驗證通過！');
