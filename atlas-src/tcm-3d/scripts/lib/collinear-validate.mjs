/**
 * Collinear / horizontal alignment checks for baked anime seeds (and optional runtime).
 * Thresholds per GB/T 12346 collinearity issue: seed ≤1.5 mm; runtime ≤2.5 mm.
 */

const SEED_MAX_M = 0.0015;
const RUNTIME_MAX_M = 0.0025;
const CUN_LATERAL = 0.055 / 4;
const CUN_TOLERANCE = 0.1;

/** @type {{name:string, axis:'x'|'y'|'z', codes:string[], anchor?:string, maxM?:number}[]} */
export const COLLINEAR_GROUPS = [
  {name: '前正中線（軀幹）', axis: 'x', codes: ['CV23', 'CV22', 'CV17', 'CV14', 'CV8'], anchor: 'CV8'},
  {name: '顏面正中線', axis: 'x', codes: ['GV23', 'GV26', 'GV24+', 'CV24']},
  {name: '後正中線', axis: 'x', codes: ['GV16', 'GV15', 'GV14', 'GV4']},
  {name: '乳中線（旁開四寸）', axis: 'x', codes: ['ST12', 'ST17', 'SP15'], anchor: 'ST17'},
  {name: '旁開二寸線', axis: 'x', codes: ['KI23', 'ST25'], anchor: 'KI23'},
  {name: '第4肋間列', axis: 'y', codes: ['CV17', 'KI23', 'ST17', 'PC1', 'SP18'], anchor: 'ST17'},
  {name: '臍平列', axis: 'y', codes: ['CV8', 'KI16', 'ST25', 'SP15', 'GB26'], anchor: 'CV8'},
  {name: '第2腰椎列', axis: 'y', codes: ['GV4', 'BL23', 'BL52'], anchor: 'GV4'},
  {name: '背部第4胸椎列', axis: 'y', codes: ['SI11', 'BL43', 'BL14'], anchor: 'BL14'},
  {name: '背部第一側線（旁開1.5寸）', axis: 'x', codes: ['BL11', 'BL12', 'BL13', 'BL14', 'BL15', 'BL16', 'BL17', 'BL18', 'BL19', 'BL20', 'BL21', 'BL22', 'BL23'], anchor: 'BL14'},
  {name: '背部旁開3寸', axis: 'x', codes: ['BL43', 'BL52'], anchor: 'BL43'},
  {name: '瞳孔直線', axis: 'x', codes: ['GB14', 'EX-HN4', 'ST1', 'ST2', 'ST3', 'ST4'], anchor: 'ST1'},
  {name: '睛明／攢竹', axis: 'x', codes: ['BL1', 'BL2'], anchor: 'BL1'},
  {name: '風府平風池', axis: 'y', codes: ['GV16', 'GB20'], anchor: 'GV16'},
  {name: '啞門平天柱', axis: 'y', codes: ['GV15', 'BL10'], anchor: 'GV15'},
  {name: '外眼角直線（x）', axis: 'x', codes: ['TE23', 'SI18'], anchor: 'SI18'},
  {name: '耳前線（側面 z）', axis: 'z', codes: ['TE21', 'SI19', 'GB2']},
  {name: '肘橫紋列', axis: 'x', codes: ['LU5', 'PC3', 'HT3', 'LI11', 'SI8'], anchor: 'LU5'},
  {name: '腕橫紋列', axis: 'x', codes: ['LU9', 'PC7', 'HT7', 'LI5', 'TE4', 'SI5'], anchor: 'PC7'},
  {name: '薦骨腰俞同高列', axis: 'y', codes: ['GV2', 'BL34', 'BL30', 'BL54'], anchor: 'GV2'},
];

