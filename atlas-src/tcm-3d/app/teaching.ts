import rawMeridians from './meridians.json';
import rawPoints from './teaching-points.json';

export type ToolId='needle'|'finger';
export type Vec3=[number,number,number];
export type RegionId='head'|'shoulder'|'arm'|'abdomen'|'waist'|'leg';
export const MERIDIANS=rawMeridians;
export interface TeachingPoint {meridianId:string;surfaceUnavailable?:boolean;code:string;name:string;pinyin:string;zhuyin:string;meridian:string;position:Vec3;normal:Vec3;region:RegionId;midline:boolean;reviewed:boolean;source:string;positionProvenance:string}
export const POINTS=rawPoints as TeachingPoint[];
export const POINT_BY_CODE=new Map(POINTS.map(p=>[p.code,p]));
export const STANDARD='https://zynj.shutcm.edu.cn/_upload/article/files/66/b4/b34a95604d04b0bf686251b2d317/368ea8b0-187c-48a7-a402-ffdf1e11bb99.pdf';
/** Demo tools kept on the anime assistant: needle + finger pressure only. */
export const TOOLS:{id:ToolId;name:string;verb:string;effect:string;color:string;note:string}[]=[
 {id:'needle',name:'針',verb:'針刺動作演示',effect:'進針動作與局部反饋',color:'#687d94',note:'展示針具朝向與接觸位置；不提供真人進針深度或角度。'},
 {id:'finger',name:'指壓',verb:'按壓演示',effect:'局部壓放與形變',color:'#4d857b',note:'形變與光圈為教學反饋，演示幅度不對應人體按壓力。'},
];
export const REGIONS:{id:RegionId;name:string;example:string}[]=[
 {id:'head',name:'頭面',example:'ST2'}, {id:'shoulder',name:'肩頸',example:'GB21'},
 {id:'arm',name:'手臂',example:'PC6'}, {id:'abdomen',name:'胸腹',example:'CV12'},
 {id:'waist',name:'腰背',example:'BL23'}, {id:'leg',name:'膝腿足',example:'ST36'},
];
/** Classroom lesson sets. Textbook sets first; interim practice sets fill gaps between classes. */
export type CurriculumSetId='head-neck-21'|'trunk-30'|'back-shu-14'|'hip-sacral-6'|'arm-joints-15';
export interface CurriculumSet {id:CurriculumSetId;name:string;shortName:string;lesson:string;codes:readonly string[];status:'reviewed'|'pending'}
export const CURRICULUM_SETS:CurriculumSet[]=[
 {id:'head-neck-21',name:'基準穴 · 頭頸部',shortName:'頭頸 21',lesson:'陸／一 頭頸部基準穴',status:'reviewed',codes:[
  'GV20','GV23','GV24+','GV26','CV24','CV23','CV22','BL2','BL1','LI20','GB14','EX-HN4','ST1','ST2','ST3','ST4','ST9','TE23','EX-HN5','GB1','SI18',
 ]},
 {id:'trunk-30',name:'基準穴 · 頭胸腹背腰',shortName:'頭胸腹背腰 30',lesson:'陸／頭頸 22～33 與胸腹背腰',status:'reviewed',codes:[
  'TE21','SI19','GB2','ST6','TE17','GB21','ST12','GV16','GB20','GV15','BL10','GV14',
  'CV17','KI23','ST17','PC1','SP18','CV14','CV8','KI16','ST25','SP15','GB26',
  'BL14','BL43','SI11','SI9','GV4','BL23','BL52',
 ]},
 {id:'back-shu-14',name:'課堂 · 背部膀胱經俞穴',shortName:'背俞 14',lesson:'口訣：大風肺厥心督膈，肝膽脾胃三焦腎（第 8 胸椎不取穴）。',status:'pending',codes:[
  'BL11','BL12','BL13','BL14','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22','BL23','BL52',
 ]},
 {id:'hip-sacral-6',name:'課堂 · 薦骨臀髖',shortName:'臀髖 6',lesson:'2026-11-22 薦骨四穴與髖關節兩穴',status:'pending',codes:[
  'GV2','BL34','BL30','BL54','GB30','SP12',
 ]},
 {id:'arm-joints-15',name:'課堂 · 肩肘腕關節',shortName:'肩肘腕 15',lesson:'2026-11-22 肩 3、肘 6、腕 6 關節穴',status:'pending',codes:[
  'LI15','TE14','SI10','LU5','PC3','HT3','LI11','TE10','SI8','LU9','PC7','HT7','LI5','TE4','SI5',
 ]},
];
export const CURRICULUM_BY_ID=new Map(CURRICULUM_SETS.map(set=>[set.id,set]));
export const CURRICULUM_CODES=new Set(CURRICULUM_SETS.flatMap(set=>set.codes));
export function curriculumSet(id:string|null|undefined){return id?CURRICULUM_BY_ID.get(id as CurriculumSetId):undefined;}
export function pointInCurriculum(code:string|null|undefined,setId?:string|null){
 if(!code)return false;
 if(setId){const set=curriculumSet(setId);return !!set&&set.codes.includes(code);}
 return CURRICULUM_CODES.has(code);
}
export function curriculumBadge(code:string|null|undefined){
 if(!code)return null;
 const point=POINT_BY_CODE.get(code);
 if(point?.reviewed)return '已校正';
 if(CURRICULUM_CODES.has(code))return '待校正';
 return null;
}

