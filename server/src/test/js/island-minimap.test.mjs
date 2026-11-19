// node --test server/src/test/js/island-minimap.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const M = await loadPublicModule('island-minimap.js');
const L = await loadPublicModule('island-layout.js');
const C = await loadPublicModule('island-city-data.js');

test('世界座標 ↔ 地圖：主島、城市島在上、電腦島在下', () => {
  const b = M.worldMapBounds();
  const w = 200;
  const h = 160;
  const main = L.islandById('main');
  const city = L.islandById('city');
  const computer = L.islandById('computer');
  const tcm = L.islandById('tcm');

  const pMain = M.worldToMap(main.x, main.z, b, w, h);
  const pCity = M.worldToMap(city.x, city.z, b, w, h);
  const pComputer = M.worldToMap(computer.x, computer.z, b, w, h);
  const pTcm = M.worldToMap(tcm.x, tcm.z, b, w, h);

  assert.ok(pCity.py < pComputer.py, '城市島（−Z）應在電腦島上方');
  assert.ok(pCity.py < pMain.py, '城市島比主島更靠北（畫面上方）');
  assert.ok(pTcm.px < pMain.px, '推拿島在西側');

  const back = M.mapToWorld(pMain.px, pMain.py, b, w, h);
  assert.ok(Math.abs(back.x - main.x) < 0.05);
  assert.ok(Math.abs(back.z - main.z) < 0.05);

  assert.deepEqual(M.worldNorm(0, 0), M.worldNorm(main.x, main.z));
  const cityNorm = M.worldNorm(city.x, city.z);
  const compNorm = M.worldNorm(computer.x, computer.z);
  assert.ok(cityNorm.v < compNorm.v);
});

test('區域名稱：島上、橋上、海域與室內', () => {
  assert.equal(M.resolveAreaName({ x: 0, z: 0 }), '神之島');
  assert.equal(M.resolveAreaName({ x: 0, z: -22 }), '城市島');
  const bridge = L.BRIDGES[0];
  const midX = (bridge.ax + bridge.bx) * 0.5;
  const midZ = (bridge.az + bridge.bz) * 0.5;
  assert.equal(M.resolveAreaName({ x: midX, z: midZ }), '橋上');
  assert.equal(M.resolveAreaName({ x: 40, z: 40 }), '海域');

  const tcmRoom = M.interiorByRoomId('tcm-room');
  assert.equal(M.resolveAreaName({ interiorRoom: tcmRoom }), '推拿館');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('casino-room') }), '賭場');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('shop-room') }), '神之島');
  assert.equal(M.resolveAreaName({ interiorRoom: M.interiorByRoomId('lab-room') }), '研發樓');
});

test('室內標記含推拿館經絡研習室門', () => {
  const room = M.interiorByRoomId('tcm-room');
  const markers = M.collectInteriorMarkers(room);
  const study = markers.find((mk) => mk.id === 'tcm-study');
  assert.ok(study, '應有 tcm-study 互動點');
  assert.equal(study.label, '進入經絡研習室');
  const iz = (room.interacts || []).find((row) => row.id === 'tcm-study');
  assert.equal(study.x, iz.x);
  assert.equal(study.z, iz.z);
});

test('室外標記從模組讀取（門、碼頭、粉紅車、施工牌）', () => {
  const doors = M.collectDoorMarkers();
  const tcmDoor = doors.find((d) => d.id === 'tcm-site');
  const board = doors.find((d) => d.id === 'construction-board');
  assert.ok(tcmDoor);
  assert.ok(board);
  const layoutBoard = L.BUILDINGS.find((b) => b.id === 'construction-board');
  assert.equal(board.x, layoutBoard.door.x);
  assert.equal(board.z, layoutBoard.door.z);

  const boats = M.collectBoatMarkers();
  assert.ok(boats.some((b) => b.id === 'main-berth'));
  assert.ok(boats.some((b) => b.id === 'city-board'));

  const car = M.collectCarMarker(null);
  const pink = C.pinkCarWorld();
  assert.equal(car.x, pink.x);
  assert.equal(car.z, pink.z);
});