/** Chest / abdomen lateral cun from midline; anchor ST17 = 4 cun at x. */
export const LATERAL_CUN_CHECKS = [
  {name: '神封（旁開二寸）', code: 'KI23', cun: 2, anchor: 'ST17', anchorCun: 4},
  {name: '天池（五寸）', code: 'PC1', cun: 5, anchor: 'ST17', anchorCun: 4},
  {name: '天谿（六寸）', code: 'SP18', cun: 6, anchor: 'ST17', anchorCun: 4},
  {name: '肓俞（臍旁半寸）', code: 'KI16', cun: 0.5, anchor: null, anchorCun: 0},
  {name: '天樞（臍旁二寸）', code: 'ST25', cun: 2, anchor: null, anchorCun: 0},
  {name: '大橫（臍旁四寸）', code: 'SP15', cun: 4, anchor: 'ST17', anchorCun: 4},
];

/** GB1 = outer canthus + 0.5 cun; compare TE23/SI18 x as outer line. */
export const OUTER_CANTHUS_PLUS = {pupilLine: ['TE23', 'SI18'], gb1: 'GB1', offsetCun: 0.5};

function axisIndex(axis) {
  return axis === 'x' ? 0 : axis === 'y' ? 1 : 2;
}

function pointMap(points) {
  return new Map(points.map(p => [p.code, p]));
}

function coordForAxis(p, axis) {
  const idx = axisIndex(axis);
  if (axis === 'x') return p.midline ? 0 : Math.abs(p.position[idx]);
  return p.position[idx];
}

function worstInGroup(codes, byCode, axis) {
  const entries = codes.map(c => {
    const p = byCode.get(c);
    if (!p) throw new Error(`缺少穴 ${c}`);
    return {code: c, v: coordForAxis(p, axis)};
  });
  const min = Math.min(...entries.map(e => e.v));
  const max = Math.max(...entries.map(e => e.v));
  const spread = max - min;
  const worst = entries.find(e => e.v === max || e.v === min) ?? entries[0];
  return {spread, worst: worst.code};
}

/**
 * @param {{points:{code:string,midline?:boolean,position:number[]}[]}} animeJson
 * @param {{maxM?:number}} [opts]
 */
