#!/usr/bin/env node
/** Writes data/cun-validation.json. Run patch-cun-allowlist.mjs afterwards to refresh caps. */
import {readFileSync, writeFileSync} from 'node:fs';
import {dirname, join} from 'node:path';
import {fileURLToPath} from 'node:url';

const project = join(dirname(fileURLToPath(import.meta.url)), '..');

const spans = [
  {id: 'headFront', boneSpan: 'hairline_front_to_yintang', from: 'anterior_hairline', to: 'yintang', measure: 'vertical'},
  {id: 'headArc', boneSpan: 'hairline_front_to_back', from: 'anterior_hairline', to: 'posterior_hairline', measure: 'hairArc'},
  {id: 'chest', boneSpan: 'suprasternal_to_xiphoid', from: 'suprasternal_notch', to: 'xiphoid', measure: 'vertical'},
  {id: 'upperAbdomen', boneSpan: 'xiphoid_to_navel', from: 'xiphoid', to: 'navel', measure: 'vertical'},
  {id: 'lowerAbdomen', boneSpan: 'navel_to_pubis', from: 'navel', to: 'pubis_rim', measure: 'vertical'},
  {id: 'lateral', boneSpan: 'nipple_to_nipple', from: 'right_nipple', to: 'left_nipple', measure: 'lateral'},
  {id: 'forearm', boneSpan: 'elbow_crease_to_wrist', from: 'left_elbow_crease', to: 'left_wrist_crease', measure: 'distance'},
  {id: 'leg', boneSpan: 'knee_to_lateral_malleolus', from: 'left_knee', to: 'left_lateral_malleolus', measure: 'distance'},
];

/** Skin landmarks in anime space (+X anatomical left, face toward −Z). Limb creases are surface points, not bone origins. */
const animeLandmarks = {
  // 神闕、乳中本身就是地標，標明來源，不用該穴座標的四捨五入複本來自驗。
  navel: {sourcePoint: 'CV8', role: 'landmark-source'},
  // 胸劍聯合皮膚，在乳頭與上胸骨之下。
  xiphoid: [0, 1.16, -0.11311],
  pubis_rim: [0, 0.87, -0.1],
  left_nipple: {sourcePoint: 'ST17', role: 'landmark-source'},
  right_nipple: {mirror: 'left_nipple'},
  suprasternal_notch: [0, 1.334, -0.026],
  mentolabial_groove: [0, 1.414, -0.068],
  // 舌骨上緣在頦唇溝之下、喉結之上。
  larynx_suprahyoid: [0, 1.38, -0.02684],
  laryngeal_prominence: [0, 1.3479, -0.0226],
  anterior_hairline: [0, 1.575, -0.04],
  posterior_hairline: [0, 1.505, 0.095],
  vertex: [0, 1.6249, 0.02],
  c7_spinous: [0, 1.31, 0.08],
  occipital_protuberance: [0, 1.435, 0.095],
  philtrum_upper_third: [0, 1.431, -0.045],
  nose_tip: [0, 1.44, -0.10732],
  left_clavicle_mid: [0.0609, 1.3091, -0.038],
  right_clavicle_mid: [-0.0609, 1.3091, -0.038],
  left_elbow_crease: [0.3274, 1.2905, -0.0244],
  right_elbow_crease: [-0.3274, 1.2905, -0.0244],
  left_wrist_crease: [0.5409, 1.29, -0.0408],
  right_wrist_crease: [-0.5409, 1.29, -0.0408],
  left_knee: [0.0558, 0.53, -0.0365],
  right_knee: [-0.0558, 0.53, -0.0365],
  left_medial_malleolus: [0.028, 0.105, 0.012],
  right_medial_malleolus: [-0.028, 0.105, 0.012],
  left_lateral_malleolus: [0.0737, 0.105, 0.0274],
  right_lateral_malleolus: [-0.0737, 0.105, 0.0274],
  qishe_anchor: [0.0221, 1.32, -0.032],
  yintang: [0, 1.505, -0.095],
  pupil_left: [0.0314, 1.4813, -0.08],
  infraorbital_rim: [0.0314, 1.46, -0.08077],
  infraorbital_foramen: [0.0314, 1.44, -0.08699],
  nasal_ala: [0.0421, 1.4261, -0.0772],
  mouth_corner: [0.0358, 1.406, -0.0679],
  t5_spinous: [0, 1.23, 0.088],
  t9_spinous: [0, 1.12, 0.092],
  l2_spinous: [0, 1.02, 0.1],
  l4_spinous: [0, 0.98, 0.102],
  s2_sacrum: [0, 0.9, 0.105],
};

