import { islandById } from './island-layout.js';
import { careerInteractZones, resolveCareerPickHit } from './island-career-corridor-logic.js';

export { resolveCareerPickHit };

const PREFETCH_BUFFER = 1.4;
const IMPORT_RETRY_MS = 30_000;

function distToComputer(getPlayer) {
  const p = getPlayer && getPlayer();
  const island = islandById('computer');
  if (!p || !island) return Infinity;
  return Math.hypot(p.x - island.x, p.z - island.z);
}

export function createLazyCareerCorridorController(ctx) {
  let inner = null;
  let loading = false;
  let importFailedAt = 0;
  const getNow = () => (typeof Date.now === 'function' ? Date.now() : 0);

  async function ensureInner() {
    if (inner || loading) return;
    if (distToComputer(ctx.getPlayer) >= islandById('computer').visualR + PREFETCH_BUFFER) return;
    if (importFailedAt && getNow() - importFailedAt < IMPORT_RETRY_MS) return;
    loading = true;
    try {
      const mod = await import('./island-career-corridor.js');
      inner = mod.createCareerCorridorController(ctx);
      importFailedAt = 0;
    } catch (err) {
      console.warn('[Island] 資工人生紀念館模組載入失敗', err);
      importFailedAt = getNow();
    } finally {
      loading = false;
    }
  }

  return {
    tick(dt) {
      void ensureInner();
      if (inner) inner.tick(dt);
    },
    handleZoneClick(zone) {
      if (inner) return inner.handleZoneClick(zone);
      return false;
    },
    destroy() {
      if (inner) inner.destroy();
      inner = null;
    },
    isCardOpen() {
      return inner ? inner.isCardOpen() : false;
    },
    getInteractZones() {
      if (inner) return inner.getInteractZones();
      return careerInteractZones();
    },
    showCard(id) {
      if (inner) inner.showCard(id);
    }
  };
}