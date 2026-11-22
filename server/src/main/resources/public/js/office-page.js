// 首頁「私人秘書」3D 一角。

import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam } from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

let privateTeam = null;
const roadmap = createRoadmapController({
  hooks: {
    onChange: (state) => {
      if (privateTeam && typeof privateTeam.setRoadmapOpen === 'function') {
        privateTeam.setRoadmapOpen(state.open);
      }
    }
  }
});

privateTeam = mountPrivateTeamPage({
  doc: document,
  win: window,
  hooks: {
    openRoadmap: () => roadmap.open()
  }
});

applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();