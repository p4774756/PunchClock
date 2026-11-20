import test from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const L = await loadPublicModule('lab-office-logic.js');

test('訪客不可開辦公室', () => {
  assert.equal(L.viewerCanOpenLabOffice(false), false);
  assert.equal(L.viewerCanOpenLabOffice(true), true);
});

test('辦公室只有三人', () => {
  assert.deepEqual([...L.CHARACTER_IDS], ['oki', 'tiger', 'reviewer']);
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

test('三人站位不重疊', () => {
  const states = L.buildAllCharacterStates({
    machines: {
      machines: [
        {
          machine: 'office',
          online: true,
          agentRunning: true,
          issue: 254,
          round: 4,
          model: 'grok'
        },
        { machine: 'home', online: true, role: 'review', model: 'claude', round: 4 }
      ]
    },
    now: 1_000_000
  });
  assert.deepEqual(states.map((s) => s.id), ['oki', 'tiger', 'reviewer']);
  const spots = states.map((s) => s.spot);
  assert.equal(new Set(spots).size, spots.length);
  assert.equal(states.find((s) => s.id === 'oki').spot, 'coffee');
  assert.equal(states.find((s) => s.id === 'tiger').spot, 'desk-tiger');
  assert.equal(states.find((s) => s.id === 'reviewer').spot, 'desk-review');
});