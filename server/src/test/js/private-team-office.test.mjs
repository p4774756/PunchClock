import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const {
  tigerFromPublic,
  bubbleTextForStatus,
  capsulesForActive,
  devQueueFromStatus,
  formatOfficeClock,
  fillStatusCard,
  syncStatusBodyScroll,
  isSafePublicText,
  phraseForDoneDisplay,
  phraseForActiveDisplay,
  renderActiveBlock,
  completedTodayKeys,
  hasNewCompletedToday,
  secretarySpriteUrlForStatus,
  progressBoardViewFromTiger,
  taskProgressFromTiger
} = await loadPublicModule('private-team-office.js');

test('progressBoardViewFromTiger 單號與今日進度', () => {
  const idle = progressBoardViewFromTiger({ active: [], completedToday: [{ issue: 1 }] }, 0);
  assert.equal(idle.statusLine, '待命中');
  assert.match(idle.metaLine, /今日已完成/);
  const active = progressBoardViewFromTiger(
    {
      active: [{ issue: 495, phrase: '牆上進度面板', round: 2, minutes: 8 }],
      completedToday: [{ issue: 1 }]
    },
    2
  );
  assert.equal(active.issueLine, '#495');
  assert.equal(active.statusLine, '進行中');
  assert.equal(active.phraseLine, '牆上進度面板');
  assert.match(active.metaLine, /第 2 輪/);
  assert.equal(active.todayLabel, '今日工作量');
  assert.equal(active.todayFraction, 1 / 4);
  assert.equal(active.todayCountText, '1/4');
  const bad = progressBoardViewFromTiger(
    { active: [{ issue: 9, phrase: 'ai/issue-9 hack' }], completedToday: [] },
    0
  );
  assert.equal(bad.phraseLine, '進行中');
  const vague = progressBoardViewFromTiger(
    { active: [{ issue: 502, phrase: '正在處理一項工作', title: '公開 API 標題' }], completedToday: [] },
    0
  );
  assert.equal(vague.phraseLine, '公開 API 標題');
});

test('taskProgressFromTiger 以進行中與今日完成計算', () => {
  assert.equal(taskProgressFromTiger(null), null);
  assert.equal(taskProgressFromTiger({ active: [], completedToday: [] }), null);
  const p = taskProgressFromTiger({
    active: [{ issue: 1 }],
    completedToday: [{ issue: 2 }, { issue: 3 }]
  });
  assert.deepEqual(p, { completed: 2, total: 3, fraction: 2 / 3 });
});

test('tigerFromPublic 解析公開欄位', () => {
  const body = {
    success: true,
    characters: {
      tiger: {
        status: '工作中',
        active: [{ issue: 1, phrase: '整理 API', round: 2, minutes: 5, model: 'composer-2.5' }],
        completedToday: [{ at: '2026-11-22T06:30:00.000Z', phrase: '收工', issue: 40, pr: 41 }]
      }
    }
  };
  const t = tigerFromPublic(body);
  assert.equal(t.status, '工作中');
  assert.equal(t.active.length, 1);
  assert.equal(t.completedToday.length, 1);
  assert.equal(tigerFromPublic({ success: false }), null);
});

test('hasNewCompletedToday 僅在新鍵出現時', () => {
  assert.equal(hasNewCompletedToday(['issue:1'], [{ issue: 1 }]), false);
  assert.equal(hasNewCompletedToday(['issue:1'], [{ issue: 2 }]), true);
  assert.equal(completedTodayKeys([{ at: 't', issue: 5 }])[0], 'issue:5');
});

