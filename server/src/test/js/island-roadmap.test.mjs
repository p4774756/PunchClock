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

test('研發樓螢幕長標題保留 pair 與部分標題', () => {
  const longNext = '下一張：i104（p111）經絡研習室換裝與練習裝配色調整';
  const sample = {
    success: true,
    stale: false,
    now: {
      current: { pair: 'i112', title: '出口導覽', status: '實作中' },
      next: { pair: 'i104', title: '經絡研習室換裝與練習裝配色調整', status: '排隊' },
      queue: []
    },
    stats: { mergedThisWeek: 0, openIssues: 0 }
  };
  const lines = R.formatLabDevScreenLines(sample, { maxQueue: 3 });
  const nextLine = lines.find((line) => String(line).startsWith('下一張：'));
  assert.ok(nextLine.includes('i104'), '下一張行要保留 issue pair');
  assert.ok(nextLine.includes('經絡研習室'), '下一張行要保留標題前段');

  const widths = new Map();
  const mockCtx = {
    measureText(text) {
      if (!widths.has(text)) {
        let w = 0;
        for (const ch of text) w += ch.charCodeAt(0) > 255 ? 21 : 12;
        widths.set(text, w);
      }
      return { width: widths.get(text) };
    }
  };
  const fitted = R.fitCanvasLine(mockCtx, longNext, 520);
  assert.ok(fitted.startsWith('下一張：i104'), 'Canvas 截斷不能把 pair 整段砍掉');
  assert.ok(fitted.includes('經絡'), 'Canvas 截斷要留標題字元');
  assert.ok(fitted.endsWith('…'), '太長時要有省略號');
});

test('子項進度與今日合併', () => {
  const phase = {
    done: false,
    phase: 5,
    itemsDone: 1,
    itemsTotal: 4,
    items: [{ text: '21點', done: true }, { text: '拉霸機', done: false }]
  };
  assert.equal(R.effectivePhaseStatus(phase, 5), 'active');
  assert.deepEqual(R.phaseItemProgress(phase), { done: 1, total: 4 });
  const group = { done: false, items: [{ text: 'A', done: true }, { text: 'B', done: true }] };
  assert.equal(R.effectivePhaseStatus(group, null), 'done');
  const dayStart = R.filterMergedToday([
    { action: '已合併', at: Date.now() - 1000, ref: '#1' },
    { action: '審核中', at: Date.now() - 1000 },
    { action: '已合併', at: 0, ref: '#2' }
  ], Date.now());
  assert.equal(dayStart.length, 1);
});

test('台北時間格式含台北標籤', () => {
  const text = R.formatTaipeiTime(Date.UTC(2026, 10, 17, 6, 30, 0));
  assert.match(text, /2026\/11\/17/);
  assert.match(text, /14:30/);
  assert.match(text, /台北$/);
  assert.equal(R.formatTaipeiTime(null), '');
  assert.equal(R.formatTaipeiTime(0), '');
});
