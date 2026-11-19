import { test } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const H = await loadPublicModule('island-lab-health.js');
const I = await loadPublicModule('island-interior.js');

const LAB_SCREEN_NDC = Object.freeze({
  cx: 0,
  cy: 0.2 + 0.84,
  cz: -1.74,
  hw: 0.675,
  hh: 0.44,
  frame: 0.12
});

const LAB_DOOR_TEST = Object.freeze({
  x: -1.5,
  signX: -1.0,
  signY: 0.2 + 1.56,
  southZ: -1.85,
  pillarHalfW: 0.47,
  pillarY: 0.2 + 1.55
});

const LAB_EXIT_MAT_CORNERS = Object.freeze([
  { x: -1.3, y: 0.2 + 0.14, z: -1.55 },
  { x: -0.7, y: 0.2 + 0.14, z: -1.55 },
  { x: -0.7, y: 0.2 + 0.14, z: -1.3 },
  { x: -1.3, y: 0.2 + 0.14, z: -1.3 }
]);

function ndcRect(corners) {
  let minX = Infinity;
  let maxX = -Infinity;
  let minY = Infinity;
  let maxY = -Infinity;
  for (let i = 0; i < corners.length; i++) {
    const c = corners[i];
    if (c.vz <= 0) continue;
    if (c.x < minX) minX = c.x;
    if (c.x > maxX) maxX = c.x;
    if (c.y < minY) minY = c.y;
    if (c.y > maxY) maxY = c.y;
  }
  return { minX, maxX, minY, maxY };
}

function ndcContainsPoint(rect, pt) {
  return pt.vz > 0
    && pt.x >= rect.minX
    && pt.x <= rect.maxX
    && pt.y >= rect.minY
    && pt.y <= rect.maxY;
}

test('位元組與百分比格式化', () => {
  assert.equal(H.formatTeaBytes(500), '500 B');
  assert.equal(H.formatTeaBytes(1536), '1.5 KB');
  assert.equal(H.formatTeaBytes(1288490188), '1.2 GB');
  assert.equal(H.formatTeaBytes(4294967296), '4.0 GB');
  assert.equal(H.formatTeaBytes(null), '取樣中');
  assert.equal(H.formatTeaPercent(12.34), '12.3%');
  assert.equal(H.formatTeaPercent(null), '取樣中');
  assert.equal(H.formatTeaPercent(Number.NaN), '取樣中');
});

test('製茶機行文案不含 NaN', () => {
  const sample = {
    success: true,
    processCpuPercent: 8.2,
    systemCpuPercent: 14.5,
    osUsedMemoryBytes: 1288490188,
    osTotalMemoryBytes: 4294967296,
    heapUsedBytes: 100,
    heapMaxBytes: 200
  };
  assert.match(H.formatTeaCpuLine(sample), /8\.2%/);
  assert.match(H.formatTeaMemoryLine(sample), /1\.2 GB \/ 4\.0 GB/);
  const vm = H.teaHealthViewModel(sample);
  assert.equal(vm.ok, true);
  assert.ok(vm.cpuBar != null && vm.cpuBar >= 0 && vm.cpuBar <= 1);
  assert.ok(vm.memBar != null && vm.memBar > 0 && vm.memBar < 1);
  assert.equal(H.formatTeaCpuLine({ success: false }), '製茶機狀態暫時讀不到');
  assert.match(H.formatTeaStatusCompactLine(sample), /製茶機 CPU 8\.2%/);
  assert.match(H.formatTeaStatusCompactLine(sample), /1\.2 GB \/ 4\.0 GB/);
  assert.equal(H.formatTeaStatusCompactLine({ success: false }), '製茶機狀態暫時讀不到');
  assert.equal(H.formatTeaStatusCompactLine(null), '製茶機狀態暫時讀不到');
});

