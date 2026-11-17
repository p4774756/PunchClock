import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const R = await loadPublicModule('island-roadmap.js');

test('告示牌寫出目前期數，全部完成則寫群島完工', () => {
  const building = R.signCopy({
    current: 4,
    phases: [
      { phase: 3, title: '群島地基', done: true },
      { phase: 4, title: '進屋系統', done: false }
    ]
  });
  assert.equal(building.headline, '第 4 期施工中');
  assert.equal(building.title, '進屋系統');

  const done = R.signCopy({
    current: null,
    phases: [
      { phase: 3, title: '群島地基', done: true },
      { phase: null, title: '穿插', done: false }
    ]
  });
  assert.equal(done.headline, '群島完工');
  assert.equal(R.phaseStatus({ phase: null, done: false }, null), 'pending');
  assert.equal(R.phaseStatus({ phase: 4, done: false }, 4), 'active');
  assert.equal(R.phaseStatus({ phase: 3, done: true }, 4), 'done');
});

test('開發進度篩選與失敗文案', () => {
  const items = [
    { pair: 'i1', status: '排隊' },
    { pair: 'i2', status: '實作中' },
    { pair: 'i3', status: '審核中' },
    { pair: 'i4', status: '已合併' },
    { pair: 'i36', status: '路線圖' }
  ];
  assert.equal(R.filterDevItems(items, '全部').length, 5);
  assert.deepEqual(R.filterDevItems(items, '排隊').map((r) => r.pair), ['i1']);
  assert.deepEqual(R.filterDevItems(items, '審核中').map((r) => r.pair), ['i3']);
  assert.equal(R.emptyDevStatusMessage({ message: '暫時讀不到開發進度' }), '暫時讀不到開發進度');
  assert.equal(R.emptyDevStatusMessage({ stale: true }), '顯示的是上次讀到的開發進度');
  assert.ok(R.DEV_STATUS_FILTERS.includes('排隊'));
  assert.ok(!R.DEV_STATUS_FILTERS.join('').includes('Oki'));
  assert.ok(!R.DEV_STATUS_FILTERS.join('').includes('Cursor'));
});

test('研發樓螢幕開發進度文案', () => {
  const sample = {
    success: true,
    stale: false,
    now: {
      current: { pair: 'i101', title: '研發樓室內', status: '實作中' },
      next: { pair: 'i102', title: '下一張', status: '排隊' },
      queue: [
        { order: 1, pair: 'i10', title: 'A', status: '排隊' },
        { order: 2, pair: 'i11', title: 'B', status: '排隊' }
      ]
    },
    stats: { mergedThisWeek: 3, openIssues: 12 }
  };
  const lines = R.formatLabDevScreenLines(sample, { maxQueue: 3 });
  assert.ok(lines.join('\n').includes('現在在做：i101 研發樓室內'));
  assert.ok(lines.join('\n').includes('下一張：i102 下一張'));
  assert.ok(lines.join('\n').includes('i10 A'));
  assert.ok(lines.join('\n').includes('本週合併 3'));
  assert.ok(lines.join('\n').includes('施工告示牌'));

  const fail = R.formatLabDevScreenLines({ success: false, message: '開發進度暫時讀不到' });
  assert.ok(fail.join('\n').includes('開發進度暫時讀不到'));
  const empty = R.formatLabDevScreenLines({ success: true, now: { current: null, next: null, queue: [] } });
  assert.ok(empty.join('\n').includes('開發進度暫時讀不到'));
});

test('台北時間格式含台北標籤', () => {
  const text = R.formatTaipeiTime(Date.UTC(2026, 10, 17, 6, 30, 0));
  assert.match(text, /2026\/11\/17/);
  assert.match(text, /14:30/);
  assert.match(text, /台北$/);
  assert.equal(R.formatTaipeiTime(null), '');
  assert.equal(R.formatTaipeiTime(0), '');
});
