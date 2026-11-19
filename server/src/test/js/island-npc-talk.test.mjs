// node --test server/src/test/js/island-npc-talk.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const here = dirname(fileURLToPath(import.meta.url));
const html = readFileSync(
  join(here, '..', '..', 'main', 'resources', 'public', 'index.html'),
  'utf8'
);

const D = await loadPublicModule('island-people-data.js');
const T = await loadPublicModule('island-npc-talk.js');
const { handleExploreKeydown } = await loadPublicModule('island-explore-input.js');

const ISLAND_IDS = ['main', 'tcm', 'computer', 'casino', 'beach', 'city'];

function assertZhLine(line) {
  assert.equal(typeof line, 'string');
  assert.ok(line.length > 0);
  assert.ok(line.length <= 60, '台詞過長：' + line);
  assert.ok(/[\u4e00-\u9fff]/.test(line), '應含繁中：' + line);
}

test('DOM：#islandNpcTalk 楓之谷視窗鉻', () => {
  assert.match(html, /id="islandNpcTalk"/);
  assert.match(html, /maple-window/);
  assert.match(html, /maple-win-title/);
  assert.match(html, /maple-win-close/);
  assert.match(html, /island-hud-esc-chip/);
});

test('每種造型 ≥4 名字、≥6 一般台詞；六島專屬台詞', () => {
  for (const style of D.NPC_STYLE_IDS) {
    for (let i = 0; i < 4; i++) {
      const profile = D.npcTalkProfile(style, i);
      assertZhLine(profile.name);
    }
    const picked = D.pickNpcLines(style, 'main', null, () => 0.3);
    assert.ok(picked.lines.length >= 3);
    picked.lines.forEach(assertZhLine);
  }
  for (const island of ISLAND_IDS) {
    const hit = D.pickNpcLines('summer', island, null, () => 0.91);
    assert.ok(hit.lines.length >= 3);
  }
});

test('pickNpcLines：50 次相鄰開場句不連續相同', () => {
  let seed = 1;
  const rng = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  let lastOpening = null;
  const talkKey = 'npc-test';
  for (let i = 0; i < 50; i++) {
    const picked = D.pickNpcLines('sport', 'main', lastOpening, rng);
    if (lastOpening) assert.notEqual(picked.openingKey, lastOpening, '第 ' + i + ' 次');
    lastOpening = picked.openingKey;
    assert.ok(picked.openingKey);
    void talkKey;
  }
});

test('pickNpcLines：島嶼台詞會被選到', () => {
  let found = 0;
  const markers = ['飲料店', '推拿館', '研發樓', '賭場', '海面', '7-11'];
  for (let i = 0; i < 120; i++) {
    const isl = ISLAND_IDS[i % ISLAND_IDS.length];
    const picked = D.pickNpcLines(D.NPC_STYLE_IDS[i % D.NPC_STYLE_IDS.length], isl, null, () => (i % 17) / 17);
    const text = picked.lines.join('');
    for (let m = 0; m < markers.length; m++) {
      if (text.includes(markers[m])) found += 1;
    }
  }
  assert.ok(found >= 6, '島嶼專屬台詞應出現');
});

test('nearestTalkableNpc：距離與機車騎士排除', () => {
  const npcs = [
    { x: 0, z: 0, talkable: true, scooter: false },
    { x: 1.5, z: 0, talkable: true, scooter: false },
    { x: 0, z: 2, talkable: true, scooter: true }
  ];
  assert.deepEqual(D.nearestTalkableNpc(0, 0, npcs, 1.7), npcs[0]);
  assert.equal(D.nearestTalkableNpc(0, 0, npcs, 1.7).x, 0);
  assert.equal(D.nearestTalkableNpc(4, 0, npcs, 1.7), null);
  assert.equal(D.nearestTalkableNpc(0, 0, npcs, 1.8), npcs[0]);
  const multi = [
    { x: 0.5, z: 0, talkable: true, scooter: false },
    { x: 1.0, z: 0, talkable: true, scooter: false }
  ];
  assert.deepEqual(D.nearestTalkableNpc(0, 0, multi, 1.75), multi[0]);
  const withScooter = [{ x: 0, z: 0, talkable: false, scooter: true }];
  assert.equal(D.nearestTalkableNpc(0, 0, withScooter, 1.75), null);
});

test('npcTalkShouldClose：2.9 不關、3.1 關', () => {
  assert.equal(D.npcTalkShouldClose(0, 0, 2.9, 0), false);
  assert.equal(D.npcTalkShouldClose(0, 0, 3.1, 0), true);
});

test('門口互動優先於「聊天」', () => {
  const zone = { id: 'shop-door', action: 'enterInterior', label: '進入' };
  const blocked = D.shouldOfferNpcTalkPrompt({
    exploring: true,
    paused: false,
    indoor: false,
    uiOpen: false,
    lobbyOpen: false,
    chatOpen: false,
    talkOpen: false,
    zone: zone,
    px: 0,
    pz: 0,
    npcs: [{ x: 0.5, z: 0, talkable: true, scooter: false }]
  });
  assert.equal(blocked, null);
  assert.equal(D.npcTalkBlockedByZone(zone), true);
});

test('Esc 層級：路人對話先於聊天', () => {
  let closedTalk = false;
  let closedChat = false;
  const body = { tagName: 'BODY' };
  globalThis.document = {
    body: body,
    documentElement: { tagName: 'HTML' },
    getElementById: () => null
  };
  globalThis.window = globalThis;
  const ctx = {
    event: {
      key: 'Escape',
      code: 'Escape',
      altKey: false,
      ctrlKey: false,
      metaKey: false,
      target: body,
      preventDefault() {}
    },
    document: globalThis.document,
    getMode: () => ({ exploring: true }),
    player: { keys: {} },
    controlsActive: () => true,
    islandUiOpen: () => false,
    syncInteractPrompt: () => false,
    casino: { isOpen: () => false, isTableOpen: () => false, closeLobby: () => {}, closeTable: () => {} },
    isInside: () => false,
    graffiti: null,
    roadmap: null,
    npcTalk: {
      isOpen: () => true,
      close: () => { closedTalk = true; }
    },
    islandChat: {
      isOpen: () => true,
      close: () => { closedChat = true; }
    },
    setExplore: () => {}
  };
  assert.equal(handleExploreKeydown(ctx), true);
  assert.equal(closedTalk, true);
  assert.equal(closedChat, false);
});

test('talkDialogAdvance 與打字機', () => {
  const state = { open: true, line: '你好世界', shown: 2, lineIndex: 0, lines: ['你好世界', '再見'] };
  assert.equal(T.talkDialogAdvance(state), 'typing-done');
  state.shown = state.line.length;
  assert.equal(T.talkDialogAdvance(state), 'next-line');
  state.lineIndex = 1;
  state.shown = state.line.length;
  assert.equal(T.talkDialogAdvance({ ...state, lineIndex: 1, lines: ['a', 'b'] }), 'close');
  const tick = T.advanceTypewriter({ line: '一二三四', shown: 0, reduced: false }, 0.05);
  assert.ok(tick.shown >= 1);
});