test('時間軸排序截斷與空陣列', () => {
  const events = [
    { at: 1000, action: '開了 issue', ref: '#1', title: '早期' },
    { at: 3000, action: '合併', ref: 'p2', title: '較新' },
    { at: 2000, action: '開了 PR', ref: 'p1', title: '中間' }
  ];
  const lines = H.formatLabTimelineScreenLines(events, { maxEvents: 2, titleMax: 10 });
  assert.equal(lines.length, 2);
  assert.ok(lines[0].includes('合併'));
  assert.ok(lines[0].includes('較新'));
  assert.deepEqual(H.formatLabTimelineScreenLines([]), ['近 7 天沒有動態']);
  const compact = H.formatLabTimelineScreenLines(events, {
    maxEvents: 2,
    titleMax: 10,
    compactTime: true
  });
  assert.ok(!compact[0].includes('台北'));
  assert.ok(!compact[0].includes('NaN'));
});

test('開發進度與時間軸合併', () => {
  const data = {
    success: true,
    stale: false,
    now: {
      current: { pair: 'i141', title: '數據牆', status: '實作中' },
      next: null,
      queue: []
    },
    stats: { mergedThisWeek: 1, openIssues: 2 },
    timeline: [
      { at: Date.UTC(2026, 10, 17, 6, 30, 0), action: '開了 issue', ref: '#141', title: '研發樓牆' }
    ]
  };
  const lines = H.formatLabDevScreenWithTimeline(data, { screen: true, maxQueue: 2, maxTimeline: 5 });
  assert.ok(lines.join('\n').includes('現在在做'));
  assert.ok(lines.some((line) => line.startsWith('【最近動態】（台北時間）')));
  assert.ok(lines.some((line) => line.includes('開了 issue') && line.includes('#141')));
  assert.ok(!lines.join('\n').includes('詳細進度請到主島施工告示牌'));

  const fail = H.formatLabDevScreenWithTimeline({ success: false, message: '開發進度暫時讀不到' }, { screen: true });
  assert.ok(fail.join('\n').includes('開發進度暫時讀不到'));
  assert.ok(!fail.join('\n').includes('【最近動態】'));
});

test('南牆螢幕排版最壞情況不截斷', () => {
  const queue = [];
  for (let q = 0; q < 6; q++) {
    queue.push({
      order: q,
      pair: 'i' + (140 + q),
      title: '排隊標題' + q,
      status: '排隊'
    });
  }
  const timeline = [];
  for (let t = 0; t < 9; t++) {
    timeline.push({
      at: Date.UTC(2026, 10, 18, 14, t, 0),
      action: '動態',
      ref: '#' + (200 + t),
      title: '標題' + t
    });
  }
  const data = {
    success: true,
    now: {
      current: { pair: 'i141', title: '現在這張票名稱很長要截斷測試', status: '實作中' },
      next: { pair: 'i142', title: '下一張也很長', status: '排隊' },
      queue: queue
    },
    stats: { mergedThisWeek: 3, openIssues: 12 },
    timeline: timeline
  };
  let lines = H.formatLabDevScreenWithTimeline(data, { screen: true, maxQueue: 2, maxTimeline: 5 });
  lines = H.insertLabTeaStatusLine(lines, {
    success: true,
    processCpuPercent: 12,
    osUsedMemoryBytes: 8e9,
    osTotalMemoryBytes: 16e9
  });
  lines.splice(
    lines.findIndex((l) => l.startsWith('【最近動態】')),
    0,
    '小貓 在線・跑 #99',
    '猛虎 離線'
  );
  const rows = H.layoutLabScreenLines(lines);
  const bottom = H.layoutLabScreenBottomY(rows);
  assert.ok(bottom <= 768 - 16, '最後一行 y+lineH ≤ 752，實際 ' + bottom);
  const tlStart = lines.findIndex((l) => l.startsWith('【最近動態】'));
  const tlLines = lines.slice(tlStart + 1);
  assert.equal(tlLines.length, 5, '時間軸固定 5 筆');
  const tlRows = rows.filter((r) => tlLines.includes(r.text));
  assert.equal(tlRows.length, 5);
  assert.ok(tlRows.every((r) => r.font.includes('30px') && r.font.includes('600')));
});