/** No longer deferring collar points — ST9／CV22 reopen with study-body practice outfit (#75). */
export const DEFERRED_PRACTICE_CODES=new Set<string>();
export function isPracticeDeferred(code:string|null|undefined){
 return !!code&&DEFERRED_PRACTICE_CODES.has(code);
}
export function practiceOpenCodes(codes:Iterable<string>){
 return [...codes].filter(code=>!isPracticeDeferred(code));
}
export const LOCATION:Record<string,{text:string;section:string;classic?:string;curriculum?:string}>={
 ST36:{text:'小腿外側，犢鼻下 3 寸，犢鼻與解溪連線上。',section:'5.3.36'},
 LI4:{text:'手背，第 2 掌骨橈側的中點處。',section:'5.2.4'},
 PC6:{text:'前臂前區，腕掌側遠端橫紋上 2 寸，掌長肌腱與橈側腕屈肌腱之間。',section:'5.9.6'},
 GB21:{text:'頸後部，第 7 頸椎棘突與肩峰最外側點連線的中點。',section:'5.11.21',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 CV12:{text:'上腹部，臍中上 4 寸，前正中線上。',section:'5.14.12'},
 BL23:{text:'腰部，第 2 腰椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.23',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL40:{text:'膝後區，膕橫紋中點。',section:'5.7.40'},
 KI1:{text:'足底，屈足卷趾時足心最凹陷中；須結合體位和標準註釋定位。',section:'5.8.1'},
 GV20:{text:'頭頂正中稍後。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·海論》說髓海之輸「上在於其蓋」，蓋即巔頂，後世對到百會。《針灸甲乙經》：前頂後一寸五分，頂中央旋毛中。'},
 GV23:{text:'入前髮際 1 寸。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：顱上直鼻中央，入髮際一寸陷者中。'},
 'GV24+':{text:'兩眉頭之中央。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'教材作經外奇穴；GB/T 12346—2021 編入督脈為印堂。《甲乙經》未收。元代《扁鵲神應針灸玉龍經》〈玉龍歌〉已用。'},
 GV26:{text:'人中溝上 1/3。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：鼻柱下人中。'},
 CV24:{text:'下唇下方、頦唇溝（下唇正中凹陷）中央。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·經脈》胃經「下交承漿」。《針灸甲乙經》：頤前唇之下。'},
 CV23:{text:'喉結上方、舌骨附近。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：頜下，結喉上。《素問·刺瘧》「舌下兩脈者，廉泉也」是舌下的脈，不是這一處。教材約結喉上 3 分（約食指厚度）。'},
 CV22:{text:'胸骨上窩中央。練習裝露出真實皮膚後再演示針／指壓。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：頸結喉下五寸，宛宛中。一名玉戶。'},
 BL2:{text:'眉頭內側凹陷。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：眉頭陷者中。'},
 BL1:{text:'眼內角斜外約 1 分。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·經脈》膀胱經起於目內眥。《針灸甲乙經》：目內眥外。一名淚孔。'},
 LI20:{text:'鼻孔旁 5 分，鼻唇溝上。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：鼻孔旁。'},
 GB14:{text:'瞳孔直上，眉毛中央上方約 1 寸。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：眉上一寸，直瞳子。'},
 'EX-HN4':{text:'眉毛中央。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'經外奇穴。穴名定得較晚，清以後的奇穴書才固定，不見於《內經》與《甲乙經》。'},
 ST1:{text:'瞳孔直下，眼球與眶下緣之間。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·經脈》胃經起於鼻，後世以承泣為起穴。《針灸甲乙經》：目下七分，直瞳子。'},
 ST2:{text:'瞳孔直下，眶下緣下約 1 寸。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：目下一寸。'},
 ST3:{text:'鼻孔旁 8 分，瞳孔直下。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：俠鼻孔旁八分，直瞳子。'},
 ST4:{text:'嘴角外側約 0.4 寸。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·經脈》胃經「還出挾口環唇」。《針灸甲乙經》：俠口旁四分。'},
 ST9:{text:'與喉結上緣平齊、旁開約 1.5 寸，胸鎖乳突肌前緣（頸動脈搏動處）。練習裝露出頸側皮膚後再演示。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《靈樞·寒熱病》：頸側之動脈人迎，在嬰筋之前。《針灸甲乙經》：頸大脈動應手，俠結喉。'},
 TE23:{text:'眉毛外端凹陷。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：眉後陷者中。'},
 'EX-HN5':{text:'眉梢與外眼角之間，向後的凹陷。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'經外奇穴。眼科書與《針灸大成》有這一處。《甲乙經》把「太陽」當作瞳子髎的別名，那個是外眼角旁五分，不是這裡。'},
 GB1:{text:'外眼角旁約 0.5 寸。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：目外去眥五分。一名太陽，一名前關。這裡的太陽是別名，不是顳側的奇穴太陽。'},
 SI18:{text:'顴骨下緣，外眼角直下。',section:'',curriculum:'陸／一 頭頸部基準穴',classic:'《針灸甲乙經》：面頄骨下廉，銳骨端陷者中。'},
 TE21:{text:'耳屏上切跡前方，張口凹陷處。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 SI19:{text:'耳屏與下頜關節之間，張口凹陷處。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GB2:{text:'耳屏間切跡前方，張口凹陷處。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 ST6:{text:'下頜角前方咬肌隆起處。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 TE17:{text:'耳垂後方，乳突與下頜角之間凹陷。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 ST12:{text:'鎖骨上窩中央、鎖骨上方；乳中線（前正中線旁開約 4 寸）。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GV16:{text:'後髮際正中直上 1 寸，枕外隆凸直下凹陷。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GB20:{text:'枕骨下，胸鎖乳突肌與斜方肌之間凹陷。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GV15:{text:'後髮際正中直上 0.5 寸（講義作瘂門，目錄作啞門，同一穴）。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL10:{text:'啞門旁開 1.3 寸，斜方肌外緣凹陷。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GV14:{text:'第 7 頸椎棘突下凹陷；低頭時該椎可移動以協助取穴。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 CV17:{text:'前正中線，兩乳連線中點（胸骨中線）。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 KI23:{text:'第 4 肋間，前正中線旁開 2 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 ST17:{text:'第 4 肋間，乳頭中央。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 PC1:{text:'第 4 肋間，前正中線旁開 5 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 SP18:{text:'第 4 肋間，前正中線旁開 6 寸（講義作天谿，目錄作天溪）。乳中向外 2 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 CV14:{text:'前正中線，臍中上 6 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 CV8:{text:'臍中。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 KI16:{text:'臍中旁開 0.5 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 ST25:{text:'臍中旁開 2 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 SP15:{text:'臍中旁開 4 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GB26:{text:'第 11 肋游離端下方，與臍相平。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL14:{text:'第 4 胸椎棘突下，後正中線旁開 1.5 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL43:{text:'第 4 胸椎棘突下，後正中線旁開 3 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 SI11:{text:'肩胛岡中點與肩胛下角連線上 1/3 與下 2/3 交點。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 SI9:{text:'腋後紋頭上 1 寸（肩後／腋後）。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 GV4:{text:'第 2 腰椎棘突下，與臍相平。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL52:{text:'第 2 腰椎棘突下，後正中線旁開 3 寸。',section:'',curriculum:'陸／頭頸 22～33 與胸腹背腰'},
 BL11:{text:'第 1 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.11'},
 BL12:{text:'第 2 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.12'},
 BL13:{text:'第 3 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.13'},
 BL15:{text:'第 5 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.15'},
 BL16:{text:'第 6 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.16'},
 BL17:{text:'第 7 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.17'},
 BL18:{text:'第 9 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.18'},
 BL19:{text:'第 10 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.19'},
 BL20:{text:'第 11 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.20'},
 BL21:{text:'第 12 胸椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.21'},
 BL22:{text:'第 1 腰椎棘突下，後正中線旁開 1.5 寸。',section:'5.7.22'},
 GV2:{text:'薦骨正中，左右第四骶骨孔連線中點（骶管裂孔）。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 BL34:{text:'第四骶后孔（骶骨孔）。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 BL30:{text:'第四骶椎棘突下，後正中線旁開 1.5 寸（與腰俞同高）。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 BL54:{text:'與腰俞同高，後正中線旁開 3 寸（白環俞再旁開 1.5 寸；與志室同側線）。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 GB30:{text:'側臥屈膝、腳跟抵臀時髖部；站姿示意向臀部外下與股骨頸外側之間。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 SP12:{text:'腹股溝外側，股動脈搏動處稍外側。',section:'',curriculum:'2026-11-22 薦骨四穴與髖關節兩穴'},
 LI15:{text:'手臂往前平舉時肩關節前面的凹陷；站姿示意向肩峰前方換算。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 TE14:{text:'手臂往外平舉時肩膀外端後側的凹陷；站姿示意向肩峰後外側換算。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 SI10:{text:'從腋後皺紋上端往上推到肩胛岡下緣、稍偏外；站姿示意向肩胛岡下後外。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 LU5:{text:'肘橫紋上，肘窩大筋（肱二頭肌腱）外側（肺經）。屈肘找橫紋再換算站姿皮膚。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 PC3:{text:'肘橫紋上，大筋內側（心包經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 HT3:{text:'屈肘後肘橫紋內側端盡頭（心經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 LI11:{text:'屈肘後肘橫紋外側端與肱骨外上髁之間（大腸經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 TE10:{text:'屈肘約 45 度，肘尖（尺骨鷹嘴）往上 1 寸凹陷（三焦經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 SI8:{text:'屈肘時肘尖與內上髁之間、尺神經溝一帶凹陷（小腸經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 LU9:{text:'腕橫紋橈側端，橈動脈橈側（肺經）。掌側由拇指往小指：肺、心包、心。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 PC7:{text:'掌側腕橫紋正中（心包經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 HT7:{text:'腕橫紋尺側，豌豆骨旁凹陷（心經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 LI5:{text:'拇指上翹時手背腕部兩筋之間（大腸經；講義作陽谿）。手背：大腸、三焦、小腸。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 TE4:{text:'手背腕橫紋正中（三焦經）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
 SI5:{text:'手背腕橫紋尺側凹陷（小腸經；講義作陽谷）。',section:'',curriculum:'2026-11-22 肩肘腕關節 15 穴'},
};
export interface SurfaceTarget {position:Vec3;normal:Vec3;meshId?:string;faceIndex?:number|null;pointCode?:string;pointSide?:1|-1}
export type ToolDragPhase='start'|'move'|'end'|'cancel';
export interface SimulationState {showMeridians?:boolean;meridianFilter?:string;curriculumSet?:CurriculumSetId|'all'|null;tool:ToolId;release?:number;dragging?:boolean;running:boolean;elapsed:number;amplitude:number;pointCode:string|null;side:1|-1;customTarget:SurfaceTarget|null;focus:number;showPoints:boolean;pickMode:'point';effects:boolean}
export const initialSimulation:SimulationState={showMeridians:false,meridianFilter:'all',curriculumSet:'head-neck-21',tool:'needle',running:false,elapsed:0,amplitude:.55,pointCode:'GV24+',side:1,customTarget:null,focus:0,showPoints:true,pickMode:'point',effects:true};
export function pointTarget(code:string,side:1|-1):SurfaceTarget{const p=POINT_BY_CODE.get(code)!;return {position:[p.midline?p.position[0]:p.position[0]*side,p.position[1],p.position[2]],normal:[p.normal[0]*side,p.normal[1],p.normal[2]]};}
export function classifyRegion(p:Vec3):RegionId{return p[1]>1.52?'head':p[1]>1.3?'shoulder':Math.abs(p[0])>.18?'arm':p[1]>.8?(p[2]<-.025?'waist':'abdomen'):'leg';}

const ZHUYIN_TONE_MARKS=/[\u02CA\u02C7\u02CB\u02D9]/g;
const SEARCH_WS=/[\s\-_]+/g;
export const ZHUYIN_PATTERN=/^[\u3105-\u3129\u02CA\u02C7\u02CB\u02D9 ]+$/;

/** Strip zhuyin spaces and tone marks for prefix / bare-syllable search. */
export function stripZhuyin(value:string){return value.replace(SEARCH_WS,'').replace(ZHUYIN_TONE_MARKS,'');}
export function normalizePointCode(value:string){return value.trim().toLowerCase().replace(SEARCH_WS,'');}

/** Lesson list stays on baked points. A typed query searches the full 364-point catalog. */
export function libraryPointResults(query:string,region:RegionId|'all',meridianFilter:string|null|undefined,curriculumSetId:string|null|undefined,avatarCodes:ReadonlySet<string>){
 const trimmed=query.trim();
 const lesson=trimmed||curriculumSetId==='all'?undefined:curriculumSet(curriculumSetId);
 const ordered=trimmed?POINTS:(lesson?lesson.codes.map(code=>POINTS.find(p=>p.code===code)!).filter(Boolean):POINTS.filter(p=>avatarCodes.has(p.code)));
 return ordered.filter(p=>(trimmed||avatarCodes.has(p.code))&&(!meridianFilter||meridianFilter==='all'||p.meridianId===meridianFilter)&&(region==='all'||p.region===region)&&teachingPointMatchesQuery(p,query));
}

export function teachingPointMatchesQuery(point:Pick<TeachingPoint,'code'|'name'|'zhuyin'>,rawQuery:string){
 const query=rawQuery.trim();
 if(!query)return true;
 const code=normalizePointCode(point.code);
 const codeQ=normalizePointCode(query);
 if(codeQ&&code.includes(codeQ))return true;
 if(point.name.includes(query))return true;
 if(!point.zhuyin)return false;
 const compactQuery=query.replace(SEARCH_WS,'');
 const compactZhuyin=point.zhuyin.replace(SEARCH_WS,'');
 if(compactQuery&&compactZhuyin.includes(compactQuery))return true;
 const bareQuery=stripZhuyin(query);
 const bareZhuyin=stripZhuyin(point.zhuyin);
 if(bareQuery&&bareZhuyin.includes(bareQuery))return true;
 return false;
}
