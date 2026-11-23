// 首頁「私人秘書」3D 一角。

import { createRoadmapController } from './roadmap-panel.js';
import { createHomeTasksController } from './home-tasks-panel.js';
import { createHomeDownloadController } from './home-download-panel.js';
import { createOfficeModalManager } from './office-modal.js';
import {
  applyAtlasLoginOpenParam,
  applyDownloadOpenParam,
  applyRoadmapOpenParam,
  applyTasksOpenParam
} from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

let privateTeam = null;

const modalMgr = createOfficeModalManager({
  doc: document,
  win: window,
  onPauseScene: (paused) => {
    if (privateTeam && typeof privateTeam.setSceneAnimationPaused === 'function') {
      privateTeam.setSceneAnimationPaused(paused);
    }
  },
  openByToken: (token) => {
    if (token === 'roadmap') roadmap.open();
    else if (token === 'tasks') {
      if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
    } else if (token === 'download') download.open();
    else if (token === 'atlas-login') {
      applyAtlasLoginOpenParam(window, {
        isAuthed: () => !!window.PUNCHCLOCK_AUTHENTICATED,
        openAtlas: () => window.location.assign('/atlas/tcm-3d/'),
        openLoginForAtlas: () => privateTeam.openLoginForAtlas()
      });
    }
  }
});

function openExclusive(id, opener) {
  modalMgr.openExclusive(id, opener);
}

const roadmap = createRoadmapController({
  officeModal: modalMgr,
  hooks: {
    onChange: (state) => {
      if (state.open && privateTeam && typeof privateTeam.setRoadmapOpen === 'function') {
        privateTeam.setRoadmapOpen(true);
      } else if (!state.open && privateTeam) {
        privateTeam.setRoadmapOpen(false);
      }
    }
  }
});

const download = createHomeDownloadController({
  officeModal: modalMgr,
  hooks: {
    onChange: (state) => {
      if (privateTeam && typeof privateTeam.setDownloadOpen === 'function') {
        privateTeam.setDownloadOpen(state.open);
      }
    }
  }
});

let tasks = createHomeTasksController({
  officeModal: modalMgr,
  hooks: {
    onChange: (state) => {
      if (state.open && privateTeam && typeof privateTeam.setTasksOpen === 'function') {
        privateTeam.setTasksOpen(true);
      } else if (!state.open && privateTeam) {
        privateTeam.setTasksOpen(false);
      }
    }
  }
});

modalMgr.register('roadmap', roadmap);
modalMgr.register('tasks', tasks);
modalMgr.register('download', download);

function openRoadmapFromHud() {
  openExclusive('roadmap', () => roadmap.open());
}

function openDownloadFromHud(trigger) {
  openExclusive('download', () => download.open({ trigger }));
}

function openTasksFromHud() {
  openExclusive('tasks', () => tasks.open());
}

privateTeam = mountPrivateTeamPage({
  doc: document,
  win: window,
  officeModal: modalMgr,
  hooks: {
    openRoadmap: () => openRoadmapFromHud(),
    openTasks: () => openTasksFromHud(),
    closeTasks: () => tasks.close({ fromManager: true }),
    closeDownload: () => download.close({ fromManager: true, silent: true }),
    openDownload: (trigger) => openDownloadFromHud(trigger)
  }
});

modalMgr.register('login', privateTeam.getLoginModal());

applyRoadmapOpenParam(window, () => openRoadmapFromHud());
applyTasksOpenParam(window, () => {
  if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
});
applyDownloadOpenParam(window, () => openDownloadFromHud());
applyAtlasLoginOpenParam(window, {
  isAuthed: () => !!window.PUNCHCLOCK_AUTHENTICATED,
  openAtlas: () => {
    window.location.assign('/atlas/tcm-3d/');
  },
  openLoginForAtlas: () => privateTeam.openLoginForAtlas()
});
roadmap.refresh();