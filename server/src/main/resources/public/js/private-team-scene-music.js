/**
 * 辦公室背景音樂：點 3D 場景裡的唱片機開／關。
 * 瀏覽器擋自動播放，所以只在使用者點擊（手勢）時才呼叫 play()。
 * 音檔是自製的 lo-fi 爵士循環（無版權問題），音量刻意放小。
 */
export const OFFICE_MUSIC_URL = '/audio/office-lofi.m4a';
export const OFFICE_MUSIC_VOLUME = 0.32;

/** 唱盤是否要轉：只有音樂播放中才轉，且減少動態時不轉。 */
export function vinylPlaybackSpin(playing, reducedMotion) {
  return !!playing && !reducedMotion;
}

/** 唱針臂放到唱片上（弧度，繞臂樞軸的 Y 軸）；停止時回到休息位。 */
export const VINYL_ARM_REST_Y = 0;
export const VINYL_ARM_PLAY_Y = -0.7;

export function vinylArmTarget(playing) {
  return playing ? VINYL_ARM_PLAY_Y : VINYL_ARM_REST_Y;
}

export function createOfficeMusic(opts) {
  const win = (opts && opts.win) || (typeof window !== 'undefined' ? window : null);
  const url = (opts && opts.url) || OFFICE_MUSIC_URL;
  const volume = opts && typeof opts.volume === 'number' ? opts.volume : OFFICE_MUSIC_VOLUME;
  let audio = null;
  let playing = false;
  const listeners = [];

  function notify() {
    for (let i = 0; i < listeners.length; i++) {
      try {
        listeners[i](playing);
      } catch (_e) { /* noop */ }
    }
  }

  function ensureAudio() {
    if (audio) return audio;
    const Ctor = (win && win.Audio) || (typeof Audio !== 'undefined' ? Audio : null);
    if (!Ctor) return null;
    audio = new Ctor(url);
    audio.loop = true;
    audio.preload = 'auto';
    audio.volume = volume;
    return audio;
  }

  function start() {
    const a = ensureAudio();
    if (!a) return false;
    playing = true;
    notify();
    try {
      const p = a.play();
      if (p && typeof p.catch === 'function') {
        p.catch(() => {
          // 被瀏覽器拒絕（沒有手勢、解碼失敗…）：回到停止狀態，不要讓唱盤空轉。
          playing = false;
          notify();
        });
      }
    } catch (_e) {
      playing = false;
      notify();
      return false;
    }
    return true;
  }

  function stop() {
    const was = playing;
    playing = false;
    if (audio) {
      try {
        audio.pause();
      } catch (_e) { /* noop */ }
    }
    if (was) notify();
  }

  return {
    toggle() {
      if (playing) stop();
      else start();
      return playing;
    },
    start,
    stop,
    isPlaying() {
      return playing;
    },
    onChange(fn) {
      if (typeof fn === 'function') listeners.push(fn);
    },
    dispose() {
      stop();
      if (audio) {
        audio.removeAttribute && audio.removeAttribute('src');
        audio = null;
      }
      listeners.length = 0;
    }
  };
}
