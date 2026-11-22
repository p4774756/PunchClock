// 首頁「私人秘書」3D 一角。

import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam } from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

const roadmap = createRoadmapController({});
applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();

mountPrivateTeamPage({
  doc: document,
  win: window,
  hooks: {
    openRoadmap: () => roadmap.open()
  }
});