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

  return {passed: failures.length === 0, failures};
}

export {SEED_MAX_M, RUNTIME_MAX_M, CUN_LATERAL};