test('syncPlayerAreaTitle：戶外與推拿館室內', () => {
  const doc = {
    values: {},
    getElementById(id) {
      if (id === 'islandHudPlayerTitleText') return this.textEl;
      return null;
    },
    querySelector(sel) {
      return sel === '.island-hud-player-title-mark' ? this.markEl : null;
    },
    textEl: { textContent: '' },
    markEl: { textContent: '' }
  };
  const cache = { name: null };
  M.syncPlayerAreaTitle(doc, { player: { x: 0, z: 0 } }, cache);
  assert.equal(doc.textEl.textContent, '神之島');
  assert.equal(doc.markEl.textContent, '神');
  const tcmRoom = M.interiorByRoomId('tcm-room');
  M.syncPlayerAreaTitle(doc, { interiorRoom: tcmRoom, player: { x: 0, z: 0 } }, cache);
  assert.equal(doc.textEl.textContent, '推拿館');
  assert.equal(doc.markEl.textContent, '推');
});

test('createMinimapHud：手機寬 tick 不顯示小地圖，桌機寬會顯示', () => {
  let mobile = true;
  const listeners = [];
  const mobileMq = {
    get matches() { return mobile; },
    addEventListener(type, fn) { listeners.push(fn); },
    removeEventListener() {}
  };
  const root = { id: 'islandMinimap', hidden: true, classList: { toggle() {} } };
  const collapseBtn = {
    textContent: '−',
    setAttribute() {},
    addEventListener() {}
  };
  const expandBtn = { addEventListener() {}, querySelector: () => null, textContent: '', insertAdjacentHTML() {} };
  const hudCanvas = { getContext: () => null };
  const doc = {
    getElementById(id) {
      if (id === 'islandMinimap') return root;
      if (id === 'islandMinimapCollapse') return collapseBtn;
      if (id === 'islandMinimapExpand') return expandBtn;
      if (id === 'islandMinimapCanvas') return hudCanvas;
      if (id === 'islandHudPlayerTitleText') return { textContent: '' };
      if (id === 'islandMapSheet') return { hidden: true };
      return null;
    },
    querySelector() { return { textContent: '' }; }
  };
  const hud = M.createMinimapHud({
    document: doc,
    window: { matchMedia: () => mobileMq },
    isExploring: () => true,
    getPaintState: () => ({ player: { x: 0, z: 0 }, interiorRoom: null })
  });
  hud.tick(1000);
  assert.equal(root.hidden, true, '手機探索中小地圖保持隱藏');
  mobile = false;
  listeners.forEach((fn) => fn());
  hud.tick(2000);
  assert.equal(root.hidden, false, '桌機探索中顯示小地圖');
  hud.destroy();
});

test('收合狀態下縮小與放大鈕內容不同', () => {
  const root = { hidden: false, classList: { toggle() {} } };
  let collapsed = false;
  const collapseBtn = {
    textContent: '−',
    setAttribute() {},
    addEventListener(type, fn) {
      if (type === 'click') collapseBtn._click = fn;
    }
  };
  const expandBtn = {
    textContent: '',
    innerHTML: '',
    addEventListener() {},
    querySelector: () => ({ tagName: 'svg' }),
    insertAdjacentHTML() {}
  };
  const doc = {
    getElementById(id) {
      const map = {
        islandMinimap: root,
        islandMinimapCollapse: collapseBtn,
        islandMinimapExpand: expandBtn,
        islandMinimapCanvas: { getContext: () => null },
        islandMapSheet: { hidden: true }
      };
      return map[id] || null;
    },
    querySelector() { return null; }
  };
  const hud = M.createMinimapHud({
    document: doc,
    window: { matchMedia: () => ({ matches: false, addEventListener() {}, removeEventListener() {} }) },
    isExploring: () => true,
    getPaintState: () => ({ player: { x: 0, z: 0 } })
  });
  hud.setCollapsed(true);
  assert.equal(collapseBtn.textContent, '▾');
  assert.notEqual(collapseBtn.textContent, expandBtn.textContent);
  assert.ok(expandBtn.querySelector('svg'), '放大鈕為 SVG');
  hud.destroy();
});

test('shouldOpenMapWithKey：輸入框聚焦不攔截，探索中可按 M', () => {
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: true,
    key: 'M'
  }), false);
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: false,
    key: 'M',
    mapOpen: false,
    moreOpen: false
  }), true);
  assert.equal(M.shouldOpenMapWithKey({
    exploring: true,
    typing: false,
    key: 'M',
    mapOpen: true
  }), false);
});