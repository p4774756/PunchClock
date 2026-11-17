// 晝夜混合。每呼叫一次 step 前進 0.06，和原本每幀的速度相同。
export function createDayNight(opts) {
  const reduced = !!(opts && opts.reduced);
  const hidden = opts && typeof opts.hidden === 'function' ? opts.hidden : function () { return false; };
  let mix = 0;
  let target = 0;
  const listeners = [];

  function emit() {
    const batch = listeners.slice();
    for (let i = 0; i < batch.length; i++) batch[i](mix);
  }

  function setTarget(value) {
    target = value ? 1 : 0;
    if (reduced || hidden()) {
      mix = target;
      emit();
    }
  }

  function step() {
    const prev = mix;
    if (reduced) {
      mix = target;
    } else {
      mix += (target - mix) * 0.06;
      if (Math.abs(mix - target) < 1e-6) mix = target;
    }
    if (Math.abs(mix - prev) > 1e-9) emit();
  }

  function onMix(fn) {
    listeners.push(fn);
    return function unsubscribe() {
      const at = listeners.indexOf(fn);
      if (at >= 0) listeners.splice(at, 1);
    };
  }

  return {
    get mix() { return mix; },
    get target() { return target; },
    setTarget: setTarget,
    step: step,
    refresh: emit,
    onMix: onMix
  };
}
