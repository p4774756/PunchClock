import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const Roadmap = await loadPublicModule('roadmap-panel.js');

test('formatLabDevScreenLines 可列多筆現在在做', () => {
  const lines = Roadmap.formatLabDevScreenLines({
    success: true,
    now: {
      current: [
        { pair: 'i10', title: 'A', status: '實作中' },
        { pair: 'i20（p200）', title: 'B', status: '審核中' }
      ],
      next: { pair: 'i30', title: 'C', status: '排隊' },
      queue: [{ order: 3, pair: 'i30', title: 'C', status: '排隊' }]
    },
    stats: { mergedThisWeek: 1, openIssues: 3 }
  });
  const joined = lines.join('\n');
  assert.match(joined, /現在在做/);
  assert.match(joined, /i10/);
  assert.match(joined, /i20/);
  assert.match(joined, /下一張：i30/);
  assert.doesNotMatch(joined, /進度表/);
});

test('formatLabDevScreenLines 相容舊版單一 current 物件', () => {
  const lines = Roadmap.formatLabDevScreenLines({
    success: true,
    now: {
      current: { pair: 'i40', title: 'D', status: '實作中' },
      next: null,
      queue: []
    },
    stats: {}
  });
  assert.ok(lines.some((line) => line.includes('i40')));
});