export function validateCollinearSeeds(animeJson, opts = {}) {
  const maxM = opts.maxM ?? SEED_MAX_M;
  const byCode = pointMap(animeJson.points || []);
  const failures = [];

  for (const g of COLLINEAR_GROUPS) {
    const groupMax = g.maxM ?? maxM;
    const {spread, worst} = worstInGroup(g.codes, byCode, g.axis);
    if (spread > groupMax + 1e-9) {
      failures.push(`${g.name}：${g.axis.toUpperCase()} 落差 ${(spread * 1000).toFixed(2)} mm（>${(groupMax * 1000).toFixed(1)}），偏最多 ${worst}`);
    }
  }

  for (const p of animeJson.points || []) {
    if (p.midline && Math.abs(p.position[0]) > 1e-6) {
      failures.push(`${p.code} 中線穴 x=${p.position[0]} 應為 0`);
    }
  }

  const st17 = byCode.get('ST17');
  if (st17) {
    const cunPerM = st17.position[0] / 4;
    for (const chk of LATERAL_CUN_CHECKS) {
      const p = byCode.get(chk.code);
      if (!p) continue;
      const actualCun = p.position[0] / cunPerM;
      if (Math.abs(actualCun - chk.cun) > CUN_TOLERANCE + 1e-6) {
        failures.push(`${chk.name} ${chk.code} 橫寸 ${actualCun.toFixed(2)}，應 ${chk.cun}±${CUN_TOLERANCE}`);
      }
    }
    const te = byCode.get('TE23');
    const si = byCode.get('SI18');
    const gb1 = byCode.get('GB1');
    if (te && si && gb1) {
      const outerX = (te.position[0] + si.position[0]) / 2;
      const expectedGb1 = outerX + CUN_LATERAL * OUTER_CANTHUS_PLUS.offsetCun;
      const errCun = Math.abs(gb1.position[0] - expectedGb1) / CUN_LATERAL;
      if (errCun > CUN_TOLERANCE) {
        failures.push(`瞳子髎 GB1 應為外眼角線 +0.5 寸（誤差 ${errCun.toFixed(2)} 寸）`);
      }
    }
  }

  const hipSacral = ['GV2', 'BL34', 'BL30', 'BL54'];
  const hipPresent = hipSacral.filter(c => byCode.has(c));
  const minSepM = 0.008;
  for (let i = 0; i < hipPresent.length; i++) {
    for (let j = i + 1; j < hipPresent.length; j++) {
      const a = byCode.get(hipPresent[i]);
      const b = byCode.get(hipPresent[j]);
      const dx = a.position[0] - b.position[0];
      const dy = a.position[1] - b.position[1];
      const dz = a.position[2] - b.position[2];
      const dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
      if (dist < minSepM - 1e-9) {
        failures.push(`${hipPresent[i]} 與 ${hipPresent[j]} 距離 ${(dist * 1000).toFixed(2)} mm（臀薦區須 ≥ ${minSepM * 1000} mm）`);
      }
    }
  }

  const bl34 = byCode.get('BL34');
  const bl30 = byCode.get('BL30');
  if (bl34 && bl30 && bl34.position[0] >= bl30.position[0] - 0.004) {
    failures.push(`下髎 BL34 應比白環俞 BL30 更靠正中（x ${bl34.position[0]} vs ${bl30.position[0]}）`);
  }

  const midlineMaxM = 0.002;
  for (const code of ['GV2']) {
    const p = byCode.get(code);
    if (p?.midline && Math.abs(p.position[0]) > midlineMaxM + 1e-9) {
      failures.push(`${code} 正中線 |x|=${Math.abs(p.position[0]) * 1000} mm（須 < ${midlineMaxM * 1000} mm）`);
    }
  }

  const backMidlineMaxNx = 0.2;
  for (const code of ['GV2', 'GV4', 'GV14']) {
    const p = byCode.get(code);
    if (!p) continue;
    const nx = Math.abs(p.normal?.[0] ?? 0);
    if (nx > backMidlineMaxNx + 1e-9) {
      failures.push(`${code} 後背正中法線 |normal.x|=${nx.toFixed(3)}（須 < ${backMidlineMaxNx}）`);
    }
  }

  const armFail = validateArmJointAxes(animeJson);
  failures.push(...armFail.failures);

  return {passed: failures.length === 0, failures};
}

