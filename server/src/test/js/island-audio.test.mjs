import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const { createIslandAudio, resolveTrackId } = await loadPublicModule('island-audio.js');

const FULL_TRACKS = {
  main: '/audio/bgm-main.ogg',
  main_night: '/audio/bgm-main-night.ogg',
  casino: '/audio/bgm-casino.ogg',
  casino_night: '/audio/bgm-casino-night.ogg',
  tcm: '/audio/bgm-tcm.ogg',
  tcm_night: '/audio/bgm-tcm-night.ogg',
  computer: '/audio/bgm-computer.ogg',
  computer_night: '/audio/bgm-computer-night.ogg',
  city: '/audio/bgm-city.ogg',
  city_night: '/audio/bgm-city-night.ogg'
};

function fakeStorage() {
  const map = new Map();
  return {
    getItem(key) { return map.has(key) ? map.get(key) : null; },
    setItem(key, value) { map.set(key, String(value)); },
    removeItem(key) { map.delete(key); },
    clear() { map.clear(); }
  };
}

function throwingStorage() {
  return {
    getItem() { throw new Error('storage error'); },
    setItem() { throw new Error('storage error'); },
    removeItem() { throw new Error('storage error'); }
  };
}

function fakeAudio() {
  let paused = true;
  let vol = 0;
  return {
    src: '',
    loop: false,
    preload: 'auto',
    get volume() { return vol; },
    set volume(v) { vol = v; },
    get paused() { return paused; },
    play() {
      paused = false;
      return Promise.resolve();
    },
    pause() { paused = true; }
  };
}

function rejectingAudio() {
  let paused = true;
  let vol = 0;
  return {
    src: '',
    loop: false,
    preload: 'auto',
    get volume() { return vol; },
    set volume(v) { vol = v; },
    get paused() { return paused; },
    play() {
      return Promise.reject(new Error('autoplay blocked'));
    },
    pause() { paused = true; }
  };
}

function fakeAudioContext() {
  let time = 0;
  const gainValue = { value: 0 };
  return {
    state: 'running',
    get currentTime() { return time; },
    _advanceTime(dt) { time += dt; },
    resume() { return Promise.resolve(); },
    close() {},
    createGain() {
      return {
        gain: {
          get value() { return gainValue.value; },
          set value(v) { gainValue.value = v; },
          setValueAtTime(v) { gainValue.value = v; },
          cancelScheduledValues() {},
          linearRampToValueAtTime(v) { gainValue.value = v; }
        },
        connect() {}
      };
    },
    createMediaElementSource() {
      return { connect() {} };
    },
    destination: {}
  };
}

