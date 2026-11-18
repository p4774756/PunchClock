import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const H = await loadPublicModule('island-lab-health.js');

test('位元組與百分比格式化', () => {
  assert.equal(H.formatTeaBytes(500), '500 B');
  assert.equal(H.formatTeaBytes(1536), '1.5 KB');
  assert.equal(H.formatTeaBytes(1288490188), '1.2 GB');
  assert.equal(H.formatTeaBytes(4294967296), '4.0 GB');
  assert.equal(H.formatTeaBytes(null), '取樣中');
  assert.equal(H.formatTeaPercent(12.34), '12.3%');
  assert.equal(H.formatTeaPercent(null), '取樣中');
  assert.equal(H.formatTeaPercent(Number.NaN), '取樣中');
});

test('製茶機行文案不含 NaN', () => {
  const sample = {
    success: true,
    processCpuPercent: 8.2,
    systemCpuPercent: 14.5,
    osUsedMemoryBytes: 1288490188,
    osTotalMemoryBytes: 4294967296,
    heapUsedBytes: 100,
    heapMaxBytes: 200
  };
  assert.match(H.formatTeaCpuLine(sample), /8\.2%/);
  assert.match(H.formatTeaMemoryLine(sample), /1\.2 GB \/ 4\.0 GB/);
  const vm = H.teaHealthViewModel(sample);
  assert.equal(vm.ok, true);
  assert.ok(vm.cpuBar != null && vm.cpuBar >= 0 && vm.cpuBar <= 1);
  assert.ok(vm.memBar != null && vm.memBar > 0 && vm.memBar < 1);
  assert.equal(H.formatTeaCpuLine({ success: false }), '製茶機狀態暫時讀不到');
});

test('時間軸排序截斷與空陣列', () => {
  const events = [
    { at: 1000, action: '開了 issue', ref: '#1', title: '早期' },
    { at: 3000, action: '合併', ref: 'p2', title: '較新' },
    { at: 2000, action: '開了 PR', ref: 'p1', title: '中間' }
  ];
  const lines = H.formatLabTimelineScreenLines(events, { maxEvents: 2, titleMax: 10 });
  assert.equal(lines.length, 2);
  assert.ok(lines[0].includes('合併'));
  assert.ok(lines[0].includes('較新'));
  assert.deepEqual(H.formatLabTimelineScreenLines([]), ['近 7 天沒有動態']);
});

test('開發進度與時間軸合併', () => {
  const data = {
    success: true,
    stale: false,
    now: {
      current: { pair: 'i141', title: '數據牆', status: '實作中' },
      next: null,
      queue: []
    },
    stats: { mergedThisWeek: 1, openIssues: 2 },
    timeline: [
      { at: Date.UTC(2026, 10, 17, 6, 30, 0), action: '開了 issue', ref: '#141', title: '研發樓牆' }
    ]
  };
  const lines = H.formatLabDevScreenWithTimeline(data, { maxQueue: 2, maxTimeline: 5 });
  assert.ok(lines.join('\n').includes('現在在做'));
  assert.ok(lines.join('\n').includes('【最近動態】'));
  assert.ok(lines.some((line) => line.includes('開了 issue') && line.includes('#141')));
  assert.ok(lines.some((line) => line.includes('台北')));

  const fail = H.formatLabDevScreenWithTimeline({ success: false, message: '開發進度暫時讀不到' });
  assert.ok(fail.join('\n').includes('開發進度暫時讀不到'));
  assert.ok(!fail.join('\n').includes('【最近動態】'));
});