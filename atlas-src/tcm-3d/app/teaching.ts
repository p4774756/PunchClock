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
 {id:'needle',name:'針',verb:'針刺動作演示',effect:'進針動作與局部反饋',color:'#687d94',note:'展示針具朝向與接觸位置；不提供真人進針深度或角度。'},
 {id:'moxa',name:'灸',verb:'溫熱演示',effect:'溫熱範圍示意',color:'#be7544',note:'暖色表示講解區域，不代表溫度、灸量或安全距離。'},
 {id:'stone',name:'砭石',verb:'按揉演示',effect:'接觸與按揉',color:'#516b65',note:'展示光滑器具的接觸與按揉，不模擬未經證實的能量作用。'},
 {id:'finger',name:'指壓',verb:'按壓演示',effect:'局部壓放與形變',color:'#4d857b',note:'形變與光圈為教學反饋，演示幅度不對應人體按壓力。'},
 {id:'scraper',name:'刮痧',verb:'刮動演示',effect:'刮拭軌跡示意',color:'#6c917d',note:'沿軌跡顯紅，鬆開後保留示意色痕；重播清除。顏色不代表出痧程度或療效。'},
 {id:'cup',name:'火罐',verb:'吸附演示',effect:'杯口接觸與負壓形變',color:'#ac735d',note:'展示皮膚隆起與杯口色痕，鬆開後形變回彈；重播清除色痕。未模擬真實負壓值。'},
];
export const REGIONS:{id:RegionId;name:string;example:string}[]=[
 {id:'head',name:'頭面',example:'ST2'}, {id:'shoulder',name:'肩頸',example:'GB21'},
 {id:'arm',name:'手臂',example:'PC6'}, {id:'abdomen',name:'胸腹',example:'CV12'},
 {id:'waist',name:'腰背',example:'BL23'}, {id:'leg',name:'膝腿足',example:'ST36'},
];
export const SYSTEM_ZH:Record<SystemId,string>={skeletal:'骨骼',muscular:'肌肉',arterial:'動脈',venous:'靜脈',nervous:'神經',digestive:'消化系統',respiratory:'呼吸系統',urinary:'泌尿系統',reproductive:'生殖系統',lymphatic:'淋巴',endocrine:'內分泌',integumentary:'皮膚',connective:'結締組織',sensory:'感覺器官',cardiac:'心臟'};
export const LOCATION:Record<string,{text:string;section:string}>={
 ST36:{text:'小腿外側，犢鼻下 3 寸，犢鼻與解溪連線上。',section:'5.3.36'},
 LI4:{text:'手背，第 2 掌骨橈側的中點處。',section:'5.2.4'},
 PC6:{text:'前臂前區，腕掌側遠端橫紋上 2 寸，掌長肌腱與橈側腕屈肌腱之間。',section:'5.9.6'},
 GB21:{text:'頸後部，第 7 頸椎棘突與肩峰最外側點連線的中點。',section:'5.11.21'},
 CV12:{text:'上腹部，臍中上 4 寸，前正中線上。',section:'5.14.12'},
 BL23:{text:'腰部，第 2 腰椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.23'},
 BL40:{text:'膝後區，膕橫紋中點。',section:'5.7.40'},
 KI1:{text:'足底，屈足卷趾時足心最凹陷中；須結合體位和標準註釋定位。',section:'5.8.1'},
};
export interface SurfaceTarget {position:Vec3;normal:Vec3;meshId?:string;faceIndex?:number|null;pointCode?:string;pointSide?:1|-1}
export type ToolDragPhase='start'|'move'|'end'|'cancel';
export interface SimulationState {showMeridians?:boolean;meridianFilter?:string;pain?:PainSelection;tool:ToolId;release?:number;dragging?:boolean;running:boolean;elapsed:number;amplitude:number;pointCode:string|null;side:1|-1;customTarget:SurfaceTarget|null;focus:number;showPoints:boolean;pickMode:'point'|'pain'|'anatomy';effects:boolean}
export const initialSimulation:SimulationState={showMeridians:true,meridianFilter:'all',tool:'needle',running:false,elapsed:0,amplitude:.55,pointCode:'ST36',side:1,customTarget:null,focus:0,showPoints:true,pickMode:'point',effects:true};
export function pointTarget(code:string,side:1|-1):SurfaceTarget{const p=POINT_BY_CODE.get(code)!;return {position:[p.midline?p.position[0]:p.position[0]*side,p.position[1],p.position[2]],normal:[p.normal[0]*side,p.normal[1],p.normal[2]]};}
export function classifyRegion(p:Vec3):RegionId{return p[1]>1.52?'head':p[1]>1.3?'shoulder':Math.abs(p[0])>.18?'arm':p[1]>.8?(p[2]<-.025?'waist':'abdomen'):'leg';}
