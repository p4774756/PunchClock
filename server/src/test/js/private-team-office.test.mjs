import assert from 'node:assert/strict';
import test from 'node:test';
import { loadPublicModule } from './load-public-module.mjs';

const {
  tigerFromPublic,
  bubbleTextForStatus,
  capsulesForActive,
  devQueueFromStatus,
  formatOfficeClock,
  fillStatusCard,
  isSafePublicText,
  phraseForDoneDisplay,
  secretarySpriteUrlForStatus,
  taskProgressFromTiger
} = await loadPublicModule('private-team-office.js');

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