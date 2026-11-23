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
 *        --model relpath (default public/models/anime-assistant.glb)
 *        --output relpath (default app/anime-points.json)
 */
import {existsSync,readFileSync,writeFileSync} from 'node:fs';
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
const argVal=flag=>{
 const eq=args.find(a=>a.startsWith(`--${flag}=`));
 if(eq)return eq.slice(flag.length+3);
 const i=args.indexOf(`--${flag}`);
 return i>=0?args[i+1]:null;
};
const modelRel=argVal('model')??'public/models/anime-assistant.glb';
const outputRel=argVal('output')??'app/anime-points.json';
const outputPath=join(rootDir,outputRel);
const isAdultBake=modelRel.includes('anime-adult');

const teaching=JSON.parse(readFileSync(join(rootDir,'app/teaching-points.json'),'utf8'));
const teachingByCode=new Map((Array.isArray(teaching)?teaching:teaching.points||[]).map(p=>[p.code,p]));
// teaching-points.json is a bare array in this repo
const catalog=Array.isArray(teaching)?teaching:teaching.points||[];
for(const p of catalog)teachingByCode.set(p.code,p);

const existing=existsSync(outputPath)
 ?JSON.parse(readFileSync(outputPath,'utf8'))
 :{points:[]};
const byCode=new Map((existing.points||[]).map(p=>[p.code,{...p}]));

const buffer=readFileSync(join(rootDir,modelRel)).buffer;
const gltf=await new Promise((res,rej)=>new GLTFLoader().parse(buffer,'',res,rej));
const scene=gltf.scene;
scene.updateMatrixWorld(true);
const wp=name=>{const o=scene.getObjectByName(name);const v=new T.Vector3();o.getWorldPosition(v);return v;};
const head=wp('J_Bip_C_Head'),neck=wp('J_Bip_C_Neck'),eyeL=wp('J_Adj_L_FaceEye'),eyeR=wp('J_Adj_R_FaceEye');
const shoulderL=wp('J_Bip_L_Shoulder');
const upperArmL=wp('J_Bip_L_UpperArm');
const lowerArmL=wp('J_Bip_L_LowerArm');
const handL=wp('J_Bip_L_Hand');
const elbowX=Math.abs(lowerArmL.x);
const elbowY=(upperArmL.y+lowerArmL.y)/2;
const wristX=Math.abs(handL.x);
const wristY=handL.y;
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
 SI11:[.085*L,1.259,.095],SI9:[.11*L,1.31,.085],
 GV4:[0,1.02,.11],BL23:[.03*L,1.02,.1],BL52:[.055*L,1.02,.1],
 // 臀髖 6（2026-11-22）：薦骨四穴在命門／腎俞下方；環跳為站姿臀外下（側臥屈膝腳跟抵臀換算）。
 GV2:[0,0.905,.095],BL34:[.015*L,0.905,.092],BL30:[.03*L,0.905,.095],BL54:[.055*L,0.905,.095],
 GB30:[.105*L,0.915,.045],SP12:[.055*L,0.885,-.11],
 // 肩肘腕 15（2026-11-22）：T 字站姿手臂沿 X（近端肩→遠端指），橈尺為 Z（橈側較 −Z）。
 // 肘／腕橫紋穴共用關節 X；橈尺分開走 Z。掌側肺／心包／心；手背大腸／三焦／小腸。
 LI15:[(.08)*L,shoulderL.y-.005,shoulderL.z-.04],
 TE14:[(.12)*L,shoulderL.y,shoulderL.z+.06],
 // 臑俞：靠肩胛／天宗量級 X，Z 朝後（腋後皺襞上、肩胛岡下）；勿沿上臂 X 外推。
 SI10:[.095*L,1.252,.105],
 LU5:[elbowX*L,elbowY,lowerArmL.z-.048],
 PC3:[elbowX*L,elbowY-.004,lowerArmL.z-.012],
 HT3:[elbowX*L,elbowY-.004,lowerArmL.z+.032],
 LI11:[elbowX*L,elbowY,lowerArmL.z+.028],
 TE10:[(elbowX-.018)*L,elbowY+.015,lowerArmL.z+.017],
 SI8:[elbowX*L,elbowY-.002,lowerArmL.z+.038],
 LU9:[wristX*L,wristY,handL.z-.046],
 PC7:[wristX*L,wristY,handL.z-.038],
 HT7:[wristX*L,wristY,handL.z-.022],
 LI5:[wristX*L,wristY,handL.z+.032],
 TE4:[wristX*L,wristY,handL.z+.018],
 SI5:[wristX*L,wristY,handL.z+.006],
};
const midline=new Set(['GV20','GV23','GV24+','GV26','CV24','CV23','CV22','GV16','GV15','GV14','CV17','CV14','CV8','GV4','GV2']);
const hipSacralSet=new Set(['GV2','BL34','BL30','BL54','GB30','SP12']);
const armJointsSet=new Set(['LI15','TE14','SI10','LU5','PC3','HT3','LI11','TE10','SI8','LU9','PC7','HT7','LI5','TE4','SI5']);
const backShuSet=new Set(['BL11','BL12','BL13','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22']);
const trunkSet=new Set(['TE21','SI19','GB2','ST6','TE17','GB21','ST12','GV16','GB20','GV15','BL10','GV14','CV17','KI23','ST17','PC1','SP18','CV14','CV8','KI16','ST25','SP15','GB26','BL14','BL43','SI11','SI9','GV4','BL23','BL52']);

