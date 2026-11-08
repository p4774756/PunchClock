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
  nature: "阴" | "阳";
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
    overview: `${name}，中医经络腧穴。`,
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
    location: text.location || "参见经络循行与取穴法。",
    indications: text.indications || "详见各家针灸文献。",
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
    name: "手太阴肺经",
    shortName: "肺经",
    element: "金",
    nature: "阴",
    accent: "#3d8b7a",
    poetic: "主气司呼吸",
    description: "起于中焦，下络大肠，还循胃口，上膈属肺，从肺系横出腋下，沿上臂内侧下行至拇指桡侧端。",
    pathHint: "胸 → 上肢内侧前缘 → 拇指",
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
      P("lu5", "尺泽", "Chǐzé", "LU5", "lu", [W.elbow, 0.12, 0.05]),
      P("lu7", "列缺", "Lièquē", "LU7", "lu", [W.forearm + 0.02, -0.12, 0.04]),
      P("lu9", "太渊", "Tàiyuān", "LU9", "lu", [W.wrist, -0.22, 0.045]),
      P("lu11", "少商", "Shàoshāng", "LU11", "lu", [W.hand + 0.02, -0.34, 0.05]),
    ]),
  },
  {
    id: "li",
    name: "手阳明大肠经",
    shortName: "大肠经",
    element: "金",
    nature: "阳",
    accent: "#c45c48",
    poetic: "传导之官",
    description: "起于食指桡侧端，沿上肢外侧前缘上行，入缺盆络肺，下膈属大肠；支者上颈挟口，终于迎香。",
    pathHint: "食指 → 上肢外侧 → 面颊",
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
    name: "足阳明胃经",
    shortName: "胃经",
    element: "土",
    nature: "阳",
    accent: "#d4a017",
    poetic: "水谷之海",
    description: "起于鼻旁，下行入上齿，环唇，沿喉咙入缺盆，属胃络脾；体表支沿胸腹下行，经下肢外侧至足次趾。",
    pathHint: "面 → 胸腹 → 下肢前外侧 → 足趾",
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
      P("st6", "颊车", "Jiáchē", "ST6", "st", [0.1, B.mouth - 0.02, 0.1]),
      P("st8", "头维", "Tóuwéi", "ST8", "st", [0.1, B.forehead, 0.12]),
      P("st25", "天枢", "Tiānshū", "ST25", "st", [0.08, B.navel, 0.12]),
      P("st36", "足三里", "Zúsānlǐ", "ST36", "st", [0.09, B.shin + 0.06, 0.1]),
      P("st40", "丰隆", "Fēnglóng", "ST40", "st", [0.1, B.shin - 0.04, 0.08]),
      P("st44", "内庭", "Nèitíng", "ST44", "st", [0.05, B.sole + 0.06, 0.14]),
    ]),
  },
  {
    id: "sp",
    name: "足太阴脾经",
    shortName: "脾经",
    element: "土",
    nature: "阴",
    accent: "#7a9e45",
    poetic: "后天之本",
    description: "起于足大趾内侧端，沿内踝上行，腿内侧前缘入腹，属脾络胃，上膈挟咽，连舌本散舌下。",
    pathHint: "足大趾 → 下肢内侧 → 腹胸",
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
      P("sp6", "三阴交", "Sānyīnjiāo", "SP6", "sp", [0.05, B.ankle + 0.08, 0.02]),
      P("sp9", "阴陵泉", "Yīnlíngquán", "SP9", "sp", [0.07, B.knee + 0.02, 0.02]),
      P("sp10", "血海", "Xuèhǎi", "SP10", "sp", [0.08, B.thigh - 0.02, 0.04]),
    ]),
  },
  {
    id: "ht",
    name: "手少阴心经",
    shortName: "心经",
    element: "火",
    nature: "阴",
    accent: "#b33b3b",
    poetic: "君主之官",
    description: "起于心中，出属心系，下膈络小肠；体表支从腋下沿上肢内侧后缘至小指桡侧端。",
    pathHint: "腋下 → 上肢内侧后缘 → 小指",
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
      P("ht5", "通里", "Tōnglǐ", "HT5", "ht", [W.forearm, -0.1, -0.02]),
      P("ht7", "神门", "Shénmén", "HT7", "ht", [W.wrist - 0.02, -0.22, -0.02]),
      P("ht9", "少冲", "Shàochōng", "HT9", "ht", [W.hand - 0.04, -0.34, 0.0]),
    ]),
  },
  {
    id: "si",
    name: "手太阳小肠经",
    shortName: "小肠经",
    element: "火",
    nature: "阳",
    accent: "#e07a3d",
    poetic: "受盛之官",
    description: "起于小指尺侧端，沿上肢外侧后缘上肩，入缺盆络心，下膈属小肠；支者上颊至耳前听宫。",
    pathHint: "小指 → 上肢外侧后缘 → 耳前",
    path: [
      [W.hand - 0.04, -0.34, -0.02],
      [W.wrist, -0.22, -0.04],
      [W.elbow, 0.12, -0.06],
      [W.shoulder, B.shoulder, -0.08],
      [0.14, B.neck, -0.06],
      [0.12, B.ear, 0.02],
    ],
    points: withMirror([
      P("si3", "后溪", "Hòuxī", "SI3", "si", [W.hand - 0.04, -0.3, -0.03]),
      P("si8", "小海", "Xiǎohǎi", "SI8", "si", [W.elbow, 0.12, -0.06]),
      P("si11", "天宗", "Tiānzōng", "SI11", "si", [0.16, B.shoulder - 0.04, -0.1]),
      P("si19", "听宫", "Tīnggōng", "SI19", "si", [0.12, B.ear - 0.02, 0.04]),
    ]),
  },
  {
    id: "bl",
    name: "足太阳膀胱经",
    shortName: "膀胱经",
    element: "水",
    nature: "阳",
    accent: "#3a6ea5",
    poetic: "州都之官",
    description: "起于目内眦，上额交巅，入络脑，下行项后挟脊抵腰，络肾属膀胱，沿下肢后侧至足小趾。",
    pathHint: "目内眦 → 头项 → 背腰 → 下肢后侧",
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
      P("bl23", "肾俞", "Shènshū", "BL23", "bl", [0.06, B.danTian, -0.12]),
      P("bl40", "委中", "Wěizhōng", "BL40", "bl", [0.07, B.knee, -0.08]),
      P("bl57", "承山", "Chéngshān", "BL57", "bl", [0.06, B.shin, -0.08]),
      P("bl60", "昆仑", "Kūnlún", "BL60", "bl", [0.06, B.ankle, -0.05]),
    ]),
  },
  {
    id: "ki",
    name: "足少阴肾经",
    shortName: "肾经",
    element: "水",
    nature: "阴",
    accent: "#5c4a8a",
    poetic: "先天之本",
    description: "起于足小趾下，斜向足心涌泉，沿内踝后上行，腿内侧后缘入腹，属肾络膀胱，上贯肝膈入肺。",
    pathHint: "足心 → 下肢内侧后缘 → 腹胸",
    path: [
      [0.03, B.sole + 0.02, 0.02],
      [0.04, B.ankle, -0.02],
      [0.05, B.knee, -0.02],
      [0.05, B.hip, 0.0],
      [0.04, B.navel, 0.06],
      [0.04, B.chest, 0.1],
    ],
    points: withMirror([
      P("ki1", "涌泉", "Yǒngquán", "KI1", "ki", [0.03, B.sole + 0.01, 0.04]),
      P("ki3", "太溪", "Tàixī", "KI3", "ki", [0.04, B.ankle, -0.02]),
      P("ki6", "照海", "Zhàohǎi", "KI6", "ki", [0.04, B.ankle + 0.04, 0.02]),
      P("ki7", "复溜", "Fùliū", "KI7", "ki", [0.045, B.ankle + 0.1, -0.02]),
    ]),
  },
  {
    id: "pc",
    name: "手厥阴心包经",
    shortName: "心包经",
    element: "火",
    nature: "阴",
    accent: "#c44d6a",
    poetic: "代心受邪",
    description: "起于胸中，出属心包，下膈历络三焦；体表支从胁下三寸出腋，沿上肢内侧中间下行至中指端。",
    pathHint: "胸胁 → 上肢内侧中线 → 中指",
    path: [
      [W.torso + 0.02, B.nipple, 0.1],
      [W.shoulder - 0.04, B.shoulder - 0.08, 0.05],
      [W.upperArm - 0.02, 0.28, 0.03],
      [W.elbow - 0.02, 0.1, 0.02],
      [W.wrist - 0.02, -0.22, 0.02],
      [W.hand - 0.02, -0.34, 0.03],
    ],
    points: withMirror([
      P("pc3", "曲泽", "Qūzé", "PC3", "pc", [W.elbow - 0.02, 0.1, 0.02]),
      P("pc6", "内关", "Nèiguān", "PC6", "pc", [W.forearm - 0.02, -0.1, 0.02]),
      P("pc7", "大陵", "Dàlíng", "PC7", "pc", [W.wrist - 0.02, -0.22, 0.02]),
      P("pc8", "劳宫", "Láogōng", "PC8", "pc", [W.hand - 0.04, -0.28, 0.03]),
      P("pc9", "中冲", "Zhōngchōng", "PC9", "pc", [W.hand - 0.02, -0.35, 0.03]),
    ]),
  },
  {
    id: "te",
    name: "手少阳三焦经",
    shortName: "三焦经",
    element: "火",
    nature: "阳",
    accent: "#d4893b",
    poetic: "决渎之官",
    description: "起于无名指尺侧端，沿上肢外侧中间上肩，入缺盆布膻中，散络心包，下膈属三焦；支者上耳周至眉梢。",
    pathHint: "无名指 → 上肢外侧中线 → 耳颞",
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
      P("te5", "外关", "Wàiguān", "TE5", "te", [W.forearm, -0.1, 0.02]),
      P("te14", "肩髎", "Jiānliáo", "TE14", "te", [W.shoulder, B.shoulder, 0.0]),
      P("te17", "翳风", "Yìfēng", "TE17", "te", [0.12, B.ear - 0.04, 0.02]),
      P("te23", "丝竹空", "Sīzhúkōng", "TE23", "te", [0.1, B.eye + 0.02, 0.12]),
    ]),
  },
  {
    id: "gb",
    name: "足少阳胆经",
    shortName: "胆经",
    element: "木",
    nature: "阳",
    accent: "#2f8f6b",
    poetic: "中正之官",
    description: "起于目外眦，头侧下行至肩，入缺盆；体表支胸胁侧下行，经髋外侧、下肢外侧至足四趾。",
    pathHint: "目外眦 → 头侧胸胁 → 下肢外侧",
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
      P("gb20", "风池", "Fēngchí", "GB20", "gb", [0.08, B.neck + 0.04, -0.08]),
      P("gb21", "肩井", "Jiānjǐng", "GB21", "gb", [0.14, B.shoulder + 0.02, 0.0]),
      P("gb30", "环跳", "Huántiào", "GB30", "gb", [0.16, B.hip, 0.0]),
      P("gb34", "阳陵泉", "Yánglíngquán", "GB34", "gb", [0.11, B.knee - 0.02, 0.05]),
      P("gb40", "丘墟", "Qiūxū", "GB40", "gb", [0.09, B.ankle, 0.06]),
      P("gb41", "足临泣", "Zúlínqì", "GB41", "gb", [0.07, B.sole + 0.08, 0.1]),
    ]),
  },
  {
    id: "lr",
    name: "足厥阴肝经",
    shortName: "肝经",
    element: "木",
    nature: "阴",
    accent: "#4a7c59",
    poetic: "将军之官",
    description: "起于足大趾丛毛际，沿足背上内踝前，腿内侧中间上行入阴毛中，环阴器抵小腹，挟胃属肝络胆。",
    pathHint: "足大趾 → 下肢内侧中线 → 胁肋",
    path: [
      [0.04, B.sole + 0.06, 0.12],
      [0.05, B.ankle + 0.02, 0.06],
      [0.06, B.knee, 0.0],
      [0.06, B.hip, 0.02],
      [0.08, B.navel + 0.04, 0.08],
      [0.1, B.chest - 0.04, 0.1],
    ],
    points: withMirror([
      P("lr3", "太冲", "Tàichōng", "LR3", "lr", [0.04, B.sole + 0.1, 0.12]),
      P("lr5", "蠡沟", "Lígōu", "LR5", "lr", [0.055, B.shin + 0.04, 0.02]),
      P("lr8", "曲泉", "Qūquán", "LR8", "lr", [0.07, B.knee + 0.02, 0.0]),
      P("lr14", "期门", "Qīmén", "LR14", "lr", [0.1, B.nipple - 0.04, 0.12]),
    ]),
  },
  {
    id: "cv",
    name: "任脉",
    shortName: "任脉",
    element: "奇经",
    nature: "阴",
    accent: "#8b4513",
    poetic: "阴脉之海",
    description: "起于胞中，下出会阴，沿腹正中线上行，经关元、气海、中脘、膻中至咽喉，上颐环唇。",
    pathHint: "会阴 → 腹胸正中 → 承浆",
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
      P("cv3", "中极", "Zhōngjí", "CV3", "cv", [0, B.pubis + 0.02, 0.11], "mid"),
      P("cv4", "关元", "Guānyuán", "CV4", "cv", [0, B.danTian - 0.04, 0.12], "mid"),
      P("cv6", "气海", "Qìhǎi", "CV6", "cv", [0, B.danTian + 0.02, 0.12], "mid"),
      P("cv12", "中脘", "Zhōngwǎn", "CV12", "cv", [0, B.xiphoid - 0.02, 0.13], "mid"),
      P("cv17", "膻中", "Dànzhōng", "CV17", "cv", [0, B.nipple + 0.02, 0.14], "mid"),
      P("cv22", "天突", "Tiāntū", "CV22", "cv", [0, B.neck - 0.04, 0.12], "mid"),
      P("cv24", "承浆", "Chéngjiāng", "CV24", "cv", [0, B.mouth - 0.04, 0.14], "mid"),
    ],
  },
  {
    id: "gv",
    name: "督脉",
    shortName: "督脉",
    element: "奇经",
    nature: "阳",
    accent: "#1a5f7a",
    poetic: "阳脉之海",
    description: "起于胞中，下出会阴，后行于脊内，上至风府入脑，上巅至百会，下额至鼻柱，终于龈交。",
    pathHint: "会阴 → 脊背正中 → 巅顶 → 人中",
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
      P("gv4", "命门", "Mìngmén", "GV4", "gv", [0, B.danTian, -0.13], "mid"),
      P("gv14", "大椎", "Dàzhuī", "GV14", "gv", [0, B.neck + 0.02, -0.11], "mid"),
      P("gv16", "风府", "Fēngfǔ", "GV16", "gv", [0, B.neck + 0.08, -0.1], "mid"),
      P("gv20", "百会", "Bǎihuì", "GV20", "gv", [0, B.crown, 0.0], "mid"),
      P("gv26", "水沟", "Shuǐgōu", "GV26", "gv", [0, B.mouth + 0.02, 0.145], "mid"),
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
