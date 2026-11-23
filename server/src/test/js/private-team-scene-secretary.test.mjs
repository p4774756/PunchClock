import assert from 'node:assert/strict';
import test from 'node:test';
import { readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadPublicModule } from './load-public-module.mjs';

const publicRoot = join(dirname(fileURLToPath(import.meta.url)), '../../main/resources/public');
const {
  SECRETARY_SPRITE_URL_TYPING,
  SECRETARY_SPRITE_URL_TYPING_B,
  SECRETARY_SPRITE_URL_WAVE,
  SECRETARY_SPRITE_URL_YEAH,
  SECRETARY_SPRITE_URL_IDLE,
  SECRETARY_SPRITE_URL_READ,
  SECRETARY_SPRITE_URL_PHONE,
  SECRETARY_CLAP_SPRITE_A,
  SECRETARY_CLAP_SPRITE_B,
  SECRETARY_CELEBRATE_STEPS,
  SECRETARY_DANCE_DURATION_MS,
  SECRETARY_STANDBY_IDLE_URLS,
  secretaryCelebrateFrameUrlAt,
  secretaryCelebrateJumpOffsetAt,
  secretaryTypingDisplayUrl,
  shouldTriggerIdleStandbyDance,
  pickNextStandbyIdleUrl,
  shouldIgnoreStandbyIdleSpriteUpdate,
  isSecretaryStandbyIdleUrl
} = await loadPublicModule('private-team-scene.js');

const {
  completedTodayKeys,
  hasNewCompletedToday
} = await loadPublicModule('private-team-office.js');

test('secretaryTypingDisplayUrl 工作中 B 幀與減少動態', () => {
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, true, false),
    SECRETARY_SPRITE_URL_TYPING_B
  );
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, true, true),
    SECRETARY_SPRITE_URL_TYPING
  );
  assert.equal(
    secretaryTypingDisplayUrl(SECRETARY_SPRITE_URL_TYPING, false, false),
    SECRETARY_SPRITE_URL_TYPING
  );
});

test('今日完成差量鍵', () => {
  const list = [{ issue: 2, at: 'z' }, { pr: 9 }];
  assert.deepEqual(completedTodayKeys(list), ['issue:2', 'pr:9']);
  assert.equal(hasNewCompletedToday(null, list), false);
  assert.equal(hasNewCompletedToday(['issue:2', 'pr:9'], list), false);
  assert.equal(hasNewCompletedToday(['issue:1'], list), true);
});

