#!/usr/bin/env node
/**
 * Bake / merge surface points onto public/models/anime-assistant.glb.
 *
 * Weekly add-point workflow:
 * 1. Add seed coordinates for the week's codes in `seeds` below (or keep using existing).
 * 2. Run: node scripts/bake-anime-points.mjs --codes GV16,GB20,BL10
 * 3. Confirm app/anime-points.json merged (did not shrink); hand-tune if needed.
 * 4. Library UI lists only baked points automatically.
 *
 * Without --codes, re-bakes all defined seeds and merges into the existing JSON
 * (never clears points that lack a seed this run).
 *
 * Flags: --codes A,B,C   --dry-run
 */
import {readFileSync,writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import {dirname,join} from 'node:path';
import * as T from 'three';
import {GLTFLoader} from 'three/examples/jsm/loaders/GLTFLoader.js';

globalThis.self=globalThis;
globalThis.URL=URL;
globalThis.webkitURL=URL;
if(!globalThis.document)globalThis.document={createElementNS:()=>({style:{},setAttribute(){},getContext(){return null;}})};

const rootDir=join(dirname(fileURLToPath(import.meta.url)),'..');
const args=process.argv.slice(2);
const dryRun=args.includes('--dry-run');
const codesArg=args.find(a=>a.startsWith('--codes='))??(args.includes('--codes')?args[args.indexOf('--codes')+1]:null);
const codesFilter=codesArg?codesArg.split(',').map(s=>s.trim()).filter(Boolean):null;

const teaching=JSON.parse(readFileSync(join(rootDir,'app/teaching-points.json'),'utf8'));
const teachingByCode=new Map((Array.isArray(teaching)?teaching:teaching.points||[]).map(p=>[p.code,p]));
// teaching-points.json is a bare array in this repo
const catalog=Array.isArray(teaching)?teaching:teaching.points||[];
for(const p of catalog)teachingByCode.set(p.code,p);

const existing=JSON.parse(readFileSync(join(rootDir,'app/anime-points.json'),'utf8'));
const byCode=new Map((existing.points||[]).map(p=>[p.code,{...p}]));

const buffer=readFileSync(join(rootDir,'public/models/anime-assistant.glb')).buffer;
const gltf=await new Promise((res,rej)=>new GLTFLoader().parse(buffer,'',res,rej));
const scene=gltf.scene;
scene.updateMatrixWorld(true);
const wp=name=>{const o=scene.getObjectByName(name);const v=new T.Vector3();o.getWorldPosition(v);return v;};
const head=wp('J_Bip_C_Head'),neck=wp('J_Bip_C_Neck'),eyeL=wp('J_Adj_L_FaceEye'),eyeR=wp('J_Adj_R_FaceEye');
const shoulderL=wp('J_Bip_L_Shoulder');
const eyeMid=eyeL.clone().add(eyeR).multiplyScalar(.5);
const CUN_LATERAL=.055/4;
const pupilLineX=.0298;
const pupilX=pupilLineX;
const outerCanthusX=.055;
const projectMeshes=[];
scene.traverse(o=>{
 if(!o.isMesh)return;
 const mats=Array.isArray(o.material)?o.material:[o.material];
 const n=mats[0]?.name||'';
 if(/Face_00_SKIN|Body_00_SKIN|FaceMouth/i.test(n))projectMeshes.push(o);
});
const snap=(seed,preferNormal)=>{
 const n=preferNormal.clone().normalize();
 const ray=new T.Raycaster();
 ray.set(seed.clone().addScaledVector(n,.08),n.clone().negate());ray.far=.2;
 let hits=ray.intersectObjects(projectMeshes,false);
 if(!hits[0]){ray.set(seed.clone().addScaledVector(n,.16),n.clone().negate());ray.far=.35;hits=ray.intersectObjects(projectMeshes,false);}
 if(!hits[0]){ray.set(seed.clone().add(new T.Vector3(0,0,-.12)),new T.Vector3(0,0,1));ray.far=.25;hits=ray.intersectObjects(projectMeshes,false);}
 if(!hits[0]){ray.set(seed.clone().add(new T.Vector3(0,0,.12)),new T.Vector3(0,0,-1));ray.far=.25;hits=ray.intersectObjects(projectMeshes,false);}
 if(!hits[0])return null;
 let outward=(hits[0].face?.normal.clone()??n).transformDirection(hits[0].object.matrixWorld);
 if(outward.dot(n)<0)outward.negate();
 return {position:hits[0].point.toArray().map(v=>+v.toFixed(5)),normal:outward.normalize().toArray().map(v=>+v.toFixed(5)),meshId:hits[0].object.name||'anime-skin'};
};
const L=-1;
/** Relative head/neck/body bone seeds. Add new weekly codes here before --codes bake. */
const seeds={
 GV20:[0,head.y+.155,head.z+.025],GV23:[0,eyeMid.y+.105,eyeMid.z-.055],'GV24+':[0,eyeMid.y+.028,eyeMid.z-.02],
 GV26:[0,eyeMid.y-.055,eyeMid.z-.045],
 // 承漿：下唇下頦唇溝中央（勿再落在下頦下方）。
 CV24:[0,eyeMid.y-.072,eyeMid.z-.068],
 // 廉泉：喉結上、舌骨上緣凹陷（勿贴近頸根）。
 CV23:[0,neck.y+.022,neck.z-.038],
 // 天突：胸骨上窩中央（兩鎖骨內側端之間），勿落鎖骨下上胸。
 CV22:[0,neck.y-.008,neck.z-.028],BL2:[.018*L,eyeMid.y+.03,eyeMid.z-.02],BL1:[.018*L,eyeMid.y+.005,eyeMid.z-.015],
 LI20:[.018*L,eyeMid.y-.04,eyeMid.z-.04],GB14:[pupilX*L,eyeMid.y+.055,eyeMid.z-.02],'EX-HN4':[pupilX*L,eyeMid.y+.036,eyeMid.z-.03],
 ST1:[pupilX*L,eyeMid.y-.02,eyeMid.z-.02],ST2:[pupilX*L,eyeMid.y-.035,eyeMid.z-.03],ST3:[pupilX*L,eyeMid.y-.055,eyeMid.z-.035],
 ST4:[pupilX*L,eyeMid.y-.075,eyeMid.z-.025],
 // 人迎：結喉上緣旁的頸前緣（前頸皮膚在此高度只到約 x=0.017，再外就是側極）。法線朝前，勿把種子放到頸外側。
 ST9:[.017*L,neck.y+.013,neck.z-.05],TE23:[outerCanthusX*L,eyeMid.y+.02,eyeMid.z+.005],
 'EX-HN5':[.06*L,eyeMid.y+.01,eyeMid.z+.02],GB1:[(outerCanthusX+CUN_LATERAL*.5)*L,eyeMid.y-.005,eyeMid.z-.08],SI18:[outerCanthusX*L,eyeMid.y-.05,eyeMid.z+.01],
 // Weekly trunk／head-neck 30 (#75). Teaching catalog coords → anime skin snap.
 TE21:[.07*L,eyeMid.y+.005,eyeMid.z+.015],SI19:[.065*L,eyeMid.y,eyeMid.z+.02],GB2:[.065*L,eyeMid.y-.02,eyeMid.z+.015],
 ST6:[.055*L,eyeMid.y-.06,eyeMid.z-.005],TE17:[.065*L,eyeMid.y-.025,eyeMid.z+.05],
 GB21:[.1*L,neck.y-.02,neck.z+.02],
 // 缺盆：肩骨略上方、鎖骨前坡之後的上窩底（法線朝上）。勿把 Y 壓在肩骨高度，否則仍停在鎖骨前坡，左右吸附會一側滑到鎖骨下。
 // 缺盆與乳中同 x（旁開四寸線，以 ST17 為準）。
 ST12:[.055*L,shoulderL.y+.015,neck.z-.038],
 GV16:[0,head.y+.01,head.z+.1],GB20:[.028*L,head.y+.01,.09],GV15:[0,neck.y+.04,neck.z+.095],
 BL10:[.022*L,neck.y+.036,neck.z+.095],
 // 大椎：C7 棘突下、約肩線；BL14／BL43（T4）與 SI11 在其下方。
 GV14:[0,1.31,.08],
 // 背部第一側線（旁開 1.5 寸）：T1～T7、跳過 T8、T9～L2；Y 依 BL14／BL23 錨點與椎序插值。
 BL11:[.03*L,1.297,.09],BL12:[.03*L,1.284,.09],BL13:[.03*L,1.271,.09],
 // 第 4 肋間：乳頭高度 y≈1.195、x≈0.055；ST17 在乳頭，KI23 正中旁開，PC1／SP18 再外。
 CV17:[0,1.195,-.12],KI23:[CUN_LATERAL*2*L,1.195,-.12],ST17:[.055*L,1.195,-.13],PC1:[CUN_LATERAL*5*L,1.195,-.11],SP18:[CUN_LATERAL*6*L,1.195,-.09],
 CV14:[0,1.15,-.1],CV8:[0,1.02,-.1],KI16:[CUN_LATERAL*.5*L,1.02,-.1],ST25:[CUN_LATERAL*2*L,1.02,-.1],SP15:[.055*L,1.02,-.08],GB26:[.12*L,1.02,-.04],
 BL14:[.03*L,1.259,.09],BL15:[.03*L,1.235,.09],BL16:[.03*L,1.211,.09],BL17:[.03*L,1.187,.09],
 BL18:[.03*L,1.139,.09],BL19:[.03*L,1.115,.09],BL20:[.03*L,1.091,.09],BL21:[.03*L,1.067,.09],BL22:[.03*L,1.043,.09],
 BL43:[.055*L,1.259,.095],
 // 天宗：與 T4 列同高（天宗／膏肓／厥陰俞）
 SI11:[.085*L,1.259,.095],SI9:[.15*L,1.31,.05],
 GV4:[0,1.02,.11],BL23:[.03*L,1.02,.1],BL52:[.055*L,1.02,.1],
};
const midline=new Set(['GV20','GV23','GV24+','GV26','CV24','CV23','CV22','GV16','GV15','GV14','CV17','CV14','CV8','GV4']);
const backShuSet=new Set(['BL11','BL12','BL13','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22']);
const trunkSet=new Set(['TE21','SI19','GB2','ST6','TE17','GB21','ST12','GV16','GB20','GV15','BL10','GV14','CV17','KI23','ST17','PC1','SP18','CV14','CV8','KI16','ST25','SP15','GB26','BL14','BL43','SI11','SI9','GV4','BL23','BL52']);

const preferFor=(code,arr)=>{
 // Hand overrides where catalog normals misfire on the anime silhouette.
 if(code==='GB20')return new T.Vector3(arr[0]*.35,-.2,1).normalize();
 if(code==='GB26')return new T.Vector3(arr[0],0,-.15).normalize();
 if(code==='GB1')return new T.Vector3(arr[0],0,-1).normalize();
 if(code==='CV22')return new T.Vector3(0,0,-1);
 if(code==='ST9')return new T.Vector3(0,-.05,-1).normalize();
 if(code==='ST12')return new T.Vector3(0,1,-.15).normalize();
 if(code==='GV14')return new T.Vector3(0,0,1);
 if(code==='SI11')return new T.Vector3(arr[0]*.25,-.05,1).normalize();
 const catalog=teachingByCode.get(code);
 if(catalog?.normal?.length===3){
  // Catalog: +X anatomical left, +Z front. Anime seeds: L=-1 world X, −Z front.
  const n=new T.Vector3(catalog.normal[0]*L,catalog.normal[1],-catalog.normal[2]);
  if(midline.has(code))n.x=0;
  if(n.lengthSq()>.01)return n.normalize();
 }
 if(midline.has(code))return new T.Vector3(0,code==='GV20'?1:0,arr[2]>=0?1:-1).normalize();
 return new T.Vector3(arr[0],0,arr[2]>=0?1:-.6).normalize();
};

const targetCodes=codesFilter??Object.keys(seeds);
const missing=targetCodes.filter(code=>!(code in seeds));
if(missing.length){
 console.error(`缺少種子座標（請先在 bake-anime-points.mjs 的 seeds 補上）：${missing.join(',')}`);
 process.exit(1);
}

const updated=[];
for(const code of targetCodes){
 const arr=seeds[code];
 const seed=new T.Vector3(...arr);
 const prefer=preferFor(code,arr);
 const hit=snap(seed,prefer)??{position:arr.map(v=>+v.toFixed(5)),normal:prefer.toArray().map(v=>+v.toFixed(5)),meshId:'anime-skin'};
 const pos=hit.position.slice(),nor=hit.normal.slice();
 if(!midline.has(code)){pos[0]=Math.abs(pos[0]);nor[0]=Math.abs(nor[0]);}
 else {pos[0]=0;}
 const catalogPoint=teachingByCode.get(code);
 const name=catalogPoint?.name??byCode.get(code)?.name??code;
 const defaultSet=backShuSet.has(code)?'back-shu-14':trunkSet.has(code)?'trunk-30':'head-neck-21';
 const entry={code,name,midline:midline.has(code)||!!catalogPoint?.midline,position:pos,normal:nor,reviewed:true,set:byCode.get(code)?.set??defaultSet};
 byCode.set(code,entry);
 updated.push(code);
}

/** 沿皮膚重吸附：直線組只改 x、水平組只改 y（共線校正）。 */
const realignGroup=(codes,axis,anchor,fixed)=>{
 const idx=axis==='x'?0:axis==='y'?1:2;
 const ref=byCode.get(anchor);
 if(!ref)return;
 const refVal=fixed?.fixedX??(fixed?.fixedY??(ref.midline&&axis==='x'?0:(axis==='x'?Math.abs(ref.position[idx]):ref.position[idx])));
 for(const code of codes){
  if(code===anchor&&!(fixed&&(fixed.fixedX!=null||fixed.fixedY!=null)))continue;
  const e=byCode.get(code);
  if(!e)continue;
  const pos=e.position.slice();
  if(axis==='x')pos[0]=e.midline?0:refVal;
  else pos[idx]=refVal;
  const seed=new T.Vector3(...pos);
  const hit=snap(seed,preferFor(code,pos));
  if(!hit)continue;
  const np=hit.position.slice(),nor=hit.normal.slice();
  if(!midline.has(code)){np[0]=Math.abs(np[0]);nor[0]=Math.abs(nor[0]);}
  else{np[0]=0;}
  if(axis==='x'&&!e.midline)np[0]=refVal;
  else if(axis==='y')np[1]=refVal;
  byCode.set(code,{...e,position:np,normal:nor});
 }
};
if(!codesFilter) for(const row of [
  ['x',['ST12','ST17','SP15'],'ST17'],
 ['x',['KI23','ST25'],'ST25'],
 ['x',['BL11','BL12','BL13','BL14','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22','BL23'],'BL14'],
 ['x',['BL43','BL52'],'BL43'],
 ['x',['GB14','EX-HN4','ST1','ST2','ST3','ST4'],'ST1',{fixedX:pupilLineX}],
 ['x',['BL1','BL2'],'BL1'],
 ['x',['TE23','SI18'],'SI18'],
 ['y',['CV8','KI16','ST25','SP15','GB26'],'CV8'],
 ['y',['GV4','BL23','BL52'],'GV4'],
 ['y',['SI11','BL43','BL14'],'BL14'],
 ['y',['CV17','KI23','ST17','PC1','SP18'],'ST17'],
 ['y',['GV16','GB20'],'GV16'],
 ['y',['GV15','BL10'],'GV15'],
]){
 const [axis,codes,anchor,fixed]=row;
 realignGroup(codes,axis,anchor,fixed);
}

if(!codesFilter){
const si18=byCode.get('SI18'),gb1=byCode.get('GB1');
if(si18&&gb1){
 const pos=gb1.position.slice();
 pos[0]=si18.position[0]+CUN_LATERAL*.5;
 const hit=snap(new T.Vector3(...pos),preferFor('GB1',pos));
 if(hit){
  const targetX=si18.position[0]+CUN_LATERAL*.5;
  const np=hit.position.slice(),nor=hit.normal.slice();
  np[0]=targetX;nor[0]=Math.abs(nor[0]);
  byCode.set('GB1',{...gb1,position:np,normal:nor});
 }
}
}

if(codesFilter){
 const blLine=['BL11','BL12','BL13','BL14','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22','BL23'];
 realignGroup(blLine,'x','BL14');
 for(const code of blLine){
  if(!(code in seeds))continue;
  const e=byCode.get(code);
  if(!e)continue;
  const seed=new T.Vector3(...e.position);
  const hit=snap(seed,preferFor(code,e.position));
  if(!hit)continue;
  const np=hit.position.slice(),nor=hit.normal.slice();
  np[0]=e.position[0];nor[0]=Math.abs(nor[0]);
  byCode.set(code,{...e,position:np,normal:nor});
 }
}

const points=[...byCode.values()].sort((a,b)=>a.code.localeCompare(b.code));
const payload={
 model:'anime-assistant.glb',character:'Sendagaya Shino',license:'CC0 1.0 (VRoid Studio beta sample)',set:'head-neck-21+trunk-30+back-shu-14',
 faceAxis:'-Z',sideConvention:'position.x>0 means anatomical left; anime runtime flips X to -X for left',
 note:'穴位依解剖比例與臉部／軀幹骨骼標誌吸附到角色皮膚；教學示意，非臨床取穴。合併寫入，不會清空既有穴。',
 points,
};

if(dryRun){
 console.log(`dry-run merge: would update ${updated.join(',')} ; total points ${points.length} (existing kept)`);
 process.exit(0);
}

writeFileSync(join(rootDir,'app/anime-points.json'),JSON.stringify(payload,null,2)+'\n');
console.log(`merged ${updated.length} point(s) [${updated.join(',')}]; total ${points.length}`);
