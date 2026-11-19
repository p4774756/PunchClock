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