/** 銅人穴位與今日一穴純邏輯（無 Three.js，供 Node 測試與銅人模組共用）。 */

export const PENDING_COPY = '尚待收錄';
export const DISCLAIMER = '教學參考，非診療建議';
export const FIGURE_TARGET_HEIGHT = 2.12;
export const DATA_BODY_HEIGHT = 1.72;
export const MARKER_SURFACE_EPS = 0.009;
export const BRONZE_HEAD_TOP_Y = 1.76;

/** 與 tcm-bronze-figure.glb 對齊的吸附用橢球／球（資料空間 Y-up）。 */
const BRONZE_SNAP_TOPKNOT = Object.freeze({ cx: 0, cy: 1.698, cz: -0.008, r: 0.058 });
const BRONZE_SNAP_HEAD = Object.freeze({
  cx: 0,
  cy: 1.562,
  cz: 0.052,
  rx: 0.098,
  ry: 0.112,
  rz: 0.128
});
const BRONZE_SNAP_TORSO = Object.freeze({
  cx: 0,
  cy: 1.02,
  cz: 0.015,
  rx: 0.162,
  ry: 0.3,
  rz: 0.112
});
const BRONZE_SNAP_ROBE_FRONT = Object.freeze({
  cx: 0,
  cy: 0.78,
  cz: 0.068,
  rx: 0.168,
  ry: 0.42,
  rz: 0.078
});

/** 資料空間人體分區包圍盒（|x| 取絕對值比對）；與 tcm-bronze-figure.glb 體型對齊。 */
export const BRONZE_PART_BOXES = Object.freeze([
  { minX: 0, maxX: 0.26, minY: 0, maxY: 0.16, minZ: -0.12, maxZ: 0.16 },
  { minX: 0.02, maxX: 0.17, minY: 0.02, maxY: 0.98, minZ: -0.14, maxZ: 0.14 },
  { minX: 0, maxX: 0.22, minY: 0.70, maxY: 1.38, minZ: -0.15, maxZ: 0.14 },
  { minX: 0.04, maxX: 0.38, minY: 0.66, maxY: 1.28, minZ: -0.14, maxZ: 0.10 },
  { minX: 0, maxX: 0.15, minY: 1.34, maxY: 1.76, minZ: -0.15, maxZ: 0.15 }
]);

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

function snapEllipsoid(px, py, pz, e, eps) {
  let dx = px - e.cx;
  let dy = py - e.cy;
  let dz = pz - e.cz;
  if (Math.hypot(dx, dy, dz) < 1e-8) {
    dy = e.ry;
    dx = 0;
    dz = 0;
  }
  const t =
    1 /
    Math.sqrt((dx / e.rx) ** 2 + (dy / e.ry) ** 2 + (dz / e.rz) ** 2);
  const sx = e.cx + dx * t;
  const sy = e.cy + dy * t;
  const sz = e.cz + dz * t;
  const nx = (dx * t) / (e.rx * e.rx);
  const ny = (dy * t) / (e.ry * e.ry);
  const nz = (dz * t) / (e.rz * e.rz);
  const nlen = Math.hypot(nx, ny, nz);
  const inv = nlen > 1e-8 ? 1 / nlen : 0;
  return [sx + nx * inv * eps, sy + ny * inv * eps, sz + nz * inv * eps];
}

function snapSphere(px, py, pz, s, eps) {
  return snapEllipsoid(px, py, pz, { cx: s.cx, cy: s.cy, cz: s.cz, rx: s.r, ry: s.r, rz: s.r }, eps);
}

function markerOffsetByNormal(record, side) {
  const t = pointTargetRecord(record, side != null ? side : 1);
  const pos = t.position;
  let nx = t.normal[0];
  let ny = t.normal[1];
  let nz = t.normal[2];
  const len = Math.hypot(nx, ny, nz);
  if (len < 1e-6) {
    nz = 0.08;
    ny = 0;
    nx = t.side < 0 ? -0.08 : t.side > 0 ? 0.08 : 0;
  }
  const inv = 1 / (len < 1e-6 ? Math.hypot(nx, ny, nz) : len);
  return [
    pos[0] + nx * inv * MARKER_SURFACE_EPS,
    pos[1] + ny * inv * MARKER_SURFACE_EPS,
    pos[2] + nz * inv * MARKER_SURFACE_EPS
  ];
}

export function markerSurfacePosition(record, side) {
  const t = pointTargetRecord(record, side != null ? side : 1);
  const px = t.position[0];
  const py = t.position[1];
  const pz = t.position[2];
  const eps = MARKER_SURFACE_EPS;

  if (py >= 1.648) {
    const tk = BRONZE_SNAP_TOPKNOT;
    const dtk = Math.hypot(px - tk.cx, py - tk.cy, pz - tk.cz);
    if (dtk < tk.r * 1.35 || py >= 1.68) {
      return snapSphere(px, py, pz, tk, eps);
    }
  }
  if (py >= 1.34) {
    return snapEllipsoid(px, py, pz, BRONZE_SNAP_HEAD, eps);
  }
  if (py >= 0.64 && py < 1.34) {
    if (pz > 0.04) {
      return snapEllipsoid(px, py, pz, BRONZE_SNAP_ROBE_FRONT, eps);
    }
    return snapEllipsoid(px, py, pz, BRONZE_SNAP_TORSO, eps);
  }
  return markerOffsetByNormal(record, side);
}

export function acupointNearBronzeBody(position, margin) {
  const m = margin == null ? 0.04 : margin;
  const ax = Math.abs(position[0]);
  const y = position[1];
  const z = position[2];
  for (let i = 0; i < BRONZE_PART_BOXES.length; i++) {
    const b = BRONZE_PART_BOXES[i];
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