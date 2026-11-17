// 建築錨點名牌定位；同儕名牌由呼叫端傳入 placePeerLabels。

const labelSizeCache = new WeakMap();
const LABEL_SIZE_TTL_MS = 100;

function labelSize(el, now) {
  const hit = labelSizeCache.get(el);
  if (hit && now - hit.t < LABEL_SIZE_TTL_MS) return hit;
  const entry = { w: el.offsetWidth || 76, h: el.offsetHeight || 28, t: now };
  labelSizeCache.set(el, entry);
  return entry;
}

export function placeBuildingLabels(opts) {
  const inside = opts.inside;
  const interiorBusy = opts.interiorBusy;
  const shouldHideIslandLabels = opts.shouldHideIslandLabels;
  const far = opts.far;
  const camera = opts.camera;
  const anchors = opts.anchors;
  const projected = opts.projected;
  const stage = opts.stage;
  const placePeerLabels = opts.placePeerLabels;
  const document = opts.document;
  const now = opts.now != null ? opts.now : (typeof performance !== 'undefined' ? performance.now() : 0);

  if (shouldHideIslandLabels(inside, interiorBusy)) {
    document.querySelectorAll('[data-anchor]').forEach((el) => {
      el.style.visibility = 'hidden';
    });
    placePeerLabels();
    return;
  }
  const width = stage.clientWidth || 1;
  const height = stage.clientHeight || 1;
  const nodes = [];
  document.querySelectorAll('[data-anchor]').forEach((el) => {
    const minor = el.hasAttribute('data-minor');
    const rank = Number(el.getAttribute('data-rank') || 3);
    if (far && (minor || rank >= 2)) {
      el.style.visibility = 'hidden';
      return;
    }
    const anchor = anchors[el.getAttribute('data-anchor')];
    if (!anchor) return;
    anchor.getWorldPosition(projected);
    const dist = projected.distanceTo(camera.position);
    projected.project(camera);
    if (projected.z > 1 || projected.x < -1.08 || projected.x > 1.08 || projected.y < -1.08 || projected.y > 1.08) {
      el.style.visibility = 'hidden';
      return;
    }
    if (minor && dist > 32) {
      el.style.visibility = 'hidden';
      return;
    }
    nodes.push({
      el: el,
      x: (projected.x * 0.5 + 0.5) * width,
      y: (-projected.y * 0.5 + 0.5) * height,
      minor: minor,
      rank: rank
    });
  });
  nodes.sort((a, b) => a.rank - b.rank);
  const placed = [];
  nodes.forEach((node) => {
    const size = labelSize(node.el, now);
    const w = size.w;
    const h = size.h;
    let y = node.y;
    let hidden = false;
    for (let guard = 0; guard < 5; guard++) {
      const hit = placed.some((item) => {
        return Math.abs(item.x - node.x) < (item.w + w) * 0.48
          && Math.abs(item.y - y) < (item.h + h) * 0.5;
      });
      if (!hit) break;
      if (node.minor) {
        hidden = true;
        break;
      }
      y += h + 8;
    }
    if (hidden) {
      node.el.style.visibility = 'hidden';
      return;
    }
    node.el.style.visibility = 'visible';
    node.el.style.transform = 'translate(' + node.x + 'px, ' + y + 'px) translate(-50%, -120%)';
    placed.push({ x: node.x, y: y, w: w, h: h });
  });
  placePeerLabels();
}