/** Midline bake: x=0 and rear-facing normal so runtime snap does not slide off the spine. */
const finalizeMidlineHit=(code,pos,nor)=>{
 if(!midline.has(code))return {pos,nor};
 let p=pos.slice(),n=nor.slice();
 p[0]=0;
 if(Math.abs(n[0])>.12){
  const rear=new T.Vector3(0,0,1);
  const hit=snap(new T.Vector3(p[0],p[1],p[2]),rear);
  if(hit){p=hit.position.slice();n=hit.normal.slice();p[0]=0;}
 }
 n[0]=0;
 const len=Math.hypot(n[1],n[2]);
 if(len>1e-6)n=[0,n[1]/len,n[2]/len];
 else n=[0,0,1];
 return {pos:p,nor:n};
};

const preferFor=(code,arr)=>{
 // Hand overrides where catalog normals misfire on the anime silhouette.
 if(code==='GB20')return new T.Vector3(arr[0]*.35,-.2,1).normalize();
 if(code==='GB26')return new T.Vector3(arr[0],0,-.15).normalize();
 if(code==='GB1')return new T.Vector3(arr[0],0,-1).normalize();
 if(code==='CV22')return new T.Vector3(0,0,-1);
 if(code==='ST9')return new T.Vector3(0,-.05,-1).normalize();
 if(code==='ST12')return new T.Vector3(0,1,-.15).normalize();
 if(code==='GV14'||code==='GV2')return new T.Vector3(0,0,1);
 if(code==='SI11')return new T.Vector3(arr[0]*.25,-.05,1).normalize();
 if(code==='GB30')return new T.Vector3(arr[0]*.45,-.15,1).normalize();
 if(code==='SP12')return new T.Vector3(0,-.05,-1).normalize();
 if(code==='LI15')return new T.Vector3(0,0,-1);
 if(code==='TE14')return new T.Vector3(arr[0],-.15,1).normalize();
 if(code==='SI10')return new T.Vector3(arr[0]*.15,-.05,1).normalize();
 if(code==='SI9')return new T.Vector3(arr[0]*.2,-.08,1).normalize();
 if(code==='LU5')return new T.Vector3(0,0,-1);
 if(code==='PC3')return new T.Vector3(0,0,-1);
 if(code==='HT3')return new T.Vector3(.12,.06,-1).normalize();
 if(code==='LI11')return new T.Vector3(arr[0],0,.2).normalize();
 if(code==='TE10')return new T.Vector3(0,-.05,1).normalize();
 if(code==='SI8')return new T.Vector3(arr[0]*.3,-.05,1).normalize();
 if(code==='LU9'||code==='PC7')return new T.Vector3(0,0,-1);
 if(code==='HT7')return new T.Vector3(0,.06,-1).normalize();
 if(['LI5','TE4','SI5'].includes(code))return new T.Vector3(arr[0],0,.15).normalize();
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
 let pos=hit.position.slice(),nor=hit.normal.slice();
 if(!midline.has(code)){pos[0]=Math.abs(pos[0]);nor[0]=Math.abs(nor[0]);}
 else ({pos,nor}=finalizeMidlineHit(code,pos,nor));
 const catalogPoint=teachingByCode.get(code);
 const name=catalogPoint?.name??byCode.get(code)?.name??code;
 const defaultSet=armJointsSet.has(code)?'arm-joints-15':hipSacralSet.has(code)?'hip-sacral-6':backShuSet.has(code)?'back-shu-14':trunkSet.has(code)?'trunk-30':'head-neck-21';
 const entry={code,name,midline:midline.has(code)||!!catalogPoint?.midline,position:pos,normal:nor,reviewed:true,set:byCode.get(code)?.set??defaultSet};
 byCode.set(code,entry);
 updated.push(code);
}