/** 肘腕橫紋：近遠端（X）共線、橈尺（Z）須明顯分開；陽穀不可落在掌指關節。 */
export function validateArmJointAxes(animeJson) {
  const byCode = pointMap(animeJson.points || []);
  const failures = [];
  const spread = (codes, axis) => {
    const idx = axisIndex(axis);
    const vals = codes.map(c => {
      const p = byCode.get(c);
      if (!p) throw new Error(`缺少穴 ${c}`);
      return axis === 'x' ? Math.abs(p.position[idx]) : p.position[idx];
    });
    return Math.max(...vals) - Math.min(...vals);
  };
  const elbowPalm = ['LU5', 'PC3', 'HT3'];
  const wristLine = ['LU9', 'PC7', 'HT7', 'LI5', 'TE4', 'SI5'];
  const minRadialSepM = 0.007;
  const minRadialByGroup = { '掌側腕橫紋': 0.007, '背側腕橫紋': 0.007, '肘掌側橫紋': 0.005 };
  const maxProximalDriftM = 0.012;
  const si5MaxX = 0.565;
  const radialSpan = (a, b) => Math.hypot(a.position[1] - b.position[1], a.position[2] - b.position[2]);

  if (elbowPalm.every(c => byCode.has(c))) {
    const xSpr = spread(elbowPalm, 'x');
    const yzSpr = Math.max(
      radialSpan(byCode.get('LU5'), byCode.get('PC3')),
      radialSpan(byCode.get('LU5'), byCode.get('HT3')),
      radialSpan(byCode.get('PC3'), byCode.get('HT3')),
    );
    if (xSpr > maxProximalDriftM + 1e-9) {
      failures.push(`肘掌側三穴 X 落差 ${(xSpr * 1000).toFixed(2)} mm（應 ≤ ${maxProximalDriftM * 1000} mm）`);
    }
    if (yzSpr < minRadialSepM - 1e-9) {
      failures.push(`肘掌側三穴橈尺落差 ${(yzSpr * 1000).toFixed(2)} mm（須 ≥ ${minRadialSepM * 1000} mm）`);
    }
    const lu5 = byCode.get('LU5');
    const ht3 = byCode.get('HT3');
    if (lu5 && ht3 && ht3.position[2] <= lu5.position[2] + 1e-6) {
      failures.push('尺澤 LU5 與少海 HT3 未沿 +Z 尺側分開');
    }
    if (xSpr >= yzSpr - 0.002) {
      failures.push(`肘掌側三穴 X 落差應小於橈尺（x ${(xSpr * 1000).toFixed(2)} mm，橈尺 ${(yzSpr * 1000).toFixed(2)} mm）`);
    }
  }

  const minGroupRadial = (codes) => {
    let minD = Infinity;
    for (let i = 0; i < codes.length; i++) {
      for (let j = i + 1; j < codes.length; j++) {
        minD = Math.min(minD, radialSpan(byCode.get(codes[i]), byCode.get(codes[j])));
      }
    }
    return minD;
  };
  const checkRadialGroups = [
    ['掌側腕橫紋', ['LU9', 'PC7', 'HT7']],
    ['背側腕橫紋', ['LI5', 'TE4', 'SI5']],
    ['肘掌側橫紋', ['LU5', 'PC3', 'HT3']],
  ];
  for (const [label, codes] of checkRadialGroups) {
    if (!codes.every(c => byCode.has(c))) continue;
    const minYz = minGroupRadial(codes);
    const need = minRadialByGroup[label] ?? minRadialSepM;
    if (minYz < need - 1e-9) {
      failures.push(
        `${label} 任兩穴橈尺距離最小 ${(minYz * 1000).toFixed(2)} mm（須 ≥ ${need * 1000} mm）`,
      );
    }
  }

  if (wristLine.every(c => byCode.has(c))) {
    const xSpr = spread(wristLine, 'x');
    const zSpr = spread(wristLine, 'z');
    if (xSpr > maxProximalDriftM + 1e-9) {
      failures.push(`腕六穴 X 落差 ${(xSpr * 1000).toFixed(2)} mm（應 ≤ ${maxProximalDriftM * 1000} mm）`);
    }
    if (zSpr < minRadialSepM - 1e-9) {
      failures.push(`腕六穴 Z 落差 ${(zSpr * 1000).toFixed(2)} mm（橈尺須 ≥ ${minRadialSepM * 1000} mm）`);
    }
    if (xSpr >= zSpr - 0.002) {
      failures.push(`腕六穴 X 落差應明顯小於 Z（x ${(xSpr * 1000).toFixed(2)} mm，z ${(zSpr * 1000).toFixed(2)} mm）`);
    }
  }

  const si5 = byCode.get('SI5');
  if (si5 && si5.position[0] > si5MaxX + 1e-9) {
    failures.push(`陽穀 SI5 x=${si5.position[0].toFixed(5)} 過遠端（掌指關節約 0.60，應留在腕橫紋）`);
  }

  const si10 = byCode.get('SI10');
  const si11 = byCode.get('SI11');
  const si10MaxX = 0.12;
  if (si10 && si10.position[0] > si10MaxX + 1e-9) {
    failures.push(`臑俞 SI10 x=${si10.position[0].toFixed(5)} 偏上臂上臂遠端（應 ≤ ${si10MaxX}，靠近肩胛／天宗）`);
  }
  if (si10 && si11 && si10.position[0] > si11.position[0] + 0.025 + 1e-9) {
    failures.push(
      `臑俞 SI10 x=${si10.position[0].toFixed(5)} 較天宗 SI11 x=${si11.position[0].toFixed(5)} 外偏過多`,
    );
  }

  return {passed: failures.length === 0, failures};
}

export {SEED_MAX_M, RUNTIME_MAX_M, CUN_LATERAL};