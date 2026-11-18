import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-layout.js');
const {
  CARD_FIELD_WHITELIST,
  buildCardTextModel,
  fillCareerCard,
  scanTimelinePrivacy,
  validateTimelineData,
  pickWhitelistFields,
  assertCorridorNavigable,
  assertExhibitLayout,
  careerCardEscapeShouldClose
} = await loadPublicModule('island-career-corridor-logic.js');

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public');
const timelineText = readFileSync(join(root, 'island/career-timeline.json'), 'utf8');
const timeline = JSON.parse(timelineText);

test('時間軸 10 格含夕煇兩筆與 PunchClock', () => {
  const v = validateTimelineData(timeline);
  assert.equal(v.ok, true, v.reason);
  assert.equal(timeline.entries.length, 10);
  const twi = timeline.entries.filter((e) => e.org === '夕煇');
  assert.equal(twi.length, 2);
  assert.ok(twi[0].summary.includes('虛擬貨幣交易所'));
  assert.ok(!twi[0].summary.includes('運維'));
  assert.ok(twi[1].summary.includes('運維'));
});

test('卡片僅輸出白名單欄位', () => {
  const model = buildCardTextModel(timeline.entries[6]);
  const keys = Object.keys(model).filter((k) => k !== 'isPunchClock');
  assert.deepEqual(keys.sort(), CARD_FIELD_WHITELIST.slice().sort());
  const picked = pickWhitelistFields(timeline.entries[0]);
  assert.deepEqual(Object.keys(picked).sort(), CARD_FIELD_WHITELIST.slice().sort());
});

test('fillCareerCard 以 textContent 寫入，不吃 innerHTML', () => {
  const evil = '<img src=x onerror="alert(1)">';
  const model = buildCardTextModel({
    id: 'x',
    years: '2020',
    org: evil,
    role: evil,
    summary: evil,
    prop: evil
  });
  const doc = {
    createElement: (tag) => {
      const el = {
        tagName: tag.toUpperCase(),
        className: '',
        textContent: '',
        hidden: false,
        children: [],
        firstChild: null,
        appendChild(child) {
          this.children.push(child);
          if (!this.firstChild) this.firstChild = child;
        },
        removeChild(child) {
          this.children = this.children.filter((c) => c !== child);
          this.firstChild = this.children[0] || null;
        },
        setAttribute() {},
        addEventListener() {}
      };
      return el;
    },
    createTextNode: (t) => ({ nodeType: 3, textContent: t })
  };
  const body = doc.createElement('div');
  fillCareerCard(body, model, doc);
  const text = body.children.map((c) => c.textContent).join('|');
  assert.ok(text.includes('&lt;img') === false);
  assert.ok(text.includes('<img'));
  for (let i = 0; i < body.children.length; i++) {
    assert.equal(body.children[i].innerHTML, undefined);
  }
});

test('資料檔個資掃描', () => {
  const hits = scanTimelinePrivacy(timelineText);
  assert.deepEqual(hits, []);
});

test('走廊可走且研發樓門口仍可站', () => {
  const nav = assertCorridorNavigable(L.walkable, L.blocked, 0.29);
  assert.equal(nav.ok, true, JSON.stringify(nav));
});

test('展件與門口、彼此間距符合約束', () => {
  const layout = assertExhibitLayout();
  assert.equal(layout.ok, true, JSON.stringify(layout));
});

test('僅 PunchClock 卡顯示進入研發樓鈕（hidden 時應關閉）', () => {
  const punch = buildCardTextModel(timeline.entries.find((e) => e.id === 'punchclock'));
  const thu = buildCardTextModel(timeline.entries.find((e) => e.id === 'thu-2007'));
  assert.equal(punch.isPunchClock, true);
  assert.equal(thu.isPunchClock, false);
  const css = readFileSync(
    join(root, 'js', 'island-career-corridor.js'),
    'utf8'
  );
  assert.ok(css.includes('#careerExhibitCard .career-enter-lab[hidden]{display:none!important}'));
});

test('Esc 在經歷卡開啟時應關閉本卡', () => {
  assert.equal(careerCardEscapeShouldClose(true, 'Escape'), true);
  assert.equal(careerCardEscapeShouldClose(true, 'Enter'), false);
  assert.equal(careerCardEscapeShouldClose(false, 'Escape'), false);
  const js = readFileSync(join(root, 'js', 'island-career-corridor.js'), 'utf8');
  assert.ok(js.includes('careerCardEscapeShouldClose(cardVisible, ev.key)'));
  const onDoc = js.match(/function onDocKey\(ev\) \{[\s\S]*?\n  \}/);
  assert.ok(onDoc);
  assert.equal(onDoc[0].includes('islandUiOpen'), false);
});