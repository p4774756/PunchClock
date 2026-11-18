// 多人在線純邏輯：node --test server/src/test/js/island-presence.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const P = await loadPublicModule('island-presence.js');

test('暱稱去掉空白和控制字元，超長或空白就作廢', () => {
  assert.equal(P.cleanNick('  小明 \n'), '小明');
  assert.equal(P.cleanNick('\u0007阿明'), '阿明');
  assert.equal(P.cleanNick('<script>'), '<script>');
  assert.equal(P.cleanNick('   '), '');
  assert.equal(P.cleanNick('一二三四五六七八九十甲乙'), '一二三四五六七八九十甲乙');
  assert.equal(P.cleanNick('一二三四五六七八九十甲乙丙'), '');
});

test('自動取名是旅人加三位數', () => {
  assert.equal(P.autoNick(42), '旅人 042');
  assert.equal(P.autoNick(0), '旅人 000');
  assert.equal(P.autoNick(1001), '旅人 001');
  assert.match(P.autoNick(7), /^旅人 \d{3}$/);
});

test('位置訊息可以編碼再解回來', () => {
  const raw = P.encodePos({ x: 1.25, z: -2, yaw: 0.5, moving: true, scene: 'shop' });
  const msg = P.decodeMessage(raw);
  assert.equal(msg, null);
  const hello = P.decodeMessage(P.encodeHello('小明'));
  assert.equal(hello, null);
  const welcome = P.decodeMessage(JSON.stringify({
    t: 'welcome',
    id: 'ab12',
    name: '<script>',
    rev: 1,
    players: [{ id: 'cd34', name: '旅人 042', x: 1, z: 2, yaw: 0.25, moving: false }]
  }));
  assert.equal(welcome.t, 'welcome');
  assert.equal(welcome.name, '<script>');
  assert.equal(welcome.players[0].name, '旅人 042');
  assert.equal(welcome.players[0].moving, false);

  const snap = P.decodeMessage(JSON.stringify({
    t: 'snap',
    scene: 'outdoor',
    rev: 2,
    players: [
      { id: 'cd34', name: '旅人 042', x: 1.5, z: 2.5, yaw: 0.5, moving: true },
      { id: 'bad', name: '壞', x: 'no', z: 1, yaw: 0, moving: true }
    ]
  }));
  assert.equal(snap.t, 'snap');
  assert.equal(snap.scene, 'outdoor');
  assert.equal(snap.players.length, 1);
  assert.equal(snap.players[0].x, 1.5);
  assert.equal(snap.players[0].moving, true);

  const encoded = P.decodeMessage(P.encodePos({ x: 1.2346, z: -2, yaw: 0.5, moving: true, scene: 'casino' }));
  assert.equal(encoded, null);
  const pos = JSON.parse(P.encodePos({ x: 1.2346, z: -2, yaw: 0.5, moving: true, scene: 'casino' }));
  assert.equal(pos.t, 'pos');
  assert.equal(pos.x, 1.235);
  assert.equal(pos.z, -2);
  assert.equal(pos.moving, true);
  assert.equal(pos.scene, 'casino');
  assert.equal(P.decodeMessage('{'), null);
  assert.equal(P.decodeMessage(JSON.stringify({ t: 'pos', x: '1', z: 0, yaw: 0, moving: true, scene: 'outdoor' })), null);
  assert.equal(P.decodeMessage(JSON.stringify({ t: 'leave', id: 'ab12', rev: 3 })).id, 'ab12');
  assert.equal(JSON.parse(P.encodeRename('阿明')).t, 'rename');
  assert.equal(P.decodeMessage(JSON.stringify({
    t: 'said', id: 'ab12', name: '甲', text: '嗨', ts: 9
  })).text, '嗨');
  assert.equal(P.decodeMessage(JSON.stringify({ t: 'slow' })).t, 'slow');
  assert.equal(JSON.parse(P.encodeSay('嗨')).text, '嗨');
});

test('快照之間平滑移動，角度走短邊', () => {
  const motion = P.createMotion();
  P.pushTarget(motion, { x: 0, z: 0, yaw: 0 }, 1000);
  assert.equal(P.sampleMotion(motion, 1000).x, 0);
  P.pushTarget(motion, { x: 10, z: 4, yaw: 0 }, 1000);
  const mid = P.sampleMotion(motion, 1000 + 65);
  assert.ok(Math.abs(mid.x - 5) < 0.05, 'x=' + mid.x);
  assert.ok(Math.abs(mid.z - 2) < 0.05, 'z=' + mid.z);
  const end = P.sampleMotion(motion, 1000 + P.INTERP_MS);
  assert.ok(Math.abs(end.x - 10) < 1e-9);
  assert.ok(Math.abs(end.z - 4) < 1e-9);

  const turn = P.createMotion();
  P.pushTarget(turn, { x: 0, z: 0, yaw: 3.1 }, 0);
  P.pushTarget(turn, { x: 0, z: 0, yaw: -3.1 }, 0);
  const yaw = P.sampleMotion(turn, 65).yaw;
  assert.ok(Math.abs(Math.abs(yaw) - Math.PI) < 0.08, 'yaw=' + yaw);
});

