import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const Roadmap = await loadPublicModule('roadmap-panel.js');

test('施工進度清單用圓點狀態標記而非假核取方塊', () => {
  const js = readFileSync(join(publicRoot, 'js/roadmap-panel.js'), 'utf8');
  const css = readFileSync(join(publicRoot, 'css/roadmap.css'), 'utf8');
  assert.match(js, /roadmap-state/);
  assert.doesNotMatch(js, /roadmap-check/);
  assert.match(js, /里程碑子項/);
  assert.match(css, /\.roadmap-state/);
  assert.doesNotMatch(css, /\.roadmap-check/);
});

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