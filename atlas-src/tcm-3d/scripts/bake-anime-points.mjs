#!/usr/bin/env node
/** Bake head-neck-21 surface points onto public/models/anime-assistant.glb. Node-only helper. */
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
 return {position:hits[0].point.toArray().map(v=>+v.toFixed(5)),normal:outward.normalize().toArray().map(v=>+v.toFixed(5))};
};
const L=-1;
const seeds={
 GV20:[0,head.y+.155,head.z+.025],GV23:[0,eyeMid.y+.105,eyeMid.z-.055],'GV24+':[0,eyeMid.y+.028,eyeMid.z-.02],
 GV26:[0,eyeMid.y-.055,eyeMid.z-.045],CV24:[0,eyeMid.y-.095,eyeMid.z-.02],CV23:[0,neck.y-.01,neck.z-.035],
 CV22:[0,neck.y-.09,neck.z-.055],BL2:[.012*L,eyeMid.y+.03,eyeMid.z-.02],BL1:[.018*L,eyeMid.y+.005,eyeMid.z-.015],
 LI20:[.018*L,eyeMid.y-.04,eyeMid.z-.04],GB14:[.03*L,eyeMid.y+.055,eyeMid.z-.02],'EX-HN4':[.03*L,eyeMid.y+.036,eyeMid.z-.03],
 ST1:[.028*L,eyeMid.y-.02,eyeMid.z-.02],ST2:[.03*L,eyeMid.y-.035,eyeMid.z-.03],ST3:[.032*L,eyeMid.y-.055,eyeMid.z-.035],
 ST4:[.034*L,eyeMid.y-.075,eyeMid.z-.025],ST9:[.038*L,neck.y-.02,neck.z-.045],TE23:[.055*L,eyeMid.y+.02,eyeMid.z+.005],
 'EX-HN5':[.06*L,eyeMid.y+.01,eyeMid.z+.02],GB1:[.055*L,eyeMid.y-.005,eyeMid.z+.005],SI18:[.05*L,eyeMid.y-.05,eyeMid.z+.01],
};
const names={GV20:'百會',GV23:'上星','GV24+':'印堂',GV26:'水溝',CV24:'承漿',CV23:'廉泉',CV22:'天突',BL2:'攢竹',BL1:'睛明',LI20:'迎香',GB14:'陽白','EX-HN4':'魚腰',ST1:'承泣',ST2:'四白',ST3:'巨髎',ST4:'地倉',ST9:'人迎',TE23:'絲竹空','EX-HN5':'太陽',GB1:'瞳子髎',SI18:'顴髎'};
const midline=new Set(['GV20','GV23','GV24+','GV26','CV24','CV23','CV22']);
const points=[];
for(const [code,arr] of Object.entries(seeds)){
 const seed=new T.Vector3(...arr);
 const prefer=midline.has(code)?new T.Vector3(0,code==='GV20'?1:0,-1).normalize():new T.Vector3(arr[0],0,-.6).normalize();
 const hit=snap(seed,prefer)??{position:arr.map(v=>+v.toFixed(5)),normal:prefer.toArray().map(v=>+v.toFixed(5))};
 const pos=hit.position.slice(),nor=hit.normal.slice();
 if(!midline.has(code)){pos[0]=Math.abs(pos[0]);nor[0]=Math.abs(nor[0]);}
 points.push({code,name:names[code],midline:midline.has(code),position:pos,normal:nor,reviewed:true,set:'head-neck-21'});
}
writeFileSync(join(rootDir,'app/anime-points.json'),JSON.stringify({
 model:'anime-assistant.glb',character:'Sendagaya Shino',license:'CC0 1.0 (VRoid Studio beta sample)',set:'head-neck-21',
 faceAxis:'-Z',sideConvention:'position.x>0 means anatomical left; anime runtime flips X to -X for left',
 note:'頭頸 21 穴依解剖比例與臉部骨骼標誌吸附到角色皮膚；教學示意，非臨床取穴。',points,
},null,2));
console.log(`baked ${points.length} points`);
