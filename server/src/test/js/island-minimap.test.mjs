// node --test server/src/test/js/island-minimap.test.mjs
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicDir = join(dirname(fileURLToPath(import.meta.url)), '..', '..', 'main', 'resources', 'public');
const indexHtml = readFileSync(join(publicDir, 'index.html'), 'utf8');

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

function layoutIslandGeo(layouts) {
  return layouts.map((row) => ({
    id: row.id,
    centerX: row.centerX,
    centerY: row.centerY,
    rx: row.rx,
    ry: row.ry
  }));
}

function assertLayoutsNoOverlap(layouts, width, height) {
  const geo = layoutIslandGeo(layouts);
  for (let a = 0; a < layouts.length; a++) {
    const row = layouts[a];
    const touch = row.touchBox;
    assert.ok(touchBoxInside(touch, width, height), row.id + ' 點擊區超出地圖');
    assert.ok(
      M.pointInIslandEllipse(row.anchorX, row.anchorY, row.centerX, row.centerY, row.rx, row.ry, 0.88),
      row.id + ' 名牌中心應落在自己島內'
    );
    assert.equal(
      M.pinTouchBoxHitsForeignIsland(touch, row.id, geo, 0.9),
      false,
      row.id + ' 點擊區壓到別座島'
    );
    for (let b = a + 1; b < layouts.length; b++) {
      const other = layouts[b].touchBox;
      const overlap = !(touch.right <= other.left || touch.left >= other.right
        || touch.bottom <= other.top || touch.top >= other.bottom);
      assert.equal(overlap, false, layouts[a].id + ' 與 ' + layouts[b].id + ' 名牌重疊');
    }
  }
}

function touchBoxInside(box, width, height) {
  return box.left >= 2 && box.top >= 2 && box.right <= width - 2 && box.bottom <= height - 2;
}

const MAP_LAYOUT_VPS = [
  [1280, 800],
  [1280, 720],
  [412, 915],
  [390, 844],
  [360, 740]
];

test('大地圖名牌版面：多視窗六島在界內、不重疊', () => {
  for (let v = 0; v < MAP_LAYOUT_VPS.length; v++) {
    const vw = MAP_LAYOUT_VPS[v][0];
    const size = M.largeMapCanvasCssSize(vw, MAP_LAYOUT_VPS[v][1]);
    const layouts = M.islandPinLayout(size.width, size.height);
    assert.equal(layouts.length, 6, vw + ' 應有六島');
    assertLayoutsNoOverlap(layouts, size.width, size.height);
    assert.ok(size.width <= size.contentWidth + 0.5, vw + ' canvas 寬 ≤ wrap 內容區');
  }
  const s390 = M.largeMapCanvasCssSize(390, 844);
  const l390 = M.islandPinLayout(s390.width, s390.height);
  const tcm = l390.find((row) => row.id === 'tcm');
  assert.ok(tcm && tcm.fontPx >= 13, '手機字級 ≥ 13px');
  assert.ok(tcm.lines && tcm.lines.length >= 2, '推拿島手機多行名牌');
  const s1280 = M.largeMapCanvasCssSize(1280, 800);
  const desk = M.islandPinLayout(s1280.width, s1280.height).find((row) => row.id === 'main');
  assert.ok(desk && desk.fontPx >= 15 && desk.fontPx <= 17, '桌機字級 15–16px');
  assert.ok(s1280.innerHeight <= s1280.availHeight + 1, '1280×800 canvas 內高不超出面板可用高');
});

test('largeMapCanvasSizeFromWrapContent：canvas 不超出內容區', () => {
  const contentW = 347;
  const size = M.largeMapCanvasSizeFromWrapContent(contentW, 520);
  assert.equal(size.contentWidth, contentW);
  assert.ok(size.width <= contentW + 0.01, '含邊框仍不超過 wrap 寬');
});

test('largeMapCanvasCssSize：常見視窗不撐出捲軸', () => {
  for (let i = 0; i < MAP_LAYOUT_VPS.length; i++) {
    const size = M.largeMapCanvasCssSize(MAP_LAYOUT_VPS[i][0], MAP_LAYOUT_VPS[i][1]);
    assert.ok(size.innerHeight <= size.availHeight + 1, MAP_LAYOUT_VPS[i] + ' 內高 ≤ 可用高');
    assert.ok(size.width <= size.contentWidth + 0.5, MAP_LAYOUT_VPS[i] + ' canvas 不超內容寬');
  }
});

test('名牌避開玩家標記：推拿館門口', () => {
  const size = M.largeMapCanvasCssSize(390, 844);
  const room = M.interiorByRoomId('tcm-room');
  const player = M.outdoorPlayerPoseFromInterior(room);
  const pt = M.playerMapPointFromWorld(player, size.width, size.height);
  assert.ok(pt, '門口座標');
  const obstacle = {
    x: pt.x,
    y: pt.y,
    radius: M.largeMapPlayerLabelAvoidRadius(1, true)
  };
  const layouts = M.islandPinLayout(size.width, size.height, {
    playerObstacle: obstacle,
    playerIslandId: 'tcm'
  });
  const tcm = layouts.find((row) => row.id === 'tcm');
  assert.ok(tcm, 'tcm 名牌');
  const drawPt = M.largeMapPlayerDrawPoint(pt.x, pt.y, tcm, 14);
  assert.equal(
    M.touchBoxOverlapsCircle(tcm.box, drawPt.x, drawPt.y, obstacle.radius),
    false,
    '推拿島名牌與偏移後的玩家圖示不重疊'
  );
});

