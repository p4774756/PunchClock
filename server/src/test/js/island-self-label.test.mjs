// node --test server/src/test/js/island-self-label.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-self-label.js');
const P = await loadPublicModule('island-presence.js');
const C = await loadPublicModule('island-chat.js');

const storageMem = () => {
  const map = new Map();
  return {
    getItem: (k) => (map.has(k) ? map.get(k) : null),
    setItem: (k, v) => { map.set(k, v); },
    removeItem: (k) => { map.delete(k); }
  };
};

test('resolveSelfLabelName：暱稱 > selfName > 旅人', () => {
  const st = storageMem();
  st.setItem(P.NICK_KEY, '  島主  ');
  assert.equal(L.resolveSelfLabelName({ storage: st, loadNick: P.loadNick, selfName: '旅人 001' }), '島主');
  st.removeItem(P.NICK_KEY);
  assert.equal(L.resolveSelfLabelName({ storage: st, loadNick: P.loadNick, selfName: '旅人 042' }), '旅人 042');
  assert.equal(L.resolveSelfLabelName({ storage: st, loadNick: P.loadNick, selfName: '' }), '旅人');
});

test('formatSelfDisplayName 與 cleanNick：超長省略、控制字元', () => {
  assert.equal(L.formatSelfDisplayName('   '), '');
  assert.equal(L.formatSelfDisplayName('\u0007甲'), '甲');
  assert.equal(L.formatSelfDisplayName('<x>'), '<x>');
  const exact = '一二三四五六七八九十甲乙';
  assert.equal(L.formatSelfDisplayName(exact), exact);
  const over = exact + '丙';
  assert.equal(L.formatSelfDisplayName(over), exact + '…');
  assert.equal(P.cleanNick(over), '');
});

test('loadSelfNameEnabled 預設開、0 關、storage 失敗仍為開', () => {
  assert.equal(L.loadSelfNameEnabled(storageMem()), true);
  const st = storageMem();
  st.setItem(L.SELF_NAME_KEY, '0');
  assert.equal(L.loadSelfNameEnabled(st), false);
  st.setItem(L.SELF_NAME_KEY, '1');
  assert.equal(L.loadSelfNameEnabled(st), true);
  assert.equal(L.loadSelfNameEnabled(null), true);
  assert.equal(L.loadSelfNameEnabled({ getItem() { throw new Error('x'); } }), true);
});

test('saveSelfNameEnabled 不拋錯', () => {
  const st = storageMem();
  L.saveSelfNameEnabled(st, false);
  assert.equal(st.getItem(L.SELF_NAME_KEY), '0');
  L.saveSelfNameEnabled({ setItem() { throw new Error('x'); } }, true);
});

test('selfLabelVisible：探索、開關、載具、主角隱藏', () => {
  const base = {
    mode: { exploring: true },
    selfNameEnabled: true,
    interiorBusy: false,
    uiOverlay: false,
    miluVisible: true,
    placeholderVisible: false,
    blockVisible: false,
    storage: storageMem(),
    loadNick: P.loadNick,
    selfName: '旅人 001',
    driving: false,
    sailing: false,
    steering: false,
    playerX: 0,
    playerZ: 0,
    P: 0
  };
  assert.equal(L.selfLabelVisible(base), true);
  assert.equal(L.selfLabelVisible({ ...base, selfNameEnabled: false }), false);
  assert.equal(L.selfLabelVisible({ ...base, mode: { exploring: false } }), false);
  assert.equal(L.selfLabelVisible({ ...base, interiorBusy: true }), false);
  assert.equal(L.selfLabelVisible({ ...base, uiOverlay: true }), false);
  assert.equal(L.selfLabelVisible({
    ...base,
    miluVisible: false,
    placeholderVisible: false,
    blockVisible: false
  }), false);
  assert.equal(L.selfLabelVisible({
    ...base,
    miluVisible: false,
    driving: true,
    vehicleX: 1,
    vehicleZ: 2
  }), true);
  assert.equal(L.selfLabelVisible({
    ...base,
    miluVisible: false,
    sailing: true,
    boatPose: { x: 1, z: 2, yaw: 0 },
    groundY: 0.5,
    boatBobY: 0
  }), true);
});

