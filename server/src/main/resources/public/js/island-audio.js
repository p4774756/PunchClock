/**
 * 島嶼背景音樂與環境音模組。
 *
 * 主要 API：
 *   createIslandAudio({ tracks, storage, volume, ambience })
 *   resolveTrackId({ islandId, buildingId, night, tracks })
 *
 * tracks 格式：{ main: '/audio/bgm-main.ogg', casino?: '...', tcm?: '...', computer?: '...' }
 * 夜間版本：{ main_night: '...', casino_night: '...' }
 * ambience 格式：{ src: '/audio/ocean-waves.ogg', volume: 0.22 }
 *
 * iOS Safari 的 HTMLMediaElement.volume 是唯讀，故使用 Web Audio API：
 * AudioContext + MediaElementAudioSourceNode + GainNode 控制音量與淡入淡出。
 * 不支援 AudioContext 時退回 element.volume。
 */

const STORAGE_KEY = 'punchclock.island.muted';
const FADE_DURATION = 1.5;

const BUILDING_ISLAND = Object.freeze({
  shop: 'main',
  casino: 'casino',
  'tcm-site': 'tcm',
  'computer-site': 'computer'
});

/**
 * 依島嶼／室內建築／日夜選曲目 id（不含路徑）。
 * @param {Object} opts
 * @param {string|null} [opts.islandId] - islandAt 的 id，海上／橋為 null
 * @param {string|null} [opts.buildingId] - 室內 buildingId
 * @param {boolean} opts.night
 * @param {Object<string, string>} [opts.tracks]
 */
export function resolveTrackId(opts) {
  const islandId = opts.islandId != null ? opts.islandId : null;
  const buildingId = opts.buildingId != null ? opts.buildingId : null;
  const night = !!opts.night;
  const tracks = opts.tracks || {};

  let base = 'main';
  if (buildingId) {
    base = BUILDING_ISLAND[buildingId] || 'main';
  } else if (islandId && islandId !== 'beach') {
    base = islandId;
  }

  const dayId =
    base === 'main' || base === 'casino' || base === 'tcm' || base === 'computer' || base === 'city'
      ? base
      : 'main';

  if (night) {
    const nightId = dayId + '_night';
    if (tracks[nightId]) return nightId;
    if (tracks[dayId]) return dayId;
    if (tracks.main_night) return 'main_night';
    return 'main';
  }
  if (tracks[dayId]) return dayId;
  return 'main';
}

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
 * @param {number} [opts.volume=0.35] - 背景音樂基礎音量
 * @param {Object} [opts.ambience] - 環境音設定 { src, volume }
 * @param {Function} [opts._createAudio] - 測試用：自訂 Audio 建構
 * @param {Object} [opts._audioContext] - 測試用：注入假 AudioContext
 * @param {boolean} [opts._forceElementVolume] - 測試用：強制使用 element.volume
 * @returns {Object} audio controller
 */
