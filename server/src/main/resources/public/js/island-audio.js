/**
 * 島嶼背景音樂模組。
 *
 * 主要 API：
 *   createIslandAudio({ tracks, storage, volume })
 *
 * tracks 格式：{ main: '/audio/bgm-main.ogg', casino?: '...', tcm?: '...', computer?: '...' }
 * 夜間版本預留：{ main_night: '...', casino_night: '...' }
 */

const STORAGE_KEY = 'punchclock.island.muted';
const FADE_DURATION = 1500;
const FADE_STEP = 30;

function safeParse(storage, key, fallback) {
  if (!storage) return fallback;
  try {
    const raw = storage.getItem(key);
    if (raw == null) return fallback;
    return JSON.parse(raw);
  } catch {
    return fallback;
  }
}

function safeSet(storage, key, value) {
  if (!storage) return;
  try {
    storage.setItem(key, JSON.stringify(value));
  } catch {
    // 無痕模式可能禁止寫入
  }
}

/**
 * 建立島嶼音樂控制器。
 * @param {Object} opts
 * @param {Object<string, string>} opts.tracks - 音軌路徑
 * @param {Storage} [opts.storage] - localStorage 或替代品
 * @param {number} [opts.volume=0.35] - 基礎音量
 * @returns {Object} audio controller
 */
export function createIslandAudio(opts) {
  const tracks = opts.tracks || {};
  const storage = opts.storage || null;
  const baseVolume = opts.volume != null ? opts.volume : 0.35;

  let audio = opts._audio || null;
  let currentTrack = null;
  let muted = safeParse(storage, STORAGE_KEY, false);
  let userInteracted = false;
  let awaitingPlay = false;
  let hidden = false;
  let fadeTimer = null;
  let disposed = false;

  function createAudio(src) {
    if (opts._createAudio) return opts._createAudio(src);
    const el = new Audio();
    el.src = src;
    el.loop = true;
    el.volume = 0;
    el.preload = 'auto';
    return el;
  }

  function stopFade() {
    if (fadeTimer != null) {
      clearInterval(fadeTimer);
      fadeTimer = null;
    }
  }

  function fadeTo(targetVol, cb) {
    stopFade();
    if (!audio) {
      if (cb) cb();
      return;
    }
    const start = audio.volume;
    const delta = targetVol - start;
    const steps = Math.max(1, Math.round(FADE_DURATION / FADE_STEP));
    let step = 0;
    fadeTimer = setInterval(() => {
      step += 1;
      const t = step / steps;
      const vol = start + delta * t;
      if (audio) audio.volume = Math.max(0, Math.min(1, vol));
      if (step >= steps) {
        stopFade();
        if (cb) cb();
      }
    }, FADE_STEP);
  }

  function tryPlay() {
    if (disposed || muted || hidden || !audio) {
      awaitingPlay = false;
      return;
    }
    if (audio.paused) {
      const p = audio.play();
      if (p && typeof p.catch === 'function') {
        p.then(() => {
          awaitingPlay = false;
          fadeTo(baseVolume);
        }).catch(() => {
          awaitingPlay = true;
        });
      } else {
        awaitingPlay = false;
        fadeTo(baseVolume);
      }
    } else {
      awaitingPlay = false;
      fadeTo(baseVolume);
    }
  }

  function pause() {
    stopFade();
    if (audio) {
      audio.pause();
      audio.volume = 0;
    }
  }

  function loadTrack(id) {
    if (currentTrack === id && audio) return;
    const src = tracks[id];
    if (!src) return;
    pause();
    audio = createAudio(src);
    currentTrack = id;
  }

  function setMuted(m) {
    muted = !!m;
    safeSet(storage, STORAGE_KEY, muted);
    if (muted) {
      stopFade();
      if (audio && !audio.paused) {
        fadeTo(0, () => pause());
      }
    } else if (userInteracted && !hidden) {
      tryPlay();
    }
  }

  function onUserInteraction() {
    if (userInteracted) {
      if (awaitingPlay && !muted && !hidden) tryPlay();
      return;
    }
    userInteracted = true;
    if (!currentTrack) loadTrack('main');
    if (!muted && !hidden) tryPlay();
  }

  function onVisibilityChange(isHidden) {
    hidden = !!isHidden;
    if (hidden) {
      pause();
    } else if (userInteracted && !muted) {
      tryPlay();
    }
  }

  function setTrack(id) {
    loadTrack(id);
    if (userInteracted && !muted && !hidden) tryPlay();
  }

  function dispose() {
    disposed = true;
    stopFade();
    pause();
    audio = null;
  }

  function preload() {
    if (currentTrack) return;
    const src = tracks.main;
    if (!src) return;
    loadTrack('main');
  }

  function checkAvailable() {
    return !!(tracks && tracks.main);
  }

  return {
    get muted() { return muted; },
    get interacted() { return userInteracted; },
    get playing() { return audio && !audio.paused; },
    get available() { return checkAvailable(); },
    setMuted,
    toggleMute() { setMuted(!muted); },
    onUserInteraction,
    onVisibilityChange,
    setTrack,
    preload,
    dispose,
    _getAudio() { return audio; },
    _getAwaiting() { return awaitingPlay; }
  };
}