test('secretarySpriteUrlForStatus 對應三張立繪', () => {
  assert.equal(secretarySpriteUrlForStatus('工作中'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('工作中 ×3'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('待命中'), '/assets/private-team/secretary-idle-tea.webp');
  assert.equal(secretarySpriteUrlForStatus('待機'), '/assets/private-team/secretary-idle-tea.webp');
  assert.equal(secretarySpriteUrlForStatus('休息中'), '/assets/private-team/secretary-sleep.webp');
});

test('bubbleTextForStatus 僅三態與工作中倍數', () => {
  assert.equal(bubbleTextForStatus('工作中 ×2'), '工作中 ×2');
  assert.equal(bubbleTextForStatus('待命中'), '待命中');
  assert.equal(bubbleTextForStatus('休息中'), '休息中');
  assert.equal(bubbleTextForStatus(''), '待命中');
});

test('capsulesForActive 與禁止字樣', () => {
  assert.deepEqual(capsulesForActive({ round: 2, minutes: 12, model: 'grok-4.7-high' }), [
    '第 2 輪',
    '已跑 12 分',
    'grok-4.7-high'
  ]);
  assert.equal(isSafePublicText('ai/issue-386'), false);
  assert.equal(isSafePublicText('/Users/tina/x'), false);
  assert.equal(isSafePublicText('正常句子'), true);
});

test('formatOfficeClock 台北時鐘', () => {
  const ms = Date.parse('2026-11-22T06:30:00.000Z');
  const clock = formatOfficeClock(ms);
  assert.match(clock, /^\d{2}:\d{2}$/);
});

test('phraseForDoneDisplay 去掉正在與空泛句', () => {
  assert.equal(phraseForDoneDisplay({ phrase: '正在整理', title: '標題' }), '整理');
  assert.equal(phraseForDoneDisplay({ phrase: '完成一項工作', title: 'Issue 標題' }), 'Issue 標題');
  assert.equal(phraseForDoneDisplay({ phrase: '收工一句' }), '收工一句');
});

test('phraseForActiveDisplay 空泛句改標題', () => {
  assert.equal(
    phraseForActiveDisplay({ phrase: '正在處理一項工作', title: 'README 更新' }),
    'README 更新'
  );
  assert.equal(phraseForActiveDisplay({ phrase: '秘書口語', title: '標題' }), '秘書口語');
  assert.equal(phraseForActiveDisplay({ title: '只有標題' }), '只有標題');
});

test('renderActiveBlock 標題主文與次要單號', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '' };
      node.appendChild = (c) => { node.childNodes.push(c); return c; };
      node.setAttribute = () => {};
      return node;
    }
  };
  const block = renderActiveBlock(doc, {
    phrase: '正在處理一項工作',
    title: '首頁秘書狀態卡',
    issue: 507,
    issueUrl: 'https://github.com/p4774756/PunchClock/issues/507',
    minutes: 2
  });
  const flat = JSON.stringify(block);
  assert.ok(flat.includes('首頁秘書狀態卡'));
  assert.ok(flat.includes('pt-status-active-title'));
  assert.ok(flat.includes('pt-status-issue-sub'));
  assert.ok(flat.includes('#507'));
});

test('devQueueFromStatus 解析排隊', () => {
  const q = devQueueFromStatus({
    success: true,
    now: { queue: [{ title: '甲', pr: 1 }, { title: '乙', pr: 2 }] }
  });
  assert.equal(q.length, 2);
  assert.equal(q[0].title, '甲');
});

test('fillStatusCard 產出連結與膠囊', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '', attributes: {} };
      node.appendChild = (c) => node.childNodes.push(c);
      node.setAttribute = (k, v) => { node.attributes[k] = v; };
      node.replaceChildren = () => { node.childNodes = []; };
      return node;
    }
  };
  const body = doc.createElement('div');
  fillStatusCard(doc, body, {
    tiger: {
      status: '工作中',
      active: [{
        phrase: '做秘書小卡',
        issue: 386,
        issueUrl: 'https://github.com/p4774756/PunchClock/issues/386',
        pr: 400,
        prUrl: 'https://github.com/p4774756/PunchClock/pull/400',
        round: 1,
        minutes: 3,
        model: 'composer-2.5'
      }],
      completedToday: []
    },
    queue: [{ title: '排隊項目', pr: 401 }]
  });
  const flat = JSON.stringify(body);
  assert.ok(flat.includes('正在進行'));
  assert.ok(flat.includes('排隊'));
  assert.ok(flat.includes('今天完成'));
  assert.ok(flat.includes('composer-2.5'));
  assert.ok(flat.includes('排隊項目'));
});

