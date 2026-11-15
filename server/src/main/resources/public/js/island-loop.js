// 動畫更新登記。tick 依登記順序呼叫；函式裡取消自己不會跳過後面的。
export function createUpdateLoop(opts) {
  const requestAnimationFrame = (opts && opts.requestAnimationFrame)
    || function (cb) { return globalThis.requestAnimationFrame(cb); };
  const cancelAnimationFrame = (opts && opts.cancelAnimationFrame)
    || function (id) { globalThis.cancelAnimationFrame(id); };
  const updates = [];
  let running = false;
  let raf = 0;

  function register(fn) {
    updates.push(fn);
    return function unregisterSelf() { unregister(fn); };
  }

  function unregister(fn) {
    const at = updates.indexOf(fn);
    if (at >= 0) updates.splice(at, 1);
  }

  function tick(dt, elapsed) {
    for (let i = 0; i < updates.length;) {
      const fn = updates[i];
      fn(dt, elapsed);
      if (updates[i] === fn) i += 1;
    }
  }

  function start(frame) {
    if (running) return;
    running = true;
    const loop = function () {
      if (!running) return;
      frame();
      raf = requestAnimationFrame(loop);
    };
    raf = requestAnimationFrame(loop);
  }

  function stop() {
    running = false;
    cancelAnimationFrame(raf);
  }

  return {
    register: register,
    unregister: unregister,
    tick: tick,
    start: start,
    stop: stop
  };
}
