// 島上泡泡聊天純邏輯：node --test server/src/test/js/island-chat.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const C = await loadPublicModule('island-chat.js');
const P = await loadPublicModule('island-presence.js');

test('聊天文字保留標點與 emoji，超長截斷', () => {
  assert.equal(C.cleanChatText('  你好！\n '), '你好！');
  assert.equal(C.cleanChatText('<img src=x>'), '<img src=x>');
  assert.equal(C.cleanChatText('🙂✨'), '🙂✨');
  assert.equal(C.cleanChatText('   '), '');
  const sixty = '一二三四五六七八九十'.repeat(6);
  assert.equal(C.cleanChatText(sixty), sixty);
  assert.equal(C.cleanChatText(sixty + '甲'), sixty);
});

test('聊天紀錄只留最近 20 句', () => {
  const log = C.createChatLog(20);
  for (let i = 0; i < 25; i++) {
    log.push({ name: '甲', text: '第' + i, ts: i });
  }
  const rows = log.list();
  assert.equal(rows.length, 20);
  assert.equal(rows[0].text, '第5');
  assert.equal(rows[19].text, '第24');
  assert.equal(C.formatChatLine(rows[0]), '甲：第5');
  log.clear();
  assert.equal(log.list().length, 0);
});

test('T 與無入口提示的 Enter 可開聊天，改名／打字／有入口時不行', () => {
  assert.equal(C.shouldOpenChatKey({
    exploring: true, uiOpen: false, typing: false, nickOpen: false, chatOpen: false,
    promptVisible: false, key: 't', code: 'KeyT'
  }), true);
  assert.equal(C.shouldOpenChatKey({
    exploring: true, uiOpen: false, typing: false, nickOpen: false, chatOpen: false,
    promptVisible: false, key: 'Enter', code: 'Enter'
  }), true);
  assert.equal(C.shouldOpenChatKey({
    exploring: true, uiOpen: false, typing: false, nickOpen: false, chatOpen: false,
    promptVisible: true, key: 'Enter', code: 'Enter'
  }), false);
  assert.equal(C.shouldOpenChatKey({
    exploring: true, uiOpen: false, typing: true, nickOpen: false, chatOpen: false,
    promptVisible: false, key: 't', code: 'KeyT'
  }), false);
  assert.equal(C.shouldOpenChatKey({
    exploring: true, uiOpen: false, typing: false, nickOpen: true, chatOpen: false,
    promptVisible: false, key: 't', code: 'KeyT'
  }), false);
  assert.equal(C.shouldOpenChatKey({
    exploring: false, uiOpen: false, typing: false, nickOpen: false, chatOpen: false,
    promptVisible: false, key: 't', code: 'KeyT'
  }), false);
});

test('decodeMessage 能解析 said 與 slow', () => {
  const said = P.decodeMessage(JSON.stringify({
    t: 'said',
    id: 'ab12',
    name: '<script>',
    text: '<img src=x>',
    ts: 42
  }));
  assert.equal(said.t, 'said');
  assert.equal(said.id, 'ab12');
  assert.equal(said.name, '<script>');
  assert.equal(said.text, '<img src=x>');
  assert.equal(said.ts, 42);
  assert.equal(P.decodeMessage(JSON.stringify({ t: 'slow' })).t, 'slow');
  assert.equal(P.decodeMessage(JSON.stringify({ t: 'said', id: 'x', name: 1, text: 'a' })), null);
  assert.equal(JSON.parse(P.encodeSay('嗨')).t, 'say');
  assert.equal(JSON.parse(P.encodeSay('嗨')).text, '嗨');
});

test('打字中移動鍵與互動鍵不會觸發', async () => {
  const E = await loadPublicModule('island-explore.js');
  assert.equal(E.shouldCaptureMoveKey({
    code: 'KeyW', typing: true, active: true, uiOpen: false, focus: 'explore'
  }), false);
  assert.equal(E.shouldTriggerInteractKey({
    promptVisible: true, typing: true, focus: 'explore', code: 'Enter', key: 'Enter'
  }), false);
  assert.equal(E.shouldTriggerInteractKey({
    promptVisible: true, typing: true, focus: 'explore', code: 'KeyE', key: 'e'
  }), false);
});

test('對方泡泡用獨立定位，抬到名牌上方；自己維持原 transform', () => {
  assert.match(C.peerBubbleTransform(120, 240), /translate\(120px, 240px\)/);
  assert.match(C.peerBubbleTransform(120, 240), /translateY\(-1\.85rem\)/);
  assert.equal(C.selfBubbleTransform(10, 20), 'translate(10px, 20px) translate(-50%, -120%)');
});

test('對方泡泡掛在 #islandLabels，不進名牌膠囊', () => {
  const labels = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  const name = {
    className: 'island-peer-name',
    children: [],
    querySelector() { return null; },
    insertBefore() { throw new Error('不該把泡泡插進名牌'); }
  };
  const doc = {
    getElementById(id) {
      if (id === 'islandLabels') return labels;
      return null;
    },
    createElement(tag) {
      return {
        tagName: String(tag).toUpperCase(),
        className: '',
        hidden: false,
        textContent: '',
        dataset: {},
        style: {},
        parentNode: null,
        children: [],
        querySelector() { return null; },
        appendChild(el) { this.children.push(el); el.parentNode = this; },
        insertBefore() { throw new Error('不該 insertBefore 到名牌'); }
      };
    },
    addEventListener() {}
  };
  const chat = C.createIslandChat({ document: doc, now: () => 1000 });
  chat.onSaid({ t: 'said', id: 'p1', name: '甲', text: '你好世界這是稍長的句子', ts: 1 });
  chat.placeBubbles({
    visible: false,
    peers: [{ id: 'p1', x: 100, y: 200, visible: true }]
  });
  const peers = labels.children.filter((el) => /island-chat-bubble-peer/.test(el.className));
  assert.equal(peers.length, 1);
  assert.equal(peers[0].textContent, '你好世界這是稍長的句子');
  assert.match(peers[0].style.transform, /translateY\(-1\.85rem\)/);
  assert.equal(labels.children.some((el) => el.parentNode === name), false);
  assert.equal(name.children.length, 0);
});
