import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');

const {
  tigerFromPublic,
  secretarySystemMetricsVisible,
  bubbleTextForStatus,
  capsulesForActive,
  devQueueFromStatus,
  formatOfficeClock,
  fillStatusCard,
  syncStatusBodyScroll,
  isSafePublicText,
  phraseForDoneDisplay,
  titleForDoneDisplay,
  formatAiWorkDuration,
  formatMergeSpanDuration,
  renderCompletedList,
  phraseForActiveDisplay,
  renderActiveBlock,
  progressSegmentsForActive,
  statusLineForActive,
  activeCiFailed,
  activeStageForItem,
  titleForActiveDisplay,
  completedTodayKeys,
  hasNewCompletedToday,
  secretarySpriteUrlForStatus,
  isWorkingStatus,
  progressBoardViewFromTiger,
  taskProgressFromTiger,
  mergeSecretaryQueues,
  renderQueueList
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
  const staged = progressBoardViewFromTiger(
    {
      active: [{ issue: 576, phrase: '牆上進度面板', stage: '開發中', round: 1 }],
      completedToday: []
    },
    0
  );
  assert.match(staged.metaLine, /開發中/);
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

test('tigerFromPublic 含 CPU RAM', () => {
  const t = tigerFromPublic({
    success: true,
    characters: {
      tiger: {
        status: '待命中',
        cpuPercent: 38,
        ramUsedGb: 11.4,
        ramTotalGb: 16,
        ramPercent: 71
      }
    }
  });
  assert.equal(t.cpuPercent, 38);
  assert.equal(t.ramUsedGb, 11.4);
  assert.equal(t.ramPercent, 71);
  assert.equal(secretarySystemMetricsVisible(t), true);
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

test('isWorkingStatus 舊文案與結界', () => {
  assert.equal(isWorkingStatus('工作中'), true);
  assert.equal(isWorkingStatus('工作中 ×3'), true);
  assert.equal(isWorkingStatus('三聖獸結界展開中'), true);
  assert.equal(isWorkingStatus('青龍結界展開中'), true);
  assert.equal(isWorkingStatus('待命中'), false);
  assert.equal(isWorkingStatus('休息中'), false);
});

test('secretarySpriteUrlForStatus 對應三張立繪', () => {
  assert.equal(secretarySpriteUrlForStatus('工作中'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('工作中 ×3'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('三聖獸結界展開中'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('青龍結界展開中'), '/assets/private-team/secretary-typing-a.webp');
  assert.equal(secretarySpriteUrlForStatus('待命中'), '/assets/private-team/secretary-sofa.webp');
  assert.equal(secretarySpriteUrlForStatus('待機'), '/assets/private-team/secretary-sofa.webp');
  assert.equal(secretarySpriteUrlForStatus('休息中'), '/assets/private-team/secretary-sofa.webp');
  assert.equal(
    secretarySpriteUrlForStatus('工作中', 'stage'),
    '/assets/private-team/secretary-stage-typing-a.webp'
  );
  assert.equal(
    secretarySpriteUrlForStatus('休息中', 'stage'),
    '/assets/private-team/secretary-stage-sofa.webp'
  );
});

test('bubbleTextForStatus 秘書口吻與待命／休息', () => {
  assert.equal(bubbleTextForStatus('三聖獸結界展開中'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('二聖獸結界展開中'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('四聖獸結界展開中'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('聖獸結界展開中'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('青龍結界展開中'), '青龍正在努力中喔');
  assert.equal(bubbleTextForStatus('白虎結界展開中'), '白虎正在努力中喔');
  assert.equal(bubbleTextForStatus('朱雀結界展開中'), '朱雀正在努力中喔');
  assert.equal(bubbleTextForStatus('玄武結界展開中'), '玄武正在努力中喔');
  assert.equal(bubbleTextForStatus('工作中'), '聖獸正在努力中喔');
  assert.equal(bubbleTextForStatus('工作中 ×3'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('工作中 ×2'), '聖獸們都在努力中喔');
  assert.equal(bubbleTextForStatus('待命中'), '待命中');
  assert.equal(bubbleTextForStatus('休息中'), '休息中');
  assert.equal(bubbleTextForStatus(''), '待命中');
});

test('capsulesForActive 與禁止字樣', () => {
  assert.deepEqual(capsulesForActive({ stage: '等 CI', round: 2, minutes: 12, model: 'grok-4.7-high' }), [
    '等 CI',
    '第 2 輪',
    '已跑 12 分',
    'grok-4.7-high'
  ]);
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

test('titleForDoneDisplay 只用公開標題', () => {
  assert.equal(titleForDoneDisplay({ phrase: '秘書說長句', title: '公開標題' }), '公開標題');
  assert.equal(titleForDoneDisplay({ phrase: '只有秘書說' }), '');
  assert.equal(phraseForDoneDisplay({ title: '相容別名' }), '相容別名');
});

test('formatAiWorkDuration 與開單到合併字樣', () => {
  assert.equal(formatAiWorkDuration(12), 'AI 工作 12 分');
  assert.equal(formatAiWorkDuration(0), '');
  assert.equal(formatAiWorkDuration(72), 'AI 工作 1 時 12 分');
  assert.equal(formatMergeSpanDuration(72), '開單到合併 1 時 12 分');
  assert.equal(formatMergeSpanDuration(60), '開單到合併 1 時');
  assert.equal(formatMergeSpanDuration(16_666_620), '');
});

test('renderCompletedList 缺 AI 工時只顯示開單到合併', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '' };
      node.appendChild = (c) => { node.childNodes.push(c); return c; };
      node.setAttribute = () => {};
      return node;
    }
  };
  const oldOnly = renderCompletedList(doc, [{ issue: 1, durationMinutes: 30 }], {});
  const oldFlat = JSON.stringify(oldOnly);
  assert.ok(oldFlat.includes('開單到合併 30 分'));
  assert.ok(!oldFlat.includes('AI 工作'));
  const both = renderCompletedList(doc, [{ issue: 2, aiWorkMinutes: 8, durationMinutes: 90 }], {});
  const bothFlat = JSON.stringify(both);
  assert.ok(bothFlat.includes('AI 工作 8 分'));
  assert.ok(bothFlat.includes('pt-status-done-span'));
  assert.ok(bothFlat.includes('開單到合併 1 時 30 分'));
});

test('phraseForActiveDisplay 空泛句改標題', () => {
  assert.equal(
    phraseForActiveDisplay({ phrase: '正在處理一項工作', title: 'README 更新' }),
    'README 更新'
  );
  assert.equal(phraseForActiveDisplay({ phrase: '秘書口語', title: '標題' }), '秘書口語');
  assert.equal(phraseForActiveDisplay({ title: '只有標題' }), '只有標題');
  assert.equal(
    phraseForActiveDisplay({ phrase: '正在處理一項工作', issue: 571 }),
    ''
  );
});

function miniDoc() {
  return {
    createElement(tag) {
      const node = {
        tagName: tag.toUpperCase(),
        className: '',
        classList: {
          _c: new Set(),
          add(c) { this._c.add(c); },
          remove(c) { this._c.delete(c); }
        },
        childNodes: [],
        textContent: '',
        style: {}
      };
      node.appendChild = (c) => { node.childNodes.push(c); return c; };
      node.setAttribute = (k, v) => { node[k] = v; };
      node.addEventListener = (ev, fn) => {
        if (ev === 'error') node._onError = fn;
      };
      return node;
    }
  };
}

test('renderActiveBlock 階段卡片 DOM', () => {
  const doc = miniDoc();
  const block = renderActiveBlock(doc, {
    phrase: '正在處理一項工作',
    title: '首頁秘書狀態卡',
    issue: 507,
    issueUrl: 'https://github.com/p4774756/PunchClock/issues/507',
    stage: '開發中',
    chain: '青龍',
    minutes: 12
  });
  const flat = JSON.stringify(block);
  assert.ok(flat.includes('pt-status-stage-card'));
  assert.ok(flat.includes('is-azure'));
  assert.ok(flat.includes('青龍'));
  assert.ok(flat.includes('pt-status-chain-name'));
  assert.ok(flat.includes('pt-status-chain-avatar'));
  assert.ok(flat.includes('/assets/private-team/chain-qinglong-96.webp'));
  assert.ok(flat.includes('chain-qinglong-192.webp 2x'));
  assert.ok(flat.includes('"loading":"lazy"'));
  assert.ok(flat.includes('"alt":"青龍"'));
  assert.ok(flat.includes('pt-status-progress'));
  assert.ok(flat.includes('開發中・12 分'));
  assert.ok(flat.includes('#507'));
});

test('renderActiveBlock 四象頭像與未知鏈名', () => {
  const doc = miniDoc();
  for (const { chain, slug } of [
    { chain: '白虎', slug: 'baihu' },
    { chain: '朱雀', slug: 'zhuque' },
    { chain: '玄武', slug: 'xuanwu' }
  ]) {
    const block = renderActiveBlock(doc, { chain, stage: '開發中', issue: 1 });
    const flat = JSON.stringify(block);
    assert.ok(flat.includes('chain-' + slug + '-96.webp'), chain);
    assert.ok(flat.includes('pt-status-chain-avatar'), chain);
  }
  const unknown = renderActiveBlock(miniDoc(), { chain: '未知鏈', stage: '開發中', issue: 2 });
  const unkFlat = JSON.stringify(unknown);
  assert.ok(!unkFlat.includes('pt-status-chain-avatar'));
  assert.ok(unkFlat.includes('pt-status-chain-dot'));
});

test('renderActiveBlock 頭像載入失敗退回色點', () => {
  const doc = miniDoc();
  const block = renderActiveBlock(doc, { chain: '青龍', stage: '開發中', issue: 3 });
  const chainLine = block.childNodes[0].childNodes[0];
  const img = chainLine.childNodes[0];
  assert.equal(typeof img._onError, 'function');
  img._onError();
  assert.ok(chainLine.classList._c.has('is-avatar-error'));
});

test('progressSegmentsForActive 各階段與 CI 失敗', () => {
  const dev = progressSegmentsForActive({ stage: '開發中', round: 1 });
  assert.deepEqual(dev.map((s) => s.state), ['current', 'pending', 'pending', 'pending']);
  const review = progressSegmentsForActive({ stage: '審查中', round: 2 });
  assert.deepEqual(review.map((s) => s.state), ['done', 'current', 'pending', 'pending']);
  const ciSkip = progressSegmentsForActive({ stage: '等 CI', round: 0 });
  assert.equal(ciSkip[0].state, 'done');
  assert.equal(ciSkip[1].state, 'skipped');
  assert.equal(ciSkip[2].state, 'current');
  const ciReviewed = progressSegmentsForActive({ stage: '等 CI', round: 1 });
  assert.deepEqual(ciReviewed.map((s) => s.state), ['done', 'done', 'current', 'pending']);
  const skipRendered = renderActiveBlock({
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '' };
      node.appendChild = (c) => { node.childNodes.push(c); return c; };
      node.setAttribute = () => {};
      return node;
    }
  }, { stage: '等 CI', round: 0, issue: 646 });
  assert.ok(JSON.stringify(skipRendered).includes('略過審核'));
  assert.ok(JSON.stringify(skipRendered).includes('\u2713 開發'));
  const ciItem = { stage: '等 CI', round: 2, verdict: 'FAIL' };
  const ciFail = progressSegmentsForActive(ciItem);
  assert.equal(ciFail[2].state, 'fail');
  assert.equal(statusLineForActive(ciItem), 'CI 失敗，等待處理');
  assert.equal(statusLineForActive({ stage: '審查中', minutes: 8 }), '審核中・8 分');
  assert.equal(
    statusLineForActive({ stage: '審查中', round: 2, minutes: 12 }),
    '審核中・第 2 輪・12 分'
  );
  const merge = progressSegmentsForActive({ stage: '合併中', round: 2 });
  assert.deepEqual(merge.map((s) => s.state), ['done', 'done', 'done', 'current']);
});

test('activeCiFailed 與狀態卡標題完整', () => {
  assert.equal(activeCiFailed({ stage: '修正中', failed: true }), true);
  assert.equal(activeCiFailed({ stage: '開發中', verdict: 'FAIL' }), false);
  const long = '完成卡片顯示標題與耗時的長白話標題不應再被截成半句';
  assert.equal(titleForDoneDisplay({ title: long }), long);
  assert.equal(
    titleForActiveDisplay({ phrase: '秘書口語', title: '夜間辦公室光影提亮' }),
    '夜間辦公室光影提亮'
  );
});

test('mergeSecretaryQueues 合併心跳與 GitHub 排隊', () => {
  const merged = mergeSecretaryQueues([{ title: 'GitHub 排隊', issue: 320 }], [
    { title: 'waiter 566', issue: 566 },
    { title: 'GitHub 重複', issue: 320 }
  ]);
  assert.equal(merged.length, 2);
  assert.equal(merged[0].issue, 566);
  assert.equal(merged[1].issue, 320);
});

test('tigerFromPublic 含 queue', () => {
  const t = tigerFromPublic({
    success: true,
    characters: { tiger: { status: '待命中', active: [], completedToday: [], queue: [{ issue: 1, title: '甲' }] } }
  });
  assert.equal(t.queue.length, 1);
});

test('devQueueFromStatus 解析排隊', () => {
  const q = devQueueFromStatus({
    success: true,
    now: { queue: [{ title: '甲', pr: 1 }, { title: '乙', pr: 2 }] }
  });
  assert.equal(q.length, 2);
  assert.equal(q[0].title, '甲');
});

test('fillStatusCard CPU RAM 進度條與警示', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '', style: {}, attributes: {} };
      node.appendChild = (c) => node.childNodes.push(c);
      node.setAttribute = (k, v) => { node.attributes[k] = v; };
      node.replaceChildren = () => { node.childNodes = []; };
      return node;
    }
  };
  const body = doc.createElement('div');
  fillStatusCard(doc, body, {
    tiger: {
      status: '待命中',
      cpuPercent: 90,
      ramUsedGb: 14,
      ramTotalGb: 16,
      ramPercent: 88,
      active: [],
      completedToday: []
    },
    queue: []
  });
  const flat = JSON.stringify(body);
  assert.ok(flat.includes('pt-status-sys'));
  assert.ok(flat.includes('pt-status-sys-warn'));
  assert.ok(flat.includes('14 / 16'));
});

test('fillStatusCard 缺系統欄位不誤顯 0%', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '', style: {}, attributes: {} };
      node.appendChild = (c) => node.childNodes.push(c);
      node.setAttribute = (k, v) => { node.attributes[k] = v; };
      node.replaceChildren = () => { node.childNodes = []; };
      return node;
    }
  };
  const body = doc.createElement('div');
  fillStatusCard(doc, body, {
    tiger: { status: '休息中', active: [], completedToday: [] },
    queue: []
  });
  const flat = JSON.stringify(body);
  assert.ok(!flat.includes('pt-status-sys'));
  assert.ok(!flat.includes('0%'));
});