test('selfLabelAnchor：步行、乘客縮放、車、船', () => {
  const walk = L.selfLabelAnchor({
    driving: false,
    sailing: false,
    playerX: 3,
    playerZ: 4,
    offsetX: 1,
    offsetZ: 0,
    groundY: 0.2,
    P: 0.2,
    passengerScale: 1
  });
  assert.equal(walk.kind, 'player');
  assert.equal(walk.x, 4);
  assert.ok(Math.abs(walk.headY - (0.2 + L.SELF_LABEL_HEAD_OFFSET)) < 0.001);

  const passenger = L.selfLabelAnchor({
    sailing: true,
    steering: false,
    playerX: 2,
    playerZ: 3,
    groundY: 0.9,
    boatBobY: 0,
    passengerScale: 0.58,
    miluVisible: true,
    placeholderVisible: false,
    blockVisible: false
  });
  assert.equal(passenger.kind, 'passenger');

  const vehicle = L.selfLabelAnchor({
    driving: true,
    vehicleX: 5,
    vehicleZ: 6,
    P: 0.15,
    offsetX: 0,
    offsetZ: 0
  });
  assert.equal(vehicle.kind, 'vehicle');
  assert.equal(vehicle.x, 5);
  assert.ok(Math.abs(vehicle.headY - (0.15 + L.VEHICLE_SELF_LABEL_TOP)) < 0.001);

  const boatTop = L.selfLabelAnchor({
    sailing: true,
    playerX: 1,
    playerZ: 1,
    boatPose: { x: 8, z: 9, yaw: 0 },
    boatBobY: 0.1,
    miluVisible: false,
    placeholderVisible: false,
    blockVisible: false
  });
  assert.equal(boatTop.kind, 'boat');
  assert.equal(boatTop.x, 8);
});

test('projectInView 與 peer 錯開', () => {
  const projected = {
    x: 0,
    y: 0,
    z: 0,
    set(x, y, z) {
      this._x = x;
      this._y = y;
      this._z = z;
    },
    project() {
      this.x = 0;
      this.y = 0;
      this.z = 0.5;
    }
  };
  const screen = L.projectInView(projected, {}, 0, 1, 0, 800, 600);
  assert.equal(screen.x, 400);
  assert.equal(screen.y, 300);
  projected.project = () => { projected.z = 2; };
  assert.equal(L.projectInView(projected, {}, 0, 1, 0, 800, 600), null);
  assert.equal(L.selfLabelPeerNudge(100, 100, [{ x: 110, y: 110, visible: true }]), L.SELF_PEER_SCREEN_NUDGE_PX);
});

test('聊天泡泡在名牌上方', () => {
  assert.match(C.selfBubbleTransform(10, 20), /translate\(10px, 20px\)/);
  const withName = C.selfBubbleTransform(10, 20, {
    selfNameAbove: true,
    extraTransform: L.selfBubbleExtraTransform(true)
  });
  assert.match(withName, /translateY\(-1\.65rem\)/);
  assert.doesNotMatch(C.selfBubbleTransform(1, 2, { selfNameAbove: false }), /translateY/);
});

test('createSelfLabelPlacer 只在名稱變更時寫 textContent', () => {
  const labels = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  let writes = 0;
  const doc = {
    getElementById(id) {
      if (id === 'islandLabels') return labels;
      return null;
    },
    createElement() {
      return {
        className: '',
        hidden: false,
        style: {},
        parentNode: null,
        children: [],
        set textContent(v) { writes += 1; this._text = v; },
        get textContent() { return this._text; },
        appendChild(el) { this.children.push(el); el.parentNode = this; },
        setAttribute() {}
      };
    }
  };
  const projected = {
    set() {},
    project() {
      this.z = 0;
      this.x = 0;
      this.y = 0;
    },
    x: 0,
    y: 0,
    z: 0
  };
  const placer = L.createSelfLabelPlacer({ document: doc });
  const ctx = {
    mode: { exploring: true },
    selfNameEnabled: true,
    interiorBusy: false,
    uiOverlay: false,
    miluVisible: true,
    storage: storageMem(),
    loadNick: P.loadNick,
    selfName: '旅人 007',
    playerX: 0,
    playerZ: 0,
    groundY: 0,
    P: 0,
    passengerScale: 1,
    width: 400,
    height: 300,
    camera: {},
    projected: projected,
    peers: []
  };
  writes = 0;
  placer.place(ctx);
  const first = writes;
  placer.place(ctx);
  assert.equal(writes, first, '第二次不應再寫 textContent');
  ctx.selfName = '新名字';
  placer.place(ctx);
  assert.ok(writes > first);
});

