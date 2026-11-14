// 施工進度面板與告示牌文字。不依賴 Three.js。

export function signCopy(doc) {
  const phases = (doc && doc.phases) || [];
  const numbered = phases.filter((phase) => phase && phase.phase != null);
  const current = doc && doc.current != null ? doc.current : null;
  if (current == null && numbered.length > 0 && numbered.every((phase) => phase.done)) {
    return { headline: '群島完工', title: '' };
  }
  if (current != null) {
    const phase = phases.find((item) => item && item.phase === current);
    return { headline: '第 ' + current + ' 期施工中', title: phase && phase.title ? phase.title : '' };
  }
  return { headline: '施工進度', title: '' };
}

/** done 已完成、active 施工中、pending 未開工。只有有編號且等於 current 的才算施工中。 */
export function phaseStatus(phase, current) {
  if (!phase || phase.done) return 'done';
  if (phase.phase != null && phase.phase === current) return 'active';
  return 'pending';
}

const STATUS_LABEL = { done: '已完成', active: '施工中', pending: '未開工' };

export function createRoadmapController(opts) {
  const doc = (opts && opts.doc) || globalThis.document;
  const hooks = (opts && opts.hooks) || {};
  const root = doc && doc.getElementById ? doc.getElementById('roadmapBoard') : null;
  const headline = root && root.querySelector('[data-roadmap-headline]');
  const titleEl = root && root.querySelector('[data-roadmap-title]');
  const listEl = root && root.querySelector('[data-roadmap-list]');
  const noteEl = root && root.querySelector('[data-roadmap-note]');
  const sourceEl = root && root.querySelector('[data-roadmap-source]');
  const backBtn = root && root.querySelector('[data-roadmap-back]');
  let openState = false;
  let latest = null;

  function setOpen(next) {
    const want = !!next;
    if (want === openState) return;
    openState = want;
    if (root) {
      root.hidden = !want;
      root.setAttribute('aria-hidden', want ? 'false' : 'true');
    }
    if (doc.body) doc.body.classList.toggle('roadmap-open', want);
    if (typeof hooks.onChange === 'function') hooks.onChange({ open: want });
    if (want) refresh();
  }

  function apply(data) {
    latest = data || null;
    const copy = signCopy(latest);
    if (headline) headline.textContent = copy.headline;
    if (titleEl) titleEl.textContent = copy.title || '';
    if (noteEl) {
      if (latest && latest.message && (!latest.phases || !latest.phases.length)) {
        noteEl.textContent = latest.message;
      } else if (latest && latest.stale) {
        noteEl.textContent = '顯示的是上次讀到的進度';
      } else {
        noteEl.textContent = '';
      }
    }
    if (sourceEl) {
      const href = latest && latest.source ? latest.source : 'https://github.com/p4774756/PunchClock/issues/36';
      sourceEl.href = href;
      const match = /\/issues\/(\d+)\s*$/.exec(href);
      sourceEl.textContent = '資料來源：GitHub #' + (match ? match[1] : '36');
    }
    if (listEl) {
      while (listEl.firstChild) listEl.removeChild(listEl.firstChild);
      const phases = (latest && latest.phases) || [];
      const current = latest ? latest.current : null;
      phases.forEach((phase) => {
        if (!phase) return;
        const status = phaseStatus(phase, current);
        const row = doc.createElement('details');
        row.className = 'roadmap-phase is-' + status;
        if (status === 'active') row.open = true;
        const summary = doc.createElement('summary');
        const mark = doc.createElement('span');
        mark.className = 'roadmap-mark';
        mark.textContent = status === 'done' ? '✓' : (status === 'active' ? '●' : '○');
        const name = doc.createElement('span');
        name.className = 'roadmap-name';
        name.textContent = phase.phase == null
          ? (phase.title || '')
          : ('第 ' + phase.phase + ' 期 ' + (phase.title || ''));
        const tag = doc.createElement('span');
        tag.className = 'roadmap-tag';
        tag.textContent = STATUS_LABEL[status];
        summary.appendChild(mark);
        summary.appendChild(name);
        summary.appendChild(tag);
        row.appendChild(summary);
        const items = phase.items || [];
        if (items.length) {
          const ul = doc.createElement('ul');
          items.forEach((item) => {
            const li = doc.createElement('li');
            li.textContent = item == null ? '' : String(item);
            ul.appendChild(li);
          });
          row.appendChild(ul);
        }
        listEl.appendChild(row);
      });
    }
    if (typeof hooks.onData === 'function') hooks.onData(latest, copy);
  }

  function refresh() {
    const fetchFn = (opts && opts.fetch) || (globalThis.fetch ? globalThis.fetch.bind(globalThis) : null);
    if (!fetchFn) return;
    fetchFn('/api/roadmap').then((res) => res.json()).then((body) => {
      apply(body);
    }).catch(() => {
      apply({ success: false, phases: [], message: '暫時讀不到施工進度', stale: false, current: null });
    });
  }

  if (backBtn) backBtn.addEventListener('click', () => setOpen(false));
  if (doc && doc.querySelectorAll) {
    doc.querySelectorAll('[data-open-roadmap]').forEach((el) => {
      el.addEventListener('click', () => setOpen(true));
    });
  }

  return {
    open() { setOpen(true); },
    close() { setOpen(false); },
    isOpen() { return openState; },
    refresh: refresh,
    apply: apply
  };
}
