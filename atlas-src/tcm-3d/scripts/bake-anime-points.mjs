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
const eyeMid=eyeL.clone().add(eyeR).multiplyScalar(.5);
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
 if(!hits[0]){ray.set(seed.clone().add(new T.Vector3(0,0,-.12)),new T.Vector3(0,0,1));ray.far=.25;hits=ray.intersectObjects(projectMeshes,false);}
 if(!hits[0])return null;
 let outward=(hits[0].face?.normal.clone()??n).transformDirection(hits[0].object.matrixWorld);
 if(outward.dot(n)<0)outward.negate();
 return {position:hits[0].point.toArray().map(v=>+v.toFixed(5)),normal:outward.normalize().toArray().map(v=>+v.toFixed(5)),meshId:hits[0].object.name||'anime-skin'};
};
const L=-1;
/** Relative head/neck/body bone seeds. Add new weekly codes here before --codes bake. */
const seeds={
 GV20:[0,head.y+.155,head.z+.025],GV23:[0,eyeMid.y+.105,eyeMid.z-.055],'GV24+':[0,eyeMid.y+.028,eyeMid.z-.02],
 GV26:[0,eyeMid.y-.055,eyeMid.z-.045],CV24:[0,eyeMid.y-.095,eyeMid.z-.02],CV23:[0,neck.y-.01,neck.z-.035],
 CV22:[0,neck.y-.09,neck.z-.055],BL2:[.012*L,eyeMid.y+.03,eyeMid.z-.02],BL1:[.018*L,eyeMid.y+.005,eyeMid.z-.015],
 LI20:[.018*L,eyeMid.y-.04,eyeMid.z-.04],GB14:[.03*L,eyeMid.y+.055,eyeMid.z-.02],'EX-HN4':[.03*L,eyeMid.y+.036,eyeMid.z-.03],
 ST1:[.028*L,eyeMid.y-.02,eyeMid.z-.02],ST2:[.03*L,eyeMid.y-.035,eyeMid.z-.03],ST3:[.032*L,eyeMid.y-.055,eyeMid.z-.035],
 ST4:[.034*L,eyeMid.y-.075,eyeMid.z-.025],ST9:[.038*L,neck.y-.02,neck.z-.045],TE23:[.055*L,eyeMid.y+.02,eyeMid.z+.005],
 'EX-HN5':[.06*L,eyeMid.y+.01,eyeMid.z+.02],GB1:[.055*L,eyeMid.y-.005,eyeMid.z+.005],SI18:[.05*L,eyeMid.y-.05,eyeMid.z+.01],
};
const midline=new Set(['GV20','GV23','GV24+','GV26','CV24','CV23','CV22']);

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
 const prefer=midline.has(code)?new T.Vector3(0,code==='GV20'?1:0,-1).normalize():new T.Vector3(arr[0],0,-.6).normalize();
 const hit=snap(seed,prefer)??{position:arr.map(v=>+v.toFixed(5)),normal:prefer.toArray().map(v=>+v.toFixed(5)),meshId:'anime-skin'};
 const pos=hit.position.slice(),nor=hit.normal.slice();
 if(!midline.has(code)){pos[0]=Math.abs(pos[0]);nor[0]=Math.abs(nor[0]);}
 const catalogPoint=teachingByCode.get(code);
 const name=catalogPoint?.name??byCode.get(code)?.name??code;
 const entry={code,name,midline:midline.has(code)||!!catalogPoint?.midline,position:pos,normal:nor,reviewed:true,set:byCode.get(code)?.set??'head-neck-21'};
 byCode.set(code,entry);
 updated.push(code);
}

const points=[...byCode.values()].sort((a,b)=>a.code.localeCompare(b.code));
const payload={
 model:'anime-assistant.glb',character:'Sendagaya Shino',license:'CC0 1.0 (VRoid Studio beta sample)',set:'head-neck-21',
 faceAxis:'-Z',sideConvention:'position.x>0 means anatomical left; anime runtime flips X to -X for left',
 note:'穴位依解剖比例與臉部骨骼標誌吸附到角色皮膚；教學示意，非臨床取穴。合併寫入，不會清空既有穴。',
 points,
};

if(dryRun){
 console.log(`dry-run merge: would update ${updated.join(',')} ; total points ${points.length} (existing kept)`);
 process.exit(0);
}

writeFileSync(join(rootDir,'app/anime-points.json'),JSON.stringify(payload,null,2)+'\n');
console.log(`merged ${updated.length} point(s) [${updated.join(',')}]; total ${points.length}`);
