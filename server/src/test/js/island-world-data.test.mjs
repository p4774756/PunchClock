// node --test server/src/test/js/island-world-data.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const W = await loadPublicModule('island-world-data.js');

test('2026 中秋、2027 春節、冬至', () => {
  assert.equal(W.festivalOnDate('2026-09-25'), 'mid-autumn');
  assert.equal(W.festivalOnDate('2027-02-06'), 'new-year');
  assert.equal(W.festivalOnDate('2026-12-22'), 'winter-solstice');
});

test('節氣查詢', () => {
  assert.equal(W.solarTermOnDate('2030-12-22'), '冬至');
  assert.equal(W.solarTermOnDate('2028-06-21'), '夏至');
});

test('Asia/Taipei 跨日：UTC 16:00 前後', () => {
  const before = Date.parse('2026-09-24T15:59:00Z');
  const after = Date.parse('2026-09-24T16:01:00Z');
  assert.equal(W.taipeiDateIso(before), '2026-09-24');
  assert.equal(W.taipeiDateIso(after), '2026-09-25');
});

test('?festival= 覆寫', () => {
  const ms = Date.parse('2026-06-01T00:00:00Z');
  assert.equal(W.resolveFestivalId(ms, 'mid-autumn'), 'mid-autumn');
});