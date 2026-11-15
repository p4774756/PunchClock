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
