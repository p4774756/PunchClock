/** 銅人穴位與今日一穴純邏輯（無 Three.js，供 Node 測試與銅人模組共用）。 */

export const PENDING_COPY = '尚待收錄';
export const DISCLAIMER = '教學參考，非診療建議';
export const FIGURE_TARGET_HEIGHT = 2.365;
export const DATA_BODY_HEIGHT = 1.912;
export const BODY_FOOT_Y = 0.192;
export const MARKER_SURFACE_EPS = 0.009;
export const BRONZE_HEAD_TOP_Y = 1.96;
export const BRONZE_SURFACE_TOLERANCE = 0.02;

/** 體表吸附（由 build_tcm_bronze_figure.py 產出 tcm-bronze-surface.json）。 */
let bronzeSurfaceInstances = null;
let bronzePartBoxes = null;

/** 資料空間人體分區包圍盒（|x| 取絕對值）；未載入 surface 時的保守預設。 */
const BRONZE_PART_BOXES_DEFAULT = Object.freeze([
  { minX: 0, maxX: 0.33, minY: 0, maxY: 0.18, minZ: -0.09, maxZ: 0.2 },
  { minX: 0.02, maxX: 0.31, minY: 0.14, maxY: 0.74, minZ: -0.09, maxZ: 0.16 },
  { minX: 0, maxX: 0.33, minY: 0.68, maxY: 1.38, minZ: -0.1, maxZ: 0.16 },
  { minX: 0, maxX: 0.33, minY: 0.72, maxY: 1.32, minZ: -0.1, maxZ: 0.16 },
  { minX: 0, maxX: 0.22, minY: 1.32, maxY: 1.76, minZ: -0.08, maxZ: 0.17 }
]);

export const BRONZE_PART_BOXES = BRONZE_PART_BOXES_DEFAULT;
export const BRONZE_BODY_SRGB = 0xcda862;
export const BRONZE_HAIR_SRGB = 0x54381c;

/** 遊戲內頭髮材質須保留較深古銅色，不可與身體亮金相同。 */
export function bronzeHairDistinctFromBody(hairHex, bodyHex = BRONZE_BODY_SRGB) {
  return hairHex !== bodyHex && hairHex < bodyHex;
}

export function configureBronzeSurface(surfaceDoc) {
  if (!surfaceDoc || typeof surfaceDoc !== 'object') return;
  if (surfaceDoc.instances) bronzeSurfaceInstances = surfaceDoc.instances;
  if (surfaceDoc.partBoxes && surfaceDoc.partBoxes.length) {
    bronzePartBoxes = surfaceDoc.partBoxes;
  }
}

export function bronzePartBoxesForHitTest() {
  return bronzePartBoxes || BRONZE_PART_BOXES_DEFAULT;
}

export function bronzeSurfaceInstanceKey(record, side) {
  const t = pointTargetRecord(record, side != null ? side : 1);
  const s = t.side;
  if (record.midline) return record.code + ':m';
  return record.code + ':' + s;
}

export function bronzeSurfaceBodyPosition(record, side) {
  if (!bronzeSurfaceInstances) return null;
  const key = bronzeSurfaceInstanceKey(record, side);
  const row = bronzeSurfaceInstances[key];
  if (!row || !row.position) return null;
  return row.position;
}

export const ENTRANCE_BRONZE_MATERIALS = Object.freeze(['bronze', 'bronze_dark']);
export const ENTRANCE_FIG_LOCAL = Object.freeze({ x: -0.58, y: 0, z: 1.85 });

export function meshMaterialNames(mesh) {
  if (!mesh || !mesh.material) return [];
  const mat = mesh.material;
  if (Array.isArray(mat)) {
    const names = [];
    for (let i = 0; i < mat.length; i++) {
      if (mat[i] && mat[i].name) names.push(mat[i].name);
    }
    return names;
  }
  return mat.name ? [mat.name] : [];
}

