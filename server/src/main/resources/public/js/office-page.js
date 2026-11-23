// 首頁「私人秘書」3D 一角。

import { createRoadmapController } from './roadmap-panel.js';
import { createHomeTasksController } from './home-tasks-panel.js';
import {
  applyAtlasLoginOpenParam,
  applyRoadmapOpenParam,
  applyTasksOpenParam
} from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

let privateTeam = null;
let tasks = null;

const roadmap = createRoadmapController({
  hooks: {
    onChange: (state) => {
      if (state.open && tasks && tasks.isOpen()) {
        tasks.close();
      }
      if (privateTeam && typeof privateTeam.setRoadmapOpen === 'function') {
        privateTeam.setRoadmapOpen(state.open);
      }
    }
  }
});

tasks = createHomeTasksController({
  hooks: {
    onChange: (state) => {
      if (state.open && roadmap.isOpen()) {
        roadmap.close();
      }
      if (privateTeam && typeof privateTeam.setTasksOpen === 'function') {
        privateTeam.setTasksOpen(state.open);
      }
    }
  }
});

function openTasksFromHud() {
  if (roadmap.isOpen()) roadmap.close();
  tasks.open();
}

privateTeam = mountPrivateTeamPage({
  doc: document,
  win: window,
  hooks: {
    openRoadmap: () => roadmap.open(),
    openTasks: () => openTasksFromHud(),
    closeTasks: () => tasks.close()
  }
});

applyRoadmapOpenParam(window, () => roadmap.open());
applyTasksOpenParam(window, () => {
  if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
});
applyAtlasLoginOpenParam(window, {
  isAuthed: () => !!window.PUNCHCLOCK_AUTHENTICATED,
  openAtlas: () => {
    window.location.assign('/atlas/tcm-3d/');
  },
  openLoginForAtlas: () => privateTeam.openLoginForAtlas()
});
roadmap.refresh();