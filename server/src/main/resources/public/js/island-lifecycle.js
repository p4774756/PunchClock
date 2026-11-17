// 探索／室內／分頁可見等生命週期事件；純 JS，無 DOM。
const EVENTS = [
  'enterExplore',
  'exitExplore',
  'enterRoom',
  'exitRoom',
  'visibilityChange'
];

export function createLifecycle() {
  const subs = {};
  for (let i = 0; i < EVENTS.length; i++) subs[EVENTS[i]] = [];

  function on(event, fn) {
    const list = subs[event];
    if (!list || typeof fn !== 'function') return function noop() {};
    list.push(fn);
    return function unsubscribe() {
      const at = list.indexOf(fn);
      if (at >= 0) list.splice(at, 1);
    };
  }

  function emit(event, payload) {
    const list = subs[event];
    if (!list) return;
    for (let i = 0; i < list.length; i++) list[i](payload);
  }

  function clear() {
    for (let i = 0; i < EVENTS.length; i++) subs[EVENTS[i]].length = 0;
  }

  return { on: on, emit: emit, clear: clear };
}