test('renderQueueList 顯示 issue 連結與接在後提示', () => {
  const doc = {
    createElement(tag) {
      const node = { tagName: tag.toUpperCase(), className: '', childNodes: [], textContent: '', style: {}, attributes: {} };
      node.appendChild = (c) => node.childNodes.push(c);
      node.setAttribute = (k, v) => { node.attributes[k] = v; };
      return node;
    }
  };
  const root = renderQueueList(doc, [
    {
      issue: 820,
      title: '排隊修面板',
      issueUrl: 'https://github.com/p4774756/PunchClock/issues/820',
      waitingFor: 816
    }
  ], { canLinkGithub: true });
  const flat = JSON.stringify(root);
  assert.ok(flat.includes('#820'));
  assert.ok(flat.includes('pt-status-stage-issue'));
  assert.ok(flat.includes('排隊修面板'));
  assert.ok(flat.includes('接在 #816 後'));
  assert.ok(flat.includes('https://github.com/p4774756/PunchClock/issues/820'));
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
  assert.ok(flat.includes('pt-status-stage-card'));
  assert.ok(flat.includes('排隊項目'));
});

test('fillStatusCard 新完成短閃 class', () => {
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
    tiger: { status: '工作中', active: [], completedToday: [{ issue: 1 }] },
    queue: []
  }, { flashCompleted: true });
  const flat = JSON.stringify(body);
  assert.ok(flat.includes('pt-status-done-flash'));
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
    { title: '收工甲', issue: 401 },
    { title: '收工乙', issue: 402 },
    { title: '收工丙', issue: 403 },
    { title: '收工丁', issue: 404 }
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
test('有衝突階段：CI 格標「有衝突」、不顯示等 CI 分鐘', () => {
  const item = { stage: '有衝突', round: 0, minutes: 253 };
  const segs = progressSegmentsForActive(item);
  assert.deepEqual(segs.map((s) => s.label), ['開發', '審核', '有衝突', '合併']);
  assert.equal(segs[2].state, 'current');
  assert.equal(activeStageForItem(item), '有衝突');
  assert.equal(activeCiFailed({ stage: '有衝突', verdict: 'FAIL' }), false);
  assert.equal(statusLineForActive(item), '有衝突，待解決');
  assert.ok(!statusLineForActive(item).includes('分'));
  const normal = progressSegmentsForActive({ stage: '等 CI', round: 1 });
  assert.equal(normal[2].label, 'CI');
});
