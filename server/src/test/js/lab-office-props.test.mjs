import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const css = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/css/lab-office.css'),
  'utf8'
);
const js = readFileSync(
  join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public/js/lab-office.js'),
  'utf8'
);
const L = await loadPublicModule('lab-office-logic.js');

test('場景熱點無常駐空白方塊圖示', () => {
  assert.match(css, /\.lab-office-prop::before\s*\{\s*content:\s*none;/);
  assert.doesNotMatch(css, /\.lab-office-prop-main/);
  assert.doesNotMatch(css, /\.lab-office-prop-label/);
  assert.doesNotMatch(css, /\.lab-office-prop-sub/);
});

test('打卡機改走今日狀態頁，下載桌維持下載頁', () => {
  assert.match(js, /clock:\s*'\/checkin'/);
  assert.match(js, /desk:\s*'\/download'/);
  assert.doesNotMatch(js, /download#checkin/);
});

test('主控台獨立熱點座標存在', () => {
  assert.ok(L.OFFICE_CONSOLE_POS);
  assert.ok(L.OFFICE_CONSOLE_POS.label);
  assert.ok(L.OFFICE_PROP_POS.guestbook);
  assert.ok(L.OFFICE_PROP_POS.clock.ix > 0);
  assert.equal(L.OFFICE_PROP_HIT, 14);
  assert.doesNotMatch(css, /min-width:\s*44px/);
  assert.doesNotMatch(css, /min-height:\s*44px/);
});

function publicStates(statuses, now) {
  const characters = {};
  for (const id of ['oki', 'tiger', 'reviewer']) {
    characters[id] = { status: statuses[id], updatedAt: '2026-11-21T03:56:00.000Z' };
  }
  return L.buildCharacterStatesFromPublic({ success: true, characters }, now);
}

function adminStates(opts) {
  const now = opts.now;
  const machines = [];
  if (opts.tiger === 'work') {
    machines.push({
      machine: 'tiger', online: true, lastSeen: now - 1000, agentRunning: true, issue: 1
    });
  } else if (opts.tiger) {
    machines.push({ machine: 'tiger', online: true, lastSeen: now - 30000 });
  }
  if (opts.review === 'work') {
    machines.push({ machine: 'home', online: true, role: 'review', lastSeen: now - 2000 });
  } else if (opts.review) {
    machines.push({ machine: 'home', online: true, lastSeen: now - 30000 });
  }
  return L.buildAllCharacterStates({
    machines: { machines },
    devStatus: {
      now: {
        queue: [1, 2, 3],
        current: opts.oki === 'work' ? { issue: 1, title: '辦公室' } : null
      }
    },
    now
  });
}

function sceneList() {
  const nows = [];
  for (let k = 0; k < 9; k++) nows.push(k * 180_000);
  nows.push(Date.parse('2026-11-21T03:56:00.000Z'));
  const out = [];
  const pubs = [
    { oki: '佇列 0', tiger: '待機', reviewer: '待機' },
    { oki: '工作中', tiger: '待機', reviewer: '待機' },
    { oki: '佇列 0', tiger: '工作中 ×4', reviewer: '審核中 ×2' },
    { oki: '佇列 3', tiger: '待機', reviewer: '審核中' },
    { oki: '佇列 0', tiger: '工作中 ×5', reviewer: '審核中 ×2' }
  ];
  const admins = [
    { tiger: 'work', review: 'idle', oki: 'work' },
    { tiger: 'work', review: 'work', oki: 'work' },
    { tiger: 'idle', review: 'idle', oki: 'idle' },
    { tiger: 'idle', review: 'work', oki: 'idle' }
  ];
  for (const now of nows) {
    for (const s of pubs) out.push(publicStates(s, now));
    for (const s of admins) out.push(adminStates({ ...s, now }));
  }
  return out;
}

test('熱點與名稱不壓角色、泡泡，彼此也不重疊', () => {
  const viewports = [[1280, 720], [390, 844]];
  const scenes = sceneList();
  for (const [vw, vh] of viewports) {
    const layout = L.officePropLayout(vw, vh);
    assert.equal(layout.props.length, 6);
    const guestbook = layout.props.find((p) => p.id === 'guestbook');
    const clock = layout.props.find((p) => p.id === 'clock');
    const gcx = guestbook.hit.x + guestbook.hit.w / 2;
    const gcy = guestbook.hit.y + guestbook.hit.h / 2;
    assert.equal(
      gcx > clock.hit.x && gcx < clock.hit.x + clock.hit.w && gcy > clock.hit.y && gcy < clock.hit.y + clock.hit.h,
      false,
      '簽名簿中心被打卡機接走'
    );
    for (let i = 0; i < layout.props.length; i++) {
      const a = layout.props[i];
      assert.equal(L.rectsIntersect(a.hit, a.name), false, a.id + ' 名稱壓到自己的熱點');
      for (let j = i + 1; j < layout.props.length; j++) {
        const b = layout.props[j];
        assert.equal(L.rectsIntersect(a.hit, b.hit), false, vw + ' 熱點 ' + a.id + '/' + b.id);
        assert.equal(L.rectsIntersect(a.name, b.hit), false, vw + ' 名稱壓熱點 ' + a.id + '/' + b.id);
        assert.equal(L.rectsIntersect(b.name, a.hit), false, vw + ' 名稱壓熱點 ' + b.id + '/' + a.id);
        assert.equal(L.rectsIntersect(a.name, b.name), false, vw + ' 名稱 ' + a.id + '/' + b.id);
      }
    }
    for (const states of scenes) {
      const { rects } = L.officeLayoutRects(states, vw, vh);
      for (const prop of layout.props) {
        for (const body of rects) {
          assert.equal(
            L.rectsIntersect(prop.hit, body.char),
            false,
            vw + ' ' + prop.id + ' 壓到 ' + body.id
          );
          assert.equal(
            L.rectsIntersect(prop.hit, body.bubble),
            false,
            vw + ' ' + prop.id + ' 壓到泡泡 ' + body.id
          );
          assert.equal(
            L.rectsIntersect(prop.name, body.char),
            false,
            vw + ' ' + prop.id + ' 名稱壓到 ' + body.id
          );
          assert.equal(
            L.rectsIntersect(prop.name, body.bubble),
            false,
            vw + ' ' + prop.id + ' 名稱壓到泡泡 ' + body.id
          );
        }
      }
    }
  }
});