test('斷線重連等待 1、2、4 秒，最多 30 秒', () => {
  assert.equal(P.reconnectDelayMs(0), 1000);
  assert.equal(P.reconnectDelayMs(1), 2000);
  assert.equal(P.reconnectDelayMs(2), 4000);
  assert.equal(P.reconnectDelayMs(3), 8000);
  assert.equal(P.reconnectDelayMs(5), 30000);
  assert.equal(P.reconnectDelayMs(9), 30000);
});

test('探索角色 id 只接受 milu 或 berry', () => {
  assert.equal(P.normalizeAvatar('berry'), 'berry');
  assert.equal(P.normalizeAvatar('milu'), 'milu');
  assert.equal(P.normalizeAvatar(''), 'milu');
  assert.equal(P.normalizeAvatar('cat'), 'milu');
});

test('encodePos 只有 berry 才帶 avatar，仍低於 512 bytes', () => {
  const milu = JSON.parse(P.encodePos({ x: 0, z: 0, yaw: 0, moving: false, scene: 'outdoor', avatar: 'milu' }));
  assert.equal(milu.avatar, undefined);
  const berry = JSON.parse(P.encodePos({ x: 1, z: 2, yaw: 0.5, moving: true, scene: 'shop', avatar: 'berry' }));
  assert.equal(berry.avatar, 'berry');
  assert.ok(P.encodePos({ x: 1, z: 2, yaw: 0.5, moving: true, scene: 'shop', avatar: 'berry' }).length < 512);
});

test('decode 玩家 avatar 不明值回退 milu', () => {
  const snap = P.decodeMessage(JSON.stringify({
    t: 'snap',
    scene: 'outdoor',
    rev: 1,
    players: [
      { id: 'a', name: '甲', x: 0, z: 0, yaw: 0, moving: false, avatar: 'berry' },
      { id: 'b', name: '乙', x: 1, z: 0, yaw: 0, moving: false },
      { id: 'c', name: '丙', x: 2, z: 0, yaw: 0, moving: false, avatar: 'unknown' }
    ]
  }));
  assert.equal(snap.players[0].avatar, 'berry');
  assert.equal(snap.players[1].avatar, 'milu');
  assert.equal(snap.players[2].avatar, 'milu');
});

test('角色變更時 shouldSendPos 立刻為真', () => {
  const prev = { x: 0, z: 0, yaw: 0, moving: false, scene: 'outdoor', avatar: 'milu', sentAt: 5000 };
  const next = { x: 0, z: 0, yaw: 0, moving: false, scene: 'outdoor', avatar: 'berry' };
  assert.equal(P.shouldSendPos(prev, next, 5010), true);
});

test('loadAvatar／saveAvatar 走 localStorage', () => {
  const store = Object.create(null);
  const storage = {
    getItem: (k) => (k in store ? store[k] : null),
    setItem: (k, v) => {
      store[k] = v;
    }
  };
  assert.equal(P.loadAvatar(storage), 'milu');
  assert.equal(P.saveAvatar(storage, 'berry'), 'berry');
  assert.equal(P.loadAvatar(storage), 'berry');
  assert.equal(P.saveAvatar(storage, 'evil'), 'milu');
});

test('移動中約每秒 10 次，站著不動就降頻', () => {
  const first = { x: 0, z: 0, yaw: 0, moving: true, scene: 'outdoor', sentAt: 1000 };
  assert.equal(P.shouldSendPos(null, first, 1000), true);
  assert.equal(P.shouldSendPos(first, { x: 1, z: 0, yaw: 0, moving: true, scene: 'outdoor' }, 1050), false);
  assert.equal(P.shouldSendPos(first, { x: 1, z: 0, yaw: 0, moving: true, scene: 'outdoor' }, 1100), true);
  const idle = { x: 1, z: 0, yaw: 0, moving: false, scene: 'outdoor', sentAt: 2000 };
  assert.equal(P.shouldSendPos(idle, { x: 1, z: 0, yaw: 0, moving: false, scene: 'outdoor' }, 3000), false);
  assert.equal(P.shouldSendPos(idle, { x: 1, z: 0, yaw: 0, moving: false, scene: 'outdoor' }, 7000), true);
  assert.equal(P.shouldSendPos(idle, { x: 1, z: 0, yaw: 0, moving: false, scene: 'shop' }, 2100), true);
});

test('網址跟著頁面的 http 或 https', () => {
  assert.equal(P.presenceUrl({ protocol: 'https:', host: 'app.example.com' }), 'wss://app.example.com/ws/island');
  assert.equal(P.presenceUrl({ protocol: 'http:', host: 'localhost:3000' }), 'ws://localhost:3000/ws/island');
});
