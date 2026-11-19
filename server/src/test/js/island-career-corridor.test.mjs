import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';
import { MAX_LAMP_POINT_LIGHTS } from '../../main/resources/public/js/island-kit.js';

const L = await loadPublicModule('island-layout.js');
const {
  CARD_FIELD_WHITELIST,
  buildCardTextModel,
  fillCareerCard,
  careerCardHeading,
  scanTimelinePrivacy,
  validateTimelineData,
  pickWhitelistFields,
  assertCorridorNavigable,
  assertExhibitLayout,
  assertZoneSignLayout,
  careerCardEscapeShouldClose
} = await loadPublicModule('island-career-corridor-logic.js');

const root = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public');
const timelineText = readFileSync(join(root, 'island/career-timeline.json'), 'utf8');
const timeline = JSON.parse(timelineText);

test('時間軸為兩階段結構含 PunchClock', () => {
  const v = validateTimelineData(timeline);
  assert.equal(v.ok, true, v.reason);
  assert.equal(timeline.entries.length, 4);
  const firmware = timeline.entries.find((e) => e.id === 'firmware-2013-2022');
  const software = timeline.entries.find((e) => e.id === 'software-2022-now');
  assert.equal(firmware.phase, '韌體時期');
  assert.equal(software.phase, '純軟體時期');
  assert.ok(!firmware.org);
  assert.ok(software.summary.includes('虛擬貨幣交易所'));
  assert.ok(software.summary.includes('運維'));
  assert.ok(!timelineText.includes('弘真科技'));
  assert.ok(!timelineText.includes('hongzhen'));
  assert.ok(!timelineText.includes('2018–2022'));
  assert.ok(!timelineText.includes('從缺'));
});

test('卡片僅輸出白名單欄位', () => {
  const model = buildCardTextModel(timeline.entries[1]);
  const keys = Object.keys(model).filter((k) => k !== 'isPunchClock');
  assert.deepEqual(keys.sort(), CARD_FIELD_WHITELIST.slice().sort());
  const picked = pickWhitelistFields(timeline.entries.find((e) => e.id === 'firmware-2013-2022'));
  assert.deepEqual(Object.keys(picked).sort(), ['phase', 'prop', 'summary', 'years']);
});

test('韌體卡無公司名時以時期名作標題', () => {
  const model = buildCardTextModel(timeline.entries.find((e) => e.id === 'firmware-2013-2022'));
  assert.equal(model.org, '');
  assert.equal(careerCardHeading(model), '韌體時期');
  const doc = {
    createElement: (tag) => {
      const el = {
        tagName: tag.toUpperCase(),
        className: '',
        textContent: '',
        children: [],
        firstChild: null,
        appendChild(child) {
          this.children.push(child);
          if (!this.firstChild) this.firstChild = child;
        },
        removeChild(child) {
          this.children = this.children.filter((c) => c !== child);
          this.firstChild = this.children[0] || null;
        }
      };
      return el;
    }
  };
  const body = doc.createElement('div');
  fillCareerCard(body, model, doc);
  assert.equal(body.children[0].textContent, '韌體時期');
  assert.ok(body.children[0].textContent.length > 0);
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

test('時期主題牌位於前庭朝南、不貼研發樓前門牆', () => {
  const signs = assertZoneSignLayout();
  assert.equal(signs.ok, true, JSON.stringify(signs));
});

test('僅 PunchClock 卡顯示進入研發樓鈕（hidden 時應關閉）', () => {
  const punch = buildCardTextModel(timeline.entries.find((e) => e.id === 'punchclock'));
  const fw = buildCardTextModel(timeline.entries.find((e) => e.id === 'firmware-2013-2022'));
  assert.equal(punch.isPunchClock, true);
  assert.equal(fw.isPunchClock, false);
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
  assert.ok(!js.includes('attachComputerLabExterior'));
  assert.ok(!js.includes('whitenComputerSiteMeshes'));
  assert.ok(js.includes('careerCardEscapeShouldClose(cardVisible, ev.key)'));
  const onDoc = js.match(/function onDocKey\(ev\) \{[\s\S]*?\n  \}/);
  assert.ok(onDoc);
  assert.equal(onDoc[0].includes('islandUiOpen'), false);
});

test('MAX_LAMP_POINT_LIGHTS 仍為 4', () => {
  assert.equal(MAX_LAMP_POINT_LIGHTS, 4);
});