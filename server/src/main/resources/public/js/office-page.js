// 首頁與 /office 虛擬辦公室。訪客只打 /api/office/public。

import { createLabOfficeController } from './lab-office.js';
import { createGuestbookController } from './guestbook-panel.js';
import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam } from './dev-machines-format.js';

const roadmap = createRoadmapController({});
applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();

createGuestbookController({});

const root = document.getElementById('officePage');
if (root) {
  createLabOfficeController({
    doc: document,
    win: window,
    existingRoot: root,
    page: true,
    allowGuest: true
  }).open();
}