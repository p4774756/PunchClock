import pointTexts from "../data/point-texts.json";

export type MeridianId =
  | "lu"
  | "li"
  | "st"
  | "sp"
  | "ht"
  | "si"
  | "bl"
  | "ki"
  | "pc"
  | "te"
  | "gb"
  | "lr"
  | "cv"
  | "gv";

export type Acupoint = {
  id: string;
  name: string;
  pinyin: string;
  code: string;
  meridianId: MeridianId;
  /** Approximate anatomical position on the mannequin (Y-up, facing +Z). */
  position: [number, number, number];
  side?: "left" | "right" | "mid";
};

export type Meridian = {
  id: MeridianId;
  name: string;
  shortName: string;
  element: string;
  nature: "陰" | "陽";
  accent: string;
  poetic: string;
  description: string;
  pathHint: string;
  /** Control points for the meridian polyline on the body surface. */
  path: [number, number, number][];
  points: Acupoint[];
};

type PointText = {
  name: string;
  url: string;
  overview: string;
  location: string;
  indications: string;
};

const texts = pointTexts as Record<string, PointText>;

function lookupText(name: string): PointText {
  const key = name.endsWith("穴") ? name.slice(0, -1) : name;
  const hit = texts[key] ?? texts[name] ?? texts[`${key}穴`];
  if (hit) return hit;
  return {
    name,
    url: "",
    overview: `${name}，中醫經絡腧穴。`,
    location: "",
    indications: "",
  };
}

export function getPointDetail(point: Acupoint) {
  const text = lookupText(point.name);
  return {
    ...point,
    displayName: text.name || point.name,
    overview: text.overview,
    location: text.location || "參見經絡循行與取穴法。",
    indications: text.indications || "詳見各家針灸文獻。",
    url: text.url,
  };
}

/** Body space: height ≈ 1.72 from foot sole (−0.86) to crown (+0.86), origin at pelvis. */
const B = {
  crown: 0.86,
  forehead: 0.78,
  eye: 0.72,
  ear: 0.7,
  mouth: 0.64,
  neck: 0.58,
  shoulder: 0.48,
  chest: 0.32,
  nipple: 0.28,
  xiphoid: 0.18,
  navel: 0.06,
  danTian: 0.0,
  pubis: -0.1,
  hip: -0.12,
  thigh: -0.32,
  knee: -0.48,
  shin: -0.62,
  ankle: -0.78,
  sole: -0.86,
} as const;

const W = {
  mid: 0,
  para: 0.04,
  torso: 0.12,
  flank: 0.16,
  shoulder: 0.22,
  upperArm: 0.32,
  elbow: 0.38,
  forearm: 0.42,
  wrist: 0.46,
  hand: 0.5,
  hip: 0.1,
  thigh: 0.09,
  knee: 0.08,
  shin: 0.07,
  ankle: 0.05,
  foot: 0.08,
} as const;

function P(
  id: string,
  name: string,
  pinyin: string,
  code: string,
  meridianId: MeridianId,
  position: [number, number, number],
  side: Acupoint["side"] = "right",
): Acupoint {
  return { id, name, pinyin, code, meridianId, position, side };
}

/** Mirror a unilateral meridian's points to the left side for bilateral display. */
function withMirror(points: Acupoint[]): Acupoint[] {
  const mirrored = points
    .filter((p) => p.side === "right")
    .map((p) => ({
      ...p,
      id: `${p.id}-L`,
      position: [-p.position[0], p.position[1], p.position[2]] as [number, number, number],
      side: "left" as const,
    }));
  return [...points, ...mirrored];
}