/** Catalog landmarks. 神闕、天突、乳中標明來源穴，不把該穴座標四捨五入後再寫進設定。 */
const catalogLandmarks = {
  navel: {sourcePoint: 'CV8', role: 'landmark-source'},
  xiphoid: [0, 1.224, 0.103],
  pubis_rim: [0, 0.876, 0.112],
  left_nipple: {sourcePoint: 'ST17', role: 'landmark-source'},
  right_nipple: {mirror: 'left_nipple'},
  suprasternal_notch: {sourcePoint: 'CV22', role: 'landmark-source'},
  mentolabial_groove: [0, 1.534, 0.088],
  larynx_suprahyoid: [0, 1.496, 0.028],
  laryngeal_prominence: [0, 1.488, 0.04],
  anterior_hairline: [0, 1.605, 0.048],
  posterior_hairline: [0, 1.52, -0.115],
  vertex: [0, 1.706, -0.018],
  c7_spinous: [0, 1.31, -0.115],
  occipital_protuberance: [0, 1.44, -0.115],
  philtrum_upper_third: [0, 1.478, 0.092],
  nose_tip: [0, 1.562, 0.108],
  left_clavicle_mid: [0.034, 1.45, 0.03],
  right_clavicle_mid: {mirror: 'left_clavicle_mid'},
  left_elbow_crease: {sourcePoint: 'LU5'},
  right_elbow_crease: {mirror: 'left_elbow_crease'},
  left_wrist_crease: {sourcePoint: 'PC7'},
  right_wrist_crease: {mirror: 'left_wrist_crease'},
  left_knee: {sourcePoint: 'ST35'},
  right_knee: {mirror: 'left_knee'},
  left_medial_malleolus: {sourcePoint: 'KI3'},
  right_medial_malleolus: {mirror: 'left_medial_malleolus'},
  left_lateral_malleolus: {sourcePoint: 'BL60'},
  right_lateral_malleolus: {mirror: 'left_lateral_malleolus'},
  qishe_anchor: [0.02, 1.452, 0.028],
  yintang: [0, 1.555, 0.075],
  pupil_left: [0.03, 1.61, 0.068],
  infraorbital_rim: [0.03, 1.588, 0.071],
  infraorbital_foramen: [0.03, 1.574, 0.076],
  nasal_ala: [0.038, 1.55, 0.08],
  mouth_corner: [0.028, 1.538, 0.07],
  t5_spinous: [0, 1.23, -0.118],
  t9_spinous: [0, 1.12, -0.118],
  l2_spinous: [0, 1.021, -0.116],
  l4_spinous: [0, 0.98, -0.116],
  s2_sacrum: [0, 0.9, -0.116],
};

const chest = {span: 'chest', chestSurface: true, midline: true};
// 天突至胸劍聯合 9 寸：璇璣佔 1、華蓋再 1（第 1 肋間），餘 7 寸均分到第 5 肋間。
const ics = 7 / 4;

const spin = (from, to, numer, denom) => ({spinous: {from, to, numer, denom}, midline: true, span: 'upperAbdomen'});