function mockClassList() {
  const set = new Set();
  return {
    toggle(cls, on) {
      if (on) set.add(cls);
      else set.delete(cls);
    },
    has(cls) {
      return set.has(cls);
    }
  };
}

test('syncStatusBodyScroll 可捲時標示上下提示', () => {
  const scroll = { classList: mockClassList() };
  const body = { scrollHeight: 480, clientHeight: 200, scrollTop: 0 };
  syncStatusBodyScroll(scroll, body);
  assert.ok(scroll.classList.has('can-scroll-below'));
  assert.equal(scroll.classList.has('can-scroll-above'), false);
  body.scrollTop = 285;
  syncStatusBodyScroll(scroll, body);
  assert.equal(scroll.classList.has('can-scroll-below'), false);
  assert.ok(scroll.classList.has('can-scroll-above'));
  body.scrollTop = 40;
  syncStatusBodyScroll(scroll, body);
  assert.ok(scroll.classList.has('can-scroll-below'));
  assert.ok(scroll.classList.has('can-scroll-above'));
});

test('狀態卡 CSS 與頁面結構支援內文捲動提示', () => {
  const css = readFileSync(join(publicRoot, 'css/private-team.css'), 'utf8');
  const pageJs = readFileSync(join(publicRoot, 'js/private-team-page.js'), 'utf8');
  assert.match(css, /\.pt-status-scroll[\s\S]*can-scroll-below::after/);
  assert.match(css, /\.pt-status-body[\s\S]*overflow-y:\s*auto/);
  assert.match(css, /\.pt-status-body[\s\S]*padding-bottom:/);
  assert.match(pageJs, /data-pt-status-scroll/);
  assert.match(pageJs, /syncStatusBodyScroll/);
});

test('fillStatusCard 多筆進行中仍含今天完成與顯示全部', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '', attributes: {} };
      node.appendChild = (c) => node.childNodes.push(c);
      node.setAttribute = (k, v) => { node.attributes[k] = v; };
      node.replaceChildren = () => { node.childNodes = []; };
      return node;
    }
  };
  const body = doc.createElement('div');
  const actives = [501, 502, 503].map((issue) => ({
    phrase: '正在處理一項工作',
    title: '公開標題 ' + issue,
    issue,
    round: 1,
    minutes: 5,
    model: 'composer-2.5'
  }));
  const completed = [
    { phrase: '收工甲', issue: 401 },
    { phrase: '收工乙', issue: 402 },
    { phrase: '收工丙', issue: 403 },
    { phrase: '收工丁', issue: 404 }
  ];
  const queue = Array.from({ length: 6 }, (_, i) => ({ title: '排隊任務 ' + (i + 1), pr: 600 + i }));
  fillStatusCard(doc, body, {
    tiger: { status: '工作中 ×3', active: actives, completedToday: completed },
    queue
  });
  const flat = JSON.stringify(body);
  assert.ok(flat.includes('今天完成'));
  assert.ok(flat.includes('顯示全部'));
  assert.ok(flat.includes('公開標題 501'));
  assert.ok(flat.includes('顯示全部（6）'));
});

test('fillStatusCard 休息中台詞不含猛虎', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '' };
      node.appendChild = (c) => { node.textContent += (c.textContent || ''); node.childNodes.push(c); };
      node.setAttribute = () => {};
      node.replaceChildren = () => { node.childNodes = []; node.textContent = ''; };
      if (tag === 'p' || tag === 'h3') {
        const orig = node.appendChild.bind(node);
        node.appendChild = (c) => {
          if (c && c.textContent) node.textContent += c.textContent;
          return orig(c);
        };
      }
      return node;
    }
  };
  const body = doc.createElement('div');
  fillStatusCard(doc, body, {
    tiger: { status: '休息中', active: [], completedToday: [] },
    queue: []
  });
  const flat = JSON.stringify(body) + body.textContent;
  assert.ok(flat.includes('今天先休息，晚點再來看我。'));
  assert.doesNotMatch(flat, /猛虎/);
});