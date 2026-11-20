import test from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('island-lab-office-logic.js');

test('訪客不可開辦公室', () => {
  assert.equal(L.viewerCanOpenLabOffice(false), false);
  assert.equal(L.viewerCanOpenLabOffice(true), true);
});

test('3D 關鍵字辨識', () => {
  assert.equal(L.issueTitleLooksArtistic('銅人 GLB 模型'), true);
  assert.equal(L.issueTitleLooksArtistic('一般打卡 issue'), false);
});

test('審核中狀態', () => {
  const machines = {
    machines: [{ machine: 'home', online: true, role: 'review', model: 'claude', round: 1 }]
  };
  assert.equal(L.anyReviewActive(machines), true);
  const rev = L.buildCharacterState('reviewer', { machines, now: 1_000_000 });
  assert.equal(rev.mode, 'work');
  assert.ok(rev.bubble.includes('審核'));
});

test('工程師工作中氣泡', () => {
  const machines = {
    machines: [{
      machine: 'office',
      online: true,
      agentRunning: true,
      issue: 254,
      round: 1,
      model: 'gpt',
      runStartedAt: 1_000_000
    }]
  };
  const st = L.buildCharacterState('tiger', { machines, now: 1_010_000 });
  assert.equal(st.mode, 'work');
  assert.ok(st.bubble.includes('#254'));
});