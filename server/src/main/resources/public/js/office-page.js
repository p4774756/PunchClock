// 首頁「私人秘書」3D 一角。

import { createRoadmapController } from './roadmap-panel.js';
import { createHomeTasksController } from './home-tasks-panel.js';
import { createHomeAccountsController } from './home-accounts-panel.js';
import { createHomeChangePasswordController } from './home-change-password-panel.js';
import { adminWriteFetch, setCsrfToken } from './home-admin-write.js';
import { applyRoadmapOpenParam, applyTasksOpenParam } from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

if (typeof window !== 'undefined') {
  window.PunchClockAdminWriteFetch = (url, opts) => adminWriteFetch(window, url, opts);
  window.PunchClockSetCsrfToken = (token) => setCsrfToken(window, token);
}

let privateTeam = null;
let tasks = null;
let accounts = null;
let changePassword = null;

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
      if (state.open && accounts && accounts.isOpen()) {
        accounts.close();
      }
      if (privateTeam && typeof privateTeam.setTasksOpen === 'function') {
        privateTeam.setTasksOpen(state.open);
      }
    }
  }
});

accounts = createHomeAccountsController({ doc: document, win: window });

changePassword = createHomeChangePasswordController({
  doc: document,
  win: window,
  onDone: () => {
    window.PUNCHCLOCK_AUTHENTICATED = true;
    if (document.body) {
      document.body.classList.add('is-authenticated');
    }
    if (privateTeam && typeof privateTeam.onPasswordChanged === 'function') {
      privateTeam.onPasswordChanged();
    }
  }
});

window.__openChangePassword = () => changePassword.openForced();
if (window.PUNCHCLOCK_MUST_CHANGE_PASSWORD === true) {
  changePassword.openForced();
}

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
    closeTasks: () => tasks.close(),
    openAccounts: () => {
      if (roadmap.isOpen()) roadmap.close();
      if (tasks.isOpen()) tasks.close();
      accounts.open();
    }
  }
});

applyRoadmapOpenParam(window, () => roadmap.open());
applyTasksOpenParam(window, () => {
  if (window.PUNCHCLOCK_AUTHENTICATED) openTasksFromHud();
});
roadmap.refresh();