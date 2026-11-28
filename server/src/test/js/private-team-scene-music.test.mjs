import test from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { loadPublicModule, readPrivateTeamSceneBundle } from './load-public-module.mjs';

const music = await loadPublicModule('private-team-scene-music.js');
const lounge = await loadPublicModule('private-team-scene-lounge.js');

function fakeWin() {
  const calls = [];
  class FakeAudio {
    constructor(url) {
      this.url = url;
      this.loop = false;
      this.volume = 1;
      calls.push(['new', url]);
    }
    play() {
      calls.push(['play']);
      return FakeAudio.rejectNext ? Promise.reject(new Error('blocked')) : Promise.resolve();
    }
    pause() {
      calls.push(['pause']);
    }
  }
  return { win: { Audio: FakeAudio }, calls, FakeAudio };
}

test('唱盤只在播放中才轉；減少動態不轉', () => {
  assert.equal(music.vinylPlaybackSpin(true, false), true);
  assert.equal(music.vinylPlaybackSpin(false, false), false);
  assert.equal(music.vinylPlaybackSpin(true, true), false);
});

test('唱針臂：播放時放上唱片，停止時回休息位', () => {
  assert.equal(music.vinylArmTarget(false), 0);
  assert.ok(music.vinylArmTarget(true) < -0.4);
});

test('createOfficeMusic：點一下播放（循環、小音量），再點一下停止', () => {
  const { win, calls } = fakeWin();
  const m = music.createOfficeMusic({ win });
  assert.equal(m.isPlaying(), false);
  assert.deepEqual(calls, [], '建立時不應載入或播放（自動播放會被擋）');
  assert.equal(m.toggle(), true);
  assert.equal(m.isPlaying(), true);
  assert.deepEqual(calls.map((c) => c[0]), ['new', 'play']);
  assert.equal(calls[0][1], '/audio/office-lofi.m4a');
  assert.equal(m.toggle(), false);
  assert.equal(calls[calls.length - 1][0], 'pause');
});

test('createOfficeMusic：播放被瀏覽器拒絕時回到停止，唱盤不空轉', async () => {
  const { win, FakeAudio } = fakeWin();
  FakeAudio.rejectNext = true;
  const m = music.createOfficeMusic({ win });
  const seen = [];
  m.onChange((p) => seen.push(p));
  m.toggle();
  await new Promise((r) => setTimeout(r, 0));
  assert.equal(m.isPlaying(), false);
  assert.deepEqual(seen, [true, false]);
});

test('音量偏小、音檔小於 1MB', () => {
  assert.ok(music.OFFICE_MUSIC_VOLUME <= 0.4);
  const size = readFileSync(new URL('../../main/resources/public/audio/office-lofi.m4a', import.meta.url)).length;
  assert.ok(size > 50_000 && size < 1_000_000, 'size ' + size);
});

test('tickVinylSpin：播放才轉唱盤並顯示音符，臂放上唱片', () => {
  const platter = { rotation: { y: 0 } };
  const arm = { rotation: { y: 0 } };
  const notes = [{ visible: false, position: { y: 0 }, material: { opacity: 1 } }];
  lounge.tickVinylSpin(platter, notes, false, false, 1, 0.1, arm);
  assert.equal(platter.rotation.y, 0);
  assert.equal(notes[0].visible, false);
  for (let i = 0; i < 60; i++) lounge.tickVinylSpin(platter, notes, true, false, 1 + i * 0.016, 0.016, arm);
  assert.ok(platter.rotation.y > 0.5);
  assert.equal(notes[0].visible, true);
  assert.ok(arm.rotation.y < -0.5);
  for (let i = 0; i < 90; i++) lounge.tickVinylSpin(platter, notes, false, false, 3 + i * 0.016, 0.016, arm);
  assert.ok(arm.rotation.y > -0.05, '停止後唱針臂回到休息位');
});

test('tickVinylSpin：閒置時點擊提示光圈顯示，播放時隱藏', () => {
  const hint = { visible: false, material: { opacity: 0 } };
  lounge.tickVinylSpin(null, null, false, false, 1, 0.016, null, hint);
  assert.equal(hint.visible, true);
  assert.ok(hint.material.opacity > 0.1);
  lounge.tickVinylSpin(null, null, true, false, 1, 0.016, null, hint);
  assert.equal(hint.visible, false);
});

test('減少動態：唱盤不轉、音符不飄，但唱針臂直接到位', () => {
  const platter = { rotation: { y: 0 } };
  const arm = { rotation: { y: 0 } };
  const notes = [{ visible: true, position: { y: 0 }, material: { opacity: 1 } }];
  lounge.tickVinylSpin(platter, notes, true, true, 1, 0.016, arm);
  assert.equal(platter.rotation.y, 0);
  assert.equal(notes[0].visible, false);
  assert.equal(arm.rotation.y, music.vinylArmTarget(true));
});

test('場景：點唱片機只切換音樂，不開狀態卡；頁面隱藏時停播', () => {
  const src = readPrivateTeamSceneBundle();
  assert.match(src, /userData\.pickRole = 'vinyl'/);
  assert.match(src, /if \(target === 'vinyl'\) \{[\s\S]{0,160}officeMusic\.toggle\(\);\s*return;/);
  assert.match(src, /if \(pageHidden\) officeMusic\.stop\(\)/);
  assert.match(src, /vinylTurntable\]/);
  assert.match(src, /toggleMusic\(\)/);
});

test('伺服器有 /audio 靜態路徑', () => {
  const java = readFileSync(
    new URL('../../main/java/com/example/server/ServerApp.java', import.meta.url),
    'utf8'
  );
  assert.match(java, /hostedPath = "\/audio"/);
});