/** 肘／腕橫紋：只鎖近遠端（X），不重吸附以免橈尺（Z）被收到同一點。 */
const realignGroupAxisOnly=(codes,axis,anchor)=>{
 const idx=axis==='x'?0:axis==='y'?1:2;
 const ref=byCode.get(anchor);
 if(!ref)return;
 const refVal=ref.midline&&axis==='x'?0:(axis==='x'?Math.abs(ref.position[idx]):ref.position[idx]);
 for(const code of codes){
  if(code===anchor)continue;
  const e=byCode.get(code);
  if(!e)continue;
  const pos=e.position.slice();
  if(axis==='x')pos[0]=e.midline?0:refVal;
  else pos[idx]=refVal;
  byCode.set(code,{...e,position:pos});
 }
};

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
  let np=hit.position.slice(),nor=hit.normal.slice();
  if(!midline.has(code)){np[0]=Math.abs(np[0]);nor[0]=Math.abs(nor[0]);}
  else ({pos:np,nor}=finalizeMidlineHit(code,np,nor));
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
 ['y',['GV2','BL34','BL30','BL54'],'GV2'],
 ['x',['BL30'],'BL23'],
 ['x',['BL54'],'BL52'],
 ['y',['SI11','BL43','BL14'],'BL14'],
 ['y',['CV17','KI23','ST17','PC1','SP18'],'ST17'],
 ['y',['GV16','GB20'],'GV16'],
 ['y',['GV15','BL10'],'GV15'],
]){
 const [axis,codes,anchor,fixed]=row;
 realignGroup(codes,axis,anchor,fixed);
}
const wristCreased=new Set(['LU9','PC7','HT7','LI5','TE4','SI5']);
/** 肘／腕橫紋皮膚窄，前向射線會收斂；對齊 X 後依橈→尺補 Z 再吸附。 */
const nudgeRadialSpread=(anchor,offsets)=>{
 const ref=byCode.get(anchor);
 if(!ref)return;
 const xRef=ref.position[0];
 const zRef=ref.position[2];
 for(const [code,dz] of Object.entries(offsets)){
  if(code===anchor)continue;
  const e=byCode.get(code);
  if(!e)continue;
  const spread=Math.abs(dz??0);
  const targetZ=zRef+(dz??0);
  const pos=e.position.slice();
  pos[0]=xRef;
  pos[2]=targetZ;
  const hit=snap(new T.Vector3(...pos),preferFor(code,pos));
  if(!hit)continue;
  let np=hit.position.slice(),nor=hit.normal.slice();
  np[0]=xRef;
  const wristCollapsed=wristCreased.has(code)&&spread>0&&Math.abs(np[2]-zRef)<spread*.35;
  const elbowMissed=!wristCreased.has(code)&&spread>0&&Math.abs(np[2]-targetZ)>spread*.5;
  if(wristCollapsed||elbowMissed){
   const hit2=snap(new T.Vector3(xRef,np[1],targetZ),preferFor(code,[xRef,np[1],targetZ]));
   const needHit=targetZ*zRef>=0?spread*0.1:spread*0.4;
   if(hit2&&Math.abs(hit2.position[2]-np[2])>=needHit){np=hit2.position.slice();nor=hit2.normal.slice();}
   else if(wristCollapsed){
    const y0=ref.position[1];
    let pick=null,bestPick=0;
    for(const dy of [0,.004,-.004,.008,-.008,.012,-.012,.016,-.016]){
     const h=snap(new T.Vector3(xRef,y0+dy,targetZ),preferFor(code,[xRef,y0+dy,targetZ]));
     if(!h)continue;
     const needRad=targetZ*zRef>=0?spread*.15:spread*.3;
     const radial=Math.abs(h.position[2]-zRef),toward=Math.abs(h.position[2]-targetZ);
     if(radial<needRad||toward>Math.abs(np[2]-targetZ)+1e-6)continue;
     if(radial>bestPick){pick=h;bestPick=radial;}
    }
    if(pick){np=pick.position.slice();nor=pick.normal.slice();}
    else if(hit2){np=hit2.position.slice();nor=hit2.normal.slice();}
   }
   else if(hit2){np=hit2.position.slice();nor=hit2.normal.slice();}
   else if(elbowMissed)np[2]+=(targetZ-np[2])*.65;
  }
  np[0]=xRef;
  nor[0]=Math.abs(nor[0]);
  byCode.set(code,{...e,position:np,normal:nor});
 }
};
const armRadialTweak=()=>{
 nudgeRadialSpread('LU5',{PC3:.013,HT3:.024});
 nudgeRadialSpread('LU9',{PC7:.01,HT7:.018});
 nudgeRadialSpread('LI5',{TE4:.006,SI5:.01});
 const lu5=byCode.get('LU5'),pc3=byCode.get('PC3'),ht3=byCode.get('HT3');
 if(lu5&&pc3&&ht3){
  const zLu=lu5.position[2],yLu=lu5.position[1];
  const elbowY=(code,yOff)=>{
   const e=byCode.get(code);
   if(!e)return;
   const pos=[lu5.position[0],yLu+yOff,e.position[2]];
   const hit=snap(new T.Vector3(...pos),preferFor(code,pos));
   if(!hit)return;
   const np=hit.position.slice();
   np[0]=lu5.position[0];np[2]=e.position[2];
   byCode.set(code,{...e,position:np,normal:[Math.abs(hit.normal[0]),hit.normal[1],hit.normal[2]]});
  };
  elbowY('PC3',-.006);
  elbowY('HT3',+.004);
 }
};
if(!codesFilter){
 realignGroupAxisOnly(['LU5','PC3','HT3','LI11','SI8'],'x','LU5');
 realignGroupAxisOnly(['LU9','PC7','HT7','LI5','TE4','SI5'],'x','PC7');
 armRadialTweak();
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
 const elbowLine=['LU5','PC3','HT3','LI11','SI8'];
 if(elbowLine.some(c=>codesFilter.includes(c))) realignGroupAxisOnly(elbowLine,'x','LU5');
 const wristLine=['LU9','PC7','HT7','LI5','TE4','SI5'];
 if(wristLine.some(c=>codesFilter.includes(c))) realignGroupAxisOnly(wristLine,'x','PC7');
 if(elbowLine.some(c=>codesFilter.includes(c))||wristLine.some(c=>codesFilter.includes(c))) armRadialTweak();
 const sacralLine=['GV2','BL34','BL30','BL54'];
 if(sacralLine.some(c=>codesFilter.includes(c))){
  realignGroup(sacralLine,'y','GV2');
  realignGroup(['BL30'],'x','BL23');
  realignGroup(['BL54'],'x','BL52');
 }
 const blLine=['BL11','BL12','BL13','BL14','BL15','BL16','BL17','BL18','BL19','BL20','BL21','BL22','BL23'];
 if(blLine.some(c=>codesFilter.includes(c))){
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
}

const points=[...byCode.values()].sort((a,b)=>a.code.localeCompare(b.code));
const payload=isAdultBake?{
 model:'anime-adult.glb',character:'AvatarSample_A (VRoid official sample)',license:'VRoid official sample terms (not CC0)',set:'head-neck-21+trunk-30+back-shu-14+hip-sacral-6+arm-joints-15',
 faceAxis:'-Z',sideConvention:'position.x>0 means anatomical left; anime runtime flips X to -X for left',
 note:'穴位依 J_Bip_*／J_Adj_* 骨骼世界座標種子＋皮膚射線吸附；與少女共用種子表，骨骼名稱與 AvatarSample_A 一致。教學示意，非臨床取穴。合併寫入，不會清空既有穴。',
 points,
}:{
 model:'anime-assistant.glb',character:'Sendagaya Shino',license:'CC0 1.0 (VRoid Studio beta sample)',set:'head-neck-21+trunk-30+back-shu-14+hip-sacral-6+arm-joints-15',
 faceAxis:'-Z',sideConvention:'position.x>0 means anatomical left; anime runtime flips X to -X for left',
 note:'穴位依解剖比例與臉部／軀幹骨骼標誌吸附到角色皮膚；教學示意，非臨床取穴。合併寫入，不會清空既有穴。',
 points,
};

if(dryRun){
 console.log(`dry-run merge: would update ${updated.join(',')} ; total points ${points.length} (existing kept)`);
 process.exit(0);
}

writeFileSync(outputPath,JSON.stringify(payload,null,2)+'\n');
console.log(`merged ${updated.length} point(s) [${updated.join(',')}]; total ${points.length} → ${outputRel}`);