export function meshUsesEntranceBronzeMaterial(mesh) {
  if (!mesh || mesh.isMesh !== true) return false;
  const saved = mesh.userData && mesh.userData.glbMaterialName;
  if (saved && ENTRANCE_BRONZE_MATERIALS.indexOf(saved) >= 0) return true;
  const names = meshMaterialNames(mesh);
  for (let i = 0; i < names.length; i++) {
    if (ENTRANCE_BRONZE_MATERIALS.indexOf(names[i]) >= 0) return true;
  }
  return false;
}

/** tcm-site 館門舊像本地座標保險（與 GLB props 青銅件一致）。 */
export function entranceBronzeLocalGuard(local) {
  if (!local) return true;
  const x = local.x != null ? local.x : local[0];
  const z = local.z != null ? local.z : local[2];
  return z > 1.5 && x < -0.3;
}

/**
 * 隱藏館門舊方塊銅像（依材質名；可選本地座標過濾）。
 * @returns 隱藏 mesh 數量
 */
export function hideEntranceBronzeMeshes(modelRoot, opts) {
  const o = opts || {};
  const useGuard = !!o.useLocalGuard;
  const getLocal = o.getLocalPosition;
  let hidden = 0;
  if (!modelRoot || typeof modelRoot.traverse !== 'function') return 0;
  modelRoot.traverse((obj) => {
    if (!meshUsesEntranceBronzeMaterial(obj)) return;
    if (useGuard && getLocal) {
      const local = getLocal(obj);
      if (!entranceBronzeLocalGuard(local)) return;
    }
    obj.visible = false;
    hidden += 1;
  });
  return hidden;
}

function offsetAlongNormal(pos, norm, eps) {
  let nx = norm[0];
  let ny = norm[1];
  let nz = norm[2];
  const len = Math.hypot(nx, ny, nz);
  if (len < 1e-6) {
    nz = 1;
    nx = 0;
    ny = 0;
  }
  const inv = 1 / (len < 1e-6 ? 1 : len);
  return [
    pos[0] + nx * inv * eps,
    pos[1] + ny * inv * eps,
    pos[2] + nz * inv * eps
  ];
}

export function markerSurfacePosition(record, side) {
  const key = bronzeSurfaceInstanceKey(record, side);
  if (bronzeSurfaceInstances && bronzeSurfaceInstances[key]) {
    const row = bronzeSurfaceInstances[key];
    return offsetAlongNormal(row.position, row.normal, MARKER_SURFACE_EPS);
  }
  const t = pointTargetRecord(record, side != null ? side : 1);
  return offsetAlongNormal(t.position, t.normal, MARKER_SURFACE_EPS);
}

export function acupointNearBronzeBody(position, margin) {
  const m = margin == null ? 0.04 : margin;
  const ax = Math.abs(position[0]);
  const y = position[1];
  const z = position[2];
  const boxes = bronzePartBoxesForHitTest();
  for (let i = 0; i < boxes.length; i++) {
    const b = boxes[i];
    if (
      ax >= b.minX - m && ax <= b.maxX + m &&
      y >= b.minY - m && y <= b.maxY + m &&
      z >= b.minZ - m && z <= b.maxZ + m
    ) {
      return true;
    }
  }
  return false;
}