export function createIslandAudio(opts) {
  const tracks = opts.tracks || {};
  const storage = opts.storage || null;
  const baseVolume = opts.volume != null ? opts.volume : 0.35;
  const ambience = opts.ambience || null;
  const ambienceVolume = ambience && ambience.volume != null ? ambience.volume : 0.22;

  let audio = opts._audio || null;
  let currentTrackId = null;
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

  let crossfadeOutgoing = null;
  let crossfadeOutgoingGain = null;
  let crossfadeTimer = null;
  let elementFadeTimer = null;

  let ambienceAudio = null;
  let ambienceGain = null;
  let ambienceSource = null;
  let ambienceConnected = false;
  let ambiencePlayPending = false;
  let ambiencePlaySucceeded = false;
  let ambienceAwaitingPlay = false;
  let insideRoom = false;
  const AMBIENCE_INDOOR_FACTOR = 0.3;

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

  function connectAmbienceWebAudio() {
    if (!useWebAudio || !ctx || !ambienceAudio || ambienceConnected) return;
    try {
      if (!ambienceGain) {
        ambienceGain = ctx.createGain();
        ambienceGain.gain.setValueAtTime(0, ctx.currentTime);
        ambienceGain.connect(ctx.destination);
      }
      if (!ambienceSource) {
        ambienceSource = ctx.createMediaElementSource(ambienceAudio);
        ambienceSource.connect(ambienceGain);
      }
      ambienceConnected = true;
    } catch {
      // silently fail
    }
  }

  function setVolume(vol) {
    if (useWebAudio && gainNode && ctx) {
      gainNode.gain.setValueAtTime(vol, ctx.currentTime);
    } else if (audio) {
      try { audio.volume = vol; } catch { /* iOS readonly */ }
    }
  }

  function setOutgoingVolume(vol) {
    if (useWebAudio && crossfadeOutgoingGain && ctx) {
      crossfadeOutgoingGain.gain.setValueAtTime(vol, ctx.currentTime);
    } else if (crossfadeOutgoing) {
      try { crossfadeOutgoing.volume = vol; } catch { /* iOS readonly */ }
    }
  }

  function setAmbienceVolume(vol) {
    if (useWebAudio && ambienceGain && ctx) {
      ambienceGain.gain.setValueAtTime(vol, ctx.currentTime);
    } else if (ambienceAudio) {
      try { ambienceAudio.volume = vol; } catch { /* iOS readonly */ }
    }
  }

  function getVolume() {
    if (useWebAudio && gainNode) {
      return gainNode.gain.value;
    }
    return audio ? audio.volume : 0;
  }

  function getAmbienceVolume() {
    if (useWebAudio && ambienceGain) {
      return ambienceGain.gain.value;
    }
    return ambienceAudio ? ambienceAudio.volume : 0;
  }

  function currentAmbienceTarget() {
    const base = ambienceVolume;
    return insideRoom ? base * AMBIENCE_INDOOR_FACTOR : base;
  }

  function clearCrossfadeTimers() {
    if (crossfadeTimer) {
      clearTimeout(crossfadeTimer);
      crossfadeTimer = null;
    }
    if (elementFadeTimer) {
      clearInterval(elementFadeTimer);
      elementFadeTimer = null;
    }
  }

  function finishOutgoing() {
    if (crossfadeOutgoing) {
      crossfadeOutgoing.pause();
      crossfadeOutgoing = null;
    }
    crossfadeOutgoingGain = null;
    clearCrossfadeTimers();
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

  function fadeAmbienceTo(targetVol, cb) {
    if (!ambienceAudio) {
      if (cb) cb();
      return;
    }
    if (useWebAudio && ambienceGain && ctx) {
      const now = ctx.currentTime;
      ambienceGain.gain.cancelScheduledValues(now);
      ambienceGain.gain.setValueAtTime(ambienceGain.gain.value, now);
      ambienceGain.gain.linearRampToValueAtTime(targetVol, now + FADE_DURATION);
      if (cb) setTimeout(cb, FADE_DURATION * 1000 + 50);
    } else {
      setAmbienceVolume(targetVol);
      if (cb) cb();
    }
  }

  function fadeAllMusicTo(targetVol, cb) {
    let pending = audio ? 1 : 0;
    if (crossfadeOutgoing) pending += 1;
    if (!pending) {
      if (cb) cb();
      return;
    }
    const done = () => {
      pending -= 1;
      if (pending <= 0 && cb) cb();
    };
    if (audio) {
      if (useWebAudio && gainNode && ctx) {
        const now = ctx.currentTime;
        gainNode.gain.cancelScheduledValues(now);
        gainNode.gain.setValueAtTime(gainNode.gain.value, now);
        gainNode.gain.linearRampToValueAtTime(targetVol, now + FADE_DURATION);
        setTimeout(done, FADE_DURATION * 1000 + 50);
      } else {
        setVolume(targetVol);
        done();
      }
    }
    if (crossfadeOutgoing) {
      if (useWebAudio && crossfadeOutgoingGain && ctx) {
        const now = ctx.currentTime;
        crossfadeOutgoingGain.gain.cancelScheduledValues(now);
        crossfadeOutgoingGain.gain.setValueAtTime(crossfadeOutgoingGain.gain.value, now);
        crossfadeOutgoingGain.gain.linearRampToValueAtTime(targetVol, now + FADE_DURATION);
        setTimeout(done, FADE_DURATION * 1000 + 50);
      } else {
        setOutgoingVolume(targetVol);
        done();
      }
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

  function tryPlayAmbience() {
    if (disposed || muted || hidden || !ambienceAudio) {
      ambienceAwaitingPlay = false;
      return;
    }
    if (ambiencePlayPending) return;
    if (!ambienceAudio.paused) {
      ambienceAwaitingPlay = false;
      ambiencePlaySucceeded = true;
      fadeAmbienceTo(currentAmbienceTarget());
      return;
    }

    ensureContext();
    connectAmbienceWebAudio();

    ambiencePlayPending = true;

    const resumePromise = (useWebAudio && ctx && ctx.state === 'suspended')
      ? ctx.resume().catch(() => {})
      : Promise.resolve();

    const playPromise = ambienceAudio.play();

    const onSuccess = () => {
      ambiencePlayPending = false;
      ambienceAwaitingPlay = false;
      ambiencePlaySucceeded = true;
      fadeAmbienceTo(currentAmbienceTarget());
    };

    const onFailure = () => {
      ambiencePlayPending = false;
      ambienceAwaitingPlay = true;
    };

    if (playPromise && typeof playPromise.then === 'function') {
      Promise.all([resumePromise, playPromise])
        .then(onSuccess)
        .catch(onFailure);
    } else {
      resumePromise.then(onSuccess).catch(onSuccess);
    }
  }

  function pauseMusicElements() {
    if (audio) {
      audio.pause();
      setVolume(0);
    }
    if (crossfadeOutgoing) {
      crossfadeOutgoing.pause();
      setOutgoingVolume(0);
    }
    finishOutgoing();
  }

  function pause() {
    pauseMusicElements();
  }

  function pauseAmbience() {
    if (ambienceAudio) {
      ambienceAudio.pause();
      setAmbienceVolume(0);
    }
  }

  function loadTrack(id) {
    if (currentTrackId === id && audio) return;
    const src = tracks[id];
    if (!src) return;
    finishOutgoing();
    if (audio) {
      audio.pause();
    }
    webAudioConnected = false;
    sourceNode = null;
    audio = createAudio(src);
    currentTrackId = id;
  }

  function crossfadeTo(id, src) {
    finishOutgoing();
    const outgoing = audio;
    const outgoingGain = gainNode;

    const nextAudio = createAudio(src);
    currentTrackId = id;
    audio = nextAudio;
    webAudioConnected = false;
    sourceNode = null;
    gainNode = null;

    if (outgoing) {
      crossfadeOutgoing = outgoing;
      crossfadeOutgoingGain = outgoingGain;
    }

    const beginCrossfade = () => {
      if (disposed || muted || hidden) return;

      if (useWebAudio && ensureContext()) {
        gainNode = ctx.createGain();
        gainNode.gain.setValueAtTime(0, ctx.currentTime);
        gainNode.connect(ctx.destination);
        sourceNode = ctx.createMediaElementSource(nextAudio);
        sourceNode.connect(gainNode);
        webAudioConnected = true;

        const now = ctx.currentTime;
        gainNode.gain.linearRampToValueAtTime(baseVolume, now + FADE_DURATION);
        if (crossfadeOutgoingGain && crossfadeOutgoing) {
          crossfadeOutgoingGain.gain.cancelScheduledValues(now);
          crossfadeOutgoingGain.gain.setValueAtTime(crossfadeOutgoingGain.gain.value, now);
          crossfadeOutgoingGain.gain.linearRampToValueAtTime(0, now + FADE_DURATION);
        }

        crossfadeTimer = setTimeout(() => {
          if (crossfadeOutgoing) {
            crossfadeOutgoing.pause();
          }
          crossfadeOutgoing = null;
          crossfadeOutgoingGain = null;
          crossfadeTimer = null;
        }, FADE_DURATION * 1000 + 50);
      } else {
        try { nextAudio.volume = 0; } catch { /* readonly */ }
        const start = Date.now();
        const fromOut = crossfadeOutgoing ? crossfadeOutgoing.volume : 0;
        elementFadeTimer = setInterval(() => {
          const t = Math.min(1, (Date.now() - start) / (FADE_DURATION * 1000));
          const vol = baseVolume * t;
          const outVol = fromOut * (1 - t);
          try { nextAudio.volume = vol; } catch { /* readonly */ }
          if (crossfadeOutgoing) {
            try { crossfadeOutgoing.volume = outVol; } catch { /* readonly */ }
          }
          if (t >= 1) {
            clearInterval(elementFadeTimer);
            elementFadeTimer = null;
            if (crossfadeOutgoing) {
              crossfadeOutgoing.pause();
            }
            crossfadeOutgoing = null;
            crossfadeOutgoingGain = null;
          }
        }, 50);
      }
    };

    const playPromise = nextAudio.play();
    const afterPlay = () => {
      playSucceeded = true;
      beginCrossfade();
    };

    if (playPromise && typeof playPromise.then === 'function') {
      playPromise.then(afterPlay).catch(() => {
        awaitingPlay = true;
      });
    } else {
      afterPlay();
    }
  }

  function loadAmbience() {
    if (ambienceAudio || !ambience || !ambience.src) return;
    ambienceAudio = createAudio(ambience.src);
  }

  function setMuted(m) {
    muted = !!m;
    safeSet(storage, STORAGE_KEY, muted);
    if (muted) {
      const anyPlaying =
        (audio && !audio.paused) ||
        (crossfadeOutgoing && !crossfadeOutgoing.paused);
      if (anyPlaying) {
        fadeAllMusicTo(0, () => pauseMusicElements());
      }
      if (ambienceAudio && !ambienceAudio.paused) {
        fadeAmbienceTo(0, () => pauseAmbience());
      }
    } else if (userInteracted && !hidden) {
      tryPlay();
      tryPlayAmbience();
    }
  }

  function onUserInteraction() {
    if (userInteracted) {
      if (awaitingPlay && !muted && !hidden) tryPlay();
      if (ambienceAwaitingPlay && !muted && !hidden) tryPlayAmbience();
      return;
    }
    userInteracted = true;
    if (!currentTrackId) loadTrack('main');
    loadAmbience();
    if (!muted && !hidden) {
      tryPlay();
      tryPlayAmbience();
    }
  }

  function onVisibilityChange(isHidden) {
    hidden = !!isHidden;
    if (hidden) {
      pause();
      pauseAmbience();
    } else if (userInteracted && !muted) {
      tryPlay();
      tryPlayAmbience();
    }
  }

  function setTrack(id) {
    const src = tracks[id];
    if (!src) return;
    if (currentTrackId === id && audio && !crossfadeOutgoing) {
      if (userInteracted && !muted && !hidden && audio.paused) tryPlay();
      return;
    }
    if (!userInteracted) {
      loadTrack(id);
      return;
    }
    if (!audio) {
      loadTrack(id);
      if (!muted && !hidden) tryPlay();
      return;
    }
    crossfadeTo(id, src);
  }

  function setInsideRoom(inside) {
    const was = insideRoom;
    insideRoom = !!inside;
    if (was !== insideRoom && ambienceAudio && !ambienceAudio.paused && !muted && !hidden) {
      fadeAmbienceTo(currentAmbienceTarget());
    }
  }

  function dispose() {
    disposed = true;
    pause();
    pauseAmbience();
    if (ctx && typeof ctx.close === 'function') {
      try { ctx.close(); } catch {}
    }
    audio = null;
    ambienceAudio = null;
    ctx = null;
    gainNode = null;
    sourceNode = null;
    ambienceGain = null;
    ambienceSource = null;
    crossfadeOutgoing = null;
    crossfadeOutgoingGain = null;
  }

  function preload() {
    if (currentTrackId) return;
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
    get ambiencePlaying() { return ambienceAudio && !ambienceAudio.paused; },
    get ambiencePlaySucceeded() { return ambiencePlaySucceeded; },
    get currentTrack() { return currentTrackId; },
    setMuted,
    toggleMute() { setMuted(!muted); },
    onUserInteraction,
    onVisibilityChange,
    setTrack,
    setInsideRoom,
    preload,
    dispose,
    _getAudio() { return audio; },
    _getOutgoingAudio() { return crossfadeOutgoing; },
    _getCurrentTrackId() { return currentTrackId; },
    _getAwaiting() { return awaitingPlay; },
    _getGainNode() { return gainNode; },
    _getVolume() { return getVolume(); },
    _getPlayPending() { return playPending; },
    _getAmbienceAudio() { return ambienceAudio; },
    _getAmbienceGainNode() { return ambienceGain; },
    _getAmbienceVolume() { return getAmbienceVolume(); },
    _getAmbienceAwaiting() { return ambienceAwaitingPlay; }
  };
}