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
const L = await loadPublicModule('island-layout.js');
const T = await loadPublicModule('island-npc-talk.js');
const { handleExploreKeydown } = await loadPublicModule('island-explore-input.js');
const C = await loadPublicModule('island-city-data.js');

const ISLAND_IDS = ['main', 'tcm', 'computer', 'casino', 'beach', 'city'];

function assertNpcName(name) {
  assert.equal(typeof name, 'string');
  assert.ok(name.length > 0);
  assert.ok(name.length <= 12, '名字過長：' + name);
}

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
      assertNpcName(profile.name);
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

test('pickNpcLines：各造型各島 50 次至少一次開場為島嶼台詞', () => {
  for (const style of D.NPC_STYLE_IDS) {
    for (const island of ISLAND_IDS) {
      const pools = D.npcTalkLinePools(style, island);
      if (!pools.island.length) continue;
      let seen = false;
      for (let i = 0; i < 50; i++) {
        const picked = D.pickNpcLines(style, island, null, () => (i * 0.11 + 0.02) % 1);
        if (pools.island.includes(picked.openingKey)) seen = true;
      }
      assert.ok(seen, style + ' @ ' + island + ' 開場島嶼台詞');
    }
  }
});

test('台詞禁字與腳本文案', () => {
  const lines = D.allNpcScriptLinesForTests();
  for (const line of lines) {
    for (const bad of D.NPC_LINE_BANNED_FRAGMENTS) {
      assert.ok(!line.includes(bad), '禁字「' + bad + '」：' + line);
    }
  }
});

test('npcSpawnPlan：walker islandId 符合路徑起點所在島', () => {
  const plan = D.npcSpawnPlan({});
  const walkers = plan.walkers.concat(
    plan.extras.filter((e) => e.walker && e.path).map((e) => e)
  );
  for (const def of walkers) {
    const id = D.resolveWalkerIslandId(def, null);
    const at = L.islandAt(def.path[0][0], def.path[0][1], 0.15);
    assert.equal(id, at && at.id, def.style + ' walker island');
  }
  const cityStyles = D.cityWalkerStyles();
  const cityPaths = C.cityWalkerPaths();
  for (let i = 0; i < plan.cityCount; i++) {
    const path = cityPaths[i % cityPaths.length];
    const id = D.resolveWalkerIslandId({ path: path }, { cityOnly: true });
    assert.equal(id, 'city');
    assert.equal(L.islandAt(path[0][0], path[0][1], 0.15).id, 'city');
    void cityStyles;
  }
});

test('walker 凍結解凍：pingpong 與環狀路徑位置不跳', () => {
  const ring = {
    path: D.MAIN_RING_WALK,
    speed: 0.045,
    pingpong: false,
    offset: 0.1
  };
  const pier = {
    path: D.EAST_PIER_WALK,
    speed: 0.05,
    pingpong: true,
    offset: 0.2
  };
  for (const w of [ring, pier]) {
    const t0 = 12.5;
    const before = D.npcWalkerSamplePosition(w, t0, false);
    D.freezeNpcWalker(w, t0, false);
    const frozen = D.npcWalkerSamplePosition(w, t0, false);
    assert.ok(Math.hypot(frozen.x - before.x, frozen.z - before.z) < 1e-6, 'freeze in place');
    D.unfreezeNpcWalker(w, t0 + 5, false);
    const after = D.npcWalkerSamplePosition(w, t0 + 5, false);
    assert.ok(Math.hypot(after.x - frozen.x, after.z - frozen.z) < 1e-6, 'unfreeze resume');
  }
});

test('fairy 推拿館北東側仍可聊天', () => {
  const extras = D.islandExtraDefs();
  const fairy = extras.find((d) => d.style === 'fairy' && !d.walker);
  assert.ok(fairy);
  const npc = {
    x: fairy.x,
    z: fairy.z,
    talkable: true,
    scooter: false,
    name: '綺夢'
  };
  const radius = 1.75;
  const fromNorth = D.nearestTalkableNpc(fairy.x, fairy.z + 0.55, [npc], radius);
  const fromEast = D.nearestTalkableNpc(fairy.x + 0.55, fairy.z, [npc], radius);
  assert.ok(fromNorth);
  assert.ok(fromEast);
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

test('情境台詞比例 25～45%（1000 次）', () => {
  const world = {
    weather: { kind: 'rain', tempC: 22 },
    hour: 14,
    festival: null,
    calendar: {},
    news: [{ title: '測試新聞' }]
  };
  const worldLines = new Set(D.allWorldContextLinesForTests());
  let seed = 11;
  const rng = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  let hits = 0;
  for (let i = 0; i < 1000; i++) {
    const picked = D.pickNpcLinesWithWorld('summer', 'main', null, rng, world);
    const contextual = picked.lines.some((line) => worldLines.has(line) || /你有看到「/.test(line) || /^今天是.+耶/.test(line));
    if (contextual) hits += 1;
  }
  const ratio = hits / 1000;
  assert.ok(ratio >= 0.25 && ratio <= 0.45, 'ratio=' + ratio);
});

test('rain 時可能出現沒帶傘；高溫 main 出現冰的', () => {
  let seed = 99;
  const rng = () => {
    seed = (seed * 48271) % 2147483647;
    return seed / 2147483647;
  };
  let umbrella = false;
  let hot = false;
  for (let i = 0; i < 200; i++) {
    const rainLines = D.pickNpcLinesWithWorld('summer', 'main', null, rng, {
      weather: { kind: 'rain', tempC: 20 }, hour: 12, calendar: {}, news: []
    }).lines.join('|');
    if (rainLines.includes('沒帶傘')) umbrella = true;
    const hotLines = D.pickNpcLinesWithWorld('summer', 'main', null, rng, {
      weather: { kind: 'clear', tempC: 33 }, hour: 13, calendar: {}, news: []
    }).lines.join('|');
    if (hotLines.includes('來杯冰的')) hot = true;
  }
  assert.equal(umbrella, true);
  assert.equal(hot, true);
});

test('新聞標題用 textContent 安全（假 img onerror）', () => {
  const evil = '<img src=x onerror=alert(1)>';
  const line = D.pickContextualWorldLine('summer', 'main', {
    weather: { kind: 'clear', tempC: 24 },
    hour: 12,
    calendar: {},
    news: [{ title: evil }]
  }, () => 0.05);
  assert.ok(!line.includes('<img'));
  assert.ok(line.includes('onerror') === false || !line.includes('<'));
});

test('50 次相鄰開場句不連續相同（with world）', () => {
  let seed = 3;
  const rng = () => {
    seed = (seed * 16807) % 2147483647;
    return seed / 2147483647;
  };
  let last = null;
  const world = { weather: { kind: 'clear', tempC: 24 }, hour: 10, calendar: {}, news: [] };
  for (let i = 0; i < 50; i++) {
    const picked = D.pickNpcLinesWithWorld('sport', 'main', last, rng, world);
    if (last) assert.notEqual(picked.openingKey, last);
    last = picked.openingKey;
  }
});