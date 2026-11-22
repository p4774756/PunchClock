import { createGraffitiController } from './graffiti-panel.js';
import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam, shouldOpenRoadmapFromSearch } from './dev-machines-format.js';
import {
  buildConsoleRoadmapUrl,
  buildConsoleTasksUrl,
  roadmapOpenFromPopstateHref,
  stripRoadmapOpenSearch
} from './console-nav-history.js';

createGraffitiController({});
const roadmap = createRoadmapController({
  onChange: ({ open }) => {
    if (window.PunchclockConsoleChrome && typeof window.PunchclockConsoleChrome.setRoadmapOpen === 'function') {
      window.PunchclockConsoleChrome.setRoadmapOpen(open);
    }
    syncRoadmapUrl(open);
  }
});

window.PunchclockConsoleRoadmap = {
  close: () => roadmap.close(),
  open: () => roadmap.open(),
  isOpen: () => roadmap.isOpen()
};

let roadmapHistoryDepth = 0;

function syncRoadmapUrl(open) {
  if (!window.location || !window.history) return;
  try {
    const path = window.location.pathname || '/console';
    if (open) {
      if (!shouldOpenRoadmapFromSearch(window.location.search || '')) {
        const hash = window.location.hash || '';
        window.history.pushState({ punchclockRoadmap: true }, '', buildConsoleRoadmapUrl(path) + hash);
        roadmapHistoryDepth += 1;
      }
      return;
    }
    const cleanSearch = stripRoadmapOpenSearch(window.location.search || '');
    const hash = window.location.hash || '#devices';
    const next = path + cleanSearch + (hash || '#devices');
    if (window.location.pathname + window.location.search + (window.location.hash || '') !== next) {
      window.history.replaceState(null, '', next);
    }
    roadmapHistoryDepth = Math.max(0, roadmapHistoryDepth - 1);
  } catch (e) {
    /* ignore */
  }
}

document.querySelectorAll('[data-open-roadmap]').forEach((el) => {
  el.addEventListener('click', () => roadmap.open());
});

applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();

window.addEventListener('popstate', () => {
  const wantOpen = roadmapOpenFromPopstateHref(window.location.href);
  if (wantOpen && !roadmap.isOpen()) {
    roadmap.open();
    return;
  }
  if (!wantOpen && roadmap.isOpen()) {
    roadmap.close();
    return;
  }
  if (!wantOpen && window.PunchclockConsoleChrome) {
    window.PunchclockConsoleChrome.setRoadmapOpen(false);
  }
});

document.querySelectorAll('.console-tab[data-tab="devices"]').forEach((el) => {
  el.addEventListener('click', () => {
    if (roadmap.isOpen()) roadmap.close();
    try {
      window.history.replaceState(null, '', buildConsoleTasksUrl(window.location.pathname, 'devices'));
    } catch (e) {}
  });
});