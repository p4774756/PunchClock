import { createGraffitiController } from './graffiti-panel.js';
import { createRoadmapController } from './roadmap-panel.js';
import { applyRoadmapOpenParam } from './dev-machines-format.js';

const graffiti = createGraffitiController({});
const roadmap = createRoadmapController({});

document.querySelectorAll('[data-open-graffiti]').forEach((el) => {
  el.addEventListener('click', () => graffiti.open());
});
document.querySelectorAll('[data-open-roadmap]').forEach((el) => {
  el.addEventListener('click', () => roadmap.open());
});

applyRoadmapOpenParam(window, () => roadmap.open());
roadmap.refresh();