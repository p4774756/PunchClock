// node --test server/src/test/js/island-tcm-figure.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const {
  COMMON_10_COPY,
  PENDING_COPY,
  expandCurriculumInstances,
  countInstanceSides,
  pickTodayCode,
  taipeiDateString,
  buildCardTextModel,
  fillAcupointCard,
  pointTargetRecord,
  fieldCopy,
  resolveTcmPickHit,
  acupointNearBronzeBody,
  BRONZE_HEAD_TOP_Y,
  markerSurfacePosition
} = await loadPublicModule('island-tcm-figure-logic.js');

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public');
const records = JSON.parse(readFileSync(join(root, 'island/tcm-bronze-points.json'), 'utf8'));

test('定位收錄：BL1 與有定位數量', () => {
  const bl1 = records.find((r) => r.code === 'BL1');
  assert.equal(bl1.location, '眼內角斜外約 1 分。');
  const located = records.filter((r) => r.location && r.location.length);
  assert.ok(located.length >= 50, 'located=' + located.length);
});

test('104 穴標記貼在銅人身體包圍盒內（外擴 0.04）', () => {
  const expanded = expandCurriculumInstances(records);
  assert.equal(expanded.length, 104);
  let maxY = 0;
  for (let i = 0; i < expanded.length; i++) {
    const row = expanded[i];
    const side = row.instanceKey.endsWith(':-1') ? -1 : 1;
    const rec = records.find((r) => r.code === row.code);
    const pos = markerSurfacePosition(rec, side);
    assert.ok(acupointNearBronzeBody(pos, 0.04), row.instanceKey + ' ' + pos.join(','));
    maxY = Math.max(maxY, pos[1]);
  }
  assert.ok(maxY <= BRONZE_HEAD_TOP_Y + 0.05, 'maxY=' + maxY);
});

test('課程穴 59 code 齊全', () => {
  assert.equal(records.length, 59);
  const codes = new Set(records.map((r) => r.code));
  assert.equal(codes.size, 59);
});

test('雙側鏡射約 104 點（中線 1、非中線 2）', () => {
  const stats = countInstanceSides(records);
  assert.equal(stats.codes, 59);
  assert.equal(stats.instances, 104);
  const expanded = expandCurriculumInstances(records);
  assert.equal(expanded.length, 104);
  const mid = records.filter((r) => r.midline).length;
  const bi = records.length - mid;
  assert.equal(mid + bi * 2, 104);
});

test('非中線穴左右 x 符號相反', () => {
  const st36 = records.find((r) => r.code === 'ST36');
  const left = pointTargetRecord(st36, -1);
  const right = pointTargetRecord(st36, 1);
  assert.ok(left.position[0] < 0);
  assert.ok(right.position[0] > 0);
  assert.equal(Math.abs(left.position[0]), Math.abs(right.position[0]));
  assert.equal(left.position[1], right.position[1]);
});

test('今日一穴：台北同日穩定、跨日改變', () => {
  const evening = Date.parse('2026-11-18T15:30:00.000Z'); // 台北 23:30
  const morning = Date.parse('2026-11-18T16:30:00.000Z'); // 台北 00:30 次日
  assert.equal(taipeiDateString(evening), '2026-11-18');
  assert.equal(taipeiDateString(morning), '2026-11-19');
  const a = pickTodayCode(records, evening);
  const b = pickTodayCode(records, evening + 60_000);
  const c = pickTodayCode(records, morning);
  assert.equal(a, b);
  assert.notEqual(a, c);
});

test('常用 10 主治／按法一字不差', () => {
  for (const code of Object.keys(COMMON_10_COPY)) {
    const rec = records.find((r) => r.code === code);
    assert.ok(rec, code);
    assert.equal(rec.indications, COMMON_10_COPY[code].indications);
    assert.equal(rec.method, COMMON_10_COPY[code].method);
  }
});

test('缺欄顯示尚待收錄、卡片用 textContent', () => {
  const pending = records.find((r) => r.code === 'GV20');
  const model = buildCardTextModel(pending, { nowMs: Date.now() });
  assert.equal(fieldCopy(pending.indications), PENDING_COPY);
  assert.equal(model.indications, PENDING_COPY);
  assert.equal(model.method, PENDING_COPY);

  const doc = {
    createElement(tag) {
      const el = { tagName: tag.toUpperCase(), className: '', textContent: '', children: [], hidden: false };
      el.appendChild = (ch) => { el.children.push(ch); return ch; };
      el.removeChild = (ch) => {
        el.children = el.children.filter((c) => c !== ch);
      };
      Object.defineProperty(el, 'firstChild', {
        get() { return el.children[0] || null; }
      });
      return el;
    },
    createTextNode(text) {
      return { nodeType: 3, textContent: text };
    }
  };
  const rootEl = doc.createElement('div');
  fillAcupointCard(rootEl, model, doc);
  const allText = JSON.stringify(rootEl);
  assert.ok(allText.indexOf(PENDING_COPY) >= 0);
  assert.ok(allText.indexOf('<') < 0);
});

test('resolveTcmPickHit：舊 zone 字串行為不變', () => {
  const legacy = resolveTcmPickHit({ object: { userData: { zone: 'graffiti-wall' } } });
  assert.equal(legacy, 'graffiti-wall');
  const empty = resolveTcmPickHit(null);
  assert.equal(empty, '');
});

test('resolveTcmPickHit：InstancedMesh 回傳穴 instanceKey', () => {
  const hit = resolveTcmPickHit({
    instanceId: 3,
    object: { userData: { tcmPointPick: { instanceKeys: ['A:m', 'B:1', 'B:-1', 'C:1'] } } }
  });
  assert.deepEqual(hit, { type: 'tcm-point', instanceKey: 'C:1', instanceId: 3 });
});