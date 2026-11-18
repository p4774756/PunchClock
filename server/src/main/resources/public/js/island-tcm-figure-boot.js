/** 銅人延遲載入殼：僅拉 logic，靠近中醫島才 import 本體（Three／JSON／104 點）。 */
import { islandById } from './island-layout.js';
import { resolveTcmPickHit } from './island-tcm-figure-logic.js';

export { resolveTcmPickHit };

const PREFETCH_BUFFER = 1.65;

function distToTcm(getPlayer) {
  const p = getPlayer && getPlayer();
  const island = islandById('tcm');
  if (!p || !island) return Infinity;
  return Math.hypot(p.x - island.x, p.z - island.z);
}

export function createLazyTcmFigureController(ctx) {
  let inner = null;
  let loading = false;

  async function ensureInner() {
    if (inner || loading) return;
    if (distToTcm(ctx.getPlayer) >= (islandById('tcm').visualR + PREFETCH_BUFFER)) return;
    loading = true;
    try {
      const mod = await import('./island-tcm-figure.js');
      inner = mod.createTcmFigureController(ctx);
    } finally {
      loading = false;
    }
  }

  return {
    tick() {
      ensureInner();
      if (inner) inner.tick();
    },
    handleZoneClick(zone) {
      if (inner) return inner.handleZoneClick(zone);
      return false;
    },
    destroy() {
      if (inner) inner.destroy();
      inner = null;
    },
    showTodayCard() {
      if (inner) inner.showTodayCard();
    }
  };
}