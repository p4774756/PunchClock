/**
 * 島嶼背景音樂模組。
 *
 * 主要 API：
 *   createIslandAudio({ tracks, storage, volume })
 *
 * tracks 格式：{ main: '/audio/bgm-main.ogg', casino?: '...', tcm?: '...', computer?: '...' }
 * 夜間版本預留：{ main_night: '...', casino_night: '...' }
 *
 * iOS Safari 的 HTMLMediaElement.volume 是唯讀，故使用 Web Audio API：
 * AudioContext + MediaElementAudioSourceNode + GainNode 控制音量與淡入淡出。
 * 不支援 AudioContext 時退回 element.volume。
 */

const STORAGE_KEY = 'punchclock.island.muted';
const FADE_DURATION = 1.5;

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

function supportsWebAudio() {
  return typeof AudioContext !== 'undefined' || typeof webkitAudioContext !== 'undefined';
}

function createAudioContext() {
  const Ctor = typeof AudioContext !== 'undefined' ? AudioContext : webkitAudioContext;
  return new Ctor();
}

/**
 * 建立島嶼音樂控制器。
 * @param {Object} opts
 * @param {Object<string, string>} opts.tracks - 音軌路徑
 * @param {Storage} [opts.storage] - localStorage 或替代品
 * @param {number} [opts.volume=0.35] - 基礎音量
 * @param {Function} [opts._createAudio] - 測試用：自訂 Audio 建構
 * @param {Object} [opts._audioContext] - 測試用：注入假 AudioContext
 * @param {boolean} [opts._forceElementVolume] - 測試用：強制使用 element.volume
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
  let disposed = false;
  let playSucceeded = false;

  let ctx = opts._audioContext || null;
  let gainNode = null;
  let sourceNode = null;
  let useWebAudio = !opts._forceElementVolume && (!!opts._audioContext || supportsWebAudio());
  let webAudioConnected = false;
  let playPending = false;

  function createAudio(src) {
    if (opts._createAudio) return opts._createAudio(src);
    const el = new Audio();
    el.src = src;
    el.loop = true;
    el.preload = 'auto';
    if (!useWebAudio) el.volume = 0;
    return el;
  }

  function ensureContext() {
    if (!useWebAudio) return false;
    if (ctx) return true;
    try {
      ctx = createAudioContext();
      return true;
    } catch {
      useWebAudio = false;
      return false;
    }
  }

  function connectWebAudio() {
    if (!useWebAudio || !ctx || !audio || webAudioConnected) return;
    try {
      if (!gainNode) {
        gainNode = ctx.createGain();
        gainNode.gain.setValueAtTime(0, ctx.currentTime);
        gainNode.connect(ctx.destination);
      }
      if (!sourceNode) {
        sourceNode = ctx.createMediaElementSource(audio);
        sourceNode.connect(gainNode);
      }
      webAudioConnected = true;
    } catch {
      useWebAudio = false;
      if (audio) audio.volume = 0;
    }
  }

  function setVolume(vol) {
    if (useWebAudio && gainNode && ctx) {
      gainNode.gain.setValueAtTime(vol, ctx.currentTime);
    } else if (audio) {
      try { audio.volume = vol; } catch { /* iOS readonly */ }
    }
  }

  function getVolume() {
    if (useWebAudio && gainNode) {
      return gainNode.gain.value;
    }
    return audio ? audio.volume : 0;
  }

  function fadeTo(targetVol, cb) {
    if (!audio) {
      if (cb) cb();
      return;
    }
    if (useWebAudio && gainNode && ctx) {
      const now = ctx.currentTime;
      gainNode.gain.cancelScheduledValues(now);
      gainNode.gain.setValueAtTime(gainNode.gain.value, now);
      gainNode.gain.linearRampToValueAtTime(targetVol, now + FADE_DURATION);
      if (cb) setTimeout(cb, FADE_DURATION * 1000 + 50);
    } else {
      setVolume(targetVol);
      if (cb) cb();
    }
  }

  function tryPlay() {
    if (disposed || muted || hidden || !audio) {
      awaitingPlay = false;
      return;
    }
    if (playPending) return;
    if (!audio.paused) {
      awaitingPlay = false;
      playSucceeded = true;
      fadeTo(baseVolume);
      return;
    }

    ensureContext();
    connectWebAudio();

    playPending = true;

    const resumePromise = (useWebAudio && ctx && ctx.state === 'suspended')
      ? ctx.resume().catch(() => {})
      : Promise.resolve();

    const playPromise = audio.play();

    const onSuccess = () => {
      playPending = false;
      awaitingPlay = false;
      playSucceeded = true;
      fadeTo(baseVolume);
    };

    const onFailure = () => {
      playPending = false;
      awaitingPlay = true;
    };

    if (playPromise && typeof playPromise.then === 'function') {
      Promise.all([resumePromise, playPromise])
        .then(onSuccess)
        .catch(onFailure);
    } else {
      resumePromise.then(onSuccess).catch(onSuccess);
    }
  }

  function pause() {
    if (audio) {
      audio.pause();
      setVolume(0);
    }
  }

  function loadTrack(id) {
    if (currentTrack === id && audio) return;
    const src = tracks[id];
    if (!src) return;
    pause();
    webAudioConnected = false;
    sourceNode = null;
    audio = createAudio(src);
    currentTrack = id;
  }

  function setMuted(m) {
    muted = !!m;
    safeSet(storage, STORAGE_KEY, muted);
    if (muted) {
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
    pause();
    if (ctx && typeof ctx.close === 'function') {
      try { ctx.close(); } catch {}
    }
    audio = null;
    ctx = null;
    gainNode = null;
    sourceNode = null;
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
    get playSucceeded() { return playSucceeded; },
    get available() { return checkAvailable(); },
    get usingWebAudio() { return useWebAudio && !!ctx; },
    setMuted,
    toggleMute() { setMuted(!muted); },
    onUserInteraction,
    onVisibilityChange,
    setTrack,
    preload,
    dispose,
    _getAudio() { return audio; },
    _getAwaiting() { return awaitingPlay; },
    _getGainNode() { return gainNode; },
    _getVolume() { return getVolume(); },
    _getPlayPending() { return playPending; }
  };
}
