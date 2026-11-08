import type {SurfaceTarget,Vec3} from './teaching';

export type PainTopicId='head'|'neck'|'back'|'knee';
export interface PainSelection {topic:PainTopicId|null;target:SurfaceTarget|null;side:1|-1;level:number;showLinks:boolean}
export interface PainCase {id:PainTopicId;name:string;seed:SurfaceTarget;view:'front'|'back';local:string[];distal:string[];source:string;sourceName:string;scope:string}
/** Source-listed examples, deliberately not an individual treatment prescription. */
export const PAIN_CASES:PainCase[]=[
 {id:'head',name:'頭痛',seed:{position:[.03,1.65,.09],normal:[0,0,1]},view:'front',local:[],distal:['LI4'],source:'https://www.mskcc.org/cancer-care/patient-education/acupressure-pain-and-headaches',sourceName:'MSK：頭痛與合谷指壓資料',scope:'醫院患者教育中的頭痛指壓示例，僅展示合谷的遠端關聯；不泛化到所有頭痛原因。'},
 {id:'neck',name:'頸部痛',seed:{position:[.045,1.49,-.07],normal:[0,0,-1]},view:'back',local:['GB20'],distal:['SI3','TE5','BL60'],source:'https://pmc.ncbi.nlm.nih.gov/articles/PMC33515/',sourceName:'BMJ：慢性頸痛隨機試驗',scope:'慢性頸痛試驗中記錄的部分局部與遠端選穴；研究包含多項干預，不能歸為單穴的獨立療效。'},
 {id:'back',name:'腰痛',seed:{position:[.045,1.095,-.105],normal:[0,0,-1]},view:'back',local:['BL23'],distal:['BL40','BL60','GB34','KI3'],source:'https://pmc.ncbi.nlm.nih.gov/articles/PMC3830844/',sourceName:'慢性腰痛：標準化與個體化針刺試驗',scope:'慢性腰痛試驗方案的部分選穴。本模型只顯示已收錄點，既不是完整研究方案，也不是個人配穴建議。'},
 {id:'knee',name:'膝痛',seed:{position:[.085,.445,.035],normal:[0,0,1]},view:'front',local:['ST35','ST36','SP9','GB34'],distal:['BL60','SP6','KI3'],source:'https://pmc.ncbi.nlm.nih.gov/articles/PMC3782092/',sourceName:'膝骨關節炎：針刺結合物理治療試驗',scope:'限膝骨關節炎研究的選穴示例，不能據點擊位置判斷患有骨關節炎，也不能證明單穴或各種工具具有相同作用。'},
];
export const painCase=(id:PainTopicId|null|undefined)=>PAIN_CASES.find(c=>c.id===id);
export function painSeed(id:PainTopicId,side:1|-1):SurfaceTarget{const s=painCase(id)!.seed;return {position:[s.position[0]*side,s.position[1],s.position[2]],normal:[s.normal[0]*side,s.normal[1],s.normal[2]]};}
/** Coarse surface region matching, not symptom or disease diagnosis. Unknown areas stay unknown. */
export function matchPainRegion([x,y,z]:Vec3):PainTopicId|null{
 if(![x,y,z].every(Number.isFinite))return null;
 if(Math.abs(x)<.13&&y>1.56)return 'head';
 if(Math.abs(x)<.17&&y>=1.4&&y<=1.56&&z<.02)return 'neck';
 if(Math.abs(x)<.18&&y>=.95&&y<=1.22&&z<-.025)return 'back';
 if(Math.abs(x)<.18&&y>=.32&&y<=.55)return 'knee';
 return null;
}
