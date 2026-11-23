import { wireDesktopDownloadActions } from './download-desktop-assets.js';

const actions =
  document.querySelector('[data-download-actions]') || document.querySelector('.download-root');
wireDesktopDownloadActions(actions);