test('createSelfLabelPlacer：名稱先解析、節點後建立', () => {
  const labels = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  const doc = {
    getElementById(id) { return id === 'islandLabels' ? labels : null; },
    createElement() {
      return {
        className: '',
        hidden: false,
        style: {},
        parentNode: null,
        children: [],
        set textContent(v) { this._text = v; },
        get textContent() { return this._text; },
        appendChild(el) { this.children.push(el); el.parentNode = this; },
        setAttribute() {}
      };
    }
  };
  const projected = {
    set() {},
    project() { this.z = 0; this.x = 0; this.y = 0; },
    x: 0, y: 0, z: 0
  };
  const placer = L.createSelfLabelPlacer({ document: doc });
  const ctx = {
    mode: { exploring: true },
    selfNameEnabled: true,
    interiorBusy: false,
    uiOverlay: false,
    miluVisible: true,
    storage: storageMem(),
    loadNick: P.loadNick,
    selfName: '旅人 012',
    playerX: 2,
    playerZ: -893,
    groundY: 0,
    P: 0,
    passengerScale: 1,
    width: 400,
    height: 300,
    camera: {},
    projected: projected,
    peers: []
  };
  placer.place(ctx);
  const dbg = placer.getDebug();
  assert.equal(dbg.visible, true);
  assert.equal(dbg.text, '旅人 012');
});

test('createSelfLabelPlacer：開關關閉後重開、節點重建仍帶字', () => {
  const labels = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  let host = labels;
  const doc = {
    getElementById(id) { return id === 'islandLabels' ? host : null; },
    createElement() {
      return {
        className: '',
        hidden: false,
        style: { visibility: '' },
        parentNode: null,
        children: [],
        set textContent(v) { this._text = v; },
        get textContent() { return this._text; },
        appendChild(el) { this.children.push(el); el.parentNode = this; },
        setAttribute() {}
      };
    }
  };
  const projected = {
    set() {},
    project() { this.z = 0; this.x = 0; this.y = 0; },
    x: 0, y: 0, z: 0
  };
  const placer = L.createSelfLabelPlacer({ document: doc });
  const baseCtx = {
    mode: { exploring: true },
    selfNameEnabled: true,
    interiorBusy: false,
    uiOverlay: false,
    miluVisible: true,
    storage: storageMem(),
    loadNick: P.loadNick,
    selfName: '旅人 088',
    playerX: 0,
    playerZ: 0,
    groundY: 0,
    P: 0,
    passengerScale: 1,
    width: 400,
    height: 300,
    camera: {},
    projected: projected,
    peers: []
  };
  placer.place({ ...baseCtx, selfNameEnabled: false });
  assert.equal(placer.getDebug().visible, false);
  placer.place(baseCtx);
  assert.equal(placer.getDebug().text, '旅人 088');
  host = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  placer.place(baseCtx);
  assert.equal(placer.getDebug().text, '旅人 088');
  assert.equal(placer.getDebug().visible, true);
});

test('createSelfLabelPlacer：清空暱稱後顯示 welcome 或旅人', () => {
  const labels = { children: [], appendChild(el) { this.children.push(el); el.parentNode = this; } };
  const doc = {
    getElementById(id) { return id === 'islandLabels' ? labels : null; },
    createElement() {
      return {
        className: '',
        hidden: false,
        style: {},
        parentNode: null,
        children: [],
        set textContent(v) { this._text = v; },
        get textContent() { return this._text; },
        appendChild(el) { this.children.push(el); el.parentNode = this; },
        setAttribute() {}
      };
    }
  };
  const projected = {
    set() {},
    project() { this.z = 0; this.x = 0; this.y = 0; },
    x: 0, y: 0, z: 0
  };
  const placer = L.createSelfLabelPlacer({ document: doc });
  const st = storageMem();
  const ctxWelcome = {
    mode: { exploring: true },
    selfNameEnabled: true,
    interiorBusy: false,
    uiOverlay: false,
    miluVisible: true,
    storage: st,
    loadNick: P.loadNick,
    selfName: '旅人 333',
    playerX: 1,
    playerZ: -890,
    groundY: 0,
    P: 0,
    passengerScale: 1,
    width: 400,
    height: 300,
    camera: {},
    projected: projected,
    peers: []
  };
  placer.place(ctxWelcome);
  assert.equal(placer.getDebug().text, '旅人 333');
  placer.place({ ...ctxWelcome, selfName: '' });
  assert.equal(placer.getDebug().text, '旅人');
  assert.notEqual(placer.getDebug().text, '');
});

test('selfLabelAnchor 室內世界座標不加 peer offset', () => {
  const a = L.selfLabelAnchor({
    driving: false,
    sailing: false,
    playerX: 12.5,
    playerZ: -892.4,
    offsetX: 0,
    offsetZ: 0,
    groundY: 0,
    P: 0,
    passengerScale: 1
  });
  assert.equal(a.kind, 'player');
  assert.equal(a.x, 12.5);
  assert.equal(a.z, -892.4);
});