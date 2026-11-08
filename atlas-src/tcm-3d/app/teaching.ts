import type {PainSelection} from './pain-relations';
import rawMeridians from './meridians.json';
import rawPoints from './teaching-points.json';
import type {SystemId} from './anatomy';
export type ToolId='needle'|'moxa'|'stone'|'finger'|'scraper'|'cup';
export type Vec3=[number,number,number];
export type RegionId='head'|'shoulder'|'arm'|'abdomen'|'waist'|'leg';
export const MERIDIANS=rawMeridians;
export interface TeachingPoint {meridianId:string;surfaceUnavailable?:boolean;code:string;name:string;pinyin:string;meridian:string;position:Vec3;normal:Vec3;region:RegionId;midline:boolean;reviewed:boolean;source:string;positionProvenance:string}
export const POINTS=rawPoints as TeachingPoint[];
export const POINT_BY_CODE=new Map(POINTS.map(p=>[p.code,p]));
export const STANDARD='https://zynj.shutcm.edu.cn/_upload/article/files/66/b4/b34a95604d04b0bf686251b2d317/368ea8b0-187c-48a7-a402-ffdf1e11bb99.pdf';
export const TOOLS:{id:ToolId;name:string;verb:string;effect:string;color:string;note:string}[]=[
 {id:'needle',name:'针',verb:'针刺动作演示',effect:'进针动作与局部反馈',color:'#687d94',note:'展示针具朝向与接触位置；不提供真人进针深度或角度。'},
 {id:'moxa',name:'灸',verb:'温热演示',effect:'温热范围示意',color:'#be7544',note:'暖色表示讲解区域，不代表温度、灸量或安全距离。'},
 {id:'stone',name:'砭石',verb:'按揉演示',effect:'接触与按揉',color:'#516b65',note:'展示光滑器具的接触与按揉，不模拟未经证实的能量作用。'},
 {id:'finger',name:'指压',verb:'按压演示',effect:'局部压放与形变',color:'#4d857b',note:'形变与光圈为教学反馈，演示幅度不对应人体按压力。'},
 {id:'scraper',name:'刮痧',verb:'刮动演示',effect:'刮拭轨迹示意',color:'#6c917d',note:'沿轨迹显红，松开后保留示意色痕；重播清除。颜色不代表出痧程度或疗效。'},
 {id:'cup',name:'火罐',verb:'吸附演示',effect:'杯口接触与负压形变',color:'#ac735d',note:'展示皮肤隆起与杯口色痕，松开后形变回弹；重播清除色痕。未模拟真实负压值。'},
];
export const REGIONS:{id:RegionId;name:string;example:string}[]=[
 {id:'head',name:'头面',example:'ST2'}, {id:'shoulder',name:'肩颈',example:'GB21'},
 {id:'arm',name:'手臂',example:'PC6'}, {id:'abdomen',name:'胸腹',example:'CV12'},
 {id:'waist',name:'腰背',example:'BL23'}, {id:'leg',name:'膝腿足',example:'ST36'},
];
export const SYSTEM_ZH:Record<SystemId,string>={skeletal:'骨骼',muscular:'肌肉',arterial:'动脉',venous:'静脉',nervous:'神经',digestive:'消化系统',respiratory:'呼吸系统',urinary:'泌尿系统',reproductive:'生殖系统',lymphatic:'淋巴',endocrine:'内分泌',integumentary:'皮肤',connective:'结缔组织',sensory:'感觉器官',cardiac:'心脏'};
export const LOCATION:Record<string,{text:string;section:string}>={
 ST36:{text:'小腿外侧，犊鼻下 3 寸，犊鼻与解溪连线上。',section:'5.3.36'},
 LI4:{text:'手背，第 2 掌骨桡侧的中点处。',section:'5.2.4'},
 PC6:{text:'前臂前区，腕掌侧远端横纹上 2 寸，掌长肌腱与桡侧腕屈肌腱之间。',section:'5.9.6'},
 GB21:{text:'颈后部，第 7 颈椎棘突与肩峰最外侧点连线的中点。',section:'5.11.21'},
 CV12:{text:'上腹部，脐中上 4 寸，前正中线上。',section:'5.14.12'},
 BL23:{text:'腰部，第 2 腰椎棘突下，后正中线旁开 1.5 寸。',section:'5.7.23'},
 BL40:{text:'膝后区，腘横纹中点。',section:'5.7.40'},
 KI1:{text:'足底，屈足卷趾时足心最凹陷中；须结合体位和标准注释定位。',section:'5.8.1'},
};
export interface SurfaceTarget {position:Vec3;normal:Vec3;meshId?:string;faceIndex?:number|null;pointCode?:string;pointSide?:1|-1}
export type ToolDragPhase='start'|'move'|'end'|'cancel';
export interface SimulationState {showMeridians?:boolean;meridianFilter?:string;pain?:PainSelection;tool:ToolId;release?:number;dragging?:boolean;running:boolean;elapsed:number;amplitude:number;pointCode:string|null;side:1|-1;customTarget:SurfaceTarget|null;focus:number;showPoints:boolean;pickMode:'point'|'pain'|'anatomy';effects:boolean}
export const initialSimulation:SimulationState={showMeridians:true,meridianFilter:'all',tool:'needle',running:false,elapsed:0,amplitude:.55,pointCode:'ST36',side:1,customTarget:null,focus:0,showPoints:true,pickMode:'point',effects:true};
export function pointTarget(code:string,side:1|-1):SurfaceTarget{const p=POINT_BY_CODE.get(code)!;return {position:[p.midline?p.position[0]:p.position[0]*side,p.position[1],p.position[2]],normal:[p.normal[0]*side,p.normal[1],p.normal[2]]};}
export function classifyRegion(p:Vec3):RegionId{return p[1]>1.52?'head':p[1]>1.3?'shoulder':Math.abs(p[0])>.18?'arm':p[1]>.8?(p[2]<-.025?'waist':'abdomen'):'leg';}