test('大地圖標題：室內房名與群島', () => {
  const room = M.interiorByRoomId('tcm-room');
  assert.equal(M.resolveLargeMapSheetTitle(room, false), '推拿館');
  assert.equal(M.resolveLargeMapSheetTitle(room, true), '群島地圖');
  assert.equal(M.resolveLargeMapSheetTitle(null, false), '群島地圖');
  assert.equal(M.resolveLargeMapModeToggleLabel(false), '群島地圖');
  assert.equal(M.resolveLargeMapModeToggleLabel(true), '室內平面圖');
});

test('shouldJumpIslandOnPointerUp：同島小位移才跳', () => {
  const down = { islandId: 'main', x: 100, y: 80 };
  assert.equal(M.shouldJumpIslandOnPointerUp(down, { islandId: 'main', x: 105, y: 84 }, 10), 'main');
  assert.equal(M.shouldJumpIslandOnPointerUp(down, { islandId: 'city', x: 105, y: 84 }, 10), null);
  assert.equal(M.shouldJumpIslandOnPointerUp(down, { islandId: 'main', x: 120, y: 80 }, 10), null);
  assert.equal(M.shouldJumpIslandOnPointerUp(null, { islandId: 'main', x: 100, y: 80 }, 10), null);
});

test('installMapJumpGhostClickGuard：pointerup 跳島後緊接的 click 被吞掉、超時後正常', async () => {
  const doc = {
    listeners: {},
    addEventListener(type, fn, opts) {
      if (!this.listeners[type]) this.listeners[type] = [];
      this.listeners[type].push({ fn, capture: !!(opts && opts.capture) });
    },
    removeEventListener(type, fn) {
      const list = this.listeners[type] || [];
      const idx = list.findIndex((row) => row.fn === fn);
      if (idx >= 0) list.splice(idx, 1);
    },
    dispatch(type, event) {
      const list = this.listeners[type] || [];
      for (let pass = 0; pass < 2; pass++) {
        const capture = pass === 0;
        for (let i = 0; i < list.length; i++) {
          if (list[i].capture !== capture) continue;
          list[i].fn(event);
          if (event._stopped) return;
        }
      }
    }
  };
  function makePointerEvent() {
    return {
      _stopped: false,
      preventDefault() {},
      stopPropagation() { this._stopped = true; },
      stopImmediatePropagation() { this._stopped = true; }
    };
  }
  let bubbled = 0;
  doc.addEventListener('click', () => { bubbled += 1; }, { capture: false });
  M.installMapJumpGhostClickGuard(doc, 40);
  doc.dispatch('click', makePointerEvent());
  assert.equal(bubbled, 0, '守護期間合成 click 不應落到場景');
  doc.dispatch('mousedown', makePointerEvent());
  doc.dispatch('mouseup', makePointerEvent());
  assert.equal(bubbled, 0);
  await new Promise((resolve) => setTimeout(resolve, 55));
  doc.dispatch('click', makePointerEvent());
  assert.equal(bubbled, 1, '超時後 click 應恢復正常');
});

test('室內 → 群島：島別與門口座標', () => {
  const cases = [
    ['shop-room', 'main'],
    ['casino-room', 'casino'],
    ['tcm-room', 'tcm'],
    ['lab-room', 'computer']
  ];
  for (let i = 0; i < cases.length; i++) {
    const room = M.interiorByRoomId(cases[i][0]);
    assert.equal(M.islandIdForInteriorRoom(room), cases[i][1], cases[i][0]);
    const pose = M.outdoorPlayerPoseFromInterior(room);
    assert.ok(pose && Number.isFinite(pose.x) && Number.isFinite(pose.z), cases[i][0] + ' 門口座標');
  }
});

test('大地圖點擊命中：圈心命中該島、海面 null', () => {
  const size = M.largeMapCanvasCssSize(1280, 800);
  const layouts = M.islandPinLayout(size.width, size.height);
  for (let i = 0; i < layouts.length; i++) {
    const row = layouts[i];
    const hit = M.islandAtMapPoint(row.centerX, row.centerY, size.width, size.height, 0);
    assert.equal(hit, row.id, row.id + ' 圈心應命中');
  }
  const miss = M.islandAtMapPoint(size.width * 0.08, size.height * 0.92, size.width, size.height, 12);
  assert.equal(miss, null, '左下海面不應命中');
});

test('大地圖標記過濾：不含 npc／門口／施工牌／停放車', () => {
  const large = M.collectLargeMapMarkers({ vehicleState: null, boatState: null, peers: [] });
  const small = M.collectOutdoorMarkers({ vehicleState: null, boatState: null, peers: [] });
  assert.ok(small.some((mk) => mk.kind === 'npc'));
  assert.ok(small.some((mk) => mk.kind === 'door'));
  assert.ok(small.some((mk) => mk.kind === 'board'));
  assert.equal(large.some((mk) => mk.kind === 'npc'), false);
  assert.equal(large.some((mk) => mk.kind === 'door'), false);
  assert.equal(large.some((mk) => mk.kind === 'board'), false);
  assert.equal(large.some((mk) => mk.kind === 'car'), false);
  const berthIds = large.filter((mk) => mk.kind === 'berth').map((mk) => mk.id);
  assert.equal(new Set(berthIds).size, berthIds.length, '碼頭每站一個');
  assert.ok(berthIds.length >= 3);
});

test('index.html 已移除 island-map-isle、保留 data-island 名牌', () => {
  assert.doesNotMatch(indexHtml, /island-map-isle/);
  assert.doesNotMatch(indexHtml, /island-map-grid/);
  assert.match(indexHtml, /class="island-map-pin"[^>]*data-island="main"/);
  assert.match(indexHtml, /data-island="tcm"/);
  assert.match(indexHtml, /island-map-pin-label/);
  assert.match(indexHtml, /island-map-legend-peer-dot/);
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