// 首頁「私人秘書」3D 一角。

import { createHomeTasksController } from './home-tasks-panel.js';
import { createHomeDownloadController } from './home-download-panel.js';
import { createHomeJournalController } from './home-journal-panel.js';
import { createOfficeModalManager } from './office-modal.js';
import { applyDownloadOpenParam, applyJournalOpenParam, applyTasksOpenParam } from './dev-machines-format.js';
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
    if (token === 'tasks') {
      if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
    } else if (token === 'download') download.open();
    else if (token === 'journal') openJournalFromHud();
  }
});

function openExclusive(id, opener) {
  modalMgr.openExclusive(id, opener);
}

const journal = createHomeJournalController({
  officeModal: modalMgr,
  hooks: {
    onChange: (state) => {
      if (privateTeam && typeof privateTeam.setJournalOpen === 'function') {
        privateTeam.setJournalOpen(state.open);
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

const tasks = createHomeTasksController({
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

modalMgr.register('tasks', tasks);
modalMgr.register('download', download);
modalMgr.register('journal', journal);

function openDownloadFromHud(trigger) {
  openExclusive('download', () => download.open({ trigger }));
}

function openTasksFromHud() {
  openExclusive('tasks', () => tasks.open());
}

function openJournalFromHud(trigger) {
  openExclusive('journal', () => journal.open({ trigger }));
}

privateTeam = mountPrivateTeamPage({
  doc: document,
  win: window,
  officeModal: modalMgr,
  hooks: {
    openTasks: () => openTasksFromHud(),
    closeTasks: () => tasks.close({ fromManager: true }),
    closeDownload: () => download.close({ fromManager: true, silent: true }),
    openDownload: (trigger) => openDownloadFromHud(trigger),
    openJournal: (trigger) => openJournalFromHud(trigger),
    closeJournal: () => journal.close({ fromManager: true })
  }
});

modalMgr.register('login', privateTeam.getLoginModal());

applyTasksOpenParam(window, () => {
  if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
});
applyDownloadOpenParam(window, () => openDownloadFromHud());
applyJournalOpenParam(window, () => openJournalFromHud());