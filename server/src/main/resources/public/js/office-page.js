// 獨立虛擬辦公室頁。訪客不打開發機 API。

import { createLabOfficeController } from './lab-office.js';

const authed = typeof window !== 'undefined' && window.PUNCHCLOCK_AUTHENTICATED === true;
if (authed) {
  const root = document.getElementById('officePage');
  if (root) {
    createLabOfficeController({
      doc: document,
      win: window,
      existingRoot: root,
      page: true
    }).open();
  }
}