test('場景含揮手與比耶素材鍵', () => {
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.match(src, /SECRETARY_SPRITE_URL_WAVE/);
  assert.match(src, /playSecretaryWave/);
  assert.match(src, /playSecretaryYeah/);
  assert.match(src, new RegExp(SECRETARY_SPRITE_URL_WAVE.replace(/\//g, '\\/')));
  assert.match(src, new RegExp(SECRETARY_SPRITE_URL_YEAH.replace(/\//g, '\\/')));
});

test('慶祝拍手比讚小跳序列與時長', () => {
  assert.deepEqual(
    SECRETARY_CELEBRATE_STEPS.map((s) => s.kind),
    ['frame', 'frame', 'frame', 'frame', 'frame', 'jump']
  );
  assert.equal(SECRETARY_CLAP_SPRITE_A, '/assets/private-team/secretary-clap-a.webp');
  assert.equal(SECRETARY_CLAP_SPRITE_B, '/assets/private-team/secretary-clap-b.webp');
  assert.equal(SECRETARY_CELEBRATE_STEPS[4].url, SECRETARY_SPRITE_URL_YEAH);
  assert.equal(SECRETARY_DANCE_DURATION_MS, 250 * 4 + 700 + 450);
  assert.equal(secretaryCelebrateFrameUrlAt(0), SECRETARY_CLAP_SPRITE_A);
  assert.equal(secretaryCelebrateFrameUrlAt(400), SECRETARY_CLAP_SPRITE_B);
  assert.equal(secretaryCelebrateFrameUrlAt(1000), SECRETARY_SPRITE_URL_YEAH);
  assert.equal(secretaryCelebrateFrameUrlAt(SECRETARY_DANCE_DURATION_MS), '');
  assert.ok(secretaryCelebrateJumpOffsetAt(1925) > 0.05);
  assert.equal(secretaryCelebrateJumpOffsetAt(500), 0);
});

test('待命立繪輪替選擇', () => {
  assert.deepEqual(SECRETARY_STANDBY_IDLE_URLS, [
    SECRETARY_SPRITE_URL_IDLE,
    SECRETARY_SPRITE_URL_READ,
    SECRETARY_SPRITE_URL_PHONE
  ]);
  for (const cur of SECRETARY_STANDBY_IDLE_URLS) {
    for (let ri = 0; ri < 20; ri++) {
      const next = pickNextStandbyIdleUrl(cur, ri);
      assert.ok(SECRETARY_STANDBY_IDLE_URLS.includes(next));
      assert.notEqual(next, cur);
    }
  }
  assert.equal(pickNextStandbyIdleUrl(SECRETARY_SPRITE_URL_IDLE, 0), SECRETARY_SPRITE_URL_READ);
  assert.equal(pickNextStandbyIdleUrl(SECRETARY_SPRITE_URL_IDLE, 1), SECRETARY_SPRITE_URL_PHONE);
  assert.equal(pickNextStandbyIdleUrl(SECRETARY_SPRITE_URL_READ, 0), SECRETARY_SPRITE_URL_IDLE);
  assert.equal(pickNextStandbyIdleUrl(SECRETARY_SPRITE_URL_READ, 1), SECRETARY_SPRITE_URL_PHONE);
  assert.ok(isSecretaryStandbyIdleUrl(SECRETARY_SPRITE_URL_PHONE));
  assert.equal(isSecretaryStandbyIdleUrl(SECRETARY_SPRITE_URL_TYPING), false);
});

test('心跳待命預設喝茶不蓋輪替姿勢', () => {
  assert.equal(
    shouldIgnoreStandbyIdleSpriteUpdate(SECRETARY_SPRITE_URL_READ, SECRETARY_SPRITE_URL_IDLE),
    true
  );
  assert.equal(
    shouldIgnoreStandbyIdleSpriteUpdate(SECRETARY_SPRITE_URL_PHONE, SECRETARY_SPRITE_URL_IDLE),
    true
  );
  assert.equal(
    shouldIgnoreStandbyIdleSpriteUpdate(SECRETARY_SPRITE_URL_IDLE, SECRETARY_SPRITE_URL_IDLE),
    false
  );
  assert.equal(
    shouldIgnoreStandbyIdleSpriteUpdate(SECRETARY_SPRITE_URL_TYPING, SECRETARY_SPRITE_URL_IDLE),
    false
  );
});

test('待命閒置起舞條件', () => {
  const now = 1_000_000;
  const base = {
    status: '待命中',
    standbySinceMs: now - 4 * 60 * 1000,
    now,
    lastIdleDanceMs: null,
    dancing: false,
    reducedMotion: false
  };
  assert.equal(shouldTriggerIdleStandbyDance(base), true);
  assert.equal(shouldTriggerIdleStandbyDance({ ...base, status: '工作中' }), false);
  assert.equal(shouldTriggerIdleStandbyDance({ ...base, reducedMotion: true }), false);
  assert.equal(
    shouldTriggerIdleStandbyDance({ ...base, lastIdleDanceMs: now - 2 * 60 * 1000 }),
    false
  );
  const src = readFileSync(join(publicRoot, 'js/private-team-scene.js'), 'utf8');
  assert.match(src, /playSecretaryDance/);
  assert.match(src, /secretary-clap-/);
  assert.match(src, /preloadSecretaryCelebrateFrames/);
});