test('數據牆 NDC 與朝向', () => {
  const room = I.interiorByBuilding('computer-site');
  const spawn = { x: room.spawn.x, z: room.spawn.z };
  const viewports = [
    { aspect: 1280 / 720, height: 720 },
    { aspect: 1440 / 900, height: 900 },
    { aspect: 390 / 844, height: 844 }
  ];
  const screenNdcSamples = [];
  for (let v = 0; v < viewports.length; v++) {
    const vp = viewports[v];
    const pose = I.labInteriorCameraForAspect(vp.aspect);
    const look = I.labInteriorLookTarget(room, spawn, vp.aspect);
    const eye = I.labInteriorCameraEye(pose, look, vp.aspect);
    const wall = H.labTeaWallLayout(0.2, eye.x, eye.z);
    const corners = H.labTeaWallCorners(wall).map((p) =>
      I.labInteriorProjectNdc(p.x, p.y, p.z, pose, look, vp.aspect)
    );
    const topLimit = I.labInteriorScreenTopNdcLimit(vp.height);
    if (vp.height === 720 || vp.height === 900) {
      for (let c = 0; c < corners.length; c++) {
        const ndc = corners[c];
        assert.ok(ndc.vz > 0, '牆角須在鏡頭前');
        assert.ok(Math.abs(ndc.x) <= 0.94, 'NDC x in [-0.94,0.94]');
        assert.ok(ndc.y >= -0.94 && ndc.y <= topLimit + 0.02, 'NDC y 在工具列留白內');
      }
    }
    const cos = H.labTeaWallFacingCos(wall, eye.x, eye.z);
    assert.ok(cos >= 0.8, '法線與視線 cos≥0.8，實際 ' + cos.toFixed(3));
    const panelRect = ndcRect(corners);
    const d = LAB_DOOR_TEST;
    const doorPoints = [
      I.labInteriorProjectNdc(d.signX, d.signY, d.southZ - 0.03, pose, look, vp.aspect),
      I.labInteriorProjectNdc(d.x - d.pillarHalfW, d.pillarY, d.southZ, pose, look, vp.aspect),
      I.labInteriorProjectNdc(d.x + d.pillarHalfW, d.pillarY, d.southZ, pose, look, vp.aspect)
    ];
    for (let i = 0; i < doorPoints.length; i++) {
      assert.equal(
        ndcContainsPoint(panelRect, doorPoints[i]),
        false,
        '數據牆不可蓋住出口標示'
      );
    }
    for (let m = 0; m < LAB_EXIT_MAT_CORNERS.length; m++) {
      const corner = LAB_EXIT_MAT_CORNERS[m];
      const pt = I.labInteriorProjectNdc(corner.x, corner.y, corner.z, pose, look, vp.aspect);
      assert.equal(
        ndcContainsPoint(panelRect, pt),
        false,
        '數據牆不可蓋住出口地墊（角點 ' + m + '）'
      );
    }
    assert.ok(wall.w >= 0.5, '面板寬度至少 0.5 m');
    const s = LAB_SCREEN_NDC;
    const scrCorners = [
      I.labInteriorProjectNdc(s.cx - s.hw - s.frame, s.cy + s.hh + s.frame, s.cz, pose, look, vp.aspect),
      I.labInteriorProjectNdc(s.cx + s.hw + s.frame, s.cy + s.hh + s.frame, s.cz, pose, look, vp.aspect),
      I.labInteriorProjectNdc(s.cx - s.hw - s.frame, s.cy - s.hh - s.frame, s.cz, pose, look, vp.aspect),
      I.labInteriorProjectNdc(s.cx + s.hw + s.frame, s.cy - s.hh - s.frame, s.cz, pose, look, vp.aspect)
    ];
    const scrRect = ndcRect(scrCorners);
    const overlap = panelRect.minX < scrRect.maxX
      && panelRect.maxX > scrRect.minX
      && panelRect.minY < scrRect.maxY
      && panelRect.maxY > scrRect.minY;
    assert.equal(overlap, false, '數據牆不可蓋主螢幕');
    screenNdcSamples.push(panelRect);
  }
  assert.equal(
    I.labInteriorFramingOk(1280 / 720, 720, room),
    true
  );
  assert.equal(
    I.labInteriorFramingOk(1440 / 900, 900, room),
    true
  );
  assert.equal(
    I.labInteriorFramingOk(390 / 844, 844, room),
    true
  );
});