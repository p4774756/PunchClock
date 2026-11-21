#!/usr/bin/env node
/** Writes data/cun-validation.json (run manually when rules change). */
import {writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const project = join(dirname(fileURLToPath(import.meta.url)), '..');

const animeLandmarks = {
  navel: [0, 1.02, -0.100],
  xiphoid: [0, 1.26, -0.100],
  pubis_rim: [0, 0.870, -0.100],
  left_nipple: [0.055, 1.195, -0.120],
  right_nipple: [-0.055, 1.195, -0.120],
  suprasternal_notch: [0, 1.334, -0.026],
  mentolabial_groove: [0, 1.414, -0.068],
  larynx_suprahyoid: [0, 1.452, -0.038],
  anterior_hairline: [0, 1.575, -0.040],
  posterior_hairline: [0, 1.505, 0.095],
  c7_spinous: [0, 1.310, 0.080],
  occipital_protuberance: [0, 1.435, 0.095],
  philtrum_upper_third: [0, 1.431, -0.045],
  left_clavicle_mid: [0.045, 1.470, -0.035],
  ren_ying_height: [0.017, 1.453, -0.050],
  qishe_anchor: [0.038, 1.450, -0.030],
  yintang: [0, 1.505, -0.020],
  baihui: [0, 1.622, 0.040],
  t5_spinous: [0, 1.230, 0.088],
  t9_spinous: [0, 1.120, 0.092],
  l2_spinous: [0, 1.020, 0.100],
  l4_spinous: [0, 0.980, 0.102],
  s2_sacrum: [0, 0.900, 0.105],
};

const catalogLandmarks = {
  navel: [0, 1.0258, 0.1117],
  xiphoid: [0, 1.267, 0.104],
  pubis_rim: [0, 0.876, 0.112],
  left_nipple: [0.0672, 1.2611, 0.104],
  right_nipple: [-0.0672, 1.2611, 0.104],
  suprasternal_notch: [0, 1.444, 0.0246],
  mentolabial_groove: [0, 1.485, 0.085],
  larynx_suprahyoid: [0, 1.515, 0.072],
  anterior_hairline: [0, 1.605, 0.048],
  posterior_hairline: [0, 1.520, -0.115],
  c7_spinous: [0, 1.310, -0.115],
  occipital_protuberance: [0, 1.440, -0.115],
  philtrum_upper_third: [0, 1.478, 0.092],
  left_clavicle_mid: [0.045, 1.4726, 0.0204],
  ren_ying_height: [0.034, 1.500, 0.085],
  qishe_anchor: [0.038, 1.465, 0.030],
  yintang: [0, 1.555, 0.040],
  baihui: [0, 1.717, -0.024],
  t5_spinous: [0, 1.230, -0.118],
  t9_spinous: [0, 1.120, -0.118],
  l2_spinous: [0, 1.021, -0.116],
  l4_spinous: [0, 0.980, -0.116],
  s2_sacrum: [0, 0.900, -0.116],
};

const cunFrom = [
  {axis: 'vertical', from: 'xiphoid', to: 'navel', cun: 8, verticalKind: 'torso'},
  {axis: 'vertical', from: 'navel', to: 'pubis_rim', cun: 5, verticalKind: 'torso'},
  {axis: 'lateral', from: 'midline', to: 'left_nipple', cun: 4},
  {axis: 'vertical', from: 'anterior_hairline', to: 'yintang', cun: 3, verticalKind: 'head'},
];

/** @type {object[]} */
const rules = [
  // 任脈
  {code: 'CV8', midline: true, at: 'navel', summary: '臍中'},
  {code: 'CV9', midline: true, ref: 'navel', verticalCun: 1, summary: '臍中上 1 寸'},
  {code: 'CV10', midline: true, ref: 'navel', verticalCun: 2, summary: '臍中上 2 寸'},
  {code: 'CV11', midline: true, ref: 'navel', verticalCun: 3, summary: '臍中上 3 寸'},
  {code: 'CV12', midline: true, ref: 'navel', verticalCun: 4, summary: '臍中上 4 寸'},
  {code: 'CV13', midline: true, ref: 'navel', verticalCun: 5, summary: '臍中上 5 寸'},
  {code: 'CV14', midline: true, ref: 'navel', verticalCun: 6, summary: '臍中上 6 寸（標準）'},
  {code: 'CV15', midline: true, ref: 'xiphoid', verticalCun: -1, summary: '劍突下 1 寸'},
  {code: 'CV16', midline: true, ref: 'xiphoid', verticalCun: -2, summary: '胸劍聯合下約 2 寸'},
  {code: 'CV17', midline: true, midpointOf: ['left_nipple', 'right_nipple'], summary: '兩乳連線中點'},
  {code: 'CV18', midline: true, ref: 'left_nipple', verticalCun: 1, summary: '膻中上 1 寸'},
  {code: 'CV19', midline: true, ref: 'left_nipple', verticalCun: 2, summary: '膻中上 2 寸'},
  {code: 'CV20', midline: true, ref: 'xiphoid', verticalCun: -3, summary: '胸劍聯合下 3 寸'},
  {code: 'CV21', midline: true, ref: 'xiphoid', verticalCun: -4, summary: '胸劍聯合下 4 寸'},
  {code: 'CV22', midline: true, at: 'suprasternal_notch', summary: '胸骨上窩中央'},
  {code: 'CV23', midline: true, at: 'larynx_suprahyoid', summary: '喉結上舌骨上緣'},
  {code: 'CV24', midline: true, at: 'mentolabial_groove', summary: '頦唇溝正中'},
  {code: 'CV7', midline: true, ref: 'navel', verticalCun: -1, summary: '臍中下 1 寸'},
  {code: 'CV6', midline: true, ref: 'navel', verticalCun: -1.5, summary: '臍中下 1.5 寸'},
  {code: 'CV5', midline: true, ref: 'navel', verticalCun: -2, summary: '臍中下 2 寸'},
  {code: 'CV4', midline: true, ref: 'navel', verticalCun: -3, summary: '臍中下 3 寸'},
  {code: 'CV3', midline: true, ref: 'navel', verticalCun: -4, summary: '臍中下 4 寸'},
  {code: 'CV2', midline: true, at: 'pubis_rim', summary: '恥骨聯合上緣'},
  {code: 'CV1', midline: true, ref: 'pubis_rim', verticalCun: -0.5, summary: '會陰（恥骨下）'},

  // 督脈（後正中）
  {code: 'GV14', midline: true, at: 'c7_spinous', summary: 'C7 棘突下', posterior: true},
  {code: 'GV15', midline: true, ref: 'occipital_protuberance', verticalCun: -0.5, summary: '風府下 0.5 寸', posterior: true},
  {code: 'GV16', midline: true, at: 'occipital_protuberance', summary: '枕外隆凸下凹陷', posterior: true},
  {code: 'GV17', midline: true, ref: 'occipital_protuberance', verticalCun: -1, summary: '風府下 1 寸', posterior: true},
  {code: 'GV20', midline: true, at: 'baihui', summary: '百會', posterior: true},
  {code: 'GV21', midline: true, ref: 'baihui', verticalCun: -0.5, summary: '百會前 0.5 寸', posterior: true},
  {code: 'GV22', midline: true, ref: 'baihui', verticalCun: -1, summary: '百會前 1 寸', posterior: true},
  {code: 'GV23', midline: true, ref: 'anterior_hairline', verticalCun: 1, verticalKind: 'head', summary: '前髮際上 1 寸', posterior: true},
  {code: 'GV24', midline: true, ref: 'anterior_hairline', verticalCun: 0.5, verticalKind: 'head', summary: '前髮際上 0.5 寸', posterior: true},
  {code: 'GV24+', midline: true, at: 'yintang', summary: '印堂（前髮際下 3 寸）', posterior: true},
  {code: 'GV25', midline: true, ref: 'philtrum_upper_third', verticalCun: -0.5, summary: '人中上', posterior: true},
  {code: 'GV26', midline: true, at: 'philtrum_upper_third', summary: '人中溝上 1/3', posterior: true},
  {code: 'GV27', midline: true, ref: 'philtrum_upper_third', verticalCun: -0.8, summary: '上唇下', posterior: true},
  {code: 'GV28', midline: true, ref: 'philtrum_upper_third', verticalCun: -1.2, summary: '齦交區', posterior: true},
  {code: 'GV4', midline: true, at: 'l2_spinous', summary: 'L2 棘突下（與臍相平）', posterior: true},
  {code: 'GV3', midline: true, at: 'l4_spinous', summary: 'L4 棘突下', posterior: true},
  {code: 'GV2', midline: true, at: 's2_sacrum', summary: '骶管裂孔／骶角間', posterior: true},
  {code: 'GV1', midline: true, ref: 's2_sacrum', verticalCun: -0.8, summary: '尾骨端', posterior: true},
  {code: 'GV5', midline: true, ref: 'l2_spinous', verticalCun: 0.8, summary: 'L1 上', posterior: true},
  {code: 'GV6', midline: true, ref: 'l2_spinous', verticalCun: 1.6, summary: 'T12 上', posterior: true},
  {code: 'GV7', midline: true, at: 't9_spinous', summary: 'T10 棘突下', posterior: true},
  {code: 'GV8', midline: true, at: 't9_spinous', summary: 'T9 棘突下', posterior: true},
  {code: 'GV9', midline: true, ref: 'c7_spinous', verticalCun: -2.5, summary: 'T7 下', posterior: true},
  {code: 'GV10', midline: true, ref: 'c7_spinous', verticalCun: -3.2, summary: 'T6 下', posterior: true},
  {code: 'GV11', midline: true, at: 't5_spinous', summary: 'T5 棘突下', posterior: true},
  {code: 'GV12', midline: true, ref: 'c7_spinous', verticalCun: -4.8, summary: 'T3 下', posterior: true},
  {code: 'GV13', midline: true, ref: 'c7_spinous', verticalCun: -1.2, summary: 'T1 下', posterior: true},
  {code: 'GV18', midline: true, ref: 'occipital_protuberance', verticalCun: -1.5, summary: '強間', posterior: true},
  {code: 'GV19', midline: true, ref: 'occipital_protuberance', verticalCun: -2, summary: '後頂', posterior: true},

  // 胃經頭頸（第一期）
  {code: 'ST9', at: 'ren_ying_height', summary: '喉結上緣旁開 1.5 寸（頸前緣）'},
  {code: 'ST10', along: {to: 'qishe_anchor', fraction: 0.5}, ref: 'ren_ying_height', summary: '人迎–氣舍中點'},
  {code: 'ST11', at: 'qishe_anchor', summary: '胸鎖關節上緣凹陷'},
  {code: 'ST12', ref: 'suprasternal_notch', lateralCun: 4, verticalCun: 0.8, summary: '鎖骨上窩、乳中線'},
  {code: 'ST1', ref: 'yintang', verticalCun: -2, lateralCun: 1.2, summary: '瞳孔直下眶下緣'},
  {code: 'ST2', ref: 'yintang', verticalCun: -2.5, lateralCun: 1.2, summary: '眶下孔凹陷'},
  {code: 'ST3', ref: 'yintang', verticalCun: -3, lateralCun: 1.5, summary: '鼻孔旁 0.8 寸'},
  {code: 'ST4', ref: 'mentolabial_groove', lateralCun: 0.4, summary: '口角外 0.4 寸'},
  {code: 'ST6', ref: 'mentolabial_groove', verticalCun: 0.8, lateralCun: 1.5, summary: '咬肌隆起前'},

  // 胃經胸腹 ST13–30
  {code: 'ST13', ref: 'left_clavicle_mid', lateralCun: 0, verticalCun: -0.5, summary: '鎖骨下、氣戶'},
  {code: 'ST14', ref: 'left_nipple', verticalCun: 1, lateralCun: 0, summary: '乳上肋骨間'},
  {code: 'ST15', ref: 'left_nipple', verticalCun: 0.5, lateralCun: -0.5, summary: '屋翳'},
  {code: 'ST16', ref: 'left_nipple', verticalCun: 0, lateralCun: -0.5, summary: '膺窗'},
  {code: 'ST17', at: 'left_nipple', summary: '乳頭中央'},
  {code: 'ST18', ref: 'left_nipple', verticalCun: -1, lateralCun: 0, summary: '乳根 5 肋間'},
  {code: 'ST19', ref: 'navel', verticalCun: 5, lateralCun: 2, summary: '不容'},
  {code: 'ST20', ref: 'navel', verticalCun: 4.5, lateralCun: 2, summary: '承滿'},
  {code: 'ST21', ref: 'navel', verticalCun: 4, lateralCun: 2, summary: '梁門'},
  {code: 'ST22', ref: 'navel', verticalCun: 3.5, lateralCun: 2, summary: '關門'},
  {code: 'ST23', ref: 'navel', verticalCun: 3, lateralCun: 2, summary: '太乙'},
  {code: 'ST24', ref: 'navel', verticalCun: 2.5, lateralCun: 2, summary: '滑肉門'},
  {code: 'ST25', ref: 'navel', lateralCun: 2, summary: '臍旁 2 寸'},
  {code: 'ST26', ref: 'navel', verticalCun: -1, lateralCun: 2, summary: '外陵'},
  {code: 'ST27', ref: 'navel', verticalCun: -1.5, lateralCun: 2, summary: '大巨'},
  {code: 'ST28', ref: 'navel', verticalCun: -2, lateralCun: 2, summary: '水道'},
  {code: 'ST29', ref: 'navel', verticalCun: -2.5, lateralCun: 2, summary: '歸來'},
  {code: 'ST30', ref: 'pubis_rim', lateralCun: 2, summary: '氣衝'},
];

const config = {
  thresholdCun: 0.3,
  worsenSlackCun: 0.08,
  boneSpans: {
    hairline_front_to_back: 12,
    hairline_front_to_yintang: 3,
    suprasternal_to_xiphoid: 9,
    xiphoid_to_navel: 8,
    navel_to_pubis: 5,
    nipple_to_nipple: 8,
    elbow_crease_to_wrist: 12,
    knee_to_lateral_malleolus: 16,
  },
  spaces: {
    anime: {
      axes: {vertical: 'y', lateral: 'x', anterior: 'z'},
      landmarks: animeLandmarks,
      cunFrom,
    },
    catalog: {
      axes: {vertical: 'y', lateral: 'x', anterior: 'z'},
      landmarks: catalogLandmarks,
      cunFrom,
    },
  },
  rules,
  knownExceedances: [],
};

writeFileSync(join(project, 'data/cun-validation.json'), JSON.stringify(config, null, 2) + '\n', 'utf8');
console.log(`wrote ${rules.length} rules`);