export const COMMON_10_COPY = Object.freeze({
  LI4: {
    indications: '頭痛、齒痛、面口病、感冒發熱、上肢痠痛等。',
    method:
      '在第 2 掌骨橈側中點，以拇指按揉或按壓，由輕而重，局部酸脹即可。孕婦慎用。'
  },
  ST36: {
    indications: '脾胃不和、腹痛、腹脹、消化不良、下肢痠痛；保健要穴。',
    method: '犢鼻下 3 寸、脛骨前嵴外約一橫指，以拇指按揉，酸脹為度。'
  },
  SP6: {
    indications: '脾胃不和、月經不調、失眠、下肢痠痛等。',
    method: '內踝尖上 3 寸、脛骨內側後緣，以拇指按揉。孕婦慎用。'
  },
  LR3: {
    indications: '頭痛、眩暈、煩躁、目疾、下肢痠痛等。',
    method: '足背第 1、2 蹠骨結合部前方凹陷，以拇指按揉，酸脹為度。'
  },
  PC6: {
    indications: '胸悶、心悸、噁心、嘔吐、前臂痠痛等。',
    method: '腕掌側橫紋上 2 寸，兩筋之間，以拇指按揉。'
  },
  TE5: {
    indications: '感冒發熱、頭痛、耳疾、上肢痠痛等。',
    method: '腕背橫紋上 2 寸，尺橈骨之間，以拇指按揉。'
  },
  GB20: {
    indications: '頭痛、眩暈、頸項強痛、感冒、目疾等。',
    method: '枕骨下，胸鎖乳突肌與斜方肌之間凹陷，以拇指向鼻尖方向按揉，力道溫和。'
  },
  GB21: {
    indications: '肩頸痠痛、頭痛、頸項不利等。',
    method: '肩部高點、大椎與肩峰連線中點，以指腹按揉，力道勿過猛。孕婦慎用。'
  },
  BL40: {
    indications: '腰痛、下肢痿痹、膝痛等。',
    method: '膕橫紋中點，以拇指適度按揉，避免重壓。'
  },
  KI1: {
    indications: '頭痛、失眠、心煩、咽喉不適等。',
    method: '足底前 1/3 凹陷，捲足時取穴，以拇指按揉。'
  }
});

export function pointTargetRecord(record, side) {
  const pos = record.position;
  const norm = record.normal;
  if (record.midline) {
    return {
      code: record.code,
      side: 1,
      position: [pos[0], pos[1], pos[2]],
      normal: [norm[0], norm[1], norm[2]]
    };
  }
  return {
    code: record.code,
    side: side,
    position: [pos[0] * side, pos[1], pos[2]],
    normal: [norm[0] * side, norm[1], norm[2]]
  };
}

export function expandCurriculumInstances(records) {
  const list = [];
  for (let i = 0; i < records.length; i++) {
    const rec = records[i];
    if (rec.midline) {
      const t = pointTargetRecord(rec, 1);
      list.push({
        ...t,
        instanceKey: rec.code + ':m',
        name: rec.name,
        meridian: rec.meridian,
        location: rec.location || '',
        indications: rec.indications || '',
        method: rec.method || ''
      });
    } else {
      [-1, 1].forEach((side) => {
        const t = pointTargetRecord(rec, side);
        list.push({
          ...t,
          instanceKey: rec.code + ':' + side,
          name: rec.name,
          meridian: rec.meridian,
          location: rec.location || '',
          indications: rec.indications || '',
          method: rec.method || ''
        });
      });
    }
  }
  return list;
}

export function countInstanceSides(records) {
  let midline = 0;
  let bilateral = 0;
  for (let i = 0; i < records.length; i++) {
    if (records[i].midline) midline += 1;
    else bilateral += 1;
  }
  return {
    codes: records.length,
    instances: midline + bilateral * 2,
    midline: midline,
    bilateralPairs: bilateral
  };
}

export function taipeiDateString(nowMs) {
  const t = Number(nowMs);
  if (!Number.isFinite(t) || t <= 0) return '';
  return new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Taipei',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit'
  }).format(new Date(t));
}

export function taipeiDateLabel(nowMs) {
  const iso = taipeiDateString(nowMs);
  if (!iso) return '';
  const parts = iso.split('-');
  if (parts.length !== 3) return iso;
  return parts[0] + '年' + String(Number(parts[1])) + '月' + String(Number(parts[2])) + '日（台北）';
}