export const meridians: Meridian[] = [
  {
    id: "lu",
    name: "手太陰肺經",
    shortName: "肺經",
    element: "金",
    nature: "陰",
    accent: "#3d8b7a",
    poetic: "主氣司呼吸",
    description: "起於中焦，下絡大腸，還循胃口，上膈屬肺，從肺系橫出腋下，沿上臂內側下行至拇指橈側端。",
    pathHint: "胸 → 上肢內側前緣 → 拇指",
    path: [
      [W.torso, B.chest, 0.12],
      [W.shoulder, B.shoulder - 0.02, 0.08],
      [W.upperArm, 0.3, 0.06],
      [W.elbow, 0.12, 0.05],
      [W.forearm, -0.05, 0.04],
      [W.wrist, -0.22, 0.04],
      [W.hand, -0.32, 0.05],
    ],
    points: withMirror([
      P("lu1", "中府", "Zhōngfǔ", "LU1", "lu", [W.torso + 0.02, B.chest + 0.02, 0.13]),
      P("lu5", "尺澤", "Chǐzé", "LU5", "lu", [W.elbow, 0.12, 0.05]),
      P("lu7", "列缺", "Lièquē", "LU7", "lu", [W.forearm + 0.02, -0.12, 0.04]),
      P("lu9", "太淵", "Tàiyuān", "LU9", "lu", [W.wrist, -0.22, 0.045]),
      P("lu11", "少商", "Shàoshāng", "LU11", "lu", [W.hand + 0.02, -0.34, 0.05]),
    ]),
  },
  {
    id: "li",
    name: "手陽明大腸經",
    shortName: "大腸經",
    element: "金",
    nature: "陽",
    accent: "#c45c48",
    poetic: "傳導之官",
    description: "起於食指橈側端，沿上肢外側前緣上行，入缺盆絡肺，下膈屬大腸；支者上頸挾口，終於迎香。",
    pathHint: "食指 → 上肢外側 → 面頰",
    path: [
      [W.hand, -0.34, 0.06],
      [W.wrist, -0.22, 0.07],
      [W.elbow, 0.12, 0.09],
      [W.shoulder, B.shoulder, 0.1],
      [0.12, B.neck, 0.12],
      [0.08, B.mouth, 0.14],
      [0.06, B.eye - 0.02, 0.15],
    ],
    points: withMirror([
      P("li4", "合谷", "Hégǔ", "LI4", "li", [W.hand - 0.02, -0.3, 0.07]),
      P("li11", "曲池", "Qūchí", "LI11", "li", [W.elbow, 0.12, 0.09]),
      P("li15", "肩髃", "Jiānyú", "LI15", "li", [W.shoulder, B.shoulder, 0.1]),
      P("li20", "迎香", "Yíngxiāng", "LI20", "li", [0.04, B.mouth + 0.02, 0.15]),
    ]),
  },
  {
    id: "st",
    name: "足陽明胃經",
    shortName: "胃經",
    element: "土",
    nature: "陽",
    accent: "#d4a017",
    poetic: "水穀之海",
    description: "起於鼻旁，下行入上齒，環唇，沿喉嚨入缺盆，屬胃絡脾；體表支沿胸腹下行，經下肢外側至足次趾。",
    pathHint: "面 → 胸腹 → 下肢前外側 → 足趾",
    path: [
      [0.05, B.eye, 0.15],
      [0.08, B.mouth, 0.14],
      [0.1, B.neck, 0.12],
      [0.1, B.nipple, 0.13],
      [0.08, B.navel, 0.12],
      [0.09, B.hip, 0.1],
      [0.09, B.knee, 0.09],
      [0.08, B.ankle, 0.08],
      [0.06, B.sole + 0.04, 0.12],
    ],
    points: withMirror([
      P("st6", "頰車", "Jiáchē", "ST6", "st", [0.1, B.mouth - 0.02, 0.1]),
      P("st8", "頭維", "Tóuwéi", "ST8", "st", [0.1, B.forehead, 0.12]),
      P("st25", "天樞", "Tiānshū", "ST25", "st", [0.08, B.navel, 0.12]),
      P("st36", "足三里", "Zúsānlǐ", "ST36", "st", [0.09, B.shin + 0.06, 0.1]),
      P("st40", "豐隆", "Fēnglóng", "ST40", "st", [0.1, B.shin - 0.04, 0.08]),
      P("st44", "內庭", "Nèitíng", "ST44", "st", [0.05, B.sole + 0.06, 0.14]),
    ]),
  },
  {
    id: "sp",
    name: "足太陰脾經",
    shortName: "脾經",
    element: "土",
    nature: "陰",
    accent: "#7a9e45",
    poetic: "後天之本",
    description: "起於足大趾內側端，沿內踝上行，腿內側前緣入腹，屬脾絡胃，上膈挾咽，連舌本散舌下。",
    pathHint: "足大趾 → 下肢內側 → 腹胸",
    path: [
      [0.04, B.sole + 0.04, 0.1],
      [0.05, B.ankle, 0.04],
      [0.06, B.knee, 0.03],
      [0.07, B.hip, 0.04],
      [0.08, B.navel, 0.08],
      [0.1, B.chest, 0.1],
    ],
    points: withMirror([
      P("sp3", "太白", "Tàibái", "SP3", "sp", [0.05, B.sole + 0.08, 0.08]),
      P("sp6", "三陰交", "Sānyīnjiāo", "SP6", "sp", [0.05, B.ankle + 0.08, 0.02]),
      P("sp9", "陰陵泉", "Yīnlíngquán", "SP9", "sp", [0.07, B.knee + 0.02, 0.02]),
      P("sp10", "血海", "Xuèhǎi", "SP10", "sp", [0.08, B.thigh - 0.02, 0.04]),
    ]),
  },
  {
    id: "ht",
    name: "手少陰心經",
    shortName: "心經",
    element: "火",
    nature: "陰",
    accent: "#b33b3b",
    poetic: "君主之官",
    description: "起於心中，出屬心繫，下膈絡小腸；體表支從腋下沿上肢內側後緣至小指橈側端。",
    pathHint: "腋下 → 上肢內側後緣 → 小指",
    path: [
      [W.torso + 0.04, B.chest - 0.02, 0.06],
      [W.shoulder - 0.02, B.shoulder - 0.06, 0.02],
      [W.upperArm, 0.28, 0.0],
      [W.elbow, 0.1, -0.02],
      [W.wrist, -0.22, -0.02],
      [W.hand - 0.02, -0.34, 0.0],
    ],
    points: withMirror([
      P("ht3", "少海", "Shàohǎi", "HT3", "ht", [W.elbow - 0.02, 0.1, -0.02]),
      P("ht5", "通裡", "Tōnglǐ", "HT5", "ht", [W.forearm, -0.1, -0.02]),
      P("ht7", "神門", "Shénmén", "HT7", "ht", [W.wrist - 0.02, -0.22, -0.02]),
      P("ht9", "少衝", "Shàochōng", "HT9", "ht", [W.hand - 0.04, -0.34, 0.0]),
    ]),
  },
  {
    id: "si",
    name: "手太陽小腸經",
    shortName: "小腸經",
    element: "火",
    nature: "陽",
    accent: "#e07a3d",
    poetic: "受盛之官",
    description: "起於小指尺側端，沿上肢外側後緣上肩，入缺盆絡心，下膈屬小腸；支者上頰至耳前聽宮。",
    pathHint: "小指 → 上肢外側後緣 → 耳前",
    path: [
      [W.hand - 0.04, -0.34, -0.02],
      [W.wrist, -0.22, -0.04],
      [W.elbow, 0.12, -0.06],
      [W.shoulder, B.shoulder, -0.08],
      [0.14, B.neck, -0.06],
      [0.12, B.ear, 0.02],
    ],
    points: withMirror([
      P("si3", "後溪", "Hòuxī", "SI3", "si", [W.hand - 0.04, -0.3, -0.03]),
      P("si8", "小海", "Xiǎohǎi", "SI8", "si", [W.elbow, 0.12, -0.06]),
      P("si11", "天宗", "Tiānzōng", "SI11", "si", [0.16, B.shoulder - 0.04, -0.1]),
      P("si19", "聽宮", "Tīnggōng", "SI19", "si", [0.12, B.ear - 0.02, 0.04]),
    ]),
  },
  {
    id: "bl",
    name: "足太陽膀胱經",
    shortName: "膀胱經",
    element: "水",
    nature: "陽",
    accent: "#3a6ea5",
    poetic: "州都之官",
    description: "起於目內眥，上額交巔，入絡腦，下行項後挾脊抵腰，絡腎屬膀胱，沿下肢後側至足小趾。",
    pathHint: "目內眥 → 頭項 → 背腰 → 下肢後側",
    path: [
      [0.03, B.eye, 0.14],
      [0.04, B.crown - 0.02, 0.0],
      [0.05, B.neck, -0.1],
      [0.06, B.chest, -0.12],
      [0.06, B.navel, -0.12],
      [0.07, B.hip, -0.1],
      [0.07, B.knee, -0.08],
      [0.05, B.ankle, -0.06],
      [0.06, B.sole + 0.04, -0.02],
    ],
    points: withMirror([
      P("bl1", "睛明", "Jīngmíng", "BL1", "bl", [0.025, B.eye, 0.145]),
      P("bl10", "天柱", "Tiānzhù", "BL10", "bl", [0.05, B.neck + 0.02, -0.1]),
      P("bl13", "肺俞", "Fèishū", "BL13", "bl", [0.06, B.chest + 0.04, -0.12]),
      P("bl15", "心俞", "Xīnshū", "BL15", "bl", [0.06, B.chest - 0.04, -0.12]),
      P("bl23", "腎俞", "Shènshū", "BL23", "bl", [0.06, B.danTian, -0.12]),
      P("bl40", "委中", "Wěizhōng", "BL40", "bl", [0.07, B.knee, -0.08]),
      P("bl57", "承山", "Chéngshān", "BL57", "bl", [0.06, B.shin, -0.08]),
      P("bl60", "崑崙", "Kūnlún", "BL60", "bl", [0.06, B.ankle, -0.05]),
    ]),
  },
  {
    id: "ki",
    name: "足少陰腎經",
    shortName: "腎經",
    element: "水",
    nature: "陰",
    accent: "#5c4a8a",
    poetic: "先天之本",
    description: "起於足小趾下，斜向足心湧泉，沿內踝後上行，腿內側後緣入腹，屬腎絡膀胱，上貫肝膈入肺。",
    pathHint: "足心 → 下肢內側後緣 → 腹胸",
    path: [
      [0.03, B.sole + 0.02, 0.02],
      [0.04, B.ankle, -0.02],
      [0.05, B.knee, -0.02],
      [0.05, B.hip, 0.0],
      [0.04, B.navel, 0.06],
      [0.04, B.chest, 0.1],
    ],
    points: withMirror([
      P("ki1", "湧泉", "Yǒngquán", "KI1", "ki", [0.03, B.sole + 0.01, 0.04]),
      P("ki3", "太溪", "Tàixī", "KI3", "ki", [0.04, B.ankle, -0.02]),
      P("ki6", "照海", "Zhàohǎi", "KI6", "ki", [0.04, B.ankle + 0.04, 0.02]),
      P("ki7", "復溜", "Fùliū", "KI7", "ki", [0.045, B.ankle + 0.1, -0.02]),
    ]),
  },
  {
    id: "pc",
    name: "手厥陰心包經",
    shortName: "心包經",
    element: "火",
    nature: "陰",
    accent: "#c44d6a",
    poetic: "代心受邪",
    description: "起於胸中，出屬心包，下膈歷絡三焦；體表支從脅下三寸出腋，沿上肢內側中間下行至中指端。",
    pathHint: "胸脅 → 上肢內側中線 → 中指",
    path: [
      [W.torso + 0.02, B.nipple, 0.1],
      [W.shoulder - 0.04, B.shoulder - 0.08, 0.05],
      [W.upperArm - 0.02, 0.28, 0.03],
      [W.elbow - 0.02, 0.1, 0.02],
      [W.wrist - 0.02, -0.22, 0.02],
      [W.hand - 0.02, -0.34, 0.03],
    ],
    points: withMirror([
      P("pc3", "曲澤", "Qūzé", "PC3", "pc", [W.elbow - 0.02, 0.1, 0.02]),
      P("pc6", "內關", "Nèiguān", "PC6", "pc", [W.forearm - 0.02, -0.1, 0.02]),
      P("pc7", "大陵", "Dàlíng", "PC7", "pc", [W.wrist - 0.02, -0.22, 0.02]),
      P("pc8", "勞宮", "Láogōng", "PC8", "pc", [W.hand - 0.04, -0.28, 0.03]),
      P("pc9", "中衝", "Zhōngchōng", "PC9", "pc", [W.hand - 0.02, -0.35, 0.03]),
    ]),
  },
  {
    id: "te",
    name: "手少陽三焦經",
    shortName: "三焦經",
    element: "火",
    nature: "陽",
    accent: "#d4893b",
    poetic: "決瀆之官",
    description: "起於無名指尺側端，沿上肢外側中間上肩，入缺盆布膻中，散絡心包，下膈屬三焦；支者上耳周至眉梢。",
    pathHint: "無名指 → 上肢外側中線 → 耳顳",
    path: [
      [W.hand, -0.34, 0.02],
      [W.wrist, -0.22, 0.02],
      [W.elbow, 0.12, 0.02],
      [W.shoulder, B.shoulder, 0.0],
      [0.14, B.neck, 0.0],
      [0.12, B.ear, 0.06],
      [0.1, B.eye, 0.12],
    ],
    points: withMirror([
      P("te5", "外關", "Wàiguān", "TE5", "te", [W.forearm, -0.1, 0.02]),
      P("te14", "肩髎", "Jiānliáo", "TE14", "te", [W.shoulder, B.shoulder, 0.0]),
      P("te17", "翳風", "Yìfēng", "TE17", "te", [0.12, B.ear - 0.04, 0.02]),
      P("te23", "絲竹空", "Sīzhúkōng", "TE23", "te", [0.1, B.eye + 0.02, 0.12]),
    ]),
  },
  {
    id: "gb",
    name: "足少陽膽經",
    shortName: "膽經",
    element: "木",
    nature: "陽",
    accent: "#2f8f6b",
    poetic: "中正之官",
    description: "起於目外眥，頭側下行至肩，入缺盆；體表支胸脅側下行，經髖外側、下肢外側至足四趾。",
    pathHint: "目外眥 → 頭側胸脅 → 下肢外側",
    path: [
      [0.12, B.eye, 0.12],
      [0.14, B.ear, 0.04],
      [0.16, B.shoulder, 0.02],
      [0.14, B.nipple, 0.06],
      [0.14, B.hip, 0.04],
      [0.12, B.knee, 0.04],
      [0.1, B.ankle, 0.04],
      [0.08, B.sole + 0.05, 0.1],
    ],
    points: withMirror([
      P("gb20", "風池", "Fēngchí", "GB20", "gb", [0.08, B.neck + 0.04, -0.08]),
      P("gb21", "肩井", "Jiānjǐng", "GB21", "gb", [0.14, B.shoulder + 0.02, 0.0]),
      P("gb30", "環跳", "Huántiào", "GB30", "gb", [0.16, B.hip, 0.0]),
      P("gb34", "陽陵泉", "Yánglíngquán", "GB34", "gb", [0.11, B.knee - 0.02, 0.05]),
      P("gb40", "丘墟", "Qiūxū", "GB40", "gb", [0.09, B.ankle, 0.06]),
      P("gb41", "足臨泣", "Zúlínqì", "GB41", "gb", [0.07, B.sole + 0.08, 0.1]),
    ]),
  },
  {
    id: "lr",
    name: "足厥陰肝經",
    shortName: "肝經",
    element: "木",
    nature: "陰",
    accent: "#4a7c59",
    poetic: "將軍之官",
    description: "起於足大趾叢毛際，沿足背上內踝前，腿內側中間上行入陰毛中，環陰器抵小腹，挾胃屬肝絡膽。",
    pathHint: "足大趾 → 下肢內側中線 → 脅肋",
    path: [
      [0.04, B.sole + 0.06, 0.12],
      [0.05, B.ankle + 0.02, 0.06],
      [0.06, B.knee, 0.0],
      [0.06, B.hip, 0.02],
      [0.08, B.navel + 0.04, 0.08],
      [0.1, B.chest - 0.04, 0.1],
    ],
    points: withMirror([
      P("lr3", "太沖", "Tàichōng", "LR3", "lr", [0.04, B.sole + 0.1, 0.12]),
      P("lr5", "蠡溝", "Lígōu", "LR5", "lr", [0.055, B.shin + 0.04, 0.02]),
      P("lr8", "曲泉", "Qūquán", "LR8", "lr", [0.07, B.knee + 0.02, 0.0]),
      P("lr14", "期門", "Qīmén", "LR14", "lr", [0.1, B.nipple - 0.04, 0.12]),
    ]),
  },
  {
    id: "cv",
    name: "任脈",
    shortName: "任脈",
    element: "奇經",
    nature: "陰",
    accent: "#8b4513",
    poetic: "陰脈之海",
    description: "起於胞中，下出會陰，沿腹正中線上行，經關元、氣海、中脘、膻中至咽喉，上頤環唇。",
    pathHint: "會陰 → 腹胸正中 → 承漿",
    path: [
      [0, B.pubis - 0.02, 0.1],
      [0, B.danTian, 0.12],
      [0, B.navel, 0.13],
      [0, B.xiphoid, 0.13],
      [0, B.chest + 0.04, 0.14],
      [0, B.neck - 0.02, 0.12],
      [0, B.mouth - 0.02, 0.14],
    ],
    points: [
      P("cv3", "中極", "Zhōngjí", "CV3", "cv", [0, B.pubis + 0.02, 0.11], "mid"),
      P("cv4", "關元", "Guānyuán", "CV4", "cv", [0, B.danTian - 0.04, 0.12], "mid"),
      P("cv6", "氣海", "Qìhǎi", "CV6", "cv", [0, B.danTian + 0.02, 0.12], "mid"),
      P("cv12", "中脘", "Zhōngwǎn", "CV12", "cv", [0, B.xiphoid - 0.02, 0.13], "mid"),
      P("cv17", "膻中", "Dànzhōng", "CV17", "cv", [0, B.nipple + 0.02, 0.14], "mid"),
      P("cv22", "天突", "Tiāntū", "CV22", "cv", [0, B.neck - 0.04, 0.12], "mid"),
      P("cv24", "承漿", "Chéngjiāng", "CV24", "cv", [0, B.mouth - 0.04, 0.14], "mid"),
    ],
  },
  {
    id: "gv",
    name: "督脈",
    shortName: "督脈",
    element: "奇經",
    nature: "陽",
    accent: "#1a5f7a",
    poetic: "陽脈之海",
    description: "起於胞中，下出會陰，後行於脊內，上至風府入腦，上巔至百會，下額至鼻柱，終於齦交。",
    pathHint: "會陰 → 脊背正中 → 巔頂 → 人中",
    path: [
      [0, B.pubis - 0.02, -0.08],
      [0, B.hip, -0.12],
      [0, B.navel, -0.13],
      [0, B.chest, -0.13],
      [0, B.neck, -0.11],
      [0, B.crown, 0.0],
      [0, B.forehead, 0.12],
      [0, B.mouth + 0.02, 0.145],
    ],
    points: [
      P("gv4", "命門", "Mìngmén", "GV4", "gv", [0, B.danTian, -0.13], "mid"),
      P("gv14", "大椎", "Dàzhuī", "GV14", "gv", [0, B.neck + 0.02, -0.11], "mid"),
      P("gv16", "風府", "Fēngfǔ", "GV16", "gv", [0, B.neck + 0.08, -0.1], "mid"),
      P("gv20", "百會", "Bǎihuì", "GV20", "gv", [0, B.crown, 0.0], "mid"),
      P("gv26", "水溝", "Shuǐgōu", "GV26", "gv", [0, B.mouth + 0.02, 0.145], "mid"),
    ],
  },
];

export const meridianById = Object.fromEntries(meridians.map((m) => [m.id, m])) as Record<
  MeridianId,
  Meridian
>;

export const allPoints: Acupoint[] = meridians.flatMap((m) => m.points);

export function searchPoints(query: string): Acupoint[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  return allPoints.filter((p) => {
    const m = meridianById[p.meridianId];
    return (
      p.name.toLowerCase().includes(q) ||
      p.pinyin.toLowerCase().includes(q) ||
      p.code.toLowerCase().includes(q) ||
      m.name.includes(q) ||
      m.shortName.includes(q)
    );
  });
}
