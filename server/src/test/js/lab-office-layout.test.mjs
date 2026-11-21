import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('lab-office-logic.js');

const css = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/css/lab-office.css'),
  'utf8'
);

const TIGER_WORKING = {
  machine: 'tiger',
  online: true,
  lastSeen: 1_080_000,
  agentRunning: true,
  issue: 254,
  round: 4,
  model: 'grok-4.7',
  runStartedAt: 1_000_000,
  branch: 'ai/issue-254',
  commit: 'f2cdbae83d24aea832968cd6708bf62ed1af8639',
  battery: 76
};

const REVIEW_WORKING = {
  machine: 'home',
  online: true,
  role: 'review',
  model: 'claude',
  round: 4
};

function statesFor(opts) {
  const machines = [];
  const now = opts.now == null ? 1_090_000 : opts.now;
  if (opts.tiger) {
    machines.push(opts.tiger === 'work' ? TIGER_WORKING : { machine: 'tiger', online: true, lastSeen: now - 30_000 });
  }
  if (opts.review) {
    machines.push(opts.review === 'work' ? { ...REVIEW_WORKING, lastSeen: now - 20_000 } : { machine: 'home', online: true, lastSeen: now - 30_000 });
  }
  return L.buildAllCharacterStates({
    machines: { machines },
    devStatus: {
      now: {
        queue: [1, 2, 3, 4, 5, 6, 7],
        current: { issue: 254, title: '虛擬辦公室' }
      }
    },
    now
  });
}

const SCENES = [
  { name: '猛虎工作、審核待機', tiger: 'work', review: 'idle' },
  { name: '兩人工作', tiger: 'work', review: 'work' },
  { name: '兩人待機', tiger: 'idle', review: 'idle' },
  { name: '猛虎待機、審核工作', tiger: 'idle', review: 'work' }
];

const VIEWPORTS = [
  { name: '1280×720', w: 1280, h: 720 },
  { name: '390×844', w: 390, h: 844 }
];

function pairs(rects) {
  const out = [];
  for (let i = 0; i < rects.length; i++) {
    for (let j = i + 1; j < rects.length; j++) out.push([rects[i], rects[j]]);
  }
  return out;
}

test('手機沿用家具站位，不再釘死底列', () => {
  assert.doesNotMatch(css, /data-char="[^"]+"\][^{]*\{[^}]*!important/);
  const now = 1_090_000;
  const work = L.buildAllCharacterStates({
    machines: {
      machines: [TIGER_WORKING, { ...REVIEW_WORKING, lastSeen: now - 20_000 }]
    },
    devStatus: { now: { queue: [1], current: { issue: 1, title: '辦公室' } } },
    now
  });
  const idle = L.buildAllCharacterStates({
    machines: { machines: [] },
    devStatus: { now: { queue: [], current: null } },
    now
  });
  const byId = (list, id) => list.find((s) => s.id === id);
  assert.equal(byId(work, 'oki').spot, 'desk-oki');
  assert.equal(byId(work, 'tiger').spot, 'desk-tiger');
  assert.equal(byId(work, 'reviewer').spot, 'desk-review');
  assert.equal(byId(idle, 'oki').spot, 'coffee');
  assert.notEqual(byId(idle, 'tiger').spot, 'desk-tiger');
  assert.equal(L.chibiAssetPath('oki', 'plan'), L.chibiAssetPath('oki', 'idle'));
  assert.notEqual(L.chibiAssetPath('oki', 'work'), L.chibiAssetPath('oki', 'idle'));
  const phone = L.officeLayoutRects(work, 390, 844);
  const desk = L.officeLayoutRects(work, 1280, 720);
  for (let i = 0; i < work.length; i++) {
    assert.equal(phone.rects[i].spot, desk.rects[i].spot);
    const pos = L.SPOT_POS[phone.rects[i].spot];
    const ax = phone.stage.w * pos.x / 100;
    const mid = phone.rects[i].char.x + phone.rects[i].char.w / 2;
    assert.ok(Math.abs(mid - ax) < 1.5, phone.rects[i].id);
  }
});

test('審核員待機點在畫面右半', () => {
  for (const now of [0, 180_000, 360_000, 1_090_000]) {
    const states = statesFor({ tiger: 'work', review: 'idle', now });
    const rev = states.find((s) => s.id === 'reviewer');
    assert.equal(rev.spot, 'idle-review');
    assert.ok(L.SPOT_POS[rev.spot].x >= 65 && L.SPOT_POS[rev.spot].x <= 75, rev.spot);
  }
});

test('桌機與手機三人及氣泡兩兩不相交', () => {
  const nows = [0, 180_000, 360_000, 540_000, 1_090_000];
  for (const scene of SCENES) {
    for (const now of nows) {
      const states = statesFor({ ...scene, now });
      assert.equal(states.length, 3);
      for (const vp of VIEWPORTS) {
        const { rects } = L.officeLayoutRects(states, vp.w, vp.h);
        assert.equal(rects.length, 3);
        for (const [a, b] of pairs(rects)) {
          assert.equal(
            L.rectsIntersect(a.char, b.char),
            false,
            scene.name + ' ' + vp.name + ' 角色 ' + a.id + '/' + b.id
          );
          assert.equal(
            L.rectsIntersect(a.bubble, b.bubble),
            false,
            scene.name + ' ' + vp.name + ' 氣泡 ' + a.id + '/' + b.id
          );
          assert.equal(
            L.rectsIntersect(a.bubble, b.char),
            false,
            scene.name + ' ' + vp.name + ' 氣泡壓到 ' + b.id
          );
          assert.equal(
            L.rectsIntersect(b.bubble, a.char),
            false,
            scene.name + ' ' + vp.name + ' 氣泡壓到 ' + a.id
          );
        }
      }
    }
  }
});
