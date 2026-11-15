import { test, describe } from 'node:test';
import assert from 'node:assert/strict';
import { loadPublicModule } from './load-public-module.mjs';

const { createIslandAudio } = await loadPublicModule('island-audio.js');

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

  describe('ambience audio', () => {
    test('ambience plays together with music on interaction', async () => {
      let musicPlayCalled = false;
      let ambiencePlayCalled = false;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 },
        _createAudio: (src) => {
          const a = fakeAudio();
          a.src = src;
          const origPlay = a.play.bind(a);
          a.play = () => {
            if (src.indexOf('bgm') >= 0) musicPlayCalled = true;
            else ambiencePlayCalled = true;
            return origPlay();
          };
          return a;
        },
        _forceElementVolume: true
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(musicPlayCalled, true, 'music should play');
      assert.equal(ambiencePlayCalled, true, 'ambience should play');
      assert.equal(audio.ambiencePlaying, true, 'ambience should be playing');
    });

    test('ambience has separate GainNode from music', async () => {
      const ctx = fakeAudioContext();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 },
        _createAudio: fakeAudio,
        _audioContext: ctx
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      const musicGain = audio._getGainNode();
      const ambienceGain = audio._getAmbienceGainNode();
      assert.ok(musicGain, 'music GainNode should exist');
      assert.ok(ambienceGain, 'ambience GainNode should exist');
      assert.notEqual(musicGain, ambienceGain, 'should be separate GainNodes');
    });

    test('setMuted(true) mutes both music and ambience', async () => {
      const ctx = fakeAudioContext();
      let musicAudio = null;
      let ambienceAudio = null;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 },
        _createAudio: (src) => {
          const a = fakeAudio();
          a.src = src;
          if (src.indexOf('bgm') >= 0) musicAudio = a;
          else ambienceAudio = a;
          return a;
        },
        _audioContext: ctx
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 50));
      assert.equal(audio.playing, true, 'music playing');
      assert.equal(audio.ambiencePlaying, true, 'ambience playing');

      audio.setMuted(true);
      await new Promise(r => setTimeout(r, 100));
      assert.equal(audio.muted, true, 'should be muted');
      assert.equal(audio._getVolume(), 0, 'music volume should be 0');
      assert.equal(audio._getAmbienceVolume(), 0, 'ambience volume should be 0');
    });

    test('onVisibilityChange(true) pauses both music and ambience', async () => {
      let musicAudio = null;
      let ambienceAudio = null;
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 },
        _createAudio: (src) => {
          const a = fakeAudio();
          a.src = src;
          if (src.indexOf('bgm') >= 0) musicAudio = a;
          else ambienceAudio = a;
          return a;
        },
        _forceElementVolume: true
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(musicAudio.paused, false, 'music should be playing');
      assert.equal(ambienceAudio.paused, false, 'ambience should be playing');

      audio.onVisibilityChange(true);
      assert.equal(musicAudio.paused, true, 'music should pause when hidden');
      assert.equal(ambienceAudio.paused, true, 'ambience should pause when hidden');

      audio.onVisibilityChange(false);
      await new Promise(r => setTimeout(r, 10));
      assert.equal(musicAudio.paused, false, 'music should resume');
      assert.equal(ambienceAudio.paused, false, 'ambience should resume');
    });

    test('no ambience when ambience option not provided', async () => {
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        _createAudio: fakeAudio,
        _forceElementVolume: true
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 10));
      assert.equal(audio.playing, true, 'music should play');
      assert.ok(!audio.ambiencePlaying, 'no ambience should be playing');
      assert.equal(audio._getAmbienceAudio(), null, 'ambience audio should be null');
    });

    test('setIndoor reduces ambience volume', async () => {
      const ctx = fakeAudioContext();
      const audio = createIslandAudio({
        tracks: { main: '/audio/bgm.ogg' },
        storage: fakeStorage(),
        ambience: { src: '/audio/ocean-waves.ogg', volume: 0.22 },
        _createAudio: fakeAudio,
        _audioContext: ctx
      });

      audio.onUserInteraction();
      await new Promise(r => setTimeout(r, 50));
      const outdoorVol = audio._getAmbienceVolume();
      assert.ok(Math.abs(outdoorVol - 0.22) < 0.01, 'outdoor volume should be ~0.22');

      audio.setIndoor(true);
      await new Promise(r => setTimeout(r, 50));
      const indoorVol = audio._getAmbienceVolume();
      assert.ok(indoorVol < outdoorVol, 'indoor volume should be less than outdoor');
      assert.ok(indoorVol > 0, 'indoor volume should not be zero');

      audio.setIndoor(false);
      await new Promise(r => setTimeout(r, 50));
      const backOutdoorVol = audio._getAmbienceVolume();
      assert.ok(Math.abs(backOutdoorVol - 0.22) < 0.01, 'should restore outdoor volume');
    });
  });
});
