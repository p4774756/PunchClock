// 首頁「我的私人團隊」3D 一角。訪客簽名簿仍可由 ?open=guestbook 開啟。

import { createGuestbookController } from './guestbook-panel.js';
import { applyGuestbookOpenParam } from './guestbook-logic.js';
import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam } from './dev-machines-format.js';
import { mountPrivateTeamPage } from './private-team-page.js';

const roadmap = createRoadmapController({});
applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();

const guestbook = createGuestbookController({});
applyGuestbookOpenParam(window, () => guestbook.open());

mountPrivateTeamPage({
  doc: document,
  win: window,
  hooks: {
    openRoadmap: () => roadmap.open()
  }
});