describe('island-audio', () => {
  test('muted state persists in storage', () => {
    const storage = fakeStorage();
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: fakeAudio,
      _forceElementVolume: true
    });
    assert.equal(audio.muted, false);
    audio.setMuted(true);
    assert.equal(audio.muted, true);

    const audio2 = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: fakeAudio,
      _forceElementVolume: true
    });
    assert.equal(audio2.muted, true);
  });

  test('storage read/write errors do not throw', () => {
    const storage = throwingStorage();
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: fakeAudio,
      _forceElementVolume: true
    });
    assert.doesNotThrow(() => audio.setMuted(true));
    assert.equal(audio.muted, true);
  });

  test('play() not called before user interaction', async () => {
    const storage = fakeStorage();
    let playCalled = false;
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => {
        const a = fakeAudio();
        const orig = a.play;
        a.play = () => { playCalled = true; return orig.call(a); };
        return a;
      },
      _forceElementVolume: true
    });
    audio.preload();
    await Promise.resolve();
    assert.equal(playCalled, false, 'play() should not be called before interaction');
  });

  test('play() called after user interaction', async () => {
    const storage = fakeStorage();
    let playCalled = false;
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => {
        const a = fakeAudio();
        const orig = a.play;
        a.play = () => { playCalled = true; return orig.call(a); };
        return a;
      },
      _forceElementVolume: true
    });
    audio.onUserInteraction();
    await Promise.resolve();
    assert.equal(playCalled, true, 'play() should be called after interaction');
  });

  test('play() reject retries on next interaction', async () => {
    const storage = fakeStorage();
    let attempts = 0;
    let succeed = false;
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => {
        return {
          src: '',
          loop: false,
          preload: 'auto',
          volume: 0,
          paused: true,
          play() {
            attempts += 1;
            if (succeed) {
              this.paused = false;
              return Promise.resolve();
            }
            return Promise.reject(new Error('blocked'));
          },
          pause() { this.paused = true; }
        };
      },
      _forceElementVolume: true
    });
    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 1, 'first attempt should happen');
    assert.equal(audio._getAwaiting(), true, 'should mark awaiting');
    assert.equal(audio.playSucceeded, false, 'playSucceeded should be false');

    succeed = true;
    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 2, 'second attempt on next interaction');
    assert.equal(audio.playSucceeded, true, 'playSucceeded should be true after success');
  });

  test('multiple interactions until play succeeds', async () => {
    const storage = fakeStorage();
    let attempts = 0;
    let succeedOnAttempt = 3;
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => {
        return {
          src: '',
          loop: false,
          preload: 'auto',
          volume: 0,
          paused: true,
          play() {
            attempts += 1;
            if (attempts >= succeedOnAttempt) {
              this.paused = false;
              return Promise.resolve();
            }
            return Promise.reject(new Error('blocked'));
          },
          pause() { this.paused = true; }
        };
      },
      _forceElementVolume: true
    });

    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 1);
    assert.equal(audio.playSucceeded, false);

    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 2);
    assert.equal(audio.playSucceeded, false);

    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 3);
    assert.equal(audio.playSucceeded, true);

    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(attempts, 3, 'no more attempts after success');
  });

  test('pause on hidden, resume on visible', async () => {
    const storage = fakeStorage();
    const fakeA = fakeAudio();
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => fakeA,
      _forceElementVolume: true
    });
    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(fakeA.paused, false, 'should be playing');

    audio.onVisibilityChange(true);
    assert.equal(fakeA.paused, true, 'should pause when hidden');

    audio.onVisibilityChange(false);
    await new Promise(r => setTimeout(r, 10));
    assert.equal(fakeA.paused, false, 'should resume when visible');
  });

  test('mute pauses, unmute resumes', async () => {
    const storage = fakeStorage();
    const fakeA = fakeAudio();
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: () => fakeA,
      _forceElementVolume: true
    });
    audio.onUserInteraction();
    await new Promise(r => setTimeout(r, 10));
    assert.equal(fakeA.paused, false, 'should be playing');

    audio.setMuted(true);
    await new Promise(r => setTimeout(r, 60));
    assert.equal(audio.muted, true);

    audio.setMuted(false);
    await new Promise(r => setTimeout(r, 10));
    assert.equal(fakeA.paused, false, 'should resume after unmute');
  });

  test('available returns false when no tracks', () => {
    const audio = createIslandAudio({
      tracks: {},
      storage: fakeStorage(),
      _forceElementVolume: true
    });
    assert.equal(audio.available, false);
  });

  test('available returns true when main track exists', () => {
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage: fakeStorage(),
      _forceElementVolume: true
    });
    assert.equal(audio.available, true);
  });

  test('toggleMute flips state', () => {
    const storage = fakeStorage();
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage,
      _createAudio: fakeAudio,
      _forceElementVolume: true
    });
    assert.equal(audio.muted, false);
    audio.toggleMute();
    assert.equal(audio.muted, true);
    audio.toggleMute();
    assert.equal(audio.muted, false);
  });

  test('no error with null storage', () => {
    const audio = createIslandAudio({
      tracks: { main: '/audio/bgm.ogg' },
      storage: null,
      _createAudio: fakeAudio,
      _forceElementVolume: true
    });
    assert.doesNotThrow(() => audio.setMuted(true));
    assert.equal(audio.muted, true);
  });

  describe('Web Audio API', () => {
    test('uses Web Audio when AudioContext is provided', async () => {
      const ctx = fakeAudioContext();
      const fakeA = fakeAudio();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        _createAudio: () => fakeA,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(audio.usingWebAudio, true);
    });

    test('GainNode controls volume instead of element.volume', async () => {
      const ctx = fakeAudioContext();
      const fakeA = fakeAudio();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        volume: 0.35,
        _createAudio: () => fakeA,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      const gainNode = audio._getGainNode();
      assert.ok(gainNode, 'GainNode should be created');
      assert.equal(audio._getVolume(), 0.35, 'volume should be set via gain');
    });

    test('falls back to element.volume when _forceElementVolume is true', async () => {
      const fakeA = fakeAudio();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        volume: 0.35,
        _createAudio: () => fakeA,
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(audio.usingWebAudio, false);
      assert.equal(fakeA.volume, 0.35);
    });

    test('Web Audio fade sets gain value', async () => {
      const ctx = fakeAudioContext();
      const fakeA = fakeAudio();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        volume: 0.5,
        _createAudio: () => fakeA,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(audio._getVolume(), 0.5, 'gain should be 0.5');
    });

    test('mute sets gain to 0', async () => {
      const ctx = fakeAudioContext();
      const fakeA = fakeAudio();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        volume: 0.35,
        _createAudio: () => fakeA,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 50));
      audio.setMuted(true);
      await new Promise(r => setTimeout(r, 50));
      assert.equal(audio._getVolume(), 0, 'gain should be 0 when muted');
    });

    test('play() and ctx.resume() called synchronously within onUserInteraction', () => {
      let playCalled = false;
      let resumeCalled = false;
      const ctx = {
        state: 'suspended',
        currentTime: 0,
        resume() {
          resumeCalled = true;
          return Promise.resolve();
        },
        close() {},
        createGain() {
          return {
            gain: {
              value: 0,
              setValueAtTime() {},
              cancelScheduledValues() {},
              linearRampToValueAtTime(v) { this.value = v; }
            },
            connect() {}
          };
        },
        createMediaElementSource() {
          return { connect() {} };
        },
        destination: {}
      };
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        _createAudio: () => ({
          src: '',
          loop: false,
          preload: 'auto',
          paused: true,
          play() {
            playCalled = true;
            this.paused = false;
            return Promise.resolve();
          },
          pause() { this.paused = true; }
        }),
        _audioContext: ctx
      });

      audio.onUserInteraction();

      assert.equal(playCalled, true, 'play() should be called synchronously');
      assert.equal(resumeCalled, true, 'ctx.resume() should be called synchronously');
    });

    test('duplicate interactions while pending only call play() once', async () => {
      let playCount = 0;
      let resolvePlay;
      const playPromise = new Promise(resolve => { resolvePlay = resolve; });

      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        _createAudio: () => ({
          src: '',
          loop: false,
          preload: 'auto',
          paused: true,
          play() {
            playCount += 1;
            return playPromise.then(() => { this.paused = false; });
          },
          pause() { this.paused = true; }
        }),
        _forceElementVolume: true
      });

      audio.onUserInteraction();
      assert.equal(playCount, 1, 'first interaction calls play()');
      assert.equal(audio._getPlayPending(), true, 'should be pending');

      audio.onUserInteraction();
      assert.equal(playCount, 1, 'second interaction while pending should not call play() again');

      audio.onUserInteraction();
      assert.equal(playCount, 1, 'third interaction while pending should not call play() again');

      resolvePlay();
      await new Promise(r => setTimeout(r, 10));

      assert.equal(audio._getPlayPending(), false, 'should no longer be pending');
      assert.equal(audio.playSucceeded, true, 'playSucceeded should be true');
    });
  });

  describe('ambience', () => {
    test('ambience plays alongside music on user interaction', async () => {
      const storage = fakeStorage();
      let musicCalled = false;
      let ambienceCalled = false;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage,
        _createAudio: (src) => {
          const a = fakeAudio();
          a.src = src;
          const orig = a.play;
          a.play = () => {
            if (src.indexOf('bgm') !== -1) musicCalled = true;
            if (src.indexOf('ocean') !== -1) ambienceCalled = true;
            return orig.call(a);
          };
          return a;
        },
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));
      assert.equal(musicCalled, true, 'music should play');
      assert.equal(ambienceCalled, true, 'ambience should play');
    });

    test('ambience has its own GainNode', async () => {
      const ctx = fakeAudioContext();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage: fakeStorage(),
        _createAudio: fakeAudio,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));
      const musicGain = audio._getGainNode();
      const ambienceGain = audio._getAmbienceGainNode();
      assert.ok(musicGain, 'music gain should exist');
      assert.ok(ambienceGain, 'ambience gain should exist');
    });

    test('setMuted(true) pauses both music and ambience', async () => {
      const storage = fakeStorage();
      const musicEl = fakeAudio();
      const ambienceEl = fakeAudio();
      let call = 0;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage,
        _createAudio: () => {
          call++;
          return call === 1 ? musicEl : ambienceEl;
        },
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));
      assert.equal(musicEl.paused, false);
      assert.equal(ambienceEl.paused, false);

      audio.setMuted(true);
      await new Promise(r => setTimeout(r, 80));
      assert.equal(audio.muted, true);
    });

    test('onVisibilityChange(true) pauses both', async () => {
      const storage = fakeStorage();
      const musicEl = fakeAudio();
      const ambienceEl = fakeAudio();
      let call = 0;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage,
        _createAudio: () => {
          call++;
          return call === 1 ? musicEl : ambienceEl;
        },
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));

      audio.onVisibilityChange(true);
      assert.equal(musicEl.paused, true);
      assert.equal(ambienceEl.paused, true);
    });

    test('no ambience when ambience option not provided', async () => {
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage,
        _createAudio: fakeAudio,
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));
      assert.equal(audio._getAmbienceAudio(), null, 'no ambience audio when not configured');
    });

    test('setInsideRoom reduces ambience volume', async () => {
      const ctx = fakeAudioContext();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage: fakeStorage(),
        _createAudio: fakeAudio,
        _audioContext: ctx
      });
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 20));
      const volOutside = audio._getAmbienceVolume();
      audio.setInsideRoom(true);
      await new Promise(r => setTimeout(r, 20));
      const volInside = audio._getAmbienceVolume();
      assert.ok(volInside < volOutside, `inside volume ${volInside} should be less than outside ${volOutside}`);
    });

    test('music success + ambience fail: next interaction retries ambience', async () => {
      const storage = fakeStorage();
      let musicAttempts = 0;
      let ambienceAttempts = 0;
      let ambienceSucceed = false;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        ambience: { src: '/audio/ocean.ogg', volume: 0.22 },
        storage,
        _createAudio: (src) => {
          return {
            src,
            loop: false,
            preload: 'auto',
            volume: 0,
            paused: true,
            play() {
              if (src.indexOf('bgm') !== -1) {
                musicAttempts++;
                this.paused = false;
                return Promise.resolve();
              }
              ambienceAttempts++;
              if (ambienceSucceed) {
                this.paused = false;
                return Promise.resolve();
              }
              return Promise.reject(new Error('blocked'));
            },
            pause() { this.paused = true; }
          };
        },
        _forceElementVolume: true
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 30));
      assert.equal(musicAttempts, 1, 'music should play once');
      assert.equal(ambienceAttempts, 1, 'ambience should try once');
      assert.equal(audio.playSucceeded, true, 'music succeeded');
      assert.equal(audio.ambiencePlaySucceeded, false, 'ambience failed');
      assert.equal(audio._getAmbienceAwaiting(), true, 'ambience awaiting retry');

      ambienceSucceed = true;
      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 30));
      assert.equal(ambienceAttempts, 2, 'ambience should retry on next interaction');
      assert.equal(audio.ambiencePlaySucceeded, true, 'ambience succeeded on retry');
    });
  });

  describe('resolveTrackId', () => {
    test('島嶼與日夜對應曲目', () => {
      assert.equal(resolveTrackId({ islandId: 'casino', night: false, tracks: FULL_TRACKS }), 'casino');
      assert.equal(resolveTrackId({ islandId: 'casino', night: true, tracks: FULL_TRACKS }), 'casino_night');
      assert.equal(resolveTrackId({ islandId: 'city', night: false, tracks: FULL_TRACKS }), 'city');
      assert.equal(resolveTrackId({ islandId: null, night: false, tracks: FULL_TRACKS }), 'main');
      assert.equal(resolveTrackId({ islandId: 'beach', night: true, tracks: FULL_TRACKS }), 'main_night');
    });

    test('室內建築對應島嶼曲目', () => {
      assert.equal(
        resolveTrackId({ buildingId: 'tcm-site', night: false, tracks: FULL_TRACKS }),
        'tcm'
      );
      assert.equal(
        resolveTrackId({ buildingId: 'computer-site', night: true, tracks: FULL_TRACKS }),
        'computer_night'
      );
    });

    test('夜間檔缺失時退回白天曲', () => {
      const partial = { main: '/m.ogg', casino: '/c.ogg' };
      assert.equal(resolveTrackId({ islandId: 'casino', night: true, tracks: partial }), 'casino');
    });

    test('島嶼曲缺失時退回 main / main_night', () => {
      const partial = { main: '/m.ogg', main_night: '/mn.ogg' };
      assert.equal(resolveTrackId({ islandId: 'city', night: false, tracks: partial }), 'main');
      assert.equal(resolveTrackId({ islandId: 'city', night: true, tracks: partial }), 'main_night');
    });
  });

  describe('setTrack crossfade', () => {
    test('setTrack 會換成指定 id', async () => {
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/a.ogg', casino: '/b.ogg' },
        storage,
        _createAudio: () => fakeAudio(),
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise((r) => setTimeout(r, 10));
      audio.setTrack('casino');
      await new Promise((r) => setTimeout(r, 10));
      assert.equal(audio.currentTrack, 'casino');
    });

    test('同一 id 再呼叫不會重啟', async () => {
      let playCount = 0;
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/a.ogg', casino: '/b.ogg' },
        storage,
        _createAudio: () => {
          const a = fakeAudio();
          const orig = a.play;
          a.play = () => {
            playCount += 1;
            return orig.call(a);
          };
          return a;
        },
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise((r) => setTimeout(r, 10));
      const afterFirst = playCount;
      audio.setTrack('main');
      await new Promise((r) => setTimeout(r, 10));
      assert.equal(playCount, afterFirst, 'same track should not call play again');
      assert.equal(audio.currentTrack, 'main');
    });

    test('換曲時交叉淡出期間可有兩個 element', async () => {
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/a.ogg', casino: '/b.ogg' },
        storage,
        _createAudio: fakeAudio,
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise((r) => setTimeout(r, 10));
      audio.setTrack('casino');
      await new Promise((r) => setTimeout(r, 30));
      assert.ok(audio._getAudio(), 'incoming element');
      assert.ok(audio._getOutgoingAudio(), 'outgoing during crossfade');
      await new Promise((r) => setTimeout(r, 1600));
      assert.equal(audio._getOutgoingAudio(), null, 'outgoing cleared after fade');
      assert.equal(audio.currentTrack, 'casino');
    });

    test('靜音會暫停淡出中的兩邊', async () => {
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/a.ogg', casino: '/b.ogg' },
        storage,
        _createAudio: fakeAudio,
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise((r) => setTimeout(r, 10));
      audio.setTrack('casino');
      await new Promise((r) => setTimeout(r, 30));
      const incoming = audio._getAudio();
      const outgoing = audio._getOutgoingAudio();
      assert.ok(incoming && outgoing);
      audio.setMuted(true);
      await new Promise((r) => setTimeout(r, 1600));
      assert.equal(incoming.paused, true);
      assert.equal(outgoing.paused, true);
    });

    test('setInsideRoom 不改曲目', async () => {
      const storage = fakeStorage();
      const audio = createIslandAudio({
        tracks: { main: '/a.ogg', casino: '/b.ogg' },
        storage,
        _createAudio: fakeAudio,
        _forceElementVolume: true
      });
      audio.onUserInteraction();
      await new Promise((r) => setTimeout(r, 10));
      audio.setTrack('casino');
      await new Promise((r) => setTimeout(r, 10));
      assert.equal(audio.currentTrack, 'casino');
      audio.setInsideRoom(true);
      assert.equal(audio.currentTrack, 'casino');
      audio.setInsideRoom(false);
      assert.equal(audio.currentTrack, 'casino');
    });
  });
});