function fnv1a(str) {
  let h = 2166136261;
  for (let i = 0; i < str.length; i++) {
    h ^= str.charCodeAt(i);
    h = Math.imul(h, 16777619);
  }
  return h >>> 0;
}

export function pickTodayCode(records, nowMs) {
  const date = taipeiDateString(nowMs);
  if (!date || !records.length) return null;
  const preferred = records.filter((r) => (r.location && r.location.length) || (r.indications && r.indications.length));
  const pool = preferred.length ? preferred : records;
  const idx = fnv1a(date) % pool.length;
  return pool[idx].code;
}

export function fieldCopy(value) {
  if (value != null && String(value).trim()) return String(value).trim();
  return PENDING_COPY;
}

export function buildCardTextModel(record, opts) {
  const o = opts || {};
  const today = !!o.today;
  const title = today ? '今日一穴' : record.name;
  return {
    title: title,
    subtitle: today
      ? record.name + '（' + record.code + '）· ' + record.meridian
      : record.code + ' · ' + record.meridian,
    dateLine: today ? taipeiDateLabel(o.nowMs) : '',
    locationLabel: '標準定位',
    location: fieldCopy(record.location),
    indicationsLabel: '主治',
    indications: fieldCopy(record.indications),
    methodLabel: '按法',
    method: fieldCopy(record.method),
    disclaimer: DISCLAIMER
  };
}

export function appendTextRow(parent, label, value, doc) {
  const row = doc.createElement('p');
  const strong = doc.createElement('strong');
  strong.textContent = label;
  row.appendChild(strong);
  row.appendChild(doc.createTextNode(' ' + value));
  parent.appendChild(row);
  return row;
}

export function resolveTcmPickHit(hit) {
  if (!hit || !hit.object) return '';
  const ud = hit.object.userData;
  if (ud.tcmPointPick && hit.instanceId != null) {
    const keys = ud.tcmPointPick.instanceKeys;
    const key = keys[hit.instanceId];
    if (!key) return '';
    return { type: 'tcm-point', instanceKey: key, instanceId: hit.instanceId };
  }
  if (ud.tcmEntranceHint) return { type: 'tcm-entrance-hint' };
  return ud.zone || '';
}

export function fillAcupointCard(root, model, doc, opts) {
  const compact = !!(opts && opts.compact);
  while (root.firstChild) root.removeChild(root.firstChild);
  const title = doc.createElement('h2');
  title.textContent = model.title;
  root.appendChild(title);
  const sub = doc.createElement('p');
  sub.className = 'tcm-card-sub';
  sub.textContent = model.subtitle;
  root.appendChild(sub);
  if (model.dateLine) {
    const dateEl = doc.createElement('p');
    dateEl.className = 'tcm-card-date';
    dateEl.textContent = model.dateLine;
    root.appendChild(dateEl);
  }
  if (compact) {
    const ind = doc.createElement('p');
    ind.className = 'tcm-card-indications-line';
    ind.textContent = model.indications;
    root.appendChild(ind);
    const details = doc.createElement('details');
    details.className = 'tcm-card-more';
    const summary = doc.createElement('summary');
    summary.textContent = '完整說明';
    details.appendChild(summary);
    appendTextRow(details, model.locationLabel + '：', model.location, doc);
    appendTextRow(details, model.methodLabel + '：', model.method, doc);
    const foot = doc.createElement('p');
    foot.className = 'tcm-card-disclaimer';
    foot.textContent = model.disclaimer;
    details.appendChild(foot);
    root.appendChild(details);
    return;
  }
  appendTextRow(root, model.locationLabel + '：', model.location, doc);
  appendTextRow(root, model.indicationsLabel + '：', model.indications, doc);
  appendTextRow(root, model.methodLabel + '：', model.method, doc);
  const foot = doc.createElement('p');
  foot.className = 'tcm-card-disclaimer';
  foot.textContent = model.disclaimer;
  root.appendChild(foot);
}