const rules = [
  {code: 'CV8', midline: true, at: 'navel', span: 'upperAbdomen', summary: '臍中'},
  {code: 'CV9', midline: true, ref: 'navel', verticalCun: 1, span: 'upperAbdomen', summary: '臍中上 1 寸'},
  {code: 'CV10', midline: true, ref: 'navel', verticalCun: 2, span: 'upperAbdomen', summary: '臍中上 2 寸'},
  {code: 'CV11', midline: true, ref: 'navel', verticalCun: 3, span: 'upperAbdomen', summary: '臍中上 3 寸'},
  {code: 'CV12', midline: true, ref: 'navel', verticalCun: 4, span: 'upperAbdomen', summary: '臍中上 4 寸'},
  {code: 'CV13', midline: true, ref: 'navel', verticalCun: 5, span: 'upperAbdomen', summary: '臍中上 5 寸'},
  {code: 'CV14', midline: true, ref: 'navel', verticalCun: 6, span: 'upperAbdomen', summary: '臍中上 6 寸'},
  {code: 'CV15', midline: true, ref: 'xiphoid', verticalCun: -1, span: 'upperAbdomen', summary: '劍突下 1 寸'},
  {code: 'CV16', midline: true, at: 'xiphoid', span: 'chest', summary: '胸劍結合、第5肋間'},
  {code: 'CV17', midline: true, midpointOf: ['left_nipple', 'right_nipple'], span: 'chest', summary: '兩乳連線中點'},
  {code: 'CV18', ...chest, ref: 'suprasternal_notch', verticalCun: -(2 + 2 * ics), summary: '第3肋間、前正中線'},
  {code: 'CV19', ...chest, ref: 'suprasternal_notch', verticalCun: -(2 + ics), summary: '第2肋間、前正中線'},
  {code: 'CV20', ...chest, ref: 'suprasternal_notch', verticalCun: -2, summary: '第1肋間、前正中線'},
  {code: 'CV21', ...chest, ref: 'suprasternal_notch', verticalCun: -1, summary: '天突下 1 寸'},
  {code: 'CV22', midline: true, at: 'suprasternal_notch', span: 'chest', summary: '胸骨上窩中央'},
  {code: 'CV23', midline: true, at: 'larynx_suprahyoid', span: 'upperAbdomen', summary: '喉結上、舌骨上緣'},
  {code: 'CV24', midline: true, at: 'mentolabial_groove', span: 'upperAbdomen', summary: '頦唇溝正中'},
  {code: 'CV7', midline: true, ref: 'navel', verticalCun: -1, span: 'lowerAbdomen', summary: '臍中下 1 寸'},
  {code: 'CV6', midline: true, ref: 'navel', verticalCun: -1.5, span: 'lowerAbdomen', summary: '臍中下 1.5 寸'},
  {code: 'CV5', midline: true, ref: 'navel', verticalCun: -2, span: 'lowerAbdomen', summary: '臍中下 2 寸'},
  {code: 'CV4', midline: true, ref: 'navel', verticalCun: -3, span: 'lowerAbdomen', summary: '臍中下 3 寸'},
  {code: 'CV3', midline: true, ref: 'navel', verticalCun: -4, span: 'lowerAbdomen', summary: '臍中下 4 寸'},
  {code: 'CV2', midline: true, at: 'pubis_rim', span: 'lowerAbdomen', summary: '恥骨聯合上緣'},
  {code: 'CV1', midline: true, ref: 'pubis_rim', verticalCun: -0.5, span: 'lowerAbdomen', summary: '會陰'},

  {code: 'GV14', midline: true, at: 'c7_spinous', span: 'upperAbdomen', summary: '第7頸椎棘突下'},
  {code: 'GV13', ...spin('c7_spinous', 't5_spinous', 1, 5), summary: '第1胸椎棘突下'},
  {code: 'GV12', ...spin('c7_spinous', 't5_spinous', 3, 5), summary: '第3胸椎棘突下'},
  {code: 'GV11', midline: true, at: 't5_spinous', span: 'upperAbdomen', summary: '第5胸椎棘突下'},
  {code: 'GV10', ...spin('t5_spinous', 't9_spinous', 1, 4), summary: '第6胸椎棘突下'},
  {code: 'GV9', ...spin('t5_spinous', 't9_spinous', 2, 4), summary: '第7胸椎棘突下'},
  {code: 'GV8', midline: true, at: 't9_spinous', span: 'upperAbdomen', summary: '第9胸椎棘突下'},
  {code: 'GV7', ...spin('t9_spinous', 'l2_spinous', 1, 5), summary: '第10胸椎棘突下'},
  {code: 'GV6', ...spin('t9_spinous', 'l2_spinous', 2, 5), summary: '第11胸椎棘突下'},
  {code: 'GV5', ...spin('t9_spinous', 'l2_spinous', 4, 5), summary: '第1腰椎棘突下'},
  {code: 'GV4', midline: true, at: 'l2_spinous', span: 'upperAbdomen', summary: '第2腰椎棘突下'},
  {code: 'GV3', midline: true, at: 'l4_spinous', span: 'upperAbdomen', summary: '第4腰椎棘突下'},
  {code: 'GV2', midline: true, at: 's2_sacrum', span: 'lowerAbdomen', summary: '骶管裂孔'},
  {code: 'GV1', midline: true, ref: 's2_sacrum', verticalCun: -0.8, span: 'lowerAbdomen', summary: '尾骨端'},

  {code: 'GV24+', midline: true, at: 'yintang', span: 'headFront', summary: '兩眉頭連線中點'},
  {code: 'GV24', midline: true, scalpFrom: 'anterior_hairline', scalpCun: 0.5, span: 'headFront', summary: '前髮際正中直上 0.5 寸'},
  {code: 'GV23', midline: true, scalpFrom: 'anterior_hairline', scalpCun: 1, span: 'headFront', summary: '前髮際正中直上 1 寸'},
  {code: 'GV22', midline: true, scalpFrom: 'anterior_hairline', scalpCun: 2, span: 'headFront', summary: '前髮際正中直上 2 寸'},
  {code: 'GV21', midline: true, scalpFrom: 'anterior_hairline', scalpCun: 3.5, span: 'headFront', summary: '前髮際正中直上 3.5 寸'},
  {code: 'GV20', midline: true, scalpFrom: 'anterior_hairline', scalpCun: 5, span: 'headFront', summary: '前髮際正中直上 5 寸'},
  {code: 'GV19', midline: true, scalpFrom: 'posterior_hairline', scalpCun: 5.5, span: 'headFront', summary: '後髮際正中直上 5.5 寸'},
  {code: 'GV18', midline: true, scalpFrom: 'posterior_hairline', scalpCun: 4, span: 'headFront', summary: '後髮際正中直上 4 寸'},
  {code: 'GV17', midline: true, scalpFrom: 'posterior_hairline', scalpCun: 2.5, span: 'headFront', summary: '後髮際正中直上 2.5 寸'},
  {code: 'GV16', midline: true, scalpFrom: 'posterior_hairline', scalpCun: 1, span: 'headFront', summary: '後髮際正中直上 1 寸'},
  {code: 'GV15', midline: true, scalpFrom: 'posterior_hairline', scalpCun: 0.5, span: 'headFront', summary: '後髮際正中直上 0.5 寸'},
  {code: 'GV26', midline: true, at: 'philtrum_upper_third', span: 'upperAbdomen', summary: '人中溝上 1/3'},
  {code: 'GV25', midline: true, at: 'nose_tip', span: 'headFront', summary: '鼻尖'},
  {code: 'GV27', midline: true, ref: 'philtrum_upper_third', verticalCun: -0.8, span: 'upperAbdomen', summary: '上唇上方'},
  {code: 'GV28', midline: true, ref: 'philtrum_upper_third', verticalCun: -1.2, span: 'upperAbdomen', summary: '上唇內齦交'},

  {code: 'ST1', atLevel: {xFrom: 'pupil_left', yFrom: 'infraorbital_rim', zFrom: 'infraorbital_rim'}, span: 'headFront', summary: '瞳孔直下、眶下緣'},
  {code: 'ST2', atLevel: {xFrom: 'pupil_left', yFrom: 'infraorbital_foramen', zFrom: 'infraorbital_foramen'}, span: 'headFront', summary: '瞳孔直下、眶下孔'},
  {code: 'ST3', atLevel: {xFrom: 'pupil_left', yFrom: 'nasal_ala', zFrom: 'nasal_ala'}, span: 'headFront', summary: '橫平鼻翼下緣、瞳孔直下'},
  {code: 'ST4', ref: 'mouth_corner', lateralCun: 0.4, span: 'headFront', summary: '口角旁 0.4 寸'},
  {code: 'ST6', ref: 'mentolabial_groove', verticalCun: 0.8, lateralCun: 1.5, span: 'upperAbdomen', summary: '下頜角前、咬肌隆起'},
  {code: 'ST9', ref: 'laryngeal_prominence', lateralAbsCun: 1.5, span: 'headFront', summary: '喉結旁開 1.5 寸'},
  {code: 'ST10', midpointOfRules: ['ST9', 'ST11'], span: 'headFront', summary: '人迎與氣舍連線中點'},
  {code: 'ST11', at: 'qishe_anchor', span: 'chest', summary: '鎖骨胸骨端上緣凹陷'},
  {code: 'ST12', ref: 'left_clavicle_mid', lateralAbsCun: 4, span: 'chest', summary: '鎖骨上窩、前正中線旁開 4 寸'},
  {code: 'ST13', ref: 'left_clavicle_mid', lateralAbsCun: 4, span: 'chest', summary: '鎖骨下緣、前正中線旁開 4 寸'},
  {code: 'ST14', sameLevelAs: 'CV20', lateralAbsCun: 4, zFrom: 'left_nipple', span: 'chest', summary: '第1肋間、前正中線旁開 4 寸'},
  {code: 'ST15', sameLevelAs: 'CV19', lateralAbsCun: 4, zFrom: 'left_nipple', span: 'chest', summary: '第2肋間、前正中線旁開 4 寸'},
  {code: 'ST16', sameLevelAs: 'CV18', lateralAbsCun: 4, zFrom: 'left_nipple', span: 'chest', summary: '第3肋間、前正中線旁開 4 寸'},
  {code: 'ST17', at: 'left_nipple', span: 'chest', summary: '乳頭中央、前正中線旁開 4 寸'},
  {code: 'ST18', sameLevelAs: 'CV16', lateralAbsCun: 4, zFrom: 'left_nipple', span: 'chest', summary: '第5肋間、前正中線旁開 4 寸'},
  {code: 'ST19', ref: 'navel', verticalCun: 6, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 6 寸、旁開 2 寸'},
  {code: 'ST20', ref: 'navel', verticalCun: 5, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 5 寸、旁開 2 寸'},
  {code: 'ST21', ref: 'navel', verticalCun: 4, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 4 寸、旁開 2 寸'},
  {code: 'ST22', ref: 'navel', verticalCun: 3, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 3 寸、旁開 2 寸'},
  {code: 'ST23', ref: 'navel', verticalCun: 2, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 2 寸、旁開 2 寸'},
  {code: 'ST24', ref: 'navel', verticalCun: 1, lateralCun: 2, span: 'upperAbdomen', summary: '臍中上 1 寸、旁開 2 寸'},
  {code: 'ST25', ref: 'navel', lateralCun: 2, span: 'upperAbdomen', summary: '臍中旁開 2 寸'},
  {code: 'ST26', ref: 'navel', verticalCun: -1, lateralCun: 2, span: 'lowerAbdomen', summary: '臍中下 1 寸、旁開 2 寸'},
  {code: 'ST27', ref: 'navel', verticalCun: -2, lateralCun: 2, span: 'lowerAbdomen', summary: '臍中下 2 寸、旁開 2 寸'},
  {code: 'ST28', ref: 'navel', verticalCun: -3, lateralCun: 2, span: 'lowerAbdomen', summary: '臍中下 3 寸、旁開 2 寸'},
  {code: 'ST29', ref: 'navel', verticalCun: -4, lateralCun: 2, span: 'lowerAbdomen', summary: '臍中下 4 寸、旁開 2 寸'},
  {code: 'ST30', ref: 'pubis_rim', lateralCun: 2, span: 'lowerAbdomen', summary: '恥骨聯合上緣旁開 2 寸'},
];

if (rules.length !== 80) {
  console.error(`規則 ${rules.length} 條，應為 80`);
  process.exit(1);
}

let knownExceedances = [];
try {
  knownExceedances = JSON.parse(readFileSync(join(project, 'data/cun-validation.json'), 'utf8')).knownExceedances || [];
} catch { /* first write */ }

const config = {
  thresholdCun: 0.3,
  worsenSlackCun: 0,
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
      spans,
    },
    catalog: {
      axes: {vertical: 'y', lateral: 'x', anterior: 'z'},
      landmarks: catalogLandmarks,
      spans,
    },
  },
  rules,
  knownExceedances,
};

writeFileSync(join(project, 'data/cun-validation.json'), `${JSON.stringify(config, null, 2)}\n`);
console.log(`wrote ${rules.length} rules`);
