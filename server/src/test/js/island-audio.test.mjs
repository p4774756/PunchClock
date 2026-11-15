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
    await Promise.resolve();
    assert.equal(fakeA.paused, false, 'should be playing');

    audio.onVisibilityChange(true);
    assert.equal(fakeA.paused, true, 'should pause when hidden');

    audio.onVisibilityChange(false);
    await Promise.resolve();
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
  });
});
