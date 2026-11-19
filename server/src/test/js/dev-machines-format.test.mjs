import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const F = await loadPublicModule('dev-machines-format.js');

test('最後回報與已跑時間格式化', () => {
  const now = 10 * 60 * 60 * 1000;
  assert.equal(F.formatLastSeenAgo(null, now), '尚無回報');
  assert.equal(F.formatLastSeenAgo(now - 30 * 1000, now), '剛剛');
  assert.equal(F.formatLastSeenAgo(now - 5 * 60 * 1000, now), '5 分鐘前');
  assert.equal(F.formatLastSeenAgo(now - 2 * 60 * 60 * 1000, now), '2 小時前');
  assert.equal(F.formatRunDuration(0, now), '—');
  assert.equal(F.formatRunDuration(now - 45 * 60 * 1000, now), '已跑 45 分鐘');
  assert.equal(F.formatRunDuration(now - (2 * 60 + 20) * 60 * 1000, now), '已跑 2 小時 20 分');
});

test('工作狀態與研發樓兩行', () => {
  const idle = F.formatMachineWork({ online: true, issue: null }, null);
  assert.equal(idle.text, '閒置');
  const waiting = F.formatMachineWork({ online: true, issue: 172, agentRunning: false }, null);
  assert.equal(waiting.text, '等待 review・#172');
  const work = F.formatMachineWork(
    { online: true, issue: 126, issueTitle: '示範', agentRunning: true },
    'https://github.com/p4774756/PunchClock'
  );
  assert.match(work.text, /#126/);
  assert.ok(work.href.endsWith('/issues/126'));

  const lines = F.formatLabDevMachineLines({
    success: true,
    machines: [
      { label: '小貓', online: true, issue: 126, agentRunning: true },
      { label: '猛虎', online: false }
    ]
  });
  assert.deepEqual(lines, ['小貓 在線・跑 #126', '猛虎 離線']);

  const base = ['【開發進度】', '', '內容', '', '詳細進度請到主島施工告示牌'];
  const merged = F.appendLabMachineLines(base, lines);
  assert.ok(merged.includes('小貓 在線・跑 #126'));
  assert.ok(merged[merged.length - 1].includes('詳細進度'));
});

test('管理員才請求／顯示', () => {
  assert.equal(F.shouldFetchDevMachines(false), false);
  assert.equal(F.shouldFetchDevMachines(true), true);
  assert.equal(F.shouldShowDevStatusNavLink(false), false);
  assert.equal(F.shouldShowDevStatusNavLink(true), true);

  const doc = { createElement(tag) { return { tagName: tag, textContent: '', hidden: false, childNodes: [], appendChild() {}, replaceChildren() {} }; } };
  const root = doc.createElement('p');
  root.firstChild = null;
  root.removeChild = () => {};
  F.mergeRoadmapDevStatusLink(doc, root, false);
  assert.equal(root.hidden, true);

  F.mergeRoadmapDevStatusLink(doc, root, true);
  assert.equal(root.hidden, false);
});

test('401 時不產生研發樓行', () => {
  assert.deepEqual(F.formatLabDevMachineLines({ success: false }), []);
  assert.deepEqual(F.formatLabDevMachineLines(null), []);
});

test('effectivePhaseStatus：0/5 pending、3/5 progress、5/5 done、current 0/5 active', () => {
  const phase = (n, done, total, extra) => Object.assign({ phase: n, done: false, itemsDone: done, itemsTotal: total }, extra);
  assert.equal(F.effectivePhaseStatus(phase(6, 0, 5), 7), 'pending');
  assert.equal(F.effectivePhaseStatus(phase(6, 3, 5), 7), 'progress');
  assert.equal(F.effectivePhaseStatus(phase(6, 5, 5), 7), 'done');
  assert.equal(F.effectivePhaseStatus(phase(7, 0, 5), 7), 'active');
  assert.equal(F.effectivePhaseStatus({ phase: 5, done: true, itemsDone: 2, itemsTotal: 5 }, null), 'done');
});

test('今日合併篩選接受 API 合併與已合併', () => {
  const now = Date.UTC(2026, 10, 19, 8, 6, 0);
  const merged = F.filterMergedToday(
    [
      { action: '合併', at: Date.UTC(2026, 10, 19, 8, 6, 0), ref: '#205' },
      { action: '已合併', at: Date.UTC(2026, 10, 19, 7, 55, 0), ref: '#200' },
      { action: '合併', at: Date.UTC(2026, 10, 18, 14, 0, 0), ref: '#old' },
      { action: '審核中', at: Date.UTC(2026, 10, 19, 8, 0, 0) }
    ],
    now
  );
  assert.equal(merged.length, 2);
  assert.equal(merged[0].ref, '#205');
  assert.equal(merged[1].ref, '#200');
});