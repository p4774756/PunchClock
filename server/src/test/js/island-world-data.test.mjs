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
  assert.equal(W.resolveFestivalId(ms, '亂填'), null);
});

test('最近剛過的節氣：當天、數日後、跨年、表格之前', () => {
  const today = W.recentSolarTermForDate('2026-11-07');
  assert.equal(today.name, '立冬');
  assert.equal(today.isToday, true);

  const after = W.recentSolarTermForDate('2026-11-20');
  assert.equal(after.name, '立冬');
  assert.equal(after.date, '2026-11-07');
  assert.equal(after.isToday, false);

  const nextTerm = W.recentSolarTermForDate('2026-11-22');
  assert.equal(nextTerm.name, '小雪');
  assert.equal(nextTerm.isToday, true);

  const acrossYear = W.recentSolarTermForDate('2027-01-02');
  assert.equal(acrossYear.name, '冬至');
  assert.equal(acrossYear.date, '2026-12-22');
  assert.equal(acrossYear.isToday, false);

  assert.equal(W.recentSolarTermForDate('2025-12-31'), null);
});

test('calendarViewForMs 與伺服器欄位一致', () => {
  const view = W.calendarViewForMs(Date.parse('2026-11-20T04:00:00Z'));
  assert.equal(view.date, '2026-11-20');
  assert.equal(view.solarTerm, null);
  assert.equal(view.recentSolarTerm, '立冬');
  assert.equal(view.recentSolarTermDate, '2026-11-07');
  assert.equal(view.solarTermIsToday, false);

  const onTerm = W.calendarViewForMs(Date.parse('2026-11-07T04:00:00Z'));
  assert.equal(onTerm.solarTerm, '立冬');
  assert.equal(onTerm.solarTermIsToday, true);
});