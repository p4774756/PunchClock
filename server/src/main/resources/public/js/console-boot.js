import { createGraffitiController } from './graffiti-panel.js';
import { createRoadmapController } from './roadmap-panel.js';

createGraffitiController({});
const roadmap = createRoadmapController({});
document.querySelectorAll('[data-open-roadmap]').forEach((el) => {
  el.addEventListener('click', () => roadmap.open());
});

roadmap.refresh();